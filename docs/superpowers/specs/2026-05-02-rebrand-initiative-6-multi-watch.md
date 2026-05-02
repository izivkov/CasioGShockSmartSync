# Initiative #6 — Multi-Watch Management on Home

> Sub-spec under the umbrella roadmap `docs/superpowers/specs/2026-05-02-rebrand-overhaul-roadmap.md`. Lands on top of Initiatives #1 (already merged), #2, #3, #4, and #5. Replaces the single-watch UI on the Time tab with a per-row paired-watches list. Largest data-model shift of the rebrand: introduces a real "currently-selected watch" state machine and migrates a handful of globally-keyed prefs into per-watch namespaces. After acceptance, the executable plan is produced via `superpowers:writing-plans`.

## Context

The app already supports multiple paired devices in storage (`LocalDataStorage.getDeviceAddresses()`, per-address `DeviceName_<addr>` keys, CDM associations) but the UI exposes only one watch at a time via `WatchNameView` + `WatchSummaryCard`. Users with two or three watches today have to use the system Bluetooth menu to switch — and even then, the app's cached state (alarms, hourly-signal, sync history) is global, so switching to a different watch shows the wrong data.

Initiative #6 delivers two things:

1. **A list-driven UI on the Time tab** with one row per paired watch, a green/red dot, a ⋮ menu (Connect / Disconnect / Forget), and an `+ Add watch` button.
2. **Per-watch persistence of all watch-bound state** — alarms, view mode, day selections, hourly-signal settings, sync history, watch-side settings (locale/light/font/operationTone/fine-time/power-savings) — keyed by BLE address.

The user explicitly highlighted a subtlety: "some settings can be read from the watch if not stored, for example light, power saving etc — has to be smart." The spec encodes this as a **dirty-flag-based reconciliation** that already has precedent in `AlarmSyncStorage` (the `PhoneAlarmDraftsDirty` flag).

## Confirmed decisions (from brainstorm)

