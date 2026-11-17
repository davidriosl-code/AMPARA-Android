# EcoResilia 0.9 RC4.1 — GEOPERÚ WMS y Liquid Glass

Corrección de compatibilidad del parser XML de GetCapabilities en Android. Consulta LEEME_PRIMERO_09_RC4_1.md en la raíz.

# EcoResilia 0.9.0 RC4 — GEOPERÚ WMS + Liquid Glass

**Estado:** candidato de integración. Requiere compilar y comprobar en Android Studio/Medium Phone. Instrucciones en `../LEEME_PRIMERO_09_RC4.md`.

- Kotlin 2.0.21 / Jetpack Compose / Material 3 / MVVM, SDK 35, Gradle 8.9, JDK 17+.
- Diseño Liquid Glass y logos recuperados de la exportación local.
- Mapa base OpenStreetMap/osmdroid, con dos capas WMS de GEOPERÚ: inundaciones y movimientos en masa por lluvias fuertes. No usa Google Maps SDK ni API key.
- Comprobación `GetCapabilities` + `GetMap` antes de habilitar capas, con fallback a OSM y control de opacidad.
- GPS con FusedLocationProviderClient, búsqueda de ciudades/distritos, Open-Meteo y registros INDECI.
- Room para mochila/reportes offline; Nearby experimental.
- Firebase Auth/Firestore para reportes propios; FCM para recepción de avisos y temas regionales (requiere configuración cliente).
- Herramientas Python de consulta/revisión de avisos, sin tareas ni envíos activados.

No se incluyen claves privadas, cuentas de servicio, `google-services.json`, `.env`, bases de datos locales ni APK compilado. Las reglas `firebase/firestore.rules` deben verificarse y desplegarse por un administrador autorizado.

**La susceptibilidad territorial NO es una alerta actual, un pronóstico de inundación ni una evaluación individual de viviendas. No usar esta app para decidir evacuaciones.**
