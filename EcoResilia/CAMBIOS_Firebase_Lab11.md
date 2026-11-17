# Cambios reales de EcoResilia 0.8

**Archivos modificados:**
- `build.gradle.kts`: Google Services Gradle plugin disponible.
- `app/build.gradle.kts`: Firebase BoM, Auth, Firestore y coroutine Tasks; el plugin de Google Services se aplica solo cuando existe `app/google-services.json`.
- `app/src/main/java/com/ecoresilia/app/ui/CommunityScreen.kt`: sección nueva de reportes en la nube sin quitar reportes offline.
- `.gitignore`: excluye la configuración JSON de Firebase.

**Archivos nuevos:**
- `app/src/main/java/com/ecoresilia/app/data/cloud/CloudCommunityReport.kt`
- `app/src/main/java/com/ecoresilia/app/data/cloud/FirebaseSessionManager.kt`
- `app/src/main/java/com/ecoresilia/app/data/cloud/CloudReportsRepository.kt`
- `app/src/main/java/com/ecoresilia/app/ui/cloud/CloudReportsViewModel.kt`
- `app/src/main/java/com/ecoresilia/app/ui/cloud/CloudCommunityPanel.kt`
- `firebase/firestore.rules`
- `FIREBASE_ECORESILIA_LEEME.md`

**Sin cambios:** `EcoViewModel`, `EcoApp`, `EmergencyDatabase` (Room), `NearbyReportMesh`, módulo clima, mapa, mochila, backend JS, branding y diseño previo.

**Validación:** pendientes sincronización Gradle, compilación, ejecución en emulador y Firebase Console hasta configurar el JSON propio de EcoResilia.
