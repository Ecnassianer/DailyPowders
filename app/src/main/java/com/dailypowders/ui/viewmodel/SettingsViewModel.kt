package com.dailypowders.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailypowders.alarm.AlarmScheduler
import com.dailypowders.data.model.TaskDataFile
import com.dailypowders.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(application)
    private val alarmScheduler = AlarmScheduler(application)

    private val _dayResetHour = MutableStateFlow(3)
    val dayResetHour: StateFlow<Int> = _dayResetHour

    private val _dayResetMinute = MutableStateFlow(33)
    val dayResetMinute: StateFlow<Int> = _dayResetMinute

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = try { repository.load() } catch (e: Exception) {
                TaskDataFile()
            }
            _dayResetHour.value = data.dayResetHour
            _dayResetMinute.value = data.dayResetMinute
        }
    }

    fun updateDayResetTime(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _dayResetHour.value = hour
            _dayResetMinute.value = minute

            repository.update { data ->
                data.copy(dayResetHour = hour, dayResetMinute = minute)
            }

            val updated = repository.load()

            // Reschedule day reset alarm and all trigger alarms with new time
            val now = LocalDateTime.now()
            alarmScheduler.scheduleAllAlarms(updated, now)
        }
    }
}
