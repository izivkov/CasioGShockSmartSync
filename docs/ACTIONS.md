# Actions

## What an action represents

An action is a logical unit of work that the app can trigger from more than one
entry point. It is not necessarily one command or one call to the watch API. A
single action may coordinate app-side preparation, several calls to `GShockAPI`,
verification, and a notification to the rest of the app.

For example, setting alarms is a workflow: the app stores alarm names locally,
writes alarm times and enabled states to the watch, may update the hourly chime
through watch settings, reads the resulting state back, and publishes the
confirmed alarms so the screen can refresh. `GShockAPI` owns the BLE transport
and watch protocol; the action owns the app-level sequence that makes the
feature work as one operation.

Actions can also represent work that does not call `GShockAPI`, such as taking
a photo, toggling the phone flashlight, or controlling media playback. The
abstraction is about a named, triggerable unit of app work, not specifically
about a watch API call.

## Where Actions fit

`ActionContainer` is the process-wide owner of the action instances. It keeps
the available actions, persists their user-configurable state, and decides
whether each action should run for a particular `RunEnvironment`. It is not a
screen ViewModel because actions must remain available when no screen is
visible, including when the watch triggers an action in the background.

The main connections are:

- `ActionRunner` receives watch events and asks `ActionContainer` to run the
  actions eligible for that trigger.
- `ActionsViewModel` is a thin screen-scoped facade used by the Compose Actions
  screen to list and configure the container's actions.
- Feature ViewModels, such as `AlarmViewModel`, can invoke a specific action
  directly when a user taps a feature-specific button.
- `VoiceDispatcher` resolves voice commands to actions and supplies the
  command's parameters before invoking them.
- Watch operations flow through `GShockRepository`, the app's injectable
  wrapper around `GShockAPI`.
- After a successful operation, actions can publish a `ProgressEvents` event.
  Interested ViewModels use it to update their state without coupling the
  action to a particular screen.

An action's `shouldRun(RunEnvironment)` method is the policy gate: it can check
whether the action is enabled and whether that trigger is supported. For
example, a voice command and a watch-button press do not automatically run the
same actions. `RunMode` separately describes whether an action's execution is
synchronous or launched asynchronously.

## Alarm actions

Alarms show why an action is more than a direct API wrapper. The alarm feature
has multiple entry paths, but shares the write behavior:

### Editing alarms in the Alarms screen

1. `AlarmViewModel` holds the edited alarm list and normalizes a missing name
   to an empty string.
2. When the user chooses **Send to Watch**, the ViewModel gets the shared
   `SetAlarmAction` and calls `runWithAlarms` with the full list. This direct
   invocation writes exactly that list; it does not merge a single alarm into
   the current watch state.
3. The shared writer saves the names in `AlarmNameStorage`, calls
   `GShockRepository.setAlarms`, and, when supported and requested, updates the
   hourly chime through watch settings.
4. It reads alarms back from the watch, combines them with the locally stored
   names and supported chime state, then publishes `AlarmsUpdated` with an
   `AlarmsWritten` payload.
5. `AlarmViewModel` receives that event and updates its `StateFlow` with the
   confirmed list.

### Setting one alarm by voice

The voice path uses the same `SetAlarmAction`, but has different input
semantics. It reads the current alarms from the watch, restores their local
names, enables an existing alarm if its time matches, or chooses a disabled
slot (falling back to the first slot) for the requested time. It then calls the
same shared alarm writer, so the persistence, watch write, read-back, and
`AlarmsUpdated` notification are consistent with the screen path.

`ClearAllAlarmsAction` and `DisableAllAlarmsAction` are related examples. Both
read the current alarm list and write an updated list; clearing also resets the
times and names, while disabling preserves the times. The same write-and-refresh
path is reused.

## Why centralize this work

Putting the workflow in an action gives multiple triggers one implementation:
the Alarms screen, voice commands, and other app code can share the behavior
without reimplementing watch writes or refresh handling. The action stays
independent of the Compose screen, while events let screens observe results.
This makes Actions the app-level orchestration layer between user or watch
triggers, feature-specific work, and `GShockAPI`.

## Code landmarks

- `app/src/main/java/org/avmedia/gshockGoogleSync/ActionContainer.kt` — action
  types, run eligibility, dispatch, and alarm workflows.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionRunner.kt` —
  watch event subscriptions and dispatch.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt` —
  facade for the Actions screen.
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmViewModel.kt` —
  alarm state, direct send-to-watch invocation, and event handling.
- `app/src/main/java/org/avmedia/gshockGoogleSync/data/repository/GShockRepository.kt` —
  injectable repository wrapper delegating to `GShockAPI`.