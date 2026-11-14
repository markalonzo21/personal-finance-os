package com.mcalonzo.personalfinanceos.domain.calculator

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class CalculatorsTest {
    @Test
    fun `safe to spend reserves unpaid loans upfront and keeps balance unchanged when loan settles`() {
        val beforePay = BudgetCalculator.calculateSafeToSpend(5_000_000L, 2_300_000L, 835_000L)
        assertThat(beforePay).isEqualTo(1_865_000L) // ₱18,650.00
        // Loan ₱4,850 settled -> moves from committed loans to spent; Safe-to-Spend remains ₱18,650.00
        val afterPay = BudgetCalculator.calculateSafeToSpend(5_000_000L, 2_785_000L, 350_000L)
        assertThat(afterPay).isEqualTo(1_865_000L)

        // Overspending returns negative balance without clamping to zero
        val overspent = BudgetCalculator.calculateSafeToSpend(1_000_000L, 1_150_000L, 100_000L)
        assertThat(overspent).isEqualTo(-250_000L)
    }

    @Test
    fun `calculateDailyPace handles past, current inclusive, future cycles, and non-positive balance`() {
        val today = LocalDate.of(2026, 11, 14)

        // Past month returns 0
        assertThat(
            BudgetCalculator.calculateDailyPace(1_865_000L, today, YearMonth.of(2026, 10))
        ).isEqualTo(0L)

        // Non-positive Safe-to-Spend returns 0
        assertThat(
            BudgetCalculator.calculateDailyPace(0L, today, YearMonth.of(2026, 11))
        ).isEqualTo(0L)
        assertThat(
            BudgetCalculator.calculateDailyPace(-50_000L, today, YearMonth.of(2026, 11))
        ).isEqualTo(0L)

        // Current month divides by remaining inclusive days: 30 - 14 + 1 = 17
        assertThat(
            BudgetCalculator.calculateDailyPace(1_865_000L, today, YearMonth.of(2026, 11))
        ).isEqualTo(109_705L)

        // Final day of month divides by 1
        assertThat(
            BudgetCalculator.calculateDailyPace(250_000L, LocalDate.of(2026, 11, 30), YearMonth.of(2026, 11))
        ).isEqualTo(250_000L)

        // Future month divides by full length of target month (Dec = 31 days)
        assertThat(
            BudgetCalculator.calculateDailyPace(3_100_000L, today, YearMonth.of(2026, 12))
        ).isEqualTo(100_000L)
    }

    @Test
    fun `formatPeso formats positive, zero, negative, comma grouping, and half-up rounding`() {
        assertThat(BudgetCalculator.formatPeso(1_865_000L)).isEqualTo("₱18,650.00")
        assertThat(BudgetCalculator.formatPeso(1_865_000L, showDecimal = false)).isEqualTo("₱18,650")
        assertThat(BudgetCalculator.formatPeso(109_705L, showDecimal = false)).isEqualTo("₱1,097")
        assertThat(BudgetCalculator.formatPeso(109_750L, showDecimal = false)).isEqualTo("₱1,098")
        assertThat(BudgetCalculator.formatPeso(109_749L, showDecimal = false)).isEqualTo("₱1,097")
        assertThat(BudgetCalculator.formatPeso(0L)).isEqualTo("₱0.00")
        assertThat(BudgetCalculator.formatPeso(0L, showDecimal = false)).isEqualTo("₱0")
        assertThat(BudgetCalculator.formatPeso(-150L)).isEqualTo("-₱1.50")
        assertThat(BudgetCalculator.formatPeso(-123_450L)).isEqualTo("-₱1,234.50")
        assertThat(BudgetCalculator.formatPeso(-150L, showDecimal = false)).isEqualTo("-₱2")
        assertThat(BudgetCalculator.formatPeso(-49L, showDecimal = false)).isEqualTo("₱0")
    }

    @Test
    fun `dueDayOfMonth 31 clamps to end of month in February and November without throwing`() {
        assertThat(DueDateCalculator.resolveDueDate(YearMonth.of(2026, 2), 31)).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(DueDateCalculator.resolveDueDate(YearMonth.of(2028, 2), 31)).isEqualTo(LocalDate.of(2028, 2, 29))
        assertThat(DueDateCalculator.resolveDueDate(YearMonth.of(2026, 11), 31)).isEqualTo(LocalDate.of(2026, 11, 30))
        assertThat(DueDateCalculator.resolveDueDate(YearMonth.of(2026, 11), 0)).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(DueDateCalculator.resolveDueDate(YearMonth.of(2026, 11), -5)).isEqualTo(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `daysUntilDue computes signed day offset against clamped due date`() {
        val today = LocalDate.of(2026, 2, 25)
        assertThat(DueDateCalculator.daysUntilDue(YearMonth.of(2026, 2), 31, today)).isEqualTo(3L)
        assertThat(DueDateCalculator.daysUntilDue(YearMonth.of(2026, 2), 25, today)).isEqualTo(0L)
        assertThat(DueDateCalculator.daysUntilDue(YearMonth.of(2026, 2), 20, today)).isEqualTo(-5L)
    }

    @Test
    fun `notification triggers only on configured lead days or overdue`() {
        assertThat(DueDateCalculator.shouldNotify(7L, true, false, false, false, false)).isTrue()
        assertThat(DueDateCalculator.shouldNotify(3L, true, true, true, true, true)).isTrue()
        assertThat(DueDateCalculator.shouldNotify(1L, false, false, true, false, false)).isTrue()
        assertThat(DueDateCalculator.shouldNotify(0L, false, false, false, true, false)).isTrue()
        assertThat(DueDateCalculator.shouldNotify(5L, true, true, true, true, true)).isFalse()
        assertThat(DueDateCalculator.shouldNotify(-2L, false, false, false, false, true)).isTrue()
        assertThat(DueDateCalculator.shouldNotify(-2L, true, true, true, true, false)).isFalse()
    }

    @Test
    fun `honda click 160 full tank and weekly price diff calculate accurately`() {
        assertThat(MotoCalculator.calculateFullTankCentavos(7840L)).isEqualTo(43120L) // default 5.5L -> ₱431.20
        assertThat(MotoCalculator.calculateFullTankCentavos(7840L, 5.5)).isEqualTo(43120L) // ₱431.20
        assertThat(MotoCalculator.calculateFullTankCentavos(7845L, 5.5)).isEqualTo(43148L) // half-up
        assertThat(MotoCalculator.calculateFullTankCentavos(100L, 1.15)).isEqualTo(115L) // avoids Double drift
        assertThat(MotoCalculator.calculateFullTankCentavos(0L, 5.5)).isEqualTo(0L)
        assertThat(MotoCalculator.calculateFullTankCentavos(7840L, Double.NaN)).isEqualTo(0L)
        assertThat(MotoCalculator.calculatePriceDiff(7840L, 7795L)).isEqualTo(45L) // +₱0.45
        assertThat(MotoCalculator.calculatePriceDiff(7690L, 7840L)).isEqualTo(-150L) // -₱1.50
        assertThat(MotoCalculator.calculatePriceDiff(7840L, null)).isEqualTo(0L)
    }

    @Test
    fun `calculatePmsRemainingKm returns remaining distance and negative when overdue`() {
        assertThat(MotoCalculator.calculatePmsRemainingKm(8450, 1500, 7500)).isEqualTo(550)
        assertThat(MotoCalculator.calculatePmsRemainingKm(9000, 1500, 7500)).isEqualTo(0)
        assertThat(MotoCalculator.calculatePmsRemainingKm(9250, 1500, 7500)).isEqualTo(-250)
    }
}
