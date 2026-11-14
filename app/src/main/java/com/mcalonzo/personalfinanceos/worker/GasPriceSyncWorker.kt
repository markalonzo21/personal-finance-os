package com.mcalonzo.personalfinanceos.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mcalonzo.personalfinanceos.data.repository.GasPriceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Syncs Petron Fairview Premium 95 pricing from GasWatchPH, retrying transient network or parse failures.
 */
@HiltWorker
class GasPriceSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gasPriceRepository: GasPriceRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            gasPriceRepository.syncFromRemote().fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WEEKLY_WORK_NAME = "weekly_gas_price_sync"
        const val UNIQUE_COLD_START_WORK_NAME = "cold_start_gas_price_sync"
    }
}
