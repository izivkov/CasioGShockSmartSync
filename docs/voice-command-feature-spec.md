# Voice Command Layer — Spec

**Audience:** a developer or an AI coding agent working on the `voice`
branch of `github.com/izivkov/CasioGShockSmartSync`. This describes the
architecture **as actually implemented**, not a plan to implement — treat
it as ground truth for what's built, and update it in place as the code
changes rather than layering a second version alongside it.

---

## Goal

For every recognized voice command:

1. **Navigate** to the screen that corresponds to the command.
2. **Run the command on the watch** (the actual BLE write).
3. **Re-read the values from the watch.** This updates the UI.

There is deliberately **no optimistic local UI update** anywhere in this
flow — the UI never patches itself from the parsed command's parameters.
It only ever shows what a fresh read from the watch reports. This was a
conscious choice: the watch is always the source of truth, and an
optimistic patch can drift from what's actually on the watch if a write
silently fails or partially applies.

---

## Flow

```
mic tap
  → VoiceCommandManager.startListening()      (speech → text, auto-retries once on
                                                a transient SpeechRecognizer error)
  → IntentParser.parse(text)                  (text → VoiceCommand)
  → VoiceCommandTable[VoiceCommand::class]    (VoiceCommand → route + Action class + applyParams)
  → spec.applyParams(action, command, api)    (fills in the Action's fields)
  → ProgressEvents.onNext("NavigateTo", VoiceNavigation(spec.route, command))
  → actionsViewModel.runSingleActionSuspend(action)   (awaits the Action's runSuspend())
  → action's runSuspend() writes to the watch, then emits its own
    "<X>Updated" event (e.g. "AlarmsUpdated") carrying exactly what
    was written
  → the screen's ViewModel, already subscribed, re-reads from the
    watch and updates its StateFlow
```

`VoiceDispatcher.kt` runs this whole sequence; `VoiceCommand.kt` and
`VoiceCommandTable.kt` define the command types and their routing.

---

## Command → Action → Screen

| VoiceCommand | Route | Action | Refresh event |
|---|---|---|---|
| `SetAlarm(hour, minute)` | Alarms | `SetAlarmAction` | `AlarmsUpdated` (carries `AlarmsWritten`) |
| `ClearAllAlarms` | Alarms | `ClearAllAlarmsAction` | `AlarmsUpdated` (carries `AlarmsWritten`) |
| `SetTimer(hours, minutes, seconds)` | Time | `SetTimerAction` | `TimerUpdated` (no payload yet — see Open Items) |
| `SetSetting(name, enabled)` | Settings | `SetSettingsAction` | `SettingsUpdated` (no payload) |

`IntentParser.kt` sums **every** `<amount> <unit>` pair it finds once it's
confirmed the phrase mentions "timer" (not just the last one), so "3
minutes 10 seconds" resolves to both parts, not just the trailing one.

---

## RunEnvironment.DIRECT_INVOCATION

Actions run in more than one context (watch button press, in-app button,
voice command). `ActionsViewModel.RunEnvironment` includes
`DIRECT_INVOCATION` specifically for voice/programmatic single-action
runs. Each voice-reachable Action overrides `shouldRun()` to allow
`DIRECT_INVOCATION`:

```kotlin
override fun shouldRun(environment: RunEnvironment): Boolean =
    environment == RunEnvironment.DIRECT_INVOCATION || super.shouldRun(environment)
```

`SetSettingsAction` additionally accepts a `fullSettings: Settings?`
field so it can be driven either by a single voice-parsed field
(`settingName`/`settingValue`) or by a complete `Settings` object (used
by `SettingsViewModel.sendToWatch()`'s manual "send to watch" button,
which now also routes through this Action under `DIRECT_INVOCATION`
rather than writing to the watch directly).

---

## Refresh-after-write

Each destination screen's ViewModel subscribes to its Action's
"`<X>Updated`" event and re-reads from the watch when it fires. Two
things make this reliable:

