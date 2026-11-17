package com.ecoresilia.app.ui.cloud

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ecoresilia.app.data.CloudDraft
import com.ecoresilia.app.data.CloudDraftDao
import com.ecoresilia.app.data.EmergencyDatabase
import com.ecoresilia.app.data.CommunityFeedDao
import com.ecoresilia.app.data.CommunityFeedEntry
import com.ecoresilia.app.data.cloud.CommunityEntryKeys
import com.ecoresilia.app.data.cloud.OfflineDraftSync
import java.util.UUID
import com.ecoresilia.app.data.cloud.CloudCommunityReport
import com.ecoresilia.app.data.cloud.CloudReportsRepository
import com.ecoresilia.app.data.cloud.FirebaseSessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Una pantalla extra en Comunidad; el EcoViewModel original permanece intacto. */
class CloudReportsViewModel(
    private val session: FirebaseSessionManager,
    private val repository: CloudReportsRepository,
    private val drafts: CloudDraftDao,
    private val database: EmergencyDatabase,
    private val context: Context
) : ViewModel() {
    val currentUser = session.userChanges
        .map { it?.let { user -> CloudIdentity(user.uid, user.isAnonymous, user.email) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pending: StateFlow<List<CloudDraft>> = session.userChanges
        .flatMapLatest { user -> if (user != null) drafts.observePending(user.uid) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun requestSync() = OfflineDraftSync.enqueue(context)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val feed: StateFlow<List<CommunityFeedEntry>> = session.userChanges
        .map { it?.uid ?: "" }
        .distinctUntilChanged()
        .flatMapLatest { owner -> database.communityFeed().observeForOwner(owner) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var remoteJob: Job? = null

    /** Cloud listener solo para traer la cuenta propia a Room, nunca para sustituir la vista offline. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCloudIntoRoom() {
        if (remoteJob?.isActive == true) return
        remoteJob = viewModelScope.launch {
            session.userChanges
                .flatMapLatest { user ->
                    if (user == null) flowOf(null)
                    else repository.observeMine(user.uid).map { user.uid to it }
                }
                .collect { result ->
                    if (result == null) { _loading.value = false; return@collect }
                    val (owner, snapshot) = result
                    val confirmedAt = if (!snapshot.fromCache && !snapshot.pendingWrites) System.currentTimeMillis() else 0L
                    database.withTransaction {
                        val feedDao = database.communityFeed()
                        for (remote in snapshot.reports) {
                            if (remote.userId != owner || remote.id.isBlank()) continue
                            val key = CommunityEntryKeys.cloud(owner, remote.id)
                            val existing = feedDao.getById(key)
                            // Una escritura todavía pendiente en Firestore NO es publicación confirmada.
                            if (existing?.syncStatus == "PENDING" && confirmedAt == 0L) continue
                            val entry = CommunityFeedEntry(
                                entryId = key,
                                ownerUid = owner, remoteId = remote.id,
                                category = remote.category, description = remote.description, zone = remote.zone,
                                occurredAtEpochMs = remote.createdAtEpochMs,
                                lastServerConfirmedAtEpochMs = confirmedAt,
                                source = "cloud", syncStatus = "SYNCED",
                                latitude = existing?.latitude, longitude = existing?.longitude,
                                photoUri = existing?.photoUri ?: ""
                            )
                            // No degradar la marca de confirmación si Firestore emite solo caché.
                            if (confirmedAt == 0L) {
                                feedDao.upsert(entry.copy(lastServerConfirmedAtEpochMs = existing?.lastServerConfirmedAtEpochMs ?: 0L))
                            } else feedDao.upsert(entry)
                        }
                        // La caché incompleta no debe eliminar registros offline.
                        if (confirmedAt > 0L) {
                            val validKeys = snapshot.reports.map { CommunityEntryKeys.cloud(owner, it.id) }.toSet()
                            val obsolete = feedDao.currentCloudKeys(owner).filter { it !in validKeys }
                            if (obsolete.isNotEmpty()) feedDao.removeMissingFromServer(obsolete, owner)
                        }
                    }
                    _loading.value = false
                }
        }
        remoteJob?.invokeOnCompletion { cause ->
            _loading.value = false
            if (cause != null && cause !is CancellationException) {
                _notice.value = "No se pudo actualizar la nube. Tus copias locales continúan disponibles."
            }
        }
    }

    fun start() = viewModelScope.launch {
        observeCloudIntoRoom()
        if (session.currentUser != null) { requestSync(); return@launch }
        perform("No se pudo iniciar la sesión anónima") { session.ensureAnonymousSession(); requestSync() }
    }

    fun save(id: String?, category: String, description: String, zone: String,
             latitude: Double?, longitude: Double?, photoUri: String, onSaved: () -> Unit) {
        perform("No se pudo guardar el reporte") {
            if (id == null) {
                require(category.trim() in setOf("Huaico", "Inundación", "Derrumbe"))
                require(description.trim().length in 8..500)
                require(zone.trim().length in 2..100)
                val hasGps = latitude != null && longitude != null &&
                    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
                val idCreated = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val uid = session.currentUser?.uid
                if (uid == null) {
                    // La base Room siempre guarda aun si el primer inicio ocurre sin Internet.
                    // No atribuimos silenciosamente un reporte a otra cuenta Firebase.
                    database.communityFeed().insertIfMissing(CommunityFeedEntry(
                        entryId = "incident:" + idCreated, remoteId = idCreated,
                        category = category.trim(), description = description.trim(), zone = zone.trim(),
                        latitude = if (hasGps) latitude else null,
                        longitude = if (hasGps) longitude else null,
                        occurredAtEpochMs = now, source = "local", syncStatus = "LOCAL", photoUri = photoUri
                    ))
                    _notice.value = "Reporte guardado SOLO en este teléfono. Aún no hay identidad Firebase; no se sincronizará automáticamente. Conéctate y vincula una cuenta para futuras publicaciones."
                } else {
                    val draft = CloudDraft(
                        draftId = idCreated, ownerUid = uid,
                        category = category.trim(), description = description.trim(),
                        zone = zone.trim(), createdAtEpochMs = now
                    )
                    database.withTransaction {
                        drafts.insert(draft)
                        database.communityFeed().insertIfMissing(CommunityFeedEntry(
                            entryId = CommunityEntryKeys.cloud(uid, draft.draftId),
                            ownerUid = uid, remoteId = draft.draftId,
                            category = draft.category, description = draft.description,
                            zone = draft.zone, occurredAtEpochMs = draft.createdAtEpochMs,
                            latitude = if (hasGps) latitude else null,
                            longitude = if (hasGps) longitude else null,
                            photoUri = photoUri,
                            source = "cloud", syncStatus = "PENDING"
                        ))
                    }
                    requestSync()
                    _notice.value = "Reporte guardado localmente y en cola para Firebase. No se publicará como alerta oficial."
                }
            } else {
                repository.update(id, category, description, zone)
                _notice.value = "Reporte actualizado en Firestore. Su copia local se actualizará al sincronizar."
            }
            onSaved() // Limpiar formulario solo tras guardar localmente o confirmar edición.
        }
    }

    fun delete(id: String) = perform("No se pudo eliminar el reporte") {
        val owner = requireNotNull(session.currentUser?.uid)
        repository.delete(id)
        database.communityFeed().deleteMine(CommunityEntryKeys.cloud(owner, id), owner)
        _notice.value = "Reporte eliminado de Firestore y de la copia local."
    }

    fun register(email: String, password: String) = perform("No se pudo vincular o crear la cuenta") {
        require(email.contains('@') && email.trim().length >= 5) { "Introduce un correo válido" }
        require(password.length >= 6) { "La contraseña necesita al menos 6 caracteres" }
        session.linkOrRegister(email, password)
        _notice.value = "Cuenta vinculada. Tus reportes mantienen su propietario."
    }

    fun signIn(email: String, password: String) = perform("No se pudo iniciar sesión") {
        session.signIn(email, password)
        _notice.value = "Sesión iniciada. Se muestran solo tus reportes."
    }

    fun signOut() = perform("No se pudo cerrar sesión") {
        session.signOutRegisteredUser()
        session.ensureAnonymousSession()
        _notice.value = "Sesión cerrada. Se creó una cuenta temporal independiente."
    }

    private fun perform(errorTitle: String, action: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _notice.value = "$errorTitle: ${e.localizedMessage ?: "intenta otra vez"}" }
            finally { _busy.value = false }
        }
    }

    fun dismissNotice() { _notice.value = null }
    fun isOnline(): Boolean = repository.hasInternet()

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CloudReportsViewModel::class.java))
            val session = FirebaseSessionManager(FirebaseAuth.getInstance())
            val repository = CloudReportsRepository(FirebaseFirestore.getInstance(), session, context.applicationContext)
            val database = EmergencyDatabase.get(context)
            return CloudReportsViewModel(session, repository, database.drafts(), database, context.applicationContext) as T
        }
    }
}

data class CloudIdentity(val uid: String, val anonymous: Boolean, val email: String?)
