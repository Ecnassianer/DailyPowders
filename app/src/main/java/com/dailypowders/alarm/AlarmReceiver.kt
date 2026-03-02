package com.dailypowders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailypowders.data.repository.TaskRepository
import com.dailypowders.domain.TaskManager
import com.dailypowders.notification.NotificationHelper
import java.time.LocalDateTime
import java.time.LocalTime

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = TaskRepository(context)
        val taskManager = TaskManager()
        val notificationHelper = NotificationHelper(context)
        val alarmScheduler = AlarmScheduler(context)
        val now = LocalDateTime.now()

        when (intent.action) {
            AlarmScheduler.ACTION_TRIGGER_FIRE -> {
                val triggerId = intent.getStringExtra(AlarmScheduler.EXTRA_TRIGGER_ID) ?: return
                handleTriggerFire(
                    context, repository, taskManager, notificationHelper,
                    alarmScheduler, triggerId, now
                )
            }
            AlarmScheduler.ACTION_SNOOZE_FIRE -> {
                val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
                val triggerId = intent.getStringExtra(AlarmScheduler.EXTRA_TRIGGER_ID) ?: return
                handleSnoozeFire(
                    context, repository, taskManager, notificationHelper, taskId, triggerId, now
                )
            }
            AlarmScheduler.ACTION_DAY_RESET -> {
                handleDayReset(
                    context, repository, taskManager, notificationHelper, alarmScheduler, now
                )
            }
        }
    }

    private fun handleTriggerFire(
        context: Context,
        repository: TaskRepository,
        taskManager: TaskManager,
        notificationHelper: NotificationHelper,
        alarmScheduler: AlarmScheduler,
        triggerId: String,
        now: LocalDateTime
    ) {
        repository.update { data ->
            var updated = taskManager.ensureFreshState(data, now)

            val trigger = updated.triggers.find { it.id == triggerId }
            if (trigger != null) {
                // Check if this trigger should actually fire for the current effective day
                val dayResetTime = LocalTime.of(updated.dayResetHour, updated.dayResetMinute)
                if (taskManager.shouldTriggerFire(trigger, now, dayResetTime)) {
                    updated = taskManager.activateTrigger(updated, triggerId, now)
                }

                // Post notifications for active tasks in this trigger
                val freshTrigger = updated.triggers.find { it.id == triggerId }
                if (freshTrigger != null && triggerId in updated.dailyState.activatedTriggers) {
                    for (task in freshTrigger.tasks) {
                        if (task.id !in updated.dailyState.completedTaskIds &&
                            task.id !in updated.dailyState.expiredTaskIds
                        ) {
                            val expirationTime = taskManager.getExpirationTime(task, freshTrigger, updated, now)
                            notificationHelper.postTaskNotification(task, freshTrigger, expirationTime, now)
                        }
                    }
                }

                // Reschedule this trigger for the next occurrence
                alarmScheduler.scheduleTriggerAlarm(freshTrigger ?: trigger, updated, now)
            }

            updated
        }
    }

    private fun handleSnoozeFire(
        context: Context,
        repository: TaskRepository,
        taskManager: TaskManager,
        notificationHelper: NotificationHelper,
        taskId: String,
        triggerId: String,
        now: LocalDateTime
    ) {
        // Layer 4: State check before posting
        val data = try {
            var d = repository.load()
            d = taskManager.ensureFreshState(d, now)
            d
        } catch (e: Exception) { return }
        val state = data.dailyState

        if (taskId in state.completedTaskIds || taskId in state.expiredTaskIds) {
            return // Task already completed or expired, don't re-post
        }

        // If the day rolled over, this snooze is from the previous day - skip it
        if (triggerId !in state.activatedTriggers) {
            return
        }

        val trigger = data.triggers.find { it.id == triggerId } ?: return
        val task = trigger.tasks.find { it.id == taskId } ?: return

        // Check if task has expired since snooze was scheduled
        if (taskManager.isTaskExpired(task, trigger, data, now)) {
            return
        }

        val expirationTime = taskManager.getExpirationTime(task, trigger, data, now)
        notificationHelper.postTaskNotification(task, trigger, expirationTime, now)
    }

    private fun handleDayReset(
        context: Context,
        repository: TaskRepository,
        taskManager: TaskManager,
        notificationHelper: NotificationHelper,
        alarmScheduler: AlarmScheduler,
        now: LocalDateTime
    ) {
        // Layer 6: Clean sweep
        val data = try { repository.load() } catch (e: Exception) {
            // Still cancel notifications even if data load fails
            notificationHelper.cancelAll()
            return
        }

        // Cancel all snooze alarms before clearing state
        alarmScheduler.cancelAllSnoozeAlarms(data)

        // Cancel all notifications
        notificationHelper.cancelAll()

        // Clear daily state
        repository.update { d ->
            val updated = taskManager.ensureFreshState(d, now)
            updated
        }

        // Reschedule all alarms for the new day (re-load to get fresh state)
        val freshData = try { repository.load() } catch (e: Exception) { return }
        alarmScheduler.scheduleAllAlarms(freshData, now)
    }
}
