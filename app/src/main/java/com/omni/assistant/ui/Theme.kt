package com.omni.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Colours and brushes the Material scheme has no slot for. */
@Immutable
class OmniExtras(
    val background: Brush,
    val accent: Brush,
    val userBubble: Color,
    val onUserBubble: Color,
    val assistantBubble: Color,
    val glow: Color,
    val lime: Color,
    val dark: Boolean
)

val LocalOmni = staticCompositionLocalOf<OmniExtras> { error("OmniTheme not provided") }

private val AccentBrush = Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFC084FC)))

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1B1035),
    primaryContainer = Color(0xFF2E1F5C),
    onPrimaryContainer = Color(0xFFE9E1FF),
    secondary = Color(0xFFC6F432),
    onSecondary = Color(0xFF11190A),
    background = Color(0xFF0A0812),
    onBackground = Color(0xFFEDEBF7),
    surface = Color(0xFF0A0812),
    onSurface = Color(0xFFEDEBF7),
    surfaceVariant = Color(0xFF1D1A2B),
    onSurfaceVariant = Color(0xFFB9B4CE),
    surfaceContainer = Color(0xFF15121F),
    surfaceContainerHigh = Color(0xFF1C1829),
    surfaceContainerHighest = Color(0xFF241F34),
    outline = Color(0xFF4A4372),
    outlineVariant = Color(0xFF2A2542),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08),
    errorContainer = Color(0xFF4A1B1B),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF25005A),
    secondary = Color(0xFF4D7C0F),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F5FF),
    onBackground = Color(0xFF1A1626),
    surface = Color(0xFFF7F5FF),
    onSurface = Color(0xFF1A1626),
    surfaceVariant = Color(0xFFEDE8FA),
    onSurfaceVariant = Color(0xFF4A4560),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF3EFFF),
    surfaceContainerHighest = Color(0xFFEAE4FB),
    outline = Color(0xFF7D7599),
    outlineVariant = Color(0xFFDCD5F0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF22134A), Color(0xFF0E0A1D), Color(0xFF0A0812))),
    accent = AccentBrush,
    userBubble = Color(0xFFF4F1FF),
    onUserBubble = Color(0xFF14101F),
    assistantBubble = Color(0xFF1C1829),
    glow = Color(0xFF8B5CF6),
    lime = Color(0xFFC6F432),
    dark = true
)

private val LightExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFFE9DFFF), Color(0xFFF7F5FF), Color(0xFFF7F5FF))),
    accent = AccentBrush,
    userBubble = Color(0xFF6D28D9),
    onUserBubble = Color(0xFFFFFFFF),
    assistantBubble = Color(0xFFFFFFFF),
    glow = Color(0xFF8B5CF6),
    lime = Color(0xFF4D7C0F),
    dark = false
)

@Composable
fun OmniTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalOmni provides (if (dark) DarkExtras else LightExtras)) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
