package com.dailypowders.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
            items(activeTasks, key = { it.first.id }) { (task, trigger) ->
                TaskRow(
                    task = task,
                    trigger = trigger,
                    isCompleted = false,
                    isExpired = false,
                    isHighlighted = task.id == highlightTaskId,
                    onClick = {
                        viewModel.completeTask(task.id)
                    },
                    onHighlightFinished = {
                        viewModel.clearHighlight()
                    }
                )
            }
        }

        // Completed Tasks Section
        if (completedTasks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader("Completed Tasks")
            }
            items(completedTasks, key = { it.first.id }) { (task, trigger) ->
                TaskRow(
                    task = task,
                    trigger = trigger,
                    isCompleted = true,
                    isExpired = false,
                    isHighlighted = false,
                    onClick = {
                        viewModel.uncompleteTask(task.id)
                    }
                )
            }
        }

        // Expired Tasks Section
        if (expiredTasks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader("Expired Tasks")
            }
            items(expiredTasks, key = { it.first.id }) { (task, trigger) ->
                TaskRow(
                    task = task,
                    trigger = trigger,
                    isCompleted = false,
                    isExpired = true,
                    isHighlighted = false,
                    onClick = {
                        viewModel.completeTask(task.id)
                    }
                )
            }
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
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(
                checkedColor = CompletedGreen,
                uncheckedColor = if (isExpired) ExpiredGray else MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}
