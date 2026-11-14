package com.mcalonzo.personalfinanceos.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Schedules background WorkManager jobs for weekly Saturday 12:00 PM Asia/Manila gas syncs
 * and initial cold-start price refreshes.
 */
object WorkScheduler {

    const val WEEKLY_GAS_SYNC_WORK_NAME = GasPriceSyncWorker.UNIQUE_WEEKLY_WORK_NAME
    const val COLD_START_GAS_SYNC_WORK_NAME = GasPriceSyncWorker.UNIQUE_COLD_START_WORK_NAME
    private val MANILA_ZONE: ZoneId = ZoneId.of("Asia/Manila")

    /**
     * Computes delay until the next strictly-future Saturday 12:00:00 in [now]'s time zone.
     */
    fun nextSaturdayNoonDelay(now: ZonedDateTime): Duration {
        val saturdayDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        var candidate = ZonedDateTime.of(saturdayDate, LocalTime.NOON, now.zone)
        if (!candidate.isAfter(now)) {
            candidate = ZonedDateTime.of(saturdayDate.plusWeeks(1), LocalTime.NOON, now.zone)
        }
        return Duration.between(now, candidate)
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

    private fun connectedNetworkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
