# Daily Powders - Deployment Test Plan

## Deployment: Shadow PC to Test Phone via ADB over Tailscale

### Prerequisites

- Shadow PC and test phone (Jelly Star) both connected to the same Tailscale network
- USB Debugging enabled on the phone (Settings > Developer Options > USB Debugging)
- Wireless Debugging enabled on the phone (Settings > Developer Options > Wireless Debugging)
- ADB installed on Shadow PC (included with Android SDK at `C:/Users/Shadow/AppData/Local/Android/Sdk/platform-tools/`)

### Network Info

| Device | Tailscale IP |
|--------|-------------|
| Shadow PC | 100.88.128.73 |
| Jelly Star | 100.73.229.94 |

### Step-by-Step Deployment

#### 1. Build the APK

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
cd C:/Users/Shadow/Projects/DailyPowders
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

#### 2. Enable ADB over TCP on the phone

On the Jelly Star:
1. Go to **Settings > Developer Options > Wireless Debugging** and toggle it ON
2. Tap **Wireless Debugging** to open its settings
3. Tap **Pair device with pairing code** - note the pairing code, IP, and port

#### 3. Pair with the phone (first time only)

```bash
# Use the pairing port shown on the phone's Wireless Debugging screen
adb pair 100.73.229.94:<pairing-port>
# Enter the pairing code when prompted
```

#### 4. Connect via ADB over Tailscale

```bash
# Use the connection port shown on the Wireless Debugging main screen
# (this is different from the pairing port)
adb connect 100.73.229.94:<connect-port>

# Verify connection
adb devices
# Should show: 100.73.229.94:<port>   device
```

#### 5. Install the APK

```bash
# Fresh install (or overwrite existing)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# If there's a signature mismatch from a previous install:
adb uninstall com.dailypowders
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### 6. Launch the app

```bash
adb shell am start -n com.dailypowders/.MainActivity
```

#### 7. Grant permissions

On the phone when prompted:
- **Allow notifications** (POST_NOTIFICATIONS) - tap Allow
- **Exact alarms** - if prompted, toggle on for Daily Powders in system settings

Or grant via ADB:
```bash
# Notification permission (API 33+)
adb shell pm grant com.dailypowders android.permission.POST_NOTIFICATIONS

# Exact alarm permission must be granted through system UI
# Open the settings page directly:
adb shell am start -a android.settings.REQUEST_SCHEDULE_EXACT_ALARM -d "package:com.dailypowders"
```

### Quick Redeploy (after code changes)

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
cd C:/Users/Shadow/Projects/DailyPowders
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Troubleshooting

| Issue | Fix |
|-------|-----|
| `adb devices` shows nothing | Ensure Wireless Debugging is ON, re-pair if needed |
| Connection refused | Check Tailscale is connected on both devices (`tailscale status`) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall old version first: `adb uninstall com.dailypowders` |
| `INSTALL_FAILED_USER_RESTRICTED` | Enable "Install via USB" in Developer Options |
| Phone disconnects after sleep | Re-run `adb connect 100.73.229.94:<port>` |
| Slow transfer | Tailscale uses relay if no direct connection; check `tailscale ping jelly-star` |

### Viewing Logs

```bash
# All app logs
adb logcat --pid=$(adb shell pidof com.dailypowders) 2>/dev/null

# Filter to just app tags
adb logcat -s AlarmReceiver:* NotificationHelper:* TaskRepository:* DailyPowders:*

