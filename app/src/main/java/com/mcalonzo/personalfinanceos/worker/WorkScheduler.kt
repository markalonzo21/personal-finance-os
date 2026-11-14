package com.mcalonzo.personalfinanceos.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Schedules background WorkManager jobs for weekly Saturday 12:00 PM Asia/Manila gas syncs,
 * initial cold-start price refreshes, daily 09:00 AM due-date reminders, 1-hour snoozes,
 * and daily midnight month-rollover checks.
 */
object WorkScheduler {

    const val WEEKLY_GAS_SYNC_WORK_NAME = GasPriceSyncWorker.UNIQUE_WEEKLY_WORK_NAME
    const val COLD_START_GAS_SYNC_WORK_NAME = GasPriceSyncWorker.UNIQUE_COLD_START_WORK_NAME
    const val DAILY_DUE_REMINDERS_WORK_NAME = DueReminderWorker.UNIQUE_DAILY_WORK_NAME
    const val MONTHLY_ROLLOVER_WORK_NAME = MonthRolloverWorker.UNIQUE_WORK_NAME
    private val MANILA_ZONE: ZoneId = ZoneId.of("Asia/Manila")
    private val DAILY_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    fun snoozeWorkName(itemKey: String): String = "${DueReminderWorker.SNOOZE_WORK_PREFIX}$itemKey"

    /**
     * Computes delay from [now] until the next strictly-future Saturday 12:00:00 in Asia/Manila.
     */
    fun nextSaturdayNoonDelay(now: ZonedDateTime): Duration {
        val manilaNow = now.withZoneSameInstant(MANILA_ZONE)
        val saturdayDate = manilaNow.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        var candidate = ZonedDateTime.of(saturdayDate, LocalTime.NOON, MANILA_ZONE)
        if (!candidate.isAfter(manilaNow)) {
            candidate = ZonedDateTime.of(saturdayDate.plusWeeks(1), LocalTime.NOON, MANILA_ZONE)
        }
        return Duration.between(manilaNow, candidate)
    }

    /**
     * Computes delay from [now] until the next strictly-future 09:00:00 in Asia/Manila.
     */
    fun nextDailyNineAmDelay(now: ZonedDateTime): Duration {
        return nextDailyTimeDelay(now, DAILY_REMINDER_TIME)
    }

    /**
     * Computes delay from [now] until the next strictly-future 00:00:00 in Asia/Manila.
     */
    fun nextDailyMidnightDelay(now: ZonedDateTime): Duration {
        return nextDailyTimeDelay(now, LocalTime.MIDNIGHT)
    }

    fun enqueueWeeklyGasWatcher(workManager: WorkManager) {
        val initialDelay = nextSaturdayNoonDelay(ZonedDateTime.now(MANILA_ZONE))
        val request = PeriodicWorkRequestBuilder<GasPriceSyncWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(connectedNetworkConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            WEEKLY_GAS_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueColdStartGasSync(workManager: WorkManager) {
        val request = OneTimeWorkRequestBuilder<GasPriceSyncWorker>()
            .setConstraints(connectedNetworkConstraints())
            .build()

        workManager.enqueueUniqueWork(
            COLD_START_GAS_SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueDailyDueReminders(workManager: WorkManager) {
        val initialDelay = nextDailyNineAmDelay(ZonedDateTime.now(MANILA_ZONE))
        val request = PeriodicWorkRequestBuilder<DueReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DAILY_DUE_REMINDERS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleSnoozeReminder(workManager: WorkManager, itemKey: String) {
        val normalizedKey = itemKey.trim()
        if (normalizedKey.isEmpty()) return

        val request = OneTimeWorkRequestBuilder<DueReminderWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(workDataOf(DueReminderWorker.INPUT_ITEM_KEY to normalizedKey))
            .build()

        workManager.enqueueUniqueWork(
            snoozeWorkName(normalizedKey),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueMonthlyRollover(workManager: WorkManager) {
        val initialDelay = nextDailyMidnightDelay(ZonedDateTime.now(MANILA_ZONE))
        val request = PeriodicWorkRequestBuilder<MonthRolloverWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            MONTHLY_ROLLOVER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleRecurringWork(workManager: WorkManager) {
        enqueueWeeklyGasWatcher(workManager)
        enqueueDailyDueReminders(workManager)
        enqueueMonthlyRollover(workManager)
    }

    fun scheduleStartupWork(workManager: WorkManager) {
        scheduleRecurringWork(workManager)
        enqueueColdStartGasSync(workManager)
    }

    private fun nextDailyTimeDelay(now: ZonedDateTime, targetTime: LocalTime): Duration {
        val manilaNow = now.withZoneSameInstant(MANILA_ZONE)
        var candidate = ZonedDateTime.of(manilaNow.toLocalDate(), targetTime, MANILA_ZONE)
        if (!candidate.isAfter(manilaNow)) {
            candidate = candidate.plusDays(1)
        }
        return Duration.between(manilaNow, candidate)
    }

    private fun connectedNetworkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
