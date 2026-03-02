# Daily Powders - Security Review Guidelines

## 1. Threat Model

Daily Powders is an **offline-only** Android app with no network access, no analytics, and no telemetry. The threat model is narrow:

**In Scope:**
- Local data privacy (another app reading our data)
- Intent/PendingIntent spoofing (malicious app triggering our receivers)
- Data corruption (file system issues, race conditions)
- Sensitive data leakage via backups or logs

**Out of Scope:**
- Network attacks (no internet permission)
- Server-side vulnerabilities (no backend)
- Authentication/authorization (single-user local app)

---

## 2. Security Review Checklist

### 2.1 Data Storage and Privacy

**What to check:**
- [ ] Data stored in `noBackupFilesDir` (not `filesDir` or `externalFilesDir`)
- [ ] `android:allowBackup="false"` in manifest
- [ ] `android:fullBackupContent="false"` in manifest
- [ ] No sensitive data in SharedPreferences
- [ ] No sensitive data written to logs (`Log.d`, `Log.e`)
- [ ] No sensitive data in intent extras visible to other apps
- [ ] File permissions are default (app-private)

**What good looks like:**
- All user data in `context.noBackupFilesDir` which is:
  - App-private (only accessible by our app or root)
  - Excluded from cloud backup
  - Encrypted at rest by Android's file-based encryption

**Common mistakes:**
- Using `filesDir` (included in cloud backup by default)
- Using `externalFilesDir` (readable by other apps on older Android versions)
- Logging user data for debugging and forgetting to remove it

### 2.2 Intent and PendingIntent Security

**What to check:**
- [ ] All PendingIntents use `FLAG_IMMUTABLE`
- [ ] BroadcastReceivers are `android:exported="false"`
- [ ] PendingIntent request codes don't collide (verify spacing)
- [ ] No implicit intents for internal communication
- [ ] Activity launch intents use `FLAG_ACTIVITY_SINGLE_TOP` correctly

**What good looks like:**
- `PendingIntent.FLAG_IMMUTABLE` on all PendingIntents (prevents modification by receiving app)
- Explicit intents targeting specific component classes
- Non-exported receivers that can only be triggered by our own app or system alarms

**Common mistakes:**
- Using `FLAG_MUTABLE` without need (allows intent modification)
- Exporting receivers that should be internal
- Using implicit intents for broadcasts (can be intercepted)

### 2.3 BroadcastReceiver Security

**What to check:**
- [ ] All receivers are `exported="false"` except BootReceiver
- [ ] BootReceiver only accepts known actions (BOOT_COMPLETED, MY_PACKAGE_REPLACED, QUICKBOOT_POWERON)
- [ ] All receivers validate intent extras before use
- [ ] No sensitive operations triggered by external intents
- [ ] Receivers handle exceptions gracefully (no crashes on malformed data)

**What good looks like:**
- BootReceiver is `exported="false"` but receives system broadcasts (Android allows this for BOOT_COMPLETED)
- AlarmReceiver and NotificationActionReceiver are non-exported and only triggered by our own PendingIntents
- All extras retrieved with null checks (`getStringExtra(...) ?: return`)

### 2.4 File I/O Safety

**What to check:**
- [ ] Atomic write pattern used (write to tmp, verify, rename)
- [ ] Backup file created before overwrite
- [ ] Corrupted files handled gracefully (fallback to backup, then defaults)
- [ ] File operations are synchronized (no race conditions)
- [ ] No path traversal vulnerabilities in file names
- [ ] Temp files cleaned up on failure

**What good looks like:**
- Write to `.tmp` file, verify JSON round-trips, backup `.bak`, atomic `Files.move`
- `synchronized(LOCK)` on all read/write operations
- `tryParseFile()` returns null on corruption, falls back gracefully

**Common mistakes:**
- Writing directly to the data file (corruption on crash)
- No verification of written data
- Race conditions between UI thread and BroadcastReceivers

### 2.5 Serialization Safety

