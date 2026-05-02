# Initiative #3 — Hourly Signal Time-Window Card

> Sub-spec under the umbrella roadmap `docs/superpowers/specs/2026-05-02-rebrand-overhaul-roadmap.md`. Lands on top of Initiative #1 (visual refresh + tokens) and Initiative #2 (alarms overhaul). Assumes `Spacing` token, M3 typography, deep-blue primary, and the new package `com.beamburst.casswatch` are already in. After acceptance, the executable plan is produced via `superpowers:writing-plans`.

## Context

Today, the Alarms screen has a single binary "Signal (chime)" toggle (`AlarmChaimeSwitch.kt`) bound to `alarm[0].hasHourlyChime`. Switch ON → the watch chimes at every hour, 24/7. The user wants a more useful version: define a window (e.g. 7:00–18:00) and have the chime auto-managed — ON during the window, OFF outside it. There's no new background scheduler in the app; the watch's existing 4×/day auto-sync (RunEnvironment.AUTO_TIME_ADJUSTMENT at 0:00 / 6:00 / 12:00 / 18:00) is the only mechanism that updates the watch state, and that's enough.

The user picked a deliberately constrained UI: only 4 valid start hours and 4 valid end hours — exactly aligned with the auto-sync moments. This eliminates impossible windows (no "set 7:30, expect a sync at 7:30") and keeps the dialog tiny.

## Confirmed decisions (from brainstorm)

| Topic | Decision |
|-------|----------|
| Relationship to existing `AlarmChimeSwitch` | **Replace entirely.** `AlarmChaimeSwitch.kt` is deleted; the new card is the single source of truth for the watch's hourly chime. |
| Cross-midnight windows | **Not allowed.** `start < end` always (`end == 0` represents 24, end-of-day). |
| Off-state behavior | When card is OFF, app **forces `hourlyChime = false` on every sync** (unambiguous; user cannot unintentionally drift back to ON). |
| Sync paths that apply the rule | Auto sync (`RunEnvironment.AUTO_TIME_ADJUSTMENT`), manual **Send time**, manual **Send alarms**. **Not** Send events. |
| Window granularity | **Discrete hours, 4 starts × 4 ends, aligned with sync moments.** Start ∈ {1, 7, 13, 19}; End ∈ {6, 12, 18, 0(=24)}. 10 valid combinations. |
| Default window | **7:00 – 18:00** (i.e. start=7, end=18). |
| Card position | Below the alarm list, above the footer chips — the slot the existing chime switch occupies today. |
| Picker UI | Two compact `NumberPicker`s (`setDisplayedValues`) side by side inside an `AlertDialog`. |
| API surface | `api.setSettings(api.getSettings().copy(hourlyChime = …))` — the same call already used at `AlarmViewModel.kt:192`. |
| Hourly-chime carrier | `alarm[0].hasHourlyChime` is the legacy in-payload representation; the new card writes `Settings.hourlyChime` directly so the alarm[0] coupling becomes a vestige (kept for backward-compat reads only). |

## Goals

1. Replace the always-on / always-off chime toggle with a window-aware card.
2. Auto-evaluate the chime state at every relevant sync moment so the watch always reflects the right rule.
3. Keep the UI scoped to the four sync moments — no impossible half-hour windows.
4. Deterministic OFF when the card is disabled.

## Non-goals

- Cross-midnight windows (e.g. 22:00–06:00) — explicitly out.
- Minute-level precision in the picker.
- A new background scheduler / WorkManager. The existing 4×/day auto-sync is the only timer.
- Changing `api.setSettings` / GShockAPI — we use what exists.
- Per-day windowing (different schedule on weekends) — out of scope.

---

## 1. Window semantics

### 1.1 Valid combinations

| start \\ end | 6 | 12 | 18 | 0 (=24) |
|--------------|---|----|----|---------|
| 1            | ✓ | ✓  | ✓  | ✓       |
| 7            | – | ✓  | ✓  | ✓       |
| 13           | – | –  | ✓  | ✓       |
| 19           | – | –  | –  | ✓       |

10 valid windows. Any combination with `end <= start` (after normalising end=0→24) is invalid; the dialog's "Save" button is disabled when invalid.

