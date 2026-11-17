#!/usr/bin/env python3
"""Publica en FCM SOLO boletines aprobados y todavía vigentes.
Por defecto es vista previa. Para entrega real: --send. Las credenciales son privadas.
"""
from __future__ import annotations
import argparse
import datetime as dt
import json
import os
import pathlib
import sqlite3
import sys
import urllib.parse

BASE = pathlib.Path(__file__).resolve().parent
COLA = BASE/'avisos_pendientes.json'
DB = BASE/'estado_envios.sqlite'  # misma base del emisor inicial: no duplica IDs enviados allí
REGIONES = set('amazonas ancash apurimac arequipa ayacucho cajamarca callao cusco huancavelica huanuco ica junin la_libertad lambayeque lima loreto madre_de_dios moquegua pasco piura puno san_martin tacna tumbes ucayali'.split())

def parse_date(value):
    if not isinstance(value,str): return None
    try:
        r=dt.datetime.fromisoformat(value.replace('Z','+00:00'))
        return r if r.tzinfo is not None else None
    except ValueError: return None

def es_valido(n, now):
    if not isinstance(n,dict) or n.get('aprobado') is not True: return False
    if n.get('categoria')!='boletin_informativo' or n.get('region') not in REGIONES: return False
    if not isinstance(n.get('id'),str) or not n['id'].startswith('senamhi_q_'): return False
    if not isinstance(n.get('aprobado_por'),str) or len(n['aprobado_por'].strip()) < 3: return False
    if not isinstance(n.get('titulo'),str) or not 5<=len(n['titulo'])<=85: return False
    if not isinstance(n.get('mensaje'),str) or not 15<=len(n['mensaje'])<=350: return False
    url=urllib.parse.urlparse(n.get('fuente_url',''))
    if url.scheme != 'https' or url.hostname != 'geosinpad.indeci.gob.pe': return False
    emit=parse_date(n.get('fecha_fuente_utc'))
    expires=parse_date(n.get('caduca_utc'))
    verified=parse_date(n.get('verificado_el_utc'))
    if not all((emit,expires,verified)): return False
    return emit <= verified <= now < expires and (now-verified)<dt.timedelta(hours=12)

def run(send=False):
    if not COLA.exists(): print('No existen avisos pendientes.'); return 0
    cola=json.loads(COLA.read_text(encoding='utf-8-sig'))
    if not isinstance(cola,list): raise ValueError('La cola tiene un formato inválido')
    now=dt.datetime.now(dt.timezone.utc)
    vistos=set(); candidatos=[]
    for n in cola:
        if es_valido(n,now) and n['id'] not in vistos:
            vistos.add(n['id']);candidatos.append(n)
    print('Boletines aprobados, vigentes y revisados hace menos de 12 horas:',len(candidatos))
    if not send:
        for n in candidatos: print('VISTA PREVIA:',n['id'],n['region'],n['titulo'])
        print('SIN ENVÍO. Solo --send realiza publicaciones reales.')
        return 0
    if not candidatos:
        print('Nada por enviar.'); return 0
    private=os.environ.get('GOOGLE_APPLICATION_CREDENTIALS','')
    if not pathlib.Path(private).is_file():
        raise ValueError('Falta GOOGLE_APPLICATION_CREDENTIALS: JSON de cuenta de SERVICIO fuera del proyecto')
    import firebase_admin
    from firebase_admin import messaging
    if not firebase_admin._apps:
        firebase_admin.initialize_app()
    with sqlite3.connect(DB,timeout=30) as con:
        con.execute('CREATE TABLE IF NOT EXISTS sent (id TEXT PRIMARY KEY, sent_at TEXT NOT NULL, firebase_id TEXT NOT NULL)')
        con.commit()
        for n in candidatos:
            # Mantener un bloqueo SQLite entre comprobación y envío para reducir duplicados concurrentes.
            con.execute('BEGIN IMMEDIATE')
            if con.execute('SELECT 1 FROM sent WHERE id=?',(n['id'],)).fetchone():
                print('YA ENVIADO:',n['id']); con.commit(); continue
            msg=messaging.Message(
                notification=messaging.Notification(title=('Información preventiva · '+n['titulo'])[:110], body=n['mensaje']),
                data={'ecoresilia_category':'boletin_informativo','source_url':n['fuente_url'], 'region':n['region'],'notice_id':n['id']},
                topic='ecoresilia_region_'+n['region'],
                android=messaging.AndroidConfig(priority='normal', ttl=dt.timedelta(hours=2))
            )
            try:
                result=messaging.send(msg)
                con.execute('INSERT INTO sent (id,sent_at,firebase_id) VALUES (?,?,?)',(
                    n['id'],dt.datetime.now(dt.timezone.utc).isoformat(),result))
                con.commit()
                print('ENVIADO:',n['id'],'al tema',n['region'])
            except Exception as e:
                con.rollback()
                print('FALLO:',n['id'],str(e),file=sys.stderr)
                return 1
    return 0

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--send',action='store_true')
    args=p.parse_args()
    try: sys.exit(run(send=args.send))
    except Exception as exc: print('ERROR:',exc,file=sys.stderr); sys.exit(1)
