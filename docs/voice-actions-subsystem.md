# Voice Subsystem & Actions Subsystem — CasioGShockSmartSync (`voice` branch)

## 1. Overview

Voice control is layered strictly on top of the existing Actions subsystem rather than replacing or duplicating it. The voice code's only job is: turn speech into a strongly-typed command, look up which `Action` handles that command, mutate that `Action`'s fields, and hand execution off to the same action-running machinery the UI buttons already use. Nothing in the voice layer talks to `GShockRepository` or the watch directly except one narrow exception (the reminder/event flow).

Files:
- Voice subsystem: `app/src/main/java/org/avmedia/gshockGoogleSync/voice/`
  - `VoiceCommand.kt`, `VoiceCommandManager.kt`, `VoiceCommandTable.kt`, `VoiceDispatcher.kt`, `IntentParser.kt`, `VoiceSpeechFeedback.kt`
- Actions subsystem: `app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt`

## 2. Voice subsystem components

**`VoiceCommandManager`** — thin wrapper around Android's `SpeechRecognizer`. `startListening(onResult, onError)` starts a recognition session pinned to `en-US` (regardless of the phone's system locale, since `IntentParser` only understands English), with widened silence timeouts for slower speakers. It auto-retries once on transient errors (`ERROR_SERVER_DISCONNECTED`, `ERROR_RECOGNIZER_BUSY`) before surfacing a real error. It has no knowledge of commands or actions — it only produces raw recognized text.

**`VoiceCommand`** (`voice/VoiceCommand.kt`) — the sealed class that is the contract between parsing and dispatch:

```kotlin
sealed class VoiceCommand {
    data class SetAlarm(val hour: Int, val minute: Int) : VoiceCommand()
    object ClearAllAlarms : VoiceCommand()
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : VoiceCommand()
    data class SetSetting(val name: String, val value: String) : VoiceCommand()
    data class AddReminder(
        val title: String? = null,
        val startDate: LocalDate? = null,
        val repeatPeriod: RepeatPeriod? = null
    ) : VoiceCommand()
}
```

**`IntentParser`** — turns raw recognized text into a `VoiceCommand?` (or `null` if nothing matches). Notably fixed to sum *all* `<amount> <unit>` pairs in a timer phrase rather than only capturing the last regex match ("3 minutes 10 seconds" now resolves to both parts). Also exposes `parseDate(text)` used separately during the multi-turn reminder conversation.

