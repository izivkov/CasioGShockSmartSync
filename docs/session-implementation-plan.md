# Session Implementation Plan — CasioGShockSmartSync

Plan only; no code changed. Based on a read of both repos: Skuare's connection and sync layers, and the current `MainEventHandler`, `AppScreenState`, `DeviceManager`, `ActionRunner` and the ViewModels.

## 1. What the code does today

- **Event subscribers:** `ProgressEvents` is subscribed from at least 8 places: `MainEventHandler`, `DeviceManager`, `ActionRunner`, `PreConnectionViewModel`, the Time, Alarm, Event and Health ViewModels, `BottomNavigationBarWithPermissions`, and `AppConnectionSpinner`. Most use `subscribeToProgressEvents()`, which only exists to work around the one-subscription-per-name bug.
- **Navigation:** `MainEventHandler` maps events to `AppScreenState` calls. `WatchInitializationCompleted` goes to `showMainNavigation()` or the content selector, `Disconnect` goes to `showInitialScreen()`, and `Error` goes to the error screen followed by the pre-connection screen. `RunActions` uses a `delay(3000)` hack to leave its screen.
- **Per-connection state:** each ViewModel holds its own copy and refetches when `ConnectionSetupComplete` or `DeviceName` fires. Nothing is cleared on disconnect, and nothing is shared between screens.
- **Save semantics:** `SettingsViewModel` saves on every change (`viewModelScope.launch { updatedSetting.save() }`), so "pending" doesn't exist there yet. Skuare has it as a saved/pending pair plus a `SaveState`.
- **Payload reads:** `ProgressEvents.getPayload(name)` is a global mutable read after the event fires. `DeviceManager` already has a comment about the payload being overwritten before its coroutine runs.

## 2. Design

**`SessionManager` (`@Singleton`)** holds `StateFlow<Session?>`. A new `Session` is created per connection and the previous one is closed. This is Skuare's `SingleConnectionSlot` idea: a mutex plus compare-and-set release, so a late "disconnected" for an old session can't kill a new one. It is the only class that creates or closes a `Session`.

**`Session` (plain class, not Hilt-injected)** owns the connection scope and exposes:

- **`phase: StateFlow<Phase>`**, where `Phase` is `Connecting`, `Ready(entry)` or `Closed(reason)`. `entry` is `Normal`, `ContentSelector` or `RunActions`. This replaces the `isNormalButtonPressed()` branching and the `delay(3000)` hack.
- **`watch: StateFlow<WatchSnapshot>`** with name, address, model and feature flags. `WatchFeatureManager` could be folded in here later.
- **Typed per-section flows** (settings, alarms, timer, health, events) replacing the `AlarmsUpdated`, `SettingsUpdated` and similar subscriptions.
- **`draft(Section): Draft<T>`**, described below.
- **Commands:** `save(section)`, `disconnect()`.

**`SessionEventBridge` (process-scoped)** is the only class that touches `ProgressEvents`. It registers once at app start, before any connection, so nothing is dropped. It reads each payload synchronously inside the callback and forwards a typed value on a single-threaded dispatcher. This removes the payload-overwrite race, the unique-name workaround, and the leaked subscribers.

### The four responsibilities

1. **Navigation.** Session shouldn't drive navigation directly. The background path (`GShockCompanionDeviceService`, voice `DIRECT_INVOCATION`, always-connected) runs with no UI, so Session must work headless. Instead, Session decides and the UI renders: `AppScreenState.state` becomes a derivation of `session.phase` plus errors. Same behavior, with no `NavController` inside Session.
2. **Cache clearing.** Anything per-connection lives in Session, or in a child scope or `onClose {}` hook registered on it. Closing cancels the scope and drops the drafts. No watch-data cache was found in the app module. If `GShockAPI` keeps connection-scoped state, add one `clearConnectionState()` hook on it that Session calls on close. This needs checking.
3. **Pending-save tracking.** Session owns the values, not just dirty flags. A `Draft<T>` exposes `saved`, `pending` and `isDirty` as flows. Screens bind to `pending` and show a modified marker when `pending != saved`. Because the draft lives in Session, it survives ViewModel recreation and rotation, and it disappears on disconnect. A flags-only design would leave values in ViewModels and lose them on recreation. Skuare's `SaveState(hasUpdates, isLoading, hasErrors)` is a good shape for the aggregate.
4. **Facade.** Components import `Session` and `SessionManager` only. Outside the bridge, `ProgressEvents` becomes an implementation detail of the library.

