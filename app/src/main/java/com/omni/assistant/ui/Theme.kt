package com.omni.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
val LocalFontSizeScale = staticCompositionLocalOf { 1.0f }

data class ThemePreset(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val bgColor: Color,
    val description: String
)

val THEME_PRESETS = listOf(
    ThemePreset("dark_green", "Emerald Dark Green", Color(0xFF10B981), Color(0xFF06140D), "Rich forest green & cyber emerald"),
    ThemePreset("grey_green", "Sage & Grey Green", Color(0xFF84A98C), Color(0xFF151917), "Muted slate & organic sage green"),
    ThemePreset("dark", "Claude Obsidian", Color(0xFFA78BFA), Color(0xFF0A0812), "Deep violet charcoal dark theme"),
    ThemePreset("cyber_cyan", "Cyberpunk Cyan", Color(0xFF00F0FF), Color(0xFF070B14), "Electric neon cyan & deep abyss"),
    ThemePreset("royal_gold", "Obsidian Gold", Color(0xFFF59E0B), Color(0xFF0D0B08), "24K luxury gold on jet black"),
    ThemePreset("deep_ocean", "Deep Ocean Abyss", Color(0xFF38BDF8), Color(0xFF050E18), "Midnight aquatic navy & teal"),
    ThemePreset("crimson_rose", "Sunset Crimson", Color(0xFFFB7185), Color(0xFF14070B), "Velvet burgundy & ruby rose"),
    ThemePreset("nord_frost", "Nordic Frost", Color(0xFF88C0D0), Color(0xFF151A22), "Arctic slate & ice frost"),
    ThemePreset("warm_espresso", "Mocha Espresso", Color(0xFFD4A373), Color(0xFF15100D), "Warm dark espresso & caramel"),
    ThemePreset("royal_amethyst", "Royal Amethyst", Color(0xFFC084FC), Color(0xFF0F071A), "Luminous amethyst & royal purple"),
    ThemePreset("light", "Clean Ivory Light", Color(0xFF6D28D9), Color(0xFFF7F5FF), "Clean, bright paper ivory"),
    ThemePreset("system", "System Default", Color(0xFFA78BFA), Color(0xFF0A0812), "Matches device system theme")
)

// 1. Classic Dark (Obsidian Purple)
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
    onError = Color(0xFF3B0A08)
)

private val DarkExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF22134A), Color(0xFF0E0A1D), Color(0xFF0A0812))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFC084FC))),
    userBubble = Color(0xFFF4F1FF),
    onUserBubble = Color(0xFF14101F),
    assistantBubble = Color(0xFF1C1829),
    glow = Color(0xFF8B5CF6),
    lime = Color(0xFFC6F432),
    dark = true
)

// 2. Clean Ivory Light
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
    onError = Color(0xFFFFFFFF)
)

private val LightExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFFE9DFFF), Color(0xFFF7F5FF), Color(0xFFF7F5FF))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFC084FC))),
    userBubble = Color(0xFF6D28D9),
    onUserBubble = Color(0xFFFFFFFF),
    assistantBubble = Color(0xFFFFFFFF),
    glow = Color(0xFF8B5CF6),
    lime = Color(0xFF4D7C0F),
    dark = false
)

// 3. Emerald Dark Green
private val DarkGreenColors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF042F1E),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF022C1A),
    background = Color(0xFF06140D),
    onBackground = Color(0xFFE6F4EA),
    surface = Color(0xFF06140D),
    onSurface = Color(0xFFE6F4EA),
    surfaceVariant = Color(0xFF0E271D),
    onSurfaceVariant = Color(0xFFA3CDB8),
    surfaceContainer = Color(0xFF0B2117),
    surfaceContainerHigh = Color(0xFF102D20),
    surfaceContainerHighest = Color(0xFF163929),
    outline = Color(0xFF1F513A),
    outlineVariant = Color(0xFF133827),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val DarkGreenExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF083321), Color(0xFF092216), Color(0xFF06140D))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF34D399))),
    userBubble = Color(0xFFD1FAE5),
    onUserBubble = Color(0xFF064E3B),
    assistantBubble = Color(0xFF0E271D),
    glow = Color(0xFF10B981),
    lime = Color(0xFF34D399),
    dark = true
)

