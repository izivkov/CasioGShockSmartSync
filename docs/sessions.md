# Session Architecture & Connection Management

This document details the **Session Architecture** in Casio G-Shock Smart Sync. The Session system provides a high-level, connection-scoped facade for active watch connections, pending save state management, and connection lifecycle handling.

---

## 1. Overview & Motivation

Previously, components and ViewModels across the application directly subscribed to raw string-based `ProgressEvents` (`"ConnectionSetupComplete"`, `"WatchInitializationCompleted"`, `"Disconnect"`, `"Error"`, etc.) and queried low-level repository button flags (`isNormalButtonPressed()`, `isActionButtonPressed()`, etc.). This approach caused several architectural issues:

- **State Fragmentation**: No single source of truth existed for whether a watch was currently connected or what state the connection was in.
- **Subscriber Leaks & Stale Callbacks**: Multiple components subscribing to raw events led to duplicate listeners, payload overwrite races, and stale callbacks from prior connections.
- **Scattered Button Logic**: UI components directly evaluated low-level hardware button flags to decide which screens or actions to run.
- **Untracked Local Edits**: Edits made on UI screens (Alarms, Settings, Events, Health Profile) were held in local ViewModel state with no central tracking of whether changes were still pending save to watch hardware.

The **Session Architecture** addresses these problems by introducing a connection-scoped `Session` object managed by a `@Singleton SessionManager`.

---

## 2. Architecture Diagram

```
+--------------------------------------------------------------------------+
|                            GShockApplication                             |
|                                                                          |
|   +-----------------------+              +---------------------------+   |
|   |   SessionEventBridge  | ------------>|      SessionManager       |   |
|   | (Process Event Bridge)|              |        (@Singleton)       |   |
|   +-----------------------+              +---------------------------+   |
|               ^                                        |                 |
|               | (ProgressEvents)                       v                 |
|       +---------------+                   +------------------------+     |
|       |   GShockAPI   |                   |     Current Session    |     |
|       +---------------+                   |   (Connection Scope)   |     |
|                                           +------------------------+     |
+--------------------------------------------------------|-----------------+
                                                         |
                   StateFlow<SessionState> & PendingSaves|
                                                         v
                       +-----------------------------------+
                       | ViewModels & Subsystems           |
                       | (Navigation, Actions, Services)   |
                       +-----------------------------------+
```

---

## 3. Core Domain Models (`org.avmedia.gshockGoogleSync.session`)

### `ComponentId`
Enum identifying application components that can hold pending unsaved modifications:
```kotlin
enum class ComponentId {
    ALARMS,
    SETTINGS,
    EVENTS,
    ACTIONS,
    HEALTH_PROFILE
}
```

### `ConnectionButton`
Enum representing the physical watch button or trigger that initiated the current connection. Known as soon as the session is `SessionPhase.Ready`:
```kotlin
enum class ConnectionButton {
    NORMAL,             // LOWER-LEFT long-press
    FROM_ACTION,        // LOWER-RIGHT short-press (action button)
    AUTOMATIC_SYNC,     // Watch connected by itself (Auto-Time), no button pressed
    FIND,               // Find Phone pressed on the watch
    ALWAYS_CONNECTED    // Always-connected watches (e.g. DW-H5600, ECB)
}
```

### `EntryReason`
Maps a physical `ConnectionButton` to the initial screen destination:
```kotlin
enum class EntryReason {
    NORMAL,
    CONTENT_SELECTOR,
    RUN_ACTIONS;

    companion object {
        fun forButton(button: ConnectionButton): EntryReason = when (button) {
            ConnectionButton.NORMAL -> NORMAL
            ConnectionButton.FROM_ACTION, ConnectionButton.AUTOMATIC_SYNC -> RUN_ACTIONS
            ConnectionButton.FIND, ConnectionButton.ALWAYS_CONNECTED -> CONTENT_SELECTOR
        }
    }
}
```

### `SessionPhase`
Sealed interface representing the lifecycle phase of a watch connection:
```kotlin
sealed interface SessionPhase {
    data object Disconnected : SessionPhase
    data object Connecting : SessionPhase
    data class Ready(val entryReason: EntryReason = EntryReason.NORMAL) : SessionPhase
    data class Closed(val reason: String = "", val isError: Boolean = false) : SessionPhase
}
```

### `SessionState`
Immutable snapshot of the current session state:
```kotlin
data class SessionState(
    val phase: SessionPhase = SessionPhase.Disconnected,
    val button: ConnectionButton? = null,
    val watch: WatchSnapshot = WatchSnapshot(),
    val isSetupComplete: Boolean = false,
    val isRunningActions: Boolean = false,
    val pendingSaves: Set<ComponentId> = emptySet()
) {
    val isNormal: Boolean get() = button == ConnectionButton.NORMAL
    val isFromAction: Boolean get() = button == ConnectionButton.FROM_ACTION
    val isAutomaticSync: Boolean get() = button == ConnectionButton.AUTOMATIC_SYNC
    val isFind: Boolean get() = button == ConnectionButton.FIND
    val isAlwaysConnected: Boolean get() = button == ConnectionButton.ALWAYS_CONNECTED

    fun isPendingSave(component: ComponentId): Boolean = component in pendingSaves
    fun hasPendingSaves(): Boolean = pendingSaves.isNotEmpty()
}
```

