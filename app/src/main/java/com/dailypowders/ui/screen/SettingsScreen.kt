package com.dailypowders.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailypowders.BuildConfig
import com.dailypowders.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val dayResetHour by viewModel.dayResetHour.collectAsState()
    val dayResetMinute by viewModel.dayResetMinute.collectAsState()
    val debugEnabled by viewModel.debugFeaturesEnabled.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Day Reset Time",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "When \"yesterday\" ends and tasks reset",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val displayHour = if (dayResetHour == 0) 12 else if (dayResetHour > 12) dayResetHour - 12 else dayResetHour
                val amPm = if (dayResetHour < 12) "AM" else "PM"
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${displayHour.toString().padStart(2, '0')}:${dayResetMinute.toString().padStart(2, '0')} $amPm",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.toggleDebugFeatures() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (debugEnabled) "Disable Debug Features" else "Enable Debug Features")
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dayResetHour,
            initialMinute = dayResetMinute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Day Reset Time") },
            text = {
                TimeInput(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateDayResetTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
