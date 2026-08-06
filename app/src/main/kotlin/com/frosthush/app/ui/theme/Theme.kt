package com.frosthush.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D6FE3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF001A4E),
    secondary = Color(0xFF5A5D72),
    tertiary = Color(0xFF006874),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB1C4FF),
    onPrimary = Color(0xFF002E74),
    primaryContainer = Color(0xFF24448F),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFC4C5DD),
    tertiary = Color(0xFF4CD9EB),
)

@Composable
fun FrostHushTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
