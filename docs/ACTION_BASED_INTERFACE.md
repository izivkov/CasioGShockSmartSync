# Action-Based Interface Architecture

This document describes the architectural transition from direct `GShockAPI` calls to a centralized, action-based command system in the Casio G-Shock Smart Sync application.

## Overview

Previously, UI components (ViewModels) interacted directly with the `GShockRepository` (which delegates to `GShockAPI`) to send commands to the watch. While simple, this approach led to duplicated logic for command validation, threading, and handling different trigger sources.

The **Action-Based Interface** centralizes all watch interactions into a single coordinator (`ActionContainer`), using a command pattern to encapsulate specific watch operations.

## Key Components

### 1. The `Action` Interface
Every watch operation is encapsulated in a subclass of the `Action` abstract class defined inside `ActionContainer`.
- **`title`**: Human-readable name of the action.
- **`enabled`**: Whether the action is currently active (persisted in local storage/watch scratchpad).
- **`runMode`**: `SYNC` or `ASYNC`.
- **`run()` / `runSuspend()`**: The core execution logic for the command.
- **`shouldRun(RunEnvironment)`**: Logic that determines if the action should execute based on the trigger source.

### 2. Run Environments (`RunEnvironment`)
Actions behave differently depending on where they are triggered from:
- `NORMAL_CONNECTION`: Standard Bluetooth connection.
- `ACTION_BUTTON_PRESSED`: Triggered by the short-press of the lower-right watch button.
- `AUTO_TIME_ADJUSTMENT`: Scheduled background sync.
- `FIND_PHONE_PRESSED`: Long-press of the lower-right watch button.
- `VOICE_COMMAND`: Triggered by a natural language command.
- `DIRECT_INVOCATION`: Programmatic call from a specific UI button (e.g., "Send to Watch" in Settings).

### 3. Action Container (`ActionContainer`)
A `@Singleton` component that serves as the "source of truth" for all actions. It is responsible for:
- Initializing and maintaining the list of available actions.
- Loading/Saving action states (enabled/disabled) to the watch's scratchpad.
- Routing events from the watch (via `ActionRunner`) to the appropriate actions.

### 4. Actions ViewModel (`ActionsViewModel`)
A thin, `@HiltViewModel` wrapper around `ActionContainer`. It provides a lifecycle-aware interface for Compose screens.

---

## Usage Patterns

### A. Direct Invocation (UI Buttons)
When a user manually triggers an update (e.g., tapping "Send to Watch" in the Alarms screen), the app uses `DIRECT_INVOCATION`.

**Implementation:**
The ViewModel gets the specific action instance from the container and calls a specialized entry point.
```kotlin
// Inside AlarmViewModel.kt
fun sendAlarmsToWatch() {
    val setAlarmAction = actionContainer.getAction(ActionContainer.SetAlarmAction::class.java)
    // Direct call bypassing general filters
    setAlarmAction.runWithAlarms(appContext, alarmsToSend)
}
```

### B. Environment Triggers (Watch Buttons)
When a watch hardware button is pressed, the `ActionRunner` handles the signal and passes it to the container to run all actions compatible with that environment.

**Implementation:**
1. `ActionRunner` listens for `ButtonPressedInfoReceived`.
2. It calls `actionContainer.runActionsForActionButton(context)`.
3. The container filters all actions where `action.shouldRun(ACTION_BUTTON_PRESSED)` is true.
```kotlin
// Inside ActionContainer.kt
fun runActionsForActionButton(context: Context) {
    val actions = _actions.value.filter { it.shouldRun(RunEnvironment.ACTION_BUTTON_PRESSED) }
    runFilteredActions(context, actions)
}
```

### C. Voice Commands
Voice commands are parsed into `VoiceCommand` objects, which are then mapped to specific `Action` classes via the `VoiceCommandTable`.

**Implementation:**
```kotlin
// Inside VoiceDispatcher.kt
val action = actionContainer.getAction(spec.actionClass)
spec.applyParams(action, command, api)
actionContainer.runSingleActionSuspend(action)
```

---

## Benefits of the Action System

- **Centralized Validation**: `Action.shouldRun()` centralizes hardware capability checks.
- **Decoupling**: ViewModels provide intent; the Container handles execution and threading.
- **Consistency**: The same logic is used across UI taps, watch buttons, and voice.
- **Robustness**: Asynchronous operations are managed centrally using Coroutines.
