package com.mcalonzo.personalfinanceos.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Pure domain calculations for Philippine Peso Safe-to-Spend balances,
 * daily pacing, and deterministic peso formatting.
 */
object BudgetCalculator {
    private val philippineLocale: Locale = Locale.Builder()
        .setLanguage("en")
        .setRegion("PH")
        .build()

    /**
     * Computes remaining discretionary budget after deducting variable/bill spend
     * and reserving unpaid loan commitments upfront.
     */
    fun calculateSafeToSpend(
        totalBudgetCentavos: Long,
        variableAndBillSpentCentavos: Long,
        committedUnpaidLoansCentavos: Long
    ): Long = totalBudgetCentavos - variableAndBillSpentCentavos - committedUnpaidLoansCentavos

    /**
     * Computes daily spending pace in centavos. Returns 0 for past months or
     * non-positive balances, divides by inclusive remaining days for the current
     * month, and divides by full month length for future months.
     */
    fun calculateDailyPace(
        safeToSpendCentavos: Long,
        today: LocalDate,
        cycleMonth: YearMonth
    ): Long {
        if (safeToSpendCentavos <= 0L) return 0L
        val currentMonth = YearMonth.from(today)
        if (cycleMonth.isBefore(currentMonth)) return 0L

        val divisorDays = if (cycleMonth == currentMonth) {
            (cycleMonth.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
        } else {
            cycleMonth.lengthOfMonth()
        }
        return safeToSpendCentavos / divisorDays.toLong()
    }

    /**
     * Formats centavos strictly in Philippine Peso (`₱`) using comma grouping,
     * `"-₱..."` negative prefix, 2 decimal places when [showDecimal] is true,
     * and half-up whole peso rounding when [showDecimal] is false.
     */
    fun formatPeso(centavos: Long, showDecimal: Boolean = true): String {
        val rawPesos = BigDecimal.valueOf(centavos).movePointLeft(2)
        val scale = if (showDecimal) 2 else 0
        val scaledPesos = rawPesos.setScale(scale, RoundingMode.HALF_UP)
        val isNegative = scaledPesos.signum() < 0
        val pattern = if (showDecimal) "#,##0.00" else "#,##0"
        val symbols = DecimalFormatSymbols.getInstance(philippineLocale)
        val formattedMagnitude = DecimalFormat(pattern, symbols).format(scaledPesos.abs())
        return if (isNegative) "-₱$formattedMagnitude" else "₱$formattedMagnitude"
    }
}
