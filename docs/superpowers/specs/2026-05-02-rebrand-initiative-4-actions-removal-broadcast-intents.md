# Initiative #4 — Actions Tab Removal + Settings Broadcast Intents

> Sub-spec under the umbrella roadmap `docs/superpowers/specs/2026-05-02-rebrand-overhaul-roadmap.md`. Lands on top of Initiatives #1–#3. Assumes `Spacing` + M3 typography (#1), the alarms screen rewrite (#2), and the hourly-signal card (#3) are already in. After acceptance the executable plan is produced via `superpowers:writing-plans`.

## Context

The Actions tab is the largest single chunk of code in the app today — 18 files in `ui/actions/` plus a Hilt-injected `ActionViewModel` with 9 Action subclasses. Most of the features it ships are things the user would rather wire up themselves with Tasker / MacroDroid / Automate. This initiative is mostly **deletion** with three small additions:

1. The watch's button-press events become **broadcast intents** instead of running built-in Action subclasses.
2. The two features still wanted as built-ins — `Set time on watch` and `Toggle flashlight` — survive as Settings rows / cards.
3. The legacy `Find my phone` behaviour also survives, as a co-existing card alongside the new long-press broadcast.

It is also a **package-id and namespace consequence** initiative: every reference to the old `org.avmedia.gshockGoogleSync` namespace inside dropped files goes away with the deletion; the broadcast action strings use the new `com.beamburst.casswatch` namespace established in #1.

## Confirmed decisions (from brainstorm)

| Topic | Decision |
|-------|----------|
| Extras storage | **Per-card** — short and long broadcasts each have their own key/value extras list |
| Toggle semantics | **OFF suppresses the broadcast.** The press still arrives at `WatchButtonDispatcher`; the dispatcher just skips emission for OFF cards |
| Broadcast action format | `com.beamburst.casswatch.RUN_SHORT` and `com.beamburst.casswatch.RUN_LONG` |
| Find my phone | **Kept as a separate toggleable card alongside the long-press broadcast.** Both can fire on the same long press (broadcast + ring) |
| Other Actions features (voice/next-track/photo/prayer/dial) | **All deleted entirely**, including `Adhan2` and `CameraX` dependencies |
| Flashlight | **Kept as a built-in card.** Hardcoded to short press. Co-fires with the short-press broadcast if both cards are ON |
| `Set time on watch` | **Top of Settings** as a one-tap action row |
| Press-event handler location | New top-level `WatchButtonDispatcher.kt` at the root package (sibling of `GShockApplication.kt`) |
| Auto-sync chain location | New top-level `AutoSyncCoordinator.kt` next to the dispatcher |
| Extras value types | **String + Int + Boolean** (each extra has a type picker) |
| Broadcast targeting | **Wide-open `sendBroadcast(intent)`** — no per-card target package field in v1 |
| `ACCESS_COARSE_LOCATION` permission | Default-keep; verify with grep during implementation that Events tab actually uses it. If not, drop in a follow-up cleanup commit |

## Goals

1. Remove dead/unused features so future maintenance focuses on what users actually use.
2. Replace the in-app action runner with a thin broadcast emitter so users plug in their own automations.
3. Preserve the two genuinely useful built-in actions (set time, flashlight) and the legacy find-phone behaviour.
4. Keep the auto-sync chain (the `AUTO_TIME_ADJUSTMENT` path) working through the deletion of `ActionViewModel`.

## Non-goals

- Adding a graphical "extras builder" beyond key/value rows (no JSON formatter, no template variables, no scripting).
- Per-card target package selection (deferred — a future enhancement if user feedback wants it).
- Changing the `setTime` or `getSettings` API surfaces.
- Re-imagining the Settings tab layout beyond inserting the new rows. The general row composables (Locale, Light, Font, etc.) keep their styling from #1.

---

## 1. Deletions

### 1.1 Files to delete (`ui/actions/` — 18 files)