**`VoiceDispatcher`** — the orchestrator. `dispatch(text: String)`:
1. Handles a bare "cancel" and any in-progress multi-turn reminder conversation first (`currentReminder` state machine — reminders are the one command that needs several back-and-forth turns to collect title/date/repeat, so they're handled outside the normal single-shot path).
2. Otherwise calls `intentParser.parse(text)`. `null` → snackbar + spoken "Command not understood".
3. Looks up the parsed command's `KClass` in `voiceCommandTable`. No entry → same "not understood" fallback.
4. Checks `spec.isSupported(command)` against `WatchInfo` (e.g. does this watch model even have alarms/settings/reminders). Unsupported → speaks "`<feature>` not supported for this watch".
5. Fetches the `Action` singleton via `actionsViewModel.getAction(spec.actionClass)` and checks `action.enabled`.
6. Applies params (`spec.applyParams(action, command, api)`), runs it (`actionsViewModel.runSingleActionSuspend(action)`), waits 1s (to let the STT "success" beep finish), then speaks a result string built by `getFeedbackText(command)`.

Navigation was deliberately removed from this method — an earlier version emitted a `"NavigateTo"` event so voice commands would jump to the relevant screen; that's gone, so a voice-set alarm/setting no longer auto-switches screens. The mic itself only lives on the Time screen (wired up in `TimeViewModel`), so this wasn't a functional gap for alarms/settings/timer.

**`VoiceCommandTable`** — see §4, it's the seam between the two subsystems.

**`VoiceSpeechFeedback`** — text-to-speech wrapper used for all the `speak(...)` calls above.

## 3. Actions subsystem components

**`ActionsViewModel.Action`** (abstract, in `ui/actions/ActionViewModel.kt`) — every user-facing action (voice-triggered or button-triggered) is a subclass with `title`, `enabled`, a `RunMode` (sync/async), a `run(context)` entry point, and lifecycle hooks (`save`/`load` for persisted settings). Relevant to voice: `shouldRun(runEnvironment: RunEnvironment): Boolean` — each action must explicitly opt in to being invoked outside normal UI interaction.

**`RunEnvironment`** — an enum including `DIRECT_INVOCATION` and `VOICE_COMMAND`. Actions that support voice (`SetAlarmAction`, `ClearAllAlarmsAction`, `SetSettingsAction`, `SetTimerAction`) override `shouldRun()` to return `enabled` for both of those; actions that don't override it default to not running outside a normal UI click. This is the guard that keeps voice from accidentally triggering actions that were never designed for headless invocation.

**`ActionsViewModel`** holds the action registry (`getAction(actionClass)` returns the shared instance for a given `Action` subclass) and `runSingleActionSuspend(action)`, a suspend entry point voice uses instead of the fire-and-forget `run(context)` UI buttons use, so `VoiceDispatcher` can `await` completion before speaking feedback.

**The individual actions voice drives**, all inner classes of `ActionsViewModel`:
- `SetAlarmAction(alarmHour, alarmMinute)` — reads current alarms via `api.getAlarms()`, finds a matching or disabled slot to overwrite, writes back via `api.setAlarms()`, then emits `ProgressEvents.onNext("AlarmsUpdated", AlarmsWritten(alarmList))`.
- `ClearAllAlarmsAction` — disables all alarms, same write + event pattern.
- `SetSettingsAction(settingName, settingValue, fullSettings)` — accepts either one named field (voice path) or a whole `Settings` object (`SettingsViewModel.sendToWatch()` also routes through this action under `DIRECT_INVOCATION` rather than writing directly). Applies the single field over the freshly-fetched current settings, sends via `api.setSettings()`, emits `"SettingsUpdated"`.
- `SetTimerAction(timerValueS)` — voice supplies total seconds (hours/minutes/seconds summed).
- `SetEventsAction` — used by the reminder flow, though the actual write for `AddReminder` happens inline in `VoiceDispatcher.finalizeReminder()` rather than through `applyParams`, since it needs the multi-turn collected state.

## 4. The bridge: `VoiceCommandTable`

This is the only place `voice/` and `ui/actions/` reference each other. It's a `Map<KClass<out VoiceCommand>, VoiceCommandSpec>`, and each entry bundles:
- `route` — the screen this command belongs to (informational now that auto-navigation is removed)
- `actionClass` — which `Action` subclass to fetch from `ActionsViewModel`
- `isSupported` — a per-command capability check against `WatchInfo`
- `featureName` — used to build the "`<feature>` not supported" message
- `applyParams` — a `suspend (Action, VoiceCommand, GShockRepository) -> Unit` that casts both the action and command to their concrete types and copies fields across, e.g.:

```kotlin
applyParams = { action, cmd, _ ->
    (action as SetAlarmAction).let {
        val c = cmd as VoiceCommand.SetAlarm
        it.alarmHour = c.hour
        it.alarmMinute = c.minute
    }
}
```

Adding a new voice command means: add a case to `VoiceCommand`, teach `IntentParser` to produce it, add an `Action` if one doesn't exist, and add one `VoiceCommandTable` entry connecting them. `VoiceDispatcher` itself never needs to change.

## 5. Watch I/O and the feedback loop

`Action.runSuspend()` calls into `GShockRepository` (the app's wrapper — this is where a `Mutex` was added around `getAlarms()` to fix a read-vs-read concurrency corruption), which in turn calls the external `GShockAPI` JitPack library for the actual BLE I/O.

After a successful write, the action emits a `ProgressEvents.onNext(eventName, payload)`. This is push-based: any ViewModel elsewhere in the app (e.g. `AlarmViewModel`, `SettingsViewModel`) that subscribed to that event name re-reads from the watch to refresh its own UI state — voice never touches those ViewModels directly, and `VoiceDispatcher` doesn't wait on them either; it only waits for `runSingleActionSuspend` to complete before speaking. This is the same event bus where a subscriber-collision bug lived (`ProgressEvents.Subscriber.runEventActions` silently drops a second subscription registered under an already-seen name), which is why `subscribeToProgressEvents()` now appends a UUID per subscription.

## 6. Worked example — "Set an alarm for 7 AM"

1. `VoiceCommandManager` returns the text "set an alarm for 7 AM".
2. `IntentParser.parse()` → `VoiceCommand.SetAlarm(hour = 7, minute = 0)`.
3. `VoiceDispatcher` looks up `SetAlarm::class` in `voiceCommandTable` → gets the spec with `actionClass = SetAlarmAction::class.java`.
4. `spec.isSupported` checks `WatchInfo.alarmCount > 0` — passes.
5. `actionsViewModel.getAction(SetAlarmAction::class.java)` returns the shared instance; `action.enabled` checked.
6. `spec.applyParams` sets `alarmHour = 7`, `alarmMinute = 0` on that instance.
7. `runSingleActionSuspend(action)` → `SetAlarmAction.runSuspend()` reads current alarms, finds a slot, writes back, emits `"AlarmsUpdated"`.
8. `VoiceDispatcher` waits 1s, then `getFeedbackText(command)` builds "Alarm set in watch for 7:00 AM", spoken via `VoiceSpeechFeedback`.
9. Separately, `AlarmViewModel` (subscribed to `"AlarmsUpdated"`) receives the event and reloads alarms from the watch to update its Compose state — independent of and not awaited by the voice flow.
