package com.ecoresilia.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ecoresilia.app.data.DeviceLocationProvider
import com.ecoresilia.app.data.IncidentReport
import com.ecoresilia.app.data.GeoPeruWmsLayer
import com.ecoresilia.app.data.GeoPeruWmsUrl
import com.ecoresilia.app.data.OfficialIncident
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** Teselas WMS de GEOPERÚ superpuestas a OpenStreetMap, nunca alertas en tiempo real. */
private class GeoPeruWmsTiles(private val layer: GeoPeruWmsLayer) : OnlineTileSourceBase(
    "GEOPERU_${layer.displayName.replace(" ", "_")}_${layer.layerName}",
    4, 19, 256, ".png", arrayOf(layer.endpoint)
) {
    override fun getTileURLString(pMapTileIndex: Long): String = GeoPeruWmsUrl.getMapUrl(
        layer.endpoint, layer.layerName,
        MapTileIndex.getZoom(pMapTileIndex),
        MapTileIndex.getX(pMapTileIndex),
        MapTileIndex.getY(pMapTileIndex)
    )
}

/** Filtro de alfa para las teselas WMS: el mapa base sigue visible. */
private fun wmsOpacityFilter(opacity: Float): ColorMatrixColorFilter {
    val alpha = opacity.coerceIn(0f, 1f)
    return ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, alpha, 0f
    )))
}

@Composable
internal fun EcoNativeMap(
    latitude: Double,
    longitude: Double,
    reports: List<IncidentReport>,
    officialRecords: List<OfficialIncident>,
    incidentFilter: String,
    wmsLayer: GeoPeruWmsLayer?,
    layerOpacity: Float,
    showOfficialRecords: Boolean,
    userLocation: DeviceLocationProvider.Result? = null,
    onReportSelected: (IncidentReport) -> Unit,
    onOfficialSelected: (OfficialIncident) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember(context) { NativeMapController(context) }
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.map.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.map.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        controller.map.onResume()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            controller.map.onPause()
            controller.map.onDetach()
        }
    }
    AndroidView(
        factory = { controller.map },
        modifier = modifier.fillMaxWidth(),
        update = {
            controller.update(
                latitude, longitude, reports, officialRecords, incidentFilter,
                wmsLayer, layerOpacity, showOfficialRecords, userLocation,
                onReportSelected, onOfficialSelected
            )
        }
    )
}