**1. Verify against what was actually written, not just "did anything
change".** `SetAlarmAction` / `ClearAllAlarmsAction` emit an
`AlarmsWritten(alarms)` payload — the exact list they wrote.
`AlarmViewModel.refreshAlarmsAfterExternalWrite()` re-reads and compares
the read-back against that payload on the fields the watch actually
stores (hour/minute/enabled), retrying briefly (5 attempts, 400ms apart)
until it matches or it runs out of attempts. `sendAlarmsToWatch()` (the
manual button) uses the same function, passing the list it just sent as
`expected`.

**2. Every ProgressEvents subscription needs a unique name.**
`ProgressEvents.Subscriber.runEventActions(name, actions)` — from the
external `GShockAPI` library — silently drops a **second** registration
under a name it has already seen:

```kotlin
fun runEventActions(name: String, eventActions: Array<EventAction>) {
    if (state.subscribers.contains(name)) return   // silently ignored
    ...
}
```

`ProgressEvents` is a process-wide singleton, so this state persists for
the whole app process — it is **not** reset per Activity or ViewModel.
Every one of our subscribers (`AlarmViewModel`, `TimeViewModel`,
`BottomNavigationBarWithPermissions`) is tied to a *recreatable* scope: a
`@HiltViewModel` can be recreated, and the nav-hosting Composable can be
recreated on a config change (rotation, etc.). The first time any of
these is ever recreated during a process's lifetime, its new instance's
subscription call under the old hardcoded name becomes a silent no-op —
the event it should be receiving (`AlarmsUpdated`, `NavigateTo`, ...)
goes nowhere for the rest of that process's life, while a stale closure
from the previous instance (bound to a ViewModel or NavController that's
no longer the one on screen) is the only thing left registered.

This was confirmed as the actual root cause of two reported symptoms:
alarms not refreshing on screen after a voice-set write, and voice
commands never navigating to the Settings screen. Both are explained by
this single bug, not by any BLE timing or caching issue.

**Fix:** `utils/ProgressEventsExt.kt` adds
`subscribeToProgressEvents(baseName, actions): String`, which appends a
UUID to `baseName` before subscribing, so registration always succeeds,
and returns the generated name so the caller can unsubscribe it via
`ProgressEvents.subscriber.stop(name)` when its own scope ends
(`onCleared()` for a ViewModel, `DisposableEffect.onDispose` for a
Composable).

**Adopted so far:** `AlarmViewModel`, `BottomNavigationBarWithPermissions`.
**Not yet migrated** (same latent bug, lower observed impact so far):
`TimeViewModel` (subscribes as `"TimeViewModel"`). `WatchFeatureManager`
subscribes as `"WatchFeatureManager"` too, but it's an
`@Singleton` — never recreated — so it isn't at risk the same way and
doesn't need this.

---

## Open items

- **`TimeViewModel`'s `"TimerUpdated"` handler** re-reads the timer with
  a single fixed `delay(500)` and no retry — the same shape
  `AlarmViewModel` used to have before it was hardened. It hasn't been
  reported as broken, but it's exposed to the same class of BLE
  write-then-read race and doesn't yet have `SetTimerAction` emitting a
  payload to verify against. Worth porting both fixes (unique
  subscription name + payload-verified retry) if timer refresh turns out
  to be flaky too.
- **`TimeViewModel` subscription name** isn't migrated to
  `subscribeToProgressEvents()` yet — same latent risk as the
  pre-fix `AlarmViewModel`/`BottomNavigationBarWithPermissions`.
- **`SettingsViewModel`'s `"SettingsUpdated"` handler** re-reads via
  `initializeSettings()` with no payload verification — lower risk since
  settings write comparatively simple state, but same general shape.

---

## Manual test plan

- "Set alarm for 7 30" → navigates to Alarms, alarm 1 shows 7:30 within
  ~2s of the watch confirming.
- "Clear all alarms" → navigates to Alarms, all alarms show disabled.
- "Set a timer for 3 minutes 10 seconds" → Time screen shows a
  3:10 timer, not 0:10.
- "Turn on auto light" (and variants: "autolight on", "turn off power
  saving") → navigates to Settings, the corresponding toggle reflects the
  new state.
- Rotate the device (or otherwise force an Activity recreation), then
  repeat all of the above — this is the scenario that used to silently
  break navigation and refresh before the subscription-name fix.
- 