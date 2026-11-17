package com.ecoresilia.app.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** FCM informativo: SOLO opt-in; una zona elegida, dos tipos con emisores separados.
 * No emplea GPS ni envía la localización. La inscripción es por topic; no guarda el token.
 */
object AlertSubscriptions {
    private const val PREFS = "ecoresilia_alertas_v2"
    const val BOLETINES = "quebradas"
    const val COMUNIDAD = "comunidad"
    val tipos = setOf(BOLETINES, COMUNIDAD)
    val regiones = linkedMapOf(
        "amazonas" to "Amazonas", "ancash" to "Áncash", "apurimac" to "Apurímac",
        "arequipa" to "Arequipa", "ayacucho" to "Ayacucho", "cajamarca" to "Cajamarca",
        "callao" to "Callao", "cusco" to "Cusco", "huancavelica" to "Huancavelica",
        "huanuco" to "Huánuco", "ica" to "Ica", "junin" to "Junín",
        "la_libertad" to "La Libertad", "lambayeque" to "Lambayeque", "lima" to "Lima",
        "loreto" to "Loreto", "madre_de_dios" to "Madre de Dios", "moquegua" to "Moquegua",
        "pasco" to "Pasco", "piura" to "Piura", "puno" to "Puno",
        "san_martin" to "San Martín", "tacna" to "Tacna", "tumbes" to "Tumbes",
        "ucayali" to "Ucayali"
    )

    data class Selection(val enabled: Boolean, val region: String, val types: Set<String>)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Mutex()
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saved(context: Context): Selection {
        val p = prefs(context)
        return Selection(p.getBoolean("enabled", false), p.getString("region", "arequipa") ?: "arequipa",
            p.getStringSet("types", setOf(BOLETINES))?.toSet() ?: setOf(BOLETINES))
    }

    fun topic(region: String, kind: String): String {
        require(region in regiones && kind in tipos)
        return "ecoresilia_v2_${region}_$kind"
    }

    private fun desired(s: Selection): Set<String> =
        if (s.enabled && s.region in regiones) s.types.intersect(tipos).map { topic(s.region, it) }.toSet()
        else emptySet()

    /** Cambio persistente; si falla la red, el próximo inicio vuelve a conciliar topics. */
    fun save(context: Context, enabled: Boolean, region: String, selected: Set<String>) {
        require(region in regiones)
        require(selected.all { it in tipos })
        require(!enabled || selected.isNotEmpty())
        prefs(context).edit().putBoolean("enabled", enabled).putString("region", region)
            .putStringSet("types", selected.toSet()).apply()
    }

    fun onTokenRotated(context: Context) {
        // Las suscripciones anteriores pueden pertenecer a otro identificador FCM.
        prefs(context).edit().remove("subscribed_topics").apply()
        syncAsync(context)
    }

    fun syncAsync(context: Context, callback: ((String) -> Unit)? = null) {
        val applicationContext = context.applicationContext
        scope.launch {
            val result = try { syncLock.withLock { synchronize(applicationContext) } }
                catch (_: Exception) { "Inscripción pendiente; se reintentará con conexión." }
            withContext(Dispatchers.Main) { callback?.invoke(result) }
        }
    }

    private suspend fun synchronize(context: Context): String {
        val p = prefs(context)
        val current = p.getStringSet("subscribed_topics", emptySet())?.toMutableSet() ?: mutableSetOf()
        val target = desired(saved(context))
        val fcm = FirebaseMessaging.getInstance()
        // Migración: dejar el topic regional antiguo para no recibir avisos de otros tipos.
        val legacy = context.getSharedPreferences("eco_alertas_regionales", Context.MODE_PRIVATE)
            .getString("region_activa", "") ?: ""
        if (legacy in regiones) {
            fcm.unsubscribeFromTopic("ecoresilia_region_$legacy").await()
            context.getSharedPreferences("eco_alertas_regionales", Context.MODE_PRIVATE)
                .edit().remove("region_activa").apply()
        }
        for (name in current.minus(target)) {
            fcm.unsubscribeFromTopic(name).await()
            current.remove(name)
            p.edit().putStringSet("subscribed_topics", current.toSet()).apply()
        }
        for (name in target.minus(current)) {
            fcm.subscribeToTopic(name).await()
            current.add(name)
            p.edit().putStringSet("subscribed_topics", current.toSet()).apply()
        }
        return if (target.isEmpty()) "Avisos desactivados en este dispositivo."
            else "Suscripción FCM confirmada: ${target.size} tipo(s) para ${regiones[saved(context).region]}."
    }
}
