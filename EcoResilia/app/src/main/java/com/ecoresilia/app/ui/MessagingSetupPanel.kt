package com.ecoresilia.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ecoresilia.app.BuildConfig
import com.ecoresilia.app.push.EcoPush
import com.google.firebase.messaging.FirebaseMessaging

/** Prueba de recepción, no automatiza ni autentica el envío de avisos. */
@Composable
internal fun MessagingSetupPanel() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var status by remember { mutableStateOf("Notificaciones aún no comprobadas.") }
    var fcmToken by remember { mutableStateOf<String?>(null) }

    fun requestToken() {
        EcoPush.ensureChannel(context)
        status = "Solicitando token al servicio de Firebase…"
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                fcmToken = task.result
                status = "Dispositivo registrado para pruebas. Copia el token para enviar un aviso de prueba desde Firebase Console."
            } else {
                status = "No se pudo obtener el token: ${task.exception?.localizedMessage ?: "comprueba Internet y Google Play Services"}"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestToken()
        else status = "Permiso de notificaciones denegado. Puedes habilitarlo en ajustes de Android."
    }

    FrostCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Notificaciones en el celular · Firebase Cloud Messaging", style = MaterialTheme.typography.titleMedium)
            Text(
                "Recibe avisos de prueba y actualizaciones informativas. AMPARA no emite alertas oficiales de emergencia.",
                style = MaterialTheme.typography.bodySmall
            )
            if (!BuildConfig.FIREBASE_CONFIGURED) {
                Text("Firebase no está configurado para este APK. Comprueba app/google-services.json y reinstala.")
            } else {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else requestToken()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Activar avisos y obtener token de prueba")
                }
                if (fcmToken != null) {
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(fcmToken!!))
                        status = "Token copiado. No lo publiques ni lo envíes en el chat."
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Copiar token FCM para prueba")
                    }
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
