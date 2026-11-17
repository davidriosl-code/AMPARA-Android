# EcoResilia · Cierre de notificaciones (fase de monitoreo)

## Estado comprobado por el usuario
- FCM al token individual: recibido en emulador.
- Tema regional `ecoresilia_region_arequipa`: recibido en emulador.
- Los avisos en Android y Firestore/Authentication funcionan.

## Qué añade este instalador
- Consulta lectura pública a la capa GeoSINPAD de SENAMHI sobre posible activación de quebradas en 24 horas.
- Elabora borradores según departamento y fecha de origen, con detalle de distrito/provincia cuando está disponible.
- Descarta filas sin región, sin fecha y con fecha mayor a 36 horas.
- Evita repetir el mismo registro en `avisos_pendientes.json` por identificador.
- No publica ni envía mensajes; necesita revisión humana y credencial de servidor.
- Puede programar **solo la consulta** cada 30 minutos en el Programador de tareas de Windows.

## Probar el detector ahora

```powershell
py -3 D:\EcoResiliaFirebase\herramientas\alertas\detectar_avisos_oficiales.py
py -3 D:\EcoResiliaFirebase\herramientas\alertas\revisar_avisos.py --list
```

Puede que no existan avisos recientes; `0 borradores` no es un fallo si el servicio responde normalmente.

## Revisión antes del envío
1. Leer el registro y abrir el enlace fuente; comprobar geografía, fechas, nivel y vigencia en el comunicado original.
2. Solo si se verificó, aprobar mediante `revisar_avisos.py --aprobar <ID> --revisor <nombre> --confirmo-fuente`.
3. Configurar un entorno seguro para el **Firebase Admin SDK**, con `GOOGLE_APPLICATION_CREDENTIALS` apuntando a JSON de cuenta de servicio en una carpeta privada fuera del proyecto. No compartirlo, subirlo a GitHub ni incluirlo en ZIP.
4. Para ver qué se enviaría: `py -3 D:\EcoResiliaFirebase\herramientas\alertas\publicar_avisos_verificados.py`.
5. Para enviarlo **solo después de verificar**: `py -3 D:\EcoResiliaFirebase\herramientas\alertas\publicar_avisos_verificados.py --send`. El publicador comprueba nuevamente la vigencia, aprobación y fecha de revisión, y utiliza `estado_envios.sqlite` para no reenviar el mismo ID.

## Qué queda pendiente para operación completa
- Credencial de cuenta de servicio y prueba de emisión real usando un entorno servidor de confianza.
- Programar el envío supervisado solo cuando el sistema de revisión esté operativo; el publicador valida caducidad y aprobación justo antes de publicar.
- Ejecutar el monitor en un servidor 24/7 si se desea disponibilidad continua (en PC solo funciona cuando está encendida).
- Incorporar capas/fuentes adicionales y validar el mapeo a distritos; por ahora los temas FCM son solo por departamento.
- Para una notificación realmente urgente hay que confirmar la autoridad competente. EcoResilia no suplanta alertas oficiales.

## Observación
El servicio oficial es de terceros y puede cambiar campos, disponibilidad o cobertura. Esta integración no estima peligro mediante IA ni asigna nivel de riesgo propio.
