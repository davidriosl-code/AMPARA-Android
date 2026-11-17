# AMPARA — Sistema de Control y Prevención de Riesgos Naturales

**Aplicación móvil Android para la preparación familiar y prevención de riesgos en el Perú.**

AMPARA reúne información meteorológica, un mapa interactivo, consulta de avisos oficiales, reportes comunitarios, mochila familiar de emergencia, diagnóstico preventivo y un asistente de orientación. **No sustituye las indicaciones de las autoridades ni garantiza alertas en tiempo real.**

## Funcionalidades

- **Inicio y clima:** consulta meteorológica y presentación visual de las condiciones del tiempo.
- **Mapa:** navegación geográfica, ubicación y consulta de capas y reportes.
- **Mochila de emergencia:** lista de suministros, inventario familiar y recordatorios.
- **Asistente:** orientación preventiva; algunas funciones requieren conexión y backend.
- **Avisos y comunidad:** consulta de fuentes oficiales y reportes de usuarios (los reportes no constituyen avisos verificados).
- **Preparación:** recursos de seguridad y diagnóstico familiar.

## Tecnologías

Kotlin, Jetpack Compose, Material 3, MVVM, Room, WorkManager, Google Play Services (ubicación y Nearby), osmdroid, Firebase (Auth, Firestore y Messaging), Node.js, Gradle y herramientas de prueba.

## Estructura

- `EcoResilia/` — app Android y pruebas.
- `ASISTENTE_NUBE/` — servidor de apoyo para el asistente.
- `BACKEND/` — backend adicional del proyecto.
- `MONITOR_NUBE/` — monitor experimental de datos preventivos y sus pruebas. Existe también el [repositorio del monitor](https://github.com/davidriosl-code/ecoresilia-cloud-monitor).
- `HERRAMIENTAS_WINDOWS/` — automatización de compilación y diagnósticos.
- `IDENTIDAD_AMPARA/` — identidad y recursos gráficos.

## Ejecutar en Android Studio

1. Abrir `EcoResilia/` como proyecto Gradle en Android Studio.
2. Disponer del SDK Android 35, Java 17 y conexión para sincronizar dependencias.
3. Para configurar Firebase en un entorno propio, colocar `google-services.json` en `EcoResilia/app/`. **Ese archivo se excluye del repositorio.**
4. Para el asistente en línea, configurar su URL HTTPS de backend según la guía del proyecto; nunca poner llaves privadas en el APK ni en GitHub.
5. Compilar el módulo `app` con Gradle.

**Nota de seguridad:** se excluyen credenciales, archivos `.env`, secretos locales, keystores, APKs y artefactos generados. Este repositorio contiene código fuente, no credenciales de la instalación real.

## Estado

Versión académica en desarrollo, con mejoras recientes de interfaz **Liquid Glass clara**, corazón translúcido y controles sin indicación gris de pulsación. El repositorio representa el código local en el momento de su publicación y no se sincroniza automáticamente con el teléfono.