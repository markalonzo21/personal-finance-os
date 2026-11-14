package com.mcalonzo.personalfinanceos.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/**
 * Re-registers recurring WorkManager schedules after device reboot or app package update.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WorkScheduler.scheduleRecurringWork(WorkManager.getInstance(appContext))
        }
    }
}
