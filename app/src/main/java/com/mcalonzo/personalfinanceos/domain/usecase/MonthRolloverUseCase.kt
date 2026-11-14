package com.mcalonzo.personalfinanceos.domain.usecase

import androidx.room.withTransaction
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.entity.BillEntity
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.EnvelopeEntity
import java.time.YearMonth
import javax.inject.Inject

/**
 * Ensures a [BudgetCycleEntity] exists for the requested [YearMonth] by cloning
 * baseline routine envelope and active bill templates inside a single Room transaction.
 * Never backfills skipped intermediate months.
 */
class MonthRolloverUseCase @Inject constructor(
    private val db: AppDatabase
) {
    private val routineDao = db.routineDao()
    private val budgetDao = db.budgetDao()
    private val obligationDao = db.obligationDao()

    suspend operator fun invoke(targetMonth: YearMonth): Long {
        val yearMonthKey = targetMonth.toString()
        return db.withTransaction {
            budgetDao.getCycleByYearMonth(yearMonthKey)?.let { existing ->
                return@withTransaction existing.id
            }

            val routineTemplate = routineDao.getRoutineTemplateOnce()
            val envelopeTemplates = routineDao.getEnvelopeTemplatesOnce()
            val activeBillTemplates = routineDao.getActiveBillTemplatesOnce()

            val totalBudgetCentavos = routineTemplate?.totalMonthlyBudgetCentavos
                ?: envelopeTemplates.sumOf { it.allocatedLimitCentavos }

            val cycleId = budgetDao.insertCycle(
                BudgetCycleEntity(
                    yearMonth = yearMonthKey,
                    totalBudgetCentavos = totalBudgetCentavos,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )

            if (envelopeTemplates.isNotEmpty()) {
                budgetDao.insertEnvelopes(
                    envelopeTemplates.map { template ->
                        EnvelopeEntity(
                            cycleId = cycleId,
                            name = template.name,
                            iconEmoji = template.iconEmoji,
                            colorHex = template.colorHex,
                            allocatedLimitCentavos = template.allocatedLimitCentavos,
                            spentCentavos = 0L,
                            isGasEnvelope = template.isGasEnvelope
                        )
                    }
                )
            }

            if (activeBillTemplates.isNotEmpty()) {
                obligationDao.insertBills(
                    activeBillTemplates.map { template ->
                        BillEntity(
                            cycleId = cycleId,
                            templateGroupId = template.id.toString(),
                            name = template.name,
                            iconEmoji = template.iconEmoji,
                            amountCentavos = template.defaultAmountCentavos,
                            dueDayOfMonth = template.dueDayOfMonth,
                            isPaid = false,
                            paidAtEpochMs = null
                        )
                    }
                )
            }

            cycleId
        }
    }
}
