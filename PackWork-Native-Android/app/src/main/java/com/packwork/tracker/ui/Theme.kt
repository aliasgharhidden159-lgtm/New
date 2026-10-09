package com.packwork.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF15803D), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7), onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF475569), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0), onSecondaryContainer = Color(0xFF1E293B),
    background = Color(0xFFF5F7F5), onBackground = Color(0xFF111827),
    surface = Color.White, onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF2EE), onSurfaceVariant = Color(0xFF5B6560),
    outline = Color(0xFFB8C2BC), outlineVariant = Color(0xFFE1E7E2),
    error = Color(0xFFBE123C), errorContainer = Color(0xFFFFE4E6), onErrorContainer = Color(0xFF881337),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80), onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D), onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFF94A3B8), onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF334155), onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0F1512), onBackground = Color(0xFFE5EAE6),
    surface = Color(0xFF161D19), onSurface = Color(0xFFE5EAE6),
    surfaceVariant = Color(0xFF1F2823), onSurfaceVariant = Color(0xFF9BA7A0),
    outline = Color(0xFF55615A), outlineVariant = Color(0xFF2A352F),
    error = Color(0xFFFB7185), errorContainer = Color(0xFF4C0519), onErrorContainer = Color(0xFFFECDD3),
)

@Composable
fun PackWorkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

enum class Tint { Green, Amber, Rose, Slate }

/** (container, content) colours for a tint. */
@Composable
fun tintColors(t: Tint): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return when (t) {
        Tint.Green -> if (dark) Color(0xFF14532D) to Color(0xFF86EFAC) else Color(0xFFDCFCE7) to Color(0xFF15803D)
        Tint.Amber -> if (dark) Color(0xFF451A03) to Color(0xFFFCD34D) else Color(0xFFFEF3C7) to Color(0xFFB45309)
        Tint.Rose -> if (dark) Color(0xFF4C0519) to Color(0xFFFDA4AF) else Color(0xFFFFE4E6) to Color(0xFFBE123C)
        Tint.Slate -> if (dark) Color(0xFF1E293B) to Color(0xFFCBD5E1) else Color(0xFFE2E8F0) to Color(0xFF475569)
    }
}