private class NativeMapController(context: Context) {
    private val context = context
    private val configuration = Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.filesDir, "ecoresilia_maps").apply { mkdirs() }
        osmdroidTileCache = File(context.cacheDir, "ecoresilia_tiles").apply { mkdirs() }
    }
    val map = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        controller.setZoom(11.0)
        minZoomLevel = 4.0
        maxZoomLevel = 19.0
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }
    private var lastCenter: Pair<Double, Double>? = null
    private var lastSignature: Int? = null
    private var layerKey: String? = null
    private var tileOverlay: TilesOverlay? = null
    private val ownMarkers = mutableListOf<Marker>()
    private var userMarker: Marker? = null
    private var lastUserPosition: Pair<Double, Double>? = null

    fun update(
        lat: Double, lon: Double,
        reports: List<IncidentReport>, official: List<OfficialIncident>,
        incidentFilter: String, wmsLayer: GeoPeruWmsLayer?, layerOpacity: Float, showOfficial: Boolean,
        userLocation: DeviceLocationProvider.Result?,
        onReportSelected: (IncidentReport) -> Unit,
        onOfficialSelected: (OfficialIncident) -> Unit
    ) {
        if (lastCenter != (lat to lon)) {
            val target = GeoPoint(lat, lon)
            if (lastCenter == null) map.controller.setCenter(target)
            else map.controller.animateTo(target)
            lastCenter = lat to lon
        }
        val nextKey = wmsLayer?.let { "${it.endpoint}|${it.layerName}" }
        if (layerKey != nextKey) {
            tileOverlay?.let { previous ->
                map.overlays.remove(previous)
                previous.onDetach(map)
            }
            tileOverlay = null
            if (wmsLayer != null) {
                val provider = MapTileProviderBasic(context)
                provider.setTileSource(GeoPeruWmsTiles(wmsLayer))
                tileOverlay = TilesOverlay(provider, context).also {
                    it.setColorFilter(wmsOpacityFilter(layerOpacity))
                    // Nunca mostrar una tesela gris/amarilla de carga sobre el mapa base.
                    it.setLoadingBackgroundColor(AndroidColor.TRANSPARENT)
                    it.setLoadingLineColor(AndroidColor.TRANSPARENT)
                    map.overlays.add(0, it)
                }
            }
            layerKey = nextKey
        }
        tileOverlay?.setColorFilter(wmsOpacityFilter(layerOpacity))
        val matching = reports.filter { incidentFilter == "Todos" || it.type == incidentFilter }.take(100)
        val signature = listOf(
            matching.map { listOf(it.reportId, it.type, it.description, it.latitude, it.longitude, it.timestamp) },
            if (showOfficial) official.take(25).map { listOf(it.id, it.kind, it.lat, it.lon, it.timestamp) } else emptyList<Any>(),
            incidentFilter, showOfficial
        ).hashCode()
        if (signature != lastSignature) {
            ownMarkers.forEach { map.overlays.remove(it) }
            ownMarkers.clear()
            matching.forEach { report ->
                if (RiskMapPresentation.validCoordinates(report.latitude, report.longitude)) {
                    val marker = Marker(map).apply {
                        position = GeoPoint(report.latitude, report.longitude)
                        icon = circleIcon(context, RiskMapPresentation.colorForReport(report.type), RiskMapPresentation.symbolForReport(report.type))
                        title = "${report.type} · Ciudadano sin verificar"
                        snippet = report.description.take(180)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> onReportSelected(report); true }
                    }
                    map.overlays.add(marker)
                    ownMarkers.add(marker)
                }
            }
            if (showOfficial) official.take(25).forEach { record ->
                val rlat = record.lat
                val rlon = record.lon
                if (rlat != null && rlon != null && RiskMapPresentation.validCoordinates(rlat, rlon)) {
                    val marker = Marker(map).apply {
                        position = GeoPoint(rlat, rlon)
                        icon = circleIcon(context, AndroidColor.rgb(36, 104, 185), "i")
                        title = "Registro INDECI (histórico/administrativo)"
                        snippet = "${record.kind} · ${record.dateLabel()}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> onOfficialSelected(record); true }
                    }
                    map.overlays.add(marker)
                    ownMarkers.add(marker)
                }
            }
            lastSignature = signature
        }
        val gpsPosition = userLocation?.let { it.latitude to it.longitude }
        if (gpsPosition != lastUserPosition) {
            userMarker?.let { map.overlays.remove(it) }
            userMarker = null
            if (gpsPosition != null && RiskMapPresentation.validCoordinates(gpsPosition.first, gpsPosition.second)) {
                val gps = userLocation
                userMarker = Marker(map).apply {
                    position = GeoPoint(gpsPosition.first, gpsPosition.second)
                    icon = circleIcon(context, AndroidColor.rgb(26, 119, 223), "●")
                    title = "Tu ubicación actual"
                    snippet = gps.accuracyMeters?.let { "Precisión aproximada ±${it.toInt()} metros" }
                        ?: "Ubicación obtenida con permiso"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }.also { map.overlays.add(it) }
                map.controller.setCenter(GeoPoint(gpsPosition.first, gpsPosition.second))
                map.controller.setZoom(16.0)
            }
            lastUserPosition = gpsPosition
        }
        map.invalidate()
    }

    private fun circleIcon(context: Context, color: Int, sign: String): BitmapDrawable {
        val bmp = Bitmap.createBitmap(68, 68, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(34f, 34f, 30f, paint)
        paint.color = color
        canvas.drawCircle(34f, 34f, 25f, paint)
        paint.color = AndroidColor.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText(sign, 34f, 45f, paint)
        return BitmapDrawable(context.resources, bmp)
    }
}
