#!/usr/bin/env python3
# EcoResilia: gestion local y envio FCM de boletines verificados.
# Standard library; Firebase Admin SDK only needed for explicit sending.
from __future__ import annotations
import datetime as dt
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request

PROJECT = Path(os.environ.get('ECORESILIA_PROJECT', r'D:\EcoResiliaFirebase'))
DIR = PROJECT / 'herramientas' / 'alertas'
QUEUE = DIR / 'avisos_pendientes.json'
DB = DIR / 'estado_envios.sqlite'  # Share history with original emitter
CONFIG = Path(r'D:\EcoResiliaPrivado\config_alertas.json') if os.name == 'nt' else Path.home()/'.ecoresilia_config_alertas.json'
PRIVADO = CONFIG.parent
OFFICIAL = 'https://geosinpad.indeci.gob.pe/indeci/rest/services/Ent_Tecnico_Cientificas/SENAMHI/MapServer/2/query'
REGIONS = set('amazonas ancash apurimac arequipa ayacucho cajamarca callao cusco huancavelica huanuco ica junin la_libertad lambayeque lima loreto madre_de_dios moquegua pasco piura puno san_martin tacna tumbes ucayali'.split())
UTC = dt.timezone.utc
MAX_AGE_HOURS = 36
APPROVAL_TTL_HOURS = 2


def now():
    return dt.datetime.now(UTC)


def parse_time(v):
    if not isinstance(v,str) or not v: return None
    try:
        t=dt.datetime.fromisoformat(v.replace('Z','+00:00'))
        return t.astimezone(UTC) if t.tzinfo else None
    except (ValueError,OverflowError): return None


def iso(t=None):
    return (t or now()).replace(microsecond=0).astimezone(UTC).isoformat().replace('+00:00','Z')


def atomic_json(dest,data):
    dest.parent.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix='.eco-',suffix='.tmp',dir=dest.parent)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as f:
            json.dump(data,f,indent=2,ensure_ascii=False);f.write('\n');f.flush();os.fsync(f.fileno())
        os.replace(tmp,dest)
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def read_queue():
    if not QUEUE.is_file(): raise RuntimeError(f'No existe la cola {QUEUE}; ejecuta primero el monitor ya instalado')
    data=json.loads(QUEUE.read_text(encoding='utf-8-sig'))
    if not isinstance(data,list): raise RuntimeError('La cola debe ser un arreglo JSON')
    return data


def app_project_id():
    p=PROJECT/'app'/'google-services.json'
    if not p.is_file(): raise RuntimeError('Falta app/google-services.json; no es seguro continuar')
    return str(json.loads(p.read_text(encoding='utf-8-sig'))['project_info']['project_id'])


def official_object_id(n):
    # Strictly pin original source URL to trusted GeoSINPAD service and one integer OBJECTID
    url=n.get('fuente_url','')
    try: parsed=urllib.parse.urlsplit(url)
    except ValueError: return None
    if (parsed.scheme!='https' or parsed.hostname!='geosinpad.indeci.gob.pe' or
        parsed.path.rstrip('/')!=urllib.parse.urlsplit(OFFICIAL).path): return None
    q=urllib.parse.parse_qs(parsed.query)
    wheres=q.get('where',[])
    if len(wheres)!=1: return None
    m=re.fullmatch(r'OBJECTID\s*=\s*(\d{1,9})',wheres[0],re.I)
    return m.group(1) if m else None


