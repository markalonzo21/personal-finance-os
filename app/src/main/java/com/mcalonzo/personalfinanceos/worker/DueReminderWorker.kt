package com.mcalonzo.personalfinanceos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.entity.BillEntity
import com.mcalonzo.personalfinanceos.data.local.entity.LoanEntity
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.domain.calculator.DueDateCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

/**
 * Evaluates unpaid cycle bills and active loans for due/overdue reminders at 09:00 AM
 * Asia/Manila or handles single-item 1-hour snooze re-notifications via [INPUT_ITEM_KEY].
 */
@HiltWorker
class DueReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    constructor(appContext: Context, workerParams: WorkerParameters) : this(
        appContext = appContext,
        workerParams = workerParams,
        database = testDatabaseOverride ?: EntryPointAccessors.fromApplication(
            appContext.applicationContext,
            WorkerEntryPoint::class.java
        ).appDatabase()
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun appDatabase(): AppDatabase
    }

    override suspend fun doWork(): Result {
        if (!NotificationHelper.isGlobalPushEnabled(applicationContext)) {
            return Result.success()
        }

        return try {
            val today = testTodayProvider?.invoke() ?: LocalDate.now(MANILA_ZONE)
            val targetItemKey = inputData.getString(INPUT_ITEM_KEY)?.trim()?.takeIf { it.isNotEmpty() }

            if (targetItemKey != null) {
                handleSnoozedItem(targetItemKey, today)
            } else {
                evaluateAllObligations(today)
            }
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun handleSnoozedItem(itemKey: String, today: LocalDate) {
        val obligationDao = database.obligationDao()
        val budgetDao = database.budgetDao()

        when {
            itemKey.startsWith("${NotificationActionReceiver.ENTITY_TYPE_BILL}_") -> {
                val billId = itemKey
                    .removePrefix("${NotificationActionReceiver.ENTITY_TYPE_BILL}_")
                    .toLongOrNull() ?: return
                val bill = obligationDao.getBillById(billId) ?: return
                if (bill.isPaid) return

                val cycleMonth = budgetDao.getCycleById(bill.cycleId)
                    ?.let { YearMonth.parse(it.yearMonth) }
                    ?: YearMonth.from(today)
                val daysUntilDue = DueDateCalculator.daysUntilDue(
                    yearMonth = cycleMonth,
                    dueDayOfMonth = bill.dueDayOfMonth,
                    today = today
                )
                postBillNotification(bill, daysUntilDue)
            }

            itemKey.startsWith("${NotificationActionReceiver.ENTITY_TYPE_LOAN}_") -> {
                val loanId = itemKey
                    .removePrefix("${NotificationActionReceiver.ENTITY_TYPE_LOAN}_")
                    .toLongOrNull() ?: return
                val loan = obligationDao.getLoanById(loanId) ?: return
                if (!loan.isActive || loan.paidInstallments >= loan.totalInstallments) return

                val daysUntilDue = DueDateCalculator.daysUntilDue(
                    yearMonth = YearMonth.parse(loan.nextDueYearMonth),
                    dueDayOfMonth = loan.dueDayOfMonth,
                    today = today
                )
                val activeCycleId = budgetDao.getCycleByYearMonth(YearMonth.from(today).toString())?.id
                postLoanNotification(loan, daysUntilDue, activeCycleId)
            }
        }
    }

    private suspend fun evaluateAllObligations(today: LocalDate) {
        val currentMonth = YearMonth.from(today)
        val budgetDao = database.budgetDao()
        val obligationDao = database.obligationDao()

        val activeCycle = budgetDao.getCycleByYearMonth(currentMonth.toString())
        if (activeCycle != null) {
            val unpaidBills = obligationDao.getUnpaidBillsForCycleOnce(activeCycle.id)
            unpaidBills.forEach { bill ->
                val daysUntilDue = DueDateCalculator.daysUntilDue(
                    yearMonth = currentMonth,
                    dueDayOfMonth = bill.dueDayOfMonth,
                    today = today
                )
                val shouldAlert = DueDateCalculator.shouldNotify(
                    daysUntilDue = daysUntilDue,
                    notify7d = bill.notify7d,
                    notify3d = bill.notify3d,
                    notify1d = bill.notify1d,
                    notifyOnDue = bill.notifyOnDue,
                    notifyDailyOverdue = bill.notifyDailyOverdue
                )
                if (shouldAlert) {
                    postBillNotification(bill, daysUntilDue)
                }
            }
        }

        val activeLoans = obligationDao.getActiveLoansOnce()
            .filter { it.isActive && it.paidInstallments < it.totalInstallments }
        activeLoans.forEach { loan ->
            val dueMonth = YearMonth.parse(loan.nextDueYearMonth)
            val daysUntilDue = DueDateCalculator.daysUntilDue(
                yearMonth = dueMonth,
                dueDayOfMonth = loan.dueDayOfMonth,
                today = today
            )
            val shouldAlert = DueDateCalculator.shouldNotify(
                daysUntilDue = daysUntilDue,
                notify7d = loan.notify7d,
                notify3d = loan.notify3d,
                notify1d = loan.notify1d,
                notifyOnDue = loan.notifyOnDue,
                notifyDailyOverdue = loan.notifyDailyOverdue
            )
            if (shouldAlert) {
                postLoanNotification(loan, daysUntilDue, activeCycle?.id)
            }
        }
    }

    private fun postBillNotification(bill: BillEntity, daysUntilDue: Long) {
        val itemKey = "${NotificationActionReceiver.ENTITY_TYPE_BILL}_${bill.id}"
        NotificationHelper.showDueAlert(
            context = applicationContext,
            itemKey = itemKey,
            notificationId = NotificationHelper.stableNotificationId(itemKey),
            title = "${bill.iconEmoji} ${bill.name}".trim(),
            body = formatDueStatusBody(daysUntilDue, bill.amountCentavos),
            entityType = NotificationActionReceiver.ENTITY_TYPE_BILL,
            entityId = bill.id,
            amountCentavos = bill.amountCentavos,
            cycleId = bill.cycleId
        )
    }

    private fun postLoanNotification(loan: LoanEntity, daysUntilDue: Long, cycleId: Long?) {
        val itemKey = "${NotificationActionReceiver.ENTITY_TYPE_LOAN}_${loan.id}"
        NotificationHelper.showDueAlert(
            context = applicationContext,
            itemKey = itemKey,
            notificationId = NotificationHelper.stableNotificationId(itemKey),
            title = "${loan.iconEmoji} ${loan.name}".trim(),
            body = formatDueStatusBody(daysUntilDue, loan.monthlyAmortizationCentavos),
            entityType = NotificationActionReceiver.ENTITY_TYPE_LOAN,
            entityId = loan.id,
            amountCentavos = loan.monthlyAmortizationCentavos,
            cycleId = cycleId
        )
    }

    private fun formatDueStatusBody(daysUntilDue: Long, amountCentavos: Long): String {
        val formattedPeso = BudgetCalculator.formatPeso(amountCentavos)
        val statusPrefix = when {
            daysUntilDue < 0L -> {
                val overdueDays = abs(daysUntilDue)
                val unit = if (overdueDays == 1L) "day" else "days"
                "Overdue by $overdueDays $unit"
            }
            daysUntilDue == 0L -> "Due Today"
            else -> {
                val unit = if (daysUntilDue == 1L) "day" else "days"
                "Due in $daysUntilDue $unit"
            }
        }
        return "$statusPrefix • $formattedPeso"
    }

    companion object {
        const val UNIQUE_DAILY_WORK_NAME = "daily_due_date_reminders"
        const val SNOOZE_WORK_PREFIX = "due_reminder_snooze_"
        const val INPUT_ITEM_KEY = "input_item_key"

        private val MANILA_ZONE: ZoneId = ZoneId.of("Asia/Manila")

        @Volatile
        internal var testDatabaseOverride: AppDatabase? = null

        @Volatile
        internal var testTodayProvider: (() -> LocalDate)? = null
    }
}
