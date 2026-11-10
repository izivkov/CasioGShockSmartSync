# Session Conversion — Action Plan for Next Session

Branch: `session` (CasioGShockSmartSync). Goal: components talk only to `Session` / `SessionManager`; `ProgressEvents` is used only inside `SessionEventBridge` (and the library).

## Where we are

Done (delivered as complete files, uncompiled):
- `Session` / `SessionManager` fixes: new session per connection, closed sessions never reused, device passed to teardown, `sessionState` derived from `currentSession`, idempotent `close()`, stale-disconnect guard.
- Connection button moved into Session: `ConnectionButton` enum (`NORMAL`, `FROM_ACTION`, `AUTOMATIC_SYNC`, `FIND`, `ALWAYS_CONNECTED`), `EntryReason` derived from it, `ActionRunner` / `MainEventHandler` / `ContentSelector` read it from the Session. Only `Session.kt` reads the repository's `isXPressed()` flags.

Still event-driven (not yet converted): `PhoneFinder`, `NotificationMonitorService`, `AppConnectionSpinner`, `ActionContainer` reload, `WatchFeatureManager` / `EventViewModel` / `SettingsViewModel` / `DeviceManager` (`Device*` events), `MainEventHandler` navigation.

## Step 0 — Apply and verify yesterday's files (do first)

1. Copy the 10 files from `connection-button/` into `app/src/main/java/org/avmedia/gshockGoogleSync/`; delete `session/ConnectionTrigger.kt`.
2. Build; fix any compile errors (nothing was compiled yet).
3. Manual test matrix, checking logs for one Session per connection and the right `ConnectionButton`:
    - Normal connect (lower-left long-press) → Time screen
    - Action button (lower-right short-press) → RunActions screen, actions run once
    - Auto-time connect → RunActions, auto-time action runs once
    - Find phone → find screen, phone rings
    - Always-connected watch (DW-H5600 / ECB) → cover screen
    - Disconnect then reconnect → new session id, old one closed, button re-read
    - Connect failure / `Error` → `Closed(reason)` and pre-connection screen

## Tasks, in order

| # | Task | Files | Risk |
|---|------|-------|------|
| 1 | **PhoneFinder uses Session.** Change `registerCloseHook` to return an unregister handle. `PhoneFinder.ring(context, session)` registers a close hook that stops the ring; `stopRing()` unregisters it. Remove `setupDisconnectListener()` (its call-site-derived name means only the first ring ever registers, capturing a stale `Context`). `FindPhoneAction` needs `SessionManager` from `ActionContainer`. | `Session.kt`, `PhoneFinder.kt`, `ActionContainer.kt` (line ~366) | Low |
| 2 | **NotificationMonitorService.** Replace `WatchInitializationCompleted` / `Disconnect` with `sessionState.map { phase is Ready }`. | `NotificationMonitorService.kt` | Low |
| 3 | **Bridge: `ConnectionStarted` and `ApiError`.** `ConnectionStarted` creates the session in `Connecting` (check it fires more than once, at `Connection.kt:152` and `IGShockManager.kt:210`, and make sure it never closes an active Ready session). Map `ApiError` to `onDisconnected`. | `SessionEventBridge.kt`, `SessionManager.kt` | Medium |
| 4 | **AppConnectionSpinner** shows while `phase is Connecting`; drop the `isInitial` hack. Depends on 3. | `AppConnectionSpinner.kt` | Low |
| 5 | **Device events into Session.** Bridge turns `DeviceName`, `DeviceAddress`, `ConnectionSetupComplete` into `Session.updateWatch(...)` plus a "setup complete" signal. Migrate `WatchFeatureManager` (`refreshCounter`), `EventViewModel`, `SettingsViewModel`, `DeviceManager`. | bridge, those 4 files | Medium |
| 6 | **ActionContainer** reloads its data when a session becomes Ready (same pattern as `ActionRunner`). Optional. | `ActionContainer.kt` | Low |
| 7 | **Navigation (Phase 2).** Derive `AppScreenState` from `session.phase`; remove nav handlers from `MainEventHandler`; fold `RunActions` into the phase and delete `delay(3000)`; remove the stopgap `onConnected()` call in `MainEventHandler`. | `AppScreenState.kt`, `MainEventHandler.kt`, `GShockApplication.kt` | High |
| 8 | **Pending-save UI.** `EventViewModel` and `HealthViewModel` only call `clearPendingSave`; add `markPendingSave`. Decide per screen whether it keeps auto-save (Settings) or gets an explicit save with a pending marker. Screens observe `isPendingSaveFlow`. | VMs and screens | Medium (UX change) |
| 9 | **Cleanup.** Remove `subscribeToProgressEvents` wrappers and fixed subscription names, retire `ProgressEventsExt.kt`, remove the `ProgressEvents.onNext("Error")` producers in favor of Session calls, remove the unused `SessionState.errorMessage` (or populate it). | various | Low |
| 10 | **JVM tests.** Fake `GShockRepository` and a fake event source for the bridge. Cover: new session per connect, button per trigger, stale disconnect ignored, drafts and pending cleared on close, close-hook run and unregister. Needs `Dispatchers.setMain`. | `app/src/test` | Low |

## Open questions

- Keep `ALWAYS_CONNECTED` as a fifth button value? (Kept for now; the DW-H5600 / ECB paths use it.)
- Does `ConnectionStarted` fire for periodic always-connected connections with no user action? Decides how task 3 works.
- Does `GShockAPI` keep connection-scoped state that needs a `clearConnectionState()` hook on session close?
- Is a lost `Disconnect` event realistic? The button is fixed per session, so a lost one would leave a stale button on the next connection.
- Task 8: which screens should switch to explicit save?

## Definition of done

- No `ProgressEvents` references outside the bridge and the library (except app-level events like `AppNotification`).
- Navigation is a pure function of Session state.
- A disconnect, reconnect or ViewModel recreation leaves no stale state or leaked subscriber.