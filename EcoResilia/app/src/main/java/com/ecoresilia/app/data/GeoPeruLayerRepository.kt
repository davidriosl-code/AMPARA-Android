package com.ecoresilia.app.data

import android.graphics.BitmapFactory
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import org.xmlpull.v1.XmlPullParser

/** Servicio oficial GEOPERÚ. Susceptibilidad territorial, NO alerta vigente ni diagnóstico de una vivienda. */
data class GeoPeruWmsLayer(val displayName: String, val endpoint: String, val layerName: String)
data class GeoPeruInspection(val ready: Boolean, val message: String, val layer: GeoPeruWmsLayer? = null)

object GeoPeruLayerRepository {
    private const val FLOOD = "https://espacialg.geoperu.gob.pe/geoserver/geoperu/peru_inundacion_group_/wms"
    private const val MASS = "https://espacialg.geoperu.gob.pe/geoserver/geoperu/peru_inundacion_mmasa_group_/wms"

    fun endpoint(name: String): String? = when (name) {
        "Inundación" -> FLOOD
        "Movimientos en masa" -> MASS
        else -> null
    }

    private fun expectedName(name: String): String = when (name) {
        "Inundación" -> "peru_inundacion_group_"
        else -> "peru_inundacion_mmasa_group_"
    }

    suspend fun inspect(name: String, latitude: Double, longitude: Double): GeoPeruInspection =
        withContext(Dispatchers.IO) {
            val service = endpoint(name)
                ?: return@withContext GeoPeruInspection(false, "No hay capa GEOPERÚ seleccionada.")
            try {
                val layerName = readLayerName(service, expectedName(name))
                    ?: return@withContext GeoPeruInspection(false,
                        "GEOPERÚ respondió, pero no se encontró el grupo de capas esperado. No se dibuja una capa distinta por error.")
                val layer = GeoPeruWmsLayer(name, service, layerName)
                // No basta GetCapabilities: se comprueba también una respuesta PNG de GetMap.
                val (x, y) = GeoPeruWmsUrl.tileXY(latitude, longitude, 8)
                val probeUrl = GeoPeruWmsUrl.getMapUrl(service, layerName, 8, x, y, 64)
                val conn = (URL(probeUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("Accept", "image/png")
                    setRequestProperty("User-Agent", "EcoResilia-Android/0.9 (WMS preview)")
                }
                try {
                    if (conn.responseCode !in 200..299)
                        return@withContext GeoPeruInspection(false,
                            "GEOPERÚ no entregó el mapa (HTTP ${conn.responseCode}). Se mantiene OpenStreetMap.")
                    val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    if (bitmap == null)
                        return@withContext GeoPeruInspection(false,
                            "GEOPERÚ respondió, pero no devolvió una imagen WMS válida. Se mantiene OpenStreetMap.")
                    bitmap.recycle()
                } finally {
                    conn.disconnect()
                }
                GeoPeruInspection(true,
                    "GEOPERÚ respondió con una imagen WMS para la zona consultada. Los colores representan susceptibilidad, NO una emergencia activa.",
                    layer)
            } catch (e: Exception) {
                // En RC4 el DOM de Android lanzaba UnsupportedOperationException al llamar
                // a setXIncludeAware(false). El lector Pull de abajo evita esa API.
                Log.w("EcoResiliaGeoPeru", "Error al comprobar WMS: $name", e)
                GeoPeruInspection(false,
                    "No se pudo cargar la capa GEOPERÚ (${e.javaClass.simpleName}). " +
                        "OpenStreetMap sigue disponible. Revisa la conexión o los registros de la app.")
            }
        }

    /**
     * GetCapabilities en Android: XmlPullParser evita DocumentBuilderFactory.setXIncludeAware,
     * que en algunos dispositivos lanza UnsupportedOperationException.
     *
     * Solo se acepta un <Name> hijo directo de <Layer> cuyo nombre coincida con
     * la capa esperada; no se selecciona una capa distinta silenciosamente.
     */
    private fun readLayerName(endpoint: String, expected: String): String? {
        val conn = (URL(GeoPeruWmsUrl.capabilitiesUrl(endpoint)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("Accept", "application/xml,text/xml")
            setRequestProperty("User-Agent", "EcoResilia-Android/0.9 (WMS capabilities)")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "UTF-8")
                val layers = ArrayDeque<Int>()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "Layer" -> layers.addLast(parser.depth)
                                "Name" -> {
                                    if (layers.isNotEmpty() && parser.depth == layers.peekLast() + 1) {
                                        val candidate = parser.nextText().trim()
                                        if (candidate.substringAfter(':') == expected) return candidate
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "Layer" && layers.isNotEmpty() && parser.depth == layers.peekLast()) {
                                layers.removeLast()
                            }
                        }
                        XmlPullParser.DOCDECL -> {
                            throw IllegalArgumentException("No se admite DOCTYPE en GetCapabilities")
                        }
                    }
                    event = parser.next()
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }
}
