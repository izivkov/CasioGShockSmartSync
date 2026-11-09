# Session Architecture & Connection Management (`docs/sessions.md`)

This document details the **Session Architecture** in Casio G-Shock Smart Sync. The Session system provides a high-level, connection-scoped facade for active watch connections, pending save state management, and connection lifecycle handling.

---

## 1. Overview & Motivation

Previously, components and ViewModels across the application directly subscribed to raw string-based `ProgressEvents` (`"ConnectionSetupComplete"`, `"WatchInitializationCompleted"`, `"Disconnect"`, `"Error"`, etc.). This approach caused several architectural issues:

- **State Fragmentation**: No single source of truth existed for whether a watch was currently connected or what state the connection was in.
- **Subscriber Leaks & Race Conditions**: Multiple components subscribing to raw events led to duplicate listeners, payload overwrite races, and stale callbacks from prior connections.
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
|   | (Single Event Listener|              |        (@Singleton)       |   |
|   +-----------------------+              +---------------------------+   |
|               ^                                        |                 |
|               | (ProgressEvents)                       v                 |
|       +---------------+                   +------------------------+     |
|       |   GShockAPI   |                   |    Active Session      |     |
|       +---------------+                   | (Connection Scope)     |     |
|                                           +------------------------+     |
+--------------------------------------------------------|-----------------+
                                                         |
                   StateFlow<SessionState> & PendingSaves|
                                                         v
                       +-----------------------------------+
                       | ViewModels & Compose UI Components|
                       | (Alarms, Settings, Events, Health)|
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

### `SessionPhase` & `EntryReason`
Sealed interface representing the lifecycle phase of a watch connection:
```kotlin
enum class EntryReason {
    NORMAL,
    CONTENT_SELECTOR,
    RUN_ACTIONS
}

sealed interface SessionPhase {
    data object Disconnected : SessionPhase
    data object Connecting : SessionPhase
    data class Ready(val entryReason: EntryReason = EntryReason.NORMAL) : SessionPhase
    data class Closed(val reason: String = "") : SessionPhase
}
```

### `SessionState`
Immutable snapshot of the current session:
```kotlin
data class SessionState(
    val phase: SessionPhase = SessionPhase.Disconnected,
    val watch: WatchSnapshot = WatchSnapshot(),
    val pendingSaves: Set<ComponentId> = emptySet(),
    val errorMessage: String? = null
) {
    fun isPendingSave(component: ComponentId): Boolean = component in pendingSaves
    fun hasPendingSaves(): Boolean = pendingSaves.isNotEmpty()
}
```

---

## 4. Key Classes

### 1. `Session.kt`
A connection-scoped instance representing a single active watch connection lifetime:
- **`sessionScope`**: A `CoroutineScope` tied specifically to the connection lifecycle (`SupervisorJob() + Dispatchers.Main.immediate`).
- **`pendingSaves`**: Tracks component-level pending saves (`ComponentId`).
- **`close(reason)`**: Cancels the `sessionScope`, triggers registered close hooks, and calls `repository.teardownConnection(device)` to safely release BLE resources.

### 2. `SessionManager.kt` (`@Singleton`)
The application-wide manager and single source of truth for watch connection sessions:
- Exposes `sessionState: StateFlow<SessionState>` and `currentSession: StateFlow<Session?>`.
- **`createSession(device)`**: Creates a new `Session` and cleanly closes any existing stale session.
- **`onConnected(entryReason)`**: Transitions current session phase to `SessionPhase.Ready`.
- **`onDisconnected(device, reason)`**: Closes the active session and resets state to `SessionPhase.Disconnected`.
- **Pending Save API**:
  - `markPendingSave(component: ComponentId)`
  - `clearPendingSave(component: ComponentId)`
  - `isPendingSave(component: ComponentId): Boolean`
  - `isPendingSaveFlow(component: ComponentId): Flow<Boolean>`

### 3. `SessionEventBridge.kt` (`@Singleton`)
A single, process-scoped bridge registered ONCE during `GShockApplication.onCreate()`:
- Subscribes to low-level `ProgressEvents` callbacks (`WatchInitializationCompleted`, `Disconnect`, `ConnectionFailed`, `Error`).
- Translates string-based library events into type-safe calls on `SessionManager`.

---

## 5. Usage in ViewModels & UI

ViewModels inject `SessionManager` to report local edits and clear pending saves when data is successfully written to the watch hardware:

### Example: `AlarmViewModel.kt`
```kotlin
// When user edits an alarm locally:
private fun updateAlarm(index: Int, transform: (Alarm) -> Alarm) {
    _alarms.update { ... }
    sessionManager.markPendingSave(ComponentId.ALARMS)
}

// When alarms are written to the watch:
fun sendAlarmsToWatch() {
    viewModelScope.launch {
        val saved = setAlarmAction.runWithAlarms(...)
        if (saved) {
            sessionManager.clearPendingSave(ComponentId.ALARMS)
        }
    }
}
```

### Example: `SettingsViewModel.kt`
```kotlin
// When user modifies a setting:
fun onSettingUpdated(setting: Setting) {
    updateSetting(setting)
    sessionManager.markPendingSave(ComponentId.SETTINGS)
}

// When settings are sent to watch:
fun sendToWatch() {
    viewModelScope.launch {
        val saved = settingsAction.runWithSettings(...)
        if (saved) {
            sessionManager.clearPendingSave(ComponentId.SETTINGS)
        }
    }
}
```

---

## 6. Benefits

1. **Clean Separation of Concerns**: Low-level `ProgressEvents` string callbacks are isolated inside `SessionEventBridge`. Higher-level ViewModels consume clean `StateFlow` streams.
2. **Leak-Free Connection Scopes**: Session closure automatically cancels all coroutines associated with that specific connection attempt.
3. **Pending Save Transparency**: ViewModels and UI screens have a unified, reactive mechanism to display "Unsaved Changes" or "Pending Save" indicators to users.
4. **Persistent Local Data Safeguard**: Watch disconnection tears down BLE in-memory caches without touching persistent local user storage (`ActionsStorage`, `AlarmNameStorage`, `EventStorage`, `TimeSettingsStorage`).
