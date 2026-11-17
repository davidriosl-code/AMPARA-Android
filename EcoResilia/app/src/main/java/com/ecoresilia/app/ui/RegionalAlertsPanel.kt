package com.ecoresilia.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ecoresilia.app.BuildConfig
import com.ecoresilia.app.push.AlertSubscriptions

/** Una zona elegida + tipos. Requiere consentimiento; luego renovación automática. */
@Composable
internal fun RegionalAlertsPanel() {
    val context = LocalContext.current
    val config = remember(context) { AlertSubscriptions.saved(context) }
    var region by remember { mutableStateOf(config.region) }
    var types by remember { mutableStateOf(config.types) }
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (config.enabled)
        "Tus preferencias de avisos están guardadas."
        else "Selecciona tu región y qué información deseas recibir.") }
    var enabled by remember { mutableStateOf(config.enabled) }

    fun persist(activate: Boolean) {
        if (!BuildConfig.FIREBASE_CONFIGURED) { status = "Falta configurar Firebase para esta APK."; return }
        try {
            AlertSubscriptions.save(context, activate, region, types)
            enabled = activate
            status = "Guardando tus preferencias…"
            AlertSubscriptions.syncAsync(context) { result ->
                status = when {
                    result.startsWith("Suscripción FCM confirmada") -> "Tus avisos están activados para ${AlertSubscriptions.regiones[region]}."
                    result.startsWith("Avisos desactivados") -> "Los avisos están desactivados."
                    else -> "Guardamos tus preferencias. Se actualizarán cuando haya conexión."
                }
            }
        } catch (_: IllegalArgumentException) { status = "Elige al menos un tipo de información." }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) persist(true) else status = "Activa las notificaciones desde Ajustes de Android para recibir avisos visibles."
    }
    FrostCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Avisos de mi zona", style = MaterialTheme.typography.titleMedium)
            Text("Elige dónde quieres recibir avisos y qué temas te interesan.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Departamento: ${AlertSubscriptions.regiones[region]} ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AlertSubscriptions.regiones.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { region = key; expanded = false })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = AlertSubscriptions.BOLETINES in types, onCheckedChange = { checked ->
                    types = if (checked) types + AlertSubscriptions.BOLETINES else types - AlertSubscriptions.BOLETINES
                })
                Column {
                    Text("Boletines preventivos de quebradas", style = MaterialTheme.typography.bodyMedium)
                    Text("Información preventiva de fuentes oficiales.", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = AlertSubscriptions.COMUNIDAD in types, onCheckedChange = { checked ->
                    types = if (checked) types + AlertSubscriptions.COMUNIDAD else types - AlertSubscriptions.COMUNIDAD
                })
                Column {
                    Text("Reportes ciudadanos revisados", style = MaterialTheme.typography.bodyMedium)
                    Text("Información compartida y revisada.", style = MaterialTheme.typography.labelSmall)
                }
            }
            Button(enabled = types.isNotEmpty() && BuildConfig.FIREBASE_CONFIGURED, modifier = Modifier.fillMaxWidth(), onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else persist(true)
            }) { Text(if (enabled) "Guardar mis avisos" else "Activar avisos en mi celular") }
            if (enabled) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { persist(false) }) {
                    Text("Desactivar mis avisos")
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text("Necesitas internet para recibir nuevos avisos. Sigue siempre las alertas oficiales.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
