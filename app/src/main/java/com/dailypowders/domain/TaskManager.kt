package com.dailypowders.domain

import com.dailypowders.data.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

class TaskManager {

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
    }

    fun ensureFreshState(data: TaskDataFile, now: LocalDateTime): TaskDataFile {
        val dayResetTime = LocalTime.of(data.dayResetHour, data.dayResetMinute)
        val today = effectiveDate(now, dayResetTime)
        val storedDate = data.dailyState.date

        return if (storedDate.isEmpty() || storedDate != today.format(DATE_FORMAT)) {
            data.copy(dailyState = DailyState(date = today.format(DATE_FORMAT)))
        } else {
            data
        }
    }

    fun activateTrigger(data: TaskDataFile, triggerId: String, now: LocalDateTime): TaskDataFile {
        val trigger = data.triggers.find { it.id == triggerId } ?: return data
        val state = data.dailyState
        val isReactivation = triggerId in state.activatedTriggers

        val newActivated = if (isReactivation) {
            state.activatedTriggers
        } else {
            state.activatedTriggers + triggerId
        }

        val newManualCounts = if (trigger.happensEvery == HappensEvery.MANUALLY) {
            state.manualTriggerCounts + (triggerId to (state.manualTriggerCounts[triggerId] ?: 0) + 1)
        } else {
            state.manualTriggerCounts
        }

        // For manual re-activation, clear completed/expired status so tasks become active again
        val triggerTaskIds = trigger.tasks.map { it.id }.toSet()
        val newCompleted = if (isReactivation && trigger.happensEvery == HappensEvery.MANUALLY) {
            state.completedTaskIds.filter { it !in triggerTaskIds }
        } else {
            state.completedTaskIds
        }
        val baseExpired = if (isReactivation && trigger.happensEvery == HappensEvery.MANUALLY) {
            state.expiredTaskIds.filter { it !in triggerTaskIds }
        } else {
            state.expiredTaskIds
        }

        // Expire any tasks that are already past their expiration time
        val newExpired = baseExpired.toMutableList()
        for (task in trigger.tasks) {
            if (task.id !in newCompleted && task.id !in newExpired) {
                if (isTaskExpired(task, trigger, data, now)) {
                    newExpired.add(task.id)
                }
            }
        }

        return data.copy(
            dailyState = state.copy(
                activatedTriggers = newActivated,
                completedTaskIds = newCompleted,
                manualTriggerCounts = newManualCounts,
                expiredTaskIds = newExpired
            )
        )
    }

    fun completeTask(data: TaskDataFile, taskId: String): TaskDataFile {
        val state = data.dailyState
        if (taskId in state.completedTaskIds) return data

        val newCompleted = state.completedTaskIds + taskId
        val newExpired = state.expiredTaskIds - taskId

        return data.copy(
            dailyState = state.copy(
                completedTaskIds = newCompleted,
                expiredTaskIds = newExpired
            )
        )
    }

    fun uncompleteTask(data: TaskDataFile, taskId: String, now: LocalDateTime): TaskDataFile {
        val state = data.dailyState
        if (taskId !in state.completedTaskIds) return data

        val newCompleted = state.completedTaskIds - taskId

        // Check if the task should go back to expired
        val task = findTask(data, taskId)
        val trigger = findTriggerForTask(data, taskId)
        val shouldExpire = if (task != null && trigger != null) {
            isTaskExpired(task, trigger, data, now)
        } else false

        val newExpired = if (shouldExpire) {
            state.expiredTaskIds + taskId
        } else {
            state.expiredTaskIds
        }

        return data.copy(
            dailyState = state.copy(
                completedTaskIds = newCompleted,
                expiredTaskIds = newExpired
            )
        )
    }

    fun updateExpirations(data: TaskDataFile, now: LocalDateTime): TaskDataFile {
        val state = data.dailyState
        val newExpired = state.expiredTaskIds.toMutableList()
        var changed = false

        for (trigger in data.triggers) {
            if (trigger.id !in state.activatedTriggers) continue
            for (task in trigger.tasks) {
                if (task.id in state.completedTaskIds) continue
                if (task.id in newExpired) continue
                if (isTaskExpired(task, trigger, data, now)) {
                    newExpired.add(task.id)
                    changed = true
                }
            }
        }

        return if (changed) {
            data.copy(dailyState = state.copy(expiredTaskIds = newExpired))
        } else {
            data
        }
    }

    fun getActiveTasks(data: TaskDataFile): List<Pair<Task, Trigger>> {
        val state = data.dailyState
        val result = mutableListOf<Pair<Task, Trigger>>()
        for (trigger in data.triggers) {
            if (trigger.id !in state.activatedTriggers) continue
            for (task in trigger.tasks) {
                if (task.id !in state.completedTaskIds && task.id !in state.expiredTaskIds) {
                    result.add(task to trigger)
                }
            }
        }
        return result
    }

    fun getCompletedTasks(data: TaskDataFile): List<Pair<Task, Trigger>> {
        val state = data.dailyState
        val result = mutableListOf<Pair<Task, Trigger>>()
        for (trigger in data.triggers) {
            if (trigger.id !in state.activatedTriggers) continue
            for (task in trigger.tasks) {
                if (task.id in state.completedTaskIds) {
                    result.add(task to trigger)
                }
            }
        }
        return result
    }

    fun getExpiredTasks(data: TaskDataFile): List<Pair<Task, Trigger>> {
        val state = data.dailyState
        val result = mutableListOf<Pair<Task, Trigger>>()
        for (trigger in data.triggers) {
            if (trigger.id !in state.activatedTriggers) continue
            for (task in trigger.tasks) {
                if (task.id in state.expiredTaskIds) {
                    result.add(task to trigger)
                }
            }
        }
        return result
    }

    fun shouldTriggerFire(trigger: Trigger, now: LocalDateTime, dayResetTime: LocalTime): Boolean {
        if (trigger.happensEvery == HappensEvery.MANUALLY) return false
        trigger.when_ ?: return false

        // Use effective date to check day-of-week/month correctly across the dayReset boundary
        val today = effectiveDate(now, dayResetTime)

        return when (trigger.happensEvery) {
            HappensEvery.DAILY -> true
            HappensEvery.WEEKLY -> {
                val targetDay = trigger.weekDay ?: return false
                if (targetDay !in 1..7) return false
                today.dayOfWeek == DayOfWeek.of(targetDay)
            }
            HappensEvery.MONTHLY -> {
                val targetDay = trigger.monthDay ?: return false
                if (targetDay !in 1..31) return false
                today.dayOfMonth == targetDay
            }
            HappensEvery.MANUALLY -> false
        }
    }

    fun getTriggersToFireNow(data: TaskDataFile, now: LocalDateTime): List<Trigger> {
        val dayResetTime = LocalTime.of(data.dayResetHour, data.dayResetMinute)
        return data.triggers.filter { trigger ->
            trigger.id !in data.dailyState.activatedTriggers &&
            shouldTriggerFire(trigger, now, dayResetTime) &&
            trigger.when_ != null &&
            hasTriggerTimePassed(trigger.when_, now, dayResetTime)
        }
    }

    fun isTaskExpired(task: Task, trigger: Trigger, data: TaskDataFile, now: LocalDateTime): Boolean {
        val expirationTime = getExpirationTime(task, trigger, data, now) ?: return false
        return now.isAfter(expirationTime)
    }

    fun getExpirationTime(task: Task, trigger: Trigger, data: TaskDataFile, now: LocalDateTime): LocalDateTime? {
        val expiresHours = task.expiresHours ?: return null
        if (expiresHours <= 0) return null
        val when_ = trigger.when_ ?: return null
        val dayResetTime = LocalTime.of(data.dayResetHour, data.dayResetMinute)
        val today = effectiveDate(now, dayResetTime)
        val triggerLocalTime = LocalTime.of(when_.hour, when_.minute)
        val triggerTime = LocalDateTime.of(today, triggerLocalTime)
        return triggerTime.plusHours(expiresHours.toLong())
    }

    fun addTrigger(data: TaskDataFile, trigger: Trigger): TaskDataFile {
        return data.copy(triggers = data.triggers + trigger)
    }

    fun updateTrigger(data: TaskDataFile, trigger: Trigger): TaskDataFile {
        return data.copy(
            triggers = data.triggers.map { if (it.id == trigger.id) trigger else it }
        )
    }

    fun deleteTrigger(data: TaskDataFile, triggerId: String): TaskDataFile {
        val trigger = data.triggers.find { it.id == triggerId } ?: return data
        val taskIds = trigger.tasks.map { it.id }.toSet()
        val state = data.dailyState

        return data.copy(
            triggers = data.triggers.filter { it.id != triggerId },
            dailyState = state.copy(
                activatedTriggers = state.activatedTriggers - triggerId,
                completedTaskIds = state.completedTaskIds.filter { it !in taskIds },
                expiredTaskIds = state.expiredTaskIds.filter { it !in taskIds },
                manualTriggerCounts = state.manualTriggerCounts - triggerId
            )
        )
    }

    private fun findTask(data: TaskDataFile, taskId: String): Task? {
        for (trigger in data.triggers) {
            for (task in trigger.tasks) {
                if (task.id == taskId) return task
            }
        }
        return null
    }

    fun findTriggerForTask(data: TaskDataFile, taskId: String): Trigger? {
        for (trigger in data.triggers) {
            for (task in trigger.tasks) {
                if (task.id == taskId) return trigger
            }
        }
        return null
    }

    fun effectiveDate(now: LocalDateTime, dayResetTime: LocalTime): LocalDate {
        return if (now.toLocalTime().isBefore(dayResetTime)) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
    }

    private fun hasTriggerTimePassed(when_: TimeOfDay, now: LocalDateTime, dayResetTime: LocalTime): Boolean {
        val triggerTime = LocalTime.of(when_.hour, when_.minute)
        val currentTime = now.toLocalTime()

        if (triggerTime.isBefore(dayResetTime)) {
            // Trigger fires before day reset (e.g., 2:00 AM trigger, 3:33 AM reset)
            if (currentTime.isBefore(dayResetTime)) {
                // We're also before day reset - check if trigger time has passed
                return !currentTime.isBefore(triggerTime)
            } else {
                // We're after day reset - this trigger's time for the current effective day
                // hasn't come yet (it will fire before the next dayReset)
                return false
            }
        } else {
            // Trigger fires at or after day reset (normal case)
            return !currentTime.isBefore(triggerTime)
        }
    }
}
