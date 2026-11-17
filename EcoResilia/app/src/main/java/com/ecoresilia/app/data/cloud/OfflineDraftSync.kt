package com.ecoresilia.app.data.cloud

import android.content.Context
import androidx.work.*
import com.ecoresilia.app.data.EmergencyDatabase
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await

/** Envia solamente borradores del UID actual; se conserva el ID local para impedir duplicados. */
class OfflineDraftSync(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.retry()
        val database = EmergencyDatabase.get(applicationContext)
        val dao = database.drafts()
        val queue = dao.pending(user.uid)
        if (queue.isEmpty()) return Result.success()
        val collection = FirebaseFirestore.getInstance().collection("communityReports")
        return try {
            for (draft in queue) {
                // La conectividad puede fallar durante una escritura. El mismo ID evita duplicados.
                withTimeout(20_000L) {
                    collection.document(draft.draftId).set(
                        CloudCommunityReport(
                            id = draft.draftId, userId = user.uid,
                            category = draft.category, description = draft.description,
                            zone = draft.zone, createdAtEpochMs = draft.createdAtEpochMs
                        )
                    ).await()
                }
                val at = System.currentTimeMillis()
                database.withTransaction {
                    dao.markSynced(draft.draftId, user.uid, at)
                    database.communityFeed().markCloudSynced(
                        CommunityEntryKeys.cloud(user.uid, draft.draftId), user.uid, at
                    )
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<OfflineDraftSync>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, java.time.Duration.ofSeconds(30))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "ecoresilia_cloud_drafts", ExistingWorkPolicy.KEEP, request
            )
        }
    }
}
