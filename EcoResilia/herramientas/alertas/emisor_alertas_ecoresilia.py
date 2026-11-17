#!/usr/bin/env python3
"""Emisor LOCAL FCM de comunicados aprobados y programados por departamento.

MODO SEGURO POR DEFECTO: solo valida, no envía.
Para envío: py -3 emisor_alertas_ecoresilia.py --send
Se ejecuta cuando se llama: no requiere Cloud Functions ni Blaze.
Requiere: python -m pip install firebase-admin (instalar solo en la PC/servidor de confianza).
Credencial: variable GOOGLE_APPLICATION_CREDENTIALS apuntando a JSON de cuenta de SERVICIO,
  NUNCA app/google-services.json. Guardar fuera del código y nunca subir a GitHub.
"""
import argparse
import datetime as dt
import json
import os
import pathlib
import sqlite3
import sys
import urllib.parse

BASE = pathlib.Path(__file__).resolve().parent
PENDIENTES = BASE / "avisos_pendientes.json"
ESTADO = BASE / "estado_envios.sqlite"
VALID_REGIONS = set("amazonas ancash apurimac arequipa ayacucho cajamarca callao cusco huancavelica huanuco ica junin la_libertad lambayeque lima loreto madre_de_dios moquegua pasco piura puno san_martin tacna tumbes ucayali".split())
TRUSTED = ("senamhi.gob.pe", "indeci.gob.pe", "cenepred.gob.pe", "gob.pe", "sigrid.cenepred.gob.pe")

def validate(notice):
    if not isinstance(notice,dict): raise ValueError("cada aviso debe ser un objeto JSON")
    uid = notice.get("id", "")
    if not isinstance(uid,str) or not (3 <= len(uid) <= 90) or not all(c.isalnum() or c in "_-" for c in uid):
        raise ValueError("id inválido")
    region = notice.get("region", "")
    if region not in VALID_REGIONS: raise ValueError(f"región inválida: {region}")
    if notice.get("aprobado") is not True: raise ValueError("aviso sin aprobación explícita")
    if not isinstance(notice.get("aprobado_por"),str) or len(notice["aprobado_por"].strip()) < 3:
        raise ValueError("falta aprobador")
    title,body = notice.get("titulo"),notice.get("mensaje")
    if not isinstance(title,str) or not (5<=len(title.strip())<=85): raise ValueError("título inválido")
    if not isinstance(body,str) or not (15<=len(body.strip())<=350): raise ValueError("mensaje inválido")
    category = notice.get("categoria")
    if category not in ("simulacro", "boletin_informativo"):
        raise ValueError("categoría no autorizada: solo simulacro o boletin_informativo")
    if category == "boletin_informativo":
        url=notice.get("fuente_url", "")
        try: parsed=urllib.parse.urlparse(url)
        except ValueError: raise ValueError("enlace de fuente inválido")
        host=(parsed.hostname or "").lower()
        if parsed.scheme != "https" or not any(host==d or host.endswith("."+d) for d in TRUSTED):
            raise ValueError("fuente institucional HTTPS no permitida")
    elif notice.get("fuente_url"):
        raise ValueError("simulacros no deberían enlazar a un aviso oficial")
    when=notice.get("programado_utc")
    if not isinstance(when,str): raise ValueError("programado_utc requerido")
    try: value=dt.datetime.fromisoformat(when.replace("Z", "+00:00"))
    except ValueError: raise ValueError("programado_utc no es ISO 8601")
    if value.tzinfo is None: raise ValueError("programado_utc debe incluir Z o zona")
    return value.astimezone(dt.timezone.utc)

def main():
    cli=argparse.ArgumentParser(description="EcoResilia: avisos por región, no emergencias automáticas")
    cli.add_argument("--send", action="store_true",help="Autorizar entrega real de avisos aprobados")
    args=cli.parse_args()
    if not PENDIENTES.exists():
        raise SystemExit(f"No existe {PENDIENTES}")
    notices=json.loads(PENDIENTES.read_text(encoding="utf-8-sig"))
    if not isinstance(notices,list): raise SystemExit("avisos_pendientes.json debe contener una lista")
    con=sqlite3.connect(ESTADO) if args.send else None
    if con:
        con.execute("CREATE TABLE IF NOT EXISTS sent (id TEXT PRIMARY KEY, sent_at TEXT NOT NULL, firebase_id TEXT NOT NULL)")
        con.commit()
    now=dt.datetime.now(dt.timezone.utc)
    seen=set()
    eligible=[]
    for notice in notices:
        try: when=validate(notice)
        except ValueError as exc:
            print("OMITIDO",notice.get("id", "sin-id") if isinstance(notice,dict) else "sin-id", "-",exc)
            continue
        if notice["id"] in seen:
            print("OMITIDO", notice["id"], "- ID duplicado")
            continue
        seen.add(notice["id"])
        if when>now:
            print("PROGRAMADO PARA MÁS ADELANTE",notice["id"],when.isoformat())
            continue
        if con and con.execute("SELECT 1 FROM sent WHERE id=?",(notice["id"],)).fetchone():
            print("YA ENVIADO",notice["id"])
            continue
        eligible.append(notice)
    print("Avisos elegibles:",len(eligible))
    if not args.send:
        for n in eligible: print("VISTA PREVIA",n["id"],n["region"],n["titulo"])
        print("MODO SEGURO: no se enviaron notificaciones. Para autorizar: --send")
        return 0
    if not eligible:
        print("No hay avisos nuevos que enviar.")
        return 0
    credentials=os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "")
    if not credentials or not pathlib.Path(credentials).is_file():
        raise SystemExit("Falta GOOGLE_APPLICATION_CREDENTIALS con el JSON de CUENTA DE SERVICIO (no google-services.json).")
    try:
        import firebase_admin
        from firebase_admin import messaging
        if not firebase_admin._apps: firebase_admin.initialize_app()
    except Exception as exc:
        raise SystemExit("No se pudo iniciar Firebase Admin; configura el SDK y permisos: " + str(exc))
    for n in eligible:
        prefix = "SIMULACRO · " if n["categoria"]=="simulacro" else "Información preventiva · "
        message=messaging.Message(
            notification=messaging.Notification(title=prefix+n["titulo"],body=n["mensaje"]),
            data={"category":n["categoria"],"source_url":n.get("fuente_url","")},
            topic="ecoresilia_region_"+n["region"],
            android=messaging.AndroidConfig(priority="normal")
        )
        try:
            result=messaging.send(message)
        except Exception as exc:
            print("FALLO DE ENVÍO",n["id"],str(exc),file=sys.stderr)
            continue
        # Solo se registra como enviado tras recibir ID de FCM; posibles duplicados en fallos extremos de escritura local.
        con.execute("INSERT OR IGNORE INTO sent (id,sent_at,firebase_id) VALUES (?,?,?)",
                    (n["id"],dt.datetime.now(dt.timezone.utc).isoformat(),result))
        con.commit()
        print("ENVIADO",n["id"],"tema",n["region"],"ID-FCM",result)
    return 0

if __name__=="__main__": sys.exit(main())