**What to check:**
- [ ] `ignoreUnknownKeys = true` (forward compatibility)
- [ ] Schema version checked before deserialization
- [ ] `SchemaVersionTooNewException` thrown for unsupported versions
- [ ] No `@Transient` fields hiding security-relevant data
- [ ] `@SerialName` used correctly for Kotlin keyword conflicts

**What good looks like:**
- Known schema version checked first, then migration path applied
- Unknown keys ignored (allows future fields without breaking old versions)
- Clean error for schema versions from the future

### 2.6 Permission Management

**What to check:**
- [ ] Only necessary permissions declared
- [ ] Runtime permissions requested with graceful degradation
- [ ] `SCHEDULE_EXACT_ALARM` falls back to inexact if denied
- [ ] `POST_NOTIFICATIONS` works without notification delivery
- [ ] No dangerous permissions beyond what's needed

**Current permissions (all justified):**
- `POST_NOTIFICATIONS` - core functionality (task reminders)
- `SCHEDULE_EXACT_ALARM` - precise trigger timing
- `RECEIVE_BOOT_COMPLETED` - alarm rescheduling after reboot

### 2.7 Manifest Configuration

**What to check:**
- [ ] `android:allowBackup="false"`
- [ ] `android:fullBackupContent="false"`
- [ ] No unnecessary `exported="true"` components
- [ ] `launchMode="singleTop"` on main activity (prevents task stack exploits)
- [ ] No `android:debuggable="true"` in release builds
- [ ] No unprotected content providers

### 2.8 Build Configuration

**What to check:**
- [ ] ProGuard/R8 enabled for release builds
- [ ] ProGuard rules preserve serialization classes
- [ ] `debuggable false` in release buildType
- [ ] `minifyEnabled true` in release buildType
- [ ] No hardcoded secrets, API keys, or credentials
- [ ] Signing key properly managed (not in repo)

---

## 3. Current Security Posture

### Done Well
- Data stored in `noBackupFilesDir` (no cloud backup)
- `allowBackup=false` and `fullBackupContent=false`
- No internet permission (zero network attack surface)
- All PendingIntents use `FLAG_IMMUTABLE`
- All BroadcastReceivers are non-exported
- Atomic write pattern with backup and verification
- Synchronized file access prevents race conditions
- Schema versioning with forward-compatibility
- No logging of user data

### Areas to Watch
- ProGuard rules for kotlinx.serialization (added, verify in release builds)
- Notification content visible on lock screen (Android default, consider `VISIBILITY_PRIVATE`)
- No input validation on dayResetHour/dayResetMinute beyond time picker
- Task/Trigger IDs use truncated UUIDs (low collision risk but not zero)

---

## 4. Tools and Techniques

### Static Analysis
- **Android Lint**: `./gradlew lint` - checks for common security issues
- **Dependency check**: Review `build.gradle.kts` for known vulnerable dependencies
- **Manifest analysis**: Review exported components, permissions, backup config

### Dynamic Testing
- **ADB**: `adb shell am broadcast` to test receiver behavior with crafted intents
- **File inspection**: `adb shell run-as com.dailypowders ls /data/data/com.dailypowders/no_backup/`
- **Backup test**: `adb backup com.dailypowders` should produce empty/minimal backup
- **Intent testing**: Verify non-exported receivers reject external broadcasts

### Code Review Focus
- Search for `Log.` calls that might leak user data
- Search for `exported="true"` in manifest
- Verify all file paths use `noBackupFilesDir`
- Check PendingIntent flags (`FLAG_IMMUTABLE` everywhere)
- Verify `synchronized` on all repository access paths

---

## 5. Review History

| Date | Reviewer | Version | Findings | Status |
|------|----------|---------|----------|--------|
| 2026-03-02 | Initial build | 1.0.0 | 57 issues found across all layers, all fixed | Complete |

---

## 6. When to Re-Review

Trigger a new security review when:
- Adding network functionality (analytics, crash reporting, sync)
- Adding new permissions
- Adding new exported components
- Changing data storage location or format
- Adding user authentication
- Updating major dependencies (especially serialization library)
