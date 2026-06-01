package com.dailypowders.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailypowders.alarm.AlarmScheduler
import com.dailypowders.data.model.TaskDataFile
import com.dailypowders.data.repository.TaskRepository
import com.dailypowders.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(application)
    private val alarmScheduler = AlarmScheduler(application)
    private val notificationHelper = NotificationHelper(application)

    // Serializes every "read disk + publish to flows" and "write disk + publish"
    // sequence so a navigation-triggered loadData() can't publish a stale value
    // on top of a toggle that just wrote to disk (and vice versa).
    private val stateMutex = Mutex()

    private val _dayResetHour = MutableStateFlow(3)
    val dayResetHour: StateFlow<Int> = _dayResetHour

    private val _dayResetMinute = MutableStateFlow(33)
    val dayResetMinute: StateFlow<Int> = _dayResetMinute

    private val _debugFeaturesEnabled = MutableStateFlow(false)
    val debugFeaturesEnabled: StateFlow<Boolean> = _debugFeaturesEnabled

    private val _tasksPaused = MutableStateFlow(false)
    val tasksPaused: StateFlow<Boolean> = _tasksPaused

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    /** Publish all persisted settings to the UI flows. Call inside [stateMutex]. */
    private fun publishSettings(data: TaskDataFile) {
        _dayResetHour.value = data.dayResetHour
        _dayResetMinute.value = data.dayResetMinute
        _debugFeaturesEnabled.value = data.debugFeaturesEnabled
        _tasksPaused.value = data.tasksPaused
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                val data = try { repository.load() } catch (e: Exception) {
                    TaskDataFile()
                }
                publishSettings(data)
            }
        }
    }

    fun toggleDebugFeatures() {
        viewModelScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                repository.update { data ->
                    data.copy(debugFeaturesEnabled = !data.debugFeaturesEnabled)
                }
                _debugFeaturesEnabled.value = repository.load().debugFeaturesEnabled
            }
        }
    }

    fun setTasksPaused(paused: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                repository.update { data ->
                    data.copy(tasksPaused = paused)
                }
                _tasksPaused.value = paused
            }
        }
    }

    fun updateDayResetTime(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                repository.update { data ->
                    data.copy(dayResetHour = hour, dayResetMinute = minute)
                }

                val updated = repository.load()
                _dayResetHour.value = updated.dayResetHour
                _dayResetMinute.value = updated.dayResetMinute

                // Reschedule day reset alarm and all trigger alarms with new time
                val now = LocalDateTime.now()
                alarmScheduler.scheduleAllAlarms(updated, now)
            }
        }
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            try {
                resolver.openOutputStream(uri, "wt").use { output ->
                    if (output == null) {
                        _userMessage.value = "Export failed: could not open destination"
                        return@launch
                    }
                    repository.exportTo(output)
                }
                _userMessage.value = "Export complete"
            } catch (e: Exception) {
                _userMessage.value = "Export failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            try {
                stateMutex.withLock {
                    resolver.openInputStream(uri).use { input ->
                        if (input == null) {
                            _userMessage.value = "Import failed: could not open file"
                            return@launch
                        }
                        repository.importFrom(input)
                    }

                    // Wipe stale notifications and rebuild alarms for the new data set.
                    notificationHelper.cancelAll()
                    val data = repository.load()
                    alarmScheduler.cancelAllSnoozeAlarms(data)
                    alarmScheduler.scheduleAllAlarms(data, LocalDateTime.now())

                    // Reflect imported settings in the UI.
                    publishSettings(data)

                    _userMessage.value = "Import complete — ${data.triggers.size} trigger(s) restored"
                }
            } catch (e: Exception) {
                _userMessage.value = "Import failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
