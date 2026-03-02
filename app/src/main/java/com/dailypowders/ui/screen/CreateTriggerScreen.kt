package com.dailypowders.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailypowders.data.model.HappensEvery
import com.dailypowders.data.model.TimeOfDay
import com.dailypowders.ui.viewmodel.TriggerViewModel

data class TaskEntry(
    val title: String = "",
    val expiresHours: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTriggerScreen(
    viewModel: TriggerViewModel,
    onDone: () -> Unit
) {
    var triggerTitle by remember { mutableStateOf("") }
    var happensEvery by remember { mutableStateOf(HappensEvery.DAILY) }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var weekDay by remember { mutableIntStateOf(1) } // Monday
    var monthDay by remember { mutableIntStateOf(1) }
    var tasks by remember { mutableStateOf(listOf(TaskEntry())) }
    var showTimePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "New Trigger",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Trigger Title
        item {
            OutlinedTextField(
                value = triggerTitle,
                onValueChange = { triggerTitle = it },
                label = { Text("Trigger Title (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Happens Every
        item {
            Text("Happens Every", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HappensEvery.entries.take(2).forEach { option ->
                        FilterChip(
                            selected = happensEvery == option,
                            onClick = { happensEvery = option },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HappensEvery.entries.drop(2).forEach { option ->
                        FilterChip(
                            selected = happensEvery == option,
                            onClick = { happensEvery = option },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }

        // When (time picker) - not shown for Manual
        if (happensEvery != HappensEvery.MANUALLY) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("When:", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showTimePicker = true }) {
                        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                        val amPm = if (hour < 12) "AM" else "PM"
                        Text("${displayHour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPm")
                    }
                }
            }

            // Week day picker
            if (happensEvery == HappensEvery.WEEKLY) {
                item {
                    Text("Day of Week", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        days.forEachIndexed { index, day ->
                            FilterChip(
                                selected = weekDay == index + 1,
                                onClick = { weekDay = index + 1 },
                                label = { Text(day) }
                            )
                        }
                    }
                }
            }

            // Month day picker
            if (happensEvery == HappensEvery.MONTHLY) {
                item {
                    OutlinedTextField(
                        value = monthDay.toString(),
                        onValueChange = {
                            val day = it.toIntOrNull()
                            if (day != null && day in 1..31) monthDay = day
                        },
                        label = { Text("Day of Month (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(200.dp)
                    )
                }
            }
        }

        // Tasks section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tasks", style = MaterialTheme.typography.titleMedium)
        }

        itemsIndexed(tasks) { index, task ->
            TaskEntryRow(
                task = task,
                triggerTitle = triggerTitle,
                onTitleChange = { newTitle ->
                    tasks = tasks.toMutableList().apply {
                        set(index, task.copy(title = newTitle))
                    }
                },
                onExpiresChange = { newExpires ->
                    tasks = tasks.toMutableList().apply {
                        set(index, task.copy(expiresHours = newExpires))
                    }
                },
                onDelete = if (tasks.size > 1) {
                    { tasks = tasks.toMutableList().apply { removeAt(index) } }
                } else null
            )
        }

        item {
            TextButton(
                onClick = { tasks = tasks + TaskEntry() }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Task")
            }
        }

        // Save button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val taskList = tasks
                        .filter { it.title.isNotBlank() }
                        .map { entry ->
                            val expires = entry.expiresHours.toIntOrNull()
                            entry.title to expires
                        }
                    if (taskList.isNotEmpty()) {
                        viewModel.createTrigger(
                            title = triggerTitle,
                            happensEvery = happensEvery,
                            when_ = if (happensEvery != HappensEvery.MANUALLY) TimeOfDay(hour, minute) else null,
                            weekDay = if (happensEvery == HappensEvery.WEEKLY) weekDay else null,
                            monthDay = if (happensEvery == HappensEvery.MONTHLY) monthDay else null,
                            tasks = taskList
                        )
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Trigger")
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimeInput(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

private val expiresOptions = listOf(
    "" to "End of Day",
    "1" to "1 hour",
    "2" to "2 hours",
    "4" to "4 hours",
    "6" to "6 hours",
    "8" to "8 hours",
    "12" to "12 hours"
)

@Composable
private fun TaskEntryRow(
    task: TaskEntry,
    triggerTitle: String,
    onTitleChange: (String) -> Unit,
    onExpiresChange: (String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var expiresExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = task.title,
                    onValueChange = onTitleChange,
                    label = { Text("Task Title") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                // Button to copy trigger title into task title
                if (triggerTitle.isNotBlank()) {
                    TextButton(onClick = { onTitleChange(triggerTitle) }) {
                        Text("Use title")
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove task")
                    }
                }
            }
            Box {
                val selectedLabel = expiresOptions.find { it.first == task.expiresHours }?.second
                    ?: if (task.expiresHours.isBlank()) "End of Day" else "${task.expiresHours} hours"
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    label = { Text("Expires after") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // Invisible clickable overlay to open dropdown
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expiresExpanded = true }
                )
                DropdownMenu(
                    expanded = expiresExpanded,
                    onDismissRequest = { expiresExpanded = false }
                ) {
                    expiresOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onExpiresChange(value)
                                expiresExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