| Topic | Decision |
|-------|----------|
| List placement | **Replace `WatchSummaryCard` on the Time tab** with a per-row paired-watches list + `+ Add watch` button. |
| Tap on a row | **Select only.** Connection state is not changed. User explicitly Connects via the row's ⋮ menu. |
| Per-watch state | **Alarms, watch-settings (Locale, Light, Font, OperationTone, fine-time, power-savings), hourly-signal settings, sync history** (#5's keys already are per-watch). |
| Settings reconciliation | **Dirty-flag based.** On connect: if cache is dirty → push to watch + clear dirty; else → read from watch into cache. |
| Forget cleanup | **Wipe everything for that address** (alarms, settings, hourly-signal, sync history). Confirmation dialog warns. |
| Migration on first launch | **Copy globals to `<key>_<LastDeviceAddress>` namespace, delete globals, gate on `MultiWatchMigrated` boolean.** One-shot. |
| Add-watch entry point | **Reuse the existing `viewModel.associateWithUi(...)` CDM flow.** `WatchConnectionDialog` is deleted; the list's `+ Add watch` button calls the same flow. |
| Single-active-watch invariant | **Yes** — Casio BLE supports one connection at a time. Connecting watch B implicitly disconnects watch A. |

## Goals

1. Show every paired watch on a single screen with at-a-glance connection status.
2. Make watch-switching deterministic: select a row, see that watch's cached state, optionally connect.
3. Stop leaking state across watches (alarms set on watch A no longer appear when watch B is selected).
4. Establish `CurrentWatchSelector` as the single source of truth that all VMs read.
5. Don't break the existing CDM pairing path — reuse `PreConnectionViewModel.associateWithUi(...)` and `disassociate(...)`.

## Non-goals

- Concurrent multi-watch connections (BLE / Casio firmware doesn't support it).
- Per-watch app-level UI preferences (the user's choice of theme, app-icon, etc., stays global — those are *app* settings, not *watch* settings).
- A separate "Watches" bottom-nav tab (decided against; the list lives on Time).
- Live battery / temperature for non-connected watches (we show the last-cached values with a `cached` tag; live values require a connection).
- Cloud sync of per-watch state. Strictly local.

---

## 1. Selection state — `CurrentWatchSelector` becomes real

In #5 this was a stub backed by `LocalDataStorage.get(context, "LastDeviceAddress", null)`. In #6 it becomes a proper Hilt singleton:

```kotlin
// data/local/CurrentWatchSelector.kt   (now: real implementation)

@Singleton
class CurrentWatchSelector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _selected = MutableStateFlow(LocalDataStorage.get(context, KEY, null))
    val selected: StateFlow<String?> = _selected.asStateFlow()

    fun currentAddress(): String? = _selected.value

    suspend fun select(address: String) {
        _selected.value = address
        LocalDataStorage.put(context, KEY, address)
    }

    suspend fun clear() {
        _selected.value = null
        LocalDataStorage.delete(context, KEY)
    }

    private companion object { const val KEY = "LastDeviceAddress" }
}
```

Reuses the existing `LastDeviceAddress` key for backward compatibility with `PreConnectionViewModel.kt:116, 171, 197, 199` (which already reads it). All VMs that need to know "which watch is selected" inject `CurrentWatchSelector` and `collectAsState()` on its flow.

### 1.1 Recomposition contract

When `selected` emits a new address:

- `AlarmViewModel` reloads alarms / view mode / day selections from the new address's per-watch keys.
- `EventViewModel` keeps its events list (events are phone-side calendar data; not per-watch).
- `SettingsViewModel` reloads watch-settings (locale, light, font, etc.) from the new address.
- `SyncHistoryViewModel` already observes `store.observe(address)`; no change beyond passing the new address.
- `TimeViewModel`'s `WatchSummaryCard`-equivalent rendering (now per-row in the list) updates in place.

This is achieved via a single pattern in each VM: declare the relevant flows as `selectedFlow.flatMapLatest { addr -> /* load per-addr */ }`. No manual "on watch switch, reload" coordination — the flow graph handles it.

---

## 2. The paired-watches list (`PairedWatchList`)

### 2.1 Composable tree on the Time tab (post-#6)

```
TimeScreen
  ├─ ScreenTitle("time")
  ├─ LocalTimeView                              [unchanged]
  ├─ TimerView                                  [unchanged]
  └─ PairedWatchList                            [NEW — replaces WatchSummaryCard / WatchNameView / WatchInfoView]
       ├─ row(s) per paired watch
       │    ┌─────────────────────────────────────────┐
       │    │ ●  GW-B5600BC      87% 21°C        ⋮    │   ← currently connected
       │    │ ○  DW-H5600       (cached: 64%, 19°C)⋮  │   ← not connected
       │    │ ○  GA-2110                          ⋮   │
       │    └─────────────────────────────────────────┘
       └─ + Add watch                                       [NEW button]
```

### 2.2 Per-row composable (`WatchRow`)

```
┌──────────────────────────────────────────────┐
│  ●   GW-B5600BC                          ⋮   │   ← title row (titleMedium)
│      87% • 21°C • Tokyo 18:42                │   ← subtitle (bodyMedium, onSurfaceVariant)
└──────────────────────────────────────────────┘
```

- **Dot:** `●` for currently-connected (`colorScheme.primary` — deep-blue), `◌` (or smaller) for connecting, `○` for disconnected. Static. Updated reactively via `ProgressEvents` (see §4).
- **Selected indicator:** the *currently-selected* row gets a light tonal background (`colorScheme.surfaceVariant.copy(alpha = 0.6f)`) regardless of connection state. Selected ≠ connected.
- **Tap on row body** (excluding the ⋮ icon's hit-target): `viewModel.select(address)` → `CurrentWatchSelector.select(addr)`. UI of all tabs updates within one frame.
- **⋮ menu**: a `DropdownMenu` with items based on row state:
  - Currently connected: **Disconnect**, **Forget**
  - Connecting: **Cancel** (only)
  - Disconnected: **Connect**, **Forget**

### 2.3 Subtitle content rules

| Watch state | Subtitle |
|-------------|----------|
| Connected | live `{battery}% • {temp}°C • Home {homeTime}` (last value from `api.get*()`) |
| Disconnected, has cache from prior connection | `cached: {battery}% • {temp}°C` (greyed) |
| Disconnected, never connected (just paired) | `not yet synced` |
| Connecting | `connecting…` |
| Connection error | `connection error` (single short error caption) |

The "cached" values come from `WatchSnapshot_<address>` keys (new) — written on every successful `WatchInitializationCompleted` for that watch. Two fields: `battery: Int`, `temp: Int`, `cachedAt: Long`. Tiny payload.

### 2.4 The `+ Add watch` button

Rendered as the last "row" of the list (full-width, `OutlinedButton` style with a `+` icon). Tapping calls `preConnectionViewModel.associateWithUi(context, delegate)` — the same path `WatchConnectionDialog.kt:185-202` uses today. The OS CDM device chooser appears; on bond complete, `LocalDataStorage.setDeviceName(context, addr, name)` persists, and the new watch appears as a new row.

### 2.5 Empty state (zero watches paired)

```
┌──────────────────────────────────────┐
│   No watches paired yet.             │
│                                      │
│   [   + Add watch   ]                │
└──────────────────────────────────────┘
```

Single column, helper text + the same `+ Add watch` action. No selection state is set; `CurrentWatchSelector.selected.value == null`. All other tabs that depend on a selection show a "no watch selected" placeholder until the user pairs one.

---

## 3. Per-watch state migration (`MultiWatchMigrator`)

### 3.1 What migrates

| Storage area | Today's key | New key |
|--------------|-------------|---------|
| Alarms drafts | `PhoneAlarmDrafts` | `PhoneAlarmDrafts_<addr>` |
| Alarm dirty flag | `PhoneAlarmDraftsDirty` | `PhoneAlarmDraftsDirty_<addr>` |
| Alarm view mode | `AlarmViewMode` | `AlarmViewMode_<addr>` |
| Alarm day selections | `AlarmDaySelections` | `AlarmDaySelections_<addr>` |
| Hourly-signal enabled | `HourlySignalEnabled` (#3) | `HourlySignalEnabled_<addr>` |
| Hourly-signal start hour | `HourlySignalStartHour` (#3) | `HourlySignalStartHour_<addr>` |
| Hourly-signal end hour | `HourlySignalEndHour` (#3) | `HourlySignalEndHour_<addr>` |
| Hourly-signal migrated flag | `HourlySignalMigrated` (#3) | `HourlySignalMigrated_<addr>` |
| Watch-settings (each: `light`, `font`, `operationTone`, `fineTimeAdjustment`, `powerSavings`, etc.) | various global keys | `<key>_<addr>` |

`SyncHistory_<addr>` is already per-watch from #5. `LastDeviceAddress` and `LastDeviceName` stay global (they're the selection state itself). `DeviceAddresses` and `DeviceName_<addr>` are unchanged.

### 3.2 Migration steps

```kotlin
// data/local/MultiWatchMigrator.kt   (new)

class MultiWatchMigrator(private val context: Context) {
    fun migrateIfNeeded() {
        if (LocalDataStorage.getBoolean(context, "MultiWatchMigrated")) return
        val addr = LocalDataStorage.get(context, "LastDeviceAddress", null) ?: return run {
            // No paired watch ever; nothing to migrate. Mark as done.
            CoroutineScope(Dispatchers.IO).launch {
                LocalDataStorage.putBoolean(context, "MultiWatchMigrated", true)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            GLOBAL_KEYS.forEach { key ->
                LocalDataStorage.get(context, key, null)?.let { value ->
                    LocalDataStorage.put(context, "${key}_$addr", value)
                    LocalDataStorage.delete(context, key)
                }
            }
            LocalDataStorage.putBoolean(context, "MultiWatchMigrated", true)
        }
    }

    private companion object {
        val GLOBAL_KEYS = listOf(
            "PhoneAlarmDrafts", "PhoneAlarmDraftsDirty", "AlarmViewMode", "AlarmDaySelections",
            "HourlySignalEnabled", "HourlySignalStartHour", "HourlySignalEndHour",
            "HourlySignalMigrated",
            "fineTimeAdjustment", "timeAdjustmentNotification",
            // Watch-side settings TBD: enumerate during implementation by grep'ing
            // SettingsViewModel for LocalDataStorage.get(...) call-sites.
        )
    }
}
```

Called once during `GShockApplication.onCreate()`, before any VM is constructed. Idempotent (gated on the boolean).

If `LastDeviceAddress` is null (user never paired a watch), the migration is a no-op and the boolean is set so subsequent launches skip the work.

### 3.3 Edge case: multiple watches paired before #6

Today, `getDeviceAddresses()` already supports multiple addresses. If the user had two watches paired *before* #6 (using the existing CDM flow), only `LastDeviceAddress` was authoritative for app state — the second watch shared the same global keys. After migration, the second watch starts with empty per-watch state (since the globals went to `LastDeviceAddress`). **Acceptable** — the user re-syncs when they next connect to it. Document this in release notes.

---

## 4. Connection state observation

The `●/○` dot per row tracks whether each watch is currently connected. Source of truth: BLE / CDM events fanned through `ProgressEvents`.

### 4.1 New `WatchConnectionState` per address

```kotlin
// data/local/WatchConnectionTracker.kt   (new)

@Singleton
class WatchConnectionTracker @Inject constructor() {
    private val _states = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val states: StateFlow<Map<String, ConnectionState>> = _states.asStateFlow()

    enum class ConnectionState { CONNECTED, CONNECTING, DISCONNECTED, ERROR }

    init {
        ProgressEvents.runEventActions(Utils.AppHashCode(), arrayOf(
            EventAction("WatchInitializationCompleted") { addr -> mark(addr, CONNECTED) },
            EventAction("Disconnect")                  { addr -> mark(addr, DISCONNECTED) },
            EventAction("DeviceAppeared")              { addr -> /* unchanged */ },
        ))
    }

    fun mark(address: String?, state: ConnectionState) {
        if (address == null) return
        _states.update { it + (address to state) }
    }
}
```

`PairedWatchList` collects this StateFlow and looks up each row's connection state by address. The `Connect` / `Disconnect` actions on the ⋮ menu call `repository.waitForConnection(addr)` / `repository.disconnect()` and proactively call `tracker.mark(addr, CONNECTING)` so the dot updates immediately.

### 4.2 Single-active-watch invariant

When the user taps `Connect` on a row that isn't the currently-connected watch:

1. `tracker.mark(currentlyConnected, DISCONNECTED)` (optimistic).
2. `repository.disconnect()` — best-effort; gracefully handles "wasn't connected" cases.
3. `tracker.mark(target, CONNECTING)`.
4. `repository.waitForConnection(target)` — same call `CompanionDevicePresenceMonitor.kt:40` already uses.
5. On `WatchInitializationCompleted` event, the tracker auto-flips `target` to `CONNECTED`.

If the user taps `Connect` on a row mid-connection elsewhere, the in-flight connection is cancelled and the new one starts. Implementation: a `Mutex` on `tracker` guards transitions.

---

## 5. Forget watch flow

### 5.1 Confirmation

```
┌────────────────────────────────────────┐
│  Forget GW-B5600BC?                    │
│                                        │
│  This removes the watch from your      │
│  paired list and deletes all its       │
│  alarms, settings, and sync history    │
│  on this phone.                        │
│                                        │
│  The watch itself is unaffected.       │
│                                        │
│       [ Cancel ]   [ Forget ]          │
└────────────────────────────────────────┘
```

Tapping `Forget` calls `viewModel.forget(address)`. The VM:

1. If `address == selectedAddress`, switches selection to another paired address (or null if none remain).
2. If currently connected to this watch, calls `repository.disconnect()`.
3. Calls `preConnectionViewModel.disassociate(context, address)` — same path `WatchConnectionDialog.kt:165-173` uses today (CDM removeAssociation + removes `DeviceAddresses` entry + `DeviceName_<addr>` key).
4. Calls `PerWatchCacheCleaner.wipe(address)` — new helper that deletes every `<key>_<addr>` for the keys listed in `MultiWatchMigrator.GLOBAL_KEYS` plus `SyncHistory_<addr>` plus `WatchSnapshot_<addr>`.
5. `tracker.states.update { it - address }`.

Guard: if the user forgets the *only* paired watch, the UI returns to the empty-state screen (§2.5).

### 5.2 `PerWatchCacheCleaner`

```kotlin
// data/local/PerWatchCacheCleaner.kt   (new)

class PerWatchCacheCleaner(private val context: Context) {
    suspend fun wipe(address: String) {
        val keys = MultiWatchMigrator.GLOBAL_KEYS.map { "${it}_$address" } +
                   listOf("SyncHistory_$address", "WatchSnapshot_$address")
        keys.forEach { LocalDataStorage.delete(context, it) }
    }
}
```

---

## 6. Settings reconciliation (dirty-flag rule)

Each per-watch setting that the user can change *both* on the phone and on the watch carries a dirty flag (`<key>_<addr>_dirty`). The reconciliation contract:

```kotlin
suspend fun reconcileOnConnect(address: String, api: IGShockAPI) {
    SETTINGS_KEYS.forEach { key ->
        val dirty = LocalDataStorage.getBoolean(context, "${key}_${address}_dirty")
        if (dirty) {
            // Phone has authority — push to watch
            pushToWatch(api, key, LocalDataStorage.get(context, "${key}_$address"))
            LocalDataStorage.putBoolean(context, "${key}_${address}_dirty", false)
        } else {
            // Watch has authority — read into cache
            val watchValue = readFromWatch(api, key)
            LocalDataStorage.put(context, "${key}_$address", watchValue)
        }
    }
}
```

Called from `AutoSyncCoordinator.runChain()` (Initiative #4) and from explicit "Send to watch" actions, after the watch is reachable.

`SETTINGS_KEYS` enumerates only the watch-side settings (light, font, operationTone, locale, powerSavings) that the user can edit on either side. Alarms have their own dirty flag in `AlarmSyncStorage`; hourly-signal and fine-time-adjustment are phone-only (no watch UI for editing them) so they're always pushed (no read step).

The exact list of "smart" settings is **TBD during implementation** — a grep across the GShockAPI surface for getter/setter pairs (`api.getSettings()`, `api.setSettings(...)`) will reveal the full set. Track in §11 follow-ups.

### 6.1 `WatchSnapshot_<address>` for live values

Battery and temperature are read-only-on-connect. Persist a small snapshot:

```kotlin
data class WatchSnapshot(
    val battery: Int?,
    val temperature: Int?,
    val homeTime: String?,
    val cachedAt: Long,
)
```

Updated whenever any successful `api.getBatteryLevel()` / `api.getWatchTemperature()` returns. Read by `WatchRow` to render the `cached: …` subtitle when disconnected.

---

## 7. ViewModel contract changes

### 7.1 Existing VMs that need to react to `CurrentWatchSelector`

| VM | Per-watch read pattern |
|----|------------------------|
| `AlarmViewModel` | `selector.selected.flatMapLatest { addr -> AlarmSyncStorage.flowFor(addr) }` |
| `SettingsViewModel` | `selector.selected.flatMapLatest { addr -> watchSettingsFlowFor(addr) }` |
| `SyncHistoryViewModel` (#5) | already supports per-address; pass `selector.currentAddress()` to `store.observe(...)` |
| `TimeViewModel` | rendered as part of `PairedWatchList`; reads `tracker.states` and `WatchSnapshot_<addr>` per row |

`AlarmSyncStorage.flowFor(addr)` is a new method that emits the per-watch alarm list whenever any of the relevant `<key>_<addr>` DataStore keys change. Implementation: combine the existing `getString` + a `MutableStateFlow<Long>` change-tick (same pattern as #5's `SyncHistoryStore`).

### 7.2 New `MultiWatchViewModel`

Drives `PairedWatchList`. Owns:

- `paired: StateFlow<List<PairedWatch>>` — combines `LocalDataStorage.getDeviceAddresses()` with `DeviceName_<addr>`, `WatchConnectionTracker.states`, and `WatchSnapshot_<addr>` reads.
- Actions: `select(addr)`, `connect(addr)`, `disconnect(addr)`, `forget(addr)`, `addWatch(activity)`.

`PairedWatch` is a small UI-state data class:

```kotlin
data class PairedWatch(
    val address: String,
    val name: String,
    val state: ConnectionState,
    val snapshot: WatchSnapshot?,
    val isSelected: Boolean,
)
```

---

## 8. Critical files

**New:**
- `app/.../data/local/CurrentWatchSelector.kt` — promoted from the #5 stub to a real Hilt singleton.
- `app/.../data/local/WatchConnectionTracker.kt` — per-address connection-state map.
- `app/.../data/local/WatchSnapshot.kt` — battery/temp/homeTime cache.
- `app/.../data/local/MultiWatchMigrator.kt` — one-shot migrator.
- `app/.../data/local/PerWatchCacheCleaner.kt` — forget-watch wipe.
- `app/.../ui/devices/PairedWatchList.kt` — list composable on the Time tab.
- `app/.../ui/devices/WatchRow.kt` — per-row composable.
- `app/.../ui/devices/MultiWatchViewModel.kt` — drives the list.
- `app/.../di/MultiWatchModule.kt` — Hilt provides for the new singletons.

**Modify:**
- `app/.../GShockApplication.kt` — call `MultiWatchMigrator.migrateIfNeeded()` from `onCreate()` before any DI graph use.
- `app/.../ui/time/TimeScreen.kt` — replace `WatchSummaryCard` (or `WatchNameView` + `WatchInfoView` if #1's card consolidation hasn't landed yet — verify branch state) with `PairedWatchList(...)`.
- `app/.../ui/time/WatchSummaryCard.kt` (or `WatchNameView.kt` / `WatchInfoView.kt`) — **deleted**. Their visual elements (battery / temp / home time) are absorbed into `WatchRow`.
- `app/.../ui/time/WatchConnectionDialog.kt` — **deleted**. The list's `+ Add watch` button and per-row ⋮ menu now own the add/forget actions.
- `app/.../data/local/AlarmSyncStorage.kt` — re-key all reads/writes from global to `<key>_<addr>`. Add `flowFor(addr)`.
- `app/.../ui/alarms/AlarmViewModel.kt` — switch to `selector.selected.flatMapLatest { … }`.
- `app/.../data/local/HourlySignalSettings.kt` (#3) — re-key to per-watch.
- `app/.../ui/settings/SettingsViewModel.kt` — re-key the watch-settings reads/writes; expose `dirty` flags.
- `app/.../ui/alarms/PhoneFallbackReceiver.kt` (#2/#5 file) — pass `selector.currentAddress()` to `SyncHistoryStore.load(...)`.
- `app/.../data/local/SyncHistoryStore.kt` (#5) — no change; already per-address.
- `app/.../ui/main/PreConnectionViewModel.kt` — its existing `associateWithUi(...)` and `disassociate(...)` are reused; remove any dialog-specific glue once `WatchConnectionDialog` is gone.
- `app/.../utils/LocalDataStorage.kt` — no API change; the new keys follow existing patterns.
- `res/values/strings.xml` + 10 locales — new strings: `paired_watches_empty`, `add_watch`, `forget_watch_confirm_title`, `forget_watch_confirm_body`, `connect`, `disconnect`, `forget`, `cached_prefix`, `connecting`, `connection_error`, `not_yet_synced`. Drop `manage_watches` (button text used by the deleted dialog).

**Tests (new):**
- `app/src/test/java/.../data/local/CurrentWatchSelectorTest.kt` — emits stored value on construction; emits new value on `select`; clears.
- `app/src/test/java/.../data/local/MultiWatchMigratorTest.kt` — copies all globals to per-watch namespace; idempotent; no-op when no `LastDeviceAddress`.
- `app/src/test/java/.../data/local/PerWatchCacheCleanerTest.kt` — wipes only the targeted address; other watches unaffected.
- `app/src/test/java/.../ui/devices/MultiWatchViewModelTest.kt` — paired list construction; select moves the highlight; connect/disconnect/forget paths.
- `app/src/test/java/.../data/local/WatchConnectionTrackerTest.kt` — event-driven state transitions; mutex-guarded single-active invariant.

---

## 9. Reuse from existing code

- `LocalDataStorage` per-device methods (`getDeviceAddresses`, `addDeviceAddress`, `removeDeviceAddress`, `getDeviceName`, `setDeviceName`) — already in place. No changes.
- `PreConnectionViewModel.associateWithUi(...)` — reused verbatim by `+ Add watch`. The CDM dialog and pairing path don't change.
- `PreConnectionViewModel.disassociate(...)` — reused by `Forget`.
- `repository.waitForConnection(addr)` — reused by `Connect`. Already used by `CompanionDevicePresenceMonitor.kt:40`.
- `repository.disconnect()` — reused by `Disconnect`. Already called from `BottomNavigationBarWithPermissions.kt:67` and `MainEventHandler.kt:79`.
- `ProgressEvents.runEventActions(Utils.AppHashCode(), …)` — pattern used throughout for new event subscribers.
- `AlarmSyncStorage`'s existing `dirty` flag → precedent for the new per-key dirty flags in `SettingsViewModel`.
- `Spacing` token + M3 typography (#1) — every new composable uses these.
- `MaterialTheme.colorScheme.primary` — green dot color (under deep-blue palette this reads as a teal-ish "active" tone; verify visually during implementation).

---

## 10. Verification

1. **Build/lint/tests green:** `./gradlew ktlint lint test assembleDebug` passes.
2. **Migration on first run:** Install over an existing build that has alarms set. Open the app → Time tab shows one watch row (the previously-`LastDeviceAddress` one) with the alarms intact when you switch to the Alarms tab. Inspect DataStore: `PhoneAlarmDrafts` is gone; `PhoneAlarmDrafts_<addr>` exists.
3. **Single watch UX (no behavior regressions):**
   - Time tab shows one row with green dot when connected, red when not.
   - Alarms / Settings / Hourly-signal / Sync history all show the same data they did before.
   - `+ Add watch` opens the OS pairing dialog (CDM chooser).
4. **Two-watch flow:**
   - Pair watch A (existing). Pair watch B via `+ Add watch` → second row appears.
   - Tap watch B's row → highlight moves to B; Alarms tab now empty (B has no cache yet).
   - Tap watch A's row → highlight moves back to A; Alarms tab shows A's alarms.
5. **Connect / Disconnect:**
   - With A connected and B selected (but not connected), tap ⋮ → Connect on B's row. Within a few seconds: A's dot → red, B's dot → green. Alarms tab continues to reflect B (the selected watch); now any sync writes go to B.
   - Tap ⋮ → Disconnect on B's row. B's dot → red. No watch is connected; selection is unchanged.
6. **Forget:**
   - Long-pair both watches' alarms. Tap ⋮ → Forget on B. Confirm. Row vanishes. DataStore: every `<key>_<B-address>` is gone, including `SyncHistory_<B-address>` and `WatchSnapshot_<B-address>`.
   - Selection auto-moves to A. UI continues to render correctly.
7. **Forget the last watch:**
   - With one watch paired, Forget it → empty-state screen appears with `+ Add watch` button. `LastDeviceAddress` is cleared.
8. **Settings reconciliation (smart):**
   - Set "Light: Auto" on watch A (via the watch's own buttons). Connect A to the app → Settings tab now shows "Light: Auto" (read from watch into cache).
   - On the app, change "Light: Auto" → "Light: Off". Watch immediately receives the update. Restart the app. The phone-side cache still says "Off" (dirty was cleared after push); watch confirms "Off".
   - Set the "Light" key's dirty flag manually (debug tool). On next reconnect, the value is pushed to the watch even if the watch's current value is something else.
9. **Sync history per-watch:**
   - Open Sync history with watch A selected → A's history. Switch to B → B's history (or empty if never synced).
10. **Cached subtitle on disconnected rows:**
    - Connect A, observe battery 87% / temp 21°C in subtitle. Disconnect → subtitle becomes `cached: 87% • 21°C` greyed.
11. **No raw sp/dp leftovers:** `git grep -nE 'fontSize\s*=\s*[0-9]+\.sp|\.dp\)' app/src/main/java/com/beamburst/casswatch/ui/devices/` returns nothing.
12. **Selection persistence:** Kill the app. Reopen. The previously-selected watch is still selected (`LastDeviceAddress` survived).
13. **Concurrent connect:** Tap Connect on A immediately followed by Connect on B. The mutex guarantees a clean transition: either A connects then B disconnects A and connects, or B connects directly. No "both green" state ever observed.

---

## 11. Cross-cutting / dependencies

- **Depends on Initiative #1**: visual-refresh tokens, M3 typography, package id, deep-blue palette.
- **Depends on Initiative #2**: alarm modal editor + footer + fire-once flow. The alarm rows' per-watch keying happens here.
- **Depends on Initiative #3**: hourly-signal settings + evaluator. Per-watch keying happens here.
- **Depends on Initiative #4**: `AutoSyncCoordinator` calls `reconcileOnConnect` after the watch is up. `WatchButtonDispatcher` is unchanged (presses are global, not per-watch — confirmed in #4).
- **Depends on Initiative #5**: `CurrentWatchSelector` was stubbed in #5; this initiative makes it real. `SyncHistoryStore` is already per-watch and works without modification.
- **Heads-up for Initiative #7**: watch-side feedback round-trip writes to *the connected* watch via the round-trip API. With #6 it writes to whichever watch is connected at that moment, and the resulting sync-history entry is keyed to that address — no extra per-watch logic needed in #7 beyond what's already established.

---

## 12. PR shape

Single PR titled `Initiative #6 — Multi-watch management on Home`. Suggested commit split:

1. `refactor: promote CurrentWatchSelector from stub to real StateFlow-backed singleton` — pure DI / API change; existing `LastDeviceAddress` callers unaffected.
2. `refactor: re-key AlarmSyncStorage to per-watch addresses; add flowFor(address)` — data-layer change; AlarmViewModel updated to use `flatMapLatest`. Tests verify isolation.
3. `refactor: re-key HourlySignalSettings + watch-settings to per-watch addresses` — same pattern for the other affected stores.
4. `feat: MultiWatchMigrator + first-run gated migration of globals to per-watch namespaces` — one-shot migrator, idempotent.
5. `feat: WatchConnectionTracker + WatchSnapshot persistence` — per-address connection state and battery/temp cache.
6. `feat: PairedWatchList + WatchRow + MultiWatchViewModel; replace WatchSummaryCard / WatchConnectionDialog` — the visible UI lands. Empty-state, ⋮ menu, Add/Forget all wired.
7. `feat: settings dirty-flag reconciliation on connect (reconcileOnConnect)` — the "smart" reconciliation rule.
8. `feat: PerWatchCacheCleaner + Forget confirmation dialog wiping all per-watch state` — the deletion path.
9. `chore: drop manage_watches string and any related dialog-only assets; add new strings in 10 locales` — cleanup.

Each commit builds and tests on its own.

---

## 13. Open questions / follow-ups

- **Exact `SETTINGS_KEYS` list for reconciliation** — enumerate during implementation by grep'ing `SettingsViewModel` and the `api.setSettings(...)` / `api.getSettings()` call-sites for paired getter/setter pairs. Track each as either "phone-only push" (alarms, hourly-signal, fine-time-adjustment) or "smart reconcile" (light, font, locale, operationTone, powerSavings). The light + power-saving pair is the user's headline example.
- **Edge case: Selected watch is forgotten while other tabs are open** — `selector.selected` flips to the next paired address (or null). Each VM's `flatMapLatest` reloads. Verify there's no transient flash of the deleted watch's data.
- **Concurrent BLE connection attempts** — the `Mutex` in `WatchConnectionTracker` should be enough; if subtle races appear during testing, fall back to a single-coroutine actor that serialises all `connect/disconnect` calls.
- **GShockAPI's `disconnect()` on a watch we're not connected to** — verify it's a graceful no-op. If it throws, wrap in `runCatching` at the call site.
- **CDM removeAssociation gotchas** — `PreConnectionViewModel.disassociate(...)` already handles this; verify with the existing test on stock Android, GrapheneOS, and LineageOS (the three ROMs the explore agent flagged as having CDM quirks).
- **Empty-state translation** — "No watches paired yet." needs translator review for tone in the 10 supported locales.
