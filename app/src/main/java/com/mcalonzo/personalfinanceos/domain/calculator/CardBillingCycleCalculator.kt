package com.mcalonzo.personalfinanceos.domain.calculator

import java.time.LocalDate
import java.time.YearMonth

/**
 * Resolves the target budget [YearMonth] for a card transaction based on its billing cycle cut-off day.
 */
object CardBillingCycleCalculator {
    /**
     * Returns the month following [transactionDate] when its day of month is strictly after
     * [billingCycleDay] (clamped to the month's length), the same month otherwise, or [fallbackMonth]
     * if either [transactionDate] or [billingCycleDay] is null.
     */
    fun resolveTargetMonth(
        transactionDate: LocalDate?,
        billingCycleDay: Int?,
        fallbackMonth: YearMonth
    ): YearMonth {
        if (transactionDate == null || billingCycleDay == null) {
            return fallbackMonth
        }
        val clampedCycleDay = billingCycleDay.coerceIn(1, transactionDate.lengthOfMonth())
        val baseMonth = YearMonth.from(transactionDate)
        return if (transactionDate.dayOfMonth > clampedCycleDay) {
            baseMonth.plusMonths(1)
        } else {
            baseMonth
        }
    }
}