def notice_ok(n, t=None, need_approval=False):
    t=t or now()
    if not isinstance(n,dict): return 'registro inválido'
    uid=n.get('id','')
    if not isinstance(uid,str) or not re.fullmatch(r'senamhi_q_[0-9a-f]{18}',uid): return 'simulacro/no es registro oficial del detector'
    if n.get('categoria')!='boletin_informativo' or n.get('tipo_registro')!='posible_activacion_quebradas_24h': return 'tipo de registro no autorizado'
    if n.get('region') not in REGIONS: return 'departamento ausente o no reconocido'
    if not official_object_id(n): return 'no hay enlace individual al registro oficial'
    for field in ('lugar_detalle','nivel_fuente','titulo','mensaje'):
        if not isinstance(n.get(field),str) or len(n[field].strip())<4: return f'falta {field}'
    if len([x for x in n['lugar_detalle'].split(',') if x.strip()])<3:
        return 'ubicación demasiado general: falta distrito o provincia'
    if not (5<=len(n['titulo'])<=85 and 15<=len(n['mensaje'])<=350): return 'longitud de mensaje inválida'
    fecha=parse_time(n.get('fecha_fuente_utc'))
    exp=parse_time(n.get('caduca_utc'))
    if not fecha or not exp: return 'fechas incompletas'
    if not (fecha<=t+dt.timedelta(minutes=15) and fecha>=t-dt.timedelta(hours=MAX_AGE_HOURS)):
        return 'fuente antigua o con fecha futura'
    if not (t<exp<=fecha+dt.timedelta(hours=MAX_AGE_HOURS,minutes=1)):
        return 'aviso caducado o caducidad incompatible'
    if need_approval:
        if n.get('aprobado') is not True or not n.get('aprobado_por'): return 'sin aprobación'
        verified=parse_time(n.get('verificado_el_utc'))
        if not verified or verified>t+dt.timedelta(minutes=1) or t-verified>dt.timedelta(hours=APPROVAL_TTL_HOURS):
            return 'revisión caducada; volver a verificar'
        if n.get('revision_hash')!=fingerprint(n):return 'datos modificados después de la aprobación'
    return None


def fingerprint(n):
    keys=['id','region','titulo','mensaje','fuente_url','fecha_fuente_utc','caduca_utc','lugar_detalle','nivel_fuente','tipo_registro']
    b=json.dumps({k:n.get(k) for k in keys},sort_keys=True,ensure_ascii=False).encode('utf-8')
    return hashlib.sha256(b).hexdigest()


def fetch_official(n):
    oid=official_object_id(n)
    if not oid:raise RuntimeError('El enlace de origen no es válido')
    q=urllib.parse.urlencode({'where':f'OBJECTID={oid}', 'outFields':'OBJECTID,NOMBDEP,NOMBPROV,NOMBDIST,NIVEL,FECHA', 'returnGeometry':'false','f':'json'})
    req=urllib.request.Request(OFFICIAL+'?'+q,headers={'User-Agent':'EcoResilia-Verificador/1.0','Accept':'application/json'})
    with urllib.request.urlopen(req,timeout=20) as response:
        data=response.read(100_001)
    if len(data)>100_000:raise RuntimeError('Respuesta oficial demasiado grande')
    obj=json.loads(data)
    features=obj.get('features')
    if obj.get('error') or not isinstance(features,list) or len(features)!=1:
        raise RuntimeError('Fuente oficial no devolvió exactamente un registro')
    a=features[0].get('attributes') or {}
    if str(a.get('OBJECTID'))!=oid: raise RuntimeError('El identificador ya no coincide con la fuente')
    # Match exact place and priority as the monitor originally rendered.
    expected=', '.join(' '.join(str(a.get(k) or '').split())[:45] for k in ('NOMBDIST','NOMBPROV','NOMBDEP') if a.get(k))
    if expected.casefold()!=n['lugar_detalle'].casefold():raise RuntimeError(f'Ubicación cambió: fuente={expected}; cola={n["lugar_detalle"]}')
    nivel=' '.join(str(a.get('NIVEL') or '').split())[:30]
    if nivel.casefold()!=n['nivel_fuente'].casefold():raise RuntimeError('Nivel cambió en la fuente; revisa manualmente')
    valor=a.get('FECHA')
    if not isinstance(valor,(int,float)) or valor<100000000000:raise RuntimeError('Fecha oficial no reconocida')
    when=dt.datetime.fromtimestamp(valor/1000,UTC)
    original=parse_time(n['fecha_fuente_utc'])
    if not original or abs((when-original).total_seconds())>60:
        raise RuntimeError('Fecha cambió en la fuente; revisar actualización')
    return a


