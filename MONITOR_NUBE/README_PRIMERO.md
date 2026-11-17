# EcoResilia · Monitor en la nube RC5 (preparado, NO desplegado)

**Objetivo:** consultar datos públicos de SENAMHI/GeoSINPAD sin depender de Windows. Usa GitHub Actions programado cada 30 minutos, Firebase Cloud Messaging para entrega y Firestore para recordar grupos vistos. Mantiene intacto EcoResilia Android RC4.1 y su mapa GEOPERÚ.

## Qué hace

- `preview`: consulta GeoSINPAD y resume registros recientes por departamento y fecha; no escribe en Firebase.
- `bootstrap`: guarda en Firestore los grupos existentes como **históricos sin enviar** y registra que se inicializó el historial. Es obligatorio antes de habilitar envíos.
- `demo`: envía una notificación claramente marcada **SIMULACRO TÉCNICO** solo al token de prueba configurado como secreto. No usa datos reales ni envía por región.
- `auto`: corre según horario. Por defecto **no publica**. Si el administrador habilita la variable `ECORESILIA_AUTO_PUBLISH=true`, solo envía información preventiva de grupos nuevos que pasaron validaciones, usando el historial persistente de Firestore.

**No es una alerta oficial de emergencia.** La ventana técnica de 24 h no prueba vigencia de un boletín. GeoSINPAD puede publicar registros por distrito, no emergencias únicas. Los textos indican expresamente que son datos preventivos y remiten a la fuente oficial.

## Configuración única en GitHub

1. Crear un repositorio **privado nuevo** para el monitor de EcoResilia, separado de Eterna y del portafolio. No subir el ZIP completo de Android ni credenciales.
2. Subir estos archivos conservando la carpeta `.github/workflows/` (si subes el ZIP como un solo archivo, GitHub Actions no lo ejecutará).
3. En **Settings → Secrets and variables → Actions → Secrets**, añadir:
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: contenido completo del JSON de cuenta de servicio del proyecto Firebase `ecoresilia`. **Nunca** es `google-services.json`. Mantener privado; no compartir por chat ni incluir en commits.
   - `ECORESILIA_DEMO_TOKEN`: token FCM del emulador para pruebas; no incluirlo en logs ni archivos.
4. En **Actions → EcoResilia · Monitor GeoSINPAD (nube) → Run workflow**, elegir `preview` para probar consulta sin envíos. Ver logs.
5. Ejecutar `bootstrap` **una vez** para marcar grupos ya existentes como históricos sin enviar. Revisar Firestore: colección `ecoresilia_monitor_meta_v1`, documento `bootstrap`, campo `completado=true`.
6. Ejecutar `demo` para probar FCM solo al emulador. Dejar Android en segundo plano y comprobar notificación.
7. **Solo tras comprobar fuente, fechas, contenido, permisos y recibir aprobación para publicar a suscriptores**, crear variable de repositorio `ECORESILIA_AUTO_PUBLISH=true` en **Settings → Secrets and variables → Actions → Variables**. La tarea programada empezará a publicar **resúmenes informativos**, nunca mensajes de emergencia.

## Limitaciones y seguridad

- Las ejecuciones programadas de GitHub Actions pueden demorarse; 30 minutos es el intervalo solicitado, no una garantía de tiempo real. Revisar minutos incluidos en el plan; puede haber límites o cargos según cuenta/uso.
- El servicio oficial puede estar fuera de línea o cambiar su estructura. Ante datos inválidos, parciales o de más de 24 horas, no se envía.
- Si FCM acepta un envío pero falla el registro posterior, el grupo queda reservado para evitar duplicados; requiere revisión manual del registro. Firestore es la memoria durable, no un archivo local efímero.
- Si se ejecuta `bootstrap` cuando la publicación ya está activa, se marcarán grupos nuevos como históricos sin enviar. Usarlo solo antes de habilitar publicaciones o para recuperación controlada.
- Un grupo es **departamento + fecha UTC + tipo de registro**, no una emergencia única. Se emite como máximo una notificación por grupo, sin prometer detección instantánea.
- Los mensajes dirigidos a un tema FCM pueden llegar a todos los dispositivos suscritos. No habilitar la publicación hasta validar el flujo y autorizar expresamente.
- La clave privada en GitHub Secrets debe tener acceso mínimo y rotarse si se filtra. No pegarla en ChatGPT.
- No requiere Android recompilado: reutiliza los temas `ecoresilia_region_<departamento>` que RC4.1 ya usa.
- Esta entrega es código **listo para configurar**, no un despliegue en la nube ya activo.

## Pruebas locales sin secretos

Desde la carpeta raíz del repositorio:

```bash
python -m unittest discover -s tests -v
python monitor/ecoresilia_cloud.py --mode preview --fixture tests/fixture_ejemplo.json
```

## Para la exposición

Muestra la ejecución automática en GitHub Actions, su log de consulta y un `demo` al token de Medium Phone. La prueba de FCM es una simulación separada de los datos reales; no se afirma que sea un aviso oficial. La PC no participa en ninguna de las dos acciones.
