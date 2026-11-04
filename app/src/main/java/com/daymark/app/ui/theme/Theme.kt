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

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2B7A67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE2),
    onPrimaryContainer = Color(0xFF0B2E25),
    secondary = Color(0xFFB8673A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D1),
    onSecondaryContainer = Color(0xFF3A1A07),
    tertiary = Color(0xFF5C6BC0),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF181C1B),
    surface = Color(0xFFF6F5F1),
    onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFE5E8E4),
    onSurfaceVariant = Color(0xFF5B635F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F1EE),
    surfaceContainerHighest = Color(0xFFE9EBE7),
    outline = Color(0xFFBFC6C2),
    outlineVariant = Color(0xFFDDE1DE),
    error = Color(0xFFC4453B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FD3B9),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF14503F),
    onPrimaryContainer = Color(0xFFCDEDE2),
    secondary = Color(0xFFF2B48C),
    onSecondary = Color(0xFF4A230B),
    secondaryContainer = Color(0xFF5E3417),
    onSecondaryContainer = Color(0xFFFFE3D1),
    tertiary = Color(0xFFA9B4FF),
    background = Color(0xFF0D1211),
    onBackground = Color(0xFFE1E5E2),
    surface = Color(0xFF0D1211),
    onSurface = Color(0xFFE1E5E2),
    surfaceVariant = Color(0xFF29302D),
    onSurfaceVariant = Color(0xFFA9B2AE),
    surfaceContainerLowest = Color(0xFF0A0E0D),
    surfaceContainerLow = Color(0xFF151B19),
    surfaceContainer = Color(0xFF171E1C),
    surfaceContainerHigh = Color(0xFF1F2725),
    surfaceContainerHighest = Color(0xFF29312F),
    outline = Color(0xFF59625E),
    outlineVariant = Color(0xFF2E3633),
    error = Color(0xFFF08A80),
)

private val LightExtra = DaymarkColors(
    clean = Color(0xFF3BA57B),
    relapse = Color(0xFFE0675C),
    untracked = Color(0xFFDCDDD8),
    accentWarm = Color(0xFFE9A06B),
    heroGradientTop = Color(0xFF2F8A72),
    heroGradientBottom = Color(0xFF1D5E4E),
    isDark = false,
)

private val DarkExtra = DaymarkColors(
    clean = Color(0xFF4CC38F),
    relapse = Color(0xFFF07C72),
    untracked = Color(0xFF2A3230),
    accentWarm = Color(0xFFF2B48C),
    heroGradientTop = Color(0xFF1C5446),
    heroGradientBottom = Color(0xFF10302A),
    isDark = true,
)

val LocalDaymarkColors = staticCompositionLocalOf { LightExtra }

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
        dark -> DarkScheme
        else -> LightScheme
    }
    val reduced = remember {
        runCatching {
            SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalDaymarkColors provides if (dark) DarkExtra else LightExtra,
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(colorScheme = scheme, typography = DaymarkTypography, content = content)
    }
}

object DaymarkTheme {
    val colors: DaymarkColors @Composable get() = LocalDaymarkColors.current
}