def choose(nitems,pred,qty=8):
    arr=[n for n in nitems if isinstance(n,dict) and pred(n)]
    for n in arr[:qty]:
        print(' ',n.get('id'),'|',n.get('region'),'|',n.get('lugar_detalle','')[:50], '|',n.get('nivel_fuente',''), '|',n.get('caduca_utc',''))
    if len(arr)>qty:print(' ...',len(arr)-qty,'más')
    return arr


def state():
    q=read_queue(); t=now()
    by={}
    for n in q:
        if isinstance(n,dict):by[n.get('region','?')]=by.get(n.get('region','?'),0)+1
    valid=[n for n in q if notice_ok(n,t) is None]
    app=[n for n in valid if notice_ok(n,t,True) is None]
    print('EcoResilia | Gestor de avisos verificados')
    print('Proyecto Firebase:', app_project_id()); print('Total de registros históricos:',len(q))
    print('Vigentes, completos y con enlace oficial:',len(valid))
    print('Aprobados y aún vigentes para enviar:',len(app))
    print('Arequipa reales vigentes:',sum(n.get('region')=='arequipa' for n in valid))
    print('Simulacros/demostraciones (bloqueados):',sum(str(n.get('id','')).startswith('demo') for n in q if isinstance(n,dict)))
    print('REGIONES CON REGISTROS:',', '.join(f'{k}:{v}' for k,v in sorted(by.items())))
    print('Primeros válidos (si existen):'); choose(valid,lambda n:True,5)


def by_id(uid):
    q=read_queue(); matches=[n for n in q if isinstance(n,dict) and n.get('id')==uid]
    if len(matches)!=1: raise RuntimeError('ID ausente o repetido; no se puede usar')
    return q,matches[0]


def inspect(uid):
    _,n=by_id(uid)
    for k in ('id','categoria','region','titulo','mensaje','lugar_detalle','nivel_fuente','fecha_fuente_utc','caduca_utc','fuente_url','aprobado','aprobado_por','verificado_el_utc'):
        print(f'{k:20}',n.get(k))
    print('ELEGIBILIDAD:',notice_ok(n) or 'VIGENTE Y COMPLETO')
    return n


def approval(uid):
    q,n=by_id(uid)
    err=notice_ok(n)
    if err:raise RuntimeError(f'No se aprueba: {err}')
    print('\nREVISIÓN:',uid)
    inspect(uid)
    print('\nContrastando fecha, distrito y nivel con GeoSINPAD...')
    fetch_official(n)
    print('Verificación técnica de campos: correcta.')
    print('IMPORTANTE: abre el enlace de origen y comprueba su contexto y vigencia antes de aprobar.')
    yes=input(f'Escribe APROBAR {uid} si revisaste la fuente y deseas habilitar su publicación: ').strip()
    if yes!=f'APROBAR {uid}':print('Cancelado.');return
    reviewer=input('Tu nombre como responsable de revisión: ').strip()
    if len(reviewer)<3:raise RuntimeError('Indica el nombre del responsable (mínimo 3 caracteres)')
    n['aprobado']=True;n['aprobado_por']=reviewer
    n['verificado_el_utc']=iso()
    n['revision_hash']=fingerprint(n)
    backup=QUEUE.with_name('avisos_respaldo_antes_revision_'+now().strftime('%Y%m%d_%H%M%S')+'.json')
    shutil.copy2(QUEUE,backup)
    atomic_json(QUEUE,q)
    print('APROBADO solo para publicación controlada:',uid)
    print('No se enviaron notificaciones. Respaldo:',backup)


