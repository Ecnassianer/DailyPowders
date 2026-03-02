package com.dailypowders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailypowders.data.model.HappensEvery
import com.dailypowders.data.repository.TaskRepository
import com.dailypowders.domain.TaskManager
import com.dailypowders.notification.NotificationHelper
import java.time.LocalDateTime

class BootReceiver : BroadcastReceiver() {

    companion object {
        private val ACCEPTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACCEPTED_ACTIONS) return

        val repository = TaskRepository(context)
        val taskManager = TaskManager()
        val notificationHelper = NotificationHelper(context)
        val alarmScheduler = AlarmScheduler(context)
        val now = LocalDateTime.now()

        var data = try { repository.load() } catch (e: Exception) { return }

        // Step 1: Check if daily state is stale
        data = taskManager.ensureFreshState(data, now)

        // Step 2: Activate triggers that should have fired during downtime
        val missedTriggers = taskManager.getTriggersToFireNow(data, now)
        for (trigger in missedTriggers) {
            data = taskManager.activateTrigger(data, trigger.id, now)
        }

        // Step 3: Update expirations
        data = taskManager.updateExpirations(data, now)
        repository.save(data)

        // Step 4: Reschedule all future alarms
        alarmScheduler.scheduleAllAlarms(data, now)

        // Step 5: Post notifications for active, non-expired tasks
        for (trigger in data.triggers) {
            if (trigger.id !in data.dailyState.activatedTriggers) continue
            for (task in trigger.tasks) {
                if (task.id in data.dailyState.completedTaskIds) continue
                if (task.id in data.dailyState.expiredTaskIds) continue

                val expirationTime = taskManager.getExpirationTime(task, trigger, data, now)
                // Don't post if already expired
                if (expirationTime != null && expirationTime.isBefore(now)) continue

                notificationHelper.postTaskNotification(task, trigger, expirationTime, now)
            }
        }
    }
}
