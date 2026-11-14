package com.mcalonzo.personalfinanceos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PersonalFinanceDarkColorScheme = darkColorScheme(
    primary = OLEDColors.accentSky,
    onPrimary = OLEDColors.bgBase,
    primaryContainer = OLEDColors.accentSkyDeep,
    onPrimaryContainer = OLEDColors.textPrimary,
    secondary = OLEDColors.accentEmerald,
    onSecondary = OLEDColors.bgBase,
    tertiary = OLEDColors.accentAmber,
    onTertiary = OLEDColors.bgBase,
    error = OLEDColors.accentRose,
    onError = OLEDColors.textPrimary,
    background = OLEDColors.bgBase,
    onBackground = OLEDColors.textPrimary,
    surface = OLEDColors.bgCard,
    onSurface = OLEDColors.textPrimary,
    surfaceVariant = OLEDColors.bgCardSubtle,
    onSurfaceVariant = OLEDColors.textSecondary,
    surfaceContainerLowest = OLEDColors.bgBase,
    surfaceContainerLow = OLEDColors.bgCard,
    surfaceContainer = OLEDColors.bgCardSubtle,
    surfaceContainerHigh = OLEDColors.bgCardElevated,
    outline = OLEDColors.borderSubtle,
    outlineVariant = OLEDColors.borderSubtle
)

@Composable
fun PersonalFinanceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PersonalFinanceDarkColorScheme,
        typography = FinanceTypography,
        content = content
    )
}
