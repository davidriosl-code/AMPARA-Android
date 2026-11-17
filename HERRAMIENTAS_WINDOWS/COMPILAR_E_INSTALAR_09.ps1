# AMPARA RC4.5.4 · Liquid Glass táctil + interruptores animados; Windows PowerShell 5.1+
# No modifica D:\EcoResiliaFirebase. No envia mensajes FCM ni inicia monitores.
param(
    [string]$Proyecto = (Join-Path $PSScriptRoot '..\EcoResilia'),
    [string]$ProyectoOriginal = 'D:\EcoResiliaFirebase',
    [string]$FirebaseJson = '',
    [string]$DeviceId = '',
    [switch]$Instalar
)
$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path -LiteralPath $Proyecto).Path
$gradle = Join-Path $projectPath 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle)) { throw 'No encuentro gradlew.bat en la carpeta del candidato.' }

# Si JAVA_HOME apunta a un JDK borrado, intenta el JDK integrado de Android Studio.
if ($env:JAVA_HOME -and -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Write-Warning "JAVA_HOME anterior no existe. Buscando JDK de Android Studio."
    Remove-Item Env:\JAVA_HOME -ErrorAction SilentlyContinue
}
if (-not $env:JAVA_HOME) {
    $jdks = @(
        'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot',
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Android\Android Studio\jre',
        'C:\Program Files\Java\jdk-17',
        'C:\Program Files\Eclipse Adoptium\jdk-17'
    )
    foreach ($candidate in $jdks) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe')) {
            $env:JAVA_HOME = $candidate
            Write-Host "JDK detectado: $candidate"
            break
        }
    }
}
$javaAvailable = $false
if ($env:JAVA_HOME) { $javaAvailable = Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe') }
if (-not $javaAvailable) { $javaAvailable = [bool](Get-Command java.exe -ErrorAction SilentlyContinue) }
if (-not $javaAvailable) { throw 'Falta Java JDK 17 o superior. Instala/selecciona el JDK de Android Studio.' }

if (-not $env:ANDROID_HOME) {
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $sdk) { $env:ANDROID_HOME = $sdk }
}

# Reutiliza SOLO el archivo cliente google-services.json del proyecto original.
if (-not $FirebaseJson -and $ProyectoOriginal) {
    $autoFirebase = Join-Path $ProyectoOriginal 'app\google-services.json'
    if (Test-Path -LiteralPath $autoFirebase) { $FirebaseJson = $autoFirebase }
}
$target = Join-Path $projectPath 'app\google-services.json'
if ($FirebaseJson -and -not (Test-Path -LiteralPath $target)) {
    $source = (Resolve-Path -LiteralPath $FirebaseJson).Path
    if ([IO.Path]::GetFileName($source) -ne 'google-services.json') {
        throw 'No se aceptan cuentas de servicio ni claves Admin: solo google-services.json del cliente Android.'
    }
    $config = Get-Content -LiteralPath $source -Raw -Encoding UTF8 | ConvertFrom-Json
    $packages = @($config.client | ForEach-Object { $_.client_info.android_client_info.package_name })
    if ($packages -notcontains 'com.ecoresilia.app') { throw 'El JSON no pertenece a com.ecoresilia.app. No se copio.' }
    Copy-Item -LiteralPath $source -Destination $target
    Write-Host 'Firebase cliente copiado al candidato. No se muestran credenciales.' -ForegroundColor Green
}
if (-not (Test-Path -LiteralPath $target)) {
    if ($Instalar) {
        throw 'Instalacion bloqueada por seguridad: falta google-services.json del cliente. Indica -FirebaseJson con la configuracion de com.ecoresilia.app o copia el archivo desde el proyecto original. Se protege Firebase y tus reportes existentes.'
    }
    Write-Warning 'No se encontro google-services.json. Compilacion sin Firebase; no instales el APK en tu OPPO.'
}

$evidence = Join-Path (Split-Path $projectPath -Parent) 'EVIDENCIAS_LOCALES'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$log = Join-Path $evidence "compilacion_ampara_rc454_$stamp.log"
Push-Location $projectPath
try {
    Write-Host 'Compilando :app:assembleDebug (puede descargar dependencias)...' -ForegroundColor Cyan
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $gradle ':app:assembleDebug' '--no-daemon' '--no-watch-fs' '--max-workers=1' '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' '--console=plain' '--stacktrace' 2>&1 | Tee-Object -FilePath $log }
    finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0) { throw "Gradle fallo (codigo $LASTEXITCODE). Log: $log" }
} finally { Pop-Location }
$apk = Join-Path $projectPath 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Gradle termino pero no genero app-debug.apk.' }
$copy = Join-Path $evidence 'AMPARA_RC454_Glass_Switches_debug.apk'
Copy-Item -LiteralPath $apk -Destination $copy -Force
Write-Host "APK: $copy" -ForegroundColor Green
Write-Host "SHA256: $((Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash)"
Write-Host "Log: $log"
if (-not $Instalar) {
    Write-Host 'Compilacion terminada. Para instalar en telefono USB, repite con -Instalar.'
    return
}
$adbCandidates = @()
if ($env:ANDROID_HOME) { $adbCandidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }
$adbCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
$adb = $adbCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $adb) { throw 'No encuentro adb.exe. Instala Android SDK Platform-Tools desde Android Studio.' }
$connected = @(& $adb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
if ($DeviceId -eq 'emulator-5554' -and $connected -notcontains $DeviceId) {
    $emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
    if (-not (Test-Path -LiteralPath $emulator)) { throw 'No se encontro Android Emulator.' }
    Write-Host 'Iniciando Medium_Phone despues de compilar para ahorrar RAM...' -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList '-avd Medium_Phone'
    for ($i=0; $i -lt 90; $i++) {
        $boot = & $adb -s $DeviceId shell getprop sys.boot_completed 2>$null
        if ($boot -match '^1$') { break }
        Start-Sleep -Seconds 3
    }
    $connected = @(& $adb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
}
if ($DeviceId) {
    if ($connected -notcontains $DeviceId) { throw "Dispositivo $DeviceId no autorizado. Ejecuta adb devices." }
    $selected = $DeviceId
} else {
    if ($connected.Count -ne 1) { throw "Hay $($connected.Count) dispositivos listos. Conecta uno o usa -DeviceId." }
    $selected = $connected[0]
}
& $adb -s $selected install -r $copy
if ($LASTEXITCODE -ne 0) { throw 'Fallo la instalacion en el telefono. Revisa el mensaje de adb.' }
& $adb -s $selected shell monkey -p com.ecoresilia.app -c android.intent.category.LAUNCHER 1
if ($LASTEXITCODE -ne 0) { throw 'El APK se instalo, pero no se pudo abrir automaticamente.' }
Write-Host 'Instalacion e inicio solicitados. FALTAN las pruebas manuales de mapa, Firebase y avisos.' -ForegroundColor Yellow