def check_config():
    if not CONFIG.is_file(): raise RuntimeError('No hay cuenta de servicio configurada. En el menú utiliza opción 5.')
    cfg=json.loads(CONFIG.read_text(encoding='utf-8'))
    key=Path(cfg.get('cuenta_servicio',''))
    if not key.is_file():raise RuntimeError('Archivo de cuenta de servicio no encontrado')
    data=json.loads(key.read_text(encoding='utf-8-sig'))
    if data.get('type')!='service_account' or data.get('project_id')!=app_project_id() or not data.get('private_key'):
        raise RuntimeError('La cuenta de servicio no coincide con EcoResilia o no es una clave de servicio')
    return key


def configure_key():
    print('Firebase Console > Configuración del proyecto > Cuentas de servicio > Generar clave privada.')
    print('El archivo NO es app/google-services.json. No lo compartas ni lo subas al repositorio.')
    original=input('Ruta completa del JSON de cuenta de servicio en D: (o Enter para cancelar): ').strip().strip('"')
    if not original:return
    source=Path(original)
    if not source.is_file():raise RuntimeError('No se encontró el archivo indicado')
    raw=json.loads(source.read_text(encoding='utf-8-sig'))
    if raw.get('type')!='service_account' or raw.get('project_id')!=app_project_id() or not raw.get('private_key'):
        raise RuntimeError('No es una cuenta de servicio del proyecto Firebase EcoResilia')
    PRIVADO.mkdir(parents=True,exist_ok=True)
    target=PRIVADO/'firebase-admin.json'
    if source.resolve()!=target.resolve():
        if target.exists():raise RuntimeError(f'Ya existe {target}; se conserva, no se reemplaza')
        shutil.copy2(source,target)
    atomic_json(CONFIG,{'cuenta_servicio':str(target)})
    print('Configuración privada guardada en',CONFIG)
    print('Puedes borrar manualmente la copia de Descargas cuando confirmes que todo funciona.')
    print('Nunca coloques este archivo de servicio dentro de app o GitHub.')


def firebase_modules(unattended=False):
    try:
        import firebase_admin
        from firebase_admin import messaging
        return firebase_admin,messaging
    except ImportError:
        deps=DIR/'firebase_deps'
        sys.path.insert(0,str(deps))
        try:
            import firebase_admin
            from firebase_admin import messaging
            return firebase_admin,messaging
        except ImportError:
            if unattended: raise RuntimeError('Falta firebase-admin: configura la opción 5 y prueba desde el menú antes de programar envíos')
            print('Falta Firebase Admin SDK. No instala nada sin tu autorización.')
            answer=input('¿Instalar firebase-admin solo en D: (S/N)? ').strip().lower()
            if answer!='s':raise RuntimeError('Instalación cancelada')
            deps.mkdir(parents=True,exist_ok=True)
            env=os.environ.copy()
            tmp=PROJECT/'herramientas'/'temp_pip'; tmp.mkdir(parents=True,exist_ok=True)
            env['TMP']=str(tmp);env['TEMP']=str(tmp);env['TMPDIR']=str(tmp)
            cmd=[sys.executable,'-m','pip','install','--no-cache-dir','--target',str(deps),'firebase-admin']
            res=subprocess.run(cmd,env=env)
            if res.returncode:raise RuntimeError('No se pudo instalar Firebase Admin; revisa el mensaje de pip')
            import importlib
            importlib.invalidate_caches()
            import firebase_admin
            from firebase_admin import messaging
            return firebase_admin,messaging


def db_connect():
    DIR.mkdir(parents=True,exist_ok=True)
    con=sqlite3.connect(DB,timeout=25,isolation_level=None)
    con.execute('PRAGMA busy_timeout=25000')
    con.execute('CREATE TABLE IF NOT EXISTS sent (id TEXT PRIMARY KEY, sent_at TEXT NOT NULL, firebase_id TEXT NOT NULL)')
    con.execute('''CREATE TABLE IF NOT EXISTS eco_secure_attempts (
        id TEXT PRIMARY KEY, started_at TEXT NOT NULL, status TEXT NOT NULL,
        region TEXT NOT NULL, response TEXT NOT NULL DEFAULT '')''')
    return con


