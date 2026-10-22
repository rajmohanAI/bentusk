package com.bentusk.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BentuskColors {
    val Ink = Color(0xFF0D0E11)
    val Graphite = Color(0xFF17181D)
    val Slate = Color(0xFF22242B)
    val Gold = Color(0xFFE6C17E)
    val GoldDeep = Color(0xFFB8914F)
    val Pearl = Color(0xFFF4F1EA)
    val Mist = Color(0xFFA3A6AE)
    val Coral = Color(0xFFFF7A6B)
    val Sky = Color(0xFF8FB8FF)
}

private val Dark = darkColorScheme(
    primary = BentuskColors.Gold,
    onPrimary = BentuskColors.Ink,
    primaryContainer = Color(0xFF3A3021),
    onPrimaryContainer = BentuskColors.Gold,
    secondary = BentuskColors.Sky,
    onSecondary = BentuskColors.Ink,
    tertiary = BentuskColors.Coral,
    background = BentuskColors.Ink,
    onBackground = BentuskColors.Pearl,
    surface = BentuskColors.Ink,
    onSurface = BentuskColors.Pearl,
    surfaceVariant = BentuskColors.Slate,
    onSurfaceVariant = BentuskColors.Mist,
    surfaceContainer = BentuskColors.Graphite,
    surfaceContainerHigh = BentuskColors.Slate,
    surfaceContainerLow = Color(0xFF131418),
    outline = Color(0xFF3A3D46),
    outlineVariant = Color(0xFF2A2C33),
    error = BentuskColors.Coral,
)

private val Light = lightColorScheme(
    primary = Color(0xFF7A5B22),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E4C2),
    onPrimaryContainer = Color(0xFF2A1E08),
    secondary = Color(0xFF2E5AA8),
    tertiary = Color(0xFFC0473A),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF16171B),
    surface = Color(0xFFFAF8F4),
    onSurface = Color(0xFF16171B),
    surfaceVariant = Color(0xFFECE8E0),
    onSurfaceVariant = Color(0xFF5A5C63),
    surfaceContainer = Color(0xFFF1EEE7),
    surfaceContainerHigh = Color(0xFFE9E5DC),
    surfaceContainerLow = Color(0xFFF6F3EE),
    outline = Color(0xFFC9C4BA),
    outlineVariant = Color(0xFFE0DBD1),
)

private val BentuskTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

private val BentuskShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Tabular figures keep counters and sizes from jittering as numbers change. */
val NumericStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun BentuskTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = BentuskTypography,
        shapes = BentuskShapes,
        content = content,
    )
}
