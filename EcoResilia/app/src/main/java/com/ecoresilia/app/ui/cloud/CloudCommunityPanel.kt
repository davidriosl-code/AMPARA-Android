package com.ecoresilia.app.ui.cloud

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.ecoresilia.app.ui.FrostCard
import com.ecoresilia.app.ui.SectionHeading
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ecoresilia.app.data.DeviceLocationProvider
import com.ecoresilia.app.data.CommunityFeedEntry
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Se dibuja dentro del scroll de Comunidad: no crea otra pantalla ni otra Activity. */
@Composable
fun CloudCommunityPanel(vm: CloudReportsViewModel, nearbyConnected: Boolean,
    initialLocation: DeviceLocationProvider.Result? = null,
    onLocationConsumed: () -> Unit = {},
    onShareNearby: (CommunityFeedEntry, Boolean) -> Unit) {
    val reports by vm.feed.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val identity by vm.currentUser.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf("Huaico") }
    var description by rememberSaveable { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf("") }
    var imageUri by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    var currentLocation by remember { mutableStateOf<DeviceLocationProvider.Result?>(null) }
    var locationNote by remember { mutableStateOf("Ubicación opcional") }
    LaunchedEffect(initialLocation) {
        initialLocation?.let { selected ->
            currentLocation = selected
            locationNote = "Ubicación del mapa seleccionada · comprueba que sea correcta"
            if (zone.isBlank()) zone = "Mi zona actual"
            onLocationConsumed()
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!precise) locationNote = "No se concedió ubicación PRECISA. Puedes guardar indicando una zona referencial."
        else DeviceLocationProvider.request(context, onSuccess = { result ->
            currentLocation = result
            locationNote = result.accuracyMeters?.let { "Ubicación lista · precisión aproximada ±${it.toInt()} m" }
                ?: "Ubicación actual lista"
            if (zone.isBlank()) zone = "Mi zona actual"
        }, onError = { locationNote = it }, requireFresh = true)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            imageUri = uri.toString()
        }
    }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var authExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var confirmShare by remember { mutableStateOf<CommunityFeedEntry?>(null) }
    var sharePreciseLocation by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.start(); vm.requestSync() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SectionHeading("Mi comunidad", "Tus reportes y tu cuenta")
        FrostCard {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Outlined.AccountCircle, null, tint = Color(0xFF14A6AD))
                Text("Mi cuenta", style = MaterialTheme.typography.titleMedium)
            }
            Text("Aquí puedes guardar y consultar tus reportes.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF638390))
        if (!vm.isOnline()) Text("Sin conexión: puedes guardar borradores para enviarlos más adelante.", color = MaterialTheme.colorScheme.primary)
        if (identity == null) {
            Text("Preparando sesión Firebase...", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.start() }, enabled = !busy) { Text("Reintentar acceso") }
        } else if (identity!!.anonymous) {
            Text("Estás usando AMPARA como invitado", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("Vincula un correo para recuperar tus reportes en otro celular.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Cuenta: ${identity!!.email ?: "Correo vinculado"}",
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }

        if (notice != null) {
            Text(notice ?: "", color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = vm::dismissNotice) { Text("Cerrar aviso") }
        }
        OutlinedButton(onClick = { authExpanded = !authExpanded }, enabled = !busy) {
            Text(if (authExpanded) "Ocultar acceso con correo" else "Vincular cuenta / iniciar sesión")
        }
        if (authExpanded) {
            OutlinedTextField(value = email, onValueChange = { email = it.take(120) }, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(value = password, onValueChange = { password = it.take(128) }, label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Text("Si este celular ya tiene reportes en una sesión temporal, vincular un correo NUEVO conserva el mismo usuario Firebase. Ingresar a una cuenta existente cambia el usuario y NO traslada automáticamente esos reportes. No uses la contraseña de Gmail salvo que sea tu contraseña registrada expresamente en Firebase.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.register(email, password) },
                enabled = !busy && email.isNotBlank() && password.length >= 6,
                modifier = Modifier.fillMaxWidth()) { Text("Vincular sesión a un correo NUEVO") }
            OutlinedButton(onClick = { vm.signIn(email, password) },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("Ingresar a cuenta ya existente") }
            if (identity?.anonymous == false) OutlinedButton(onClick = vm::signOut, enabled = !busy) { Text("Cerrar sesión") }
        }
        }
        FrostCard {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Outlined.EditNote, null, tint = Color(0xFF14A6AD))
                Text(if (editId == null) "Nuevo reporte" else "Editar mi reporte",
                    style = MaterialTheme.typography.titleMedium)
            }
        Text("Tu reporte se guarda aunque no tengas internet. Se enviará al recuperar conexión.",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B8493))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Huaico", "Inundación", "Derrumbe").forEach { option ->
                FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
            }
        }
        OutlinedTextField(description, { description = it.take(500) }, label = { Text("¿Qué ocurrió?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(zone, { zone = it.take(100) }, label = { Text("Barrio o zona") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (editId == null) {
            OutlinedButton(onClick = {
                locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.MyLocation, null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Usar mi ubicación actual")
            }
            Text(locationNote, style = MaterialTheme.typography.bodySmall)
            Text("Tu ubicación exacta no se comparte con otras personas sin tu permiso.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) {
                Text(if (imageUri.isBlank()) "Adjuntar foto (opcional)" else "Cambiar foto adjunta")
            }
            if (imageUri.isNotBlank()) Text("Foto guardada únicamente en este teléfono; no se envía a Firebase ni por Nearby.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                vm.save(editId, category, description, zone, currentLocation?.latitude,
                    currentLocation?.longitude, imageUri) {
                    editId = null; description = ""; zone = ""; imageUri = ""; currentLocation = null
                    locationNote = "Ubicación no solicitada · puedes enviar sin GPS"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && (editId == null || (identity != null && vm.isOnline())) &&
                description.trim().length >= 8 && zone.trim().length >= 2
        ) { Text(if (busy) "Guardando..." else if (editId == null) "Guardar reporte" else "Guardar cambios") }
        if (editId != null) OutlinedButton(onClick = { editId = null; description = ""; zone = ""; currentLocation = null }) { Text("Cancelar edición") }

        }
        SectionHeading("Mis reportes", "${reports.size} guardados · ${pending.size} por enviar")
        Text("Puedes verlos también sin internet.", style = MaterialTheme.typography.bodySmall)
        if (loading && reports.isEmpty()) Text("Actualizando copias desde Firebase...", style = MaterialTheme.typography.bodySmall)
        if (reports.isEmpty() && !loading) Text("Todavía no hay reportes guardados en este dispositivo.", style = MaterialTheme.typography.bodySmall)
        reports.forEach { report ->
            Surface(modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = .87f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .95f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Place, null, tint = Color(0xFF17A9AD))
                        Text("${report.category} · ${report.zone}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF264B59))
                    }
                    Text(report.description, color = Color(0xFF466778),
                        style = MaterialTheme.typography.bodyMedium)
                    val label = when {
                        report.source == "nearby" -> "Recibido de otro celular · No verificado"
                        report.source == "local" -> "Guardado en este teléfono"
                        report.syncStatus == "PENDING" -> "Se enviará cuando tengas internet"
                        else -> "Guardado en mi cuenta (privado)"
                    }
                    Surface(color = when {
                            report.source == "nearby" -> Color(0xFFE2ECFC)
                            report.syncStatus == "PENDING" -> Color(0xFFFFF1D6)
                            else -> Color(0xFFDDF5ED)
                        }, shape = RoundedCornerShape(13.dp)) {
                        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            color = when {
                                report.source == "nearby" -> Color(0xFF40658F)
                                report.syncStatus == "PENDING" -> Color(0xFF936624)
                                else -> Color(0xFF147B72)
                            }, style = MaterialTheme.typography.labelMedium)
                    }
                    if (nearbyConnected && report.source != "nearby") {
                        OutlinedButton(onClick = { confirmShare = report; sharePreciseLocation = false }) {
                            Text("Compartir por Nearby (sin internet)")
                        }
                    }
                    if (report.source == "cloud" && report.syncStatus == "SYNCED") {
                        Text(if (report.lastServerConfirmedAtEpochMs > 0L)
                            "Última confirmación del servidor: " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(report.lastServerConfirmedAtEpochMs))
                            else "Copia local de Firebase; actualización en vivo no comprobada",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.source == "cloud" && report.syncStatus == "SYNCED" && identity?.uid == report.ownerUid) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                editId = report.remoteId
                                category = report.category
                                description = report.description
                                zone = report.zone
                                currentLocation = if (report.latitude != null && report.longitude != null)
                                    DeviceLocationProvider.Result(report.latitude, report.longitude, true) else null
                            }, enabled = !busy && vm.isOnline()) { Text("Editar") }
                            OutlinedButton(onClick = { confirmDelete = report.remoteId }, enabled = !busy && vm.isOnline()) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = vm::requestSync, enabled = !busy && identity != null,
            modifier = Modifier.fillMaxWidth()) { Text("Sincronizar reportes") }
    }
    if (confirmShare != null) {
        val selected = confirmShare!!
        AlertDialog(
            onDismissRequest = { confirmShare = null },
            title = { Text("Compartir con el celular conectado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Se enviará un reporte ciudadano NO VERIFICADO. No se publicará en Firebase ni como alerta oficial.")
                    Text(selected.category + " · " + selected.description.take(120))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = sharePreciseLocation, enabled = selected.latitude != null && selected.longitude != null,
                            onCheckedChange = { sharePreciseLocation = it })
                        Text("Compartir también mi posición exacta con el otro celular")
                    }
                    Text("Si no marcas la casilla, el reporte viaja sin coordenadas.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onShareNearby(selected, sharePreciseLocation)
                    confirmShare = null
                }) { Text("Enviar al dispositivo") }
            },
            dismissButton = { TextButton(onClick = { confirmShare = null }) { Text("Cancelar") } }
        )
    }
    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Eliminar reporte") },
            text = { Text("Se eliminará el reporte de tu cuenta Firestore y su copia local. Los reportes solo del teléfono permanecen intactos.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = confirmDelete
                    confirmDelete = null
                    if (id != null) vm.delete(id)
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } }
        )
    }
}
