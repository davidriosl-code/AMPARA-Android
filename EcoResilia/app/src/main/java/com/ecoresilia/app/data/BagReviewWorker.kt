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
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ecoresilia.app.MainActivity

/** Recordatorio voluntario del kit familiar. Nunca debe mostrarse como alerta meteorológica. */
class BagReviewWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!applicationContext.getSharedPreferences("eco_identity", 0).getBoolean("bag_reminder", false)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) return Result.success()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ecoresilia_preparacion"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(channelId, "Recordatorios de preparación", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Revisión de mochila; no son alertas de desastres" }
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            applicationContext, 501, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("EcoResilia · revisa tu mochila")
            .setContentText("Verifica agua, medicinas, baterías y fechas de vencimiento.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(501, notification)
        return Result.success()
    }
}
