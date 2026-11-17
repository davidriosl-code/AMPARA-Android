package com.ecoresilia.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "emergency_items")
data class EmergencyItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val prepared: Boolean = false,
    @ColumnInfo(defaultValue = "'General'") val category: String = "General",
    @ColumnInfo(defaultValue = "1") val quantity: Int = 1,
    @ColumnInfo(defaultValue = "''") val expiresOn: String = ""
)

@Entity(tableName = "incident_reports", indices = [Index(value = ["reportId"], unique = true)])
data class IncidentReport(
    @PrimaryKey val reportId: String,
    val authorId: String,
    val type: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val receivedFrom: String = "local",
    @ColumnInfo(defaultValue = "''") val photoUri: String = ""
)

/** Mensajes recibidos o demostraciones locales. Nunca afirma que un reporte ciudadano es oficial. */
@Entity(tableName = "received_notices")
data class ReceivedNotice(
    @PrimaryKey val noticeId: String,
    val title: String,
    val body: String,
    val receivedAtEpochMs: Long,
    val origin: String
)

/** Publicación idempotente: el UUID local también se utiliza como ID del documento Firestore. */
@Entity(tableName = "cloud_drafts")
data class CloudDraft(
    @PrimaryKey val draftId: String,
    val ownerUid: String,
    val category: String,
    val description: String,
    val zone: String,
    val createdAtEpochMs: Long,
    val syncedAtEpochMs: Long = 0L
)

@Dao
interface ReceivedNoticeDao {
    @Query("SELECT * FROM received_notices ORDER BY receivedAtEpochMs DESC LIMIT 200")
    fun observe(): Flow<List<ReceivedNotice>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notice: ReceivedNotice): Long
}

@Dao
interface CloudDraftDao {
    @Query("SELECT * FROM cloud_drafts WHERE ownerUid = :owner AND syncedAtEpochMs = 0 ORDER BY createdAtEpochMs DESC")
    fun observePending(owner: String): Flow<List<CloudDraft>>
    @Query("SELECT * FROM cloud_drafts WHERE ownerUid = :owner AND syncedAtEpochMs = 0 ORDER BY createdAtEpochMs ASC")
    suspend fun pending(owner: String): List<CloudDraft>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(draft: CloudDraft): Long
    @Query("UPDATE cloud_drafts SET syncedAtEpochMs = :at WHERE draftId = :id AND ownerUid = :owner AND syncedAtEpochMs = 0")
    suspend fun markSynced(id: String, owner: String, at: Long)
}

/** Vista unificada y persistente de reportes; nunca es un canal de alertas oficiales.
 * Las filas cloud se separan por UID; incidentes locales/cercanos no se publican automáticamente.
 */
@Entity(tableName = "community_feed", indices = [Index(value = ["ownerUid"]), Index(value = ["source", "syncStatus"])])
data class CommunityFeedEntry(
    @PrimaryKey val entryId: String,
    val ownerUid: String = "",
    val remoteId: String = "",
    val category: String,
    val description: String,
    val zone: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val occurredAtEpochMs: Long,
    val lastServerConfirmedAtEpochMs: Long = 0L,
    val source: String,
    val syncStatus: String,
    val photoUri: String = ""
)

/** Los ID estables distinguen los documentos de Firebase de los incidentes del dispositivo. */
fun IncidentReport.toFeedEntry() = CommunityFeedEntry(
    entryId = "incident:" + reportId,
    category = type, description = description, zone = "Reporte con coordenadas",
    latitude = latitude, longitude = longitude, occurredAtEpochMs = timestamp,
    source = if (receivedFrom == "nearby") "nearby" else "local",
    syncStatus = "LOCAL", photoUri = photoUri
)

@Dao
interface CommunityFeedDao {
    @Query("SELECT * FROM community_feed WHERE source IN ('local','nearby') OR ownerUid = :owner ORDER BY occurredAtEpochMs DESC, entryId DESC")
    fun observeForOwner(owner: String): Flow<List<CommunityFeedEntry>>

