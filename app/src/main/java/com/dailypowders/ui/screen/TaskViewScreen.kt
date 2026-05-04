package com.dailypowders.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dailypowders.data.model.Task
import com.dailypowders.data.model.Trigger
import com.dailypowders.ui.theme.CompletedGreen
import com.dailypowders.ui.theme.ExpiredGray
import com.dailypowders.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.delay

@Composable
fun TaskViewScreen(viewModel: TaskViewModel) {
    val activeTasks by viewModel.activeTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    val expiredTasks by viewModel.expiredTasks.collectAsState()
    val highlightTaskId by viewModel.highlightTaskId.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Active Tasks Section
        if (activeTasks.isNotEmpty()) {
            item {
                SectionHeader("Active Tasks")
            }
            taskGroupsByTrigger(
                tasks = activeTasks,
                isCompleted = false,
                isExpired = false,
                highlightTaskId = highlightTaskId,
                onTaskClick = { viewModel.completeTask(it) },
                onHighlightFinished = { viewModel.clearHighlight() }
            )
        }

        // Completed Tasks Section
        if (completedTasks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader("Completed Tasks")
            }
            taskGroupsByTrigger(
                tasks = completedTasks,
                isCompleted = true,
                isExpired = false,
                highlightTaskId = null,
                onTaskClick = { viewModel.uncompleteTask(it) }
            )
        }

        // Expired Tasks Section
        if (expiredTasks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader("Expired Tasks")
            }
            taskGroupsByTrigger(
                tasks = expiredTasks,
                isCompleted = false,
                isExpired = true,
                highlightTaskId = null,
                onTaskClick = { viewModel.completeTask(it) }
            )
        }

        // Empty state
        if (activeTasks.isEmpty() && completedTasks.isEmpty() && expiredTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tasks for today",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun TriggerGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

private fun LazyListScope.taskGroupsByTrigger(
    tasks: List<Pair<Task, Trigger>>,
    isCompleted: Boolean,
    isExpired: Boolean,
    highlightTaskId: String?,
    onTaskClick: (String) -> Unit,
    onHighlightFinished: (() -> Unit)? = null
) {
    // Preserve overall ordering by walking the list and emitting a header
    // each time the trigger changes.
    var lastTriggerId: String? = null
    for ((task, trigger) in tasks) {
        if (trigger.id != lastTriggerId) {
            item(key = "header-${trigger.id}-${task.id}") {
                TriggerGroupHeader(trigger.title)
            }
            lastTriggerId = trigger.id
        }
        item(key = task.id) {
            TaskRow(
                task = task,
                trigger = trigger,
                isCompleted = isCompleted,
                isExpired = isExpired,
                isHighlighted = task.id == highlightTaskId,
                onClick = { onTaskClick(task.id) },
                onHighlightFinished = onHighlightFinished
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    trigger: Trigger,
    isCompleted: Boolean,
    isExpired: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onHighlightFinished: (() -> Unit)? = null
) {
    // Pulse highlight animation
    val highlightAlpha = remember { Animatable(0f) }

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightAlpha.snapTo(0.6f)
            highlightAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
            )
            onHighlightFinished?.invoke()
        }
    }

    val backgroundColor = when {
        isHighlighted && highlightAlpha.value > 0f ->
            MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha.value)
        else -> Color.Transparent
    }

    val textColor = when {
        isExpired -> ExpiredGray
        isCompleted -> CompletedGreen
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Disable the 40dp min interactive target so checkbox + row collapse
        // to the visual checkbox size; the whole row is the click target anyway.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = CompletedGreen,
                    uncheckedColor = if (isExpired) ExpiredGray else MaterialTheme.colorScheme.onSurface
                )
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}
