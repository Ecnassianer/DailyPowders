# Daily Powders

A privacy-first Android app for managing daily tasks tied to scheduled or manual triggers. Get notified when it's time to act, check off tasks, and let the day reset automatically — no accounts, no internet, no tracking.

## Features

- **Triggers** — group tasks under a named trigger that fires daily, weekly, monthly, or on demand
- **Scheduled notifications** — exact-time alarms push a notification when a trigger fires, grouped Gmail-style (one group per trigger)
- **Task expiration** — mark tasks with an expiration window; they auto-expire N hours after the trigger fires
- **Manual triggers** — tap to activate any time, with a per-day count tracked
- **Configurable day reset** — the "day" rolls over at a custom time (default 3:33 AM) rather than midnight
- **No internet, no analytics, no cloud backup** — data lives only on your device

## Privacy

Data is stored in `noBackupFilesDir` and never synced to the cloud. The app declares `android:allowBackup="false"`. No network permissions are requested.

## Tech Stack

- Kotlin, Jetpack Compose, MVVM
- `kotlinx.serialization` for JSON persistence
- `AlarmManager` with exact alarms for scheduling
- Min SDK 26, Target SDK 34

## Architecture

```
alarm/          AlarmScheduler, AlarmReceiver, BootReceiver
data/model/     TaskDataFile, Trigger, Task, DailyState
data/repository TaskRepository (atomic write, schema versioning)
domain/         TaskManager (activation, completion, expiration, day reset)
notification/   NotificationHelper, NotificationActionReceiver
ui/screen/      TaskView, ManualTriggers, ViewTriggers, Settings, CreateTrigger, EditTrigger
ui/viewmodel/   TaskViewModel, TriggerViewModel, SettingsViewModel
```

## Building

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"

./gradlew assembleDebug   # build APK
./gradlew test            # run unit tests
```

## Permissions

| Permission | Purpose |
|---|---|
| `POST_NOTIFICATIONS` | Show task notifications |
| `SCHEDULE_EXACT_ALARM` | Fire triggers at precise times |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |

## License

Personal project — not licensed for redistribution.
