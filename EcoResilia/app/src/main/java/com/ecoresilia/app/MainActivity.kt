package com.ecoresilia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecoresilia.app.ui.EcoApp
import com.ecoresilia.app.ui.EcoTheme
import com.ecoresilia.app.ui.EcoViewModel
import com.ecoresilia.app.push.AlertSubscriptions
import com.ecoresilia.app.push.EcoPush
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import android.view.View
import com.ecoresilia.app.ui.AmparaIntro

class MainActivity : ComponentActivity() {
    // Contador observable para los toques en notificaciones con la Activity ya abierta.
    private val openAlertsRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EcoPush.EXTRA_OPEN_ALERTS, false) == true) {
            openAlertsRequests.value += 1
        }
        // Si el usuario se inscribió antes, restaurar sus temas sin intervención manual.
        AlertSubscriptions.syncAsync(applicationContext)
        setContent {
            EcoTheme {
                val vm: EcoViewModel = viewModel()
                val openAlertsRequest by openAlertsRequests.collectAsStateWithLifecycle()
                // Preserve the previous Activity state on rotation. Never delay a notification tap.
                val launchedFromFcm = intent?.let { launch ->
                    launch.hasExtra("google.message_id") ||
                    launch.hasExtra("gcm.message_id") ||
                    launch.hasExtra("google.c.a.c_id") ||
                    launch.getBooleanExtra(EcoPush.EXTRA_OPEN_ALERTS, false)
                } == true
                var showIntro by rememberSaveable {
                    mutableStateOf(savedInstanceState == null && openAlertsRequest == 0 && !launchedFromFcm)
                }
                LaunchedEffect(openAlertsRequest) {
                    if (openAlertsRequest > 0) showIntro = false
                }
                DisposableEffect(showIntro) {
                    val oldFlags = window.decorView.systemUiVisibility
                    if (showIntro) {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    }
                    onDispose {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = oldFlags
                    }
                }
                if (showIntro) {
                    AmparaIntro { showIntro = false }
                } else {
                    EcoApp(vm, openAlertsRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EcoPush.EXTRA_OPEN_ALERTS, false)) {
            openAlertsRequests.value += 1
        }
    }
}
