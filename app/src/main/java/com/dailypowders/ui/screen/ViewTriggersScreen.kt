package com.dailypowders.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailypowders.data.model.HappensEvery
import com.dailypowders.data.model.Trigger
import com.dailypowders.ui.viewmodel.TriggerViewModel

@Composable
fun ViewTriggersScreen(
    viewModel: TriggerViewModel,
    onCreateTrigger: () -> Unit,
    onEditTrigger: (String) -> Unit
) {
    val triggers by viewModel.triggers.collectAsState()
    val data by viewModel.data.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "View Triggers",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (triggers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No triggers yet. Tap + to create one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(triggers, key = { it.id }) { trigger ->
                        TriggerCard(
                            trigger = trigger,
                            isActivatedToday = trigger.id in data.dailyState.activatedTriggers,
                            activationCount = data.dailyState.manualTriggerCounts[trigger.id],
                            onEdit = { onEditTrigger(trigger.id) },
                            onDelete = { viewModel.deleteTrigger(trigger.id) },
                            onActivate = {
                                if (trigger.happensEvery == HappensEvery.MANUALLY) {
                                    viewModel.activateManualTrigger(trigger.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        // FAB menu
        FloatingActionButton(
            onClick = onCreateTrigger,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create trigger")
        }
    }
}

@Composable
private fun TriggerCard(
    trigger: Trigger,
    isActivatedToday: Boolean,
    activationCount: Int?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = triggerDisplayTitle(trigger),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = triggerScheduleDescription(trigger),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${trigger.tasks.size} task${if (trigger.tasks.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isActivatedToday) {
                    val countText = if (trigger.happensEvery == HappensEvery.MANUALLY && activationCount != null) {
                        "Activated today ($activationCount)"
                    } else {
                        "Activated today"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete trigger",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Trigger") },
            text = { Text("Delete \"${triggerDisplayTitle(trigger)}\" and its ${trigger.tasks.size} task(s)?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun triggerDisplayTitle(trigger: Trigger): String {
    return trigger.title.ifBlank {
        triggerScheduleDescription(trigger)
    }
}

private fun triggerScheduleDescription(trigger: Trigger): String {
    return when (trigger.happensEvery) {
        HappensEvery.DAILY -> {
            val w = trigger.when_
            if (w != null) "Daily at ${w.hour.toString().padStart(2, '0')}:${w.minute.toString().padStart(2, '0')}"
            else "Daily"
        }
        HappensEvery.WEEKLY -> {
            val dayName = trigger.weekDay?.let {
                java.time.DayOfWeek.of(it).name.lowercase().replaceFirstChar { c -> c.uppercase() }
            } ?: ""
            val w = trigger.when_
            if (w != null) "Weekly on $dayName at ${w.hour.toString().padStart(2, '0')}:${w.minute.toString().padStart(2, '0')}"
            else "Weekly on $dayName"
        }
        HappensEvery.MONTHLY -> {
            val day = trigger.monthDay ?: 1
            val w = trigger.when_
            val suffix = when {
                day in 11..13 -> "th"
                day % 10 == 1 -> "st"
                day % 10 == 2 -> "nd"
                day % 10 == 3 -> "rd"
                else -> "th"
            }
            if (w != null) "Monthly on the $day$suffix at ${w.hour.toString().padStart(2, '0')}:${w.minute.toString().padStart(2, '0')}"
            else "Monthly on the $day$suffix"
        }
        HappensEvery.MANUALLY -> "Manual"
    }
}
