package com.namsan.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED99C),
    onPrimary = Color(0xFF0B390E),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFDCF5DA),
    secondary = Color(0xFFD7C08C),
    surface = Color(0xFF121712),
    background = Color(0xFF0E120E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    primaryContainer = Color(0xFFDCF5DA),
    onPrimaryContainer = Color(0xFF0B390E),
    secondary = Color(0xFF7A6220),
)

@Composable
fun NamsanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
