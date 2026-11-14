package com.mcalonzo.personalfinanceos.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mcalonzo.personalfinanceos.MainActivity
import com.mcalonzo.personalfinanceos.R
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator

/**
 * Manages the high-importance due-date notification channel, global push toggle,
 * deterministic notification IDs, and actionable Pay / Remind-in-1h alerts.
 */
object NotificationHelper {

    const val CHANNEL_ID = "due_date_alerts"
    const val CHANNEL_NAME = "Due-Date Alerts"

    private const val PREFS_NAME = "finance_os_notification_prefs"
    private const val KEY_GLOBAL_PUSH_ENABLED = "global_push_enabled"
    private const val MAX_PREFIXED_ID = 0x3FFF_FFFFL
    private const val LOAN_ID_FLAG = 0x4000_0000

    fun isGlobalPushEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GLOBAL_PUSH_ENABLED, true)
    }

    fun setGlobalPushEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GLOBAL_PUSH_ENABLED, enabled)
            .apply()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for upcoming and overdue bills and loans"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Generates a deterministic positive integer ID without collisions between
     * `bill_<id>` and `loan_<id>` keys.
     */
    fun stableNotificationId(itemKey: String): Int {
        if (itemKey.startsWith("bill_")) {
            val id = itemKey.removePrefix("bill_").toLongOrNull()
            if (id != null && id in 1L..MAX_PREFIXED_ID) {
                return id.toInt()
            }
        }
        if (itemKey.startsWith("loan_")) {
            val id = itemKey.removePrefix("loan_").toLongOrNull()
            if (id != null && id in 1L..MAX_PREFIXED_ID) {
                return LOAN_ID_FLAG or id.toInt()
            }
        }
        val hash = itemKey.hashCode() and 0x7FFF_FFFF
        return if (hash == 0) 1 else hash
    }

    fun showDueAlert(
        context: Context,
        itemKey: String,
        notificationId: Int,
        title: String,
        body: String,
        entityType: String,
        entityId: Long,
        amountCentavos: Long,
        dueYearMonth: String? = null
    ) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val normalizedDueYearMonth = dueYearMonth?.trim()?.takeIf { it.isNotEmpty() }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val payIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_PAY_NOW
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_TYPE, entityType)
            putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, entityId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_ITEM_KEY, itemKey)
            if (normalizedDueYearMonth != null) {
                putExtra(NotificationActionReceiver.EXTRA_DUE_YEAR_MONTH, normalizedDueYearMonth)
            }
        }
        val payPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            payIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_1H
            putExtra(NotificationActionReceiver.EXTRA_ITEM_KEY, itemKey)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            if (normalizedDueYearMonth != null) {
                putExtra(NotificationActionReceiver.EXTRA_DUE_YEAR_MONTH, normalizedDueYearMonth)
            }
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId.inv(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Pay ${BudgetCalculator.formatPeso(amountCentavos)}", payPendingIntent)
            .addAction(0, "Remind in 1h", snoozePendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
    }
}
