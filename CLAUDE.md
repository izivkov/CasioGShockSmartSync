# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**CasioSmartSync** is an unofficial open-source Android app (package `org.avmedia.gshockGoogleSync`) that controls G-Shock, Edifice, and Pro Trek Bluetooth watches without requiring a Casio account. It syncs time, calendar events, and alarms, and allows the watch buttons to trigger phone actions.

The core BLE protocol is entirely encapsulated in an external library, **GShockAPI** (`com.github.izivkov:GShockAPI` via JitPack). This app never talks to BLE directly — all watch communication goes through `GShockRepository`, which delegates to `GShockAPI` via Kotlin's `by` delegation.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD env vars)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "org.avmedia.gshockGoogleSync.scratchpad.AlarmNameStorageTest"

# Lint check
./gradlew lint

# Release to GitHub (uses gh CLI)
./release.sh          # production release
./release.sh debug    # debug APK upload
```

CI (CircleCI) runs `./gradlew ktlint` then `./gradlew lint test`.

## Architecture

### Dependency Injection

Hilt is used throughout. The DI graph has two key bindings:

- `ApiModule` provides a singleton `GShockAPI(context)`
- `RepositoryModule` provides `GShockRepository` (wraps `GShockAPI` via `by api` delegation) and also binds `IGShockAPI` to the repository

ViewModels inject `GShockRepository` directly and call API methods on it.

### App State Machine

`GShockApplication` (the `Application` class) owns app-wide screen state via a `currentScreen: AppScreen` mutable state. Screens are:

```
INITIAL → PRE_CONNECTION → CONTENT_SELECTOR → MAIN_NAVIGATION
                                           → RUN_ACTIONS
```

`MainEventHandler` subscribes to `ProgressEvents` (the pub/sub bus from GShockAPI) and drives these transitions by calling `IScreenManager` methods on `GShockApplication`.

### Event System

`ProgressEvents` (from GShockAPI) is a global reactive event bus. Code subscribes using:

```kotlin
ProgressEvents.runEventActions(Utils.AppHashCode(), arrayOf(
    EventAction("EventName") { /* handler */ }
))
```

`Utils.AppHashCode()` generates a unique subscription key from the call-site method name. Multiple components subscribe independently; events fan out to all subscribers.

Key events: `WatchInitializationCompleted`, `ButtonPressedInfoReceived`, `DeviceAppeared`, `DeviceDisappeared`, `Disconnect`, `RunActions`, `AppNotification`.

### Navigation

After connection, `BottomNavigationBarWithPermissions` hosts a Compose `NavHost` with five screens: **Time**, **Alarms**, **Events**, **Actions**, **Settings**. Routes are defined in `Screens.kt` as sealed objects. The Events and Actions tabs require runtime permissions and redirect to the Time tab if denied.

A 3-minute inactivity timer (`InactivityHandler`) disconnects the watch when the user stops interacting.

### BLE Connection Flow

Two detection sources work in parallel, deduplicated by `DeviceEventGate`:

1. **CompanionDeviceManager (CDM)** — preferred. `GShockCompanionDeviceService` (a `CompanionDeviceService`) fires `DeviceAppeared`/`DeviceDisappeared` events via CDM callbacks. Handles API 31, 32, 33+, and 36+ differently due to API evolution.
2. **BLE fallback scan** (`BleScanReceiver`) — safety net for ROMs that don't fully implement CDM.

CDM events take priority: `DeviceEventGate` suppresses BLE events for 5 seconds after a CDM event for the same device.

`CompanionDevicePresenceMonitor` subscribes to `DeviceAppeared` and calls `repository.waitForConnection(address)`.

`DeviceAssociationManager` manages the pairing lifecycle: syncing CDM associations to `LocalDataStorage`, starting/stopping presence observation, and recovering from pairing crashes.

### MVVM Pattern

Each screen has a ViewModel (`@HiltViewModel`) following unidirectional data flow:
- `StateFlow` for persistent screen state
- `SharedFlow<UiEvent>` for one-time events like snackbar messages
- Sealed `Action`/`UiEvent` classes for type-safe interactions

### Scratchpad Storage (Watch Memory)

The watch exposes a small scratchpad memory used to persist app-side settings across reconnections. The system uses a fixed-offset buffer layout (documented in `SCRATCHPAD_LAYOUT.md`):

| Offset | Size | Client |
|--------|------|--------|
| 0 | 3 bytes | `AlarmNameStorage` — 6 alarm names, 3-bit packed |
| 3 | 2 bytes | `ActionsStorage` — 9 boolean flags, 1-bit packed |

`ScratchpadManager` coordinates load/save. Each `ScratchpadClient` declares a fixed `getStorageOffset()` — registration order does not matter. To add a new client, implement `ScratchpadClient`, declare the next available offset (currently 5), and register in `init`.

### Local Persistence

`LocalDataStorage` wraps **DataStore Preferences** (migrated from SharedPreferences) using a mutex-protected coroutine scope. It stores device addresses, device names, fine time adjustment, and other app settings. The DataStore file lives at:
`/data/data/org.avmedia.gshockGoogleSync/files/datastore/CASIO_GOOGLE_SYNC_STORAGE.preferences_pb`

### Services

- `GShockCompanionDeviceService` — `CompanionDeviceService` that handles CDM presence callbacks; promotes itself to foreground on `DeviceAppeared`.
- `NotificationMonitorService` — `NotificationListenerService` that forwards phone notifications to the watch (only for watches that support `AppNotifications`).
- `BootReceiver` — restarts BLE scanning after device reboot.

## Key Conventions

- **Never call BLE directly.** All watch I/O goes through `GShockRepository` (which delegates to `GShockAPI`).
- **WatchInfo for capability checks.** Use `WatchInfo.hasReminders`, `WatchInfo.findButtonUserDefined`, etc. to conditionally show UI elements based on the connected watch model.
- **Timber for logging.** `Timber.d/i/w/e(...)` everywhere; `Timber.DebugTree` is only planted in debug builds.
- **AppSnackbar for user feedback.** Call `AppSnackbar(message)` (a singleton-style global) rather than managing snackbar state in composables directly.
- **`Utils.runApi { }` for fire-and-forget API calls** from non-suspending contexts; it launches on `Dispatchers.IO` and swallows exceptions via `runCatching`.
- **Strings in `res/values/strings.xml`.** The app is localized into 10 languages; never hardcode user-visible strings.
- **`@ApplicationContext` for context injection.** Always inject application context (not activity context) into singletons and ViewModels.

## Tech Stack

- Kotlin 2.2.0, AGP 8.12.3, compileSdk 36, minSdk 26, Java 21
- Jetpack Compose (Material3) + ConstraintLayout Compose
- Hilt 2.57 for DI
- CameraX for photo capture
- DataStore Preferences for local storage
- Adhan2 for Islamic prayer time calculation
- RRule for calendar recurrence rule parsing
- Timber for logging
- GShockAPI 1.4.70 (external library, source at github.com/izivkov/GShockAPI)