def prep_send(uid):
    q,n=by_id(uid)
    err=notice_ok(n,need_approval=True)
    if err:raise RuntimeError(f'No autorizado: {err}')
    # check source again immediately before sending
    fetch_official(n)
    return n


def send_one(uid, enable=False, unattended=False):
    n=prep_send(uid)
    title=('Información preventiva · '+n['titulo'])[:110]
    print('\nDESTINATARIOS: todos los dispositivos del tema ecoresilia_region_'+n['region'])
    print('TÍTULO:',title)
    print('MENSAJE:',n['mensaje'])
    print('FUENTE:',n['fuente_url'])
    con=db_connect()
    try:
        row=con.execute('SELECT status FROM eco_secure_attempts WHERE id=?',(uid,)).fetchone()
        if row:raise RuntimeError('Este aviso ya tiene un intento registrado ('+row[0]+'); no se reenvía automáticamente')
        if con.execute('SELECT 1 FROM sent WHERE id=?',(uid,)).fetchone():
            raise RuntimeError('Aviso ya registrado como enviado por el emisor anterior')
        if not enable:
            print('PREVISUALIZACIÓN COMPLETA. No se ha enviado.');return False
        key=check_config()
        if not unattended:
            answer=input(f'Escribe ENVIAR {uid} para publicar a toda la región ({n["region"]}): ').strip()
            if answer != f'ENVIAR {uid}': print('Envío cancelado.');return False
        admin,messaging=firebase_modules(unattended=unattended)
        os.environ['GOOGLE_APPLICATION_CREDENTIALS']=str(key)
        if not admin._apps:admin.initialize_app()
        con.execute('BEGIN IMMEDIATE')
        if con.execute('SELECT id FROM eco_secure_attempts WHERE id=?',(uid,)).fetchone() or con.execute('SELECT id FROM sent WHERE id=?',(uid,)).fetchone():
            con.execute('ROLLBACK');raise RuntimeError('Otro proceso ya inició este envío')
        con.execute('INSERT INTO eco_secure_attempts (id,started_at,status,region,response) VALUES (?,?,?,?,?)',(uid,iso(),'iniciado',n['region'],''))
        con.execute('COMMIT')  # Reserve before external API call; crash leaves status iniciado, no automatic retry
        message=messaging.Message(
            notification=messaging.Notification(title=title,body=n['mensaje']),
            data={'category':'boletin_informativo','notice_id':uid,'region':n['region'],'source_url':n['fuente_url']},
            topic='ecoresilia_region_'+n['region'],
            android=messaging.AndroidConfig(priority='normal',ttl=dt.timedelta(hours=2))
        )
        try:
            result=messaging.send(message)
        except Exception as exc:
            con.execute('UPDATE eco_secure_attempts SET status=?,response=? WHERE id=?',('incierto',type(exc).__name__+': '+str(exc)[:250],uid))
            raise RuntimeError('No se pudo confirmar el envío; quedó INCIERTO y NO se reintentará solo. '+str(exc))
        con.execute('UPDATE eco_secure_attempts SET status=?,response=? WHERE id=?',('enviado',result,uid))
        con.execute('INSERT OR IGNORE INTO sent (id,sent_at,firebase_id) VALUES (?,?,?)',(uid,iso(),result))
        print('FCM ACEPTÓ EL MENSAJE para el tema regional. Message ID:',result)
        print('El servidor aceptó el envío; la entrega a cada dispositivo no está garantizada.')
        return True
    finally:
        con.close()


