# Initiative #2 — Alarms Overhaul

> Sub-spec under the umbrella roadmap `docs/superpowers/specs/2026-05-02-rebrand-overhaul-roadmap.md`. Lands on top of Initiative #1's visual refresh (`2026-05-02-rebrand-initiative-1-visual-refresh.md`) — assumes the new `Spacing` token, M3 typography migration, and `com.beamburst.casswatch` package id are already in. After acceptance, the executable plan is produced via `superpowers:writing-plans`.

## Context

The fork's alarm screen has accumulated foundations on this branch (`feature/app-size-and-alarms`): a Simple/Weekly mode toggle, per-alarm day-of-week selection (`AlarmSchedulePlanner`, `AlarmSyncStorage`), DataStore persistence for drafts and view mode. What it lacks is the polish: tap-to-edit instead of inline editing, zero-padded times, a "fire once" semantic for one-day-only alarms, an offline state that doesn't dead-end the user, and a footer that tells you when the watch was last synced. Initiative #2 is purely about the alarms screen UX (no broadcast intents, no multi-watch list, no actions tab work) — a self-contained UX win.

## Confirmed decisions (from brainstorm)

| Topic | Decision |
|-------|----------|
| Editor surface | **ModalBottomSheet** with drag handle, M3 native; replaces all inline editing |
| Fire-once truth model | Phone-side `enabled` flag stays ON until a sync confirms the watch has the disabled state; a `firedAt` timestamp is the derived "consumed" signal |
| Phone fallback | **Approach A+** — AlarmManager-at-T−5 + sync-recency check; if no recent sync, fire `AlarmClock.ACTION_SET_ALARM` so the system clock app rings |
| Disconnected UX | Info card replaces "Send to watch"; footer chips remain; "Send to phone" stays available |
| Footer chips | **Always visible** (connected and disconnected) |
| Fired-state UI | Toggle visually flips OFF at fire-time; underlying `enabled` flag still ON until sync confirms |
| Permission ask | `SCHEDULE_EXACT_ALARM` prompted **on first fire-once alarm enable**, in-context |
| Hour formatting | Always zero-padded (`09:15`, not `9:15`) |
| Mode rename | **Simple → Daily** |
| Per-alarm name fallback | Stop showing the literal word "daily" in the Weekly view; show empty (or day summary) instead |
| Sync-history infra | **Transient in-memory** for #2; persisted via Initiative #5 later. No premature persistence work here. |

## Goals

1. Make the alarm row tappable (one tap → modal editor) instead of poking individual fields.
2. Add a real "fire once" semantic that doesn't double-fire and doesn't silently miss.
3. Tell the user what the sync state is (footer chips), so they trust the screen.
4. Keep the screen useful when the watch is offline.

## Non-goals

- Sync-history persistence or the "Sync history" screen — Initiative #5.
- Hourly Signal card — Initiative #3.
- Multi-watch ribbons or per-watch alarms — Initiative #6.
- Replacing GShockAPI's `setAlarms` payload shape — out of scope; we work with what exists.
- An in-app ringer / fullscreen alarm UI — explicitly rejected (we use the system clock app for fallback).

---

## 1. State model changes

### 1.1 New truth fields per alarm

Today, `AlarmSyncStorage.StoredAlarm` (`AlarmSyncStorage.kt:18-24`) carries `hour, minute, enabled, hasHourlyChime, name`. Add one persistent + one ephemeral field:

```kotlin
data class StoredAlarm(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val hasHourlyChime: Boolean,
    val name: String?,
    val firedAt: Long? = null,    // NEW — non-null after fire-time passes; cleared on confirming sync
)
```

`firedAt` is **persisted** so an app restart between fire and next sync doesn't lose the consumed-state.

### 1.2 New transient sync metadata

Add an in-memory map on `AlarmViewModel`:

```kotlin
data class SyncRecord(val syncedAt: Long, val sentAlarmHashes: Set<String>)
private val _lastSync = MutableStateFlow<SyncRecord?>(null)
```

