package com.mcalonzo.personalfinanceos.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.mcalonzo.personalfinanceos.data.repository.BudgetRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles 1-tap notification actions (`Pay` settlement and `Remind in 1h` snooze)
 * asynchronously via [goAsync] without opening the app UI.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun budgetRepository(): BudgetRepository
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        if (intent == null) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        when (intent.action) {
            ACTION_PAY_NOW -> handlePayNow(appContext, intent, notificationId)
            ACTION_SNOOZE_1H -> handleSnooze(appContext, intent, notificationId)
        }
    }

    private fun handlePayNow(appContext: Context, intent: Intent, notificationId: Int) {
        val entityType = intent.getStringExtra(EXTRA_ENTITY_TYPE)
        val entityId = intent.getLongExtra(EXTRA_ENTITY_ID, -1L)
        val expectedDueMonth = intent.getStringExtra(EXTRA_DUE_YEAR_MONTH)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { rawMonth -> runCatching { YearMonth.parse(rawMonth) }.getOrNull() }
        val pendingResult: PendingResult? = goAsync()
        val dispatcher = testDispatcherOverride ?: Dispatchers.IO

        CoroutineScope(SupervisorJob() + dispatcher).launch {
            try {
                val repo = testRepositoryOverride ?: EntryPointAccessors.fromApplication(
                    appContext,
                    ReceiverEntryPoint::class.java
                ).budgetRepository()

                if (entityId > 0L) {
                    when (entityType) {
                        ENTITY_TYPE_BILL -> settleIgnoringAlreadyPaid {
                            repo.settleBill(entityId)
                        }

                        ENTITY_TYPE_LOAN -> {
                            val today = testTodayProvider?.invoke() ?: LocalDate.now(MANILA_ZONE)
                            val activeMonth = YearMonth.from(today)
                            val targetMonth = expectedDueMonth?.let { maxOf(activeMonth, it) }
                                ?: activeMonth
                            settleIgnoringAlreadyPaid {
                                repo.settleLoan(entityId, repo.getOrCreateCycleId(targetMonth))
                            }
                        }
                    }
                }

                if (notificationId != -1) {
                    NotificationHelper.cancelNotification(appContext, notificationId)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun handleSnooze(appContext: Context, intent: Intent, notificationId: Int) {
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)?.takeIf { it.isNotBlank() }
            ?: run {
                val entityType = intent.getStringExtra(EXTRA_ENTITY_TYPE)
                val entityId = intent.getLongExtra(EXTRA_ENTITY_ID, -1L)
                if (!entityType.isNullOrBlank() && entityId > 0L) {
                    "${entityType}_$entityId"
                } else {
                    null
                }
            }
        val expectedDueYearMonth = intent.getStringExtra(EXTRA_DUE_YEAR_MONTH)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (notificationId != -1) {
            NotificationHelper.cancelNotification(appContext, notificationId)
        }
        if (itemKey != null) {
            WorkScheduler.scheduleSnoozeReminder(
                workManager = WorkManager.getInstance(appContext),
                itemKey = itemKey,
                expectedDueYearMonth = expectedDueYearMonth
            )
        }
    }

    private inline fun settleIgnoringAlreadyPaid(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalStateException) {
            // Already settled in-app before notification tap; dismiss cleanly.
        } catch (_: IllegalArgumentException) {
            // Target bill/loan/cycle was deleted before tap; dismiss cleanly.
        }
    }

    companion object {
        const val ACTION_PAY_NOW = "com.mcalonzo.personalfinanceos.ACTION_PAY_NOW"
        const val ACTION_SNOOZE_1H = "com.mcalonzo.personalfinanceos.ACTION_SNOOZE_1H"

        const val EXTRA_ENTITY_TYPE = "extra_entity_type"
        const val EXTRA_ENTITY_ID = "extra_entity_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_ITEM_KEY = "extra_item_key"
        const val EXTRA_DUE_YEAR_MONTH = "extra_due_year_month"

        const val ENTITY_TYPE_BILL = "bill"
        const val ENTITY_TYPE_LOAN = "loan"

        private val MANILA_ZONE: ZoneId = ZoneId.of("Asia/Manila")

        @Volatile
        internal var testRepositoryOverride: BudgetRepository? = null

        @Volatile
        internal var testDispatcherOverride: CoroutineDispatcher? = null

        @Volatile
        internal var testTodayProvider: (() -> LocalDate)? = null
    }
}