    @Query("SELECT * FROM community_feed WHERE entryId=:id LIMIT 1")
    suspend fun getById(id: String): CommunityFeedEntry?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entry: CommunityFeedEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CommunityFeedEntry)

    @Query("UPDATE community_feed SET syncStatus='SYNCED', lastServerConfirmedAtEpochMs=:confirmedAt WHERE entryId=:id AND ownerUid=:owner AND source='cloud'")
    suspend fun markCloudSynced(id: String, owner: String, confirmedAt: Long)

    @Query("SELECT entryId FROM community_feed WHERE ownerUid=:owner AND source='cloud' AND syncStatus='SYNCED'")
    suspend fun currentCloudKeys(owner: String): List<String>

    @Query("DELETE FROM community_feed WHERE entryId IN (:keys) AND ownerUid=:owner AND source='cloud' AND syncStatus='SYNCED'")
    suspend fun removeMissingFromServer(keys: List<String>, owner: String)

    @Query("DELETE FROM community_feed WHERE entryId=:key AND ownerUid=:owner AND source='cloud'")
    suspend fun deleteMine(key: String, owner: String)
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "home_assessments")
data class HomeAssessment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val score: Int,
    val answers: String,
    val recommendation: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface EmergencyDao {
    @Query("SELECT * FROM emergency_items ORDER BY category, id")
    fun observeItems(): Flow<List<EmergencyItem>>
    @Insert suspend fun insert(item: EmergencyItem)
    @Update suspend fun update(item: EmergencyItem)
    @Delete suspend fun delete(item: EmergencyItem)
    @Query("SELECT COUNT(*) FROM emergency_items") suspend fun count(): Int
    @Query("SELECT * FROM emergency_items WHERE expiresOn != ''") suspend fun getAllItemsForExpiry(): List<EmergencyItem>
}

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incident_reports ORDER BY timestamp DESC")
    fun observeReports(): Flow<List<IncidentReport>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(report: IncidentReport): Long
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observe(): Flow<List<ChatMessage>>
    @Query("SELECT * FROM chat_messages ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ChatMessage>
    @Insert suspend fun insert(message: ChatMessage)
    @Query("DELETE FROM chat_messages") suspend fun clear()
}

@Dao
interface AssessmentDao {
    @Query("SELECT * FROM home_assessments ORDER BY createdAt DESC, id DESC")
    fun observe(): Flow<List<HomeAssessment>>
    @Insert suspend fun insert(assessment: HomeAssessment)
}

@Database(
    entities = [EmergencyItem::class, IncidentReport::class, ChatMessage::class, HomeAssessment::class, ReceivedNotice::class, CloudDraft::class, CommunityFeedEntry::class],
    version = 5,
    exportSchema = false
)
abstract class EmergencyDatabase : RoomDatabase() {
    abstract fun dao(): EmergencyDao
    abstract fun incidents(): IncidentDao
    abstract fun chats(): ChatDao
    abstract fun assessments(): AssessmentDao
    abstract fun notices(): ReceivedNoticeDao
    abstract fun drafts(): CloudDraftDao
    abstract fun communityFeed(): CommunityFeedDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emergency_items ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
                db.execSQL("ALTER TABLE emergency_items ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE emergency_items ADD COLUMN expiresOn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE incident_reports ADD COLUMN photoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS chat_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS home_assessments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, score INTEGER NOT NULL, answers TEXT NOT NULL, recommendation TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS received_notices (noticeId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, receivedAtEpochMs INTEGER NOT NULL, origin TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cloud_drafts (draftId TEXT NOT NULL PRIMARY KEY, ownerUid TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, zone TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, syncedAtEpochMs INTEGER NOT NULL)")
            }
        }
        // Migración NO destructiva. Copia los incidentes y borradores existentes sin mover ni borrar sus tablas.
        // Las copias cloud anteriores se importan cuando Firestore emite su snapshot (incluida su caché).
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS community_feed (
                    entryId TEXT NOT NULL PRIMARY KEY, ownerUid TEXT NOT NULL, remoteId TEXT NOT NULL,
                    category TEXT NOT NULL, description TEXT NOT NULL, zone TEXT NOT NULL,
                    latitude REAL, longitude REAL, occurredAtEpochMs INTEGER NOT NULL,
                    lastServerConfirmedAtEpochMs INTEGER NOT NULL, source TEXT NOT NULL,
                    syncStatus TEXT NOT NULL, photoUri TEXT NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_community_feed_ownerUid ON community_feed(ownerUid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_community_feed_source_syncStatus ON community_feed(source, syncStatus)")
                db.execSQL("""INSERT OR IGNORE INTO community_feed
                    (entryId,ownerUid,remoteId,category,description,zone,latitude,longitude,
                     occurredAtEpochMs,lastServerConfirmedAtEpochMs,source,syncStatus,photoUri)
                    SELECT 'incident:' || reportId,'','',type,description,'Reporte con coordenadas',
                           latitude,longitude,timestamp,0,
                           CASE WHEN receivedFrom = 'nearby' THEN 'nearby' ELSE 'local' END,'LOCAL',photoUri
                    FROM incident_reports""")
                db.execSQL("""INSERT OR IGNORE INTO community_feed
                    (entryId,ownerUid,remoteId,category,description,zone,latitude,longitude,
                     occurredAtEpochMs,lastServerConfirmedAtEpochMs,source,syncStatus,photoUri)
                    SELECT 'cloud:' || ownerUid || ':' || draftId,ownerUid,draftId,category,description,zone,
                           NULL,NULL,createdAtEpochMs,syncedAtEpochMs,'cloud',
                           CASE WHEN syncedAtEpochMs > 0 THEN 'SYNCED' ELSE 'PENDING' END,''
                    FROM cloud_drafts""")
            }
        }
        @Volatile private var instance: EmergencyDatabase? = null
        fun get(context: Context): EmergencyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, EmergencyDatabase::class.java, "ecoresilia.db"
            ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
    }
}
