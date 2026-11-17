#!/usr/bin/env python3
"""EcoResilia: consulta informativa GeoSINPAD/SENAMHI en la nube.

PREVIEW por defecto. DEMO solo al token secreto de prueba.
AUTO requiere habilitación explícita + inicialización previa en Firestore.
No emite alertas de emergencia, solo resúmenes informativos de datos públicos.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
import re
import sys
import unicodedata
from urllib.parse import urlencode
from urllib.request import Request, urlopen

UTC = timezone.utc
SERVICE = 'https://geosinpad.indeci.gob.pe/indeci/rest/services/Ent_Tecnico_Cientificas/SENAMHI/MapServer/2'
REGIONS = set('amazonas ancash apurimac arequipa ayacucho cajamarca callao cusco huancavelica huanuco ica junin la_libertad lambayeque lima loreto madre_de_dios moquegua pasco piura puno san_martin tacna tumbes ucayali'.split())
TYPE = 'posible_activacion_quebradas_24h'
COLLECTION = 'ecoresilia_boletines_informativos_v1'
META_COLLECTION = 'ecoresilia_monitor_meta_v1'


def now_utc():
    return datetime.now(UTC)


def slug(value):
    if not isinstance(value, str):
        return None
    norm = unicodedata.normalize('NFKD', value)
    norm = ''.join(c for c in norm if not unicodedata.combining(c)).lower()
    norm = re.sub(r'[^a-z ]', ' ', norm)
    value = '_'.join(norm.split())
    return value if value in REGIONS else None


def source_date(value):
    try:
        if isinstance(value, (int, float)) and not isinstance(value, bool) and value > 1e11:
            return datetime.fromtimestamp(value / 1000, UTC)
        if isinstance(value, str):
            parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
            return parsed.astimezone(UTC) if parsed.tzinfo else None
    except (ValueError, OverflowError, OSError):
        return None
    return None


def fetch_official():
    params = {
        'where': '1=1',
        'outFields': 'OBJECTID,ID,NIVEL,FECHA,NOMBDEP,NOMBPROV,NOMBDIST',
        'returnGeometry': 'false',
        'resultRecordCount': '1000',
        'f': 'json',
    }
    url = SERVICE + '/query?' + urlencode(params)
    req = Request(url, headers={
        'User-Agent': 'EcoResilia-CloudMonitor/1.0 (consulta informativa)',
        'Accept': 'application/json',
    })
    with urlopen(req, timeout=25) as response:
        if response.status != 200:
            raise RuntimeError('Fuente GeoSINPAD no respondió HTTP 200')
        raw = response.read(3_000_001)
    if len(raw) > 3_000_000:
        raise RuntimeError('Respuesta demasiado grande; sin publicación')
    data = json.loads(raw.decode('utf-8-sig'))
    if not isinstance(data, dict) or data.get('error') or not isinstance(data.get('features'), list):
        raise RuntimeError('Respuesta ArcGIS inválida; sin publicación')
    if data.get('exceededTransferLimit') or len(data['features']) >= 1000:
        raise RuntimeError('Consulta posiblemente incompleta (1000 filas); sin publicación')
    return data


def group_data(data, now=None):
    """No interpreta registros como eventos únicos ni declara emergencia.

    Se agrupa una vez por región + fecha UTC de la fuente + tipo.
    Filtra datos de más de 24 h y evita fechas futuras significativas.
    """
    now = now or now_utc()
    if not isinstance(data, dict) or not isinstance(data.get('features'), list):
        raise ValueError('Datos oficiales inválidos')
    if data.get('error') or data.get('exceededTransferLimit') or len(data['features']) >= 1000:
        raise ValueError('Fuente incompleta o con error')
    groups = defaultdict(lambda: {'ids': set(), 'localidades': set(), 'niveles': set()})
    for feature in data['features']:
        if not isinstance(feature, dict) or not isinstance(feature.get('attributes'), dict):
            continue
        a = feature['attributes']
        region = slug(a.get('NOMBDEP'))
        when = source_date(a.get('FECHA'))
        oid = a.get('OBJECTID') or a.get('ID')
        if not region or not when or not isinstance(oid, (str, int)):
            continue
        if when > now + timedelta(hours=2) or when < now - timedelta(hours=24):
            continue
        key = (region, when.date().isoformat(), TYPE)
        g = groups[key]
        g['ids'].add(str(oid))
        for name in ('NOMBDIST', 'NOMBPROV'):
            val = a.get(name)
            if isinstance(val, str) and val.strip():
                g['localidades'].add(val.strip().casefold()[:80])
        level = a.get('NIVEL')
        if isinstance(level, str) and level.strip():
            g['niveles'].add(level.strip()[:40])
    result = []
    for (region, date, typ), g in sorted(groups.items()):
        digest = hashlib.sha256(f'{typ}|{date}|{region}'.encode()).hexdigest()[:32]
        result.append({
            'id': f'geosinpad_{digest}',
            'region': region,
            'fecha_utc': date,
            'tipo': typ,
            'filas': len(g['ids']),
            'localidades_aprox': len(g['localidades']),
            'niveles': sorted(g['niveles']),
            'fuente': SERVICE,
        })
    return result


def admin_services():
    raw = os.environ.get('FIREBASE_SERVICE_ACCOUNT_JSON', '').strip()
    if not raw:
        raise RuntimeError('Falta secreto FIREBASE_SERVICE_ACCOUNT_JSON (no subirlo al repositorio)')
    obj = json.loads(raw)
    if obj.get('type') != 'service_account' or obj.get('project_id') != 'ecoresilia':
        raise RuntimeError('Cuenta Firebase inválida o de un proyecto diferente a ecoresilia')
    import firebase_admin
    from firebase_admin import credentials, firestore, messaging
    if not firebase_admin._apps:
        firebase_admin.initialize_app(credentials.Certificate(obj))
    return firestore.client(), messaging


def bootstrap(groups, db):
    """Marca grupos existentes como vistos sin enviar; obligatorio antes de AUTO."""
    count = 0
    for group in groups:
        ref = db.collection(COLLECTION).document(group['id'])
        try:
            ref.create({'estado': 'historico_sin_envio', 'region': group['region'],
                        'fecha_utc': group['fecha_utc'], 'tipo': group['tipo'],
                        'filas': group['filas'], 'registrado_utc': now_utc().isoformat()})
            count += 1
        except Exception as exc:
            # Solo ignorar un documento ya existente, nunca permisos/servicio caído.
            from google.api_core.exceptions import AlreadyExists
            if not isinstance(exc, AlreadyExists):
                raise
    db.collection(META_COLLECTION).document('bootstrap').set({
        'completado': True, 'fecha_utc': now_utc().isoformat(),
        'grupos_marcados_en_esta_ejecucion': count,
    })
    print(f'BASELINE: {count} grupos históricos marcados, 0 mensajes enviados.')


def demo(messaging):
    token = os.environ.get('ECORESILIA_DEMO_TOKEN', '').strip()
    if len(token) < 80:
        raise RuntimeError('Falta secreto ECORESILIA_DEMO_TOKEN para la prueba individual')
    msg = messaging.Message(
        token=token,
        notification=messaging.Notification(
            title='EcoResilia · SIMULACRO TÉCNICO',
            body='Prueba del monitor en la nube. No existe una emergencia real.'),
        data={'category': 'simulacro', 'source': 'ecoresilia_cloud'},
        android=messaging.AndroidConfig(priority='normal'),
    )
    message_id = messaging.send(msg)
    print('FCM aceptó simulacro individual; ID:', message_id)


def publish(groups, db, messaging):
    if os.environ.get('ECORESILIA_AUTO_PUBLISH', '').strip().lower() != 'true':
        print('PUBLICACIÓN DESACTIVADA: ECORESILIA_AUTO_PUBLISH != true; 0 envíos.')
        return
    marker = db.collection(META_COLLECTION).document('bootstrap').get()
    if not marker.exists or marker.to_dict().get('completado') is not True:
        raise RuntimeError('Falta baseline Firestore: ejecuta bootstrap antes de publicar')
    # No publicar el mismo día del bootstrap para evitar envío retroactivo accidental.
    baseline_date = source_date(marker.to_dict().get('fecha_utc'))
    if not baseline_date or (now_utc() - baseline_date).total_seconds() < 0:
        raise RuntimeError('Fecha de baseline inválida; publicación detenida')
    sent = 0
    for group in groups:
        ref = db.collection(COLLECTION).document(group['id'])
        try:
            # Reserva durable ANTES de FCM: falla cerrado ante ejecuciones duplicadas.
            ref.create({'estado': 'reservado', 'region': group['region'],
                        'fecha_utc': group['fecha_utc'], 'tipo': group['tipo'],
                        'filas': group['filas'], 'reservado_utc': now_utc().isoformat()})
        except Exception as exc:
            from google.api_core.exceptions import AlreadyExists
            if isinstance(exc, AlreadyExists):
                print('YA VISTO:', group['region'], group['fecha_utc'])
                continue
            raise
        region_label = group['region'].replace('_', ' ').title()
        title = f'EcoResilia · Información preventiva · {region_label}'
        body = ('GeoSINPAD/SENAMHI registra información sobre posible activación de quebradas. '
                'Consulta la fuente oficial y su vigencia. No es una alerta de emergencia.')
        msg = messaging.Message(
            topic='ecoresilia_v2_' + group['region'] + '_quebradas',
            # Data-only para que EcoMessagingService pueda guardar aviso en Room
            # tanto en foreground como en background. FCM no garantiza entrega crítica.
            data={'title': title, 'body': body, 'category': 'boletin_informativo',
                  'notice_id': group['id'], 'source_url': SERVICE,
                  'date_utc': group['fecha_utc'], 'region': group['region']},
            android=messaging.AndroidConfig(priority='normal'),
        )
        try:
            message_id = messaging.send(msg)
        except Exception as exc:
            # No reintento automático: el estado reservado evita duplicados si FCM aceptó
            # el envío pero hubo un error ambiguo en la respuesta.
            ref.update({'estado': 'requiere_revision', 'error': type(exc).__name__})
            print('ENVÍO NO CONFIRMADO:', group['region'], type(exc).__name__, file=sys.stderr)
            continue
        ref.update({'estado': 'enviado', 'firebase_id': message_id,
                    'enviado_utc': now_utc().isoformat()})
        sent += 1
        print('FCM ACEPTÓ:', group['region'], group['fecha_utc'], message_id)
    print(f'TOTAL FCM ACEPTADOS: {sent}. La entrega visible en dispositivos no está garantizada.')


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', choices=['preview', 'bootstrap', 'demo', 'auto'], default='preview')
    parser.add_argument('--fixture', help='JSON ArcGIS local para pruebas; nunca para envíos')
    args = parser.parse_args(argv)
    if args.fixture and args.mode != 'preview':
        parser.error('Los fixtures solo se admiten en preview')
    if args.mode == 'demo':
        db, messaging = admin_services()
        demo(messaging)
        return 0
    if args.fixture:
        with open(args.fixture, encoding='utf-8') as f:
            data = json.load(f)
    else:
        data = fetch_official()
    groups = group_data(data)
    print('Fuente:', SERVICE)
    print('Filas devueltas:', len(data['features']))
    print('Resúmenes recientes:', len(groups))
    for g in groups:
        print(' -', g['region'], '|', g['fecha_utc'], '|', g['filas'], 'filas')
    if args.mode == 'preview':
        print('VISTA PREVIA: 0 mensajes enviados; no se modifica Firestore.')
        return 0
    if args.mode == 'auto' and os.environ.get('ECORESILIA_AUTO_PUBLISH', '').strip().lower() != 'true':
        print('PROGRAMADO EN NUBE: solo lectura; ECORESILIA_AUTO_PUBLISH no está habilitado. 0 envíos.')
        return 0
    db, messaging = admin_services()
    if args.mode == 'bootstrap':
        bootstrap(groups, db)
    else:
        publish(groups, db, messaging)
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as exc:
        print('ERROR CONTROLADO:', type(exc).__name__, str(exc)[:250], file=sys.stderr)
        sys.exit(1)
