# Instala la misma APK de RC4.4.2 en un telefono por USB, sin resetear datos.
param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$apk = Join-Path $PSScriptRoot '..\EVIDENCIAS_LOCALES\EcoResilia_09_RC442_Nearby_Dos_Celulares_debug.apk'
if (-not (Test-Path -LiteralPath $adb)) { throw 'No se encontro adb.exe.' }
if (-not (Test-Path -LiteralPath $apk)) { throw 'Compila primero RC4.4.2 con COMPILAR_E_INSTALAR_09.ps1.' }
$devices = @(& $adb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
if ($devices -notcontains $Serial) { throw "El dispositivo $Serial no esta autorizado. Activa depuracion USB y ejecuta adb devices." }
& $adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Instalacion fallida. No desinstales la aplicacion para intentar recuperarla.' }
Write-Host "EcoResilia RC4.4.2 instalada en $Serial" -ForegroundColor Green