`SyncRecord` is set every time `api.setAlarms(...)` succeeds (`AlarmViewModel.kt:189`, `WeeklyAlarmScheduler.kt:57`). `sentAlarmHashes` is `"$hour:$minute:$dayMask:$enabled"` per alarm — the `enabled` bit matters because it lets the A+ fallback distinguish "watch has this alarm enabled" from "watch has it disabled." Used by the receiver at T−5 to verify "the watch's current state matches the truth."

> **Persistence**: in-memory in the VM, plus a **single DataStore key** (`"AlarmFallbackLastSync"`) so the BroadcastReceiver in §4 can read it when the VM is dead. Initiative #5 replaces this single key with a ring-buffer of the last 25 sync events; the VM's public shape stays the same so #5 is a drop-in. Mark the swap site with `// TODO(initiative-5): persist as ring buffer`.

### 1.3 Derived UI state

```kotlin
val showAsEnabled = alarm.enabled && alarm.firedAt == null
```

Exactly one boolean drives the toggle visual; the truth model stays clean.

---

## 2. Modal editor (ModalBottomSheet)

### 2.1 Trigger

Tap anywhere on an `AlarmItem` row → `_uiEvent.emit(UiEvent.OpenEditor(index))` → screen-level `ModalBottomSheet` opens with the alarm's current values. Inline edits in `AlarmItem.kt` (the clickable `time` Dialog at lines 102, 128–147) are removed.

### 2.2 Layout

```
┌─────────────────────────────────┐
│            ─ ─                  │   ← drag handle
│  Edit alarm                     │   ← titleLarge
│                                 │
│        06 : 30                  │   ← M3 TimePicker (display mode)
│                                 │
│  Label                          │
│  ┌───────────────────────────┐  │
│  │ Wake-up                   │  │   ← OutlinedTextField, single line
│  └───────────────────────────┘  │
│                                 │
│  Repeat                         │
│  ( Mo )( Tu )(•We)( Th )(•Fr )  │   ← M3 FilterChips, multi-select
│  ( Sa )( Su )                   │
│                                 │
│        [ Cancel ]  [ Save ]     │
└─────────────────────────────────┘
```

