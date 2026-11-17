package com.ecoresilia.app.data

import com.ecoresilia.app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Backend seguro: la API key del proveedor vive solo en Render, no en el APK. */
class AssistantNetworkRepository {
    suspend fun ask(question: String, previous: List<ChatMessage>, place: String, family: HouseholdProfile): String = withContext(Dispatchers.IO) {
        val base = BuildConfig.ASSISTANT_PROXY_URL.trimEnd('/')
        require(base.startsWith("https://") || (BuildConfig.DEBUG && base == "http://10.0.2.2:8787")) {
            "El servidor de IA todavía no está configurado"
        }
        // La autenticación Firebase no expone ninguna clave privada en el APK.
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: error("No se pudo iniciar una sesión de Firebase")
        val token = user.getIdToken(false).await().token
            ?: error("Firebase no devolvió token de autenticación")
        val payload = JSONObject().apply {
            put("place", place.take(80))
            // Solo cantidades agregadas; nunca nombres, edades individuales ni datos clínicos.
            put("household", JSONObject().apply {
                put("adults", family.adults)
                put("children", family.children)
                put("babies", family.babies)
                put("olderAdults", family.olderAdults)
                put("pets", family.pets)
            })
            val conversation = JSONArray()
            previous.take(8).asReversed().forEach { item ->
                if (item.role == "user" || item.role == "assistant_ai") {
                    conversation.put(
                        JSONObject()
                            .put("role", if (item.role == "user") "user" else "assistant")
                            .put("content", item.text.take(1000))
                    )
                }
            }
            conversation.put(JSONObject().put("role", "user").put("content", question.take(800)))
            put("messages", conversation)
        }
        val connection = URL("$base/api/assistant").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            // Render free puede iniciar tras un periodo de inactividad.
            connection.connectTimeout = 20000
            connection.readTimeout = 80000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                401, 403 -> error("La sesión de Firebase no fue aceptada. Comprueba tu conexión e inicio de sesión")
                429 -> error("Alcanzaste el límite de consultas por hora. Inténtalo más tarde")
                503 -> error("El servidor de IA aún no está listo. Comprueba su configuración")
                else -> error("Servidor de IA no disponible (HTTP $code)")
            }
            val raw = connection.inputStream.bufferedReader().use { it.readText().take(10000) }
            val answer = JSONObject(raw).optString("answer", "").trim()
            if (answer.isBlank()) error("El modelo de IA respondió sin texto")
            answer.take(3500)
        } finally {
            connection.disconnect()
        }
    }
}
