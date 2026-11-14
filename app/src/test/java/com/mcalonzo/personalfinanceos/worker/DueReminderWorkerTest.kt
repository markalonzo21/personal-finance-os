package com.mcalonzo.personalfinanceos.worker

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.SeedDatabaseCallback
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.local.entity.LoanEntity
import com.mcalonzo.personalfinanceos.data.repository.BudgetRepository
import com.mcalonzo.personalfinanceos.domain.usecase.MonthRolloverUseCase
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DueReminderWorkerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var rolloverUseCase: MonthRolloverUseCase
    private lateinit var repo: BudgetRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationHelper.setGlobalPushEnabled(context, true)
        context.getSystemService(NotificationManager::class.java).cancelAll()

        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedDatabaseCallback({ db }) { YearMonth.of(2026, 11) })
            .setQueryExecutor(SynchronousExecutor())
            .setTransactionExecutor(SynchronousExecutor())
            .allowMainThreadQueries()
            .build()
        rolloverUseCase = MonthRolloverUseCase(db)
        repo = BudgetRepository(db, rolloverUseCase)

        DueReminderWorker.testDatabaseOverride = db
        DueReminderWorker.testTodayProvider = { LocalDate.of(2026, 11, 14) }
        NotificationActionReceiver.testRepositoryOverride = repo
        NotificationActionReceiver.testTodayProvider = { LocalDate.of(2026, 11, 14) }
    }

    @After
    fun tearDown() {
        DueReminderWorker.testDatabaseOverride = null
        DueReminderWorker.testTodayProvider = null
        NotificationActionReceiver.testRepositoryOverride = null
        NotificationActionReceiver.testDispatcherOverride = null
        NotificationActionReceiver.testTodayProvider = null
        NotificationHelper.setGlobalPushEnabled(context, true)
        context.getSystemService(NotificationManager::class.java).cancelAll()
        WorkManagerTestInitHelper.closeWorkDatabase()
        db.close()
    }

    @Test
    fun `broadcast ACTION_PAY_NOW settles due bill, logs transaction, and cancels notification`() = runTest {
        NotificationActionReceiver.testDispatcherOverride = StandardTestDispatcher(testScheduler)
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val notificationId = 1001
        val billId = repo.insertUnpaidTestBill(
            cycleId = cycleId,
            name = "Converge FiberX",
            amountCentavos = 150_000L,
            dueDay = 11
        )
        NotificationHelper.showDueAlert(
            context = context,
            itemKey = "bill_$billId",
            notificationId = notificationId,
            title = "Bill Due",
            body = "₱1,500.00",
            entityType = "bill",
            entityId = billId,
            amountCentavos = 150_000L
        )

        val nm = context.getSystemService(NotificationManager::class.java)
        val posted = nm.activeNotifications.first { it.id == notificationId }
        assertThat(posted.notification.actions.map { it.title.toString() })
            .containsExactly("Pay ₱1,500.00", "Remind in 1h")
            .inOrder()

        val payIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAY_NOW
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_TYPE, "bill")
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, billId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_ITEM_KEY, "bill_$billId")
        }
        NotificationActionReceiver().onReceive(context, payIntent)
        advanceUntilIdle()

        val bill = db.obligationDao().getBillById(billId)!!
        assertThat(bill.isPaid).isTrue()
        assertThat(db.budgetDao().getTransactionsForCycleOnce(cycleId)).hasSize(1)
        assertThat(nm.activeNotifications.none { it.id == notificationId }).isTrue()
    }

    @Test
    fun `broadcast ACTION_PAY_NOW settles overdue loan and cancels notification cleanly even if already paid`() = runTest {
        NotificationActionReceiver.testDispatcherOverride = StandardTestDispatcher(testScheduler)
        val loanId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Cross-Month Loan",
                iconEmoji = "🏦",
                monthlyAmortizationCentavos = 250_000L,
                totalInstallments = 6,
                paidInstallments = 1,
                dueDayOfMonth = 10,
                nextDueYearMonth = "2026-10",
                isActive = true
            )
        )
        val notificationId = NotificationHelper.stableNotificationId("loan_$loanId")
        NotificationHelper.showDueAlert(
            context = context,
            itemKey = "loan_$loanId",
            notificationId = notificationId,
            title = "Cross-Month Loan",
            body = "Overdue by 35 days • ₱2,500.00",
            entityType = "loan",
            entityId = loanId,
            amountCentavos = 250_000L
        )

        val payIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAY_NOW
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_TYPE, "loan")
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, loanId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_ITEM_KEY, "loan_$loanId")
        }
        NotificationActionReceiver().onReceive(context, payIntent)
        advanceUntilIdle()

        val settled = db.obligationDao().getLoanById(loanId)!!
        assertThat(settled.paidInstallments).isEqualTo(2)
        assertThat(settled.nextDueYearMonth).isEqualTo("2026-11")
        assertThat(settled.lastPaidCycleYearMonth).isEqualTo("2026-11")
        val activeCycle = db.budgetDao().getCycleByYearMonth("2026-11")!!
        assertThat(db.budgetDao().getTransactionsForCycleOnce(activeCycle.id))
            .hasSize(1)

        val nm = context.getSystemService(NotificationManager::class.java)
        assertThat(nm.activeNotifications.none { it.id == notificationId }).isTrue()

        // Duplicate tap after bill/loan already settled does not crash and cancels notification.
        val billId = repo.insertUnpaidTestBill(
            cycleId = activeCycle.id,
            name = "Already Paid Bill",
            amountCentavos = 50_000L,
            dueDay = 14
        )
        repo.settleBill(billId)
        val dupNotifId = NotificationHelper.stableNotificationId("bill_$billId")
        NotificationHelper.showDueAlert(
            context = context,
            itemKey = "bill_$billId",
            notificationId = dupNotifId,
            title = "Already Paid Bill",
            body = "Due Today • ₱500.00",
            entityType = "bill",
            entityId = billId,
            amountCentavos = 50_000L
        )
        val duplicatePayIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAY_NOW
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_TYPE, "bill")
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, billId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, dupNotifId)
        }
        NotificationActionReceiver().onReceive(context, duplicatePayIntent)
        advanceUntilIdle()
        assertThat(nm.activeNotifications.none { it.id == dupNotifId }).isTrue()
    }

    @Test
    fun `evaluates unpaid bills and cross-month overdue loans for due notifications`() = runTest {
        val cycleId = db.budgetDao().insertCycle(
            BudgetCycleEntity(
                yearMonth = "2026-11",
                totalBudgetCentavos = 5_000_000L,
                createdAtEpochMs = 1_000L
            )
        )
        // 1 overdue unpaid bill (dueDay = 11, today = Nov 14 -> overdue by 3 days)
        val overdueBillId = repo.insertUnpaidTestBill(
            cycleId = cycleId,
            name = "Converge FiberX",
            amountCentavos = 150_000L,
            dueDay = 11
        )
        // Paid bill should not alert
        val paidBillId = repo.insertUnpaidTestBill(
            cycleId = cycleId,
            name = "Paid Internet",
            amountCentavos = 100_000L,
            dueDay = 11
        )
        repo.settleBill(paidBillId)

        // Completed/inactive loan should not alert
        db.obligationDao().insertLoan(
            LoanEntity(
                name = "Completed Loan",
                iconEmoji = "🏦",
                monthlyAmortizationCentavos = 120_000L,
                totalInstallments = 12,
                paidInstallments = 12,
                dueDayOfMonth = 10,
                nextDueYearMonth = "2026-10",
                isActive = false
            )
        )
        // Cross-month overdue active loan (due Oct 20, today Nov 14 -> overdue by 25 days)
        val overdueLoanId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Overdue Scooter Loan",
                iconEmoji = "🛵",
                monthlyAmortizationCentavos = 485_000L,
                totalInstallments = 24,
                paidInstallments = 10,
                dueDayOfMonth = 20,
                nextDueYearMonth = "2026-10",
                isActive = true
            )
        )

        val result = TestListenableWorkerBuilder<DueReminderWorker>(context).build().doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())

        val nm = context.getSystemService(NotificationManager::class.java)
        val expectedBillNotifId = NotificationHelper.stableNotificationId("bill_$overdueBillId")
        val expectedLoanNotifId = NotificationHelper.stableNotificationId("loan_$overdueLoanId")
        assertThat(expectedBillNotifId).isNotEqualTo(expectedLoanNotifId)
        assertThat(nm.activeNotifications.map { it.id })
            .containsExactly(expectedBillNotifId, expectedLoanNotifId)

        val billNotif = nm.activeNotifications.first { it.id == expectedBillNotifId }.notification
        val loanNotif = nm.activeNotifications.first { it.id == expectedLoanNotifId }.notification
        assertThat(billNotif.extras.getString("android.text")).contains("Overdue by 3 days • ₱1,500.00")
        assertThat(loanNotif.extras.getString("android.text")).contains("Overdue by 25 days • ₱4,850.00")
    }

    @Test
    fun `broadcast ACTION_SNOOZE_1H cancels notification and schedules 1h DueReminderWorker with INPUT_ITEM_KEY`() = runTest {
        val cycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val billId = repo.insertUnpaidTestBill(cycleId, "Meralco", 380_000L, dueDay = 14)
        val itemKey = "bill_$billId"
        val notificationId = NotificationHelper.stableNotificationId(itemKey)

        NotificationHelper.showDueAlert(
            context = context,
            itemKey = itemKey,
            notificationId = notificationId,
            title = "Meralco",
            body = "Due Today • ₱3,800.00",
            entityType = "bill",
            entityId = billId,
            amountCentavos = 380_000L
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_1H
            putExtra(NotificationActionReceiver.EXTRA_ITEM_KEY, itemKey)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        NotificationActionReceiver().onReceive(context, snoozeIntent)

        val nm = context.getSystemService(NotificationManager::class.java)
        assertThat(nm.activeNotifications.none { it.id == notificationId }).isTrue()

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkScheduler.snoozeWorkName(itemKey))
            .get()
        assertThat(workInfos).hasSize(1)
        val info = workInfos.single()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.initialDelayMillis).isEqualTo(Duration.ofHours(1).toMillis())
    }

    @Test
    fun `snoozed INPUT_ITEM_KEY bypasses lead-day filter when unpaid and skips when already paid`() = runTest {
        val cycleId = db.budgetDao().insertCycle(
            BudgetCycleEntity(
                yearMonth = "2026-11",
                totalBudgetCentavos = 5_000_000L,
                createdAtEpochMs = 1_000L
            )
        )
        // Due day 25 on Nov 14 -> 11 days away (shouldNotify normally false)
        val billId = repo.insertUnpaidTestBill(cycleId, "Spotify", 14_900L, dueDay = 25)
        val itemKey = "bill_$billId"

        val snoozedResult = TestListenableWorkerBuilder<DueReminderWorker>(context)
            .setInputData(workDataOf(DueReminderWorker.INPUT_ITEM_KEY to itemKey))
            .build()
            .doWork()
        assertThat(snoozedResult).isEqualTo(ListenableWorker.Result.success())

        val nm = context.getSystemService(NotificationManager::class.java)
        assertThat(nm.activeNotifications.map { it.id })
            .containsExactly(NotificationHelper.stableNotificationId(itemKey))

        // After settling, snoozed re-run stays silent
        nm.cancelAll()
        repo.settleBill(billId)
        val afterPaidResult = TestListenableWorkerBuilder<DueReminderWorker>(context)
            .setInputData(workDataOf(DueReminderWorker.INPUT_ITEM_KEY to itemKey))
            .build()
            .doWork()
        assertThat(afterPaidResult).isEqualTo(ListenableWorker.Result.success())
        assertThat(nm.activeNotifications).isEmpty()
    }

    @Test
    fun `global push toggle disabled suppresses daily and snoozed alerts`() = runTest {
        val cycleId = db.budgetDao().insertCycle(
            BudgetCycleEntity(
                yearMonth = "2026-11",
                totalBudgetCentavos = 5_000_000L,
                createdAtEpochMs = 1_000L
            )
        )
        val billId = repo.insertUnpaidTestBill(cycleId, "Converge FiberX", 150_000L, dueDay = 11)
        NotificationHelper.setGlobalPushEnabled(context, false)

        val dailyResult = TestListenableWorkerBuilder<DueReminderWorker>(context).build().doWork()
        val snoozedResult = TestListenableWorkerBuilder<DueReminderWorker>(context)
            .setInputData(workDataOf(DueReminderWorker.INPUT_ITEM_KEY to "bill_$billId"))
            .build()
            .doWork()

        assertThat(dailyResult).isEqualTo(ListenableWorker.Result.success())
        assertThat(snoozedResult).isEqualTo(ListenableWorker.Result.success())
        val nm = context.getSystemService(NotificationManager::class.java)
        assertThat(nm.activeNotifications).isEmpty()
    }

    @Test
    fun `WorkScheduler schedules daily 9am reminders, monthly rollover, and reschedules on boot`() {
        val manila = ZoneId.of("Asia/Manila")

        val morningBeforeNine = ZonedDateTime.of(2026, 11, 14, 7, 30, 0, 0, manila)
        assertThat(WorkScheduler.nextDailyNineAmDelay(morningBeforeNine))
            .isEqualTo(Duration.ofMinutes(90))

        val exactNine = ZonedDateTime.of(2026, 11, 14, 9, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextDailyNineAmDelay(exactNine))
            .isEqualTo(Duration.ofHours(24))

        val afternoon = ZonedDateTime.of(2026, 11, 14, 15, 0, 0, 0, manila)
        assertThat(WorkScheduler.nextDailyNineAmDelay(afternoon))
            .isEqualTo(Duration.ofHours(18))

        val utcMidnight = ZonedDateTime.of(2026, 11, 14, 0, 0, 0, 0, ZoneOffset.UTC)
        // 00:00 UTC is 08:00 Asia/Manila -> 1 hour until 09:00 Asia/Manila
        assertThat(WorkScheduler.nextDailyNineAmDelay(utcMidnight))
            .isEqualTo(Duration.ofHours(1))

        val workManager = WorkManager.getInstance(context)
        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val dueInfos = workManager
            .getWorkInfosForUniqueWork(WorkScheduler.DAILY_DUE_REMINDERS_WORK_NAME)
            .get()
        assertThat(dueInfos).hasSize(1)
        assertThat(dueInfos.single().periodicityInfo!!.repeatIntervalMillis)
            .isEqualTo(Duration.ofHours(24).toMillis())

        val rolloverInfos = workManager
            .getWorkInfosForUniqueWork(WorkScheduler.MONTHLY_ROLLOVER_WORK_NAME)
            .get()
        assertThat(rolloverInfos).hasSize(1)
        assertThat(rolloverInfos.single().periodicityInfo!!.repeatIntervalMillis)
            .isEqualTo(Duration.ofHours(24).toMillis())

        val gasInfos = workManager
            .getWorkInfosForUniqueWork(WorkScheduler.WEEKLY_GAS_SYNC_WORK_NAME)
            .get()
        assertThat(gasInfos).hasSize(1)
    }

    @Test
    fun `stableNotificationId is positive and collision-free and denied POST_NOTIFICATIONS skips safely`() {
        val billId = NotificationHelper.stableNotificationId("bill_1")
        val loanId = NotificationHelper.stableNotificationId("loan_1")
        assertThat(billId).isGreaterThan(0)
        assertThat(loanId).isGreaterThan(0)
        assertThat(billId).isNotEqualTo(loanId)
        assertThat(NotificationHelper.stableNotificationId("bill_0")).isGreaterThan(0)

        Shadows.shadowOf(context as Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationHelper.showDueAlert(
            context = context,
            itemKey = "bill_99",
            notificationId = NotificationHelper.stableNotificationId("bill_99"),
            title = "Test",
            body = "Due Today • ₱100.00",
            entityType = "bill",
            entityId = 99L,
            amountCentavos = 10_000L
        )
        val nm = context.getSystemService(NotificationManager::class.java)
        assertThat(nm.activeNotifications).isEmpty()
    }

    @Test
    fun `broadcast ACTION_PAY_NOW settles next-month 1d 3d 7d loans and ignores duplicate tap`() = runTest {
        NotificationActionReceiver.testDispatcherOverride = StandardTestDispatcher(testScheduler)
        val novToday = LocalDate.of(2026, 11, 30)
        DueReminderWorker.testTodayProvider = { novToday }
        NotificationActionReceiver.testTodayProvider = { novToday }

        val novCycleId = db.budgetDao().insertCycle(
            BudgetCycleEntity(
                yearMonth = "2026-11",
                totalBudgetCentavos = 5_000_000L,
                createdAtEpochMs = 1_000L
            )
        )
        db.obligationDao().getActiveLoansOnce().forEach { seededLoan ->
            db.obligationDao().updateLoan(seededLoan.copy(isActive = false))
        }
        val loan1dId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Loan 1d",
                iconEmoji = "🚗",
                monthlyAmortizationCentavos = 100_000L,
                totalInstallments = 6,
                paidInstallments = 1,
                dueDayOfMonth = 1,
                nextDueYearMonth = "2026-12",
                notify1d = true,
                isActive = true
            )
        )
        val loan3dId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Loan 3d",
                iconEmoji = "🛵",
                monthlyAmortizationCentavos = 200_000L,
                totalInstallments = 6,
                paidInstallments = 1,
                dueDayOfMonth = 3,
                nextDueYearMonth = "2026-12",
                notify3d = true,
                isActive = true
            )
        )
        val loan7dId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Loan 7d",
                iconEmoji = "🏠",
                monthlyAmortizationCentavos = 300_000L,
                totalInstallments = 6,
                paidInstallments = 1,
                dueDayOfMonth = 7,
                nextDueYearMonth = "2026-12",
                notify7d = true,
                isActive = true
            )
        )

        val result = TestListenableWorkerBuilder<DueReminderWorker>(context).build().doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())

        val nm = context.getSystemService(NotificationManager::class.java)
        val notif1dId = NotificationHelper.stableNotificationId("loan_$loan1dId")
        val notif3dId = NotificationHelper.stableNotificationId("loan_$loan3dId")
        val notif7dId = NotificationHelper.stableNotificationId("loan_$loan7dId")
        assertThat(nm.activeNotifications.map { it.id })
            .containsExactly(notif1dId, notif3dId, notif7dId)

        val payIntentsByLoan = listOf(loan1dId to notif1dId, loan3dId to notif3dId, loan7dId to notif7dId)
            .associate { (loanId, notifId) ->
                val posted = nm.activeNotifications.first { it.id == notifId }
                val payIntent = Shadows.shadowOf(posted.notification.actions[0].actionIntent).savedIntent
                assertThat(payIntent.action).isEqualTo(NotificationActionReceiver.ACTION_PAY_NOW)
                assertThat(payIntent.getStringExtra(NotificationActionReceiver.EXTRA_DUE_YEAR_MONTH))
                    .isEqualTo("2026-12")
                loanId to payIntent
            }

        payIntentsByLoan.forEach { (loanId, payIntent) ->
            NotificationActionReceiver().onReceive(context, payIntent)
            advanceUntilIdle()

            val settled = db.obligationDao().getLoanById(loanId)!!
            assertThat(settled.paidInstallments).isEqualTo(2)
            assertThat(settled.nextDueYearMonth).isEqualTo("2027-01")
            assertThat(settled.lastPaidCycleYearMonth).isEqualTo("2026-12")
        }

        assertThat(nm.activeNotifications).isEmpty()
        val decCycle = db.budgetDao().getCycleByYearMonth("2026-12")!!
        assertThat(db.budgetDao().getTransactionsForCycleOnce(decCycle.id)).hasSize(3)
        assertThat(db.budgetDao().getTransactionsForCycleOnce(novCycleId)).isEmpty()

        // Duplicate tap on already-settled December installment does not double-pay or crash.
        val dupPayIntent = payIntentsByLoan.getValue(loan3dId)
        NotificationActionReceiver().onReceive(context, dupPayIntent)
        advanceUntilIdle()

        val afterDup = db.obligationDao().getLoanById(loan3dId)!!
        assertThat(afterDup.paidInstallments).isEqualTo(2)
        assertThat(afterDup.nextDueYearMonth).isEqualTo("2027-01")
        assertThat(db.budgetDao().getTransactionsForCycleOnce(decCycle.id)).hasSize(3)
    }

    @Test
    fun `broadcast ACTION_PAY_NOW routes delayed cross-month loan tap into active month cycle`() = runTest {
        NotificationActionReceiver.testDispatcherOverride = StandardTestDispatcher(testScheduler)
        val novCycleId = rolloverUseCase(YearMonth.of(2026, 11))
        val loanId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Delayed Tap Loan",
                iconEmoji = "🏦",
                monthlyAmortizationCentavos = 180_000L,
                totalInstallments = 6,
                paidInstallments = 1,
                dueDayOfMonth = 28,
                nextDueYearMonth = "2026-11",
                isActive = true
            )
        )

        val notifId = NotificationHelper.stableNotificationId("loan_$loanId")
        NotificationHelper.showDueAlert(
            context = context,
            itemKey = "loan_$loanId",
            notificationId = notifId,
            title = "Delayed Tap Loan",
            body = "Due Today • ₱1,800.00",
            entityType = NotificationActionReceiver.ENTITY_TYPE_LOAN,
            entityId = loanId,
            amountCentavos = 180_000L,
            dueYearMonth = "2026-11"
        )

        val posted = context.getSystemService(NotificationManager::class.java)
            .activeNotifications.first { it.id == notifId }
        val payIntent = Shadows.shadowOf(posted.notification.actions[0].actionIntent).savedIntent

        // User taps November's Pay action after crossing into December.
        NotificationActionReceiver.testTodayProvider = { LocalDate.of(2026, 12, 2) }
        NotificationActionReceiver().onReceive(context, payIntent)
        advanceUntilIdle()

        val settled = db.obligationDao().getLoanById(loanId)!!
        assertThat(settled.paidInstallments).isEqualTo(2)
        assertThat(settled.nextDueYearMonth).isEqualTo("2026-12")
        assertThat(settled.lastPaidCycleYearMonth).isEqualTo("2026-12")

        val decCycle = db.budgetDao().getCycleByYearMonth("2026-12")!!
        assertThat(db.budgetDao().getTransactionsForCycleOnce(decCycle.id)).hasSize(1)
        assertThat(db.budgetDao().getTransactionsForCycleOnce(novCycleId)).isEmpty()
        assertThat(context.getSystemService(NotificationManager::class.java).activeNotifications).isEmpty()
    }

    @Test
    fun `snoozed INPUT_ITEM_KEY skips multi-installment loan after one installment paid in-app`() = runTest {
        val novCycleId = db.budgetDao().insertCycle(
            BudgetCycleEntity(
                yearMonth = "2026-11",
                totalBudgetCentavos = 5_000_000L,
                createdAtEpochMs = 1_000L
            )
        )
        val loanId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Multi-Installment Loan",
                iconEmoji = "🛵",
                monthlyAmortizationCentavos = 450_000L,
                totalInstallments = 12,
                paidInstallments = 1,
                dueDayOfMonth = 14,
                nextDueYearMonth = "2026-11",
                isActive = true
            )
        )
        val itemKey = "loan_$loanId"
        val notifId = NotificationHelper.stableNotificationId(itemKey)

        // Daily run posts alert carrying dueYearMonth = "2026-11" in the Snooze PendingIntent.
        assertThat(TestListenableWorkerBuilder<DueReminderWorker>(context).build().doWork())
            .isEqualTo(ListenableWorker.Result.success())
        val nm = context.getSystemService(NotificationManager::class.java)
        val posted = nm.activeNotifications.first { it.id == notifId }
        val snoozeIntent = Shadows.shadowOf(posted.notification.actions[1].actionIntent).savedIntent
        assertThat(snoozeIntent.getStringExtra(NotificationActionReceiver.EXTRA_DUE_YEAR_MONTH))
            .isEqualTo("2026-11")

        NotificationActionReceiver().onReceive(context, snoozeIntent)
        assertThat(nm.activeNotifications.none { it.id == notifId }).isTrue()
        val snoozeWorkInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkScheduler.snoozeWorkName(itemKey))
            .get()
        assertThat(snoozeWorkInfos).hasSize(1)
        assertThat(snoozeWorkInfos.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
        nm.cancelAll()

        // Unpaid installment still re-alerts when snooze fires.
        val unpaidSnoozeResult = TestListenableWorkerBuilder<DueReminderWorker>(context)
            .setInputData(
                workDataOf(
                    DueReminderWorker.INPUT_ITEM_KEY to itemKey,
                    DueReminderWorker.INPUT_EXPECTED_DUE_YEAR_MONTH to "2026-11"
                )
            )
            .build()
            .doWork()
        assertThat(unpaidSnoozeResult).isEqualTo(ListenableWorker.Result.success())
        assertThat(nm.activeNotifications.map { it.id }).containsExactly(notifId)
        nm.cancelAll()

        // Paying 1 installment in-app advances nextDueYearMonth to 2026-12 while keeping loan active.
        repo.settleLoan(loanId, novCycleId)
        val afterInAppPay = db.obligationDao().getLoanById(loanId)!!
        assertThat(afterInAppPay.isActive).isTrue()
        assertThat(afterInAppPay.paidInstallments).isEqualTo(2)
        assertThat(afterInAppPay.nextDueYearMonth).isEqualTo("2026-12")

        // Snooze run with expectedDueYearMonth stays silent.
        val afterPaidExpectedResult = TestListenableWorkerBuilder<DueReminderWorker>(context)
            .setInputData(
                workDataOf(
                    DueReminderWorker.INPUT_ITEM_KEY to itemKey,
                    DueReminderWorker.INPUT_EXPECTED_DUE_YEAR_MONTH to "2026-11"
                )
            )
            .build()
            .doWork()
        assertThat(afterPaidExpectedResult).isEqualTo(ListenableWorker.Result.success())
        assertThat(nm.activeNotifications).isEmpty()

        // Fallback suppression when INPUT_EXPECTED_DUE_YEAR_MONTH is absent also stays silent.
        val afterPaidFallbackResult = TestListenableWorkerBuilder<DueReminderWorker>(context)
            .setInputData(workDataOf(DueReminderWorker.INPUT_ITEM_KEY to itemKey))
            .build()
            .doWork()
        assertThat(afterPaidFallbackResult).isEqualTo(ListenableWorker.Result.success())
        assertThat(nm.activeNotifications).isEmpty()

        // Next-month loan snoozed across midnight still re-alerts while unpaid, then skips once paid.
        val nextMonthLoanId = db.obligationDao().insertLoan(
            LoanEntity(
                name = "Next-Month Snoozed Loan",
                iconEmoji = "🚗",
                monthlyAmortizationCentavos = 520_000L,
                totalInstallments = 12,
                paidInstallments = 0,
                dueDayOfMonth = 4,
                nextDueYearMonth = "2026-12",
                isActive = true
            )
        )
        val nextMonthKey = "loan_$nextMonthLoanId"
        DueReminderWorker.testTodayProvider = { LocalDate.of(2026, 11, 28) }
        val nextMonthUnpaidSnooze = TestListenableWorkerBuilder<DueReminderWorker>(context)
            .setInputData(
                workDataOf(
                    DueReminderWorker.INPUT_ITEM_KEY to nextMonthKey,
                    DueReminderWorker.INPUT_EXPECTED_DUE_YEAR_MONTH to "2026-12"
                )
            )
            .build()
            .doWork()
        assertThat(nextMonthUnpaidSnooze).isEqualTo(ListenableWorker.Result.success())
        assertThat(nm.activeNotifications.map { it.id })
            .containsExactly(NotificationHelper.stableNotificationId(nextMonthKey))
        nm.cancelAll()

        val decCycleId = rolloverUseCase(YearMonth.of(2026, 12))
        repo.settleLoan(nextMonthLoanId, decCycleId)
        val nextMonthPaidSnooze = TestListenableWorkerBuilder<DueReminderWorker>(context)
            .setInputData(
                workDataOf(
                    DueReminderWorker.INPUT_ITEM_KEY to nextMonthKey,
                    DueReminderWorker.INPUT_EXPECTED_DUE_YEAR_MONTH to "2026-12"
                )
            )
            .build()
            .doWork()
        assertThat(nextMonthPaidSnooze).isEqualTo(ListenableWorker.Result.success())
        assertThat(nm.activeNotifications).isEmpty()
    }

    @Test
    fun `due_date_alerts channel uses IMPORTANCE_HIGH and Pay Snooze PendingIntents are distinct and immutable`() {
        val itemKey = "loan_42"
        val notificationId = NotificationHelper.stableNotificationId(itemKey)
        NotificationHelper.showDueAlert(
            context = context,
            itemKey = itemKey,
            notificationId = notificationId,
            title = "Car Loan",
            body = "Due in 3 days • ₱15,000.00",
            entityType = NotificationActionReceiver.ENTITY_TYPE_LOAN,
            entityId = 42L,
            amountCentavos = 1_500_000L,
            dueYearMonth = "2026-12"
        )

        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel!!.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.name.toString()).isEqualTo(NotificationHelper.CHANNEL_NAME)

        val posted = nm.activeNotifications.first { it.id == notificationId }.notification
        assertThat(posted.channelId).isEqualTo(NotificationHelper.CHANNEL_ID)
        assertThat(posted.actions).hasLength(2)

        val payPendingIntent = posted.actions[0].actionIntent
        val snoozePendingIntent = posted.actions[1].actionIntent
        assertThat(payPendingIntent).isNotEqualTo(snoozePendingIntent)

        val shadowPay = Shadows.shadowOf(payPendingIntent)
        val shadowSnooze = Shadows.shadowOf(snoozePendingIntent)
        assertThat(shadowPay.requestCode).isNotEqualTo(shadowSnooze.requestCode)
        assertThat(shadowPay.isBroadcastIntent).isTrue()
        assertThat(shadowSnooze.isBroadcastIntent).isTrue()
        assertThat(shadowPay.flags and PendingIntent.FLAG_IMMUTABLE)
            .isEqualTo(PendingIntent.FLAG_IMMUTABLE)
        assertThat(shadowSnooze.flags and PendingIntent.FLAG_IMMUTABLE)
            .isEqualTo(PendingIntent.FLAG_IMMUTABLE)
        assertThat(shadowPay.savedIntent.action).isEqualTo(NotificationActionReceiver.ACTION_PAY_NOW)
        assertThat(shadowPay.savedIntent.getStringExtra(NotificationActionReceiver.EXTRA_DUE_YEAR_MONTH))
            .isEqualTo("2026-12")
        assertThat(shadowSnooze.savedIntent.action).isEqualTo(NotificationActionReceiver.ACTION_SNOOZE_1H)
        assertThat(shadowSnooze.savedIntent.getStringExtra(NotificationActionReceiver.EXTRA_DUE_YEAR_MONTH))
            .isEqualTo("2026-12")
    }
}
