package com.ecoresilia.app.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class CloudReportSnapshot(val reports: List<CloudCommunityReport>, val fromCache: Boolean, val pendingWrites: Boolean)

/** Fuente remota de datos propios. La lista pública comunitaria necesita reglas y moderación aparte. */
class CloudReportsRepository(
    private val firestore: FirebaseFirestore,
    private val session: FirebaseSessionManager,
    private val context: Context
) {
    private val collection get() = firestore.collection("communityReports")
    private val categories = setOf("Huaico", "Inundación", "Derrumbe")

    fun observeMine(userId: String): Flow<CloudReportSnapshot> = callbackFlow {
        // La condición de la consulta es obligatoria para cumplir las reglas de lectura.
        val listener = collection.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                try {
                    val results = snapshot?.documents.orEmpty()
                        .mapNotNull { it.toObject(CloudCommunityReport::class.java)?.copy(id = it.id) }
                        .sortedByDescending { it.createdAtEpochMs }
                    trySend(CloudReportSnapshot(results, snapshot?.metadata?.isFromCache ?: true,
                        snapshot?.metadata?.hasPendingWrites() ?: true))
                } catch (e: Exception) { close(e) }
            }
        awaitClose { listener.remove() }
    }

    fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun create(category: String, description: String, zone: String) {
        check(hasInternet()) { "La nube necesita Internet. Tus reportes locales siguen disponibles." }
        validate(category, description, zone)
        val user = session.ensureAnonymousSession()
        val document = collection.document()
        document.set(
            CloudCommunityReport(
                id = document.id, userId = user.uid,
                category = category.trim(), description = description.trim(), zone = zone.trim(),
                createdAtEpochMs = System.currentTimeMillis()
            )
        ).await()  // Espera confirmación del servidor, no solo caché local.
    }

    suspend fun update(id: String, category: String, description: String, zone: String) {
        check(hasInternet()) { "Conéctate a Internet para editar reportes en la nube." }
        validate(category, description, zone)
        require(id.isNotBlank()) { "Reporte no válido" }
        collection.document(id).update(mapOf(
            "category" to category.trim(), "description" to description.trim(),
            "zone" to zone.trim()
        )).await()
    }

    suspend fun delete(id: String) {
        check(hasInternet()) { "Conéctate a Internet para eliminar reportes en la nube." }
        require(id.isNotBlank()) { "Reporte no válido" }
        collection.document(id).delete().await()
    }

    private fun validate(category: String, description: String, zone: String) {
        require(category.trim() in categories) { "Selecciona una categoría válida" }
        require(description.trim().length in 8..500) { "La descripción debe tener entre 8 y 500 caracteres" }
        require(zone.trim().length in 2..100) { "La zona referencial debe tener entre 2 y 100 caracteres" }
    }
}
