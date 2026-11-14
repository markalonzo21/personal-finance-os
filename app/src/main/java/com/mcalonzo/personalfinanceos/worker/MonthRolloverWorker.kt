package com.mcalonzo.personalfinanceos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mcalonzo.personalfinanceos.data.local.dao.RoutineDao
import com.mcalonzo.personalfinanceos.domain.usecase.MonthRolloverUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/**
 * Ensures the current Asia/Manila monthly budget cycle exists when auto-rollover is enabled.
 */
@HiltWorker
class MonthRolloverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val monthRolloverUseCase: MonthRolloverUseCase,
    private val routineDao: RoutineDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val routine = routineDao.getRoutineTemplateOnce()
            if (routine?.autoRolloverEnabled == false) {
                return Result.success()
            }
            monthRolloverUseCase(YearMonth.now(ZoneId.of("Asia/Manila")))
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "monthly_budget_rollover"
    }
}
