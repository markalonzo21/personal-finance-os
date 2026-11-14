package com.mcalonzo.personalfinanceos.domain.model

/**
 * Reversible snapshot captured by write mutations so the 5-second undo window
 * can atomically delete the logged transaction and restore prior entity state.
 */
sealed interface UndoAction {
    val transactionId: Long
    val label: String

    data class ExpenseLogged(
        override val transactionId: Long,
        val cycleId: Long,
        val envelopeId: Long?,
        val amountCentavos: Long,
        override val label: String
    ) : UndoAction

    data class BillSettled(
        override val transactionId: Long,
        val billId: Long,
        val previousAmountCentavos: Long,
        val previousIsPaid: Boolean,
        val previousPaidAtEpochMs: Long?,
        override val label: String
    ) : UndoAction

    data class LoanSettled(
        override val transactionId: Long,
        val loanId: Long,
        val previousPaidInstallments: Int,
        val previousNextDueYearMonth: String,
        val previousLastPaidCycleYearMonth: String?,
        val previousIsActive: Boolean,
        override val label: String
    ) : UndoAction
}
