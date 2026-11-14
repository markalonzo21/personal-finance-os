package com.mcalonzo.personalfinanceos.domain.calculator

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure domain calculations for bill and loan due-date resolution with short-month
 * clamping and configurable reminder cadence evaluation.
 */
object DueDateCalculator {
    /**
     * Resolves a due day into [yearMonth], clamping out-of-range days into
     * `1..yearMonth.lengthOfMonth()` so day 31 safely resolves to Feb 28/29 or Apr/Nov 30.
     */
    fun resolveDueDate(yearMonth: YearMonth, dueDayOfMonth: Int): LocalDate {
        val clampedDay = dueDayOfMonth.coerceIn(1, yearMonth.lengthOfMonth())
        return yearMonth.atDay(clampedDay)
    }

    /**
     * Returns signed days from [today] until the clamped due date in [yearMonth]
     * (negative when overdue).
     */
    fun daysUntilDue(yearMonth: YearMonth, dueDayOfMonth: Int, today: LocalDate): Long {
        val dueDate = resolveDueDate(yearMonth, dueDayOfMonth)
        return ChronoUnit.DAYS.between(today, dueDate)
    }

    /**
     * Determines whether an alert should fire for the given [daysUntilDue] offset
     * and enabled notification cadence flags.
     */
    fun shouldNotify(
        daysUntilDue: Long,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ): Boolean = when {
        daysUntilDue == 7L -> notify7d
        daysUntilDue == 3L -> notify3d
        daysUntilDue == 1L -> notify1d
        daysUntilDue == 0L -> notifyOnDue
        daysUntilDue < 0L -> notifyDailyOverdue
        else -> false
    }
}
