package com.ecoresilia.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Weaddy-inspired ocean/sky identity; reserved red/amber remain semantic safety indicators. */
private val WeaddyColors = lightColorScheme(
    primary = Color(0xFF068F91), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F3F0), onPrimaryContainer = Color(0xFF16464D),
    secondary = Color(0xFF2A90A9), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAEFF9), onSecondaryContainer = Color(0xFF244A61),
    tertiary = Color(0xFFF39826), onTertiary = Color.White,
    background = Color(0xFFF0F8F7), onBackground = Color(0xFF163F47),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF163F47),
    surfaceVariant = Color(0xFFF1FAFC), onSurfaceVariant = Color(0xFF668291),
    outline = Color(0xFFCFDFE5),
    error = Color(0xFFBB4350), onError = Color.White
)
private val WeaddyType = Typography(
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)
@Composable
fun EcoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WeaddyColors, typography = WeaddyType, content = content)
}
