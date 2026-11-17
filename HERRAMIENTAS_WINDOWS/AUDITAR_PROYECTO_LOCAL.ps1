# Solo lectura. Detecta si tu D:\EcoResiliaFirebase contiene avances posteriores al ZIP.
param([Parameter(Mandatory=$true)][string]$ProyectoLocal)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $ProyectoLocal).Path
if (-not (Test-Path -LiteralPath (Join-Path $root 'settings.gradle.kts'))) { throw 'Indica la carpeta que contiene settings.gradle.kts.' }
$checks = @(
    @{ Label='Firebase cliente'; Rel='app/google-services.json' },
    @{ Label='FCM receptor'; Rel='app/src/main/java/com/ecoresilia/app/push/EcoMessagingService.kt' },
    @{ Label='FCM regional'; Rel='app/src/main/java/com/ecoresilia/app/ui/RegionalAlertsPanel.kt' },
    @{ Label='Mapa osmdroid'; Rel='app/src/main/java/com/ecoresilia/app/ui/EcoNativeMap.kt' },
    @{ Label='Mapa con FusedLocation'; Rel='app/src/main/java/com/ecoresilia/app/data/DeviceLocationProvider.kt' },
    @{ Label='Liquid Glass'; Rel='app/src/main/java/com/ecoresilia/app/ui/EcoLiquidBackdrop.kt' },
    @{ Label='Firestore'; Rel='app/src/main/java/com/ecoresilia/app/data/cloud/CloudReportsRepository.kt' },
    @{ Label='Monitor SENAMHI'; Rel='herramientas/alertas/detectar_avisos_oficiales.py' }
)
foreach ($item in $checks) {
    $p = Join-Path $root ($item.Rel -replace '/', '\')
    Write-Host ('{0,-28} {1}' -f $item.Label, $(if (Test-Path -LiteralPath $p) { 'PRESENTE' } else { 'AUSENTE' }))
}
$gradle = Join-Path $root 'app/build.gradle.kts'
Write-Host "`nVersiones en el proyecto local:"
Select-String -Path $gradle -Pattern 'versionName|versionCode' | ForEach-Object { Write-Host $_.Line.Trim() }
Write-Host "`nNo se modificó ni se leyó el contenido de credenciales. No copies encima de este proyecto sin comparar cambios."
