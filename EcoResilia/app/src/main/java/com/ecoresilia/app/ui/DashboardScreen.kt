package com.ecoresilia.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecoresilia.app.R
import com.ecoresilia.app.data.EmergencyItem
import com.ecoresilia.app.data.IncidentReport
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

private val AmparaOrange = Color(0xFFFF8F12)
private val AmparaInk = Color(0xFF133F51)

@Composable
private fun SunnyBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(
        Brush.verticalGradient(listOf(Color(0xFFE6F3FF), Color(0xFFD3EBFF), Color(0xFFC1E0FB)))
    )) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0x55FFF0B0), Color(0x18FFE39A), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(680f, 110f),
                radius = 280f
            )
        ))
    }
}

/** Tarjeta de clima: video al fondo y contraste adaptado a la hora local del dispositivo. */
@Composable
private fun WeatherHero(
    climate: ClimateUiState, onRefresh: () -> Unit,
    onCity: (String, Double, Double) -> Unit
) {
    val weatherClip = when (climate.weather?.code) {
        0, 1 -> R.raw.ampara_weather_sun
        in 51..99 -> R.raw.ampara_weather_rain
        else -> R.raw.ampara_weather_partly
    }
    val status = when (climate.weather?.code) {
        0, 1 -> "Soleado"
        in 51..99 -> "Lluvia en tu zona"
        2, 3 -> "Sol y nubes"
        else -> "Clima en tu zona"
    }
    val hour = LocalTime.now().hour
    val isNight = hour < 6 || hour >= 18
    val foreground = if (isNight) Color.White else Color(0xFF183B55)
    val subdued = foreground.copy(alpha = if (isNight) .92f else .82f)
    val updated = climate.lastUpdated?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    } ?: "sin actualizar"

    Surface(
        modifier = Modifier.fillMaxWidth().height(230.dp),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.18f),
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.95f)),
        shadowElevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp))) {
            if (climate.weather?.code in listOf(0, 1)) {
                SunnyBackdrop(Modifier.fillMaxSize())
            } else {
                WeatherVideoBackdrop(videoRes = weatherClip, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                if (isNight) listOf(
                    Color(0xA8132F4A), Color(0x6C173654), Color(0x15163355)
                ) else listOf(
                    Color(0x9AF7FBFF), Color(0x5CEEF7FF), Color(0x10E8F6FF)
                )
            )))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (isNight) .00f else .06f),
                Color.Transparent,
                Color(0x0F1B4667)
            ))))
            Column(
                Modifier.fillMaxSize().padding(start = 20.dp, end = 15.dp, top = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("Hoy", color = foreground, fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("Clima en tu zona", color = subdued,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Surface(onClick = onRefresh, color = Color.White.copy(alpha = if (isNight) .18f else .42f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .65f))) {
                        Icon(Icons.Outlined.Refresh, "Actualizar clima",
                            modifier = Modifier.padding(9.dp).size(20.dp), tint = foreground)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(climate.weather?.let { "${it.temperature}°C" } ?: "--°C",
                            fontSize = 50.sp, lineHeight = 56.sp,
                            fontWeight = FontWeight.SemiBold, color = foreground)
                        Text(if (climate.loading) "Actualizando..." else status,
                            color = foreground, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = Color.White.copy(alpha = if (isNight) .16f else .48f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = .38f))) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.WaterDrop, null,
                                        tint = foreground, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(climate.weather?.let { "Humedad ${it.humidity}%" } ?: "Humedad --%",
                                        color = foreground, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = foreground,
                        modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(climate.place, modifier = Modifier.weight(1f),
                        color = foreground, maxLines = 1,
                        style = MaterialTheme.typography.labelSmall)
                    Text(updated, color = subdued,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Arequipa", -16.409, -71.537),
            Triple("Lima", -12.0464, -77.0428)).forEach { (city, lat, lon) ->
            val active = climate.place.contains(city, ignoreCase = true)
            Surface(
                onClick = { onCity("$city, Perú", lat, lon) },
                color = if (active) Color(0xFFFFEAD7) else Color.White.copy(alpha = .72f),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, Color.White)
            ) {
                Text(city, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Color(0xFFB96005) else Color(0xFF52758A))
            }
        }
    }
    if (climate.error != null) {
        Text(climate.error, color = Color(0xFF99522F),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PrimaryShortcut(title: String, icon: ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.height(103.dp),
        color = Color(0xFFFAFCFE),
        shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = .99f)),
        shadowElevation = 2.dp) {
        Column(Modifier.fillMaxSize().padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null,
                modifier = Modifier.size(32.dp), tint = AmparaOrange)
            Spacer(Modifier.height(9.dp))
            Text(title, fontSize = 13.sp, color = Color(0xFF182F39),
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun SecondaryShortcut(title: String, subtitle: String,
    icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier,
        color = Color(0xFFF9FBFD),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = AmparaOrange,
                modifier = Modifier.size(25.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = AmparaInk)
                Text(subtitle, color = Color(0xFF637F91),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun DashboardScreen(
    climate: ClimateUiState,
    bag: List<EmergencyItem>,
    reports: List<IncidentReport>,
    familySize: Int,
    onNavigate: (EcoScreen) -> Unit,
    onRefresh: () -> Unit,
    onCity: (String, Double, Double) -> Unit
) {
    val done = bag.count { it.prepared }
    val ratio = if (bag.isEmpty()) 0f else done.toFloat() / bag.size
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WeatherHero(climate, onRefresh, onCity)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryShortcut("Mapa", Icons.Outlined.Map,
                { onNavigate(EcoScreen.MAP) }, Modifier.weight(1f))
            PrimaryShortcut("Avisos", Icons.Outlined.WarningAmber,
                { onNavigate(EcoScreen.ALERTS) }, Modifier.weight(1f))
            PrimaryShortcut("Mochila", Icons.Outlined.Backpack,
                { onNavigate(EcoScreen.BAG) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(3.dp))
        SectionHeading("Tu preparación familiar")
        FrostCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lista familiar", color = AmparaInk,
                        style = MaterialTheme.typography.titleMedium)
                    Text("$done de ${bag.size} elementos · $familySize integrante(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF67869A))
                }
                Text("${if (bag.isEmpty()) 0 else 100 * done / bag.size}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AmparaOrange)
            }
            LinearProgressIndicator(progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = AmparaOrange, trackColor = Color(0xFFFFE3C7))
            TextButton(onClick = { onNavigate(EcoScreen.BAG) }) {
                Text("Revisar mi lista", color = Color(0xFFD46A06))
                Icon(Icons.Outlined.ArrowForward, null, Modifier.size(17.dp),
                    tint = Color(0xFFD46A06))
            }
        }
        if (climate.forecast.isNotEmpty()) {
            SectionHeading("Pronóstico de las próximas horas")
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                climate.forecast.take(7).forEach { hour ->
                    Surface(color = Color.White.copy(alpha = .80f),
                        shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(hour.time.takeLast(5),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF597888))
                            Icon(Icons.Outlined.Cloud, contentDescription = null,
                                modifier = Modifier.size(28.dp), tint = Color(0xFF77A5C9))
                            Text("${hour.temperature}°", fontWeight = FontWeight.SemiBold,
                                color = AmparaInk)
                            Text("${hour.chance}% lluvia",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF698795))
                        }
                    }
                }
            }
        }
        SectionHeading("Más herramientas")
        SecondaryShortcut("Comunidad", "Reportes y ayuda entre vecinos",
            Icons.Outlined.Groups, { onNavigate(EcoScreen.COMMUNITY) },
            Modifier.fillMaxWidth())
        SecondaryShortcut("Asistente", "Tu guía para estar preparado",
            Icons.Outlined.ChatBubbleOutline, { onNavigate(EcoScreen.ASSISTANT) },
            Modifier.fillMaxWidth())
        SecondaryShortcut("Modo sin internet", "Guías y acciones durante emergencias",
            Icons.Outlined.Shield, { onNavigate(EcoScreen.EMERGENCY) },
            Modifier.fillMaxWidth())
        SecondaryShortcut("Diagnóstico de vivienda", "Evaluación preventiva",
            Icons.Outlined.Home, { onNavigate(EcoScreen.RISK) },
            Modifier.fillMaxWidth())
        Text("${reports.size} reportes guardados",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF668394))
        InfoNote("AMPARA brinda apoyo preventivo. Ante emergencias, sigue siempre los avisos oficiales.")
        Spacer(Modifier.height(5.dp))
    }
}
