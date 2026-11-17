package com.ecoresilia.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ecoresilia.app.data.*

private val AmparaOrange = Color(0xFFF28C1E)

/** Pantalla sencilla: no crea una vista nativa de mapa dentro de un ScrollView. */
@Composable
internal fun MapScreen(onOpenMap: () -> Unit, onReport: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Mapa de mi zona", "Explora los lugares y reportes cercanos")
        FrostCard {
            Icon(Icons.Outlined.Map, null, tint = AmparaOrange, modifier = Modifier.size(48.dp))
            Text("Explora tu zona", style = MaterialTheme.typography.titleLarge)
            Text("Amplía el mapa, busca lugares y descubre reportes de tu zona.",
                style = MaterialTheme.typography.bodyMedium)
            PrimaryAction("Ver mapa", onClick = onOpenMap, modifier = Modifier.fillMaxWidth())
        }
        PressTile("Crear reporte", "Comparte lo que observaste", onReport,
            trailing = { Icon(Icons.Outlined.Place, null, tint = AmparaOrange) })
        InfoNote("Los reportes de personas aún no están verificados. Ante una emergencia, sigue las indicaciones de las autoridades.")
    }
}

/** La vista nativa del mapa usa TODO el espacio: sin ScrollView ni contenedor con altura fija. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FullMapScreen(
    climate: ClimateUiState, reports: List<IncidentReport>, geo: GeoUiState,
    official: OfficialUiState,
    onCity: (String, Double, Double) -> Unit, onSearch: (String) -> Unit,
    onClearSearch: () -> Unit, onLoadOfficial: () -> Unit,
    onBack: () -> Unit, onReport: (DeviceLocationProvider.Result?) -> Unit
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var query by remember { mutableStateOf("") }
    var incidentFilter by remember { mutableStateOf("Todos") }
    var thematicLayer by remember { mutableStateOf("Ninguna") }
    var showOfficial by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<DeviceLocationProvider.Result?>(null) }
    var locationNote by remember { mutableStateOf("Pulsa «Mi ubicación» para ver tu posición") }
    var selectedReport by remember { mutableStateOf<IncidentReport?>(null) }
    var selectedOfficial by remember { mutableStateOf<OfficialIncident?>(null) }
    var activeWmsLayer by remember { mutableStateOf<GeoPeruWmsLayer?>(null) }
    var layerNote by remember { mutableStateOf("") }
    var layerOpacity by remember { mutableFloatStateOf(.55f) }

    LaunchedEffect(thematicLayer, climate.latitude, climate.longitude) {
        activeWmsLayer = null
        if (thematicLayer != "Ninguna") {
            layerNote = "Cargando zonas de riesgo…"
            val result = GeoPeruLayerRepository.inspect(thematicLayer, climate.latitude, climate.longitude)
            layerNote = if (result.ready) "Capa disponible" else "Esta información no está disponible ahora"
            activeWmsLayer = result.layer.takeIf { result.ready }
        } else layerNote = ""
    }
    fun locateNow() {
        locationNote = "Buscando tu ubicación…"
        // La posición del usuario nunca se sustituye por el centro de la ciudad.
        DeviceLocationProvider.request(context,
            onSuccess = { result ->
                userLocation = result
                locationNote = result.accuracyMeters?.let { "Tu posición · precisión aproximada ±${it.toInt()} m" }
                    ?: "Tu posición en el mapa"
            },
            onError = { locationNote = it },
            requireFresh = true
        )
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locateNow()
        } else locationNote = "Activa Ubicación precisa para marcar tu posición."
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFE2F2F4))) {
        EcoNativeMap(
            latitude = userLocation?.latitude ?: climate.latitude,
            longitude = userLocation?.longitude ?: climate.longitude,
            reports = reports, officialRecords = official.items,
            incidentFilter = incidentFilter,
            wmsLayer = activeWmsLayer?.takeIf { it.displayName == thematicLayer },
            layerOpacity = layerOpacity, showOfficialRecords = showOfficial,
            userLocation = userLocation,
            onReportSelected = { selectedReport = it },
            onOfficialSelected = { selectedOfficial = it },
            modifier = Modifier.fillMaxSize()
        )

        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)
            .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = .97f), shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Volver") }
                    Column(Modifier.weight(1f)) {
                        Text("Mapa", style = MaterialTheme.typography.titleMedium)
                        Text(if (userLocation != null) "Tu ubicación actual" else climate.place,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, color = Color(0xFF55717A))
                    }
                    TextButton(onClick = { optionsOpen = !optionsOpen }) {
                        Icon(Icons.Outlined.Tune, null, tint = AmparaOrange)
                        Spacer(Modifier.width(4.dp))
                        Text(if (optionsOpen) "Cerrar" else "Opciones", color = Color(0xFF385E69))
                    }
                }
            }
            if (optionsOpen) {
                Surface(shape = RoundedCornerShape(23.dp), color = Color.White.copy(alpha = .98f)) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(value = query, onValueChange = { query = it.take(80) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Buscar ciudad o distrito") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { if (query.trim().length >= 3) onSearch(query) }) {
                                    Icon(Icons.Outlined.Search, "Buscar")
                                }
                            })
                        geo.results.take(4).forEach { place ->
                            TextButton(onClick = {
                                onCity(place.display(), place.latitude, place.longitude)
                                userLocation = null // No inventar marcador GPS al buscar otra ciudad.
                                query = ""; onClearSearch(); optionsOpen = false
                            }) { Text(place.display()) }
                        }
                        geo.error?.let { Text("No se encontró el lugar. Intenta otra búsqueda.",
                            style = MaterialTheme.typography.bodySmall) }
                        Text("Mostrar en el mapa", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("Todos", "Huaico", "Inundación", "Derrumbe").forEach { f ->
                                FilterChip(selected = incidentFilter == f,
                                    onClick = { incidentFilter = f }, label = { Text(f) })
                            }
                        }
                        Text("Zonas de riesgo", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("Ninguna", "Inundación", "Movimientos en masa").forEach { layer ->
                                FilterChip(selected = thematicLayer == layer,
                                    onClick = { thematicLayer = layer },
                                    label = { Text(if (layer == "Ninguna") "Ocultar" else layer) })
                            }
                        }
                        if (layerNote.isNotEmpty()) Text(layerNote,
                            style = MaterialTheme.typography.bodySmall)
                        if (activeWmsLayer != null) {
                            Text("Visibilidad de zonas", style = MaterialTheme.typography.bodySmall)
                            Slider(value = layerOpacity, onValueChange = { layerOpacity = it },
                                valueRange = .20f.. .85f)
                            Text("Colores: verde = menor susceptibilidad; naranja y rojo = mayor susceptibilidad.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Mostrar registros de INDECI", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            LiquidGlassSwitch(checked = showOfficial,
                                label = "Mostrar registros de INDECI", onCheckedChange = {
                                    showOfficial = it
                                    if (it && official.items.isEmpty()) onLoadOfficial()
                                })
                        }
                        TextButton(onClick = { sourcesOpen = !sourcesOpen }) {
                            Text(if (sourcesOpen) "Ocultar fuentes" else "Ver fuentes oficiales")
                        }
                        if (sourcesOpen) {
                            listOf(
                                "GEOPERÚ · Inundaciones" to "https://visor.geoperu.gob.pe/?layers=1184",
                                "GEOPERÚ · Movimientos de tierra" to "https://visor.geoperu.gob.pe/?layers=1186",
                                "CENEPRED" to OfficialDataRepository.CENEPRED_URL,
                                "INDECI" to OfficialDataRepository.INDECI_URL,
                                "SENAMHI" to "https://www.senamhi.gob.pe/"
                            ).forEach { (name, url) ->
                                TextButton(onClick = { uri.openUri(url) }) { Text(name) }
                            }
                        }
                        Text("Las zonas de riesgo no muestran emergencias en tiempo real.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = Color.White.copy(alpha = .96f),
                shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(locationNote, style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F6470), maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context,
                                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locateNow()
                            else locationLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp)); Text("Mi ubicación")
                        }
                        Button(onClick = { onReport(userLocation) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AmparaOrange)) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp)); Text("Crear reporte")
                        }
                    }
                }
            }
        }
    }

    selectedReport?.let { report ->
        AlertDialog(onDismissRequest = { selectedReport = null },
            title = { Text("${report.type} · Reporte ciudadano") },
            text = { Text(report.description + "\n\nInformación no verificada.") },
            confirmButton = { TextButton(onClick = { selectedReport = null }) { Text("Cerrar") } })
    }
    selectedOfficial?.let { record ->
        AlertDialog(onDismissRequest = { selectedOfficial = null },
            title = { Text("Registro de INDECI") },
            text = { Text("${record.kind} · ${record.district}, ${record.department}\n${record.dateLabel()}\nRegistro histórico; no es una alerta vigente.") },
            confirmButton = { TextButton(onClick = { selectedOfficial = null }) { Text("Cerrar") } })
    }
}
