# Voice Command Layer — Feature Spec

## Overview

Add a voice-driven command layer to CasioGShockSmartSync. First iteration scope:

- A "Tell me what to do" button on the main watch/time screen (the mic
  trigger lives only here — it cannot fire from any other screen, so a
  live voice command and a live manual edit elsewhere, e.g. on Settings,
  can never collide).
- On tap, capture speech, parse it into a known intent, and execute the
  matching command.
- Supported intent categories for v1:
    - **Alarms** — e.g. "wake me up at 6am tomorrow"
    - **Reminders** — e.g. "remind me dentist appointment on Tuesdays"
    - **Timer** — e.g. "set a timer for 7 minutes"
    - **Settings** — e.g. "enable auto light"

For every recognized command, three things must happen:
1. **Navigate** to the screen that corresponds to the command (e.g. Alarms).
2. **Update the UI** (that screen's ViewModel), so the user sees the change.
3. **Run the command on the watch** (the actual BLE write).

## Constraints (unchanged)

- **No proprietary/heavy AI libraries.** No bundled LLM. Use native
  Android speech-to-text and a rule-based (regex / keyword) intent
  parser — no cloud NLU service.
- **F-Droid compatibility.** `SpeechRecognizer`/`RecognizerIntent` are
  AOSP APIs; no proprietary code is bundled. Must gracefully degrade via
  `SpeechRecognizer.isRecognitionAvailable()`. Optional future path: Vosk
  (fully offline) if strict FOSS is ever required.

## Audit of the `voice` branch as it stands

This branch already has more built than a first read suggests, but it had
one real architectural gap when this design session started. Corrections
worth keeping in mind:

- The class is `ActionsViewModel`, defined inside a file confusingly named
  `ActionViewModel.kt` (singular). It's `@Singleton` — deliberately: its own
  doc comment explains it was converted *from* `@HiltViewModel` specifically
  because background BLE button-presses need a listener that exists with no
  Activity/UI composed.
- `RunEnvironment.VOICE_COMMAND` already existed. `SetAlarmAction`,
  `SetSettingsAction`, `StartVoiceAssistAction` already existed.
  `ActionsViewModel.runFilteredActions(RunEnvironment)` already existed and
  is exactly what `VoiceDispatcher` calls.
- `IntentParser` already does regex-based alarm/reminder/settings parsing
  with a time normalizer. It does **not** yet handle fuzzy/relative phrasing
  ("wake me up in 7 hours") — all alarm patterns currently anchor on a
  literal clock time, not a duration. Still open — see Open Items.
- **The gap:** navigation was not wired anywhere (no `navController` was
  reachable from `VoiceDispatcher`, which lives outside any composable),
  and `SetAlarmAction.run()` talked straight to
  `api.getAlarms()`/`api.setAlarms()`, bypassing `AlarmViewModel` entirely.
  So originally, only requirement #3 (watch write) was satisfied for
  Alarms; #1 and #2 were not implemented. Resolved below.
- **Every screen that writes to the watch already follows the same
  convention**, independently of `Action`: mutate the screen's own
  ViewModel state, then call that same ViewModel's own already-existing
  "send to watch" method:
    - `AlarmViewModel`: `onTimeChanged()`/`toggleAlarm()` → separately,
      `sendAlarmsToWatch()` (also handles alarm names via `alarmNameStorage`
      and syncs the hourly-chime setting — logic `SetAlarmAction` currently
      lacks; still to be folded in — see Open Items).
    - `SettingsViewModel`: builds a full `Settings` object → `sendToWatch()`
      → `api.setSettings(settings)`. Confirmed: `GShockAPIImpl.setSettings()`
      already internally calls both `SettingsIO.set(settings)` and
      `TimeAdjustmentIO.set(settings)` — one call at the app layer is enough,
      no separate time-adjustment call needed from this app's side.
    - Timer (lives inside `TimeViewModel`, not a separate screen/ViewModel):
      `TimeAction.SetTimer(h,m,s)` (stage) → separately,
      `TimeAction.UpdateTimer(timeMs)` (commits, calls `api.setTimer()`).
    - Reminders: `EventsModel` is a plain Kotlin `object` — a process-wide
      singleton, not a screen-scoped ViewModel. Any composed Events screen
      reflects it automatically, regardless of navigation timing. This one
      never had the timing problem below.
- Neither Timer nor Settings had an `Action` subclass that performs their
  watch write when this session started — only Alarms and Reminders did
  (`SetAlarmAction`, `SetEventsAction`). Being added now (below).
- `ActionsScreen.kt`'s visible list is a **hardcoded set of specific
  `*View(...)` rows**, not a generic iteration over every registered
  action. This is the actual, already-existing mechanism by which
  `SetAlarmAction`/`SetSettingsAction` are "hidden" today — nobody wrote a
  row for them. No dedicated "hidden" flag exists or is needed.

## Design decision: enforce all watch writes through `Action`

All watch writes go through an `Action` — the same mechanism that already
handles background/headless triggers (`ACTION_BUTTON_PRESSED`,
`AUTO_TIME_ADJUSTMENT`, etc). Concretely:

- **New `RunEnvironment.DIRECT_INVOCATION`** — for actions invoked
  directly from app code (a screen's "Send to Watch" button, or the voice
  engine), as opposed to a watch button press. Since `RunEnvironment` is a
  plain enum with no `else` in the base `shouldRun()`'s `when`, adding this
  case requires a branch there too:
  ```kotlin
  enum class RunEnvironment {
      NORMAL_CONNECTION,
      ACTION_BUTTON_PRESSED,
      AUTO_TIME_ADJUSTMENT,
      FIND_PHONE_PRESSED,
      VOICE_COMMAND,
      ALWAYS_CONNECTED,
      DIRECT_INVOCATION,  // Called directly from app code (Send to Watch
                          // button, or the voice engine) — not a watch
                          // button press or background trigger.
  }

  open fun shouldRun(runEnvironment: RunEnvironment): Boolean {
      return when (runEnvironment) {
          RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
          RunEnvironment.VOICE_COMMAND -> enabled
          RunEnvironment.NORMAL_CONNECTION -> false
          RunEnvironment.AUTO_TIME_ADJUSTMENT -> false
          RunEnvironment.FIND_PHONE_PRESSED -> false
          RunEnvironment.ALWAYS_CONNECTED -> false
          RunEnvironment.DIRECT_INVOCATION -> false   // opt-in only, per action below
      }
  }
  ```
- `SetAlarmAction`, `SetSettingsAction`, `SetTimerAction` (new) each
  override `shouldRun()`:
  ```kotlin
  override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
      RunEnvironment.DIRECT_INVOCATION -> enabled
      RunEnvironment.VOICE_COMMAND -> enabled
      else -> false
  }
  ```
  This also fixes a real pre-existing bug: with no override, both actions
  fell through to the base default, which returns `enabled` for
  `ACTION_BUTTON_PRESSED` too — meaning a watch button short-press could
  already trigger them, contradicting `SetSettingsAction`'s own
  `// Hidden from UI, only for voice` comment.
- **`SetSettingsAction` revised to hold a full `Settings` object**
  (replacing today's `settingName: String, settingValue: Boolean` pair),
  set by whichever caller — voice or the screen's own "Send to Watch"
  button — before invoking `run()`. Both callers land on the same
  `api.setSettings(settings)` call:
  ```kotlin
  inner class SetSettingsAction(
      override var title: String,
      override var enabled: Boolean,
      var settings: Settings? = null,
  ) : Action(title, enabled, RunMode.ASYNC) {
      override fun run(context: Context) {
          val s = settings ?: return
          viewModelScope.launch {
              runCatching {
                  api.setSettings(s)
                  AppSnackbar(context.getString(R.string.settings_sent_to_watch))
              }.onFailure { Timber.e(it, "Failed to send settings to watch") }
          }
      }
      override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
          RunEnvironment.DIRECT_INVOCATION -> enabled
          RunEnvironment.VOICE_COMMAND -> enabled
          else -> false
      }
  }
  ```
  This also resolves the older complaint that `SetSettingsAction` only
  special-cased two hardcoded setting names via string matching — it now
  sends whatever complete `Settings` object it's given.
- **New `SetTimerAction`** — same shape as `SetAlarmAction`:
  ```kotlin
  inner class SetTimerAction(
      override var title: String,
      override var enabled: Boolean,
      var timeMs: Long = 0,
  ) : Action(title, enabled, RunMode.ASYNC) {
      override fun run(context: Context) {
          viewModelScope.launch {
              runCatching {
                  api.setTimer(timeMs)
                  AppSnackbar(context.getString(R.string.timer_sent_to_watch))
              }.onFailure { Timber.e(it, "Failed to send timer to watch") }
          }
      }
      override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
          RunEnvironment.DIRECT_INVOCATION -> enabled
          RunEnvironment.VOICE_COMMAND -> enabled
          else -> false
      }
  }
  ```
- **`SetSettingsAction` and `SetTimerAction` are hidden actions** — never
  shown on the Actions screen, only ever triggered by the screen's own
  "Send to Watch" button or by voice. Achieved the same way
  `SetAlarmAction`/`SetSettingsAction` are already hidden today: simply
  don't add a `*View(...)` row for them in `ActionsScreen.kt`'s hardcoded
  `createActionItems()` list. `enabled` stays hardcoded `true` at
  construction — since no UI ever touches it, there's no way for a user to
  disable these, so sharing the same `enabled` check between the manual
  button and voice (via `DIRECT_INVOCATION`) carries no risk of one
  silently breaking the other.
- **No ViewModel needs to be promoted to `@Singleton`.** An earlier
  direction explored this session (to make a screen's ViewModel reachable
  from the singleton `Action`/`VoiceDispatcher`) was dropped as
  unnecessary complexity — see "Bridging navigation → ViewModel" below.

## Bridging navigation → ViewModel (the "step 1 then step 2" problem)

`navController.navigate(route)` and the destination screen's `ViewModel`
coming into existence are **not the same instant** — navigation triggers
composition on a later frame, so the caller has no direct reference to the
new ViewModel to hand parameters to.

**Resolved: reuse the existing `ProgressEvents` bus** (from the `GShockAPI`
library, `ProgressEvent.kt` — the same mechanism already used elsewhere in
this codebase for `WatchInitializationCompleted`/`RunActions`/etc). Two
earlier alternatives were explored and dropped: a custom
`PendingVoiceCommand` singleton holder (redundant — `ProgressEvents`
already has an equivalent built-in payload store), and Navigation-Compose
route arguments + `SavedStateHandle` (more invasive — would touch the nav
graph's route strings and every target ViewModel's constructor).

`ProgressEvents.onNext(eventName, payload)` writes to an internal
`payloadMap` regardless of whether anyone is currently subscribed, and
`ProgressEvents.getPayload(eventName)` reads that map on demand, with no
subscription required. That's exactly what's needed here — nav needs a
**push** to an always-alive subscriber; the not-yet-created ViewModel
needs a **pull** once it exists.

```kotlin
data class VoiceNavigation(val route: String, val command: VoiceCommand)
```

`VoiceDispatcher` — one call does both the nav trigger and the data
hand-off:
```kotlin
ProgressEvents.onNext("NavigateTo", VoiceNavigation(spec.route, command))
```

`BottomNavigationBarWithPermissions` — composed for the whole app session,
so no timing risk; one new subscription:
```kotlin
LaunchedEffect(Unit) {
    ProgressEvents.runEventActions("BottomNavigationBar-Voice", arrayOf(
        EventAction("NavigateTo") {
            val nav = ProgressEvents.getPayload("NavigateTo") as? VoiceNavigation ?: return@EventAction
            navController.navigate(nav.route)
        }
    ))
}
```

Target ViewModel's `init` (e.g. `AlarmViewModel`) — pulls, doesn't
subscribe, and **must clear the payload after consuming it**:
```kotlin
init {
    loadAlarms()
    viewModelScope.launch {
        val cmd = (ProgressEvents.getPayload("NavigateTo") as? VoiceNavigation)?.command as? VoiceCommand.SetAlarm
        if (cmd != null) {
            ProgressEvents.addPayload("NavigateTo", null)   // consume — see caution below
            val index = alarms.value.indexOfFirst { !it.enabled }.let { if (it == -1) 0 else it }
            onTimeChanged(index, cmd.hour, cmd.minute)
            toggleAlarm(index, true)
        }
    }
}
```

**Caution, not yet fully resolved:** `getPayload` never auto-clears on its
own. If a ViewModel forgets the `addPayload(eventName, null)` clear step, a
user who sets something by voice and later navigates to that screen
manually (hours later) would have `init` silently re-apply the old, stale
command. Every consumer of this pattern must remember to clear it, or it's
worth a small shared `consumeNavigationPayload()` helper so "read + clear"
is one call instead of two and can't be forgotten. **Not yet built** —
listed in Open Items.

## Accepted trade-off: minor duplication between ViewModel and Action

Because `Action` must remain usable with no screen present, and the
ViewModel-side update above only affects on-screen state, **the "which
alarm slot to change" logic ends up implemented independently in two
places**: `AlarmViewModel` (for instant visual feedback, above) and
`SetAlarmAction` (for the actual watch write, run headless-safe,
independent of any ViewModel). If a user is already on the Alarms screen
when a voice command fires, the two independent slot picks could in
principle disagree.

**Explicitly accepted as a minor issue, not worth solving via singleton
promotion of `AlarmViewModel`.** Revisit only if it turns out to cause
real user-visible confusion in practice.

## The routing table (literal, not polymorphic dispatch)

Chosen over a sealed-class/polymorphic-dispatch design for readability —
one place to see every voice command and where it routes:

```kotlin
sealed class VoiceCommand {
    data class SetAlarm(val hour: Int, val minute: Int) : VoiceCommand()
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : VoiceCommand()
    data class SetSetting(val name: String, val enabled: Boolean) : VoiceCommand()
    data class SetReminder(val event: Event) : VoiceCommand()
}

data class VoiceCommandSpec(
    val route: String,
    val actionClass: Class<out ActionsViewModel.Action>,
    val applyParams: suspend (ActionsViewModel.Action, VoiceCommand) -> Unit,
)

val voiceCommandTable: Map<KClass<out VoiceCommand>, VoiceCommandSpec> = mapOf(
    VoiceCommand.SetAlarm::class to VoiceCommandSpec(
        route = Screens.Alarms.route,
        actionClass = ActionsViewModel.SetAlarmAction::class.java,
        applyParams = { action, cmd -> (action as ActionsViewModel.SetAlarmAction).let {
            it.alarmHour = (cmd as VoiceCommand.SetAlarm).hour
            it.alarmMinute = cmd.minute
        }},
    ),
    VoiceCommand.SetTimer::class to VoiceCommandSpec(
        route = Screens.Time.route,
        actionClass = ActionsViewModel.SetTimerAction::class.java,
        applyParams = { action, cmd -> (action as ActionsViewModel.SetTimerAction).let {
            val c = cmd as VoiceCommand.SetTimer
            it.timeMs = ((c.hours * 3600) + (c.minutes * 60) + c.seconds) * 1000L
        }},
    ),
    VoiceCommand.SetSetting::class to VoiceCommandSpec(
        route = Screens.Settings.route,
        actionClass = ActionsViewModel.SetSettingsAction::class.java,
        applyParams = { action, cmd ->
            // Note: unlike the others, this apply step is async — it needs a
            // fresh read of the watch's current settings before patching one
            // field, since SetSettingsAction now takes a whole Settings object.
            val c = cmd as VoiceCommand.SetSetting
            val current = api.getSettings()
            val patched = when {
                c.name.contains("auto light") -> current.copy(autoLight = c.enabled)
                c.name.contains("power saving") -> current.copy(powerSavingMode = c.enabled)
                else -> current
            }
            (action as ActionsViewModel.SetSettingsAction).settings = patched
        },
    ),
    // SetReminder follows the same shape once wired to SetEventsAction
)
```

`VoiceDispatcher` becomes a pure lookup + dispatch, with no per-command
branching logic living in the engine itself: parse text → look up spec by
command type → check `action.enabled` → apply params (suspend) → navigate
(`ProgressEvents.onNext("NavigateTo", ...)`, per above) →
`actionsViewModel.runFilteredActions(VOICE_COMMAND)`.

## Open items / follow-ups
- Build the shared "consume + clear" helper for the `ProgressEvents`
  payload pattern (see Caution above), rather than relying on every
  ViewModel to remember the `addPayload(eventName, null)` step.
- `SetAlarmAction` rewrite to include alarm-name/hourly-chime handling
  (currently only in `AlarmViewModel.sendAlarmsToWatch()`) is not yet
  written.
- `SetSettingsAction`'s voice-side `applyParams` above only patches "auto
  light"/"power saving" by string match — same limited coverage as
  today's version, just moved. Expand as more settings become
  voice-controllable.
- Decide phrasing coverage for v1 fuzziness ("wake me up in 7 hours" —
  relative/duration phrasing isn't handled by `IntentParser` yet, only
  literal clock times).
- Decide user feedback channel for unmatched/unparsed commands (visual
  only, or also spoken via TTS).
- `SetEventsAction`/Reminders path through the new table not yet detailed
  (Reminders' `EventsModel`-as-singleton means it likely doesn't need the
  `ProgressEvents` bridge at all — screen already reflects it whenever
  composed).