### 1.2 Display

In the picker dialog, end values render as `06`, `12`, `18`, `00` (always two digits). The card body shows the saved window as `start:00 – end:00` (e.g. `7:00 – 18:00`, `19:00 – 00:00`).

### 1.3 Evaluation rule (single source of truth)

```kotlin
// HourlySignalEvaluator.kt
fun shouldChimeAt(now: LocalDateTime, settings: HourlySignalSettings): Boolean {
    if (!settings.enabled) return false
    val endNorm = if (settings.endHour == 0) 24 else settings.endHour
    val nextHourBoundary = now.hour + 1   // 0..24
    return nextHourBoundary in settings.startHour until endNorm
}
```

The rule is **"is the next hour-boundary inside the chime window?"**, applied identically to auto-sync and manual-sync. This is the right question regardless of whether `now` is 06:00 sharp (auto) or 09:30 (manual): the chime fires at the next H:00 boundary, and that's what the user sees.

Worked examples (window 7–18, chime hours = {7, 8, …, 17}):

| sync moment | next hour | in [7, 18)? | result |
|-------------|-----------|-------------|--------|
| 00:00 (auto) | 1 | no | OFF |
| 06:00 (auto) | 7 | yes | ON |
| 09:30 (manual) | 10 | yes | ON |
| 12:00 (auto) | 13 | yes | ON |
| 17:30 (manual) | 18 | no | OFF |
| 18:00 (auto) | 19 | no | OFF |
| 23:30 (manual) | 24 | no | OFF |

Worked examples (window 19–0, chime hours = {19, 20, …, 23}):

| sync | next | in [19, 24)? | result |
|------|------|--------------|--------|
| 18:00 | 19 | yes | ON |
| 12:00 | 13 | no | OFF |
| 22:30 | 23 | yes | ON |

### 1.4 When the card is OFF

`HourlySignalEvaluator.shouldChimeAt(...)` returns `false` immediately. Every applicable sync writes `hourlyChime = false`, overriding any state the user may have toggled on the watch directly.

---

## 2. Persistence

### 2.1 New `HourlySignalSettings`

```kotlin
// data/local/HourlySignalSettings.kt (new)
data class HourlySignalSettings(
    val enabled: Boolean,
    val startHour: Int,   // ∈ {1, 7, 13, 19}
    val endHour: Int,     // ∈ {6, 12, 18, 0}
) {
    companion object {
        val Default = HourlySignalSettings(enabled = false, startHour = 7, endHour = 18)
    }
}
```

### 2.2 New `LocalDataStorage` keys

| Key | Type | Default |
|-----|------|---------|
| `"HourlySignalEnabled"` | Boolean (string-encoded via `getBoolean/putBoolean`) | `false` |
| `"HourlySignalStartHour"` | Int (string-encoded) | `7` |
| `"HourlySignalEndHour"` | Int (string-encoded) | `18` |

Use the existing `LocalDataStorage` API surface (`utils/LocalDataStorage.kt:102-108` for boolean helpers; integers via `.toString()` / `.toInt()`). No schema migration needed — missing keys read as defaults.

### 2.3 First-run migration

If the `HourlySignalEnabled` key does not exist yet (= first launch on the new build), inspect the legacy `alarm[0].hasHourlyChime` value via `LocalDataStorage` / `AlarmSyncStorage`:

- Legacy chime was **ON** → write `enabled = true, startHour = 1, endHour = 0`. This is the widest valid window (1:00 – 24:00, chimes at hours 1..23) and approximates the previous 24/7 behaviour without violating the discrete grid. The user keeps hearing chimes; they can narrow the window in the dialog.
- Legacy chime was **OFF** → write `enabled = false` and the default window 7–18 (so the dialog shows a sensible starting point if they ever turn it on).

Migration runs once at first launch, gated by a separate `"HourlySignalMigrated"` boolean. After it runs, the legacy alarm[0] flag remains a passive mirror (see §4.4) but is no longer the truth source.

---

## 3. UI

### 3.1 The card on the Alarms screen

