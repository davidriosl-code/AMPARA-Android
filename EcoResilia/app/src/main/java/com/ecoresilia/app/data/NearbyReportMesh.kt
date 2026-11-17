package com.ecoresilia.app.data

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.util.UUID

/** Estado visible de la demo: una sola conexión, siempre con aceptación de ambas personas. */
data class NearbyPeer(val endpointId: String, val name: String)
data class NearbyDemoState(
    val mode: String = "OFF",
    val status: String = "Conexión cercana desactivada",
    val peers: List<NearbyPeer> = emptyList(),
    val pendingName: String = "",
    val pendingCode: String = "",
    val connectedName: String = "",
    val lastReceived: String = "",
    val lastSent: String = ""
) {
    val connected: Boolean get() = connectedName.isNotBlank()
    val needsApproval: Boolean get() = pendingCode.isNotBlank()
}

/**
 * EcoResilia: demostración P2P entre dos Android con Google Play Services.
 * Funciona sin acceso a Internet, pero requiere radios Bluetooth / Wi-Fi activas.
 * Reportes entre usuarios con confirmación explícita, siempre NO VERIFICADOS.
 * No se retransmiten mensajes ni se difunden alertas oficiales. La ubicación es opt-in.
 */
class NearbyReportMesh(
    context: Context,
    private val onReport: (CommunityFeedEntry, () -> Unit) -> Unit,
    private val onState: (NearbyDemoState) -> Unit
) {
    private val client = Nearby.getConnectionsClient(context)
    private val serviceId = "com.ecoresilia.app.offline"
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val displayName = "EcoResilia-" + UUID.randomUUID().toString().take(4).uppercase()
    private var ui = NearbyDemoState()
    private var pendingId: String? = null
    private var connectedId: String? = null
    private val discovered = linkedMapOf<String, NearbyPeer>()
    private var expectedReceipt: String? = null

    @Synchronized
    private fun render(next: NearbyDemoState) {
        ui = next
        onState(next)
    }
    private fun note(message: String) = render(ui.copy(status = message))

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (endpointId != connectedId || payload.type != Payload.Type.BYTES) return
            try {
                val bytes = payload.asBytes() ?: return
                if (bytes.size > 4096) { note("Mensaje rechazado: demasiado grande"); return }
                val obj = JSONObject(String(bytes, Charsets.UTF_8))
                if (obj.optInt("version") !in 1..2) return
                if (obj.optString("kind") == "ack") {
                    val id = obj.optString("id")
                    if (id == expectedReceipt) {
                        expectedReceipt = null
                        render(ui.copy(lastSent = "Recibido y guardado en el segundo teléfono", status = "Envío confirmado por el receptor"))
                    }
                    return
                }
                if (obj.optString("kind") != "demo_report") return
                val id = obj.getString("id")
                val author = obj.getString("author")
                val type = obj.getString("type")
                val description = obj.getString("description").trim()
                val locationIncluded = obj.optBoolean("has_location", obj.optInt("version") == 1)
                val lat = if (locationIncluded && !obj.isNull("lat")) obj.optDouble("lat", Double.NaN) else null
                val lon = if (locationIncluded && !obj.isNull("lon")) obj.optDouble("lon", Double.NaN) else null
                val time = obj.getLong("time")
                val now = System.currentTimeMillis()
                if (id.length !in 1..80 || author.length !in 1..120 || type !in setOf("Huaico", "Inundación", "Derrumbe") ||
                    description.length !in 8..500 ||
                    (obj.optInt("version") == 1 && !description.contains("SIMULACRO", ignoreCase = true)) ||
                    (lat != null && (!lat.isFinite() || lat !in -90.0..90.0)) ||
                    (lon != null && (!lon.isFinite() || lon !in -180.0..180.0)) ||
                    ((lat == null) != (lon == null)) ||
                    time < now - 7L * 24 * 60 * 60 * 1000 || time > now + 15L * 60 * 1000) {
                    note("Reporte rechazado: formato o contenido no válido")
                    return
                }
                val report = CommunityFeedEntry(
                    entryId = "incident:" + id, remoteId = id, ownerUid = "", category = type,
                    description = description, zone = "Reporte recibido · ubicación opcional",
                    latitude = lat, longitude = lon, occurredAtEpochMs = time,
                    source = "nearby", syncStatus = "LOCAL"
                )
                onReport(report) {
                    if (endpointId == connectedId) {
                        val receipt = JSONObject().put("version", 1).put("kind", "ack").put("id", id).toString()
                        client.sendPayload(endpointId, Payload.fromBytes(receipt.toByteArray(Charsets.UTF_8)))
                    }
                    render(ui.copy(
                        lastReceived = "${report.category}: ${report.description.take(70)}",
                        status = "Reporte ciudadano recibido y guardado localmente (NO verificado)"
                    ))
                }
            } catch (_: Exception) { note("Se recibió un mensaje inválido") }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }
    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (pendingId != null || connectedId != null) {
                client.rejectConnection(endpointId)
                return
            }
            pendingId = endpointId
            render(ui.copy(pendingName = info.endpointName.take(40), pendingCode = info.authenticationDigits,
                status = "Compara el código en ambos teléfonos y acepta SOLO si coincide"))
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedId = endpointId
                pendingId = null
                client.stopAdvertising()
                client.stopDiscovery()
                val name = ui.pendingName.ifBlank { discovered[endpointId]?.name ?: "Teléfono cercano" }
                render(ui.copy(mode = "CONNECTED", peers = emptyList(), pendingName = "", pendingCode = "",
                    connectedName = name, status = "Conectados. Elige un simulacro para compartir"))
            } else {
                pendingId = null
                render(ui.copy(pendingName = "", pendingCode = "", status = "Conexión rechazada o fallida. Intenta buscar nuevamente"))
            }
        }
        override fun onDisconnected(endpointId: String) {
            if (connectedId == endpointId) {
                connectedId = null
                expectedReceipt = null
                render(ui.copy(mode = "OFF", connectedName = "", status = "El otro teléfono se desconectó. Inicia una búsqueda nueva"))
            }
        }
    }
    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connectedId != null) return
            discovered[endpointId] = NearbyPeer(endpointId, info.endpointName.take(40))
            render(ui.copy(peers = discovered.values.toList(), status = "Selecciona el teléfono del profesor o compañero"))
        }
        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
            render(ui.copy(peers = discovered.values.toList()))
        }
    }
    fun startHosting() {
        stopQuietly()
        render(NearbyDemoState(mode = "HOST", status = "Visible como $displayName. El otro teléfono debe pulsar Buscar"))
        client.startAdvertising(displayName, serviceId, lifecycle, AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { note("No se pudo anunciar este dispositivo: ${it.message}") }
    }
    fun startSearching() {
        stopQuietly()
        render(NearbyDemoState(mode = "SEARCH", status = "Buscando otros teléfonos EcoResilia cercanos...") )
        client.startDiscovery(serviceId, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { note("No se pudo buscar dispositivos: ${it.message}") }
    }
    fun connectTo(endpointId: String) {
        val peer = discovered[endpointId] ?: return
        if (ui.mode != "SEARCH" || pendingId != null || connectedId != null) return
        note("Solicitando conexión a ${peer.name}...")
        client.requestConnection(displayName, endpointId, lifecycle)
            .addOnFailureListener { note("No se pudo solicitar conexión: ${it.message}") }
    }
    fun approvePendingConnection() {
        val id = pendingId ?: return
        pendingId = null
        client.acceptConnection(id, payloadCallback)
            .addOnFailureListener { note("Error al aceptar conexión: ${it.message}") }
        note("Aceptado. El otro teléfono también debe aceptar")
    }
    fun rejectPendingConnection() {
        val id = pendingId ?: return
        pendingId = null
        client.rejectConnection(id)
        render(ui.copy(pendingCode = "", pendingName = "", status = "Conexión rechazada"))
    }
    /** Transferencia individual elegida por el usuario. GPS opt-in; NO es una alerta oficial. */
    fun shareReport(report: CommunityFeedEntry, sharePreciseLocation: Boolean) {
        val endpointId = connectedId
        if (endpointId == null) { note("Primero conecta los dos teléfonos"); return }
        if (report.source == "nearby" || report.description.trim().length !in 8..500) {
            note("No se pueden reenviar reportes ajenos ni enviar mensajes incompletos")
            return
        }
        val raw = JSONObject().apply {
            put("version", 2); put("kind", "demo_report")
            put("id", report.remoteId.ifBlank { report.entryId.removePrefix("incident:") }); put("author", "EcoResilia-local")
            put("type", report.category); put("description", report.description.take(500))
            val hasLocation = sharePreciseLocation && report.latitude != null && report.longitude != null
            put("has_location", hasLocation)
            if (hasLocation) { put("lat", report.latitude); put("lon", report.longitude) }
            put("time", report.occurredAtEpochMs)
        }.toString()
        val bytes = raw.toByteArray(Charsets.UTF_8)
        if (bytes.size > 4096) { note("El reporte es demasiado largo para transmitirlo"); return }
        expectedReceipt = report.remoteId.ifBlank { report.entryId.removePrefix("incident:") }
        note("Enviando reporte no verificado... Esperando confirmación del otro celular")
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnFailureListener { note("No se pudo transmitir: ${it.message}") }
    }
    private fun stopQuietly() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        discovered.clear()
        pendingId = null
        connectedId = null
        expectedReceipt = null
    }
    fun stop() {
        stopQuietly()
        render(NearbyDemoState(status = "Red cercana detenida"))
    }
}
