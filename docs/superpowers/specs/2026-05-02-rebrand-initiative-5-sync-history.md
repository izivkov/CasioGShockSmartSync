# Initiative #5 — Sync History (last 25)

> Sub-spec under the umbrella roadmap `docs/superpowers/specs/2026-05-02-rebrand-overhaul-roadmap.md`. Lands on top of Initiatives #1 (already merged on this branch as commits `e120f7c6` + `2dbb6506`), #2, #3, and #4. Assumes the alarms screen with footer chips (#2) and the `AutoSyncCoordinator` + `WatchButtonDispatcher` (#4) and `applyHourlySignalToWatch` helper (#3) are in place. After acceptance, the executable plan is produced via `superpowers:writing-plans`.

## Context

By the time #5 lands, four sync paths have accreted: manual `Send time` (TimeViewModel), manual `Send alarms` (AlarmViewModel), manual `Send events` (EventViewModel), the new `Set time on watch` Settings row (#4), and two automatic chains — `AutoSyncCoordinator` (4×/day) and `WeeklyAlarmScheduler.applyTodaySchedule` (on watch reconnect). #2 also needs a durable record of "what was in the last setAlarms payload" to drive the A+ fallback's T−5 sync-recency check; today that lives in a transient `_lastSync` map and a single DataStore key (`AlarmFallbackLastSync`).

Initiative #5 unifies all of this into a single per-watch ring-buffer of the last 25 sync events. Each entry captures **what was synced**, **how it was triggered**, **whether it succeeded**, and a **compact details payload** that's enough for the A+ fallback and useful for user-facing troubleshooting. The buffer is surfaced as a compact monospace "log lines" screen reachable from Settings and from the Alarms-tab footer chip.

## Confirmed decisions (from brainstorm)

| Topic | Decision |
|-------|----------|
| Failed-sync entries | **Included.** Each entry has a `success: Boolean` and an optional `errorMessage`; failed rows render in red. |
| Per-watch keying | **From day one.** Storage key is `SyncHistory_<address>`. Replaces #2's single `AlarmFallbackLastSync` key. |
| Display style | **Compact monospace 'log lines'**, one row per entry. |
| Entry points | **Two**: a Settings row (`Sync history`) and the always-visible footer chip on the Alarms screen (now tappable). |
| Row tap | **Expand inline** to show the details payload (alarm hashes, event count, extras). Tap again to collapse. |
| Clear history | **Yes** — a `Clear history` action in the TopAppBar overflow menu, with a confirmation dialog. |
| Multi-watch view | Show only the **currently-selected watch's buffer**. Header reads `Sync history — <watch name>`. Watch switching happens via #6's UI; this screen reacts. |
| Capacity | **25 entries** per watch. Standard ring-buffer eviction (oldest dropped on insert past cap). |
| Storage encoding | **JSON-encoded list in a single key** per watch, using `org.json.JSONArray` (matches `LocalDataStorage.toJsonObject` precedent). |

## Goals

