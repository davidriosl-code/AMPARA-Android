package com.ecoresilia.app.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Animación local sin sonido. Se pausa al salir y nunca consulta la red. */
@Composable
internal fun WeatherVideoBackdrop(@RawRes videoRes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    key(videoRes) {
        val view = remember { TextureView(context).apply { isOpaque = false; alpha = .96f } }
        var player: MediaPlayer? by remember { mutableStateOf(null) }
        DisposableEffect(view, owner) {
            fun release() {
                try { player?.stop() } catch (_: Exception) { }
                try { player?.release() } catch (_: Exception) { }
                player = null
            }
            fun start(texture: SurfaceTexture) {
                release()
                val media = MediaPlayer()
                player = media
                try {
                    val surface = Surface(texture)
                    media.setSurface(surface)
                    surface.release()
                    media.setDataSource(context, Uri.parse("android.resource://${context.packageName}/$videoRes"))
                    media.isLooping = true
                    media.setVolume(0f, 0f)
                    media.setOnPreparedListener {
                        view.post {
                            val screenW = view.width.toFloat().coerceAtLeast(1f)
                            val screenH = view.height.toFloat().coerceAtLeast(1f)
                            val videoRatio = it.videoWidth.toFloat() / it.videoHeight.coerceAtLeast(1)
                            val screenRatio = screenW / screenH
                            val matrix = Matrix()
                            if (videoRatio > screenRatio) {
                                matrix.setScale(videoRatio / screenRatio, 1f, screenW / 2f, screenH / 2f)
                            } else {
                                matrix.setScale(1f, screenRatio / videoRatio, screenW / 2f, screenH / 2f)
                            }
                            view.setTransform(matrix)
                            it.start()
                        }
                    }
                    media.setOnErrorListener { _, _, _ -> release(); true }
                    media.prepareAsync()
                } catch (_: Exception) { release() }
            }
            val listener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = start(surface)
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { release(); return true }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
            view.surfaceTextureListener = listener
            if (view.isAvailable) view.surfaceTexture?.let(::start)
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> try { player?.pause() } catch (_: Exception) { }
                    Lifecycle.Event.ON_RESUME -> try { player?.start() } catch (_: Exception) { }
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose {
                owner.lifecycle.removeObserver(observer)
                view.surfaceTextureListener = null
                release()
            }
        }
        AndroidView(factory = { view }, modifier = modifier.fillMaxSize())
    }
}