```
ActionRunner.kt              ActionsScreen.kt          ActionItem.kt
ActionViewModel.kt           CameraCaptureHelper.kt    FlashlightHelper.kt
FlashlightView.kt            PhoneFinder.kt            PhoneFinderView.kt
PhoneView.kt                 PhotoView.kt              PrayerAlarmsHelper.kt
PrayerAlarmsView.kt          RemindersView.kt          SeparatorView.kt
SetTimeView.kt               SkipToNextTrackView.kt    VoiceAssistView.kt
```

> **Two of these survive in repurposed form**, NOT in `ui/actions/`:
>
> - `FlashlightHelper.kt` → moves to `utils/FlashlightHelper.kt` (no UI; pure side-effect helper).
> - `PhoneFinder.kt` → moves to `utils/PhoneFinder.kt` (no UI; pure side-effect helper that rings + vibrates).
>
> They lose their `View` siblings and any `RunEnvironment` plumbing; they become single-method helpers (`fun toggle(context)` and `fun ring(context)`).

After the move-and-rename, the `ui/actions/` directory is empty and removed.

### 1.2 Code references to remove

| Site | Change |
|------|--------|
| `GShockApplication.kt:123` | `ActionRunner(context, repository)` call → replaced with `WatchButtonDispatcher(...)` and `AutoSyncCoordinator(...)` instantiation (§3, §4) |
| `Screens.kt:7` | Remove `data object Actions : Screens("Actions")` |
| `BottomNavigationItem.kt:52-58` | Remove the Actions tab entry |
| `BottomNavigationBarWithPermissions.kt:150-169` | Remove Actions route + the CAMERA / CALL_PHONE / WRITE_EXTERNAL_STORAGE / ACCESS_COARSE_LOCATION runtime-permission requests gated by it |
| `AppScreen` enum (`GShockApplication.kt`) — `RUN_ACTIONS` value | Remove this value and any state-machine transitions into it. The `MainEventHandler.kt` `RunActions` → `RUN_ACTIONS` jump goes away with `ActionRunner` |
| `ProgressEvents.RunActions` event | Subscriber list shrinks to zero; the event itself can stay in GShockAPI (we don't own that lib) but no in-app subscriber emits or listens for it after this initiative |

### 1.3 Permissions to remove (`AndroidManifest.xml`)

| Line | Permission | Verdict |
|------|-----------|---------|
| 49 | `CAMERA` | **Remove** (only used by deleted `CameraCaptureHelper.kt`) |
| 50 | `CALL_PHONE` | **Remove** (only used by deleted `PhoneDialAction`) |
| 70 | `MODIFY_AUDIO_SETTINGS` | **Remove** (only used by deleted `NextTrack` action) |
| 10 | `ACCESS_COARSE_LOCATION` | **Default-keep.** Grep-verify Events usage during implementation: `git grep -nE 'COARSE_LOCATION|requestPermission.*location' app/src/main`. If only `ui/actions/` references survive after deletion, drop in a follow-up commit. |
| 51 | `READ_CALENDAR` | **Keep** (required by Events tab) |

### 1.4 Dependencies to drop (`build.gradle`)

| Line | Dependency | Verdict |
|------|-----------|---------|
| 102 | `libs.adhan2` | **Remove** (only used by deleted `PrayerAlarmsHelper.kt`) |
| 96–100 | `androidx.camera.*` (CameraX) | **Remove** (only used by deleted `CameraCaptureHelper.kt`) |

Verify with `git grep` that no other file imports `com.batoulapps.adhan2.*` or `androidx.camera.*` before dropping. Any leftover import is a build-break and must be cleaned up in the same commit.

### 1.5 Strings to delete (`res/values/strings.xml` + 10 locales)

The 25 R.string IDs scoped to `ui/actions/`:

```
actions, actions_saved, actions_save_failed, back_cam, camera_capture_error,
cannot_go_to_next_track, emergency_actions, find_phone (KEEP — re-used in §2.2),
flashlight_not_available, front_cam, image_captured, make_phonecall,
next_track, prayer_times_info, send_to_watch (KEEP — used by Events too — verify),
set_prayer_alarms, set_reminders, set_time (KEEP — used by §2.1),
skip_to_next_track_info, start_voice_assistant, take_photo,
toggle_flashlight (KEEP — used by §2.2),
unable_to_get_default_sound_uri, voice_assistant_not_available_on_this_device,
watch_not_connected (KEEP — used in many places), you_have_not_given_permission_to_to_run_action
```

Implementation phase grep-verifies "KEEP" candidates. Final removal list is whatever survives the verification.

### 1.6 What stays from the Actions tab semantically

- The `ButtonPressedInfoReceived` subscription (moves to `WatchButtonDispatcher`).
- The auto-sync action chain — setTime, setEvents, prayer-alarms-removal, hourly-signal-application — restructured into `AutoSyncCoordinator` (§4).
- `LocalDataStorage.getFineTimeAdjustment(context)` — used by the new `Set time` row (§2.1).
- `LocalDataStorage.getTimeAdjustmentNotification(context)` — used by the restyled "Notify me" row (§2.3).

---

## 2. Settings additions

The Settings tab gains **5 new rows / cards** at the top, all inside a single `Column` that sits above the existing Locale/Light/Font/etc. block. Order (top-to-bottom):

1. **Set time on watch** — one-tap action row (§2.1).
2. **Notify on auto-sync** — restyled existing toggle (§2.3).
3. **Short button press** — broadcast card (§2.2).
4. **Long button press** — broadcast card (§2.2).
5. **Find my phone** — legacy card (§2.2).
6. **Flashlight** — built-in card (§2.2).

After these, an `HorizontalDivider` and the existing rows continue.

### 2.1 `Set time on watch` row

Single-row composable showing the title `Set time on watch`, a small caption `Sends current local time + fine adjustment`, and a chevron / send-icon on the right. Tap → `viewModel.setTimeNow()` which calls:

```kotlin
fun setTimeNow() = viewModelScope.launch {
    if (!repository.isConnected()) {
        AppSnackbar(context.getString(R.string.watch_not_connected))
        return@launch
    }
    val timeOffset = LocalDataStorage.getFineTimeAdjustment(context)
    val timeMs = System.currentTimeMillis() + timeOffset
    repository.setTime(timeMs = timeMs)
    AppSnackbar(context.getString(R.string.time_sent_to_watch))   // NEW string
}
```

Reuses `repository.setTime(...)` which is already callable from `TimeViewModel.kt:87`. New file `ui/settings/SetTimeRow.kt`.

### 2.2 Press cards (short, long, find-phone, flashlight)

All four follow the same anatomy:

```
┌──────────────────────────────────────┐
│  <title>                    [ on/off]│   ← titleMedium + Switch
│  <subtitle>                          │   ← bodySmall, onSurfaceVariant
└──────────────────────────────────────┘
```

- **Subtitle for broadcast cards** = the action string in monospace (`com.beamburst.casswatch.RUN_SHORT`).
- **Subtitle for find-phone / flashlight cards** = a one-liner describing what the card does (`Rings the phone on long press` / `Toggles the flashlight on short press`).
- Tap **anywhere except the switch** → opens the card's editor (broadcast cards: full editor with extras list and copy-action-string button; find-phone / flashlight: minimal info dialog with "what this does" text and an OK button).
- Toggle suppresses / enables the card's effect on press; does not touch the watch directly.
- All four cards reuse a shared composable `ui/settings/PressCard.kt` parameterised by title, subtitle, dialog content, on/off state, and a tap handler.

#### 2.2.1 Broadcast editor dialog (Short / Long cards)

```
┌──────────────────────────────────────────┐
│  Short button press                      │   ← titleLarge
│                                          │
│  Action string                           │   ← labelMedium
│  ┌────────────────────────────────────┐  │
│  │ com.beamburst.casswatch.RUN_SHORT  │  │   ← read-only TextField
│  └────────────────────────────────────┘  │
│  [ Copy to clipboard ]                   │
│                                          │
│  Extras                                  │   ← labelMedium
│  ┌────────────────────────────────────┐  │
│  │ key=mode  type=String  value=quick│✕│  │   ← extras row
│  └────────────────────────────────────┘  │
│  ┌────────────────────────────────────┐  │
│  │ key=level type=Int     value=911  │✕│  │
│  └────────────────────────────────────┘  │
│  + Add extra                              │
│                                          │
│  How to receive                          │   ← labelMedium
│  Tasker → Profile → Event → System →     │
│  Intent Received → Action: <copy above>  │
│  …short blurb for MacroDroid + Automate  │
│                                          │
│              [ Cancel ]   [ Save ]       │
└──────────────────────────────────────────┘
```

- The action string is **read-only** — it's owned by the app, not the user.
- `[ Copy to clipboard ]` uses `ClipboardManager.setPrimaryClip(...)` (new pattern in this codebase; see §6).
- Extras list: each row shows `key`, a type-picker (String / Int / Boolean), a value field, and a delete X. `Add extra` appends a fresh String row.
- "How to receive" is static help text with one paragraph per supported automation app (Tasker / MacroDroid / Automate). 3–5 lines total.
- New file `ui/settings/BroadcastIntentDialog.kt`.

#### 2.2.2 Find-phone card behaviour

`Find my phone` card with a single dialog showing "Rings the phone on long press while connected. Disable to silence." No editor. Reuses `utils/PhoneFinder.kt` (moved from `ui/actions/`).

#### 2.2.3 Flashlight card behaviour

`Flashlight` card with a single dialog showing "Toggles the device flashlight on short press while connected. Disable to ignore." Reuses `utils/FlashlightHelper.kt`.

### 2.3 `Notify on auto-sync` (restyled)

Today this row lives at the bottom of `TimeAdjustment.kt` with its own visual style. Restyle to match the new `SettingCard` layout introduced in Initiative #1: same outer padding, `titleMedium` label, M3 `Switch` on the right. The state binding (`LocalDataStorage.getTimeAdjustmentNotification`) is unchanged.

The string changes from `R.string.notify_me` (today) to a clearer `R.string.notify_on_auto_sync` ("Notify on auto-sync"). Update all 10 locales.

---

## 3. Press dispatch (`WatchButtonDispatcher.kt`)

### 3.1 File and shape

```kotlin
// app/src/main/java/com/beamburst/casswatch/WatchButtonDispatcher.kt   (new)
class WatchButtonDispatcher(
    private val context: Context,
    private val api: IGShockAPI,
    private val cardRepository: PressCardRepository,
) {
    init {
        ProgressEvents.runEventActions(Utils.AppHashCode(), arrayOf(
            EventAction("ButtonPressedInfoReceived") { onPress() },
        ))
    }

    private suspend fun onPress() {
        when {
            api.isActionButtonPressed()   -> handleShort()
            api.isFindPhoneButtonPressed()-> handleLong()
            api.isAutoTimeStarted()       -> /* delegated to AutoSyncCoordinator */
            api.isNormalButtonPressed()
              || api.isAlwaysConnectedConnectionPressed() -> /* connection housekeeping; reuse existing logic verbatim from ActionRunner */
        }
    }

    private fun handleShort() {
        val cards = cardRepository.shortPress()    // PressCardState
        if (cards.broadcast.enabled) sendBroadcast(BroadcastSpec.Short, cards.broadcast.extras)
        if (cards.flashlight.enabled) FlashlightHelper.toggle(context)
    }

    private fun handleLong() {
        val cards = cardRepository.longPress()
        if (cards.broadcast.enabled) sendBroadcast(BroadcastSpec.Long, cards.broadcast.extras)
        if (cards.findPhone.enabled) PhoneFinder.ring(context)
    }
}
```

- Subscribed via the existing `ProgressEvents.runEventActions(Utils.AppHashCode(), …)` pattern (`ActionRunner.kt:19` precedent).
- Delegation rule for `isAutoTimeStarted`: the dispatcher does **not** run the auto-sync chain itself. It either ignores that branch (if `AutoSyncCoordinator` subscribes independently) or forwards a method call. Cleaner design: `AutoSyncCoordinator` has its own `EventAction("ButtonPressedInfoReceived")` subscription and self-filters on `api.isAutoTimeStarted()`. Two subscribers on the same event = fine; each runs independently.
- `cardRepository: PressCardRepository` — new class wrapping `LocalDataStorage` reads/writes for all four cards. Lives under `data/local/PressCardRepository.kt` (new).
- The `connection housekeeping` branch (`isNormalButtonPressed` / `isAlwaysConnectedConnectionPressed`) keeps its existing behaviour from `ActionRunner.runActionForConnection` / `runActionForAlwaysConnected`. Whatever those methods do today (mostly state-machine bookkeeping for the connection lifecycle) is copied verbatim into the dispatcher's two branches. No semantic change.

### 3.2 `BroadcastSpec` and `sendBroadcast(...)`

```kotlin
enum class BroadcastSpec(val action: String) {
    Short("com.beamburst.casswatch.RUN_SHORT"),
    Long ("com.beamburst.casswatch.RUN_LONG"),
}

private fun sendBroadcast(spec: BroadcastSpec, extras: List<BroadcastExtra>) {
    val intent = Intent(spec.action).apply {
        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        extras.forEach { ex ->
            when (ex.type) {
                ExtraType.STRING  -> putExtra(ex.key, ex.value)
                ExtraType.INT     -> putExtra(ex.key, ex.value.toIntOrNull() ?: 0)
                ExtraType.BOOLEAN -> putExtra(ex.key, ex.value.toBooleanStrictOrNull() ?: false)
            }
        }
    }
    context.sendBroadcast(intent)
}
```

Wide-open broadcast — no `setPackage(...)` call. `FLAG_INCLUDE_STOPPED_PACKAGES` ensures Tasker etc. receive even if not foregrounded. Receivers that don't expect us simply ignore an unknown action.

### 3.3 Instantiation

`WatchButtonDispatcher` and `AutoSyncCoordinator` are instantiated in `GShockApplication.kt` at the same place `ActionRunner(context, repository)` was at line 123. Both are `@Singleton` classes with `@ApplicationContext`-injected `Context` and the existing `GShockRepository` / `IGShockAPI`. Hilt module: `di/DispatcherModule.kt` (new) provides them.

---

## 4. Auto-sync (`AutoSyncCoordinator.kt`)

### 4.1 File and shape

```kotlin
// app/src/main/java/com/beamburst/casswatch/AutoSyncCoordinator.kt   (new)
class AutoSyncCoordinator(
    private val context: Context,
    private val api: IGShockAPI,
) {
    init {
        ProgressEvents.runEventActions(Utils.AppHashCode(), arrayOf(
            EventAction("ButtonPressedInfoReceived") {
                if (api.isAutoTimeStarted()) runChain()
            },
        ))
    }

    private suspend fun runChain() {
        // 1. Set time with fine adjustment
        val offset = LocalDataStorage.getFineTimeAdjustment(context)
        api.setTime(timeMs = System.currentTimeMillis() + offset)

        // 2. Set events (if Events tab populated and watch supports reminders)
        if (WatchInfo.hasReminders) api.setEvents(EventsModel.events)

        // 3. Apply hourly-signal rule (Initiative #3)
        applyHourlySignalToWatch(api, HourlySignalSettings.load(context), LocalDateTime.now())

        // 4. Optionally: post auto-sync notification if user enabled it
        if (LocalDataStorage.getTimeAdjustmentNotification(context)
            && !WatchInfo.alwaysConnected) {
            postAutoSyncNotification(context)
        }
    }
}
```

### 4.2 What's gone from the original chain

- `PrayerAlarmsAction.run()` — deleted. Prayer alarms feature gone entirely (Adhan2 dropped).
- `runActionsForActionButton` / `runActionForConnection` etc. — moved into `WatchButtonDispatcher` (§3).
- The "iterate enabled actions and run each" loop — gone; the chain is now a fixed sequence of 4 steps.

### 4.3 Notification helper

`postAutoSyncNotification(context)` is a new pure function in `utils/AutoSyncNotification.kt`. Replaces the inline notification logic at `ActionViewModel.kt:631-649`. Same notification channel id, same icon, same content. Just relocated.

---

## 5. Broadcast intent reference (for users)

For documentation embedded in the BroadcastIntentDialog (§2.2.1) and for `docs/` if we decide to add a user-facing reference:

```
Action: com.beamburst.casswatch.RUN_SHORT     (short press while connected)
Action: com.beamburst.casswatch.RUN_LONG      (long  press while connected)

Extras: user-defined key/value pairs, typed (String / Int / Boolean).

The broadcast is sent unconditionally to all installed apps
(no setPackage). Use your automation app's intent-receive trigger,
filter on the action string, and parse extras as needed.
```

This text is also the body of the "How to receive" section in the dialog.

---

## 6. Manifest and build.gradle

### 6.1 Manifest

- Remove permissions: `CAMERA`, `CALL_PHONE`, `MODIFY_AUDIO_SETTINGS` (lines 49, 50, 70 today).
- Default-keep `ACCESS_COARSE_LOCATION` pending grep verification (§1.3).
- **No new receiver registration.** We're a sender, not a receiver, of the broadcast intents — recipients register their own filters.

### 6.2 build.gradle

- Drop `libs.adhan2` (line 102).
- Drop `androidx.camera.*` block (lines 96–100).

---

## 7. Critical files

**Delete (18 files in ui/actions/):** see §1.1 list. After deletion, the directory `ui/actions/` no longer exists.

**Move (2 files):**
- `ui/actions/FlashlightHelper.kt` → `utils/FlashlightHelper.kt`
- `ui/actions/PhoneFinder.kt` → `utils/PhoneFinder.kt`
  Each file is simplified to a single side-effect method; remove `View`, `RunEnvironment`, and Action-base-class plumbing.

**New:**
- `WatchButtonDispatcher.kt` (root package, sibling of `GShockApplication.kt`)
- `AutoSyncCoordinator.kt` (root package)
- `data/local/PressCardRepository.kt` — wraps the 4 cards' enabled state + extras lists into a `LocalDataStorage`-backed repository
- `data/local/PressCardState.kt` — pure data classes (`BroadcastExtra`, `ExtraType`, etc.)
- `ui/settings/SetTimeRow.kt`
- `ui/settings/PressCard.kt` — shared composable for all four cards
- `ui/settings/BroadcastIntentDialog.kt` — short/long broadcast editor
- `utils/AutoSyncNotification.kt` — relocated notification helper
- `di/DispatcherModule.kt` — provides `WatchButtonDispatcher` + `AutoSyncCoordinator`

**Modify:**
- `GShockApplication.kt:123` — instantiate the two new top-level classes; remove `ActionRunner(...)`.
- `Screens.kt` — remove Actions route.
- `BottomNavigationItem.kt` — remove Actions tab.
- `BottomNavigationBarWithPermissions.kt` — remove Actions route, remove the Actions-only permission requests.
- `MainEventHandler.kt` — remove the `RunActions` → `RUN_ACTIONS` state transition.
- `ui/settings/SettingsScreen.kt` — render the new top-of-Settings rows in the order from §2.
- `ui/settings/TimeAdjustment.kt` — restyle "Notify me" → "Notify on auto-sync"; the toggle binding is unchanged.
- `ui/settings/SettingsViewModel.kt` — add `setTimeNow()` action handler (§2.1).
- `AndroidManifest.xml` — remove three permissions (§6.1).
- `app/build.gradle` — drop two dependencies (§6.2).
- `res/values/strings.xml` + 10 locales — delete the 25-ish Actions-only strings (§1.5), add new strings for the broadcast dialog, the Set-time-row caption, and the rename of `notify_me` → `notify_on_auto_sync`.

**Tests (new):**
- `app/src/test/java/.../WatchButtonDispatcherTest.kt` — covers: short with both cards ON; short with broadcast OFF + flashlight ON; long with all ON; long with all OFF (no-op); auto-time-started branch ignored.
- `app/src/test/java/.../AutoSyncCoordinatorTest.kt` — covers: chain order; missing fine-adjustment defaults to 0; notification posted iff toggle ON and `!alwaysConnected`.
- `app/src/test/java/.../data/local/PressCardRepositoryTest.kt` — covers: persistence of per-card enabled flags + extras lists round-trips through DataStore; missing keys → defaults; extras of all three types.

---

## 8. Reuse from existing code

- `ProgressEvents.runEventActions(Utils.AppHashCode(), …)` — `ActionRunner.kt:19` precedent for both new top-level classes.
- `LocalDataStorage` — used for `PressCardRepository` and the existing fine-adjustment / notify keys. No new API needed; just three new key prefixes (`PressCard.Short.*`, `PressCard.Long.*`, `PressCard.FindPhone.*`, `PressCard.Flashlight.*`).
- `repository.setTime(...)` — call-pattern from `TimeViewModel.kt:87` reused verbatim in `setTimeNow()`.
- `WatchInfo.hasReminders` / `WatchInfo.alwaysConnected` — capability flags, reused in `AutoSyncCoordinator` exactly as in `ActionViewModel.kt:631-649`.
- `applyHourlySignalToWatch(...)` — Initiative #3's helper, called once in the new auto-sync chain.
- `Spacing` token + M3 typography — every new composable obeys these (Initiative #1).
- `MaterialTheme.colorScheme.primary` (deep-blue) — used by all new switches.
- `AppSnackbar(...)` — for the `Set time` row's success / error feedback.

---

## 9. Verification

1. **Build/lint/tests green:** `./gradlew ktlint lint test assembleDebug` passes.
2. **Bottom nav has 4 tabs:** Time / Alarms / Events / Settings. No Actions.
3. **`ui/actions/` is gone:** `find app/src/main/java -path '*ui/actions/*'` returns empty.
4. **No dead imports:** `git grep -nE 'com\.batoulapps\.adhan2|androidx\.camera|FlashlightView|PhotoView|PhoneFinderView|RunEnvironment|ActionRunner|ActionsViewModel|PrayerAlarms' app/src/main` returns empty.
5. **Manifest:** `grep -E 'CAMERA|CALL_PHONE|MODIFY_AUDIO_SETTINGS' app/src/main/AndroidManifest.xml` returns empty.
6. **build.gradle:** `grep -E 'adhan2|androidx\.camera' app/build.gradle` returns empty.
7. **Settings tab top order:** Set time → Notify on auto-sync → Short button press → Long button press → Find my phone → Flashlight → divider → existing rows.
8. **Set time row:** Tap with watch connected → `AppSnackbar("Time sent to watch")`. Tap disconnected → `AppSnackbar("Watch not connected")`. `repository.setTime(...)` is called once in either case (or zero if disconnected — verify with logs).
9. **Short broadcast:** Set Short card ON, set extras `{mode=String=quick, level=Int=42, urgent=Boolean=true}`. Press the watch's action button. Verify in `adb shell dumpsys activity broadcasts` (or via a tiny test receiver app) that an intent with action `com.beamburst.casswatch.RUN_SHORT` is sent with the three correctly-typed extras.
10. **Long broadcast + find-phone co-fire:** Long card ON + Find phone ON. Long-press watch button → broadcast emitted **and** phone rings.
11. **Toggle suppression:** Set Long card OFF, Find phone ON. Long-press → phone rings; broadcast not emitted.
12. **Flashlight short press:** Flashlight card ON. Action button press → flashlight on. Press again → off.
13. **Auto-sync chain:** Trigger an `AUTO_TIME_ADJUSTMENT` event (e.g. wait for the watch's 4×/day reconnect, or use a debug button to fire it). Verify in logs:
    - `api.setTime(...)` called once with `now + fineAdjustment`.
    - `api.setEvents(...)` called once iff `WatchInfo.hasReminders`.
    - `applyHourlySignalToWatch(...)` called once.
    - Notification posted iff `Notify on auto-sync` is ON and `!alwaysConnected`.
14. **Copy-to-clipboard:** Open Short broadcast dialog, tap `Copy to clipboard`. Paste into another app — should yield `com.beamburst.casswatch.RUN_SHORT`.
15. **No raw sp/dp leftovers:** `git grep -nE 'fontSize\s*=\s*[0-9]+\.sp|\.dp\)' app/src/main/java/com/beamburst/casswatch/{,ui/settings/PressCard.kt,ui/settings/BroadcastIntentDialog.kt,ui/settings/SetTimeRow.kt}` returns nothing.

---

## 10. Cross-cutting / dependencies

- **Depends on Initiative #1**: `Spacing` token, M3 typography, package id `com.beamburst.casswatch` (the broadcast action strings hard-code this).
- **Depends on Initiative #3**: `applyHourlySignalToWatch(...)` helper called from `AutoSyncCoordinator`. If #3 hasn't merged when #4 lands, leave the call site stubbed (`// TODO: wire when #3 lands`) and add it as a tiny follow-up commit. Order-of-merging: #3 should land first so this stub doesn't exist.
- **Independent of Initiative #2**: alarms screen unaffected.
- **Heads-up for Initiative #5**: every press-card emission and every successful auto-sync chain is a candidate sync-history entry. Don't write a history entry from `WatchButtonDispatcher` in #4 — wait for #5's `SyncHistoryStore` and add the calls then.
- **Heads-up for Initiative #6**: when multi-watch lands, the four press cards are global (not per-watch); the user-configured broadcast triggers fire regardless of which watch caused the press. Document this in `PressCardRepository` comments so #6 doesn't accidentally make them per-watch.

---

## 11. PR shape

Single PR titled `Initiative #4 — Actions tab removal + Settings broadcast intents`. Suggested commit split:

1. `refactor: extract WatchButtonDispatcher and AutoSyncCoordinator from ActionRunner/ActionViewModel` — pure relocation; behaviour-preserving so far. ActionsScreen still renders.
2. `refactor: relocate FlashlightHelper and PhoneFinder to utils/` — small file moves with import updates.
3. `feat: PressCardRepository + per-card data layer (DataStore-backed)` — pure data layer, fully unit-testable.
4. `feat: Settings press cards (Short broadcast / Long broadcast / Find phone / Flashlight)` — UI lands; dispatcher gains its emit / fan-out logic.
5. `feat: Set time on watch settings row` — single-row addition + `setTimeNow()` VM method.
6. `chore: rename "Notify me" → "Notify on auto-sync"; restyle to match other rows` — small string + style change in 10 locales.
7. `chore: delete ui/actions/, drop Adhan2 + CameraX, remove Actions-only permissions and strings, drop Actions tab from bottom nav` — the big delete commit. This is the PR's headline diff: ~3000 lines deleted.
8. `chore: grep-verify ACCESS_COARSE_LOCATION usage; drop if unused` — separate cleanup commit; gated on the grep result.

Each commit builds and tests on its own.

---

## 12. Open questions / follow-ups

- **Connection-housekeeping branch in `WatchButtonDispatcher`** — depends on the exact behaviour of `runActionForConnection` and `runActionForAlwaysConnected` in `ActionViewModel`. Inspect these during implementation; if they're trivial, fold inline; if they touch state machines, factor a helper.
- **Notify-on-auto-sync notification channel id** — keep the same id as today, so existing user notification settings carry over. Verify with the existing channel constant in `utils/AutoSyncNotification.kt` after the move.
- **Tasker / MacroDroid / Automate help text** — exact wording and language for the "How to receive" section. Default English; localise during the strings update if practical, or fall back to English-only with a note. Translator review can happen after the spec lands.
- **Documenting the broadcast contract in the README / project docs** — defer to a separate doc commit after the initiative ships, so the README doesn't churn during implementation.
