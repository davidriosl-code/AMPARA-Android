# EcoResilia — Avisos informativos regionales

## Qué añade

- En **Avisos**, una tarjeta **Avisos informativos por región** con selector de los 25 departamentos peruanos, activación, cambio y baja de suscripción FCM.
- La app **no solicita GPS** ni publica la ubicación. Las preferencias son voluntarias por departamento y se guardan localmente.
- El emisor `emisor_alertas_ecoresilia.py` permite programar mensajes aprobados para una región, con historial SQLite para no repetir envíos cuando vuelve a ejecutarse.
- **No hay mensajes automáticos en producción hasta que configures un emisor seguro y lo ejecutes.** El instalador no envía nada y no solicita credenciales.

## Aplicación Android

En PowerShell desde `D:\EcoResiliaFirebase`:

```powershell
.\gradlew.bat :app:installDebug --no-daemon
```

En la app: **Avisos → Avisos informativos por región → Arequipa → Activar**. Requiere conexión y permiso de notificaciones.

## Prueba sin servidor

Puedes probar una campaña de Firebase Console dirigida al tema **`ecoresilia_region_arequipa`** después de suscribirte. Debe estar en segundo plano para ver la notificación Android predeterminada.

## Emisor automatizado local (opcional)

En Firebase Console > Configuración del proyecto > Cuentas de servicio, genera una **cuenta de servicio** solo cuando quieras enviar desde un equipo de confianza. Guarda su JSON fuera de cualquier repositorio, preferentemente en `D:\EcoResiliaPrivado\firebase-admin.json`, y protege ese archivo. **No uses `app/google-services.json`: sirve para configurar el cliente, no para autorizar envíos FCM**.

La primera prueba local puede hacerse *sin credenciales ni envío*:

```powershell
py -3 D:\EcoResiliaFirebase\herramientas\alertas\emisor_alertas_ecoresilia.py
```

El archivo `avisos_pendientes.json` contiene una demo deshabilitada (`aprobado: false`). Solo un administrador debe cambiarla a aprobada. Para emitir necesitas el paquete `firebase-admin` en tu entorno Python y la variable `GOOGLE_APPLICATION_CREDENTIALS` apuntando al JSON privado. No compartas ese JSON ni lo incluyas en un ZIP.

Una vez probado, puedes programar ese comando con `--send` en el Programador de tareas de Windows cada 5 o 10 minutos. Funciona únicamente cuando esa computadora está encendida y tiene Internet. No es un servicio 24/7.

### Restricciones de seguridad

- Solo procesa categorías `simulacro` y `boletin_informativo`; **no emite alertas de emergencia reales**.
- Para boletines exige URL institucional HTTPS de SENAMHI, INDECI, CENEPRED o gob.pe y aprobación humana (también debes verificar manualmente el contenido y la vigencia de la fuente).
- No envía reportes ciudadanos sin verificar ni detecta amenazas automáticamente.
- El historial evita reenvíos habituales si la tarea se ejecuta repetidamente. No garantiza entrega a cada dispositivo, porque FCM puede aceptar un mensaje sin que el destinatario lo reciba.
- Mantén JSON de cuenta de servicio y base SQLite **fuera de GitHub**.
- Si quieres ingestión automática de boletines oficiales tendrás que incorporar un feed/API oficial estable y un proceso de validación; esta versión automatiza la distribución de avisos ya aprobados, **no su verificación**.
