package com.ecoresilia.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.ecoresilia.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ClimateUiState(
    val place: String = "Arequipa, Perú",
    val latitude: Double = -16.409,
    val longitude: Double = -71.537,
    val weather: CurrentWeather? = null,
    val forecast: List<HourForecast> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long? = null
)
data class GeoUiState(val loading: Boolean = false, val results: List<GeoPlace> = emptyList(), val error: String? = null)
data class OfficialUiState(val loading: Boolean = false, val items: List<OfficialIncident> = emptyList(), val error: String? = null, val lastUpdated: Long? = null)

class EcoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = WeatherRepository()
    private val geo = GeocodingRepository()
    private val officialRepository = OfficialDataRepository()
    private val assistantNetwork = AssistantNetworkRepository()
    private val database = EmergencyDatabase.get(app)
    private val dao = database.dao()
    private val incidentDao = database.incidents()
    private val chatDao = database.chats()
    private val assessmentDao = database.assessments()
    private val prefs = app.getSharedPreferences("eco_identity", 0)
    private val authorId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }

    val reports = incidentDao.observeReports().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val items = dao.observeItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val messages = chatDao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val assessments = assessmentDao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Migra el tamaño familiar existente a adultos sin perder configuración ni inventario.
    private val initialPeople = prefs.getInt("family_size", 3).coerceIn(1, 20)
    private val savedHousehold = HouseholdProfile(
        adults = prefs.getInt("household_adults", initialPeople),
        children = prefs.getInt("household_children", 0),
        babies = prefs.getInt("household_babies", 0),
        olderAdults = prefs.getInt("household_older_adults", 0),
        pets = prefs.getInt("household_pets", 0)
    ).let { if (it.valid()) it else HouseholdProfile(adults = initialPeople) }
    private val _householdProfile = MutableStateFlow(savedHousehold)
    val householdProfile = _householdProfile.asStateFlow()
    private val _familySize = MutableStateFlow(savedHousehold.people)
    val familySize = _familySize.asStateFlow()
    private val _assistantOnline = MutableStateFlow(prefs.getBoolean("assistant_online", false))
    val assistantOnline = _assistantOnline.asStateFlow()
    private val _assistantBusy = MutableStateFlow(false)
    val assistantBusy = _assistantBusy.asStateFlow()
    fun setAssistantOnline(enabled: Boolean) {
        _assistantOnline.value = enabled
        prefs.edit().putBoolean("assistant_online", enabled).apply()
    }
    fun setFamilySize(size: Int) {
        val safe = size.coerceIn(1, 20)
        val current = _householdProfile.value
        val other = current.children + current.babies + current.olderAdults
        if (safe >= other) setHouseholdProfile(current.copy(adults = safe - other))
    }
    fun setHouseholdProfile(next: HouseholdProfile) {
        if (!next.valid()) return
        _householdProfile.value = next
        _familySize.value = next.people
        prefs.edit()
            .putInt("family_size", next.people)
            .putInt("household_adults", next.adults)
            .putInt("household_children", next.children)
            .putInt("household_babies", next.babies)
            .putInt("household_older_adults", next.olderAdults)
            .putInt("household_pets", next.pets)
            .apply()
    }

    private val _bagReminderEnabled = MutableStateFlow(prefs.getBoolean("bag_reminder", false))
    val bagReminderEnabled = _bagReminderEnabled.asStateFlow()
    fun setBagReminderEnabled(enabled: Boolean) {
        _bagReminderEnabled.value = enabled
        prefs.edit().putBoolean("bag_reminder", enabled).apply()
        val work = WorkManager.getInstance(getApplication())
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<BagReviewWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS).build()
            work.enqueueUniquePeriodicWork("ecoresilia_bag_review", ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork("ecoresilia_bag_review")
        }
    }

    private val _expiryRemindersEnabled = MutableStateFlow(prefs.getBoolean("bag_expiry_notifications", false))
    val expiryRemindersEnabled = _expiryRemindersEnabled.asStateFlow()
    fun setExpiryRemindersEnabled(enabled: Boolean) {
        _expiryRemindersEnabled.value = enabled
        prefs.edit().putBoolean("bag_expiry_notifications", enabled).apply()
        val work = WorkManager.getInstance(getApplication())
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<BagExpiryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.MINUTES).build()
            work.enqueueUniquePeriodicWork("ecoresilia_bag_expiry", ExistingPeriodicWorkPolicy.REPLACE, request)
        } else {
            work.cancelUniqueWork("ecoresilia_bag_expiry")
        }
    }

    private val _nearbyDemo = MutableStateFlow(NearbyDemoState())
    val nearbyDemo = _nearbyDemo.asStateFlow()
    private val nearby = NearbyReportMesh(app, { report, acknowledge ->
        viewModelScope.launch {
            try {
                database.withTransaction {
                    database.communityFeed().insertIfMissing(report)
                    if (report.latitude != null && report.longitude != null) {
                        incidentDao.insert(IncidentReport(
                            reportId = report.remoteId, authorId = "nearby-peer", type = report.category,
                            description = report.description, latitude = report.latitude, longitude = report.longitude,
                            timestamp = report.occurredAtEpochMs, receivedFrom = "nearby"
                        ))
                    }
                }
                // Confirmar recepción solo después de persistir en Room.
                acknowledge()
            } catch (_: Exception) {
                _nearbyDemo.value = _nearbyDemo.value.copy(status = "No se pudo guardar el reporte recibido")
            }
        }
    }, { _nearbyDemo.value = it })
    fun hostNearby() = nearby.startHosting()
    fun searchNearby() = nearby.startSearching()
    fun connectNearby(endpointId: String) = nearby.connectTo(endpointId)
    fun shareNearbyReport(report: CommunityFeedEntry, sharePreciseLocation: Boolean) = nearby.shareReport(report, sharePreciseLocation)
    fun createNearbyDemo() = submitReport(
        "Derrumbe",
        "SIMULACRO ACADÉMICO: Reporte ficticio para demostrar el envío entre dos celulares sin internet. NO representa una emergencia real.",
        climate.value.latitude, climate.value.longitude
    )
    fun stopNearby() = nearby.stop()
    fun approveNearbyConnection() = nearby.approvePendingConnection()
    fun rejectNearbyConnection() = nearby.rejectPendingConnection()

    fun submitReport(type: String, description: String, latitude: Double?, longitude: Double?, photoUri: String = "") {
        if (description.trim().length < 8 || type !in setOf("Huaico", "Inundación", "Derrumbe")) return
        val locationValid = latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
        val id = UUID.randomUUID().toString()
        val time = System.currentTimeMillis()
        viewModelScope.launch {
            database.withTransaction {
                if (locationValid) {
                    incidentDao.insert(IncidentReport(id, authorId, type, description.take(500),
                        latitude!!, longitude!!, time, "local", photoUri))
                }
                database.communityFeed().insertIfMissing(CommunityFeedEntry(
                    entryId = "incident:" + id, remoteId = id, category = type,
                    description = description.take(500), zone = "Reporte local",
                    latitude = if (locationValid) latitude else null,
                    longitude = if (locationValid) longitude else null,
                    occurredAtEpochMs = time, source = "local", syncStatus = "LOCAL", photoUri = photoUri
                ))
            }
            // No enviar automáticamente; se comparte por Nearby solo con acción explícita.
        }
    }

    private val _climate = MutableStateFlow(ClimateUiState())
    val climate = _climate.asStateFlow()
    private val _geoSearch = MutableStateFlow(GeoUiState())
    val geoSearch = _geoSearch.asStateFlow()
    private val _official = MutableStateFlow(OfficialUiState())
    val official = _official.asStateFlow()

    init {
        viewModelScope.launch {
            if (dao.count() == 0) {
                listOf(
                    EmergencyItem(name = "Agua potable", category = "Agua", quantity = 3),
                    EmergencyItem(name = "Alimentos no perecibles", category = "Alimentos", quantity = 3),
                    EmergencyItem(name = "Linterna y pilas", category = "Herramientas"),
                    EmergencyItem(name = "Botiquín", category = "Salud"),
                    EmergencyItem(name = "Radio a pilas", category = "Herramientas"),
                    EmergencyItem(name = "Documentos importantes", category = "Documentos"),
                    EmergencyItem(name = "Mantas", category = "Abrigo"),
                    EmergencyItem(name = "Silbato", category = "Herramientas"),
                    EmergencyItem(name = "Medicamentos personales", category = "Salud"),
                    EmergencyItem(name = "Cargador portátil", category = "Herramientas")
                ).forEach { dao.insert(it) }
            }
        }
        refresh()
        // Solo refresca mientras el proceso siga en ejecución; NO es una alerta en segundo plano.
        viewModelScope.launch {
            while (true) {
                delay(30 * 60 * 1000L)
                refresh()
            }
        }
    }

    fun chooseCity(name: String, lat: Double, lon: Double) {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        _climate.value = ClimateUiState(place = name, latitude = lat, longitude = lon)
        refresh()
    }
    fun refresh() = viewModelScope.launch {
        val old = _climate.value
        _climate.value = old.copy(loading = true, error = null)
        try {
            val weather = repository.fetch(old.latitude, old.longitude)
            // Evita que una respuesta retrasada sobrescriba el clima de otra ciudad.
            if (_climate.value.latitude == old.latitude && _climate.value.longitude == old.longitude) {
                _climate.value = _climate.value.copy(
                    weather = weather.current, forecast = weather.nextHours,
                    loading = false, lastUpdated = System.currentTimeMillis(), error = null
                )
            }
        } catch (e: Exception) {
            if (_climate.value.latitude == old.latitude && _climate.value.longitude == old.longitude) {
                _climate.value = _climate.value.copy(loading = false, error = "No se pudieron actualizar los datos. Verifica tu conexión.")
            }
        }
    }

    fun searchCity(query: String) = viewModelScope.launch {
        if (query.trim().length < 3) {
            _geoSearch.value = GeoUiState(error = "Escribe al menos 3 caracteres")
            return@launch
        }
        _geoSearch.value = GeoUiState(loading = true)
        try {
            val results = geo.search(query.trim())
            _geoSearch.value = GeoUiState(results = results, error = if (results.isEmpty()) "Sin resultados en Perú" else null)
        } catch (_: Exception) {
            _geoSearch.value = GeoUiState(error = "No se pudo buscar. Revisa internet.")
        }
    }
    fun clearCitySearch() { _geoSearch.value = GeoUiState() }

    fun loadOfficial() = viewModelScope.launch {
        _official.value = _official.value.copy(loading = true, error = null)
        try {
            _official.value = OfficialUiState(items = officialRepository.recentRegistered(), lastUpdated = System.currentTimeMillis())
        } catch (_: Exception) {
            _official.value = _official.value.copy(loading = false, error = "Servicio SINPAD no disponible en este momento. Consulta la fuente oficial.")
        }
    }

    fun addItem(name: String, category: String, quantity: Int, expiresOn: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        dao.insert(EmergencyItem(name = name.trim().take(80), category = category, quantity = quantity.coerceIn(1, 999), expiresOn = expiresOn.trim()))
    }
    fun editItem(item: EmergencyItem) = viewModelScope.launch { dao.update(item) }
    fun deleteItem(item: EmergencyItem) = viewModelScope.launch { dao.delete(item) }
    fun toggle(item: EmergencyItem) = viewModelScope.launch { dao.update(item.copy(prepared = !item.prepared)) }
    fun saveAssessment(score: Int, answers: String, recommendation: String) = viewModelScope.launch {
        assessmentDao.insert(HomeAssessment(score = score, answers = answers, recommendation = recommendation))
    }

    fun ask(question: String) {
        val text = question.trim().take(800)
        if (text.isBlank() || _assistantBusy.value) return
        _assistantBusy.value = true
        viewModelScope.launch {
            try {
                val history = chatDao.recent(8)
                chatDao.insert(ChatMessage(role = "user", text = text))
                val urgent = listOf("atrapado", "atrapada", "socorro", "rescate", "sepultado", "sepultada").any { text.contains(it, ignoreCase = true) }
                val useAi = _assistantOnline.value && !urgent
                var aiSucceeded = false
                val reply = if (useAi) {
                    try {
                        assistantNetwork.ask(text, history, climate.value.place, householdProfile.value).also { aiSucceeded = true }
                    } catch (error: Exception) {
                        val reason = error.message.orEmpty().take(180)
                        "No se pudo obtener una respuesta de IA. ${if (reason.isBlank()) "Revisa la conexión o la configuración del servidor." else reason}\n\nPuedes desactivar IA en línea para consultar la guía local sin internet."
                    }
                } else {
                    SafetyAssistant.respond(text, history, climate.value.place, familySize.value)
                }
                chatDao.insert(ChatMessage(role = if (aiSucceeded) "assistant_ai" else "assistant", text = reply))
            } finally {
                _assistantBusy.value = false
            }
        }
    }
    fun clearConversation() = viewModelScope.launch { chatDao.clear() }

    override fun onCleared() {
        nearby.stop()
        super.onCleared()
    }
}
