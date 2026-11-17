# SHA-1 de depuración para configurar restricciones Android cuando corresponda.
param([string]$Proyecto = (Join-Path $PSScriptRoot '..\EcoResilia'))
$root = (Resolve-Path -LiteralPath $Proyecto).Path
Push-Location $root
try {
    & '.\gradlew.bat' ':app:signingReport' '--no-daemon'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo obtener el reporte de firma.' }
} finally { Pop-Location }
Write-Host 'El SHA-1 de debug NO sirve para la clave de publicación: la firma release y Google Play App Signing pueden ser diferentes.'
