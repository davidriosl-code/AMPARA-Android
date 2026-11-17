package com.ecoresilia.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ecoresilia.app.data.IncidentReport
import com.ecoresilia.app.data.NearbyDemoState
import com.ecoresilia.app.BuildConfig
import com.ecoresilia.app.ui.cloud.CloudCommunityPanel
import com.ecoresilia.app.ui.cloud.CloudReportsViewModel
import com.google.firebase.FirebaseApp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecoresilia.app.data.DeviceLocationProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommunityScreen(
    reports: List<IncidentReport>, nearbyStatus: NearbyDemoState,
    initialLatitude: Double, initialLongitude: Double,
    mapSelectedLocation: DeviceLocationProvider.Result? = null,
    onLocationConsumed: () -> Unit = {},
    onHost: () -> Unit, onSearch: () -> Unit, onStop: () -> Unit,
    onConnect: (String) -> Unit, onShareNearby: (com.ecoresilia.app.data.CommunityFeedEntry, Boolean) -> Unit,
    onCreateDemo: () -> Unit,
    onApproveNearby: () -> Unit, onRejectNearby: () -> Unit,
    onSubmit: (String, String, Double?, Double?, String) -> Unit
) {
    var type by remember { mutableStateOf("Huaico") }
    var description by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<DeviceLocationProvider.Result?>(null) }
    var locationNote by remember { mutableStateOf("Puedes compartir tu ubicación si lo deseas") }
    // La posición proviene de la lectura GPS elegida al pulsar «Crear reporte» en el mapa.
    // No usamos el centro de la ciudad como posición del ciudadano.
    var imageUri by remember { mutableStateOf("") }
    val context = LocalContext.current
    if (!(BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty())) {
        LaunchedEffect(mapSelectedLocation) {
            mapSelectedLocation?.let {
                selectedLocation = it
                locationNote = "Punto del mapa seleccionado · comprueba que sea correcto"
                onLocationConsumed()
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            imageUri = uri.toString()
        }
    }
    val permissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES
    ) else if (Build.VERSION.SDK_INT >= 31) arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION
    ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    var nearbyOpen by remember { mutableStateOf(false) }
    var intendedRole by remember { mutableStateOf("HOST") }
    var permissionsNote by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            permissionsNote = ""
            if (intendedRole == "HOST") onHost() else onSearch()
        } else permissionsNote = "Concede los permisos de dispositivos cercanos para iniciar la demostración."
    }
    fun activate(role: String) {
        intendedRole = role
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            if (role == "HOST") onHost() else onSearch()
        } else launcher.launch(permissions)
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine) locationNote = "Para una ubicación precisa, permite acceso PRECISO. Puedes guardar el reporte sin GPS."
        else DeviceLocationProvider.request(context,
            onSuccess = { selectedLocation = it; locationNote = "Tu ubicación actual está lista para el reporte" },
            onError = { locationNote = it }, requireFresh = true)
    }
    val valid = description.trim().length >= 8

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AmparaPageBanner("Comunidad", "Comparte lo que sucede cerca de ti")
        InfoNote("Los reportes de la comunidad aún no están verificados.")
        if (BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()) {
                val cloudVm: CloudReportsViewModel = viewModel(factory = CloudReportsViewModel.Factory(context.applicationContext))
                CloudCommunityPanel(cloudVm, nearbyConnected = nearbyStatus.connected,
                    initialLocation = mapSelectedLocation, onLocationConsumed = onLocationConsumed,
                    onShareNearby = onShareNearby)
        } else {
            FrostCard {
                Text("Nuevo reporte · Guardado local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Tu reporte se guardará en este teléfono.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Huaico", "Inundación", "Derrumbe").forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) })
                    }
                }
                OutlinedTextField(description, { description = it.take(500) }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Describe lo que observaste") })
                OutlinedButton(onClick = { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                    Text("Usar mi ubicación actual")
                }
                Text(locationNote, style = MaterialTheme.typography.bodySmall)
                Text("Solo se usa tu ubicación si lo autorizas.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text(if (imageUri.isBlank()) "Adjuntar fotografía (opcional)" else "Cambiar fotografía") }
                if (imageUri.isNotBlank()) PhotoPreview(imageUri)
                Button(onClick = {
                    val location = selectedLocation
                    onSubmit(type, description, location?.latitude, location?.longitude, imageUri)
                    description = ""; imageUri = ""
                }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Guardar reporte local") }
                Text("Reportes anteriores guardados: ${reports.size}. Ninguno se borra al actualizar.", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { nearbyOpen = !nearbyOpen }, modifier = Modifier.fillMaxWidth()) {
            Text(if (nearbyOpen) "Ocultar conexión entre celulares" else "Compartir con otro celular sin internet")
        }
        if (nearbyOpen) FrostCard {
            Text("Compartir entre celulares · Prueba", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Text("Enciende Bluetooth y Wi-Fi en ambos teléfonos. Pulsa Ofrecer en uno y Buscar en el otro. Comprueba que los códigos coincidan.",
                style = MaterialTheme.typography.bodySmall)
            Text("Estado: ${nearbyStatus.status}", color = MaterialTheme.colorScheme.primary)
            if (permissionsNote.isNotEmpty()) Text(permissionsNote, color = MaterialTheme.colorScheme.error)
            if (nearbyStatus.mode == "OFF") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { activate("HOST") }, modifier = Modifier.weight(1f)) { Text("Ofrecer") }
                    OutlinedButton(onClick = { activate("SEARCH") }, modifier = Modifier.weight(1f)) { Text("Buscar") }
                }
            }
            if (nearbyStatus.mode == "HOST") Text("Este teléfono está visible. Desde el segundo, pulsa Buscar y selecciona su nombre.",
                style = MaterialTheme.typography.bodySmall)
            if (nearbyStatus.mode == "SEARCH") {
                Text("Teléfonos encontrados", fontWeight = FontWeight.SemiBold)
                if (nearbyStatus.peers.isEmpty()) Text("Aún no aparecen teléfonos. Acerca ambos dispositivos y revisa los permisos.",
                    style = MaterialTheme.typography.bodySmall)
                nearbyStatus.peers.forEach { peer ->
                    OutlinedButton(onClick = { onConnect(peer.endpointId) }, modifier = Modifier.fillMaxWidth(),
                        enabled = !nearbyStatus.needsApproval) {
                        Text("Conectar con ${peer.name}")
                    }
                }
            }
            if (nearbyStatus.needsApproval) {
                Text("Solicitud de: ${nearbyStatus.pendingName}", fontWeight = FontWeight.Bold)
                Text("CÓDIGO: ${nearbyStatus.pendingCode}", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("El código debe ser idéntico en los dos teléfonos. NO aceptes si es diferente.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApproveNearby, modifier = Modifier.weight(1f)) { Text("Coincide: aceptar") }
                    OutlinedButton(onClick = onRejectNearby, modifier = Modifier.weight(1f)) { Text("Rechazar") }
                }
            }
            if (nearbyStatus.connected) {
                Text("Conectado con ${nearbyStatus.connectedName}", fontWeight = FontWeight.Bold)
                Text("Selecciona un reporte para compartirlo. Tú decides si compartes también la ubicación.",
                    style = MaterialTheme.typography.bodySmall)
                if (nearbyStatus.lastSent.isNotBlank()) Text(nearbyStatus.lastSent,
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (nearbyStatus.lastReceived.isNotBlank()) {
                Text("Último reporte recibido por proximidad:", fontWeight = FontWeight.SemiBold)
                Text(nearbyStatus.lastReceived, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onCreateDemo, modifier = Modifier.fillMaxWidth()) {
                Text("Crear simulacro local (NO publicar)")
            }
            if (nearbyStatus.mode != "OFF") {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Desconectar / detener") }
            }
            Text("Los reportes recibidos se identifican como no verificados.",
                style = MaterialTheme.typography.bodySmall)
        }
        // Todos los reportes aparecen arriba dentro de CloudCommunityPanel (Room).
    }
}

@Composable
private fun PhotoPreview(imageUri: String) {
    val context = LocalContext.current
    val bitmap = remember(imageUri) {
        try {
            context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) { null }
    }
    if (bitmap != null) Image(
        bitmap = bitmap.asImageBitmap(), contentDescription = "Foto adjunta del reporte",
        modifier = Modifier.fillMaxWidth().height(140.dp),
        contentScale = ContentScale.Crop
    ) else Text("No se pudo abrir la fotografía adjunta.", style = MaterialTheme.typography.bodySmall)
}
