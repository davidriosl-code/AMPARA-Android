package com.ecoresilia.app.data

import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/** Peticiones WMS 1.1.1 en Web Mercator; sin tokens ni claves privadas. */
object GeoPeruWmsUrl {
    private const val WEB_MERCATOR_HALF = 20037508.342789244

    fun tileXY(latitude: Double, longitude: Double, zoom: Int): Pair<Int, Int> {
        require(zoom in 0..19 && latitude.isFinite() && longitude.isFinite())
        val lat = latitude.coerceIn(-85.05112878, 85.05112878)
        val lon = longitude.coerceIn(-180.0, 180.0)
        val n = 2.0.pow(zoom.toDouble())
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n.toInt() - 1)
        val phi = lat * PI / 180.0
        val mercator = ln((1.0 + sin(phi)) / cos(phi))
        val y = floor((1.0 - mercator / PI) / 2.0 * n).toInt().coerceIn(0, n.toInt() - 1)
        return x to y
    }

    fun bbox3857(zoom: Int, x: Int, y: Int): String {
        require(zoom in 0..19)
        val n = 2.0.pow(zoom.toDouble())
        val xmin = (2.0 * x / n - 1.0) * WEB_MERCATOR_HALF
        val xmax = (2.0 * (x + 1) / n - 1.0) * WEB_MERCATOR_HALF
        val ymax = (1.0 - 2.0 * y / n) * WEB_MERCATOR_HALF
        val ymin = (1.0 - 2.0 * (y + 1) / n) * WEB_MERCATOR_HALF
        return "$xmin,$ymin,$xmax,$ymax"
    }

    fun capabilitiesUrl(endpoint: String): String =
        endpoint.substringBefore('?') + "?service=WMS&request=GetCapabilities&version=1.3.0"

    fun getMapUrl(endpoint: String, layerName: String, zoom: Int, x: Int, y: Int, size: Int = 256): String {
        require(size in 32..512)
        val layer = URLEncoder.encode(layerName, "UTF-8")
        return endpoint.substringBefore('?') +
            "?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap" +
            "&LAYERS=$layer&STYLES=&FORMAT=image%2Fpng&TRANSPARENT=TRUE" +
            "&SRS=EPSG%3A3857&BBOX=${bbox3857(zoom, x, y)}" +
            "&WIDTH=$size&HEIGHT=$size"
    }
}
