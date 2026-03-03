package com.dailypowders.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dailypowders.ui.screen.*
import com.dailypowders.ui.viewmodel.TaskViewModel
import com.dailypowders.ui.viewmodel.TriggerViewModel
import com.dailypowders.ui.viewmodel.SettingsViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object TaskView : Screen("task_view", "Tasks", Icons.Default.CheckCircle)
    data object ManualTriggers : Screen("manual_triggers", "Activate", Icons.Default.PlayArrow)
    data object ViewTriggers : Screen("view_triggers", "Triggers", Icons.AutoMirrored.Filled.List)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object Debug : Screen("debug", "Debug", Icons.Default.Build)
}

val baseNavItems = listOf(
    Screen.TaskView,
    Screen.ManualTriggers,
    Screen.ViewTriggers,
    Screen.Settings
)

@Composable
fun DailyPowdersApp(
    highlightTaskIdState: MutableState<String?> = mutableStateOf(null),
    taskViewModel: TaskViewModel = viewModel(),
    triggerViewModel: TriggerViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val navController = rememberNavController()
    val highlightTaskId by highlightTaskIdState
    val debugEnabled by settingsViewModel.debugFeaturesEnabled.collectAsState()
    val triggers by triggerViewModel.triggers.collectAsState()

    // Build nav items dynamically based on debug state
    val bottomNavItems = if (debugEnabled) baseNavItems + Screen.Debug else baseNavItems

    // Load data on first composition
    LaunchedEffect(Unit) {
        taskViewModel.loadData()
        triggerViewModel.loadData()
        settingsViewModel.loadData()
    }

    // Reload data when app resumes from background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                taskViewModel.loadData()
                triggerViewModel.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle notification highlight navigation
    LaunchedEffect(highlightTaskId) {
        if (highlightTaskId != null) {
            taskViewModel.setHighlightTask(highlightTaskId!!)
            taskViewModel.loadData()
            navController.navigate(Screen.TaskView.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // Clear the highlight source so it doesn't re-trigger
            highlightTaskIdState.value = null
        }
    }

    // Determine current route for hiding bottom bar on create/edit screens
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.route } || currentRoute == null

    // FAB menu state
    var showFabMenu by remember { mutableStateOf(false) }
    // Trigger selection dialog state
    var showTriggerSelector by remember { mutableStateOf(false) }
    var triggerSearchQuery by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            if (showBottomBar) {
                Box {
                    FloatingActionButton(
                        onClick = { showFabMenu = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Trigger") },
                            onClick = {
                                showFabMenu = false
                                navController.navigate("create_trigger")
                            }
                        )
                        if (triggers.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("New Task For Existing Trigger") },
                                onClick = {
                                    showFabMenu = false
                                    triggerSearchQuery = ""
                                    showTriggerSelector = true
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.TaskView.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.TaskView.route) {
                LaunchedEffect(Unit) { taskViewModel.loadData() }
                TaskViewScreen(viewModel = taskViewModel)
            }
            composable(Screen.ManualTriggers.route) {
                LaunchedEffect(Unit) { triggerViewModel.loadData() }
                ManualTriggersScreen(viewModel = triggerViewModel)
            }
            composable(Screen.ViewTriggers.route) {
                LaunchedEffect(Unit) { triggerViewModel.loadData() }
                ViewTriggersScreen(
                    viewModel = triggerViewModel,
                    onEditTrigger = { triggerId ->
                        navController.navigate("edit_trigger/$triggerId")
                    }
                )
            }
            composable(Screen.Settings.route) {
                LaunchedEffect(Unit) { settingsViewModel.loadData() }
                SettingsScreen(viewModel = settingsViewModel)
            }
            composable(Screen.Debug.route) {
                DebugScreen(viewModel = taskViewModel)
            }
            composable("create_trigger") {
                CreateTriggerScreen(
                    viewModel = triggerViewModel,
                    onDone = {
                        navController.popBackStack()
                        taskViewModel.loadData()
                    }
                )
            }
            composable("edit_trigger/{triggerId}") { backStackEntry ->
                val triggerId = backStackEntry.arguments?.getString("triggerId") ?: ""
                EditTriggerScreen(
                    triggerId = triggerId,
                    viewModel = triggerViewModel,
                    onDone = {
                        navController.popBackStack()
                        taskViewModel.loadData()
                    }
                )
            }
        }
    }

    // Trigger selection dialog for "New Task For Existing Trigger"
    if (showTriggerSelector) {
        val filteredTriggers = if (triggerSearchQuery.isBlank()) {
            triggers
        } else {
            triggers.filter { it.title.contains(triggerSearchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showTriggerSelector = false },
            title = { Text("Select a Trigger") },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = triggerSearchQuery,
                        onValueChange = { triggerSearchQuery = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.padding(4.dp)
                    )
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(filteredTriggers.size) { index ->
                            val trigger = filteredTriggers[index]
                            val displayName = trigger.title.ifBlank {
                                "${trigger.happensEvery.name.lowercase().replaceFirstChar { it.uppercase() }} trigger"
                            }
                            TextButton(
                                onClick = {
                                    showTriggerSelector = false
                                    navController.navigate("edit_trigger/${trigger.id}")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = displayName,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTriggerSelector = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
