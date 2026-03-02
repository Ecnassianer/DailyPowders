# Daily Powders

## Project Info
- **Repo:** https://github.com/Ecnassianer/DailyPowders
- **GitHub account for Claude:** AgenticAI3909404902
- **Local path:** C:/Users/Shadow/Projects/DailyPowders

## Stack
- Kotlin, Jetpack Compose, MVVM
- Min SDK 26, Target SDK 34, Compile SDK 36
- kotlinx.serialization for JSON data
- AlarmManager for exact notification scheduling
- Gradle with Kotlin DSL
- Git via SSH (key at C:/Users/Shadow/.ssh/id_ed25519_github)

## Build
```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="C:/Users/Shadow/AppData/Local/Android/Sdk"
cd C:/Users/Shadow/Projects/DailyPowders
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
- Data stored at `noBackupFilesDir/tasks.json` (never backed up to cloud)
- `android:allowBackup="false"` in manifest
- No internet, no analytics, no telemetry
- Permissions: POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED
- Notification groups: one group per trigger (Gmail-style expand/collapse)
