package com.mcalonzo.personalfinanceos.ui.components

import androidx.compose.ui.graphics.Color
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Shared formatting and parsing helpers for Philippine Peso input fields and OLED hex colors.
 */
object CurrencyFormatters {

    val ENVELOPE_COLOR_PALETTE: List<Pair<String, String>> = listOf(
        "#38BDF8" to "Sky",
        "#10B981" to "Emerald",
        "#F59E0B" to "Amber",
        "#A855F7" to "Purple",
        "#F43F5E" to "Rose"
    )

    /**
     * Parses a user-entered peso amount (allowing optional `₱`, commas, spaces, and up to
     * 2 decimal places) into exact `Long` centavos via [BigDecimal], returning `null` if invalid.
     */
    fun parsePesoToCentavos(rawInput: String): Long? {
        val cleaned = rawInput
            .replace("₱", "")
            .replace(",", "")
            .trim()
        if (cleaned.isEmpty()) return null
        return try {
            val decimal = BigDecimal(cleaned)
            if (decimal.signum() < 0) return null
            decimal.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Formats centavos as plain numeric string for editable text fields (`1500` or `78.40`).
     */
    fun formatCentavosForInput(centavos: Long): String {
        if (centavos <= 0L) return ""
        val whole = centavos / 100L
        val remainder = centavos % 100L
        return if (remainder == 0L) {
            whole.toString()
        } else {
            "$whole.${remainder.toString().padStart(2, '0')}"
        }
    }

    /**
     * Sanitizes numeric input to digits and at most one decimal point with 2 decimal digits.
     */
    fun sanitizeDecimalInput(input: String): String {
        val filtered = StringBuilder()
        var hasDot = false
        var decimalCount = 0
        for (ch in input) {
            when {
                ch.isDigit() -> {
                    if (!hasDot || decimalCount < 2) {
                        filtered.append(ch)
                        if (hasDot) decimalCount++
                    }
                }
                ch == '.' && !hasDot -> {
                    hasDot = true
                    if (filtered.isEmpty()) filtered.append('0')
                    filtered.append('.')
                }
            }
        }
        return filtered.toString()
    }

    /**
     * Parses `#RRGGBB` or `#AARRGGBB` hex strings safely onto Compose [Color].
     */
    fun parseColorHex(hex: String, fallback: Color = OLEDColors.accentSky): Color {
        return runCatching {
            Color(android.graphics.Color.parseColor(hex.trim()))
        }.getOrDefault(fallback)
    }
}
