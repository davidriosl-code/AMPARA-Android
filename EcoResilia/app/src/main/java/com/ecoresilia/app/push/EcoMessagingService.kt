package com.ecoresilia.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ecoresilia.app.MainActivity
import com.ecoresilia.app.data.EmergencyDatabase
import com.ecoresilia.app.data.ReceivedNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Solo recepción. Nunca llamar APIs de envío desde el cliente. */
class EcoMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // El token puede rotar: restaurar solo temas elegidos con consentimiento.
        // No registrar ni transmitir este token en mensajes visibles.
        AlertSubscriptions.onTokenRotated(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        EcoPush.ensureChannel(this)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "AMPARA · Aviso informativo"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Hay una actualización informativa en AMPARA."
        // Diferenciar procedencia: un reporte ciudadano revisado nunca es una alerta oficial.
        val origin = when (message.data["category"]) {
            "boletin_informativo" -> "Fuente institucional referenciada · verificar vigencia"
            "reporte_revisado" -> "Reporte ciudadano revisado · NO es alerta oficial"
            "simulacro" -> "SIMULACRO · NO es evento real"
            else -> "FCM · Información no verificada"
        }
        // Para garantizar la persistencia en background, los emisores deben usar mensajes
        // DATA-ONLY (title, body, notice_id, category). La prueba con Firebase Console
        // suele ser notification-only y Android la muestra directamente cuando la app
        // esta en segundo plano, sin llamar a onMessageReceived.
        val notice = ReceivedNotice(
            noticeId = message.data["notice_id"]?.take(120)?.takeIf { it.isNotBlank() }
                ?: message.messageId ?: UUID.randomUUID().toString(),
            title = title.take(90), body = body.take(1200),
            receivedAtEpochMs = System.currentTimeMillis(),
            origin = origin
        )
        // Persistir al llegar, antes de mostrar. Los mensajes no entregados no aparecen offline.
        try {
            runBlocking(Dispatchers.IO) {
                EmergencyDatabase.get(applicationContext).notices().insert(notice)
            }
        } catch (_: Exception) { /* La notificación puede mostrarse aunque el almacenamiento falle. */ }
        // Muestra un aviso dentro de AMPARA cuando la UI esta abierta.
        IncomingNoticeEvents.publish(notice)
        EcoPush.show(this, title, body)
    }
}

/** Eventos efímeros para mostrar un banner en la interfaz. El historial real es Room. */
object IncomingNoticeEvents {
    private val mutable = MutableSharedFlow<ReceivedNotice>(extraBufferCapacity = 8)
    val events = mutable.asSharedFlow()
    fun publish(notice: ReceivedNotice) { mutable.tryEmit(notice) }
}

object EcoPush {
    const val CHANNEL_ID = "ecoresilia_informativos"
    const val EXTRA_OPEN_ALERTS = "com.ecoresilia.app.OPEN_ALERTS"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Avisos AMPARA", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Pruebas y avisos informativos; no son alertas oficiales" }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ALERTS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.take(90))
            .setContentText(body.take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(1200)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