# Clear log buffer and start fresh
adb logcat -c && adb logcat --pid=$(adb shell pidof com.dailypowders)
```

---

## Pre-Testing Checklist

- [ ] `./gradlew assembleDebug` passes with no errors
- [ ] `./gradlew test` passes all unit tests
- [ ] APK signed (debug or release)
- [ ] ADB connected to phone over Tailscale
- [ ] APK installed via `adb install`
- [ ] Notifications permission granted
- [ ] Exact alarm permission granted

---

## 1. First Launch

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| FL-1 | Fresh install | Install APK on clean device | App opens to Tasks tab, empty state | |
| FL-2 | Notification permission | Launch app on API 33+ | Permission dialog appears | |
| FL-3 | Exact alarm permission | Launch app on API 31+ | Redirected to exact alarm settings | |
| FL-4 | Data file created | Create a trigger then check `noBackupFilesDir` | tasks.json exists | |

## 2. Trigger Creation

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| TC-1 | Create daily trigger | Triggers tab > + > Daily, 08:00, add task "Test", Save | Trigger appears in list | |
| TC-2 | Create weekly trigger | + > Weekly, Monday, 09:00, add task, Save | Trigger with "Weekly on Monday at 09:00" | |
| TC-3 | Create monthly trigger | + > Monthly, day 15, 10:00, add task, Save | Trigger with "Monthly on the 15th at 10:00" | |
| TC-4 | Create manual trigger | + > Manually, add task, Save | Trigger appears in Activate tab | |
| TC-5 | Multiple tasks | Create trigger with 3 tasks | All 3 tasks visible when activated | |
| TC-6 | "=" button | Enter trigger title, click = on task row | Task title copies from trigger title | |
| TC-7 | Blank task filtered | Create trigger, leave one task title blank | Only non-blank tasks saved | |
| TC-8 | Immediate activation | Create daily trigger with time in the past | Trigger activates immediately, tasks appear | |

## 3. Trigger Editing

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| TE-1 | Edit title | Edit trigger, change title, save | Title updated in list | |
| TE-2 | Add task | Edit trigger, add new task, save | New task appears, original tasks keep their IDs | |
| TE-3 | Remove task | Edit trigger, delete a task, save | Task removed, other task IDs preserved | |
| TE-4 | Change schedule | Edit from Daily to Weekly, save | Alarm rescheduled correctly | |
| TE-5 | Delete trigger | Triggers tab > delete icon > confirm | Trigger removed, alarms cancelled, notifications cleared | |

## 4. Manual Triggers

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| MT-1 | Activate | Activate tab > tap manual trigger | Tasks appear in Tasks tab, notifications posted | |
| MT-2 | Activation count | Activate same trigger twice | Count shows "Activated 2 times" | |
| MT-3 | Complete task | Complete a task from manual trigger | Moves to Completed section | |

## 5. Task View

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| TV-1 | Active tasks | Activate trigger with tasks | Tasks appear in Active section | |
| TV-2 | Complete task | Tap checkbox on active task | Moves to Completed section | |
| TV-3 | Uncomplete task | Tap checkbox on completed task | Returns to Active (or Expired if past expiration) | |
| TV-4 | Expired tasks | Wait for task to expire | Moves to Expired section automatically | |
| TV-5 | Empty state | No triggers activated | Shows empty state message | |

## 6. Notifications

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| N-1 | Notification posted | Trigger fires at scheduled time | Notification appears with task title | |
| N-2 | Done! action | Tap Done! on notification | Notification dismissed, task marked complete | |
| N-3 | Snooze 30 | Tap Snooze 30 on notification | Notification dismissed, re-appears after 30 min | |
| N-4 | Snooze 60 | Tap Snooze 60 on notification | Notification dismissed, re-appears after 60 min | |
| N-5 | Tap notification | Tap notification body | App opens to Tasks tab with task highlighted | |
| N-6 | Group per trigger | Trigger with 3 tasks fires | 3 notifications grouped under trigger name | |
| N-7 | Group summary tap | Tap group summary notification | App opens to Tasks tab | |
| N-8 | Multiple triggers | Two triggers fire | Each has its own notification group | |

## 7. Notification Defense Layers

| ID | Layer | Test | Steps | Expected | Pass |
|----|-------|------|-------|----------|------|
| DL-1 | L1: Deterministic IDs | Same trigger fires twice | Notification updated, not duplicated | |
| DL-2 | L2: Cancel on state change | Complete task from app UI | Notification dismissed | |
| DL-3 | L3: setTimeoutAfter | Task with 1hr expiration | Notification auto-dismisses after ~1 hour | |
| DL-4 | L4: State check before post | Complete task, then snooze fires | No notification re-posted | |
| DL-5 | L5: Cancel snooze on complete | Complete task with pending snooze | Snooze alarm cancelled, no re-post | |
| DL-6 | L6: Day reset sweep | Wait for day reset time | All notifications cleared | |
| DL-7 | L7: Immediate dismiss on action | Tap any notification action | Notification dismisses instantly | |
| DL-8 | L8: App launch cleanup | Kill app, launch with stale notifications | Stale notifications cleaned up | |

## 8. Day Reset

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| DR-1 | Day reset fires | Wait for configured reset time | All tasks cleared, notifications dismissed | |
| DR-2 | Change reset time | Settings > change day reset time | New reset time takes effect | |
| DR-3 | Before reset = previous day | Check task state at 3:00 AM (reset at 3:33) | Tasks from previous day still active | |
| DR-4 | After reset = new day | Check task state at 3:34 AM | Previous day's tasks cleared | |

## 9. Device Events

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| DE-1 | Device reboot | Reboot device with active triggers | Alarms rescheduled, active notifications re-posted | |
| DE-2 | App update | Install updated APK | Alarms rescheduled (MY_PACKAGE_REPLACED) | |
| DE-3 | Force stop | Force stop app | Alarms cleared (expected). Next launch reschedules | |
| DE-4 | Battery optimization | Enable aggressive battery optimization | Alarms may be delayed (document behavior) | |
| DE-5 | App resume | Background app, bring to foreground | Data refreshed, shows current state | |

## 10. Data Integrity

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| DI-1 | Atomic write | Create/complete tasks rapidly | No data corruption in tasks.json | |
| DI-2 | Backup file | Check noBackupFilesDir after several saves | tasks.json.bak exists as backup | |
| DI-3 | Corrupt recovery | Manually corrupt tasks.json | App falls back to .bak file | |
| DI-4 | No cloud backup | Check device backup settings | App data not included in cloud backup | |
| DI-5 | Concurrent access | Tap Done on notification while using app | Both actions apply correctly | |

## 11. Edge Cases

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| EC-1 | DST forward | Trigger near DST spring-forward boundary | Trigger fires at correct wall-clock time | |
| EC-2 | DST backward | Trigger near DST fall-back boundary | Trigger fires once, not twice | |
| EC-3 | Timezone change | Change device timezone | Triggers adjust to new timezone | |
| EC-4 | Midnight trigger | Trigger at 00:00 with dayReset at 3:33 | Fires at midnight, within "previous" effective day | |
| EC-5 | Many triggers | Create 20+ triggers with tasks | App performs well, no ANR | |
| EC-6 | Permission revoked | Revoke notification permission | App works without crashes, tasks still tracked | |
| EC-7 | Exact alarm revoked | Revoke exact alarm permission | Falls back to inexact alarms | |

## 12. Settings

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| S-1 | View day reset time | Open Settings tab | Shows current day reset time (default 03:33) | |
| S-2 | Change day reset time | Pick new time, confirm | Time updated, alarms rescheduled | |

## 13. Navigation

| ID | Test | Steps | Expected | Pass |
|----|------|-------|----------|------|
| NAV-1 | Tab switching | Tap each bottom tab | Correct screen displayed | |
| NAV-2 | Create trigger flow | + button > fill form > Save | Returns to Triggers tab | |
| NAV-3 | Edit trigger flow | Tap trigger > edit > Save | Returns to Triggers tab | |
| NAV-4 | Bottom bar hidden | Navigate to create/edit trigger | Bottom nav bar hidden | |
| NAV-5 | Back navigation | System back from create screen | Returns without saving | |

## Compatibility Notes

- **Minimum API**: 26 (Android 8.0)
- **Target API**: 34 (Android 14)
- **Test on**: Pixel (stock Android), Samsung (One UI), Xiaomi (MIUI) if possible
- **Critical OEM differences**: Notification grouping behavior, battery optimization aggressiveness
