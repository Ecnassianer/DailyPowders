package com.dailypowders.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailypowders.alarm.AlarmScheduler
import com.dailypowders.data.repository.TaskRepository
import com.dailypowders.domain.TaskManager
import java.time.LocalDateTime

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DONE = "com.dailypowders.ACTION_DONE"
        const val ACTION_SNOOZE = "com.dailypowders.ACTION_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TRIGGER_ID = "trigger_id"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID) ?: return

        val repository = TaskRepository(context)
        val taskManager = TaskManager()
        val notificationHelper = NotificationHelper(context)
        val alarmScheduler = AlarmScheduler(context)

        when (intent.action) {
            ACTION_DONE -> {
                // Layer 5: Cancel any pending snooze alarm
                alarmScheduler.cancelSnooze(taskId, triggerId)

                // Mark complete and save atomically
                try {
                    repository.update { data ->
                        taskManager.completeTask(data, taskId)
                    }
                    // Layer 7: Only dismiss after successful save
                    notificationHelper.cancelTaskNotification(taskId)
                } catch (e: Exception) {
                    // Save failed - don't cancel notification so user can retry
                }
            }
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 30)

                // Layer 7: Immediately dismiss
                notificationHelper.cancelTaskNotification(taskId)

                // Schedule snooze alarm
                alarmScheduler.scheduleSnooze(taskId, triggerId, minutes)
            }
        }
    }
}
