# Lee solo los mensajes recientes del módulo WMS; no modifica ni reinstala nada.
param([string]$DeviceId = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'No se encontró adb.exe.' }
& $adb -s $DeviceId get-state
if ($LASTEXITCODE -ne 0) { throw "Emulador $DeviceId no disponible." }
Write-Host '=== Errores recientes de GEOPERU (Android) ==='
& $adb -s $DeviceId logcat -d -t 1000 -s 'EcoResiliaGeoPeru:W' '*:S'
Write-Host '=== Fin del diagnóstico ==='
