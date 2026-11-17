package com.ecoresilia.app.ui

import android.animation.ValueAnimator
import android.graphics.RenderEffect as FrameworkRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * RC4.5.2 · lente líquida reactiva.
 * En Android 13+ refracta píxeles reales bajo el dedo; no consume eventos táctiles.
 * La fuerza se reduce y el filtro desaparece al soltar. En API anteriores no
 * se aplica shader (la respuesta luminosa dibujada por EcoApp queda disponible).
 * El efecto no se aplica al mapa de pantalla completa para proteger sus gestos.
 */
@Composable
internal fun Modifier.amparaTouchRefraction(
    touch: Offset,
    opacity: Float,
    enabled: Boolean
): Modifier {
    val radiusPx = with(LocalDensity.current) { 64.dp.toPx() }
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val shadersAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && animationsEnabled
    if (!shadersAvailable || !enabled) return this

    val shader = remember { RuntimeShader(AMPARA_TOUCH_AGSL) }
    return this.graphicsLayer {
        if (opacity > 0.005f && (!touch.x.isNaN() && !touch.y.isNaN()) && size.width > 0f && size.height > 0f) {
            shader.setFloatUniform("touch", touch.x, touch.y)
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("radius", radiusPx)
            shader.setFloatUniform("intensity", opacity.coerceIn(0f, 1f))
            renderEffect = FrameworkRenderEffect.createRuntimeShaderEffect(
                shader, "ui"
            ).asComposeRenderEffect()
        } else {
            renderEffect = null
        }
    }
}

/**
 * Refraction intentionally subtle: 4-5 px near the lens. Pixels outside the
 * local circular profile are untouched; light rim gives a glass impression.
 */
private const val AMPARA_TOUCH_AGSL = """
uniform shader ui;
uniform float2 touch;
uniform float2 resolution;
uniform float radius;
uniform float intensity;

half4 main(float2 xy) {
    float2 v = xy - touch;
    float dist = length(v);
    if (dist >= radius || intensity <= 0.001) {
        return ui.eval(xy);
    }

    float2 dir = v / max(dist, 1.0);
    float outer = 1.0 - smoothstep(radius * 0.68, radius, dist);
    float inner = smoothstep(0.0, radius * 0.18, dist);
    float shape = inner * outer;
    float2 uv = clamp(xy - dir * (5.5 * intensity * shape),
                      float2(0.0, 0.0), resolution - float2(1.0, 1.0));
    half4 result = ui.eval(uv);
    float shine = (1.0 - smoothstep(0.0, radius * 0.18,
        abs(dist - radius * 0.73))) * intensity;
    result.rgb += half3(0.028, 0.032, 0.038) * half(shine);
    return result;
}
"""