---

## 4. Key Classes in `org.avmedia.gshockGoogleSync.session`

### 1. `Session.kt`
A single-use, connection-scoped instance representing an active watch connection attempt/session:
- **`sessionScope`**: A `CoroutineScope` tied specifically to the connection lifecycle (`SupervisorJob() + Dispatchers.Main.immediate`).
- **Single-Use Guarantee**: Once `close()` is called, a session transitions to `SessionPhase.Closed` and becomes inactive. Any subsequent mutators are no-ops, preventing late callbacks from resurrecting closed sessions.
- **`registerCloseHook(hook)`**: Allows components to attach cleanup hooks that run deterministically when the session closes.
- **`pendingSaves`**: Tracks component-level pending saves (`ComponentId`).

### 2. `SessionManager.kt` (`@Singleton`)
The application-wide manager providing the single source of truth for watch connection sessions:
- Exposes `sessionState: StateFlow<SessionState>`, `currentSession: StateFlow<Session?>`, and state queries (`isReady`, `activeSession`).
- **`createSession(device)`**: Creates a new `Session` and cleanly closes any existing stale session.
- **`onConnecting(device)`**: Ensures an active session in `SessionPhase.Connecting`.
- **`onConnected(entryReason, device)`**: Transitions the session to `SessionPhase.Ready` and fixes its `ConnectionButton`.
- **`onSetupComplete(device)`**: Marks `isSetupComplete = true` when BLE link setup completes ("ConnectionSetupComplete").
- **`onDisconnected(device, reason, isError)`**: Closes the active session cleanly.
- **Pending Save API**: `markPendingSave`, `clearPendingSave`, `isPendingSave`, `isPendingSaveFlow`.

### 3. `SessionEventBridge.kt` (`@Singleton`)
A single, process-scoped bridge registered ONCE during `GShockApplication.onCreate()`:
- Subscribes to low-level `ProgressEvents` callbacks (`"ConnectionStarted"`, `"ConnectionSetupComplete"`, `"WatchInitializationCompleted"`, `"Disconnect"`, `"Error"`, `"ApiError"`, `"RunActions"`).
- Translates string-based library events into type-safe calls on `SessionManager`. Uses `ProgressEvents.subscribe()` for safe lifecycle management.

---

## 5. How Sessions Fit with the Rest of the App

### Navigation Subsystem (`AppNavigationHost`, `MainEventHandler`, `AppNavigationViewModel`)
- Instead of checking raw repository flags (`repository.isActionButtonPressed()`, etc.), navigation screens observe `sessionState.trigger` / `sessionState.button`.
- `ContentSelector` uses `sessionState.button` to decide whether to show `CoverScreen`, `RunActionsScreen`, `RunFindPhoneScreen`, or `BottomNavigationBarWithPermissions`.
- `MainEventHandler` checks `sessionManager.sessionState.value.isNormal` upon watch initialization to route to main navigation or content selector.

### Actions Subsystem (`ActionRunner`, `ActionContainer`)
- **`ActionRunner`**: Process-scoped component that observes `sessionManager.currentSession`. Once a session becomes `Ready`, it runs the connection actions associated with the session's `button` trigger (`runActionsWhenReady`).
- **Always-Connected Watches**: Listens to `session.state` for `isRunningActions` flag changes (`runActionsOnRequest`), executing actions when requested by the watch and clearing the flag upon completion.
- **`ActionContainer`**: Waits for session `Ready` before updating persistent action maps and running actions.

### Background Services & Integrations (`NotificationMonitorService`, `PhoneFinder`)
- **`NotificationMonitorService`**: Queries `sessionManager.isReady` directly when a notification is posted to verify an active session exists before forwarding notifications to the watch.
- **`PhoneFinder`**: Registers a session close hook (`session.registerCloseHook { stopRinging() }`) so ringing automatically stops if the watch disconnects.

### Feature ViewModels (`AlarmViewModel`, `EventViewModel`, `SettingsViewModel`, `HealthViewModel`)
- **Pending Save Tracking**: ViewModels inject `SessionManager` to call `markPendingSave(ComponentId)` when user edits occur, and `clearPendingSave(ComponentId)` when writes succeed.
- **Data Reloading**: ViewModels collect `sessionState.isSetupComplete` to automatically reload watch state when a connection completes setup.

### Feature Visibility (`WatchFeatureManager`)
- Reacts to changes in `sessionState` (`watch.name` and `isSetupComplete`) to trigger UI feature updates across the app.

