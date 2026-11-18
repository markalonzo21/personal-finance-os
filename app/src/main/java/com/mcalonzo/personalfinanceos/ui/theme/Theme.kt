package com.mcalonzo.personalfinanceos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val PersonalFinanceDarkColorScheme = darkColorScheme(
    primary = DarkOLEDColors.accentSky,
    onPrimary = DarkOLEDColors.bgBase,
    primaryContainer = DarkOLEDColors.accentSkyDeep,
    onPrimaryContainer = DarkOLEDColors.textPrimary,
    secondary = DarkOLEDColors.accentEmerald,
    onSecondary = DarkOLEDColors.bgBase,
    tertiary = DarkOLEDColors.accentAmber,
    onTertiary = DarkOLEDColors.bgBase,
    error = DarkOLEDColors.accentRose,
    onError = DarkOLEDColors.textPrimary,
    background = DarkOLEDColors.bgBase,
    onBackground = DarkOLEDColors.textPrimary,
    surface = DarkOLEDColors.bgCard,
    onSurface = DarkOLEDColors.textPrimary,
    surfaceVariant = DarkOLEDColors.bgCardSubtle,
    onSurfaceVariant = DarkOLEDColors.textSecondary,
    surfaceContainerLowest = DarkOLEDColors.bgBase,
    surfaceContainerLow = DarkOLEDColors.bgCard,
    surfaceContainer = DarkOLEDColors.bgCardSubtle,
    surfaceContainerHigh = DarkOLEDColors.bgCardElevated,
    outline = DarkOLEDColors.borderSubtle,
    outlineVariant = DarkOLEDColors.borderSubtle
)

private val PersonalFinanceLightColorScheme = lightColorScheme(
    primary = LightWarmPaperColors.accentSky,
    onPrimary = Color.White,
    primaryContainer = LightWarmPaperColors.accentSkyDeep,
    onPrimaryContainer = Color.White,
    secondary = LightWarmPaperColors.accentEmerald,
    onSecondary = Color.White,
    tertiary = LightWarmPaperColors.accentAmber,
    onTertiary = Color.White,
    error = LightWarmPaperColors.accentRose,
    onError = Color.White,
    background = LightWarmPaperColors.bgBase,
    onBackground = LightWarmPaperColors.textPrimary,
    surface = LightWarmPaperColors.bgCard,
    onSurface = LightWarmPaperColors.textPrimary,
    surfaceVariant = LightWarmPaperColors.bgCardSubtle,
    onSurfaceVariant = LightWarmPaperColors.textSecondary,
    surfaceContainerLowest = LightWarmPaperColors.bgCard,
    surfaceContainerLow = LightWarmPaperColors.bgCardElevated,
    surfaceContainer = LightWarmPaperColors.bgCardSubtle,
    surfaceContainerHigh = LightWarmPaperColors.borderSubtle,
    outline = LightWarmPaperColors.borderSubtle,
    outlineVariant = LightWarmPaperColors.borderSubtle
)

val LocalFinanceColors = staticCompositionLocalOf { DarkOLEDColors }

object FinanceTheme {
    val colors: FinanceColors
        @Composable
        get() = LocalFinanceColors.current
}

@Composable
fun PersonalFinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val financeColors = if (darkTheme) DarkOLEDColors else LightWarmPaperColors
    val materialColorScheme = if (darkTheme) {
        PersonalFinanceDarkColorScheme
    } else {
        PersonalFinanceLightColorScheme
    }

    CompositionLocalProvider(LocalFinanceColors provides financeColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = FinanceTypography,
            content = content
        )
    }
}