- **Time picker**: `androidx.compose.material3.TimePicker` in display mode. Always shows `HH` zero-padded (M3 default behavior; we just don't override).
- **Label**: `OutlinedTextField`, single-line, defaults to existing alarm name; placeholder = empty string. No automatic "daily" fallback any more (see §6.3).
- **Repeat chips**: `FilterChip` row (M3) — selected = filled with `colorScheme.primary`. **Visible only in Weekly mode.** In Daily mode the chips are hidden; the alarm fires daily by definition.
- **Validation**:
  - Daily mode: no extra rules; saving any combination is fine.
  - Weekly mode + 0 days selected → fire-once. We **don't block save**; we just route the saved alarm through the fire-once flow (§3) when the user enables it.
- **Save** dispatches `Action.UpsertAlarm(index, hour, minute, label, days)`; the VM updates `_alarms`, `_alarmDays`, marks dirty via `AlarmSyncStorage.saveAlarms(..., dirty = true)`, persists `_alarmDays` via `AlarmSyncStorage.saveDaySelections(...)`. Sheet dismisses.
- **Cancel** dismisses without changes.

### 2.3 Composable structure

- New file `ui/alarms/AlarmEditorSheet.kt` — the sheet content, parameter object `AlarmDraft(index, hour, minute, label, days, mode)`.
- `AlarmsScreen.kt` owns one `ModalBottomSheet` host driven by `viewModel.editorTarget: StateFlow<AlarmDraft?>`. Null → sheet hidden; non-null → sheet shown.
- `AlarmItem.kt` becomes display-only: tap on row emits `Action.OpenEditor(index)`. The inline `Dialog` at lines 128–147 and the in-row `AppSwitch` for enabled stay (toggle still works in-place from the row — only fields with multiple values move into the sheet).

---

## 3. Fire-once flow

### 3.1 State machine

```
        ┌──────────────────────────────────────────────────────────┐
        │ user enables a Weekly alarm with 0 days selected         │
        └────────────────────────────┬─────────────────────────────┘
                                     │
                          enabled = true, firedAt = null
                                     │
                ┌────────────────────┼────────────────────┐
                │                                         │
        sync to watch (next                        schedule AlarmManager
         sendAlarmsToWatch or                      wake at fireInstant − 5min
         WeeklyAlarmScheduler)                          │
                │                                         │
        watch payload includes alarm with                 │
         all-days mask, enabled = true                    │
                │                                         │
                ▼                                         ▼
        watch fires at fireInstant                AlarmManager fires at T−5
                                                          │
                                                  read _lastSync; was alarm
                                                  in sentAlarmHashes after enable?
                                                  ├── yes → do nothing (trust watch)
                                                  └── no  → AlarmClock.ACTION_SET_ALARM
                                                              for fireInstant
                                                              (system clock rings)
                                     │
                                     ▼
                          fireInstant passes
                                     │
                          firedAt = now()  ← phone-side
                          (UI: toggle flips OFF visually, enabled still ON)
                                     │
                            next watch sync
                                     │
                  app sends payload with this alarm enabled = false
                  on success → enabled = false, firedAt = null
                                     │
                                     ▼
                              clean state
```

### 3.2 Watch payload rules (`AlarmSchedulePlanner.kt`)

Currently the planner returns alarms unchanged when `selectedDays.isNullOrEmpty()` in Weekly mode (`AlarmSchedulePlanner.kt:22`). New rule:

- Daily mode: alarm goes to watch with all-days mask, `enabled` from truth.
- Weekly mode + non-empty days: alarm goes with selected-days mask, `enabled` from truth.
- Weekly mode + empty days + `enabled=true` + `firedAt=null` → **fire-once unfired**: send with all-days mask, `enabled=true`. Watch will fire at next occurrence.
- Weekly mode + empty days + `enabled=true` + `firedAt!=null` → **fire-once consumed**: send with `enabled=false`. Watch won't fire again.
- Weekly mode + empty days + `enabled=false` → send disabled.

After every successful `api.setAlarms(...)`, post-process in the VM: any alarm with non-null `firedAt` that was just sent disabled gets `firedAt = null` AND `enabled = false`. This is the "confirming sync" clear from option 2.

### 3.3 fire-time tick

A coroutine in `AlarmViewModel.init` runs every 60s while the screen is visible:

```kotlin
// pseudo
viewModelScope.launch {
    while (isActive) {
        val now = System.currentTimeMillis()
        _alarms.update { list ->
            list.map { alarm ->
                val isFireOnce = alarm.enabled && _alarmDays.value[index].isNullOrEmpty()
                                 && _viewMode.value == WEEKLY
                if (isFireOnce && alarm.firedAt == null && nextFireInstant(alarm) <= now) {
                    alarm.copy(firedAt = now)
                } else alarm
            }
        }
        delay(60_000)
    }
}
```

`nextFireInstant(alarm)` computes the upcoming wall-clock time for `alarm.hour:alarm.minute` (today if still in the future, tomorrow otherwise). The 60s granularity is fine for visual flip; precision matters only for the AlarmManager wake (which uses millisecond-exact scheduling).

---

## 4. Phone-side fallback (Approach A+)

### 4.1 New components

- `AlarmManager` registration: `ui/alarms/PhoneFallbackScheduler.kt` — singleton, injected with `@ApplicationContext`. API:
  - `schedule(alarm, fireInstant)` → registers an exact alarm at `fireInstant - 5*60_000`. Stores PendingIntent request code = `alarmIndex`. Idempotent (cancels prior).
  - `cancel(alarmIndex)` → cancels.
  - `cancelAll()` → cancels all known.
- New BroadcastReceiver: `ui/alarms/PhoneFallbackReceiver.kt`, registered in manifest with `android:exported="false"`. On receive:
  1. Read alarmIndex + the alarm's expected hash from extras (passed in at scheduling time, so the receiver doesn't need access to live VM state).
  2. Read the latest `SyncRecord` from a DataStore key `"AlarmFallbackLastSync"` (JSON-encoded). This key is updated by the VM on every successful `api.setAlarms`. The receiver reads it from disk because it can run when VM is dead.
  3. If `SyncRecord.sentAlarmHashes` contains the expected hash → return (trust the watch; the hash carries `hour:minute:dayMask:enabled` so we know the watch has the right state).
  4. Else → fire `Intent(AlarmClock.ACTION_SET_ALARM)` with `EXTRA_HOUR`, `EXTRA_MINUTES`, `EXTRA_MESSAGE = alarm.name ?: ""`, `EXTRA_SKIP_UI = true`, `FLAG_ACTIVITY_NEW_TASK`. The system clock registers and rings at fire-time.