def test_token():
    print('Prueba SOLO a un dispositivo, no por región. Nunca usa un boletín real.')
    key=check_config()
    token=getpass.getpass('Pega el token FCM del emulador (entrada oculta): ').strip()
    if len(token)<80:raise RuntimeError('Token FCM ausente o demasiado corto')
    if input('Escribe PRUEBA para enviar al token individual: ').strip()!='PRUEBA':
        print('Cancelado.');return
    admin,messaging=firebase_modules();os.environ['GOOGLE_APPLICATION_CREDENTIALS']=str(key)
    if not admin._apps:admin.initialize_app()
    response=messaging.send(messaging.Message(
        token=token,
        notification=messaging.Notification(title='EcoResilia · SIMULACRO TÉCNICO',body='Prueba de comunicación desde el emisor seguro. No existe una emergencia real.'),
        data={'category':'simulacro','region':'test','source':'ecoresilia-local'},
        android=messaging.AndroidConfig(priority='normal',ttl=dt.timedelta(minutes=20))
    ))
    print('FCM aceptó el mensaje individual:',response)



def dispatch_approved():
    """Schedule-safe: only a *previously human-approved* item, at most one per run.
    Does not approve, rewrite or generate alerts; it re-verifies the live source.
    """
    if not CONFIG.exists():
        print('No hay Firebase Admin configurado. Sin envío.')
        return 0
    with db_connect() as con:
        items=con.execute("SELECT id,region FROM eco_secure_attempts WHERE status IN ('iniciado','incierto','enviado')").fetchall()
        attempts={x[0] for x in items}
        sent={x[0] for x in con.execute('SELECT id FROM sent')}
        # Prevent flooding a region with multiple separate approved notices.
        last=con.execute("SELECT region,MAX(started_at) FROM eco_secure_attempts GROUP BY region").fetchall()
        recent_regions={r for r,v in last if parse_time(v) and now()-parse_time(v)<dt.timedelta(hours=3)}
    candidates=[n for n in read_queue() if isinstance(n,dict) and notice_ok(n,need_approval=True) is None
                and n['id'] not in attempts and n['id'] not in sent and n['region'] not in recent_regions]
    candidates.sort(key=lambda n:n.get('fecha_fuente_utc',''),reverse=True)
    if not candidates:
        print('Sin boletines aprobados y vigentes para publicar. Sin envío.')
        return 0
    # One per run; after 3 hours per region. No batch, no demonstrations.
    uid=candidates[0]['id']
    print('PUBLICACIÓN PROGRAMADA DE BOLETÍN YA APROBADO:',uid)
    try:
        send_one(uid,True,unattended=True)
        return 0
    except Exception as exc:
        print('ERROR EN DESPACHO PROGRAMADO (sin reintento automático):',exc,file=sys.stderr)
        return 1


def schedule_dispatch():
    if os.name!='nt': raise RuntimeError('Programador de tareas solo disponible en Windows')
    # Prerequisites must work before creating an ongoing task.
    check_config()
    firebase_modules(unattended=False)
    prepare()
    script=DIR/'ECORE_GESTOR_NOTIFICACIONES_FINAL.py'
    cmd=DIR/'ECORE_DESPACHO_APROBADOS.cmd'
    logfile=DIR/'despachos_autorizados.log'
    lines='@echo off\r\n'+('"'+str(sys.executable)+'" "'+str(script)+'" --despachar-aprobados >> "'+str(logfile)+'" 2>&1\r\n')
    cmd.write_bytes(lines.encode('utf-8'))
    print('Una tarea consultará cada 10 minutos SOLO los boletines aprobados expresamente y revisados hace <2 h.')
    print('Se enviará como máximo un aviso por ciclo, con pausa de 3 horas entre avisos de una región.')
    print('No se enviará NINGÚN aviso si falta aprobación, si está caducado o si cambia la fuente.')
    if input('Escribe PROGRAMAR para activar la tarea de envío regional: ').strip()!='PROGRAMAR':
        print('Cancelado: archivo CMD preparado, pero tarea NO creada.');return
    r=subprocess.run(['schtasks','/Create','/F','/TN','EcoResilia-FCM-Verificados',
                      '/TR','cmd.exe /d /c "'+str(cmd)+'"','/SC','MINUTE','/MO','10'],capture_output=True,text=True)
    if r.returncode:raise RuntimeError('Windows no pudo programar la tarea: '+r.stderr.strip())
    print('PROGRAMACIÓN ACTIVA. No aprueba alertas. Para desactivarla:')
    print('schtasks /Delete /TN EcoResilia-FCM-Verificados /F')


