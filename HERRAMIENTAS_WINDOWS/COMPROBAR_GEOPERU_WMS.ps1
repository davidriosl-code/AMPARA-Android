# Prueba de lectura: consulta GetCapabilities y GetMap en GEOPERU.
# No cambia el proyecto Android ni las credenciales. Guarda dos PNG de muestra.
param([string]$Salida = (Join-Path $PSScriptRoot '..\EVIDENCIAS_LOCALES'))
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Path $Salida -Force | Out-Null
$servicios = @(
    @{ Nombre='Inundacion'; Esperado='peru_inundacion_group_'; Url='https://espacialg.geoperu.gob.pe/geoserver/geoperu/peru_inundacion_group_/wms' },
    @{ Nombre='Movimientos_masa'; Esperado='peru_inundacion_mmasa_group_'; Url='https://espacialg.geoperu.gob.pe/geoserver/geoperu/peru_inundacion_mmasa_group_/wms' }
)
# Tesela de prueba sobre Arequipa (zoom 8), EPSG:3857.
$bbox = '-7983694.730330089,-1878516.4071364915,-7827151.696402049,-1721973.3732084506'
foreach ($item in $servicios) {
    Write-Host "`n=== $($item.Nombre) ===" -ForegroundColor Cyan
    try {
        $url = "$($item.Url)?service=WMS&request=GetCapabilities&version=1.3.0"
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30
        [xml]$doc = $response.Content
        $nodes = @($doc.SelectNodes("//*[local-name()='Layer']/*[local-name()='Name']"))
        $names = @($nodes | ForEach-Object { $_.InnerText.Trim() } | Where-Object { $_ })
        Write-Host "Capas publicadas: $($names.Count)"
        $layer = $names | Where-Object { ($_ -split ':')[-1] -eq $item.Esperado } | Select-Object -First 1
        if (-not $layer) {
            Write-Warning "No aparece el grupo esperado: $($item.Esperado). Nombres disponibles: $($names -join ', ')"
            continue
        }
        Write-Host "Capa elegida: $layer"
        $encoded = [uri]::EscapeDataString($layer)
        $mapUrl = "$($item.Url)?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=$encoded&STYLES=&FORMAT=image%2Fpng&TRANSPARENT=TRUE&SRS=EPSG%3A3857&BBOX=$bbox&WIDTH=256&HEIGHT=256"
        $file = Join-Path $Salida "$($item.Nombre)_geoperu_prueba.png"
        Invoke-WebRequest -Uri $mapUrl -UseBasicParsing -TimeoutSec 30 -OutFile $file
        $bytes = [IO.File]::ReadAllBytes($file)
        if ($bytes.Length -lt 8 -or $bytes[0] -ne 137 -or $bytes[1] -ne 80 -or $bytes[2] -ne 78 -or $bytes[3] -ne 71) {
            Write-Warning 'La respuesta GetMap no es PNG valido. Revisar el servicio.'
        } else {
            Write-Host "GetMap PNG correcto: $file ($($bytes.Length) bytes)" -ForegroundColor Green
        }
    } catch {
        Write-Warning "No se pudo completar la prueba: $($_.Exception.Message)"
    }
}
