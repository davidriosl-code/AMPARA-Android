package com.ecoresilia.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/** Paisaje AMPARA: cielo azul, amanecer sutil y montañas, basado en la referencia aprobada. */
@Composable
internal fun EcoLiquidBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(brush = Brush.verticalGradient(listOf(
            Color(0xFFCCE6FA), Color(0xFFE5F2FE), Color(0xFFF6FBFF)
        )))
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x6BFFFFFF), Color.Transparent)),
            center = Offset(w * .14f, h * .12f), radius = w * .65f
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x5FFFD5A7), Color.Transparent)),
            center = Offset(w * 1.02f, h * .76f), radius = w * .65f
        )
        // Planos de montaña muy tenues: no ocultan información ni controles.
        val distant = Path().apply {
            moveTo(0f, h * .79f)
            cubicTo(w * .13f, h * .74f, w * .21f, h * .84f, w * .37f, h * .77f)
            cubicTo(w * .51f, h * .70f, w * .64f, h * .80f, w * .75f, h * .74f)
            cubicTo(w * .87f, h * .69f, w * .96f, h * .77f, w, h * .75f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(distant, brush = Brush.verticalGradient(
            listOf(Color(0x6098C4E5), Color(0x2286BBE0)),
            startY = h * .72f, endY = h
        ))
        val foreground = Path().apply {
            moveTo(0f, h * .89f)
            cubicTo(w * .14f, h * .81f, w * .25f, h * .88f, w * .36f, h * .83f)
            cubicTo(w * .53f, h * .77f, w * .58f, h * .91f, w * .77f, h * .85f)
            cubicTo(w * .87f, h * .81f, w * .97f, h * .87f, w, h * .86f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(foreground, brush = Brush.verticalGradient(
            listOf(Color(0x6586B6DC), Color(0x1E6DA8D5)),
            startY = h * .81f, endY = h
        ))
        // Reflejo tipo burbuja Liquid Glass en esquinas; decorativo y no interactivo.
        val center = Offset(w * .89f, h * .19f)
        drawCircle(Color.White.copy(alpha = .09f), radius = w * .14f, center = center)
        drawCircle(Color.White.copy(alpha = .50f), radius = w * .14f, center = center,
            style = Stroke(width = 1.5f))
    }
}