// 4. Sage & Grey Green
private val GreyGreenColors = darkColorScheme(
    primary = Color(0xFF84A98C),
    onPrimary = Color(0xFF1A261E),
    primaryContainer = Color(0xFF354F52),
    onPrimaryContainer = Color(0xFFCAD2C5),
    secondary = Color(0xFF52796F),
    onSecondary = Color(0xFF111E18),
    background = Color(0xFF151917),
    onBackground = Color(0xFFE4E9E6),
    surface = Color(0xFF151917),
    onSurface = Color(0xFFE4E9E6),
    surfaceVariant = Color(0xFF222825),
    onSurfaceVariant = Color(0xFFACB7B0),
    surfaceContainer = Color(0xFF1C221F),
    surfaceContainerHigh = Color(0xFF242C28),
    surfaceContainerHighest = Color(0xFF2D3732),
    outline = Color(0xFF45554D),
    outlineVariant = Color(0xFF2C3731),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val GreyGreenExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF25332C), Color(0xFF1B231F), Color(0xFF151917))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF52796F), Color(0xFF84A98C))),
    userBubble = Color(0xFFD8E2DC),
    onUserBubble = Color(0xFF1E2822),
    assistantBubble = Color(0xFF202723),
    glow = Color(0xFF84A98C),
    lime = Color(0xFF84A98C),
    dark = true
)

// 5. Cyberpunk Cyan
private val CyberCyanColors = darkColorScheme(
    primary = Color(0xFF00F0FF),
    onPrimary = Color(0xFF002930),
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF001F2B),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFE0F2FE),
    surface = Color(0xFF070B14),
    onSurface = Color(0xFFE0F2FE),
    surfaceVariant = Color(0xFF0E1A2B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF0E1626),
    surfaceContainerHigh = Color(0xFF152238),
    surfaceContainerHighest = Color(0xFF1D2E4A),
    outline = Color(0xFF0284C7),
    outlineVariant = Color(0xFF0C4A6E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val CyberCyanExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF08273E), Color(0xFF0B1424), Color(0xFF070B14))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF00F0FF))),
    userBubble = Color(0xFFE0F2FE),
    onUserBubble = Color(0xFF032541),
    assistantBubble = Color(0xFF111D30),
    glow = Color(0xFF00F0FF),
    lime = Color(0xFF00F0FF),
    dark = true
)

// 6. Obsidian & Royal Gold
private val RoyalGoldColors = darkColorScheme(
    primary = Color(0xFFF59E0B),
    onPrimary = Color(0xFF2D1E00),
    primaryContainer = Color(0xFF452B03),
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF2B1C00),
    background = Color(0xFF0D0B08),
    onBackground = Color(0xFFFEF3C7),
    surface = Color(0xFF0D0B08),
    onSurface = Color(0xFFFEF3C7),
    surfaceVariant = Color(0xFF1F1A12),
    onSurfaceVariant = Color(0xFFD4C2A1),
    surfaceContainer = Color(0xFF17140E),
    surfaceContainerHigh = Color(0xFF211D15),
    surfaceContainerHighest = Color(0xFF2C271D),
    outline = Color(0xFF785918),
    outlineVariant = Color(0xFF423211),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val RoyalGoldExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF2B1C06), Color(0xFF17130B), Color(0xFF0D0B08))),
    accent = Brush.horizontalGradient(listOf(Color(0xFFD97706), Color(0xFFFBBF24))),
    userBubble = Color(0xFFFEF3C7),
    onUserBubble = Color(0xFF3B2304),
    assistantBubble = Color(0xFF1C1811),
    glow = Color(0xFFF59E0B),
    lime = Color(0xFFFBBF24),
    dark = true
)

