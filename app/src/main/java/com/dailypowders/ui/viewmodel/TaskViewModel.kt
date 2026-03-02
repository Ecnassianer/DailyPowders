package com.dailypowders.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailypowders.alarm.AlarmScheduler
import com.dailypowders.data.model.Task
import com.dailypowders.data.model.TaskDataFile
import com.dailypowders.data.model.Trigger
import com.dailypowders.data.repository.TaskRepository
import com.dailypowders.domain.TaskManager
import com.dailypowders.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(application)
    private val taskManager = TaskManager()
    private val notificationHelper = NotificationHelper(application)
    private val alarmScheduler = AlarmScheduler(application)

    private val _data = MutableStateFlow(TaskDataFile())
    val data: StateFlow<TaskDataFile> = _data

    private val _activeTasks = MutableStateFlow<List<Pair<Task, Trigger>>>(emptyList())
    val activeTasks: StateFlow<List<Pair<Task, Trigger>>> = _activeTasks

    private val _completedTasks = MutableStateFlow<List<Pair<Task, Trigger>>>(emptyList())
    val completedTasks: StateFlow<List<Pair<Task, Trigger>>> = _completedTasks

    private val _expiredTasks = MutableStateFlow<List<Pair<Task, Trigger>>>(emptyList())
    val expiredTasks: StateFlow<List<Pair<Task, Trigger>>> = _expiredTasks

    private val _highlightTaskId = MutableStateFlow<String?>(null)
    val highlightTaskId: StateFlow<String?> = _highlightTaskId

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = LocalDateTime.now()
            try {
                repository.update { data ->
                    var updated = taskManager.ensureFreshState(data, now)
                    updated = taskManager.updateExpirations(updated, now)
                    updated
                }
                val loaded = repository.load()
                updateState(loaded)

                // Layer 8: App launch cleanup sweep
                performCleanupSweep(loaded)
            } catch (e: Exception) {
                updateState(TaskDataFile())
            }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.update { data ->
                    taskManager.completeTask(data, taskId)
                }
                val updated = repository.load()
                updateState(updated)

                // Layer 2: Cancel notification
                notificationHelper.cancelTaskNotification(taskId)

                // Layer 5: Cancel snooze alarm
                val trigger = taskManager.findTriggerForTask(updated, taskId)
                if (trigger != null) {
                    alarmScheduler.cancelSnooze(taskId, trigger.id)
                }
            } catch (e: Exception) {
                // Reload to get consistent state
                loadData()
            }
        }
    }

    fun uncompleteTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = LocalDateTime.now()
                repository.update { data ->
                    taskManager.uncompleteTask(data, taskId, now)
                }
                val updated = repository.load()
                updateState(updated)
            } catch (e: Exception) {
                loadData()
            }
        }
    }

    fun setHighlightTask(taskId: String?) {
        _highlightTaskId.value = taskId
    }

    fun clearHighlight() {
        _highlightTaskId.value = null
    }

    private fun updateState(data: TaskDataFile) {
        _data.value = data
        _activeTasks.value = taskManager.getActiveTasks(data)
        _completedTasks.value = taskManager.getCompletedTasks(data)
        _expiredTasks.value = taskManager.getExpiredTasks(data)
    }

    private fun performCleanupSweep(data: TaskDataFile) {
        val allTaskIds = data.triggers.flatMap { it.tasks.map { t -> t.id } }.toSet()
        notificationHelper.cleanupStaleNotifications(
            completedTaskIds = data.dailyState.completedTaskIds.toSet(),
            expiredTaskIds = data.dailyState.expiredTaskIds.toSet(),
            activeTriggerIds = data.dailyState.activatedTriggers.toSet(),
            allTaskIds = allTaskIds
        )
    }
}
