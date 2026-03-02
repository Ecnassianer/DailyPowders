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
import com.dailypowders.data.model.*
import com.dailypowders.ui.viewmodel.TriggerViewModel

data class TaskEntryWithId(
    val originalId: String?,
    val title: String = "",
    val expiresHours: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTriggerScreen(
    triggerId: String,
    viewModel: TriggerViewModel,
    onDone: () -> Unit
) {
    val data by viewModel.data.collectAsState()
    val trigger = data.triggers.find { it.id == triggerId }

    if (trigger == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Trigger not found")
        }
        return
    }

    var triggerTitle by remember(trigger) { mutableStateOf(trigger.title) }
    var happensEvery by remember(trigger) { mutableStateOf(trigger.happensEvery) }
    var hour by remember(trigger) { mutableIntStateOf(trigger.when_?.hour ?: 8) }
    var minute by remember(trigger) { mutableIntStateOf(trigger.when_?.minute ?: 0) }
    var weekDay by remember(trigger) { mutableIntStateOf(trigger.weekDay ?: 1) }
    var monthDay by remember(trigger) { mutableIntStateOf(trigger.monthDay ?: 1) }
    // Track tasks with their original IDs (null = new task)
    var tasks by remember(trigger) {
        mutableStateOf(trigger.tasks.map { task ->
            TaskEntryWithId(
                originalId = task.id,
                title = task.title,
                expiresHours = task.expiresHours?.toString() ?: ""
            )
        })
    }
    var showTimePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Edit Trigger",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            OutlinedTextField(
                value = triggerTitle,
                onValueChange = { triggerTitle = it },
                label = { Text("Trigger Title (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

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

        if (happensEvery != HappensEvery.MANUALLY) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("When:", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showTimePicker = true }) {
                        Text("${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")
                    }
                }
            }

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

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tasks", style = MaterialTheme.typography.titleMedium)
        }

        itemsIndexed(tasks) { index, task ->
            Card(modifier = Modifier.fillMaxWidth()) {
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
                            onValueChange = { newTitle ->
                                tasks = tasks.toMutableList().apply {
                                    set(index, task.copy(title = newTitle))
                                }
                            },
                            label = { Text("Task Title") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (tasks.size > 1) {
                            IconButton(onClick = {
                                tasks = tasks.toMutableList().apply { removeAt(index) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove task")
                            }
                        }
                    }
                    var expiresExpanded by remember { mutableStateOf(false) }
                    Box {
                        val expiresOptions = listOf(
                            "" to "End of Day",
                            "1" to "1 hour",
                            "2" to "2 hours",
                            "4" to "4 hours",
                            "6" to "6 hours",
                            "8" to "8 hours",
                            "12" to "12 hours"
                        )
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
                                        tasks = tasks.toMutableList().apply {
                                            set(index, task.copy(expiresHours = value))
                                        }
                                        expiresExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            TextButton(onClick = {
                tasks = tasks + TaskEntryWithId(originalId = null, title = "", expiresHours = "")
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Task")
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val taskList = tasks.mapNotNull { entry ->
                        if (entry.title.isBlank()) return@mapNotNull null
                        val taskId = entry.originalId ?: viewModel.generateId()
                        Task(
                            id = taskId,
                            title = entry.title,
                            expiresHours = entry.expiresHours.toIntOrNull()
                        )
                    }

                    if (taskList.isNotEmpty()) {
                        val updatedTrigger = Trigger(
                            id = triggerId,
                            title = triggerTitle,
                            happensEvery = happensEvery,
                            when_ = if (happensEvery != HappensEvery.MANUALLY) TimeOfDay(hour, minute) else null,
                            weekDay = if (happensEvery == HappensEvery.WEEKLY) weekDay else null,
                            monthDay = if (happensEvery == HappensEvery.MONTHLY) monthDay else null,
                            tasks = taskList
                        )
                        viewModel.updateTrigger(updatedTrigger)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
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