```
┌──────────────────────────────────────┐
│  Hourly signal              [ ON ]   │   ← title (titleMedium) + Switch
│  7:00 – 18:00                        │   ← subtitle (bodyMedium, onSurfaceVariant)
└──────────────────────────────────────┘
```

- Card padding `Spacing.lg`, vertical content padding `Spacing.sm`.
- Tap on the **title row or subtitle area** (anywhere except the switch's hit-target) → opens the config dialog.
- Switch toggles `enabled` directly. No dialog required to flip it.
- When `enabled = false`, the subtitle is dimmed (`onSurfaceVariant.copy(alpha = 0.5f)`) but still shows the saved window — so re-enabling restores the previous setting.
- New file `ui/alarms/HourlySignalCard.kt`.

### 3.2 The config dialog

```
        ┌────────────────────────────────┐
        │  Hourly signal                 │  ← titleLarge
        │                                │
        │  ┌──────┐         ┌──────┐     │
        │  │  07  │   to    │  18  │     │  ← Two NumberPickers
        │  └──────┘         └──────┘     │
        │  start            end          │  ← labelMedium captions
        │                                │
        │  Chimes at 07:00, 08:00,       │  ← bodySmall, dynamic line
        │  …, 17:00 (11 chimes / day)    │     showing exact chime times
        │                                │
        │  Requires automatic sync.      │  ← bodySmall, onSurfaceVariant
        │  The watch updates at 0, 6,    │
        │  12 and 18.                    │
        │                                │
        │       [ Cancel ]   [ Save ]    │  ← Save disabled if invalid
        └────────────────────────────────┘
```

- Start NumberPicker: `setDisplayedValues(arrayOf("01", "07", "13", "19"))`, `setMinValue(0)`, `setMaxValue(3)`. Index → hour mapping.
- End NumberPicker: `setDisplayedValues(arrayOf("06", "12", "18", "00"))` — **filtered dynamically** to only show ends greater than the current start (e.g. when start=13 is selected, end shows only `18, 00`). Triggered via the start picker's `OnValueChangedListener`.
- "Chimes at … (N chimes / day)" line is computed live as `(start until endNorm).joinToString(", ") { String.format("%02d:00", it) }` truncated/elided if it exceeds two lines.
- "Requires automatic sync. …" caption is a hard-coded help line that explains the 4×/day cadence so the user understands why end values are constrained to {6, 12, 18, 00}.
- New file `ui/alarms/HourlySignalDialog.kt`.

### 3.3 Strings

Add to `res/values/strings.xml` (and 10 locales):
- `R.string.hourly_signal_title` = "Hourly signal"
- `R.string.hourly_signal_window_caption` = "%1$s:00 – %2$s:00" (formatted)
- `R.string.hourly_signal_chimes_at` = "Chimes at %1$s (%2$d chimes / day)"
- `R.string.hourly_signal_requires_auto_sync` = "Requires automatic sync. The watch updates at 0:00, 6:00, 12:00 and 18:00."
- `R.string.hourly_signal_picker_start` = "start"
- `R.string.hourly_signal_picker_end` = "end"

Remove (since AlarmChimeSwitch is deleted):
- `R.string.signal_chime` ("Signal (chime)") — delete from all 10 locales.
- The clause "and a 'signal' (hourly chime)" in `R.string.alarms_screen_info` — reword to drop the parenthetical, since the feature is now its own card.

---

## 4. ViewModel + sync wiring

### 4.1 `HourlySignalViewModel` (new) or `AlarmViewModel` extension

Two options:
- **(a)** Extend `AlarmViewModel` with the new state and actions, since the card lives on the Alarms screen.
- **(b)** Introduce a small dedicated `HourlySignalViewModel` for separation of concerns.

Recommendation: **(a)**. The card already shares a screen with alarms; spawning a second VM for one Card adds DI noise and an extra `collectAsState` bridge. The new state is small (3 ints + boolean) and the actions are localised.

`AlarmViewModel` adds:

```kotlin
data class HourlySignalUiState(
    val enabled: Boolean,
    val startHour: Int,
    val endHour: Int,
    val showDialog: Boolean,
)

private val _hourlySignal = MutableStateFlow(HourlySignalUiState(...))
val hourlySignal: StateFlow<HourlySignalUiState> = _hourlySignal.asStateFlow()

sealed class HourlySignalAction {
    data class SetEnabled(val on: Boolean) : HourlySignalAction()
    data object OpenDialog : HourlySignalAction()
    data class SaveWindow(val startHour: Int, val endHour: Int) : HourlySignalAction()
    data object DismissDialog : HourlySignalAction()
}
```

On `SetEnabled` and `SaveWindow`, the VM persists via `LocalDataStorage` and recomputes the next chime evaluation for the **current sync moment** so the next call to `applyHourlySignalToWatch()` has fresh data. The toggle does not auto-trigger a sync (that would need an active connection); state is applied on the next sync.

### 4.2 New helper: `applyHourlySignalToWatch(api, now)`

A single suspend extension function used by every sync site. Collocate in `ui/alarms/HourlySignalApplier.kt` (new):

```kotlin
suspend fun applyHourlySignalToWatch(
    api: IGShockAPI,
    settings: HourlySignalSettings,
    now: LocalDateTime,
) {
    val target = HourlySignalEvaluator.shouldChimeAt(now, settings)
    val current = api.getSettings()
    if (current.hourlyChime != target) {
        api.setSettings(current.copy(hourlyChime = target))
    }
}
```

The early-return when `current.hourlyChime == target` avoids redundant BLE writes (the watch is bandwidth-sensitive on slow links).

### 4.3 Sync site wiring

| Site | File:Line | Change |
|------|-----------|--------|
| Auto sync | `ActionViewModel.runActionsForAutoTimeSetting(...)` line 631 | Append a new step at the end of the `RunEnvironment.AUTO_TIME_ADJUSTMENT` chain that calls `applyHourlySignalToWatch(api, currentSettings, LocalDateTime.now())`. |
| Manual Send time | `TimeViewModel.kt:96` (after `api.setTime(timeMs)`) | Same call. |
| Manual Send alarms | `AlarmViewModel.kt:189-193` (currently does `api.setSettings(...).copy(hourlyChime = …)` based on `alarm[0]`) | **Replace** the current `if (WatchInfo.chimeInSettings) { … }` block with a call to `applyHourlySignalToWatch(...)`. Removes the legacy alarm[0] coupling at the call-site. |
| ~~Send events~~ | `EventViewModel.kt:105` | **No change** — explicitly excluded per decision. |

Each site uses `LocalDataStorage` to read the latest settings before calling the helper.

### 4.4 What about the legacy `alarm[0].hasHourlyChime` flag?

The watch firmware represents the chime as `alarm[0].hasHourlyChime` in some payloads and as `Settings.hourlyChime` in others — `WatchInfo.chimeInSettings` distinguishes these (see `AlarmViewModel.kt:191`). To keep the watch's payload internally consistent across versions:

- The legacy in-alarm flag stops being a UI control (no toggle for it) but is **still set to match `Settings.hourlyChime`** during alarm sync. `AlarmSchedulePlanner` (modified in #2) already produces alarm payloads; we add a final pass that sets `alarms[0].hasHourlyChime = currentSettings.hourlyChime` derived from `applyHourlySignalToWatch`'s evaluated target. That keeps the in-alarm flag a passive mirror of the truth source rather than a separate truth.
- For watches where `WatchInfo.chimeInSettings == false` (the legacy path), `applyHourlySignalToWatch` becomes a no-op for `setSettings`, and the `alarm[0]` mirror is the only mechanism. Verify this is rare on the supported watches and document in code with a one-line comment at the call-site.

---

## 5. Critical files

**Delete:**
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmChaimeSwitch.kt` (or post-Initiative-#1 path under `com.beamburst.casswatch`).
- `R.string.signal_chime` in all 10 `values-*/strings.xml` files.

**Modify:**
- `app/.../ui/alarms/AlarmsScreen.kt` — remove `AlarmChimeSwitch(...)` render call (lines 129–131); add `HourlySignalCard(...)` in its place (below alarm list, above footer chips).
- `app/.../ui/alarms/AlarmViewModel.kt` — add `_hourlySignal` state, dispatch handler for `HourlySignalAction`, refactor `sendAlarmsToWatch` to call `applyHourlySignalToWatch` instead of inline `setSettings`.
- `app/.../ui/alarms/AlarmSchedulePlanner.kt` — final-pass mirror of `Settings.hourlyChime` into `alarms[0].hasHourlyChime` for legacy compatibility.
- `app/.../ui/time/TimeViewModel.kt:96` — append `applyHourlySignalToWatch(...)` after `api.setTime(timeMs)`.
- `app/.../ui/actions/ActionViewModel.kt:631` (`runActionsForAutoTimeSetting`) — append `applyHourlySignalToWatch(...)` to the chain. (When Initiative #4 deletes `ui/actions/`, this auto-sync entry point moves; the helper call moves with it.)
- `app/.../utils/LocalDataStorage.kt` — no API changes, just three new keys consumed by `HourlySignalSettings.load(context)` factory function.
- `res/values/strings.xml` and `values-*/strings.xml` — add new strings, remove `signal_chime`, reword `alarms_screen_info`.

**New:**
- `app/.../ui/alarms/HourlySignalCard.kt`
- `app/.../ui/alarms/HourlySignalDialog.kt`
- `app/.../ui/alarms/HourlySignalEvaluator.kt` (pure function; trivially unit-testable)
- `app/.../ui/alarms/HourlySignalApplier.kt` (suspend helper)
- `app/.../data/local/HourlySignalSettings.kt` (data class + load/save extensions on `LocalDataStorage`)

**Tests (new):**
- `app/src/test/java/.../ui/alarms/HourlySignalEvaluatorTest.kt` — table-driven across all 10 valid windows × 24 hour boundaries × on/off, asserting expected ON/OFF.
- `app/src/test/java/.../ui/alarms/HourlySignalApplierTest.kt` — verifies (1) early-return when `current == target`, (2) writes when they differ, (3) reads `LocalDataStorage` correctly.
- `app/src/test/java/.../data/local/HourlySignalSettingsTest.kt` — round-trips through DataStore including missing-keys → defaults.

---

## 6. Reuse from existing code

- `LocalDataStorage.put / get / putBoolean / getBoolean` — pattern at `utils/LocalDataStorage.kt:102-108`. No new API needed.
- `api.setSettings(api.getSettings().copy(hourlyChime = …))` — pattern at `AlarmViewModel.kt:192`. The new `HourlySignalApplier` lifts this idiom into a single helper.
- `WatchInfo.chimeInSettings` — capability flag for legacy chime carrier; still respected in the planner mirror pass.
- `ProgressEvents` — no new event types needed; the existing sync fan-out is sufficient.
- `Spacing` token + M3 typography from Initiative #1 — every new composable uses these. No raw sp/dp.
- `MaterialTheme.colorScheme` — deep-blue primary from #1 propagates through the switch and dialog automatically.

---

## 7. Verification

End-to-end checks for the implementation phase:

1. **Build/lint/tests green:** `./gradlew ktlint lint test assembleDebug` passes.
2. **AlarmChimeSwitch is gone:** `git grep -ni 'AlarmChimeSwitch\|signal_chime' app/` returns nothing.
3. **Card rendering:**
   - Default state on first launch: card OFF, subtitle reads "7:00 – 18:00" dimmed.
   - Toggle ON → subtitle becomes full opacity.
   - Tap card body → dialog opens with start=07, end=18 highlighted.
4. **Picker validity:**
   - Selecting start=01 → end picker shows {06, 12, 18, 00}.
   - Selecting start=07 → end picker shows {12, 18, 00}.
   - Selecting start=13 → end picker shows {18, 00}.
   - Selecting start=19 → end picker shows {00} only.
   - Save button enabled iff a valid (start, end) pair is chosen.
5. **Live chime-times caption:**
   - 7–18 → "Chimes at 07:00, 08:00, 09:00, 10:00, 11:00, 12:00, 13:00, 14:00, 15:00, 16:00, 17:00 (11 chimes / day)".
   - 19–00 → "Chimes at 19:00, 20:00, 21:00, 22:00, 23:00 (5 chimes / day)".
   - 1–6 → "Chimes at 01:00, 02:00, 03:00, 04:00, 05:00 (5 chimes / day)".
6. **Persistence:**
   - Toggle ON, save window 13–18, kill the app, relaunch → card shows ON, 13:00–18:00.
7. **Sync application (instrumented or manual on a connected watch):**
   - Card ON, window 7–18. At 06:00 auto-sync, watch's `hourlyChime` becomes `true` (verify in watch settings menu or via the next `api.getSettings()`).
   - At 18:00 auto-sync, watch's `hourlyChime` becomes `false`.
   - Card OFF → at 12:00 auto-sync, watch's `hourlyChime` is forced to `false` even if it was `true` before (e.g. user changed it on the watch directly).
8. **Manual sync application:**
   - At 09:30, card ON 7–18 → tap "Send time" → watch becomes `hourlyChime = true`.
   - At 19:30, card ON 7–18 → tap "Send time" → watch becomes `hourlyChime = false`.
   - At any time, tap "Send alarms" with card OFF → watch becomes `hourlyChime = false`.
   - Tap "Send events" → watch's `hourlyChime` is **unchanged**.
9. **Idempotence / no redundant writes:**
   - Two consecutive syncs at the same hour with the same card state → only one `setSettings` call (verify via Timber logs or BLE traffic capture).
10. **Migration:**
    - Install build with chime previously ON → after upgrade, card defaults to ON with window 1–00. Subsequent syncs apply that window.
    - Install build with chime previously OFF → card OFF.
11. **Localisation:**
    - All 10 locales render the new strings without truncation or layout breakage. Specifically the German and Polish strings (typically longest) fit.
12. **No raw sp/dp leftovers in scope:** `git grep -nE 'fontSize\s*=\s*[0-9]+\.sp|\.dp\)' app/src/main/java/com/beamburst/casswatch/ui/alarms/HourlySignal*.kt` returns nothing.

---

## 8. Cross-cutting / dependencies

- **Depends on Initiative #1**: `Spacing` token, M3 typography, package id, deep-blue primary.
- **Depends on Initiative #2**: card sits on the Alarms screen alongside the new alarm rows, footer chips, info card, modal editor. No state collisions; #3 just adds another VM-state slice.
- **Heads-up for Initiative #4 (actions removal)**: the auto-sync hook lives at `ActionViewModel.runActionsForAutoTimeSetting`. When #4 deletes `ui/actions/`, the auto-sync entry point relocates (likely into a smaller `AutoSyncCoordinator`); the `applyHourlySignalToWatch` call moves with it. No design change, just a refactor coordination point.
- **Independent of #5/#6/#7**: hourly signal does not produce sync-history events (it's a side-effect of the existing sync, not a top-level user action), does not vary per-watch (chime is a global watch setting), and has no broadcast-intent dimension.

---

## 9. PR shape

Single PR titled `Initiative #3 — Hourly Signal time-window card`. Suggested commit split:

1. `feat: HourlySignalSettings + LocalDataStorage keys + evaluator + tests` — pure data/logic, no UI, fully unit-testable. Lands first.
2. `feat: HourlySignalApplier + wire into auto-sync, Send time, Send alarms` — the helper + 3 call-site changes; the planner mirror pass to alarm[0]. No UI yet.
3. `feat: HourlySignalCard + HourlySignalDialog on Alarms screen` — the visible UI.
4. `chore: delete AlarmChimeSwitch and signal_chime strings, run migration` — cleanup + first-run migration shim.

Each commit builds and tests on its own.

---

## 10. Open questions

- **Watches without `WatchInfo.chimeInSettings`** (legacy carrier in alarm[0] only): verify this still works via the planner-mirror pass during device testing. If it doesn't, fall back to reading-modify-writing the alarm payload's first slot at sync time. Mark with `// TODO(initiative-3-followup): test on legacy watch`.
- **Per-day windowing** (different schedule on weekends): explicitly out of scope. If the user wants this later it's a separate initiative — would need 7 windows × 4 sync moments and a more nuanced UI.
- **A "test chime" button** in the dialog (tap to trigger a one-off chime now, for verification): out of scope; verify with a real next-hour boundary.
