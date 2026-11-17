package com.ecoresilia.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ecoresilia.app.R

private class PathBasedShape(private val builder: (Size) -> Path) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(builder(size))
}

val HeartLiquidShape: Shape = PathBasedShape { size ->
    val w = size.width; val h = size.height
    Path().apply {
        moveTo(w * 0.50f, h * 0.92f)
        cubicTo(w * 0.14f, h * 0.70f, w * 0.02f, h * 0.42f, w * 0.18f, h * 0.23f)
        cubicTo(w * 0.31f, h * 0.08f, w * 0.47f, h * 0.16f, w * 0.50f, h * 0.28f)
        cubicTo(w * 0.53f, h * 0.16f, w * 0.69f, h * 0.08f, w * 0.82f, h * 0.23f)
        cubicTo(w * 0.98f, h * 0.42f, w * 0.86f, h * 0.70f, w * 0.50f, h * 0.92f)
        close()
    }
}

val CloudLiquidShape: Shape = PathBasedShape { size ->
    val w = size.width; val h = size.height
    Path().apply {
        moveTo(w * 0.18f, h * 0.68f)
        cubicTo(w * 0.05f, h * 0.66f, w * 0.03f, h * 0.45f, w * 0.16f, h * 0.39f)
        cubicTo(w * 0.19f, h * 0.18f, w * 0.40f, h * 0.12f, w * 0.53f, h * 0.24f)
        cubicTo(w * 0.62f, h * 0.08f, w * 0.86f, h * 0.12f, w * 0.90f, h * 0.32f)
        cubicTo(w * 1.01f, h * 0.35f, w * 1.01f, h * 0.58f, w * 0.86f, h * 0.62f)
        lineTo(w * 0.18f, h * 0.68f)
        close()
    }
}

@Composable
fun FrostCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.90f)),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

@Composable
fun SectionHeading(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color(0xFF234759))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF638392))
    }
}

@Composable
fun PressTile(title: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "tile-scale")
    Surface(
        onClick = onClick, interactionSource = source,
        modifier = modifier.fillMaxWidth().scale(scale),
        color = Color.White.copy(alpha = 0.84f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.95f))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF254A5B))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF698694))
            }
            if (trailing != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF3E6)) {
                    Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) { trailing() }
                }
            }
        }
    }
}

@Composable
fun InfoNote(message: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .88f))) {
        Text(message, modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF46717F))
    }
}

@Composable
fun AmparaAmberGlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(19.dp)
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "button-scale")
    // Cristal claro por defecto; naranja suave solo mientras se mantiene pulsado.
    val topColor by animateColorAsState(
        if (pressed) Color(0xF0FFE2BE) else Color(0xEAFFFFFF), label = "glass-top"
    )
    val bottomColor by animateColorAsState(
        if (pressed) Color(0xD9FFA752) else Color(0xA9DBEFFF), label = "glass-bottom"
    )
    val textColor by animateColorAsState(
        if (pressed) Color(0xFF6F3C0D) else Color(0xFF24516D), label = "glass-text"
    )
    val borderColor by animateColorAsState(
        if (pressed) Color(0xFFFFE7C8) else Color.White.copy(alpha = .96f), label = "glass-border"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 49.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)), shape)
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = .32f), Color.Transparent)), shape
            )
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) textColor else Color(0xFF7B919D),
            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AmparaAmberGlassButton(label = label, onClick = onClick, modifier = modifier, enabled = enabled)
}

@Composable
fun LiquidShapeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = HeartLiquidShape,
    size: androidx.compose.ui.unit.Dp = 62.dp
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val tint by animateColorAsState(
        if (pressed) Color(0xFFC76A0C) else Color(0xFF2B8199), label = "iconTint"
    )
    val border by animateColorAsState(
        if (pressed) Color(0xFFFFC88D) else Color.White.copy(alpha = .96f), label = "iconBorder"
    )
    val top by animateColorAsState(
        if (pressed) Color(0xEBFFD5A4) else Color(0xBFFFFFFF), label = "iconTop"
    )
    val bottom by animateColorAsState(
        if (pressed) Color(0xAAFFAA48) else Color(0x88DCEFFF), label = "iconBottom"
    )
    val scale by animateFloatAsState(if (pressed) .98f else 1f, label = "iconScale")

    Box(
        modifier = modifier
            .scale(scale)
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)), shape)
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = .20f), Color.Transparent)), shape
            )
            .border(1.dp, border, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
internal fun AmparaPageBanner(title: String, subtitle: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(23.dp),
        color = Color.White.copy(alpha = .34f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .90f))) {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            Image(painter = painterResource(R.drawable.ampara_hero),
                contentDescription = null, modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, alpha = .08f)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(
                Color(0xA8FFFFFF), Color(0x78FFF2E2), Color(0x55D6ECFB)
            ))))
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Image(painterResource(R.drawable.ampara_mark),
                    contentDescription = null, modifier = Modifier.size(48.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, color = Color(0xFF163F52), style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, color = Color(0xFF537187), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
