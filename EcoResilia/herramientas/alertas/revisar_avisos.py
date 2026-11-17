#!/usr/bin/env python3
"""Revisión humana obligatoria de borradores EcoResilia.
Uso: py -3 revisar_avisos.py --list
     py -3 revisar_avisos.py --aprobar ID --revisor TU_NOMBRE --confirmo-fuente
"""
from __future__ import annotations
import argparse
import datetime as dt
import json
import pathlib
import sys
from detectar_avisos_oficiales import COLA, guardar_atomicamente


def revisar(cola, identificador, revisor, confirmo, ahora=None):
    ahora=ahora or dt.datetime.now(dt.timezone.utc)
    if not confirmo:
        raise ValueError('Debe confirmar que verificó documento, fecha, ubicación y vigencia')
    if len(revisor.strip())<3:
        raise ValueError('Indica un nombre de revisor de al menos 3 caracteres')
    items=[a for a in cola if isinstance(a,dict) and a.get('id')==identificador]
    if len(items)!=1:
        raise ValueError('ID no encontrado o duplicado')
    item=items[0]
    caduca=item.get('caduca_utc')
    if not caduca:
        raise ValueError('Aviso sin caducidad verificable: no se puede aprobar')
    limite=dt.datetime.fromisoformat(caduca.replace('Z','+00:00'))
    if limite<=ahora:
        raise ValueError('Aviso caducado; no se puede aprobar')
    if item.get('categoria') != 'boletin_informativo' or not item.get('fuente_url','').startswith('https://geosinpad.indeci.gob.pe/'):
        raise ValueError('Este revisor solo aprueba borradores oficiales de GeoSINPAD')
    item['aprobado']=True
    item['aprobado_por']=revisor.strip()
    item['verificado_el_utc']=ahora.replace(microsecond=0).isoformat().replace('+00:00','Z')
    return item


def main():
    a=argparse.ArgumentParser()
    a.add_argument('--list',action='store_true')
    a.add_argument('--aprobar',metavar='ID')
    a.add_argument('--revisor')
    a.add_argument('--confirmo-fuente',action='store_true')
    a.add_argument('--cola',default=str(COLA))
    args=a.parse_args()
    ruta=pathlib.Path(args.cola)
    if not ruta.exists():
        print('Todavía no hay avisos pendientes; ejecuta el detector.')
        return 0
    cola=json.loads(ruta.read_text(encoding='utf-8-sig'))
    if not isinstance(cola,list):
        raise SystemExit('Cola inválida')
    if args.aprobar:
        item=revisar(cola,args.aprobar,args.revisor or '',args.confirmo_fuente)
        guardar_atomicamente(ruta,cola)
        print('APROBADO:',item['id'], '| región:',item['region'],'| caduca:',item['caduca_utc'])
        print('No se envió ninguna notificación. La distribución FCM es una acción separada.')
        return 0
    print('AVISOS REGISTRADOS:',len(cola))
    for x in cola[-30:]:
        if not isinstance(x,dict): continue
        print('\nID:',x.get('id'),'| región:',x.get('region'),'| aprobado:',x.get('aprobado'))
        print('Fuente fecha:',x.get('fecha_fuente_utc'),'| caduca:',x.get('caduca_utc'))
        print('Título:',x.get('titulo'))
        print('Fuente:',x.get('fuente_url'))
    print('\nPara enviar, comprobar antes que el aviso esté vigente, publicado y realmente corresponda a esa región.')
    return 0

if __name__=='__main__':
    try: sys.exit(main())
    except Exception as e: print('ERROR:',e,file=sys.stderr);sys.exit(1)
