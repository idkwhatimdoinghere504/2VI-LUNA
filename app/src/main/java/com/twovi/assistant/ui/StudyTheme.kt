package com.twovi.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudyColors = lightColorScheme(
    primary = Color(0xFF684BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE7FF),
    onPrimaryContainer = Color(0xFF271565),
    secondary = Color(0xFF168B78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F4EC),
    onSecondaryContainer = Color(0xFF073D34),
    background = Color(0xFFF7F5FC),
    onBackground = Color(0xFF211D2C),
    surface = Color.White,
    onSurface = Color(0xFF211D2C),
    surfaceVariant = Color(0xFFF0EDF6),
    onSurfaceVariant = Color(0xFF625C6F),
    outline = Color(0xFFE5E0EC),
    error = Color(0xFFB3261E),
)

@Composable
fun StudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudyColors,
        content = content,
    )
}