- New manifest permission: `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />`.
- Manifest receiver:
  ```xml
  <receiver
      android:name=".ui.alarms.PhoneFallbackReceiver"
      android:exported="false" />
  ```

### 4.2 Permission flow (in-context)

When the user enables an alarm and the planner determines it's fire-once **for the first time** (i.e., the permission was never granted before), `AlarmViewModel` emits `UiEvent.RequestExactAlarmPermission`. `AlarmsScreen` collects and shows an `AlertDialog`:

> **Phone-side safety net**
>
> If your watch isn't connected when the alarm should ring, the phone can fire it via your default clock app. This needs permission to schedule exact alarms.
>
> [ Not now ]   [ Open settings ]

"Open settings" launches `Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)` (Android 12+). If the user denies / dismisses, the alarm still saves but `PhoneFallbackScheduler.schedule(...)` becomes a no-op and the row shows a small `WarningOutlined` icon with caption "Watch only — phone fallback disabled" tapping which re-opens the dialog. Graceful degradation.

### 4.3 Cancel/reschedule rules

- Editing the time of a fire-once alarm → cancel + reschedule.
- Disabling a fire-once alarm → cancel.
- Switching mode away from Weekly → cancel all fire-once schedules (Daily-mode alarms aren't fire-once).
- Successful sync that clears `firedAt` → cancel (already fired and cleared, no need to wake).
- Successful sync that included the alarm → leave the schedule (T−5 will check sync-recency at fire time and skip; no harm).

---

## 5. Footer + chip ribbon

```
Last sync: 2h ago  •  4 active  •  1 disabled
```

- **Last sync**: time-ago format ("just now", "5m ago", "2h ago", "yesterday", or `MMM d` for older). Source: `_lastSync.value?.syncedAt` or `null` → "Never".
- **Active / disabled chips**: counted from `_alarms`. Active = `showAsEnabled` (so a fire-once that's already fired counts as not-active).
- **No interaction in #2.** Initiative #5 will make them tap-to-history.

Footer is a simple `Row` with `bodyMedium` text + bullet separators. Always rendered, both states. Uses `colorScheme.onSurfaceVariant` for the muted look.

---

## 6. Smaller polish items

### 6.1 Zero-padded hours

Single point of change. In `AlarmItem.kt` (line 100ish, where the time text renders), use `String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute)` — **always**, regardless of system 12/24-hour preference. The watch is a 24-hour device and the existing screen already shows 24h; we're just plugging the leading-zero hole. (12h-format support is out of scope for #2.)

### 6.2 Simple → Daily

- `R.string.alarm_mode_simple` → rename string id to `alarm_mode_daily`, value `"Daily"`. Update all 10 locales' translations (use the existing translation for "daily" if present; else flag for translator review — list of locales: see `app/src/main/res/values-*` directories).
- `AlarmViewMode.SIMPLE` enum value → keep as `SIMPLE` internally to avoid a breaking change in the DataStore-persisted string value (`AlarmSyncStorage.kt:67-70` reads enum name). Add a comment noting the user-visible label is now "Daily."

### 6.3 Stop showing "daily" as alarm name fallback

Today `AlarmItem.kt` (around line 105-106) shows `alarm.name ?: getString(R.string.daily)` — so any unnamed alarm displays the localized word "daily." Remove the fallback: `alarm.name ?: ""`. With an empty label the row just shows the time + day chips, which is correct in Weekly mode and unambiguous in Daily mode.

The new modal editor's label field also has no auto-fill — empty stays empty.

---

## 7. Critical files

**Modify (this initiative):**
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmViewModel.kt` — add `firedAt` handling, `_lastSync`, fire-time coroutine, modal-editor actions, fallback scheduler integration, permission UiEvent.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmSyncStorage.kt` — extend `StoredAlarm` with `firedAt`. Bump JSON schema; tolerate missing field on read (treat as null) for forward-migration.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmSchedulePlanner.kt` — implement the §3.2 watch-payload rules.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmsScreen.kt` — add `ModalBottomSheet`, footer, info card on disconnect, conditional "Send to watch" button.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmItem.kt` — strip inline edit, make whole row tappable, drop "daily" fallback name, zero-pad hour.
- `app/src/main/java/org/avmedia/gshockGoogleSync/utils/WeeklyAlarmScheduler.kt:57` — record `_lastSync` after successful `api.setAlarms`.
- `app/src/main/AndroidManifest.xml` — add `SCHEDULE_EXACT_ALARM` permission, register `PhoneFallbackReceiver`.
- `app/src/main/res/values/strings.xml` and `values-*/strings.xml` (10 locales) — rename `alarm_mode_simple` → `alarm_mode_daily`; add new strings for editor sheet, info card, footer chips, permission dialog.

**New (this initiative):**
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmEditorSheet.kt` — the ModalBottomSheet content.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/PhoneFallbackScheduler.kt` — AlarmManager wrapper.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/PhoneFallbackReceiver.kt` — BroadcastReceiver.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmsFooter.kt` — chip-ribbon row.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/DisconnectedInfoCard.kt` — the "Watch not connected" card.

**Tests (new):**
- `app/src/test/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmSchedulePlannerTest.kt` — table-driven test for the §3.2 rules (Daily / Weekly + days / Weekly + empty + unfired / Weekly + empty + fired).
- `app/src/test/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmFireOnceStateMachineTest.kt` — covers the toggle/firedAt/sync-clear cycle.

---

## 8. Reuse from existing code

- `AlarmSyncStorage` — DataStore plumbing (already JSON-encoded). Just add the new field.
- `AlarmSchedulePlanner` — keep the per-day filtering math; layer the §3.2 rules on top.
- `WeeklyAlarmScheduler` — extend its existing `applyTodaySchedule` flow; doesn't need restructuring.
- `AlarmClock.ACTION_SET_ALARM` — pattern from `AlarmViewModel.kt:218` (already used by "Send to phone"). Same intent, same extras, same `preventReconnection()` discipline.
- `LocalDataStorage.put/get` — DataStore wrapper used for the `AlarmFallbackLastSync` key.
- `ProgressEvents.runEventActions(Utils.AppHashCode(), …)` — for the receiver's hand-off back to the VM if the screen is alive.
- `MaterialTheme.typography` and `Spacing` token from Initiative #1 — every new composable uses these. No raw sp/dp.

---

## 9. Verification

End-to-end checks for the implementation phase:

1. **Build/lint/tests green:** `./gradlew ktlint lint test assembleDebug` passes.
2. **Modal editor:**
   - Tap an alarm row → ModalBottomSheet slides up with current values.
   - Change time, label, days; press Save → row updates; sheet dismisses; `_alarms` and `_alarmDays` persist (kill-and-relaunch retains the change).
   - Cancel → no change.
   - Tapping outside the sheet dismisses without saving.
3. **Zero-pad hours:** `06:30`, `09:15`, `18:05` always show two digits each; never `6:30`.
4. **Mode rename:** segmented toggle reads "Daily | Weekly". 10 locales updated.
5. **Empty name in Weekly mode:** an alarm with `name=null` shows the time + day chips and no extraneous text.
6. **Fire-once flow (happy path, watch online):**
   - Set an alarm 2 minutes from now in Weekly mode with no days, enable it.
   - Sync to watch.
   - Wait for fire-time. Watch rings (verify on device). Toggle visually flips OFF immediately at fire-time.
   - Trigger another sync. Toggle stays OFF; underlying `enabled` is now `false`; `firedAt` cleared. Repeat doesn't re-fire.
7. **Fire-once flow (fallback path, watch offline):**
   - Disable Bluetooth on phone OR put watch out of range.
   - Set fire-once alarm 7 minutes from now.
   - Wait. At T−5min the AlarmManager wake should fire `AlarmClock.ACTION_SET_ALARM` (verify in adb logs or in the system clock app's list).
   - At fire-time, system clock app rings. Toggle flips OFF visually.
   - Reconnect watch. Trigger sync. Toggle stays OFF; underlying `enabled=false`; watch payload sent disabled. Repeat doesn't re-fire on the watch.
8. **Permission denial:**
   - Reset app data. Set a fire-once alarm. The permission dialog appears.
   - Tap "Not now" or deny in system settings. Alarm saves with a small "Watch only" warning icon. AlarmManager schedule is a no-op.
   - Tap the warning icon → re-opens the dialog. Grant. Subsequent fire-once alarms schedule correctly.
9. **Disconnected UX:**
   - Disconnect watch. Alarms tab shows the info card; "Send to watch" button is gone; "Send to phone" button still works; footer reads "Never" or "Last sync: …".
10. **Footer chips:**
    - Connected, never synced: "Last sync: Never".
    - After successful sync: counts match (active = enabled-and-not-yet-fired; disabled = disabled).
    - After a fire-once alarm fires: active count drops by 1 immediately at fire-time even before next sync.
11. **Schema-tolerant load:** Install an older build's DataStore (without the `firedAt` field), upgrade, verify alarms load correctly with `firedAt = null`.
12. **No raw sp/dp leftovers:** `git grep -nE 'fontSize\s*=\s*[0-9]+\.sp|\.dp\)' app/src/main/java/com/beamburst/casswatch/ui/alarms` returns only intentional exceptions.

---

## 10. Cross-cutting / dependencies

- **Depends on Initiative #1**: `Spacing` token, M3 typography migration, deep-blue primary, package id `com.beamburst.casswatch`. All new files in #2 use these.
- **Coupling to Initiative #5 (sync history)**: `_lastSync` is in-memory + a single DataStore key in #2. When #5 lands, replace the single-key with a ring buffer; the public VM API stays the same. A `// TODO(initiative-5): persist as ring buffer` comment marks the swap site.
- **Independent of Initiative #3 (hourly signal)**: separate card on the same screen; no interaction.
- **Independent of Initiative #4 (actions removal)**: alarms tab doesn't depend on `ui/actions/`.

---

## 11. PR shape

Single PR titled `Initiative #2 — Alarms overhaul (modal editor, fire-once, footer)`. Recommended commit split:

1. `refactor: extend AlarmSyncStorage and AlarmSchedulePlanner for fire-once truth model` — schema + planner rules + tests. No UI changes.
2. `feat: ModalBottomSheet alarm editor with M3 TimePicker + day chips` — replaces inline editing.
3. `feat: phone-side fallback (Approach A+) for fire-once alarms` — AlarmManager + receiver + permission flow.
4. `feat: alarms screen footer chips and disconnected info card` — footer + DisconnectedInfoCard + info-only state.
5. `chore: rename Simple→Daily, drop "daily" name fallback, zero-pad hours` — small string + format changes.

Each commit builds and tests on its own.

---

## 12. Open questions deferred to later initiatives

- **Per-watch alarms** (Initiative #6): when multi-watch lands, each watch's alarm list is independent. The footer's "active" count becomes per-watch. Out of scope here.
- **Historical sync record durability** (Initiative #5): replace transient `_lastSync` with a ring-buffer in DataStore.
- **In-app ringer (Approach B/C)**: a future enhancement if A+'s system-clock fallback turns out to be unreliable on some ROMs. Track as a separate initiative; not on the roadmap.
- **12-hour time format**: alarm display is 24h-only today; supporting 12h is a future polish item.
