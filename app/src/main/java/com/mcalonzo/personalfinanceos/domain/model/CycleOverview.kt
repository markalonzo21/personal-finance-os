package com.mcalonzo.personalfinanceos.domain.model

import com.mcalonzo.personalfinanceos.data.local.entity.BillEntity
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.EnvelopeEntity
import com.mcalonzo.personalfinanceos.data.local.entity.LoanEntity
import com.mcalonzo.personalfinanceos.data.local.entity.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * A cycle bill paired with its resolved calendar due date and overdue state.
 */
data class BillWithStatus(
    val bill: BillEntity,
    val dueDate: LocalDate,
    val daysUntilDue: Long,
    val isOverdue: Boolean,
    val overdueDays: Int
)

/**
 * An installment loan evaluated against a target budget cycle and reference date.
 */
data class LoanWithStatus(
    val loan: LoanEntity,
    val dueDate: LocalDate,
    val daysUntilDue: Long,
    val isPaidForCycle: Boolean,
    val isOverdue: Boolean,
    val overdueDays: Int
)

/**
 * Aggregated reactive snapshot for a monthly [BudgetCycleEntity] including
 * Safe-to-Spend math, daily pacing, envelopes, bills, loans, and activity.
 */
data class CycleOverview(
    val cycle: BudgetCycleEntity,
    val yearMonth: YearMonth,
    val totalBudgetCentavos: Long,
    val spentSoFarCentavos: Long,
    val committedUnpaidLoansCentavos: Long,
    val unpaidBillsCentavos: Long,
    val safeToSpendCentavos: Long,
    val dailyPaceCentavos: Long,
    val remainingDaysInMonth: Int,
    val envelopes: List<EnvelopeEntity>,
    val bills: List<BillWithStatus>,
    val overdueBillsCount: Int,
    val loans: List<LoanWithStatus>,
    val overdueLoansCount: Int,
    val transactions: List<TransactionEntity>
)
