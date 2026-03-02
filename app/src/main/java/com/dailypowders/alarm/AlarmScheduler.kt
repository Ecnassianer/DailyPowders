package com.dailypowders.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dailypowders.data.model.TaskDataFile
import com.dailypowders.data.model.HappensEvery
import com.dailypowders.data.model.Trigger
import com.dailypowders.domain.TaskManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {

    companion object {
        const val ACTION_TRIGGER_FIRE = "com.dailypowders.ACTION_TRIGGER_FIRE"
        const val ACTION_SNOOZE_FIRE = "com.dailypowders.ACTION_SNOOZE_FIRE"
        const val ACTION_DAY_RESET = "com.dailypowders.ACTION_DAY_RESET"
        const val EXTRA_TRIGGER_ID = "trigger_id"
        const val EXTRA_TASK_ID = "task_id"

        private const val DAY_RESET_REQUEST_CODE = 999999
        private const val TRIGGER_BASE_CODE = 0
        private const val SNOOZE_BASE_CODE = 500000
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val taskManager = TaskManager()

    fun scheduleAllAlarms(data: TaskDataFile, now: LocalDateTime) {
        scheduleDayReset(data, now)
        for (trigger in data.triggers) {
            if (trigger.happensEvery != HappensEvery.MANUALLY) {
                scheduleTriggerAlarm(trigger, data, now)
            }
        }
    }

    fun scheduleTriggerAlarm(trigger: Trigger, data: TaskDataFile, now: LocalDateTime) {
        val when_ = trigger.when_ ?: return
        val dayResetTime = LocalTime.of(data.dayResetHour, data.dayResetMinute)
        val triggerTime = LocalTime.of(when_.hour, when_.minute)

        val targetDateTime = computeNextOccurrence(trigger, triggerTime, dayResetTime, now)
            ?: return // No valid next occurrence

        val millis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_FIRE
            putExtra(EXTRA_TRIGGER_ID, trigger.id)
        }
        val requestCode = TRIGGER_BASE_CODE + trigger.id.hashCode().and(0x7FFFFFFF)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarm(millis, pendingIntent)
    }

    /**
     * Compute the next occurrence of a trigger, accounting for DAILY/WEEKLY/MONTHLY
     * schedules and the dayResetTime boundary.
     */
    private fun computeNextOccurrence(
        trigger: Trigger,
        triggerTime: LocalTime,
        dayResetTime: LocalTime,
        now: LocalDateTime
    ): LocalDateTime? {
        val effectiveToday = taskManager.effectiveDate(now, dayResetTime)

        // Start from effective today and try up to 32 days forward
        for (daysAhead in 0..31) {
            val candidateDate = effectiveToday.plusDays(daysAhead.toLong())
            val candidateDateTime = LocalDateTime.of(candidateDate, triggerTime)

            // Must be in the future
            if (!candidateDateTime.isAfter(now)) continue

            // Check if this date matches the trigger's schedule
            val matches = when (trigger.happensEvery) {
                HappensEvery.DAILY -> true
                HappensEvery.WEEKLY -> {
                    val targetDay = trigger.weekDay ?: return null
                    if (targetDay !in 1..7) return null
                    candidateDate.dayOfWeek == DayOfWeek.of(targetDay)
                }
                HappensEvery.MONTHLY -> {
                    val targetDay = trigger.monthDay ?: return null
                    if (targetDay !in 1..31) return null
                    candidateDate.dayOfMonth == targetDay
                }
                HappensEvery.MANUALLY -> return null
            }

            if (matches) return candidateDateTime
        }

        return null // No occurrence found within 32 days
    }

    fun scheduleSnooze(taskId: String, triggerId: String, durationMinutes: Int) {
        val millis = System.currentTimeMillis() + durationMinutes * 60 * 1000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_FIRE
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TRIGGER_ID, triggerId)
        }
        val requestCode = SNOOZE_BASE_CODE + taskId.hashCode().and(0x7FFFFFFF)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarm(millis, pendingIntent)
    }

    fun cancelSnooze(taskId: String, triggerId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_FIRE
        }
        val requestCode = SNOOZE_BASE_CODE + taskId.hashCode().and(0x7FFFFFFF)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleDayReset(data: TaskDataFile, now: LocalDateTime) {
        val resetTime = LocalTime.of(data.dayResetHour, data.dayResetMinute)
        var targetDateTime = LocalDateTime.of(now.toLocalDate(), resetTime)

        if (targetDateTime.isBefore(now) || targetDateTime.isEqual(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val millis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DAY_RESET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, DAY_RESET_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarm(millis, pendingIntent)
    }

    fun cancelAllSnoozeAlarms(data: TaskDataFile) {
        for (trigger in data.triggers) {
            for (task in trigger.tasks) {
                cancelSnooze(task.id, trigger.id)
            }
        }
    }

    fun cancelTriggerAlarm(trigger: Trigger) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_FIRE
        }
        val requestCode = TRIGGER_BASE_CODE + trigger.id.hashCode().and(0x7FFFFFFF)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun setExactAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } else {
                // Fallback to inexact alarm when exact alarm permission is denied
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        }
    }
}
