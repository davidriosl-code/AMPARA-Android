param(
    [Parameter(Mandatory=$true)]
    [string]$Url
)
$ErrorActionPreference = 'Stop'
$projectDir = Resolve-Path (Join-Path $PSScriptRoot '..\EcoResilia')
$gradle = Join-Path $projectDir 'gradle.properties'
$urlClean = $Url.Trim().TrimEnd('/')
if ($urlClean -notmatch '^https://[a-zA-Z0-9][a-zA-Z0-9.-]+(?::443)?$') {
    throw 'La URL debe ser la raiz HTTPS del servidor (ejemplo https://ecoresilia-ai.onrender.com), sin rutas /api.'
}
if ($urlClean -match '[`"\s]') {throw 'URL invalida.'}
$raw = Get-Content -LiteralPath $gradle -Raw -Encoding UTF8
$line = "ecoresiliaAssistantProxyUrl=$urlClean"
if ($raw -match '(?m)^ecoresiliaAssistantProxyUrl=.*$') {
    $raw = [regex]::Replace($raw, '(?m)^ecoresiliaAssistantProxyUrl=.*$', $line)
} else {
    $raw = $raw.TrimEnd() + "`r`n" + $line + "`r`n"
}
Set-Content -LiteralPath $gradle -Value $raw -Encoding UTF8
Write-Host "URL del Asistente EcoResilia configurada: $urlClean"
Write-Host 'Ahora ejecuta COMPILAR_E_INSTALAR_09.ps1 para generar el APK con la URL HTTPS.'
