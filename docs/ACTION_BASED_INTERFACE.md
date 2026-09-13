# Action-Based Interface Architecture

This document describes the architectural transition from direct `GShockAPI` calls to a centralized, action-based command system in the Casio G-Shock Smart Sync application.

## Overview

Previously, UI components (ViewModels) interacted directly with the `GShockRepository` (which delegates to `GShockAPI`) to send commands to the watch. While simple, this approach led to duplicated logic for command validation, threading, and handling different trigger sources (e.g., watch button presses vs. voice commands).

The new **Action-Based Interface** centralizes all watch interactions into a single coordinator (`ActionContainer`), using a command pattern to encapsulate specific watch operations.

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
- Providing specific entry points for complex writes (e.g., `runWithAlarms`, `runWithSettings`).

### 4. Actions ViewModel (`ActionsViewModel`)
A thin, `@HiltViewModel` wrapper around `ActionContainer`. It provides a lifecycle-aware interface for Compose screens to collect action states and UI events (like snackbars) via Hilt.

## Benefits of the Action System

### Centralized Validation
Instead of checking "is this watch connected?" or "does this model support reminders?" in every ViewModel, the `Action` itself determines if it should run via the `shouldRun()` method and feature gates.

### Decoupling of Intent and Execution
ViewModels no longer need to know *how* to set a timer or *how* to handle a Bluetooth transaction. They simply provide the intent (e.g., "Run the Timer action with these seconds"), and the `ActionContainer` handles the execution and threading.

### Uniform Trigger Handling
The same `SetAlarmAction` logic is used whether the user taps a button in the app, speaks a voice command, or presses a button on the watch itself.

### Robust Threading
Asynchronous operations are managed centrally using Kotlin Coroutines, ensuring that long-running Bluetooth transfers don't block the UI thread and are resilient to process backgrounding.

## Usage in ViewModels

### Old Way (Direct API)
```kotlin
// Inside AlarmViewModel
fun sendToWatch() {
    viewModelScope.launch {
        api.setAlarms(currentAlarms)
    }
}
```

### New Way (Action Interface)
```kotlin
// Inside AlarmViewModel
fun sendToWatch() {
    viewModelScope.launch {
        val setAlarmAction = actionContainer.getAction(ActionContainer.SetAlarmAction::class.java)
        // Pass specific data to the action and run it under DIRECT_INVOCATION
        setAlarmAction.runWithAlarms(appContext, alarmsToSend)
    }
}
```
