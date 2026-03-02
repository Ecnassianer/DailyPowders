package com.dailypowders.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailypowders.ui.viewmodel.TriggerViewModel

@Composable
fun ManualTriggersScreen(viewModel: TriggerViewModel) {
    val manualTriggers by viewModel.manualTriggers.collectAsState()
    val data by viewModel.data.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Activate Manual Triggers",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Tap a trigger to activate its tasks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (manualTriggers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No manual triggers",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(manualTriggers, key = { it.id }) { trigger ->
                    val activationCount = data.dailyState.manualTriggerCounts[trigger.id] ?: 0
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.activateManualTrigger(trigger.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = trigger.title.ifBlank { "Manual Trigger" },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${trigger.tasks.size} tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (activationCount > 0) {
                                Text(
                                    text = "Activated $activationCount${if (activationCount == 1) " time" else " times"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