### Relationship to Actions

Keep the `ui/actions` model unchanged. Actions answer "what to do when X happens". Session answers "what state is the connection in". `ActionRunner` would read `session.connectReason` instead of subscribing to `ButtonPressedInfoReceived`. Actions can use `session` presence in `shouldRun()`.

### Events that don't belong in Session

`AppNotification` and `LocationServicesDisabled` aren't per-connection. They go on a small app-level event flow owned by the bridge. `WaitForConnection` and `NavigateTo` (voice) also need explicit homes.

## 3. Migration phases

Each phase should be shippable on its own.

- **Phase 0: inventory.** List every event, its payload type and its producer and consumers. Write characterization tests for the connect, disconnect, error and reconnect sequences.
- **Phase 1: shadow mode.** Add `SessionManager`, `Session` and the bridge. They only observe and log, and the output is compared against `MainEventHandler`. Behavior doesn't change.
- **Phase 2: navigation.** Derive `AppScreenState` from `session.phase`. Remove the nav handlers from `MainEventHandler`. Fold `RunActions` into `Phase.Ready(RunActions)` and delete the delay.
- **Phase 3: ViewModels, one at a time.** Order: Time, Alarm, Event, Health, Settings, then PreConnection (last, since it runs before any Session exists). Each one drops its subscription code and `onCleared` cleanup. Retire `ProgressEventsExt.kt` and `DeviceManager`'s subscription once all consumers are done.
- **Phase 4: drafts.** Sections opt in individually. Decide per screen whether it keeps auto-save (Settings today) or switches to explicit save with a pending marker. That is a UX change, not just a refactor.
- **Phase 5: close semantics.** Add the cache hook, discard drafts on disconnect, and add stale-session guards.

## 4. Risks and open questions

- **Stale sessions.** In-flight coroutines from an old connection must fail fast. Run them on `session.scope` and check `isActive` before writing back.
- **Silent connect failures.** The `WaitForConnectionIO` hang means `Connecting` can last forever. Session needs its own timeout transitioning to `Closed(ConnectTimeout)`, independent of when the library fix lands.
- **Pending edits on disconnect.** Discarding matches the requirement, but on flaky links a brief disconnect silently loses edits. Have `Closed` carry `pendingLost = true` so the UI can show a snackbar.
- **Library fixes.** The `ProgressEvents` fixes in `GShockAPI` (a `Map<String, Job>` registry, with cancel-and-replace on re-subscription and a real `stop()`) aren't a blocker, since the bridge registers once. Still worth applying to remove the underlying trap.
- **Multiple watches.** The plan assumes one active connection at a time, as Skuare does.
- **Threading.** Library events arrive off-main. Use the single-threaded dispatcher pattern `DeviceManager` already uses.

## 5. Testing

Put `Session` behind a small interface, as Skuare does with `ConnectedWatch`, and have the bridge take a fake event source. Then phase transitions, drafts and stale-session behavior are plain JVM tests with no Android or BLE. Skuare's `WatchSessionHandlerTest` is a good model.

## 6. Definition of done

- No `ProgressEvents` references outside the bridge and the library.
- No hardcoded subscription names and no `ProgressEventsExt.kt`.
- Navigation is a pure function of Session state.
- A disconnect, reconnect or ViewModel recreation leaves no stale state or leaked subscriber.

Next concrete step: Phase 0, the event inventory table.
