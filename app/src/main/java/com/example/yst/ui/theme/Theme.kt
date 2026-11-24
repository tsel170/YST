package com.example.yst.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFF0E1116)
private val Surface = Color(0xFF171C24)
private val SurfaceVariant = Color(0xFF232A35)
private val Primary = Color(0xFF8EAAFF)
private val OnPrimary = Color(0xFF0B1220)
private val Secondary = Color(0xFF8BE0C2)
private val OnSurface = Color(0xFFE8EDF5)
private val OnSurfaceVariant = Color(0xFFB7C0CE)
private val Outline = Color(0xFF3A4454)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnPrimary,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    primaryContainer = Color(0xFF243456),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondaryContainer = Color(0xFF1C3A32),
    onSecondaryContainer = Color(0xFFC8F6E6),
)

@Composable
fun VoiceTriggerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
