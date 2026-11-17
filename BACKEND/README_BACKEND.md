# EcoResilia — backend opcional de IA

**No se necesita este backend para la mochila, el mapa, el clima ni la guía offline.**

Si deseas respuestas generativas más amplias en Android Emulator:

1. Instala Node.js 22 o superior en tu equipo.
2. Obtén tu propia clave NVIDIA NIM válida. No la envíes por chat ni la pongas en Kotlin.
3. En PowerShell, abre una terminal **distinta** a la de Gradle:

```powershell
cd "D:\EcoResilia_08_Integral\BACKEND"
$secret = Read-Host "Clave NVIDIA API" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try { $env:NVIDIA_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
node .\server.mjs
```

El servidor se inicia en `127.0.0.1:8787`, accesible desde el emulador con `http://10.0.2.2:8787`.
No expongas este proxy directamente a internet. En producción, usa un backend HTTPS con autenticación, límites de uso y tratamiento adecuado de datos personales.

En el emulador, abre **Asistente** y activa el switch **IA conectada (requiere proxy propio)**. Cuando el servidor no funcione o no tenga una clave válida, el asistente vuelve automáticamente a la guía local. La conversación se almacena en Room en el dispositivo; cuando el modo online está activo se envían la consulta, el historial reciente y la ubicación seleccionada al proveedor de IA a través del proxy.

Para comprobar que el proceso esté arriba:

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
```

Debería decir `ready` solo si la variable de entorno NVIDIA_API_KEY tiene un valor. Ello no prueba que la clave tenga permisos ni que el proveedor esté disponible. Las cuotas, disponibilidad y costos dependen de tu cuenta NVIDIA.

La API utilizada es la interfaz compatible OpenAI Chat Completions de NVIDIA NIM: `https://integrate.api.nvidia.com/v1/chat/completions`. Modelo predeterminado configurable: `meta/llama-3.3-70b-instruct`.

**Seguridad:** el asistente no está capacitado para emitir alertas oficiales, ordenar evacuaciones ni sustituir a autoridades. Nunca confíes solo en IA para una decisión de vida o muerte.
