package com.ecoresilia.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sin scraping de alertas. Lectura transparente de emergencias REGISTRADAS en ArcGIS INDECI. */
data class OfficialIncident(
    val id: String,
    val kind: String,
    val district: String,
    val department: String,
    val timestamp: Long?,
    val lat: Double?,
    val lon: Double?
) {
    fun dateLabel(): String = timestamp?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE")).format(Date(it))
    } ?: "Fecha no disponible"
}

class OfficialDataRepository {
    companion object {
        const val INDECI_URL = "https://geosinpad.indeci.gob.pe/indeci/rest/services/Emergencias/EMERGENCIAS_SINPAD/MapServer/0"
        const val CENEPRED_URL = "https://sig.cenepred.gob.pe/sigrid/rest/services/S05CP/MapServer"
    }
    suspend fun recentRegistered(): List<OfficialIncident> = withContext(Dispatchers.IO) {
        val endpoint = "$INDECI_URL/query?where=1%3D1&outFields=IDE_SINPAD%2CFECHA%2CFENOMENO%2CDISTRITO%2CDEPARTAMENTO&returnGeometry=true&outSR=4326&orderByFields=FECHA%20DESC&resultRecordCount=25&f=json"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("Servicio no disponible: ${connection.responseCode}")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            if (root.has("error")) error("El servicio oficial rechazó la consulta")
            val features = root.optJSONArray("features") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until features.length()) {
                    val record = features.optJSONObject(index) ?: continue
                    val attrs = record.optJSONObject("attributes") ?: continue
                    val geom = record.optJSONObject("geometry")
                    add(OfficialIncident(
                        id = attrs.optString("IDE_SINPAD", "Sin código"),
                        kind = attrs.optString("FENOMENO", "Emergencia registrada"),
                        district = attrs.optString("DISTRITO", "No informado"),
                        department = attrs.optString("DEPARTAMENTO", "Perú"),
                        timestamp = attrs.optLong("FECHA").takeIf { it > 0L },
                        lat = geom?.optDouble("y")?.takeIf { it.isFinite() && it in -90.0..90.0 },
                        lon = geom?.optDouble("x")?.takeIf { it.isFinite() && it in -180.0..180.0 }
                    ))
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
