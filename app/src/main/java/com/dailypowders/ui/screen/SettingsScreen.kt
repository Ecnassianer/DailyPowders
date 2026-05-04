package com.dailypowders.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val dayResetHour by viewModel.dayResetHour.collectAsState()
    val dayResetMinute by viewModel.dayResetMinute.collectAsState()
    val debugEnabled by viewModel.debugFeaturesEnabled.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportData(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importData(uri)
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Backup",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Export saves all your triggers, tasks, and settings to a file you can restore later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            exportLauncher.launch("dailypowders-backup-${LocalDate.now()}.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export to file")
                    }
                    OutlinedButton(
                        onClick = { showImportConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import from file")
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

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
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

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Import will replace everything") },
            text = {
                Text(
                    "Importing a backup overwrites all current triggers, tasks, and settings. " +
                        "This cannot be undone. Continue?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                }) {
                    Text("Choose file")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
