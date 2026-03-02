package com.dailypowders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailypowders.alarm.AlarmScheduler
import com.dailypowders.data.repository.TaskRepository
import com.dailypowders.ui.DailyPowdersApp
import com.dailypowders.ui.theme.DailyPowdersTheme
import com.dailypowders.ui.viewmodel.TaskViewModel
import com.dailypowders.ui.viewmodel.TriggerViewModel
import com.dailypowders.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HIGHLIGHT_TASK_ID = "highlight_task_id"
    }

    // Shared mutable state for highlight, observable by Compose without recreating tree
    private val highlightTaskIdState = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result - app works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()
        scheduleAlarmsIfNeeded()

        highlightTaskIdState.value = intent?.getStringExtra(EXTRA_HIGHLIGHT_TASK_ID)

        setContent {
            DailyPowdersTheme {
                DailyPowdersApp(highlightTaskIdState = highlightTaskIdState)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val taskId = intent.getStringExtra(EXTRA_HIGHLIGHT_TASK_ID)
        if (taskId != null) {
            highlightTaskIdState.value = taskId
        }
    }

    private fun requestPermissions() {
        // POST_NOTIFICATIONS (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // SCHEDULE_EXACT_ALARM (API 31+) — needs special app access, not a runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    private fun scheduleAlarmsIfNeeded() {
        try {
            val repository = TaskRepository(this)
            val data = repository.load()
            val alarmScheduler = AlarmScheduler(this)
            alarmScheduler.scheduleAllAlarms(data, LocalDateTime.now())
        } catch (e: Exception) {
            // First launch, no data yet — nothing to schedule
        }
    }
}
