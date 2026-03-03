# Daily Powders

When I started working out, my trainer recommended so many daily powders for me to take. Creatine, protein isolate, fiber, multi-vitamins, AGZ. And they all happen at different points in the day! It's a lot to keep track of, so I made the Daily Powders app to keep track of all the little daily tasks that need to get done. No guilt for yesterday, just a quick way to keep track of what's done today, and an automatic reset for tomorrow!

Notifications happen at the right time for each task, and you can complete them right from the notification, no need to open the app.

Daily Powders is a privacy-first Android app for managing daily tasks tied to scheduled or manual triggers. Get notified when it's time to act, check off tasks, and let the day reset automatically — no accounts, no internet, no tracking.

## Features

- **Triggers** — group tasks under a named trigger that fires daily, weekly, monthly, or on demand
- **Scheduled notifications** — exact-time alarms push a notification when a trigger fires, grouped Gmail-style (one group per trigger)
- **Task expiration** — mark tasks with an expiration window; they auto-expire N hours after the trigger fires
- **Manual triggers** — tap to activate any time, with a per-day count tracked
- **Configurable day reset** — the "day" rolls over at a custom time (default 3:33 AM) rather than midnight
- **No internet, no analytics** — data lives on your device (backed up via Android Auto Backup if enabled)

## Privacy

Data is stored locally on-device. The app allows Android Auto Backup of task data only (`tasks.json`). No network permissions are requested.

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
./gradlew assembleDebug   # build APK
./gradlew test            # run unit tests
```

Requires `JAVA_HOME` and `ANDROID_HOME` to be set.

## Permissions

| Permission | Purpose |
|---|---|
| `POST_NOTIFICATIONS` | Show task notifications |
| `SCHEDULE_EXACT_ALARM` | Fire triggers at precise times |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |

## License

Personal project — not licensed for redistribution.
