package com.ecoresilia.app.ui

import androidx.activity.compose.BackHandler
import com.ecoresilia.app.data.DeviceLocationProvider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecoresilia.app.R
import com.ecoresilia.app.push.IncomingNoticeEvents
import kotlinx.coroutines.flow.collect

// ECORESILIA_LIQUID_GLASS_V1

internal enum class EcoScreen(val label: String) {
    HOME("Inicio"), MAP("Mapa"), MAP_FULL("Mapa completo"), BAG("Mochila"), ASSISTANT("Asistente"),
    RISK("Vivienda"), COMMUNITY("Comunidad"), ALERTS("Avisos"), EMERGENCY("Emergencia")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoApp(vm: EcoViewModel, openAlertsRequest: Int = 0) {
    var screen by remember { mutableStateOf(if (openAlertsRequest > 0) EcoScreen.ALERTS else EcoScreen.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    var mapReportLocation by remember { mutableStateOf<DeviceLocationProvider.Result?>(null) }
    BackHandler(enabled = screen == EcoScreen.MAP_FULL) { screen = EcoScreen.MAP }
    // Small logo entrance, no artificial launch delay (important in emergency scenarios).
    val entrance = remember { Animatable(0.90f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
    LaunchedEffect(openAlertsRequest) {
        if (openAlertsRequest > 0) screen = EcoScreen.ALERTS
    }
    LaunchedEffect(Unit) {
        IncomingNoticeEvents.events.collect { notice ->
            val result = snackbarHostState.showSnackbar(
                message = "AMPARA · " + notice.title.take(80),
                actionLabel = "Ver aviso",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) screen = EcoScreen.ALERTS
        }
    }
    val climate by vm.climate.collectAsStateWithLifecycle()
    val bag by vm.items.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    val nearby by vm.nearbyDemo.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val assistantOnline by vm.assistantOnline.collectAsStateWithLifecycle()
    val assistantBusy by vm.assistantBusy.collectAsStateWithLifecycle()
    val assessments by vm.assessments.collectAsStateWithLifecycle()
    val familySize by vm.familySize.collectAsStateWithLifecycle()
    val bagReminder by vm.bagReminderEnabled.collectAsStateWithLifecycle()
    val householdProfile by vm.householdProfile.collectAsStateWithLifecycle()
    val expiryRemindersEnabled by vm.expiryRemindersEnabled.collectAsStateWithLifecycle()
    val geo by vm.geoSearch.collectAsStateWithLifecycle()
    val official by vm.official.collectAsStateWithLifecycle()
    // Passive touch lens: it never consumes gestures, buttons and scrolling keep working.
    var fingerPosition by remember { mutableStateOf(Offset.Unspecified) }
    var fingerDown by remember { mutableStateOf(false) }
    val lensOpacity by animateFloatAsState(
        targetValue = if (fingerDown) 1f else 0f,
        animationSpec = tween(durationMillis = 160), label = "touch-glass-lens")

    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fingerPosition = down.position
            fingerDown = true
            do {
                val event = awaitPointerEvent()
                val active = event.changes.firstOrNull { it.pressed }
                if (active != null) fingerPosition = active.position
            } while (active != null)
            fingerDown = false
        }
    }) {
        // El shader afecta el contenido que queda debajo de la lente, pero el
        // detector táctil sigue en el contenedor exterior y no consume eventos.
        Box(Modifier.fillMaxSize().amparaTouchRefraction(
            touch = fingerPosition,
            opacity = lensOpacity,
            enabled = screen != EcoScreen.MAP_FULL
        )) {
        EcoLiquidBackdrop()
        if (screen == EcoScreen.MAP_FULL) {
            FullMapScreen(
                climate = climate, reports = reports, geo = geo, official = official,
                onCity = vm::chooseCity, onSearch = vm::searchCity,
                onClearSearch = vm::clearCitySearch, onLoadOfficial = vm::loadOfficial,
                onBack = { screen = EcoScreen.MAP },
                onReport = { selected ->
                    mapReportLocation = selected
                    screen = EcoScreen.COMMUNITY
                }
            )
        } else {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(painter = painterResource(R.drawable.ampara_mark),
                                contentDescription = "Nuevo símbolo de AMPARA",
                                modifier = Modifier.size(41.dp).scale(entrance.value))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("AMPARA", fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF123D4B))
                                Text(if (screen == EcoScreen.HOME) "Prepárate hoy, cuida a los tuyos" else screen.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF557C98))
                            }
                        }
                    },
                    navigationIcon = {
                        if (screen !in listOf(EcoScreen.HOME, EcoScreen.MAP, EcoScreen.BAG,
                                EcoScreen.ASSISTANT, EcoScreen.COMMUNITY)) {
                            IconButton(onClick = { screen = EcoScreen.HOME }) {
                                Icon(Icons.Default.ArrowBack, "Volver a inicio", tint = Color(0xFF286273))
                            }
                        }
                    },
                    actions = {
                        LiquidShapeIconButton(
                            icon = Icons.Outlined.FavoriteBorder,
                            contentDescription = "Abrir avisos",
                            onClick = { screen = EcoScreen.ALERTS },
                            modifier = Modifier.padding(end = 10.dp),
                            shape = HeartLiquidShape,
                            size = 58.dp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent)
                )
            },
            bottomBar = { EcoGlassDock(selected = screen, onSelect = { screen = it }) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    EcoScreen.HOME -> DashboardScreen(
                        climate = climate, bag = bag, reports = reports, familySize = familySize,
                        onNavigate = { screen = it }, onRefresh = vm::refresh,
                        onCity = vm::chooseCity
                    )
                    EcoScreen.BAG -> BagScreen(
                        bag = bag, household = householdProfile,
                        reminderEnabled = bagReminder, onReminderEnabled = vm::setBagReminderEnabled,
                        expiryRemindersEnabled = expiryRemindersEnabled,
                        onExpiryRemindersEnabled = vm::setExpiryRemindersEnabled,
                        onHouseholdChange = vm::setHouseholdProfile,
                        onAdd = vm::addItem, onUpdate = vm::editItem,
                        onDelete = vm::deleteItem, onToggle = vm::toggle
                    )
                    EcoScreen.MAP -> MapScreen(
                        onOpenMap = { screen = EcoScreen.MAP_FULL },
                        onReport = { mapReportLocation = null; screen = EcoScreen.COMMUNITY }
                    )
                    EcoScreen.MAP_FULL -> Unit
                    EcoScreen.ASSISTANT -> AssistantScreen(
                        messages = messages, online = assistantOnline, busy = assistantBusy,
                        onOnline = vm::setAssistantOnline,
                        onAsk = vm::ask, onClear = vm::clearConversation
                    )
                    EcoScreen.RISK -> RiskScreen(assessments = assessments, onSave = vm::saveAssessment)
                    EcoScreen.COMMUNITY -> CommunityScreen(
                        reports = reports, nearbyStatus = nearby,
                        initialLatitude = climate.latitude, initialLongitude = climate.longitude,
                        mapSelectedLocation = mapReportLocation,
                        onLocationConsumed = { mapReportLocation = null },
                        onHost = vm::hostNearby, onSearch = vm::searchNearby, onStop = vm::stopNearby,
                        onConnect = vm::connectNearby, onShareNearby = vm::shareNearbyReport,
                        onCreateDemo = vm::createNearbyDemo,
                        onApproveNearby = vm::approveNearbyConnection, onRejectNearby = vm::rejectNearbyConnection,
                        onSubmit = vm::submitReport
                    )
                    EcoScreen.ALERTS -> AlertsScreen(official, vm::loadOfficial)
                    EcoScreen.EMERGENCY -> OfflineHubScreen(
                        bag = bag, localReports = reports.size,
                        onNavigate = { screen = it }
                    )
                }
            }
        }
        } // La navegación y barra inferior no se dibujan encima del mapa completo.
        } // Contenido refractado; el feedback visual se dibuja sobre esta capa.
        // Un aro de luz muy discreto complementa la refracción, sin interceptar toques.
        Canvas(Modifier.fillMaxSize()) {
            if (lensOpacity > 0.015f && !fingerPosition.x.isNaN() && !fingerPosition.y.isNaN()) {
                val radius = 48.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.075f * lensOpacity),
                            Color(0xFFFFE6C8).copy(alpha = 0.09f * lensOpacity),
                            Color.Transparent
                        ), center = fingerPosition, radius = radius
                    ), radius = radius, center = fingerPosition
                )
                drawCircle(color = Color.White.copy(alpha = 0.30f * lensOpacity),
                    radius = radius, center = fingerPosition,
                    style = Stroke(width = 1.3.dp.toPx()))
            }
        }
    }
}
