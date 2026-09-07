# Voice Command Layer — Implementation Spec

**Audience:** a developer or an AI coding agent implementing this directly
on the `voice` branch of `github.com/izivkov/CasioGShockSmartSync`. Code
snippets below are grounded in the actual current source (verified by
reading each file during this design session, not assumed) — file paths,
class names, and method signatures are exact as of this writing. Where
something still needs a verification pass before implementing, that's
called out explicitly rather than glossed over.

## Goal

An independent voice-command engine, orthogonal to existing screens and
ViewModels, with minimal modification to any of them. Every recognized
voice command must, in order:
1. **Navigate** to the screen that corresponds to the command.
2. **Update the UI** (that screen's ViewModel) so the user sees the change.
3. **Run the command on the watch** (the actual BLE write).

The mic trigger ("Tell me what to do") lives only on the Time screen
(`TimeViewModel` already injects `VoiceCommandManager`/`VoiceDispatcher`
and calls `voiceDispatcher.dispatch(text)` from
`TimeAction.StartVoiceCommand`'s `onResult` callback) — so a live voice
command and a live manual edit on another screen can never collide.

## Constraints (unchanged)
- No proprietary/heavy AI libraries — native `SpeechRecognizer` +
  regex/keyword parsing (`IntentParser`), no cloud NLU.
- F-Droid compatible: `SpeechRecognizer.isRecognitionAvailable()` gate
  already implemented in `VoiceCommandManager.kt`.

## Architecture (end-to-end flow)

```
Time screen mic button
  → TimeViewModel.onAction(StartVoiceCommand)
  → VoiceCommandManager.startListening(...)
  → onResult: voiceDispatcher.dispatch(text)
      → IntentParser.parse(text) -> VoiceCommand?
      → voiceCommandTable[command::class] -> VoiceCommandSpec
      → actionsViewModel.getAction(spec.actionClass)
      → if action.enabled:
          → spec.applyParams(action, command)     // sets the Action's own params
          → ProgressEvents.onNext("NavigateTo", VoiceNavigation(spec.route, command))
              -> writes to ProgressEvents' internal payloadMap regardless of subscribers
              -> BottomNavigationBarWithPermissions is already subscribed (composed for
                 the whole app session) -> navController.navigate(spec.route)
          → actionsViewModel.runFilteredActions(RunEnvironment.VOICE_COMMAND)
              -> Action.run() performs the actual watch write, independently
                 of whether any screen/ViewModel is currently composed

Meanwhet, the destination screen's ViewModel (if newly created by the
navigation above) pulls the same payload for on-screen feedback:
  ProgressEvents.getPayload("NavigateTo") as? VoiceNavigation
  -> if command matches this screen's type: apply optimistic local UI
     update, then ProgressEvents.addPayload("NavigateTo", null) to consume
     (must clear, or a later manual navigation replays a stale command)
```

Two consumers of the same payload, at different times because their
ViewModels have different lifecycles:
- **Alarms / Settings** (not the active screen at dispatch time): the
  target ViewModel doesn't exist yet — it reads the payload in its own
  `init`, once created by navigation.
- **Timer** (lives inside `TimeViewModel`, which is *already alive* — it's
  literally the screen the mic button is on, so navigation may not even
  need to fire): the payload must be checked **immediately after
  `voiceDispatcher.dispatch(text)` returns**, in the same `onResult`
  callback — not in `init`, since `init` already ran long before this
  voice command existed. This is the one place the generic pattern needs
  a different call site, not a different mechanism.

## Step 1 — `RunEnvironment.DIRECT_INVOCATION` (`ActionViewModel.kt`)

`RunEnvironment` is a plain enum; `Action.shouldRun()`'s `when` has no
`else`, so adding a case requires a branch there too (compiler-enforced).

Current (lines 218–244):
```kotlin
enum class RunEnvironment {
    NORMAL_CONNECTION,
    ACTION_BUTTON_PRESSED,
    AUTO_TIME_ADJUSTMENT,
    FIND_PHONE_PRESSED,
    VOICE_COMMAND,
    ALWAYS_CONNECTED,
}

abstract inner class Action(...) {
    open fun shouldRun(runEnvironment: RunEnvironment): Boolean {
        return when (runEnvironment) {
            RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
            RunEnvironment.VOICE_COMMAND -> enabled
            RunEnvironment.NORMAL_CONNECTION -> false
            RunEnvironment.AUTO_TIME_ADJUSTMENT -> false
            RunEnvironment.FIND_PHONE_PRESSED -> false
            RunEnvironment.ALWAYS_CONNECTED -> false
        }
    }
    ...
}
```

Change to:
```kotlin
enum class RunEnvironment {
    NORMAL_CONNECTION,
    ACTION_BUTTON_PRESSED,
    AUTO_TIME_ADJUSTMENT,
    FIND_PHONE_PRESSED,
    VOICE_COMMAND,
    ALWAYS_CONNECTED,
    DIRECT_INVOCATION,  // Called directly from app code (a screen's "Send
                        // to Watch" button, or the voice engine) — not a
                        // watch button press or background trigger.
}

abstract inner class Action(...) {
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
    ...
}
```

## Step 2 — `SetAlarmAction` (`ActionViewModel.kt`, currently lines 705–751)

Only add a `shouldRun()` override — everything else (the slot-picking
`run()` logic) is correct and unchanged. This also fixes a real
pre-existing bug: with no override, the base default returns `enabled`
for `ACTION_BUTTON_PRESSED` too, so a watch button press could already
trigger this action.

Add inside the class body:
```kotlin
override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
    RunEnvironment.DIRECT_INVOCATION -> enabled
    RunEnvironment.VOICE_COMMAND -> enabled
    else -> false
}
```

## Step 3 — `SetSettingsAction` (`ActionViewModel.kt`, currently lines 753–781)

Current version only supports the voice single-field-patch shape
(`settingName`/`settingValue`, patched onto a fresh `api.getSettings()`
read inside `run()`). The screen's own "Send to Watch" button needs to
send a *complete*, already-locally-edited `Settings` object instead — a
different shape, not a patch. Keep both, and let `run()` prefer the full
object when one is supplied:

```kotlin
inner class SetSettingsAction(
        override var title: String,
        override var enabled: Boolean,
        var settingName: String = "",
        var settingValue: Boolean = false,
        var fullSettings: org.avmedia.gshockapi.model.Settings? = null,
) : Action(title, enabled, RunMode.ASYNC) {
    override fun run(context: Context) {
        viewModelScope.launch {
            runCatching {
                val toSend = fullSettings ?: run {
                    val current = api.getSettings()
                    when {
                        settingName.contains("auto light") -> current.copy(autoLight = settingValue)
                        settingName.contains("power saving") -> current.copy(powerSavingMode = settingValue)
                        else -> current
                    }
                }
                api.setSettings(toSend)
                AppSnackbar(context.getString(R.string.settings_sent_to_watch))
            }.onFailure {
                Timber.e(it, "Failed to send settings to watch")
            }
            fullSettings = null   // reset so a later voice-only invocation doesn't reuse a stale full object
        }
    }

    override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
        RunEnvironment.DIRECT_INVOCATION -> enabled
        RunEnvironment.VOICE_COMMAND -> enabled
        else -> false
    }

    override suspend fun save(context: Context, actionsStorage: ActionsStorage) {}
    override suspend fun load(context: Context, actionsStorage: ActionsStorage) {}
}
```

Confirmed separately in this session: `GShockAPIImpl.setSettings()`
already internally calls both `SettingsIO.set(settings)` and
`TimeAdjustmentIO.set(settings)` — one `api.setSettings()` call is
sufficient; no separate time-adjustment call is needed here.

## Step 4 — new `SetTimerAction` (`ActionViewModel.kt`)

Add alongside `SetAlarmAction`/`SetSettingsAction`:
```kotlin
inner class SetTimerAction(
        override var title: String,
        override var enabled: Boolean,
        var timeMs: Int = 0,
) : Action(title, enabled, RunMode.ASYNC) {
    override fun run(context: Context) {
        viewModelScope.launch {
            runCatching {
                api.setTimer(timeMs)
                AppSnackbar(context.getString(R.string.timer_set))
            }.onFailure {
                Timber.e(it, "Failed to send timer to watch")
            }
        }
    }

    override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
        RunEnvironment.DIRECT_INVOCATION -> enabled
        RunEnvironment.VOICE_COMMAND -> enabled
        else -> false
    }

    override suspend fun save(context: Context, actionsStorage: ActionsStorage) {}
    override suspend fun load(context: Context, actionsStorage: ActionsStorage) {}
}
```
`api.setTimer()` takes `Int` milliseconds (matches
`TimeAction.UpdateTimer(val timeMs: Int)`'s existing type in
`TimeViewModel.kt` — kept consistent, not `Long`).

## Step 5 — register `SetTimerAction`, keep both hidden (`ActionViewModel.kt`, `loadInitialActions()`)

Current (line 200–201):
```kotlin
add(SetAlarmAction(appContext.getString(R.string.set_alarm), true))
add(SetSettingsAction("Set Settings", true)) // Hidden from UI, only for voice
```

Add:
```kotlin
add(SetTimerAction("Set Timer", true)) // Hidden from UI, only for voice/Send-to-Watch
```

**No change needed to `ActionsScreen.kt`** — hidden means simply not
adding a `*View(...)` row there; `SetAlarmAction`/`SetSettingsAction`
already rely on this same omission, confirmed by reading
`ActionsScreen.kt`'s `createActionItems()`, which is a hardcoded list of
specific rows, not a generic iteration over all registered actions.
`enabled` stays hardcoded `true` — there is no UI anywhere that can flip
it, so sharing this flag between the manual button and voice via
`DIRECT_INVOCATION` carries no risk of one silently disabling the other.

## Step 6 — `VoiceCommand.kt` (new file, `org.avmedia.gshockGoogleSync.voice`)

```kotlin
package org.avmedia.gshockGoogleSync.voice

sealed class VoiceCommand {
    data class SetAlarm(val hour: Int, val minute: Int) : VoiceCommand()
    data class SetTimer(val hours: Int, val minutes: Int, val seconds: Int) : VoiceCommand()
    data class SetSetting(val name: String, val enabled: Boolean) : VoiceCommand()
    // data class SetReminder(...) — TODO, see Open Items: needs an Event
    // construction decision before adding here.
}

data class VoiceNavigation(val route: String, val command: VoiceCommand)
```

## Step 7 — `IntentParser.kt` (rewrite `ResolvedIntent`/`parse()`)

Current `parse()` returns `ResolvedIntent(actionClass, parameters: Map<String, Any>)`
— an untyped map keyed by string, matched by `Class<out Action>`. Replace
with the typed `VoiceCommand` directly; this removes
`VoiceDispatcher.updateActionParameters()`'s `when(action)` block entirely
(that logic moves into the table's `applyParams`, Step 9).

```kotlin
package org.avmedia.gshockGoogleSync.voice

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber
import javax.inject.Inject

class IntentParser @Inject constructor() {

    private val alarmPatterns = listOf(
        Regex("wake me up at (.*)", RegexOption.IGNORE_CASE),
        Regex("set alarm for (.*)", RegexOption.IGNORE_CASE),
        Regex("set an alarm for (.*)", RegexOption.IGNORE_CASE),
        Regex("alarm at (.*)", RegexOption.IGNORE_CASE)
    )

    private val timerPatterns = listOf(
        Regex("set a timer for (\\d+)\\s*(hours?|minutes?|seconds?)", RegexOption.IGNORE_CASE),
        Regex("timer for (\\d+)\\s*(hours?|minutes?|seconds?)", RegexOption.IGNORE_CASE),
    )

    private val settingsPatterns = listOf(
        Regex("enable (.*)", RegexOption.IGNORE_CASE),
        Regex("disable (.*)", RegexOption.IGNORE_CASE),
        Regex("turn (on|off) (.*)", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String): VoiceCommand? {
        Timber.d("Parsing text: '$text'")
        val cleanedText = text.trim().removeSuffix(".")

        alarmPatterns.firstNotNullOfOrNull { it.find(cleanedText) }?.let { match ->
            val timeString = match.groupValues[1]
            parseTime(timeString)?.let { time ->
                return VoiceCommand.SetAlarm(time.hour, time.minute)
            }
            Timber.w("Failed to parse time string: '$timeString'")
        }

        timerPatterns.firstNotNullOfOrNull { it.find(cleanedText) }?.let { match ->
            val amount = match.groupValues[1].toIntOrNull()
            val unit = match.groupValues[2].lowercase()
            if (amount != null) {
                return when {
                    unit.startsWith("hour") -> VoiceCommand.SetTimer(amount, 0, 0)
                    unit.startsWith("minute") -> VoiceCommand.SetTimer(0, amount, 0)
                    else -> VoiceCommand.SetTimer(0, 0, amount)
                }
            }
        }

        settingsPatterns.firstNotNullOfOrNull { it.find(cleanedText) }?.let { match ->
            val target = match.groupValues.last().lowercase()
            if (target.contains("auto light") || target.contains("power saving")) {
                val actionWord = match.groupValues[1].lowercase()
                val enabled = actionWord == "enable" || actionWord == "on"
                return VoiceCommand.SetSetting(target, enabled)
            }
        }

        Timber.w("No matching pattern found for: '$cleanedText'")
        return null
    }

    private fun parseTime(timeStr: String): LocalTime? {
        // unchanged from current implementation
        ...
    }
}
```
`reminderPatterns`/reminder handling removed here pending the Event-model
decision — see Open Items. Fuzzy/relative alarm phrasing ("wake me up in
7 hours") is still not handled — also unchanged from before this session,
tracked in Open Items.

## Step 8 — the routing table (new file, `VoiceCommandTable.kt`)

```kotlin
package org.avmedia.gshockGoogleSync.voice

import org.avmedia.gshockGoogleSync.Screens
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import kotlin.reflect.KClass

data class VoiceCommandSpec(
    val route: String,
    val actionClass: Class<out ActionsViewModel.Action>,
    val applyParams: suspend (ActionsViewModel.Action, VoiceCommand, GShockRepository) -> Unit,
)

val voiceCommandTable: Map<KClass<out VoiceCommand>, VoiceCommandSpec> = mapOf(
    VoiceCommand.SetAlarm::class to VoiceCommandSpec(
        route = Screens.Alarms.route,
        actionClass = ActionsViewModel.SetAlarmAction::class.java,
        applyParams = { action, cmd, _ -> (action as ActionsViewModel.SetAlarmAction).let {
            val c = cmd as VoiceCommand.SetAlarm
            it.alarmHour = c.hour
            it.alarmMinute = c.minute
        }},
    ),
    VoiceCommand.SetTimer::class to VoiceCommandSpec(
        route = Screens.Time.route,
        actionClass = ActionsViewModel.SetTimerAction::class.java,
        applyParams = { action, cmd, _ -> (action as ActionsViewModel.SetTimerAction).let {
            val c = cmd as VoiceCommand.SetTimer
            it.timeMs = ((c.hours * 3600) + (c.minutes * 60) + c.seconds) * 1000
        }},
    ),
    VoiceCommand.SetSetting::class to VoiceCommandSpec(
        route = Screens.Settings.route,
        actionClass = ActionsViewModel.SetSettingsAction::class.java,
        applyParams = { action, cmd, api -> (action as ActionsViewModel.SetSettingsAction).let {
            // Voice path only: sets the single-field patch inputs. run()'s own
            // existing logic (Step 3) does the api.getSettings()-then-patch —
            // no need to read settings here too. fullSettings stays null.
            val c = cmd as VoiceCommand.SetSetting
            it.settingName = c.name
            it.settingValue = c.enabled
        }},
    ),
)
```
Note `applyParams` takes `GShockRepository` as a parameter for
consistency/future use (e.g. a future command needing a live read before
applying), even though only unused (`_`) in two of the three entries
today.

## Step 9 — rewrite `VoiceDispatcher.kt`

```kotlin
package org.avmedia.gshockGoogleSync.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel.RunEnvironment.VOICE_COMMAND
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import javax.inject.Inject

class VoiceDispatcher @Inject constructor(
    private val actionsViewModel: ActionsViewModel,
    private val api: GShockRepository,
    @ApplicationContext private val context: Context,
    private val intentParser: IntentParser
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun dispatch(text: String) {
        Timber.d("Voice command received: '$text'")
        val command = intentParser.parse(text)
        if (command == null) {
            Timber.w("Could not resolve intent for text: '$text'")
            emitSnackbar("Command not understood")
            return
        }

        val spec = voiceCommandTable[command::class]
        if (spec == null) {
            Timber.w("No routing spec for command: $command")
            emitSnackbar("Command not understood")
            return
        }

        val action = actionsViewModel.getAction(spec.actionClass)
        if (!action.enabled) {
            Timber.w("Action ${action.javaClass.simpleName} is disabled")
            emitSnackbar("Action disabled")
            return
        }

        scope.launch {
            try {
                spec.applyParams(action, command, api)
                ProgressEvents.onNext("NavigateTo", VoiceNavigation(spec.route, command))
                actionsViewModel.runFilteredActions(VOICE_COMMAND)
            } catch (e: Exception) {
                Timber.e(e, "Error executing voice action")
                emitSnackbar("Command not understood")
            }
        }
    }

    private fun emitSnackbar(message: String) {
        scope.launch {
            AppSnackbar(message)
        }
    }
}
```

## Step 10 — `BottomNavigationBarWithPermissions.kt`: subscribe and navigate

Add near the existing `DisposableEffect(Unit)` block (around line 71):
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
Needs two new imports: `org.avmedia.gshockapi.ProgressEvents`,
`org.avmedia.gshockapi.EventAction`, and
`org.avmedia.gshockGoogleSync.voice.VoiceNavigation`. `replay = 10` on
`ProgressEvents`' internal `MutableSharedFlow` means this subscription
being registered slightly "late" relative to any single emission is not a
concern — confirmed by reading `ProgressEvent.kt` in the `GShockAPI`
library directly, not assumed.

## Step 11 — `AlarmViewModel.kt`: apply a pending `SetAlarm` command

Modify `loadAlarms()` (currently lines 69–95) — add the check right after
`_alarms.value = newAlarms`, using the freshly-computed `newAlarms` (not
the StateFlow's current value, to avoid any visibility race):

```kotlin
private fun loadAlarms() = viewModelScope.launch {
    runCatching {
        alarmNameStorage.load()

        val alarmsFromWatch = api.getAlarms()
            .take(watchFeatureManager.getAlarmCount())
            .mapIndexed { index, alarm ->
                val name = alarmNameStorage.get(index)
                alarm.copy(name = name)
            }

        val newAlarms = if (watchFeatureManager.isFeatureSupported("alarms.chime")) {
            val settings = api.getSettings()
            alarmsFromWatch.mapIndexed { index, alarm ->
                if (index == 0) alarm.copy(hasHourlyChime = settings.hourlyChime)
                else alarm
            }
        } else {
            alarmsFromWatch
        }
        _alarms.value = newAlarms
        ProgressEvents.onNext("Alarms Loaded")

        // Apply a pending voice-set alarm, if one is waiting for this screen.
        (ProgressEvents.getPayload("NavigateTo") as? VoiceNavigation)?.command
            ?.let { it as? VoiceCommand.SetAlarm }
            ?.let { cmd ->
                ProgressEvents.addPayload("NavigateTo", null) // consume — see Caution below
                val index = newAlarms.indexOfFirst { !it.enabled }.let { if (it == -1) 0 else it }
                onTimeChanged(index, cmd.hour, cmd.minute)
                toggleAlarm(index, true)
            }
    }.onFailure {
        ProgressEvents.onNext("Error")
    }
}
```
Needs new imports: `org.avmedia.gshockGoogleSync.voice.VoiceNavigation`,
`org.avmedia.gshockGoogleSync.voice.VoiceCommand`.

Note `loadAlarms()` is also called again from `sendAlarmsToWatch()` as its
own "reload after save" step — harmless here, since the payload was
already cleared by the first pass.

**Accepted trade-off, not a bug:** this only updates on-screen state. The
actual watch write happens independently via `SetAlarmAction.run()`
(fired by `runFilteredActions(VOICE_COMMAND)` in `VoiceDispatcher`), which
picks its own free slot from a fresh `api.getAlarms()` read, without
knowing about this ViewModel's pick. If the user is already on the Alarms
screen when the voice command fires, the two picks could in principle
disagree. Explicitly accepted in this design; revisit only if it causes
real user-visible confusion.

## Step 12 — `SettingsViewModel.kt`: route the button through `SetSettingsAction`

**Needs one more verification pass before implementing** — this session
read `sendToWatch()` (lines 460–502) but not the screen's initial-load
path (how `state.settingsMap` gets populated, presumably in `init` or an
`onSettingUpdated`/`SetSmartDefaults`-adjacent function not yet
inspected). Two changes needed here, one straightforward and one that
needs that missing context:

**(a) Straightforward — inject `ActionsViewModel`, route the button
through the Action:**
```kotlin
class SettingsViewModel @Inject constructor(
    private val api: GShockRepository,
    private val watchFeatureManager: IWatchFeatureManager,
    private val actionsViewModel: ActionsViewModel,   // new
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {
    ...
    fun sendToWatch() {
        val settings = Settings()
        // ... unchanged field-building logic from the current sendToWatch() ...

        val action = actionsViewModel.getAction(ActionsViewModel.SetSettingsAction::class.java)
        action.fullSettings = settings
        actionsViewModel.runFilteredActions(ActionsViewModel.RunEnvironment.DIRECT_INVOCATION)
    }
}
```

**(b) Needs follow-up — on-screen feedback (requirement #2) for a voice
`SetSetting` command.** Unlike Alarms, there's no existing
"patch one field locally" method on `SettingsViewModel` to reuse (`Setting`
subtypes like `Light`/`PowerSavingMode` are stored in
`state.settingsMap`, keyed by class). The likely correct fix, mirroring
`AlarmViewModel`'s pattern: in `SettingsViewModel`'s own load/init path,
pull the pending `VoiceCommand.SetSetting` payload the same way, and apply
an optimistic patch to the relevant `Setting` object in `settingsMap`
before/after the normal watch-read populates it — but this requires
reading that load path first to get the mutation call right, which wasn't
done this session. **Do that read before writing this part.**

## Step 13 — `TimeViewModel.kt`: apply a pending `SetTimer` command

**This is the one place the generic pattern doesn't apply as-is.**
`TimeViewModel` is already alive when the voice command is dispatched (the
mic button lives on its own screen) — its `init` ran long before this
command existed, so checking there would never see it. Check immediately
after `voiceDispatcher.dispatch(text)` returns instead, inside the
existing `onResult` callback (currently lines 145–149):

```kotlin
TimeAction.StartVoiceCommand -> {
    if (voiceCommandManager.isRecognitionAvailable()) {
        _state.value = _state.value.copy(isListening = true)
        voiceCommandManager.startListening(
            onResult = { text ->
                _state.value = _state.value.copy(isListening = false)
                voiceDispatcher.dispatch(text)
                (ProgressEvents.getPayload("NavigateTo") as? VoiceNavigation)?.command
                    ?.let { it as? VoiceCommand.SetTimer }
                    ?.let { cmd ->
                        ProgressEvents.addPayload("NavigateTo", null)
                        onAction(TimeAction.SetTimer(cmd.hours, cmd.minutes, cmd.seconds))
                    }
            },
            onError = { error ->
                _state.value = _state.value.copy(isListening = false)
                AppSnackbar(error)
            }
        )
    } else {
        AppSnackbar(appContext.getString(R.string.voice_recognition_unavailable))
    }
}
```
Needs new imports: `org.avmedia.gshockapi.ProgressEvents`,
`org.avmedia.gshockGoogleSync.voice.VoiceNavigation`,
`org.avmedia.gshockGoogleSync.voice.VoiceCommand`. This only sets the
*displayed* timer value (`TimeAction.SetTimer` stages local state) — the
actual watch write is `SetTimerAction.run()`, fired by
`runFilteredActions(VOICE_COMMAND)` inside `VoiceDispatcher`, same as
Alarms.

## Implementation order

1. `RunEnvironment.DIRECT_INVOCATION` + base `shouldRun()` (Step 1) —
   compiles standalone.
2. `SetAlarmAction`/`SetSettingsAction`/`SetTimerAction` shouldRun
   overrides + `SetSettingsAction`/`SetTimerAction` bodies (Steps 2–5).
3. `VoiceCommand.kt` (Step 6).
4. `IntentParser.kt` rewrite (Step 7) — will not compile until Step 3 and
   6 are done (return type change).
5. `VoiceCommandTable.kt` (Step 8) — needs Steps 3–5 done (references the
   new/changed Action classes).
6. `VoiceDispatcher.kt` rewrite (Step 9) — needs Step 8.
7. `BottomNavigationBarWithPermissions.kt` (Step 10) — independent, can be
   done any time after Step 6.
8. `AlarmViewModel.kt` (Step 11), `TimeViewModel.kt` (Step 13) — independent
   of each other, needs Step 6 done.
9. `SettingsViewModel.kt` (Step 12) — do the missing verification pass
   first, then implement both (a) and (b).

## Acceptance criteria (manual test plan)

- **Alarm, screen not open:** from Time screen, say "set alarm for 6:30
  am." App navigates to Alarms screen; a previously-disabled alarm slot
  shows 6:30 AM and enabled; watch confirms via snackbar; alarm is
  actually set on the physical watch.
- **Alarm, screen already open:** navigate to Alarms manually first, then
  (there's no mic there, so this specifically tests the "manual nav still
  works normally" case) confirm nothing regresses for ordinary manual
  alarm editing/sending.
- **Timer:** from Time screen, say "set a timer for 7 minutes." Displayed
  timer updates to 7:00 immediately (no navigation needed, already on
  Time); watch confirms via snackbar; timer actually runs on the watch.
- **Settings:** from Time screen, say "enable auto light." App navigates
  to Settings screen; watch confirms via snackbar; auto light is actually
  enabled on the watch. (On-screen toggle state depends on Step 12(b)
  being completed.)
- **Disabled action:** manually disable... n/a for these three (no UI
  toggle exists per design) — instead confirm a *button-press* environment
  no longer triggers `SetAlarmAction`/`SetSettingsAction`/`SetTimerAction`
  (regression test for the bug fixed in Step 2/3/4).
- **Stale-payload check:** issue a voice alarm command, wait, then
  manually navigate to Alarms via the bottom nav (not voice). Confirm the
  old voice command is *not* silently re-applied (tests the
  `addPayload(eventName, null)` consume step actually works).
- **Manual "Send to Watch" still works:** on Settings screen, manually
  change a toggle and press "Send to Watch" with no voice involved at
  all — confirm it still reaches the watch correctly via the new
  `SetSettingsAction`/`DIRECT_INVOCATION` path.

## Open items / not yet resolved
- Reminders/`SetEventsAction` path: needs a decision on how a parsed voice
  phrase becomes an `Event` object before it can be added to
  `voiceCommandTable`/`VoiceCommand`. Not designed this session.
- `SettingsViewModel`'s optimistic on-screen patch for a voice
  `SetSetting` command (Step 12(b)) — needs the missing read of its
  load/init path before it can be written correctly.
- Fuzzy/relative alarm phrasing ("wake me up in 7 hours") — `IntentParser`
  only handles literal clock times today; unchanged by this session.
- `SetAlarmAction.run()` doesn't yet handle alarm names or the hourly-chime
  sync that `AlarmViewModel.sendAlarmsToWatch()` already does — still a
  gap, not addressed in this pass.
- User feedback channel for unmatched/unparsed commands — currently just
  `AppSnackbar`; decide if TTS feedback is wanted for v1.
- A shared "consume + clear" helper for the `ProgressEvents` payload
  pattern (`getPayload` + `addPayload(name, null)` as one call) would
  reduce the risk of a future consumer forgetting to clear it — not built,
  each of Steps 11/12/13 repeats the pattern inline for now.