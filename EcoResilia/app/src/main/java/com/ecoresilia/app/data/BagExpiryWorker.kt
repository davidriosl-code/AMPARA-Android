package com.ecoresilia.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ecoresilia.app.MainActivity
import java.time.LocalDate

/** Avisos locales y privados. Nunca representan una emergencia ni usan FCM. */
class BagExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("eco_identity", 0)
        if (!prefs.getBoolean("bag_expiry_notifications", false)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) return Result.success()

        val items = try { EmergencyDatabase.get(applicationContext).dao().getAllItemsForExpiry() }
        catch (_: Exception) { return Result.retry() }
        val today = LocalDate.now()
        val expired = items.count { BagExpiryPolicy.status(it.expiresOn, today) == ExpiryFlag.EXPIRED }
        val upcoming = items.count {
            BagExpiryPolicy.status(it.expiresOn, today) in setOf(ExpiryFlag.SOON, ExpiryFlag.TODAY)
        }
        if (expired + upcoming == 0) return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "ecoresilia_caducidad"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(channel, "Vencimientos de Mi Mochila", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Recordatorios de inventario, no alertas de desastres" }
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(applicationContext, 502, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val detail = buildList {
            if (expired > 0) add("$expired vencido(s)")
            if (upcoming > 0) add("$upcoming por vencer en 7 días")
        }.joinToString(" · ")
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("EcoResilia · revisa vencimientos")
            .setContentText(detail)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(502, notification)
        return Result.success()
    }
}
