package com.mcalonzo.personalfinanceos.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure domain calculations for Honda Click 160 full-tank costing,
 * weekly fuel price diff tracking, and PMS maintenance intervals.
 */
object MotoCalculator {
    /**
     * Calculates full-tank cost in centavos using exact decimal multiplication
     * (`BigDecimal`) to prevent binary floating-point rounding drift.
     */
    fun calculateFullTankCentavos(
        pricePerLiterCentavos: Long,
        tankLiters: Double = 5.5
    ): Long {
        if (pricePerLiterCentavos <= 0L || !tankLiters.isFinite() || tankLiters <= 0.0) {
            return 0L
        }
        return BigDecimal.valueOf(pricePerLiterCentavos)
            .multiply(BigDecimal.valueOf(tankLiters))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /**
     * Computes remaining kilometers before the next PMS service (`negative` when overdue).
     */
    fun calculatePmsRemainingKm(
        currentOdometerKm: Int,
        intervalKm: Int,
        lastServiceOdometerKm: Int
    ): Int = intervalKm - (currentOdometerKm - lastServiceOdometerKm)

    /**
     * Computes fuel price change in centavos, returning `0L` when no previous price exists.
     */
    fun calculatePriceDiff(
        newPriceCentavos: Long,
        previousPriceCentavos: Long?
    ): Long = if (previousPriceCentavos == null) 0L else newPriceCentavos - previousPriceCentavos
}
