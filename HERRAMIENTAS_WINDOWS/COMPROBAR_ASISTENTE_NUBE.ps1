param([Parameter(Mandatory=$true)][string]$Url)
$ErrorActionPreference='Stop'
$base=$Url.Trim().TrimEnd('/')
if ($base -notmatch '^https://[A-Za-z0-9][A-Za-z0-9.-]+(?::443)?$') {throw 'Se requiere URL HTTPS sin ruta.'}
Write-Host '=== ECORESILIA · ESTADO DE ASISTENTE EN NUBE ==='
$resp = Invoke-RestMethod -Uri "$base/health" -Method Get -TimeoutSec 120
$resp | Format-List
if ($resp.status -ne 'ready') {Write-Warning 'Backend accesible pero falta clave de proveedor IA.'}
else {Write-Host 'Backend preparado: falta validar una respuesta real desde Android.'}
