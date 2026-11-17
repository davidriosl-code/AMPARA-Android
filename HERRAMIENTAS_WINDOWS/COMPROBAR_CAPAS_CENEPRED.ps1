# Consulta metadatos ArcGIS, no certifica geometrías ni zonas seguras.
$ErrorActionPreference = 'Continue'
$service = 'https://sig.cenepred.gob.pe/sigrid/rest/services/S05CP/MapServer'
foreach ($entry in @(@{ Nombre='Inundación'; Id=5090024 }, @{ Nombre='Movimientos en masa'; Id=5090022 })) {
    $url = "$service/$($entry.Id)?f=json"
    Write-Host "`nCapa $($entry.Nombre) | ID $($entry.Id)" -ForegroundColor Cyan
    try {
        $data = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 20
        if ($data.error) { Write-Warning "ArcGIS devuelve error: $($data.error.message)"; continue }
        if ([int]$data.id -ne $entry.Id) { Write-Warning 'El ID devuelto no coincide.'; continue }
        Write-Host "Metadatos: $($data.name) | tipo: $($data.type)" -ForegroundColor Green
        if ($data.extent) { Write-Host "Extensión publicada: $($data.extent | ConvertTo-Json -Compress -Depth 5)" }
        Write-Host 'PENDIENTE: comprobar visualmente geometrías y cobertura frente a SIGRID.'
    } catch { Write-Warning "No se pudo consultar el servicio: $($_.Exception.Message)" }
}
