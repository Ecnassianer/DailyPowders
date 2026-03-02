package com.dailypowders.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailypowders.alarm.AlarmScheduler
import com.dailypowders.data.model.*
import com.dailypowders.data.repository.TaskRepository
import com.dailypowders.domain.TaskManager
import com.dailypowders.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class TriggerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(application)
    private val taskManager = TaskManager()
    private val notificationHelper = NotificationHelper(application)
    private val alarmScheduler = AlarmScheduler(application)

    private val _data = MutableStateFlow(TaskDataFile())
    val data: StateFlow<TaskDataFile> = _data

    private val _triggers = MutableStateFlow<List<Trigger>>(emptyList())
    val triggers: StateFlow<List<Trigger>> = _triggers

    private val _manualTriggers = MutableStateFlow<List<Trigger>>(emptyList())
    val manualTriggers: StateFlow<List<Trigger>> = _manualTriggers

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = LocalDateTime.now()
            val loaded = try {
                var d = repository.load()
                d = taskManager.ensureFreshState(d, now)
                d
            } catch (e: Exception) {
                TaskDataFile()
            }
            updateState(loaded)
        }
    }

    fun createTrigger(
        title: String,
        happensEvery: HappensEvery,
        when_: TimeOfDay?,
        weekDay: Int?,
        monthDay: Int?,
        tasks: List<Pair<String, Int?>>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = LocalDateTime.now()
            val triggerId = generateId()
            val taskList = tasks.map { (taskTitle, expiresHours) ->
                Task(
                    id = generateId(),
                    title = taskTitle,
                    expiresHours = expiresHours
                )
            }
            val trigger = Trigger(
                id = triggerId,
                title = title,
                happensEvery = happensEvery,
                when_ = when_,
                weekDay = weekDay,
                monthDay = monthDay,
                tasks = taskList
            )

            repository.update { data ->
                var updated = taskManager.addTrigger(data, trigger)

                // Immediately evaluate: if trigger should have fired today, activate it
                if (happensEvery != HappensEvery.MANUALLY) {
                    val dayResetTime = LocalTime.of(updated.dayResetHour, updated.dayResetMinute)
                    val shouldFire = taskManager.shouldTriggerFire(trigger, now, dayResetTime)
                    if (shouldFire && trigger.when_ != null) {
                        val triggerTime = LocalTime.of(trigger.when_.hour, trigger.when_.minute)
                        if (!now.toLocalTime().isBefore(triggerTime)) {
                            updated = taskManager.activateTrigger(updated, triggerId, now)
                        }
                    }
                }

                updated
            }

            val savedData = repository.load()

            // Schedule alarm for scheduled triggers
            if (happensEvery != HappensEvery.MANUALLY) {
                val savedTrigger = savedData.triggers.find { it.id == triggerId }
                if (savedTrigger != null) {
                    alarmScheduler.scheduleTriggerAlarm(savedTrigger, savedData, now)
                }
            }

            updateState(savedData)
        }
    }

    fun activateManualTrigger(triggerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = LocalDateTime.now()

            repository.update { data ->
                taskManager.activateTrigger(data, triggerId, now)
            }
            val data = repository.load()
            updateState(data)

            // Post notifications for activated tasks
            val trigger = data.triggers.find { it.id == triggerId } ?: return@launch
            for (task in trigger.tasks) {
                if (task.id !in data.dailyState.completedTaskIds &&
                    task.id !in data.dailyState.expiredTaskIds
                ) {
                    notificationHelper.postTaskNotification(task, trigger, null, now)
                }
            }
        }
    }

    fun deleteTrigger(triggerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentData = repository.load()
            val trigger = currentData.triggers.find { it.id == triggerId }

            // Cancel alarms and notifications for this trigger before deleting
            if (trigger != null) {
                alarmScheduler.cancelTriggerAlarm(trigger)
                for (task in trigger.tasks) {
                    alarmScheduler.cancelSnooze(task.id, triggerId)
                }
                notificationHelper.cancelTriggerNotifications(trigger)
            }

            repository.update { data ->
                taskManager.deleteTrigger(data, triggerId)
            }
            val updated = repository.load()
            updateState(updated)
        }
    }

    fun updateTrigger(trigger: Trigger) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = LocalDateTime.now()
            repository.update { data ->
                taskManager.updateTrigger(data, trigger)
            }
            val data = repository.load()
            updateState(data)

            if (trigger.happensEvery != HappensEvery.MANUALLY) {
                alarmScheduler.scheduleTriggerAlarm(trigger, data, now)
            }
        }
    }

    fun generateId(): String = UUID.randomUUID().toString().take(8)

    private fun updateState(data: TaskDataFile) {
        _data.value = data
        _triggers.value = data.triggers
        _manualTriggers.value = data.triggers.filter { it.happensEvery == HappensEvery.MANUALLY }
    }
}