1. One durable record of every sync, queryable by the screen and by #2's A+ fallback.
2. Survive process death (DataStore-backed; the receiver in #2 reads the same key).
3. Make troubleshooting "did my alarm reach the watch?" answerable in three taps from any tab.
4. Don't introduce Room or any new persistence framework.

## Non-goals

- Cloud sync / cross-device history. Strictly local, per-watch.
- Searching / filtering the log beyond the natural 25-entry cap.
- Per-row "re-run" actions (no "retry sync" button on a failed line). Out of scope.
- Per-row swipe-to-delete. The cap + Clear-all is enough.
- Aggregating events from before #5 lands — first-launch on the new build starts with an empty buffer.

---

## 1. Data model

```kotlin
// data/local/SyncEntry.kt   (new)

enum class SyncTrigger {
    AUTO,                   // AutoSyncCoordinator's 4×/day chain
    AUTO_ON_RECONNECT,      // WeeklyAlarmScheduler.applyTodaySchedule
    MANUAL_SET_TIME,        // TimeViewModel manual "Send time"
    MANUAL_SEND_ALARMS,     // AlarmViewModel manual "Send to watch"
    MANUAL_SEND_EVENTS,     // EventViewModel manual "Send events to watch"
    MANUAL_SETTINGS_SET_TIME, // #4's Set-time-on-watch Settings row
}

enum class SyncKind { TIME, ALARMS, EVENTS, HOURLY_SIGNAL }

data class SyncEntry(
    val timestamp: Long,           // epoch ms
    val deviceAddress: String,     // BLE MAC; redundant with key but lets the data class travel
    val trigger: SyncTrigger,
    val kinds: Set<SyncKind>,      // 1..4 per entry
    val success: Boolean,
    val errorMessage: String?,     // null on success
    val details: SyncDetails,      // empty by default; per-kind populated
)

data class SyncDetails(
    val alarmHashes: Set<String>?,    // null unless ALARMS in kinds
    val eventCount: Int?,             // null unless EVENTS in kinds
    val hourlySignalTarget: Boolean?, // null unless HOURLY_SIGNAL in kinds
    val timeMs: Long?,                // null unless TIME in kinds; the value sent to setTime()
) {
    companion object {
        val Empty = SyncDetails(null, null, null, null)
    }
}
```

Notes:

- `alarmHashes` uses the same shape as #2's `_lastSync.sentAlarmHashes` (`"$hour:$minute:$dayMask:$enabled"`), so the A+ fallback receiver reads it identically.
- `kinds` is a set, not a single value, because `AutoSyncCoordinator` runs `time + (events?) + hourlySignal` in one chain — that's one entry, not three.
- `errorMessage` is short (≤200 chars). Long stack traces aren't useful in a 25-entry buffer.

---

## 2. Storage — `SyncHistoryStore`

### 2.1 File and key shape

```kotlin
// data/local/SyncHistoryStore.kt   (new)

@Singleton
class SyncHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun load(deviceAddress: String): List<SyncEntry> {
        val raw = LocalDataStorage.get(context, key(deviceAddress), null) ?: return emptyList()
        return parse(raw)
    }

    suspend fun append(entry: SyncEntry) {
        val current = load(entry.deviceAddress).toMutableList()
        current.add(0, entry)                      // newest-first
        while (current.size > CAPACITY) current.removeAt(current.lastIndex)
        LocalDataStorage.put(context, key(entry.deviceAddress), serialize(current))
    }

    suspend fun clear(deviceAddress: String) {
        LocalDataStorage.delete(context, key(deviceAddress))
    }

    fun observe(deviceAddress: String): Flow<List<SyncEntry>> = …  // see §2.3

    private fun key(address: String) = "SyncHistory_$address"
    private companion object { const val CAPACITY = 25 }
}
```

Key naming `SyncHistory_<address>` aligns with the existing `DeviceName_<address>` pattern at `LocalDataStorage.kt:147`. Address is the BLE MAC string.

### 2.2 JSON encoding

Use `org.json.JSONArray` and `JSONObject` directly (already imported in `LocalDataStorage.kt:16`). No `kotlinx.serialization` dependency added.

```kotlin
private fun serialize(entries: List<SyncEntry>): String {
    val arr = JSONArray()
    entries.forEach { e ->
        arr.put(JSONObject().apply {
            put("ts", e.timestamp)
            put("addr", e.deviceAddress)
            put("trig", e.trigger.name)
            put("kinds", JSONArray(e.kinds.map { it.name }))
            put("ok", e.success)
            e.errorMessage?.let { put("err", it.take(200)) }
            put("d", JSONObject().apply {
                e.details.alarmHashes?.let { put("aH", JSONArray(it.toList())) }
                e.details.eventCount?.let { put("eC", it) }
                e.details.hourlySignalTarget?.let { put("hT", it) }
                e.details.timeMs?.let { put("tM", it) }
            })
        })
    }
    return arr.toString()
}
```

Compact field names (`ts`, `addr`, `trig`, `aH`, `tM`, …) keep the JSON small for 25 entries. With ~20 alarm hashes per entry, expected total payload < 8 KB per watch.

### 2.3 Reactive reads

The screen needs to react to new entries. `LocalDataStorage` is built on `androidx.datastore.preferences`; it natively exposes a `Flow<Preferences>`. Add a `getFlow(context, key)` helper if not present, or use a `MutableStateFlow<Map<address, List<SyncEntry>>>` inside `SyncHistoryStore` that re-emits on every `append` / `clear`. Simpler:

```kotlin
private val _changes = MutableStateFlow(0L)  // bumped on every mutation

fun observe(deviceAddress: String): Flow<List<SyncEntry>> =
    _changes.map { load(deviceAddress) }.distinctUntilChanged()
```

Trade-off: re-reads from DataStore on every observation tick, but the buffer is tiny (≤25 entries). Acceptable.

---

## 3. Logger API (`SyncHistoryLogger`)

A thin façade that the call-sites use without thinking about JSON or eviction:

```kotlin
// utils/SyncHistoryLogger.kt   (new)

@Singleton
class SyncHistoryLogger @Inject constructor(
    private val store: SyncHistoryStore,
    private val watchSelection: CurrentWatchSelector,   // gives currentDeviceAddress
) {
    suspend fun record(
        trigger: SyncTrigger,
        kinds: Set<SyncKind>,
        success: Boolean,
        errorMessage: String? = null,
        details: SyncDetails = SyncDetails.Empty,
    ) {
        val addr = watchSelection.currentAddress() ?: return  // no watch → no log
        store.append(
            SyncEntry(
                timestamp = System.currentTimeMillis(),
                deviceAddress = addr,
                trigger = trigger,
                kinds = kinds,
                success = success,
                errorMessage = errorMessage?.take(200),
                details = details,
            )
        )
    }
}
```

Failure rule: callers wrap their `api.set…(…)` in `runCatching { … }` and call `logger.record(success = result.isSuccess, errorMessage = result.exceptionOrNull()?.message, …)`. Existing call-sites already use `runCatching` (e.g. `WeeklyAlarmScheduler.kt:43, 57`) so the shape lines up.

`CurrentWatchSelector` is a placeholder for #6's "currently-selected watch" abstraction. For #5 we can stub it as `LocalDataStorage.get(context, "LastDeviceAddress", null)` — see §10 for how this evolves.

---

## 4. Hooks at every sync site

For each call-site, the change is **identical pattern**: wrap the existing API call in `runCatching`, then `logger.record(...)`. No restructuring.

### 4.1 `TimeViewModel.kt:87` — manual `Send time`

```kotlin
// before
api.setTime(timeMs = …)

// after
val result = runCatching { api.setTime(timeMs = timeMs) }
logger.record(
    trigger = SyncTrigger.MANUAL_SET_TIME,
    kinds = setOf(SyncKind.TIME),
    success = result.isSuccess,
    errorMessage = result.exceptionOrNull()?.message,
    details = SyncDetails(timeMs = timeMs),
)
result.exceptionOrNull()?.let { AppSnackbar(it.message ?: "Api Error") }
```

### 4.2 `AlarmViewModel.sendAlarmsToWatch()` (~line 189)

After the existing `api.setAlarms(...)`, build the `alarmHashes` set the same way #2 does (`"$hour:$minute:$dayMask:$enabled"` per alarm) and:

```kotlin
val hashes = alarmsToSend.map { hashOf(it) }.toSet()
logger.record(
    trigger = SyncTrigger.MANUAL_SEND_ALARMS,
    kinds = setOf(SyncKind.ALARMS),
    success = result.isSuccess,
    errorMessage = result.exceptionOrNull()?.message,
    details = SyncDetails(alarmHashes = hashes),
)
```

The transient `_lastSync` map from #2 is replaced by `store.load(addr).firstOrNull { SyncKind.ALARMS in it.kinds && it.success }?.details?.alarmHashes`. See §6.

### 4.3 `EventViewModel.sendEventsToWatch()` (~line 105)

```kotlin
logger.record(
    trigger = SyncTrigger.MANUAL_SEND_EVENTS,
    kinds = setOf(SyncKind.EVENTS),
    success = result.isSuccess,
    errorMessage = result.exceptionOrNull()?.message,
    details = SyncDetails(eventCount = sanitizedEvents.size),
)
```

### 4.4 `WeeklyAlarmScheduler.applyTodaySchedule()` line 57

Same pattern as 4.2, but `trigger = SyncTrigger.AUTO_ON_RECONNECT`. The `runCatching` wrapper at line 57 already exists; just add the `logger.record(...)` after it.

### 4.5 #4's `SetTimeRow` `setTimeNow()` in `SettingsViewModel`

Same pattern as 4.1 but `trigger = SyncTrigger.MANUAL_SETTINGS_SET_TIME`.

### 4.6 #4's `AutoSyncCoordinator.runChain()`

The chain runs setTime → setEvents (conditional) → applyHourlySignalToWatch → optional notification. We log **one entry per full chain**, capturing all kinds that ran:

```kotlin
suspend fun runChain() {
    val kinds = mutableSetOf<SyncKind>()
    val details = mutableMapOf<String, Any?>()
    var success = true
    var error: String? = null
    runCatching {
        // step 1
        val timeMs = System.currentTimeMillis() + LocalDataStorage.getFineTimeAdjustment(context)
        api.setTime(timeMs = timeMs)
        kinds += SyncKind.TIME
        details["timeMs"] = timeMs

        // step 2
        if (WatchInfo.hasReminders) {
            api.setEvents(EventsModel.events)
            kinds += SyncKind.EVENTS
            details["eventCount"] = EventsModel.events.size
        }

        // step 3
        val target = HourlySignalEvaluator.shouldChimeAt(LocalDateTime.now(), HourlySignalSettings.load(context))
        applyHourlySignalToWatch(api, settings, now)
        kinds += SyncKind.HOURLY_SIGNAL
        details["hourlySignalTarget"] = target
    }.onFailure { e ->
        success = false
        error = e.message
    }
    logger.record(
        trigger = SyncTrigger.AUTO,
        kinds = kinds,
        success = success,
        errorMessage = error,
        details = SyncDetails(
            timeMs = details["timeMs"] as? Long,
            eventCount = details["eventCount"] as? Int,
            hourlySignalTarget = details["hourlySignalTarget"] as? Boolean,
        ),
    )
    // optional notification step (unchanged from #4)
}
```

Partial-success semantics: if step 1 succeeds but step 2 throws, the entry's `kinds` already contains `TIME` and the `errorMessage` describes the failure of `setEvents`. The user sees "AUTO time ✗ events failed" — useful diagnostics.

### 4.7 What's NOT logged

- Read-only API calls (`api.getAlarms`, `api.getSettings`, `api.getBatteryLevel`) are not syncs; not logged.
- Connection-lifecycle events (pairing, reconnect attempts that don't push state) are not logged here. The `MainEventHandler` and `DeviceAssociationManager` flows are out of scope; their telemetry can go elsewhere.
- The hourly-signal mirror pass into `alarms[0].hasHourlyChime` from #3 is part of the alarm sync step; one entry covers it.

---

## 5. UI

### 5.1 Screen file and route

New file `ui/history/SyncHistoryScreen.kt`. Reachable via:

- A new Compose `NavHost` route `Screens.SyncHistory.route` (added to `Screens.kt` as `data object SyncHistory : Screens("SyncHistory")`).
- Pushed onto the back stack via `navController.navigate(Screens.SyncHistory.route)`. Not a bottom-nav tab.

Top-level composable:

```
┌──────────────────────────────────────────────┐
│  ←  Sync history                       ⋮     │   ← TopAppBar; ⋮ → Clear history
│      GW-B5600BC                              │   ← subtitle: current watch name
├──────────────────────────────────────────────┤
│  2026-05-02 18:42:01  AUTO   time alarms hourly  ✓  │
│  2026-05-02 12:00:01  AUTO   time alarms             ✓  │
│  2026-05-02 09:30:14  MANUAL set-time                ✓  │
│  2026-05-02 06:00:01  AUTO   time alarms             ✗  watch disconnected  │
│  2026-05-02 03:14:55  RECON  alarms                  ✓  │  ← AUTO_ON_RECONNECT abbreviated
│  …                                                                          │
└──────────────────────────────────────────────┘
```

### 5.2 Compact log-line format

```
<YYYY-MM-DD HH:mm:ss>  <TRIGGER_TAG>  <kinds-list>  <result-glyph>  [error-snippet]
```

| Field | Width / format |
|-------|----------------|
| Timestamp | `yyyy-MM-dd HH:mm:ss`, fixed 19 chars |
| Trigger tag | one of `AUTO`, `RECON`, `MANUAL`, padded to 6 chars. (`MANUAL_SET_TIME` etc. → `MANUAL`; the `kinds` field clarifies what kind of manual.) |
| Kinds list | space-separated short names: `time`, `alarms`, `events`, `hourly`. Lower-case for legibility against the upper-case trigger tag. |
| Result glyph | `✓` (success, `colorScheme.primary`) or `✗` (failure, `colorScheme.error`) |
| Error snippet | shown only on failure, ≤60 chars, ellipsised |

Render with `MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)`. Single-line per entry, `softWrap = false, overflow = TextOverflow.Ellipsis`. Pixel 6 portrait (411 dp width @ 16 sp mono ≈ 60 chars) just fits the format above; on narrow screens the trailing error snippet truncates first.

### 5.3 Inline expansion (tap to expand)

State: `expandedEntry: Long?` (the timestamp of the currently-expanded entry, or `null`). Tapping a row sets it; tapping again clears.

When expanded, render below the line:

- For `ALARMS` kind: each alarm hash on its own indented row, monospace.
- For `EVENTS` kind: `event count = N`.
- For `HOURLY_SIGNAL` kind: `hourly signal target = ON` / `OFF`.
- For `TIME` kind: `set time = <human-readable>` (e.g. `set time = 2026-05-02 18:42:01.123 (offset +0 ms)`).
- On failure: full `errorMessage` (untruncated) instead of glyph-only.

Indentation: 4 spaces. Same monospace style. Background: `colorScheme.surfaceVariant.copy(alpha = 0.4f)` to visually group the expansion with its parent line.

### 5.4 Clear history

TopAppBar action: an overflow menu (`MoreVert` icon → DropdownMenu) with one entry `Clear history`. Tapping opens a confirmation dialog:

> **Clear sync history?**
>
> Removes all 25 entries for `<watch name>`. Other watches' history is unaffected.
>
> [ Cancel ]   [ Clear ]

On confirm: `viewModel.clear(deviceAddress)` calls `store.clear(addr)`. Buffer rerenders empty (showing a placeholder "No sync events yet").

### 5.5 Settings row entry point

Add a row to `SettingsScreen.kt`, below the existing rows (after the divider that ends Initiative #4's new top section):

```
┌────────────────────────────────────┐
│  Sync history                    ▸ │
└────────────────────────────────────┘
```

New file `ui/settings/SyncHistoryRow.kt`. Tap navigates `navController.navigate(Screens.SyncHistory.route)`.

### 5.6 Footer-chip entry point (Alarms screen)

#2's footer reads `Last sync: 2h ago • 4 active`. Make the entire `Last sync: …` segment **tappable**, navigating to `Screens.SyncHistory.route`. Visual change: the segment becomes underlined-on-press (use `Modifier.clickable(...)`); no extra chrome.

The `4 active` segment stays non-interactive (it's a count, not a navigation target).

---

## 6. Replacing #2's transient `_lastSync` and `AlarmFallbackLastSync` key

In #2's spec, the A+ fallback receiver reads from a single DataStore key `AlarmFallbackLastSync` to know "did the last successful sync include this alarm?" Initiative #5 makes this naturally fall out of the history store:

```kotlin
// PhoneFallbackReceiver.kt   (modified in #5)
val store = SyncHistoryStore(context)
val lastSuccess = store
    .load(deviceAddress)
    .firstOrNull { it.success && SyncKind.ALARMS in it.kinds }
val watchHasAlarm = lastSuccess?.details?.alarmHashes?.contains(expectedHash) == true
if (watchHasAlarm) return                       // trust the watch
context.sendBroadcast(Intent(AlarmClock.ACTION_SET_ALARM)…) // fire fallback
```

The `AlarmFallbackLastSync` DataStore key is removed (one-line cleanup in `LocalDataStorage` if the helper is gone). The receiver's logic stays one-shot and self-contained.

The VM-side transient `_lastSync` StateFlow in `AlarmViewModel` also goes away — the screen's footer instead reads `store.observe(addr).map { it.firstOrNull() }` for the "last sync time" chip.

---

## 7. Critical files

**New:**
- `app/.../data/local/SyncEntry.kt` — data classes + enums.
- `app/.../data/local/SyncHistoryStore.kt` — DataStore-backed ring-buffer with per-watch keys.
- `app/.../data/local/CurrentWatchSelector.kt` — thin abstraction over "currently-selected device address" (in #5: reads `LocalDataStorage`'s last-device-address; in #6: backed by the new selection state).
- `app/.../utils/SyncHistoryLogger.kt` — façade.
- `app/.../ui/history/SyncHistoryScreen.kt` — log-lines view.
- `app/.../ui/history/SyncHistoryViewModel.kt` — observes the store, holds expansion state, dispatches `clear`.
- `app/.../ui/settings/SyncHistoryRow.kt` — Settings entry point.
- `app/.../di/SyncHistoryModule.kt` — Hilt provides for the Singleton store + logger.

**Modify:**
- `Screens.kt` — add `SyncHistory` route.
- `app/.../GShockApplication.kt` (or wherever the `NavHost` is wired) — add `composable(Screens.SyncHistory.route) { SyncHistoryScreen(...) }`.
- `ui/time/TimeViewModel.kt:87` — wrap `api.setTime` in `runCatching` and call `logger.record(...)`.
- `ui/alarms/AlarmViewModel.kt:~189` — same pattern; build alarm hashes; call `logger.record(...)`. Remove the `_lastSync` StateFlow (replaced by `store.observe(addr)`). Update the footer-chip rendering to read from the store flow.
- `ui/alarms/AlarmsScreen.kt` — make the `Last sync: …` segment of the footer clickable → `navController.navigate(Screens.SyncHistory.route)`.
- `ui/alarms/PhoneFallbackReceiver.kt` (#2 file) — replace the `AlarmFallbackLastSync` lookup with the new `SyncHistoryStore` query (§6).
- `ui/events/EventViewModel.kt:~105` — log entry on send.
- `ui/alarms/WeeklyAlarmScheduler.kt:57` — log entry after the `setAlarms` call. Use existing `runCatching`.
- `ui/settings/SettingsViewModel.kt` (#4) — log entry inside `setTimeNow()`.
- `AutoSyncCoordinator.kt` (#4) — wrap the chain in the partial-success pattern from §4.6.
- `ui/settings/SettingsScreen.kt` — append `SyncHistoryRow(...)`.
- `utils/LocalDataStorage.kt` — remove the `AlarmFallbackLastSync` helper if it was added in #2; otherwise no change.
- `res/values/strings.xml` + 10 locales — new strings: `sync_history_title`, `sync_history_no_entries`, `sync_history_clear`, `sync_history_clear_confirm_title`, `sync_history_clear_confirm_body`, `sync_history_failure_no_watch`, plus the trigger/kind shortlabels (`auto`, `recon`, `manual`, `time`, `alarms`, `events`, `hourly`). The trigger/kind shortlabels could stay English-only by virtue of being technical jargon — confirm during translator review.

**Tests (new):**
- `app/src/test/java/.../data/local/SyncHistoryStoreTest.kt` — round-trip per-watch isolation; ring-buffer eviction at 26 entries; clear() empties only the targeted address; corrupt JSON → empty list and log.
- `app/src/test/java/.../utils/SyncHistoryLoggerTest.kt` — record success / failure / no-watch (no-op when `currentAddress() == null`).
- `app/src/test/java/.../ui/history/SyncHistoryViewModelTest.kt` — observes store; toggle expand; clear flow.
- `app/src/test/java/.../ui/alarms/PhoneFallbackReceiverHistoryTest.kt` — given a SyncHistoryStore with an entry containing the expected alarm hash, the receiver returns without firing; given no such entry, it fires.

---

## 8. Reuse from existing code

- `LocalDataStorage.put / get / delete` — `utils/LocalDataStorage.kt:33-43`.
- `org.json.JSONObject / JSONArray` — already imported in `LocalDataStorage.kt:16`. No new dependency.
- `runCatching { … }` — every modified call-site already follows this pattern (e.g. `WeeklyAlarmScheduler.kt:43, 57`); we just attach the logging call.
- `MaterialTheme.typography.bodySmall` + `FontFamily.Monospace` for the log lines.
- `Spacing` token (#1) — outer padding of the screen and rows.
- `MaterialTheme.colorScheme.error` — the `✗` glyph and any error-snippet color.
- `AppSnackbar` — kept for one-off error feedback (e.g. `Clear history` failed because of a write race).
- `WeeklyAlarmScheduler` already-present `runCatching` blocks at 43 / 57 — no restructuring required, just record after.

---

## 9. Verification

1. **Build/lint/tests green:** `./gradlew ktlint lint test assembleDebug` passes.
2. **First launch on the new build:** open `Settings → Sync history` — screen renders with placeholder "No sync events yet."
3. **Manual sync logging:**
   - Tap `Set time on watch` (Settings, from #4) → exactly one entry appears at the top: `<now> MANUAL time ✓`.
   - Tap `Send to watch` on Alarms tab → entry: `<now> MANUAL alarms ✓`. Tap to expand → list of alarm hashes matches the count of enabled alarms.
   - Tap `Send events to watch` → entry: `<now> MANUAL events ✓`. Expand → `event count = N`.
   - Tap `Send time` (Time tab) → entry: `<now> MANUAL time ✓`. Distinct from the Settings-row entry (different trigger).
4. **Auto sync logging:**
   - Trigger `AUTO_TIME_ADJUSTMENT` (e.g. wait for the watch's reconnect, or use a debug button to fire the chain) → exactly one entry: `<now> AUTO time alarms hourly ✓` (or whatever subset of kinds ran).
   - Watch reconnect after a config change → `<now> RECON alarms ✓` from `WeeklyAlarmScheduler`.
5. **Failure logging:**
   - Disconnect the watch, tap `Set time on watch` → entry `<now> MANUAL time ✗ watch disconnected` rendered in red. Expand → full error message.
   - Force `api.setEvents` to throw (e.g. by populating EventsModel with a malformed value) and trigger an auto-sync → partial-success entry with `kinds = {TIME}`, `success = false`, `errorMessage = "<exception>"`.
6. **Ring-buffer cap:**
   - Trigger 30 manual time-syncs (e.g. via repeated taps). Open Sync history → exactly 25 entries, oldest 5 dropped.
7. **Per-watch isolation:**
   - With two paired watches (post-#6 or simulated by switching the `LastDeviceAddress`), perform syncs on each. Open Sync history while watch A is selected → only A's entries. Switch to B → only B's entries.
8. **Clear history:**
   - Tap overflow → `Clear history` → confirm. Buffer empties for the current watch only. Switch to the other watch — its history is intact.
9. **Footer-chip navigation:**
   - On Alarms tab, tap the `Last sync: …` segment of the footer. App navigates to Sync history. Tap back → returns to Alarms with the same scroll position.
10. **A+ fallback uses the store:**
    - Set a fire-once alarm 7 minutes from now. Disable Bluetooth. Wait. At T−5min the receiver fires `AlarmClock.ACTION_SET_ALARM` (verify via system clock app). The receiver consulted `SyncHistoryStore` to make the decision (not the now-removed `AlarmFallbackLastSync` key — verify by `git grep` post-merge that the old key is gone).
11. **Process death survival:**
    - Kill the app. Reopen. Sync history shows the same entries (DataStore-backed).
12. **Compact format fits:**
    - On a Pixel 4a (narrowest supported) the typical line `2026-05-02 18:42:01  AUTO   time alarms hourly  ✓` fits without ellipsis. Lines with longer kinds-lists or error snippets ellipsise the right side, never the timestamp.

---

## 10. Cross-cutting / dependencies

- **Depends on Initiative #1**: `Spacing`, M3 typography, package id.
- **Depends on Initiative #2**: alarm-hash convention (`"$h:$m:$dayMask:$enabled"`); footer-chip rendering and clickability lives on the Alarms screen.
- **Depends on Initiative #3**: `applyHourlySignalToWatch` is a step in `AutoSyncCoordinator.runChain()` whose result is logged.
- **Depends on Initiative #4**: `AutoSyncCoordinator`, `WatchButtonDispatcher`, the `Set time on watch` Settings row.
- **Heads-up for Initiative #6**: `CurrentWatchSelector` is the seam. In #5 it reads `LastDeviceAddress` from `LocalDataStorage`. In #6 it becomes a `StateFlow<Address>` driven by the new multi-watch selection UI. The `SyncHistoryViewModel` already observes `store.observe(addr)` based on this seam — switching watches will recompose the screen automatically.
- **Independent of Initiative #7**: history records are local; #7's broadcast round-trip can produce its own entries (or not) when it lands.

---

## 11. PR shape

Single PR titled `Initiative #5 — Sync history (last 25)`. Suggested commit split:

1. `refactor: SyncEntry + SyncHistoryStore + ring-buffer encoding (data layer only)` — pure data; no call-site changes; full unit-test coverage.
2. `refactor: SyncHistoryLogger + CurrentWatchSelector seam` — façade and the watch-selection abstraction. No UI yet.
3. `feat: log every sync site (Time, Alarms, Events, WeeklyAlarmScheduler, Set-time row, AutoSyncCoordinator)` — the six call-site hooks. Each commit-level grep should show the buffer growing on every sync.
4. `feat: SyncHistoryScreen with compact log lines + tap-to-expand + Clear history` — the visible UI.
5. `feat: footer-chip on Alarms tab navigates to Sync history; Settings row entry point` — the two entry points wired.
6. `refactor: PhoneFallbackReceiver consults SyncHistoryStore instead of AlarmFallbackLastSync` — replaces #2's transient key.
7. `chore: drop AlarmFallbackLastSync helper from LocalDataStorage` — final cleanup; gated on (6) being merged.

Each commit builds and tests on its own. Commits 1–3 are behaviour-preserving (no UI), 4–7 add and migrate.

---

## 12. Open questions / follow-ups

- **Translator review for trigger / kind shortlabels** — `AUTO`, `RECON`, `MANUAL`, `time`, `alarms`, `events`, `hourly`. Likely English-only is acceptable (these are technical jargon); confirm with the German/French/Polish translators when the strings file lands.
- **Time format locale-awareness** — `yyyy-MM-dd HH:mm:ss` is ISO-ish and unambiguous. For users who strongly prefer 12-hour or `MM/dd/yyyy` we'd add a Settings toggle; out of scope here.
- **Privacy / export** — `Clear history` is the only outbound action. Sharing or exporting the history (e.g. for bug reports) is a future enhancement; if added, gate behind the existing notifications-permission framework so the user understands what's leaving the device.
- **Auto-pruning by age** — currently only the 25-entry cap evicts. If a user uses the app for 6 months without crossing 25 entries, very old entries persist. That's fine; the 25-entry cap doubles as a soft TTL. Revisit if storage growth becomes a concern.