// 7. Deep Ocean Abyss
private val DeepOceanColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF002238),
    primaryContainer = Color(0xFF073A60),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF14B8A6),
    onSecondary = Color(0xFF002722),
    background = Color(0xFF050E18),
    onBackground = Color(0xFFE0F2FE),
    surface = Color(0xFF050E18),
    onSurface = Color(0xFFE0F2FE),
    surfaceVariant = Color(0xFF0C2034),
    onSurfaceVariant = Color(0xFF93C5FD),
    surfaceContainer = Color(0xFF0A192B),
    surfaceContainerHigh = Color(0xFF10273F),
    surfaceContainerHighest = Color(0xFF173554),
    outline = Color(0xFF0284C7),
    outlineVariant = Color(0xFF084B78),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val DeepOceanExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF082845), Color(0xFF081829), Color(0xFF050E18))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8))),
    userBubble = Color(0xFFE0F2FE),
    onUserBubble = Color(0xFF042036),
    assistantBubble = Color(0xFF0E2034),
    glow = Color(0xFF38BDF8),
    lime = Color(0xFF14B8A6),
    dark = true
)

// 8. Sunset Crimson
private val CrimsonColors = darkColorScheme(
    primary = Color(0xFFFB7185),
    onPrimary = Color(0xFF380614),
    primaryContainer = Color(0xFF5E0B24),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFF43F5E),
    onSecondary = Color(0xFF330512),
    background = Color(0xFF14070B),
    onBackground = Color(0xFFFFE4E6),
    surface = Color(0xFF14070B),
    onSurface = Color(0xFFFFE4E6),
    surfaceVariant = Color(0xFF280F18),
    onSurfaceVariant = Color(0xFFF4A8B7),
    surfaceContainer = Color(0xFF220D15),
    surfaceContainerHigh = Color(0xFF2E131E),
    surfaceContainerHighest = Color(0xFF3C1827),
    outline = Color(0xFF9F1239),
    outlineVariant = Color(0xFF5A0B22),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val CrimsonExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF380917), Color(0xFF1C0910), Color(0xFF14070B))),
    accent = Brush.horizontalGradient(listOf(Color(0xFFE11D48), Color(0xFFFB7185))),
    userBubble = Color(0xFFFFE4E6),
    onUserBubble = Color(0xFF430517),
    assistantBubble = Color(0xFF260F18),
    glow = Color(0xFFFB7185),
    lime = Color(0xFFF43F5E),
    dark = true
)

// 9. Nordic Frost
private val NordColors = darkColorScheme(
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF1E2830),
    primaryContainer = Color(0xFF2E3E4C),
    onPrimaryContainer = Color(0xFFECEFF4),
    secondary = Color(0xFF81A1C1),
    onSecondary = Color(0xFF18222C),
    background = Color(0xFF151A22),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF151A22),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF222B37),
    onSurfaceVariant = Color(0xFFD8DEE9),
    surfaceContainer = Color(0xFF1E2530),
    surfaceContainerHigh = Color(0xFF27303E),
    surfaceContainerHighest = Color(0xFF313D4D),
    outline = Color(0xFF4C566A),
    outlineVariant = Color(0xFF2E3440),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val NordExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF202C3B), Color(0xFF19202A), Color(0xFF151A22))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF5E81AC), Color(0xFF88C0D0))),
    userBubble = Color(0xFFECEFF4),
    onUserBubble = Color(0xFF19222D),
    assistantBubble = Color(0xFF222B37),
    glow = Color(0xFF88C0D0),
    lime = Color(0xFF88C0D0),
    dark = true
)

