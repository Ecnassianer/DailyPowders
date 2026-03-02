package com.dailypowders.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dailypowders.MainActivity
import com.dailypowders.R
import com.dailypowders.data.model.Task
import com.dailypowders.data.model.Trigger
import java.time.Duration
import java.time.LocalDateTime

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "daily_powders_tasks"
        const val CHANNEL_NAME = "Task Reminders"
        private const val SUMMARY_BASE_ID = 900000
    }

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for daily task reminders"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun postTaskNotification(
        task: Task,
        trigger: Trigger,
        expirationTime: LocalDateTime?,
        now: LocalDateTime
    ) {
        // Use *4 spacing to prevent PendingIntent request code collisions
        // Slot 0: tap, Slot 1: done, Slot 2: snooze30, Slot 3: snooze60
        val baseRequestCode = task.id.hashCode().and(0x3FFFFFFF) * 4
        val notificationId = task.id.hashCode()
        val groupKey = "trigger_${trigger.id}"

        // Tap notification -> open Task View
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_HIGHLIGHT_TASK_ID, task.id)
        }
        val tapPending = PendingIntent.getActivity(
            context, baseRequestCode, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Done! action
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DONE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(NotificationActionReceiver.EXTRA_TRIGGER_ID, trigger.id)
        }
        val donePending = PendingIntent.getBroadcast(
            context, baseRequestCode + 1, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 30 action
        val snooze30Intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(NotificationActionReceiver.EXTRA_TRIGGER_ID, trigger.id)
            putExtra(NotificationActionReceiver.EXTRA_SNOOZE_MINUTES, 30)
        }
        val snooze30Pending = PendingIntent.getBroadcast(
            context, baseRequestCode + 2, snooze30Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 60 action
        val snooze60Intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(NotificationActionReceiver.EXTRA_TRIGGER_ID, trigger.id)
            putExtra(NotificationActionReceiver.EXTRA_SNOOZE_MINUTES, 60)
        }
        val snooze60Pending = PendingIntent.getBroadcast(
            context, baseRequestCode + 3, snooze60Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(triggerDisplayName(trigger))
            .setContentIntent(tapPending)
            .setAutoCancel(false)
            .setGroup(groupKey)
            .addAction(0, "Done!", donePending)
            .addAction(0, "Snooze 30", snooze30Pending)
            .addAction(0, "Snooze 60", snooze60Pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        // Layer 3: setTimeoutAfter for expiring tasks
        if (expirationTime != null && expirationTime.isAfter(now)) {
            val timeoutMs = Duration.between(now, expirationTime).toMillis()
            if (timeoutMs > 0) {
                builder.setTimeoutAfter(timeoutMs)
            }
        }

        notificationManager.notify(notificationId, builder.build())

        // Post/update summary notification for the group
        postGroupSummary(trigger)
    }

    fun cancelTaskNotification(taskId: String, triggerId: String? = null) {
        notificationManager.cancel(taskId.hashCode())
        // If we know the trigger, check if the summary should be cancelled too
        if (triggerId != null) {
            cancelSummaryIfNoTaskNotifications(triggerId)
        }
    }

    private fun cancelSummaryIfNoTaskNotifications(triggerId: String) {
        val groupKey = "trigger_$triggerId"
        val remaining = notificationManager.activeNotifications.count { notification ->
            notification.id < SUMMARY_BASE_ID && notification.notification.group == groupKey
        }
        if (remaining == 0) {
            cancelGroupSummary(triggerId)
        }
    }

    fun cancelTriggerNotifications(trigger: Trigger) {
        for (task in trigger.tasks) {
            cancelTaskNotification(task.id)
        }
        cancelGroupSummary(trigger.id)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }

    fun cleanupStaleNotifications(
        completedTaskIds: Set<String>,
        expiredTaskIds: Set<String>,
        activeTriggerIds: Set<String>,
        allTaskIds: Set<String>
    ) {
        val activeNotifications = notificationManager.activeNotifications
        val staleIds = mutableSetOf<String>()

        for (notification in activeNotifications) {
            val id = notification.id

            // Check summary notifications
            if (id >= SUMMARY_BASE_ID) {
                // This is a summary - check if its trigger is still active
                // We can't easily reverse-map the summary ID to a trigger ID,
                // so summaries are cleaned up separately below
                continue
            }

            // Check if this notification ID corresponds to a known task
            var found = false
            for (taskId in allTaskIds) {
                if (taskId.hashCode() == id) {
                    found = true
                    if (taskId in completedTaskIds || taskId in expiredTaskIds) {
                        notificationManager.cancel(id)
                    }
                    break
                }
            }

            // If notification doesn't match any known task, it's orphaned
            if (!found) {
                notificationManager.cancel(id)
            }
        }
    }

    private fun postGroupSummary(trigger: Trigger) {
        val summaryId = SUMMARY_BASE_ID + trigger.id.hashCode().and(0x7FFFF)
        val groupKey = "trigger_${trigger.id}"

        // Tap summary -> open Task View
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context, summaryId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(triggerDisplayName(trigger))
            .setContentText("Tasks active")
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(false)
            .setContentIntent(tapPending)
            .build()

        notificationManager.notify(summaryId, summary)
    }

    private fun cancelGroupSummary(triggerId: String) {
        val summaryId = SUMMARY_BASE_ID + triggerId.hashCode().and(0x7FFFF)
        notificationManager.cancel(summaryId)
    }

    private fun triggerDisplayName(trigger: Trigger): String {
        return trigger.title.ifBlank {
            when (trigger.happensEvery) {
                com.dailypowders.data.model.HappensEvery.DAILY -> {
                    val w = trigger.when_
                    if (w != null) "Daily at ${w.hour.toString().padStart(2, '0')}:${w.minute.toString().padStart(2, '0')}"
                    else "Daily"
                }
                com.dailypowders.data.model.HappensEvery.WEEKLY -> "Weekly"
                com.dailypowders.data.model.HappensEvery.MONTHLY -> "Monthly"
                com.dailypowders.data.model.HappensEvery.MANUALLY -> "Manual"
            }
        }
    }
}