def remove_schedule():
    if os.name!='nt':raise RuntimeError('Solo Windows')
    r=subprocess.run(['schtasks','/Delete','/TN','EcoResilia-FCM-Verificados','/F'],capture_output=True,text=True)
    print(r.stdout.strip() if r.returncode==0 else r.stderr.strip())

def prepare():
    if not (PROJECT/'gradlew.bat').is_file():raise RuntimeError('No se encontró el proyecto EcoResilia en '+str(PROJECT))
    if not (DIR/'detectar_avisos_oficiales.py').is_file():raise RuntimeError('Falta el monitor de boletines instalado')
    if not QUEUE.exists():raise RuntimeError('No se encontró avisos_pendientes.json')
    app_project_id()
    home=DIR/'ECORE_GESTOR_NOTIFICACIONES_FINAL.py'
    if Path(__file__).resolve()!=home.resolve():
        if home.exists() and hashlib.sha256(home.read_bytes()).digest()!=hashlib.sha256(Path(__file__).read_bytes()).digest():
            backup=DIR/('ECORE_GESTOR_ANTERIOR_'+now().strftime('%Y%m%d_%H%M%S')+'.py')
            shutil.copy2(home,backup)
            print('Gestor anterior respaldado:',backup)
        shutil.copy2(Path(__file__),home)
    print('Gestor listo en:',home)
    print('No se modificaron Android, Firebase, reportes ni la cola de borradores.')


def menu():
    prepare()
    while True:
        print('\n'+'='*56)
        print(' ECORESILIA | NOTIFICACIONES SEGURAS (SIN ENVÍO AUTOMÁTICO)')
        print(' 1  Ver estado de la cola')
        print(' 2  Ver avisos reales vigentes')
        print(' 3  Examinar aviso y fuente por ID')
        print(' 4  Revisar y aprobar UN aviso oficial')
        print(' 5  Configurar cuenta Firebase Admin (solo en D:)')
        print(' 6  Enviar simulacro al token de mi emulador')
        print(' 7  Vista previa de UN aviso aprobado (sin enviar)')
        print(' 8  Publicar UN aviso aprobado (envío real regional)')
        print(' 9  Programar publicaciones de avisos revisados (opcional)')
        print(' 10 Desactivar publicaciones programadas')
        print(' 0  Salir')
        v=input('Opción: ').strip()
        try:
            if v=='0': print('Cerrado, ningún envío pendiente en segundo plano.');break
            elif v=='1':state()
            elif v=='2':
                valid=choose(read_queue(),lambda n:notice_ok(n) is None,20)
                print('Total válidos:',len(valid))
            elif v=='3':inspect(input('ID: ').strip())
            elif v=='4':approval(input('ID de boletín que revisaste: ').strip())
            elif v=='5':configure_key()
            elif v=='6':test_token()
            elif v=='7':send_one(input('ID aprobado: ').strip(),False)
            elif v=='8':send_one(input('ID aprobado: ').strip(),True)
            elif v=='9':schedule_dispatch()
            elif v=='10':remove_schedule()
            else: print('Opción no reconocida')
        except (KeyboardInterrupt,EOFError):print('\nOperación cancelada.');break
        except Exception as exc:print('NO SE REALIZÓ LA ACCIÓN:',exc)

if __name__=='__main__':
    try:
        if len(sys.argv)>1 and sys.argv[1]=='--estado':
            prepare();state()
        elif len(sys.argv)>1 and sys.argv[1]=='--despachar-aprobados':
            sys.exit(dispatch_approved())
        else:menu()
    except Exception as exc:
        print('ERROR CONTROLADO:',exc,file=sys.stderr)
        sys.exit(1)
