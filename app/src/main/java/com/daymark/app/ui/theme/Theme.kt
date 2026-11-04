package com.daymark.app.ui.theme

import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daymark.app.data.ThemeMode

/** Semantic colours that Material's scheme doesn't model. */
@Immutable
data class DaymarkColors(
    val clean: Color,
    val relapse: Color,
    val untracked: Color,
    val accentWarm: Color,
    val heroGradientTop: Color,
    val heroGradientBottom: Color,
    val isDark: Boolean,
)

/** One accent = primary family + hero gradient, for light and dark. Surfaces stay neutral for every accent. */
enum class Accent(
    val id: String,
    val label: String,
    val light: Long, val dark: Long,
    val containerLight: Long, val containerDark: Long,
    val onContainerLight: Long, val onContainerDark: Long,
    val heroLight: Pair<Long, Long>, val heroDark: Pair<Long, Long>,
) {
    DUSK("dusk", "Dusk", 0xFF4C55C7, 0xFFB4BAFF, 0xFFE0E2FF, 0xFF2E3478, 0xFF0E1257, 0xFFE0E2FF, 0xFF5A63DA to 0xFF2E3590, 0xFF2D3375 to 0xFF171A3C),
    OCEAN("ocean", "Ocean", 0xFF1F6FB2, 0xFF9CCBFF, 0xFFD4E6FA, 0xFF0E4170, 0xFF001D36, 0xFFD4E6FA, 0xFF2A80C6 to 0xFF164A7E, 0xFF164272 to 0xFF0B2340),
    SUNSET("sunset", "Sunset", 0xFFC2532F, 0xFFFFB59E, 0xFFFFDBD0, 0xFF702410, 0xFF3A0B00, 0xFFFFDBD0, 0xFFE27350 to 0xFFA03A24, 0xFF7A2E1C to 0xFF3A150C),
    ROSE("rose", "Rose", 0xFFAE426D, 0xFFFFB0CB, 0xFFFFD9E4, 0xFF6B1E3D, 0xFF3E0021, 0xFFFFD9E4, 0xFFCA578B to 0xFF7E2650, 0xFF6E2346 to 0xFF351021),
    VIOLET("violet", "Violet", 0xFF7A4BC2, 0xFFD3B8FF, 0xFFEBDDFF, 0xFF45247A, 0xFF2A0067, 0xFFEBDDFF, 0xFF8C5ED8 to 0xFF4D2A8C, 0xFF4A2A80 to 0xFF22123E),
    AMBER("amber", "Amber", 0xFF8F5B00, 0xFFFFC96B, 0xFFFFE2B5, 0xFF573600, 0xFF2D1A00, 0xFFFFE2B5, 0xFFD08A1E to 0xFF8A5200, 0xFF6B4300 to 0xFF2E1C00),
    GRAPHITE("graphite", "Graphite", 0xFF3D4450, 0xFFC6CCD6, 0xFFE1E4EA, 0xFF353B45, 0xFF151A22, 0xFFE1E4EA, 0xFF525B6B to 0xFF262B33, 0xFF2E333C to 0xFF15181D),
    FOREST("forest", "Forest", 0xFF2B7A67, 0xFF7FD3B9, 0xFFCDEDE2, 0xFF14503F, 0xFF0B2E25, 0xFFCDEDE2, 0xFF2F8A72 to 0xFF1D5E4E, 0xFF1C5446 to 0xFF10302A);

    companion object {
        val DEFAULT = DUSK
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

private fun lightScheme(a: Accent) = lightColorScheme(
    primary = Color(a.light),
    onPrimary = Color.White,
    primaryContainer = Color(a.containerLight),
    onPrimaryContainer = Color(a.onContainerLight),
    secondary = Color(a.light),
    onSecondary = Color.White,
    secondaryContainer = Color(a.containerLight),
    onSecondaryContainer = Color(a.onContainerLight),
    tertiary = Color(a.light),
    background = Color(0xFFF7F7F5),
    onBackground = Color(0xFF1A1B1E),
    surface = Color(0xFFF7F7F5),
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFE6E6E9),
    onSurfaceVariant = Color(0xFF5E6068),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0F2),
    surfaceContainerHighest = Color(0xFFE9E9EC),
    outline = Color(0xFFC3C4CA),
    outlineVariant = Color(0xFFE0E0E4),
    error = Color(0xFFC4453B),
)

private fun darkScheme(a: Accent) = darkColorScheme(
    primary = Color(a.dark),
    onPrimary = Color(a.onContainerLight),
    primaryContainer = Color(a.containerDark),
    onPrimaryContainer = Color(a.onContainerDark),
    secondary = Color(a.dark),
    onSecondary = Color(a.onContainerLight),
    secondaryContainer = Color(a.containerDark),
    onSecondaryContainer = Color(a.onContainerDark),
    tertiary = Color(a.dark),
    background = Color(0xFF0F1012),
    onBackground = Color(0xFFE4E4E8),
    surface = Color(0xFF0F1012),
    onSurface = Color(0xFFE4E4E8),
    surfaceVariant = Color(0xFF2B2D33),
    onSurfaceVariant = Color(0xFFAAADB6),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF16171A),
    surfaceContainer = Color(0xFF191A1E),
    surfaceContainerHigh = Color(0xFF212328),
    surfaceContainerHighest = Color(0xFF2B2D33),
    outline = Color(0xFF5B5E66),
    outlineVariant = Color(0xFF30323A),
    error = Color(0xFFF08A80),
)

private fun extras(a: Accent, dark: Boolean) = DaymarkColors(
    clean = if (dark) Color(0xFF4CC38F) else Color(0xFF3BA57B),
    relapse = if (dark) Color(0xFFF07C72) else Color(0xFFE0675C),
    untracked = if (dark) Color(0xFF2B2D33) else Color(0xFFDFDFE3),
    accentWarm = if (dark) Color(0xFFF2B48C) else Color(0xFFE39A62),
    heroGradientTop = Color(if (dark) a.heroDark.first else a.heroLight.first),
    heroGradientBottom = Color(if (dark) a.heroDark.second else a.heroLight.second),
    isDark = dark,
)

val LocalDaymarkColors = staticCompositionLocalOf { extras(Accent.DEFAULT, false) }

/** True when the user has turned animations off system-wide; components fall back to cross-fades. */
val LocalReducedMotion = staticCompositionLocalOf { false }

private val base = Typography()

/** Size-specific tracking: tight on large display text, neutral on body, slightly open on small labels. */
val DaymarkTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em, lineHeight = 1.0.em),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.025).em),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.015).em),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(letterSpacing = 0.01.em),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.03.em),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.04.em),
)

/** Giant hero counter style. */
val HeroNumber = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.04).em, lineHeight = 96.sp)

@Composable
fun DaymarkTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    accent: Accent = Accent.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkScheme(accent)
        else -> lightScheme(accent)
    }
    val reduced = remember {
        runCatching {
            SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalDaymarkColors provides extras(accent, dark),
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(colorScheme = scheme, typography = DaymarkTypography, content = content)
    }
}

object DaymarkTheme {
    val colors: DaymarkColors @Composable get() = LocalDaymarkColors.current
}
