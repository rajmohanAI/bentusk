package com.bentusk.app.feature.covers

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** A cover background. Gradients are Pro once the trial ends. */
data class CoverPalette(val colors: List<Color>, val premium: Boolean) {
    fun brush(height: Float): Brush =
        if (colors.size == 1) Brush.linearGradient(listOf(colors[0], colors[0]))
        else Brush.verticalGradient(colors, startY = 0f, endY = height)

    /** Icon colour that reads well on this background. */
    val contentColor: Color
        get() = if (colors.map { it.luminance() }.average() > 0.55) Color(0xFF1B1C20) else Color.White
}

private fun solid(hex: Long) = CoverPalette(listOf(Color(hex)), premium = false)
private fun gradient(vararg hex: Long) = CoverPalette(hex.map { Color(it) }, premium = true)

val CoverPalettes: List<CoverPalette> = listOf(
    solid(0xFFF4EFE6), solid(0xFFE9DCC9), solid(0xFFD8C3A5), solid(0xFFB08968),
    solid(0xFF7F5539), solid(0xFFF6D9D5), solid(0xFFE8A598), solid(0xFFD5DFCF),
    solid(0xFF9CAF88), solid(0xFFCFE0EA), solid(0xFF7FA7C4), solid(0xFF1B1C20),
    solid(0xFFFFFFFF), solid(0xFF000000),
    gradient(0xFFFDE2C3, 0xFFF3A683), gradient(0xFFFFD1DC, 0xFFB794F4), gradient(0xFFA1C4FD, 0xFFC2E9FB),
    gradient(0xFF84FAB0, 0xFF8FD3F4), gradient(0xFFF6D365, 0xFFFDA085), gradient(0xFF30CFD0, 0xFF330867),
    gradient(0xFF0F2027, 0xFF2C5364), gradient(0xFFE6C17E, 0xFF8C6A3A), gradient(0xFF232526, 0xFF414345),
    gradient(0xFFFF9A9E, 0xFFFAD0C4), gradient(0xFFD4FC79, 0xFF96E6A1), gradient(0xFFE0C3FC, 0xFF8EC5FC),
)
