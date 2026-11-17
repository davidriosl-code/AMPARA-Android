#!/usr/bin/env python3
"""Lee la capa oficial SENAMHI/GeoSINPAD de posible activación de quebradas.
Genera BORRADORES pendientes de revisión. NUNCA envía notificaciones.
Uso: py -3 detectar_avisos_oficiales.py [--fixture archivo.json]
"""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import sys
import tempfile
import unicodedata
import urllib.parse
import urllib.request

BASE = pathlib.Path(__file__).resolve().parent
COLA = BASE / 'avisos_pendientes.json'
SERVICIO = 'https://geosinpad.indeci.gob.pe/indeci/rest/services/Ent_Tecnico_Cientificas/SENAMHI/MapServer/2'
REGIONES = set('amazonas ancash apurimac arequipa ayacucho cajamarca callao cusco huancavelica huanuco ica junin la_libertad lambayeque lima loreto madre_de_dios moquegua pasco piura puno san_martin tacna tumbes ucayali'.split())


def ahora():
    return dt.datetime.now(dt.timezone.utc)


def region_slug(valor):
    if not isinstance(valor, str):
        return None
    texto = unicodedata.normalize('NFKD', valor)
    texto = ''.join(c for c in texto if not unicodedata.combining(c)).lower()
    texto = re.sub(r'[^a-z ]', ' ', texto)
    texto = '_'.join(texto.split())
    return texto if texto in REGIONES else None


def to_date(valor):
    # ArcGIS representa fechas con milisegundos Unix UTC.
    if isinstance(valor, (int, float)) and valor > 1e11:
        return dt.datetime.fromtimestamp(valor / 1000, dt.timezone.utc)
    if isinstance(valor, str):
        try:
            return dt.datetime.fromisoformat(valor.replace('Z','+00:00')).astimezone(dt.timezone.utc)
        except ValueError:
            return None
    return None


def safe_text(valor, maxlen=75):
    texto = ' '.join(str(valor or '').split())
    return ''.join(c for c in texto if c.isprintable())[:maxlen]


def importar_registros(datos, tiempo=None):
    tiempo = tiempo or ahora()
    if not isinstance(datos, dict) or datos.get('error') or not isinstance(datos.get('features'), list):
        raise ValueError('Respuesta ArcGIS no válida; no se actualizó la cola')
    nuevos = []
    for item in datos['features']:
        if not isinstance(item, dict):
            continue
        a = item.get('attributes') or {}
        if not isinstance(a, dict):
            continue
        slug = region_slug(a.get('NOMBDEP'))
        fecha = to_date(a.get('FECHA'))
        if not slug or not fecha:
            continue
        edad = (tiempo - fecha).total_seconds()/3600
        if edad < -2 or edad > 36:  # Fuente 24 h: protección contra datos obsoletos.
            continue
        region = safe_text(a.get('NOMBDEP'), 30).title()
        distrito = safe_text(a.get('NOMBDIST'), 45)
        provincia = safe_text(a.get('NOMBPROV'), 45)
        nivel = safe_text(a.get('NIVEL'), 30)
        oid = str(a.get('OBJECTID') or a.get('ID') or '')
        if not oid:
            continue
        h = hashlib.sha256(f'quebradas|{oid}|{fecha.isoformat()}|{slug}'.encode()).hexdigest()[:18]
        titulo = f'Posible activación de quebradas · {region}'[:85]
        lugar = ', '.join(p for p in (distrito, provincia, region) if p)
        mensaje = f'GeoSINPAD/SENAMHI registra información preventiva sobre posible activación de quebradas en {lugar}.'
        if nivel:
            mensaje += f' Nivel indicado: {nivel}.'
        mensaje += ' Revisa el documento oficial y su vigencia. EcoResilia no emite alertas oficiales.'
        link = SERVICIO + '/query?' + urllib.parse.urlencode({'where': f'OBJECTID={oid}' if oid.isdecimal() else '1=1','outFields':'*','returnGeometry':'false','f':'html'})
        nuevos.append({
            'id':'senamhi_q_'+h,
            'categoria':'boletin_informativo',
            'region': slug,
            'titulo':titulo,
            'mensaje':mensaje[:350],
            'fuente_url':link,
            'programado_utc':tiempo.replace(microsecond=0).isoformat().replace('+00:00','Z'),
            'fecha_fuente_utc':fecha.isoformat().replace('+00:00','Z'),
            'caduca_utc':(fecha+dt.timedelta(hours=36)).isoformat().replace('+00:00','Z'),
            'lugar_detalle':lugar,
            'nivel_fuente':nivel,
            'aprobado':False,
            'aprobado_por':'',
            'verificado_el_utc':None,
            'fuente':'SENAMHI vía GeoSINPAD (INDECI)',
            'tipo_registro':'posible_activacion_quebradas_24h'
        })
    return nuevos


