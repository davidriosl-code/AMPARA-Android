package com.ecoresilia.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecoresilia.app.data.EmergencyDatabase
import com.ecoresilia.app.data.ReceivedNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ecoresilia.app.data.OfficialDataRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AlertsScreen(official: OfficialUiState, onLoadOfficial: () -> Unit) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val dao = remember(context) { EmergencyDatabase.get(context).notices() }
    val inbox by dao.observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var advancedOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("Mis avisos", "Infórmate sobre tu zona")
        FrostCard {
            Text("Avisos recibidos", fontWeight = FontWeight.Bold)
            Text("Aquí verás los avisos guardados en tu teléfono.", style = MaterialTheme.typography.bodySmall)
            Text("${inbox.size} mensaje(s) guardado(s)", style = MaterialTheme.typography.labelMedium)
            if (advancedOpen) OutlinedButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    dao.insert(ReceivedNotice(
                        noticeId = "demo-" + UUID.randomUUID(),
                        title = "DEMOSTRACIÓN · Recordatorio local",
                        body = "Esta es una prueba offline, NO una alerta oficial ni un desastre real.",
                        receivedAtEpochMs = System.currentTimeMillis(),
                        origin = "Prueba local"
                    ))
                }
            }) { Text("Crear mensaje de prueba offline") }
            if (inbox.isEmpty()) Text("No hay mensajes guardados todavía.", style = MaterialTheme.typography.bodySmall)
            inbox.take(30).forEach { notice ->
                HorizontalDivider()
                Text(notice.title, fontWeight = FontWeight.SemiBold)
                Text(notice.body)
                Text("${notice.origin} · ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(notice.receivedAtEpochMs))}", style = MaterialTheme.typography.labelSmall)
            }
        }
        RegionalAlertsPanel()
        TextButton(onClick = { advancedOpen = !advancedOpen }, modifier = Modifier.fillMaxWidth()) {
            Text(if (advancedOpen) "Ocultar opciones de prueba" else "Opciones de prueba")
        }
        if (advancedOpen) MessagingSetupPanel()
        InfoNote("AMPARA no reemplaza las alertas de las autoridades. En peligro inmediato, sigue sus indicaciones.")
        FrostCard {
            Text("Fuentes oficiales", fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { uri.openUri("https://www.senamhi.gob.pe/") }, modifier = Modifier.fillMaxWidth()) { Text("SENAMHI · Avisos meteorológicos") }
            OutlinedButton(onClick = { uri.openUri("https://sigrid.cenepred.gob.pe/sigridv3/") }, modifier = Modifier.fillMaxWidth()) { Text("CENEPRED · SIGRID") }
            OutlinedButton(onClick = { uri.openUri("https://www.gob.pe/indeci") }, modifier = Modifier.fillMaxWidth()) { Text("INDECI · Información institucional") }
        }
        TextButton(onClick = { historyOpen = !historyOpen }, modifier = Modifier.fillMaxWidth()) {
            Text(if (historyOpen) "Ocultar registros anteriores" else "Ver registros de emergencias anteriores")
        }
        if (historyOpen) FrostCard {
            Text("Registros anteriores de INDECI", fontWeight = FontWeight.Bold)
            Text("Esta lista es informativa; no indica emergencias activas.", style = MaterialTheme.typography.bodySmall)
            PrimaryAction("Consultar registros", onClick = onLoadOfficial, modifier = Modifier.fillMaxWidth())
            if (official.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            official.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            official.lastUpdated?.let {
                Text("Consulta realizada: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))}", style = MaterialTheme.typography.labelSmall)
            }
            if (!official.loading && official.error == null && official.lastUpdated != null && official.items.isEmpty()) Text("El servicio no devolvió registros para esta consulta.")
            official.items.take(15).forEach { event ->
                HorizontalDivider()
                Text(event.kind, fontWeight = FontWeight.SemiBold)
                Text("${event.district}, ${event.department} · ${event.dateLabel()}", style = MaterialTheme.typography.bodySmall)
                Text("Registro histórico", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { uri.openUri(OfficialDataRepository.INDECI_URL) }) { Text("Ver datos originales ↗") }
        }
    }
}
