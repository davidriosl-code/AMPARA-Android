package com.ecoresilia.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * AMPARA RC4.5.4: interruptor global de apariencia Liquid Glass.
 *
 * Conserva la semántica accesible de un switch, su estado controlado desde
 * los ViewModels y el control parental/sistema sobre las animaciones.
 * Microanimación: desplazamiento con resorte, presión del pomo y brillo breve.
 * No sustituye los checkboxes de selección ni solicita permisos nuevos.
 */
@Composable
internal fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Interruptor"
) {
    val capsule = RoundedCornerShape(percent = 50)
    val knobPosition by animateDpAsState(
        targetValue = if (checked) 31.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ampara-glass-switch-position"
    )
    val pressScale = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    var isFirstRender by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (isFirstRender) {
            isFirstRender = false
        } else {
            launch {
                pressScale.snapTo(.88f)
                pressScale.animateTo(1f, spring(dampingRatio = .62f, stiffness = Spring.StiffnessMedium))
            }
            launch {
                flash.snapTo(.48f)
                flash.animateTo(0f, tween(durationMillis = 350))
            }
        }
    }

    Box(
        modifier = modifier
            .size(width = 65.dp, height = 38.dp)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "Activado" else "Desactivado"
            }
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, interactionSource = remember { MutableInteractionSource() }, indication = null, onValueChange = onCheckedChange)
            .clip(capsule)
            .background(
                Brush.linearGradient(
                    if (checked) listOf(
                        Color(0xC1FFEBD7), Color(0xA8FFC48C), Color(0x78FFFFFF)
                    ) else listOf(
                        Color(0xB9FFFFFF), Color(0x9FDFECF8), Color(0x8DFFFFFF)
                    )
                ), capsule
            )
            .border(
                BorderStroke(1.2.dp, Brush.linearGradient(listOf(
                    Color.White.copy(alpha = .98f),
                    Color.White.copy(alpha = .55f),
                    Color(0xFFADC8E0).copy(alpha = .68f)
                ))), capsule
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Reflejo superior del cristal.
        Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = 3.dp)
                .fillMaxWidth(.77f)
                .height(1.4.dp)
                .background(Color.White.copy(alpha = .85f), RoundedCornerShape(50))
        )
        if (flash.value > .001f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value), capsule))
        }
        Box(
            Modifier.offset(x = knobPosition)
                .size(30.dp)
                .scale(pressScale.value)

                .background(
                    Brush.linearGradient(listOf(Color.White, Color(0xFFF2F9FF), Color(0xFFE6F0FA))),
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = .98f), CircleShape),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                Modifier.padding(top = 4.dp)
                    .size(width = 17.dp, height = 2.dp)
                    .background(Color.White, RoundedCornerShape(50))
            )
        }
    }
}
