# Daily Powders

## Project Info
- **Repo:** https://github.com/Ecnassianer/DailyPowders

## Stack
- Kotlin, Jetpack Compose, MVVM
- Min SDK 26, Target SDK 34, Compile SDK 36
- kotlinx.serialization for JSON data
- AlarmManager for exact notification scheduling
- Gradle with Kotlin DSL

## Build
```bash
./gradlew assembleDebug   # Build APK
./gradlew test            # Run unit tests
```

## Architecture
- `data/model/` — @Serializable data classes (TaskDataFile, Trigger, Task, DailyState)
- `data/repository/` — TaskRepository with atomic write, schema versioning
- `domain/` — TaskManager (activation, completion, expiration, day reset)
- `alarm/` — AlarmScheduler, AlarmReceiver, BootReceiver
- `notification/` — NotificationHelper, NotificationActionReceiver
- `ui/viewmodel/` — TaskViewModel, TriggerViewModel, SettingsViewModel
- `ui/screen/` — Compose screens (TaskView, ManualTriggers, ViewTriggers, Settings, CreateTrigger, EditTrigger)

## Key Design Decisions
- Data stored at `filesDir/tasks.json`, backed up via Android Auto Backup
- `android:allowBackup="true"` with backup rules scoped to `tasks.json` only
- No internet, no analytics, no telemetry
- Permissions: POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED
- Notification groups: one group per trigger (Gmail-style expand/collapse)
