package com.example.cyclingterminal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 暖色系配色：陶土橙 / 琥珀 / 暖棕，适合骑行终端的高对比、护眼显示（白天） */
private val WarmLightColors = lightColorScheme(
    primary = Color(0xFFB3541E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF8C5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF2D1800),
    tertiary = Color(0xFF6F5D2F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF5DED2),
    onSurfaceVariant = Color(0xFF53433B),
    outline = Color(0xFF85736A),
)

/** 暖色系深色配色（黑夜模式，保持同色相的暗暖棕） */
private val WarmDarkColors = darkColorScheme(
    primary = Color(0xFFFFB68F),
    onPrimary = Color(0xFF5C1B00),
    primaryContainer = Color(0xFF7A3A1A),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFFFCB74),
    onSecondary = Color(0xFF442B00),
    secondaryContainer = Color(0xFF654100),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFFD9C48B),
    onTertiary = Color(0xFF3B2F0E),
    background = Color(0xFF1C1613),
    onBackground = Color(0xFFF0E0D6),
    surface = Color(0xFF1C1613),
    onSurface = Color(0xFFF0E0D6),
    surfaceVariant = Color(0xFF51433A),
    onSurfaceVariant = Color(0xFFD5C2B6),
    outline = Color(0xFF9E8B80),
)

@Composable
fun CyclingTerminalTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) WarmDarkColors else WarmLightColors,
        typography = Typography(),
        content = content,
    )
}