def descargar():
    url = SERVICIO + '/query?' + urllib.parse.urlencode({
        'where':'1=1', 'outFields':'OBJECTID,ID,NIVEL,FECHA,NOMBDEP,NOMBPROV,NOMBDIST',
        'returnGeometry':'false', 'resultRecordCount':1000, 'f':'json'})
    request = urllib.request.Request(url, headers={'User-Agent':'EcoResilia-Monitoreo/1.0 (solo consulta)', 'Accept':'application/json'})
    with urllib.request.urlopen(request,timeout=18) as response:
        if response.status != 200:
            raise ValueError(f'Servicio devolvió HTTP {response.status}')
        payload=response.read(3_000_001)
        if len(payload)>3_000_000:
            raise ValueError('La respuesta oficial excede el límite de seguridad')
    return json.loads(payload.decode('utf-8-sig'))


def guardar_atomicamente(ruta, contenido):
    ruta.parent.mkdir(parents=True, exist_ok=True)
    fd, temporal = tempfile.mkstemp(prefix='.eco-cola-',suffix='.json',dir=str(ruta.parent))
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as f:
            json.dump(contenido,f,ensure_ascii=False,indent=2)
            f.write('\n')
        os.replace(temporal,ruta)
    finally:
        if os.path.exists(temporal):
            os.unlink(temporal)


def procesar(datos, ruta=COLA, tiempo=None):
    candidatos = importar_registros(datos,tiempo)
    if ruta.exists():
        cola=json.loads(ruta.read_text(encoding='utf-8-sig'))
        if not isinstance(cola,list):
            raise ValueError('La cola existente no es una lista; no se ha cambiado')
    else:
        cola=[]
    existentes={x.get('id') for x in cola if isinstance(x,dict)}
    novedades=[n for n in candidatos if n['id'] not in existentes]
    if novedades:
        guardar_atomicamente(ruta,cola+novedades)
    return len(candidatos),len(novedades)


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--fixture',help='Archivo JSON local para pruebas sin Internet')
    p.add_argument('--cola',default=str(COLA),help='Ruta de cola para pruebas')
    args=p.parse_args()
    try:
        datos=json.loads(pathlib.Path(args.fixture).read_text(encoding='utf-8')) if args.fixture else descargar()
        numero,nuevos=procesar(datos,pathlib.Path(args.cola))
        print(f'Fuente oficial consultada. Registros recientes con región reconocida: {numero}; borradores nuevos: {nuevos}.')
        print('Todos los borradores permanecen SIN APROBAR. No se enviaron notificaciones.')
        if datos.get('exceededTransferLimit'):
            print('ATENCIÓN: el servicio devolvió un resultado parcial; revisar manualmente.')
        return 0
    except Exception as exc:
        print(f'ERROR CONTROLADO: {exc}. No se enviaron notificaciones.', file=sys.stderr)
        return 1

if __name__=='__main__': sys.exit(main())
