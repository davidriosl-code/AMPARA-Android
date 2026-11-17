# EcoResilia 0.8 + Firebase Authentication / Cloud Firestore

Esta es una **integración de código** a la versión 0.8 completa, no la aplicación MakeItSo. Los módulos existentes no se sustituyen. El funcionamiento final en Firebase está PENDIENTE hasta conectar un proyecto Firebase y compilar/probar en Android Studio.

## Configurar Firebase para esta app

1. Ve a https://console.firebase.google.com/ y crea un proyecto de **pruebas de EcoResilia** independiente de `laboratorio11mov`. No ingreses datos personales reales de emergencias.
2. Agrega una aplicación Android con **package/applicationId exacto `com.ecoresilia.app`**.
3. Descarga `google-services.json` y cópialo como `EcoResilia/app/google-services.json`. No uses el JSON de MakeItSo.
4. Authentication → Sign-in method → habilita **Anonymous** y **Email/Password**; mantén activadas las medidas contra enumeración de cuentas.
5. Firestore Database → Crea la base (modo producción). Copia las reglas de `firebase/firestore.rules` en Firestore → Rules y **Publish**.
6. Abre la carpeta `EcoResilia` con Android Studio, sincroniza Gradle y ejecuta `app`. Si el JSON no está presente, la app conserva el módulo Comunidad offline y muestra un aviso de configuración en la nube (la compilación no obliga a añadir el JSON).
7. Menú Comunidad → Mis reportes en la nube. Inicia sesión temporal. Crea un reporte de prueba, comprueba `communityReports` en Firestore, edítalo, vuelve a comprobarlo y elimínalo.
8. Para conservar los reportes de la cuenta temporal al cambiar de dispositivo, toca «Vincular cuenta / iniciar sesión» → «Crear / vincular» con correo y contraseña. Dependiendo de la protección de Firebase, la vinculación puede estar restringida: **no debilites la protección** solo para pasar una prueba. No se migra automáticamente un reporte al iniciar sesión en otra cuenta existente.

## Funcionalidad implementada en código

- Sesión Firebase anónima al abrir Comunidad, y vinculación/ingreso con Email/Password.
- Reportes propios `communityReports` CRUD; `Flow` + `StateFlow` en ViewModel + `collectAsStateWithLifecycle` Compose.
- Reglas Firestore de propietario, categoría, forma del documento, inmutabilidad de UID, ID y fecha; lectores restringidos al dueño.
- Pantalla online adicional en Comunidad. No se tocan Room, Nearby, mapa, clima ni los reportes offline. Se indica falta de Internet y no se promete sincronizar Room↔Firestore.

## Qué no hace

- No crea por sí misma un proyecto Firebase ni configura cuotas o facturación.
- No sincroniza automáticamente reportes offline con la nube.
- No transforma reportes en alertas oficiales. No aporta feed público, análisis de imágenes o verificación real.
- No existe garantía de seguridad de Nearby para el despliegue público: ya era experimental en la versión original.

## Compilar desde PowerShell

```powershell
Set-Location 'D:\EcoResilia_Firebase\EcoResilia'
.\gradlew.bat :app:assembleDebug
# Para instalar en emulador iniciado:
.\gradlew.bat :app:installDebug
```

Requiere Android Studio / SDK 35, JDK 17 y acceso a Google Maven para descargar librerías. La carpeta que contiene `settings.gradle.kts` es `EcoResilia`.

## Pruebas y evidencias recomendadas

- A: app abre y **Mi mochila** funciona sin Internet.
- B: Comunidad muestra reportes locales existentes sin cambios.
- C: Firebase Authentication muestra un `uid` anónimo después de abrir la sección.
- D: Crear 1 documento Firestore `communityReports/{id}` con UID propio, solo ubicación referencial.
- E: Editarlo y confirmar que el ID no cambia.
- F: Borrarlo y verificar desaparición en consola.
- G: Probar con otro UID, confirmar que NO puede leer ni cambiar reportes ajenos.
- H: Desactivar red y verificar que la nube advierte falta de Internet, sin impedir las acciones locales.
- I: Captura de Authentication, Firestore, cambios y vídeo breve (datos ficticios).

La integración **no está verificada en Android Studio** en el entorno de generación si no se observa `BUILD SUCCESSFUL`. No subas `google-services.json`, credenciales ni datos privados a repositorios públicos.
