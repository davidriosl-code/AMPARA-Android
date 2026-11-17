package com.ecoresilia.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Capsule dock, selected glass bubble moves between sections rather than following every screen touch. */
@Composable
internal fun EcoGlassDock(selected: EcoScreen, onSelect: (EcoScreen) -> Unit) {
    val pages: List<Pair<EcoScreen, ImageVector>> = listOf(
        EcoScreen.HOME to Icons.Outlined.Home,
        EcoScreen.MAP to Icons.Outlined.Map,
        EcoScreen.BAG to Icons.Outlined.Backpack,
        EcoScreen.ASSISTANT to Icons.Outlined.ChatBubbleOutline
    )
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 9.dp)
            .height(69.dp)
            .shadow(3.dp, RoundedCornerShape(39.dp))
            .background(Color(0xF4FFFFFF), RoundedCornerShape(39.dp))
            .border(1.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(39.dp))
    ) {
        val segmentWidth = maxWidth / pages.size
        val bubbleSize = 56.dp
        val selectedIndex = pages.indexOfFirst { it.first == selected }
        val targetX = if (selectedIndex < 0) 0.dp
                      else segmentWidth * selectedIndex + (segmentWidth - bubbleSize) / 2
        val bubbleX = animateDpAsState(
            targetValue = targetX,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                                   stiffness = Spring.StiffnessLow),
            label = "glass-dock-bubble"
        )
        if (selectedIndex >= 0) {
            Box(
                Modifier.offset(x = bubbleX.value, y = 6.dp)
                    .size(bubbleSize)
                    .shadow(3.dp, CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFCFFFFFF), Color(0xB8F4FAFF))),
                        CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.95f), CircleShape)
            )
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            pages.forEach { (page, icon) ->
                val active = page == selected
                val interaction = remember(page) { MutableInteractionSource() }
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = interaction,
                        indication = null
                    ) { onSelect(page) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, page.label, Modifier.size(23.dp),
                        tint = if (active) Color(0xFFFF8B11) else Color(0xFF6684A0))
                    Spacer(Modifier.height(3.dp))
                    Text(page.label, color = if (active) Color(0xFFE87807) else Color(0xFF6684A0),
                        fontSize = 10.sp, maxLines = 1,
                        fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold
                        else androidx.compose.ui.text.font.FontWeight.Normal)
                }
            }
        }
    }
}
