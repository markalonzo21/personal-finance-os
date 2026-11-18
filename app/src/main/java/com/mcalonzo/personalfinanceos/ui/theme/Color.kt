package com.mcalonzo.personalfinanceos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class FinanceColors(
    val bgBase: Color,
    val bgCard: Color,
    val bgCardSubtle: Color,
    val bgCardElevated: Color,
    val borderSubtle: Color,
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val progressTrack: Color,
    val fabGradientEnd: Color,
    val accentSky: Color,
    val accentSkyDeep: Color,
    val accentEmerald: Color,
    val accentAmber: Color,
    val accentRose: Color,
    val accentPurple: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color
)

/**
 * Dark OLED color palette tokens matching Spec §3.
 */
val DarkOLEDColors = FinanceColors(
    bgBase = Color(0xFF06090E),
    bgCard = Color(0xFF0D1522),
    bgCardSubtle = Color(0xFF121D2F),
    bgCardElevated = Color(0xFF18263E),
    borderSubtle = Color(0x14FFFFFF),
    heroGradientStart = Color(0xFF152238),
    heroGradientEnd = Color(0xFF0A111E),
    progressTrack = Color(0xFF1E293B),
    fabGradientEnd = Color(0xFF0EA5E9),
    accentSky = Color(0xFF38BDF8),
    accentSkyDeep = Color(0xFF0284C7),
    accentEmerald = Color(0xFF10B981),
    accentAmber = Color(0xFFF59E0B),
    accentRose = Color(0xFFF43F5E),
    accentPurple = Color(0xFFA855F7),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B)
)

/**
 * Warm Paper light color palette tokens with accessible contrast on light surfaces.
 */
val LightWarmPaperColors = FinanceColors(
    bgBase = Color(0xFFF7F6F2),
    bgCard = Color(0xFFFFFFFF),
    bgCardSubtle = Color(0xFFF0EFEA),
    bgCardElevated = Color(0xFFECEAE4),
    borderSubtle = Color(0xFFE7E5DF),
    heroGradientStart = Color(0xFFFAF9F6),
    heroGradientEnd = Color(0xFFF0EFEA),
    progressTrack = Color(0xFFE7E5DF),
    fabGradientEnd = Color(0xFF0284C7),
    accentSky = Color(0xFF0369A1),
    accentSkyDeep = Color(0xFF0284C7),
    accentEmerald = Color(0xFF059669),
    accentAmber = Color(0xFFD97706),
    accentRose = Color(0xFFE11D48),
    accentPurple = Color(0xFF9333EA),
    textPrimary = Color(0xFF1C1917),
    textSecondary = Color(0xFF57534E),
    textTertiary = Color(0xFF78716C)
)