// 10. Mocha Espresso
private val EspressoColors = darkColorScheme(
    primary = Color(0xFFD4A373),
    onPrimary = Color(0xFF2B1D11),
    primaryContainer = Color(0xFF483220),
    onPrimaryContainer = Color(0xFFFAEDCD),
    secondary = Color(0xFFCCD5AE),
    onSecondary = Color(0xFF232817),
    background = Color(0xFF15100D),
    onBackground = Color(0xFFFAEDCD),
    surface = Color(0xFF15100D),
    onSurface = Color(0xFFFAEDCD),
    surfaceVariant = Color(0xFF241C17),
    onSurfaceVariant = Color(0xFFE9D8A6),
    surfaceContainer = Color(0xFF201915),
    surfaceContainerHigh = Color(0xFF2C231D),
    surfaceContainerHighest = Color(0xFF392E26),
    outline = Color(0xFF705238),
    outlineVariant = Color(0xFF433021),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val EspressoExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF302015), Color(0xFF1D1510), Color(0xFF15100D))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF9A7B56), Color(0xFFD4A373))),
    userBubble = Color(0xFFFAEDCD),
    onUserBubble = Color(0xFF2E1F14),
    assistantBubble = Color(0xFF251C17),
    glow = Color(0xFFD4A373),
    lime = Color(0xFFCCD5AE),
    dark = true
)

// 11. Royal Amethyst
private val AmethystColors = darkColorScheme(
    primary = Color(0xFFC084FC),
    onPrimary = Color(0xFF2B0A4D),
    primaryContainer = Color(0xFF49177F),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFFA855F7),
    onSecondary = Color(0xFF220540),
    background = Color(0xFF0F071A),
    onBackground = Color(0xFFF3E8FF),
    surface = Color(0xFF0F071A),
    onSurface = Color(0xFFF3E8FF),
    surfaceVariant = Color(0xFF1E1033),
    onSurfaceVariant = Color(0xFFD8B4FE),
    surfaceContainer = Color(0xFF190D2B),
    surfaceContainerHigh = Color(0xFF24143D),
    surfaceContainerHighest = Color(0xFF301C4F),
    outline = Color(0xFF7E22CE),
    outlineVariant = Color(0xFF4C1D95),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A08)
)

private val AmethystExtras = OmniExtras(
    background = Brush.verticalGradient(listOf(Color(0xFF290B4A), Color(0xFF160A26), Color(0xFF0F071A))),
    accent = Brush.horizontalGradient(listOf(Color(0xFF7E22CE), Color(0xFFC084FC))),
    userBubble = Color(0xFFF3E8FF),
    onUserBubble = Color(0xFF270845),
    assistantBubble = Color(0xFF1E1033),
    glow = Color(0xFFC084FC),
    lime = Color(0xFFA855F7),
    dark = true
)

private fun resolveColors(mode: String, systemDark: Boolean): Pair<ColorScheme, OmniExtras> {
    return when (mode.lowercase()) {
        "dark_green" -> DarkGreenColors to DarkGreenExtras
        "grey_green" -> GreyGreenColors to GreyGreenExtras
        "cyber_cyan" -> CyberCyanColors to CyberCyanExtras
        "royal_gold" -> RoyalGoldColors to RoyalGoldExtras
        "deep_ocean" -> DeepOceanColors to DeepOceanExtras
        "crimson_rose" -> CrimsonColors to CrimsonExtras
        "nord_frost" -> NordColors to NordExtras
        "warm_espresso" -> EspressoColors to EspressoExtras
        "royal_amethyst" -> AmethystColors to AmethystExtras
        "dark" -> DarkColors to DarkExtras
        "light" -> LightColors to LightExtras
        else -> if (systemDark) DarkColors to DarkExtras else LightColors to LightExtras
    }
}

fun fontSizeToScale(size: String): Float = when (size.lowercase()) {
    "small" -> 0.85f
    "large" -> 1.18f
    "huge" -> 1.35f
    else -> 1.0f
}

@Composable
fun OmniTheme(mode: String, fontSize: String = "medium", content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val (scheme, extras) = resolveColors(mode, systemDark)
    val scale = fontSizeToScale(fontSize)

    CompositionLocalProvider(
        LocalOmni provides extras,
        LocalFontSizeScale provides scale
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
