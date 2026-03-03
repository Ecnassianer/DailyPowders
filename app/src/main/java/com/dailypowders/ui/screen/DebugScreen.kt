package com.dailypowders.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailypowders.ui.viewmodel.TaskViewModel

@Composable
fun DebugScreen(viewModel: TaskViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Debug",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = { viewModel.fireTestNotification() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Notifications")
        }
    }
}
