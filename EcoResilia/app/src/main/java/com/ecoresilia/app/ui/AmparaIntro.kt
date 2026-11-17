package com.ecoresilia.app.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ecoresilia.app.R

/** Original 60fps, 4.5-second movie supplied by the AMPARA creator.
 *  Played only on a cold app launch; pressing a notification skips it.
 *  Tap "Omitir" for immediate access to emergency information.
 */
@Composable
internal fun AmparaIntro(onFinished: () -> Unit) {
    val currentDone by rememberUpdatedState(onFinished)
    val playerHolder = remember { arrayOfNulls<MediaPlayer>(1) }
    LaunchedEffect(Unit) {
        // Safety timeout if local playback never reaches completion on a particular device.
        kotlinx.coroutines.delay(8500)
        currentDone()
    }
    DisposableEffect(Unit) {
        onDispose {
            try { playerHolder[0]?.release() } catch (_: Exception) {}
            playerHolder[0] = null
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF073940))) {
        AndroidView(
            factory = { ctx ->
                val textureView = TextureView(ctx)
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            val surface = Surface(texture)
                            try {
                                val player = MediaPlayer()
                                playerHolder[0] = player
                                player.setSurface(surface)
                                surface.release()
                                val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.ampara_intro}")
                                player.setDataSource(ctx, uri)
                                player.setVolume(0f, 0f)
                                player.setOnPreparedListener { prepared ->
                                    // Centre-crop when display aspect is taller than the 9:16 source.
                                    val viewW = textureView.width.toFloat().coerceAtLeast(1f)
                                    val viewH = textureView.height.toFloat().coerceAtLeast(1f)
                                    val videoAspect = prepared.videoWidth.toFloat() / prepared.videoHeight.coerceAtLeast(1)
                                    val viewAspect = viewW / viewH
                                    val matrix = Matrix()
                                    if (videoAspect > viewAspect) {
                                        matrix.setScale(videoAspect / viewAspect, 1f, viewW / 2f, viewH / 2f)
                                    } else {
                                        matrix.setScale(1f, viewAspect / videoAspect, viewW / 2f, viewH / 2f)
                                    }
                                    textureView.setTransform(matrix)
                                    prepared.start()
                                }
                                player.setOnCompletionListener { currentDone() }
                                player.setOnErrorListener { _, _, _ -> currentDone(); true }
                                player.prepareAsync()
                            } catch (_: Exception) {
                                try { surface.release() } catch (_: Exception) {}
                                currentDone()
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            try { playerHolder[0]?.release() } catch (_: Exception) {}
                            playerHolder[0] = null
                            return true
                        }
                    }
                textureView
            },
            modifier = Modifier.fillMaxSize()
        )
        TextButton(onClick = currentDone, modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 18.dp, bottom = 35.dp)
            .background(Color(0x990B3944), RoundedCornerShape(24.dp))) {
            Text("Omitir", color = Color.White)
        }
    }
}
