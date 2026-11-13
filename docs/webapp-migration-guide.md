# Web App Migration Guide: Android Architecture Updates (v42.9.28 → Current)

Baseline Commit: `46d01e57f86255402119940b1a9a8b9e6905b630` (tag `v42.9.28`)

This document details the architectural, state management, session, event handling, and model changes implemented in the Android project since commit `46d01e57f86255402119940b1a9a8b9e6905b630` (`v42.9.28`), specifically tailored to guide the corresponding updates in the TypeScript web app project (`gshock-smart-sync-webapp`).

---

## 1. Executive Summary of Architectural Shifts

Since `v42.9.28`, the architecture shifted away from imperative, fragmented event handlers and direct BLE repository calls toward a **Single Source of Truth** pattern centered on:

1. **Connection Sessions (`Session` & `SessionManager`)**:
   - Connection lifetime is represented by a single-use `Session` object.
   - Replaces direct calls to `GShockRepository` button inspection flags (`isActionButtonPressed()`, `isNormalButtonPressed()`, `isAutoTimeStarted()`, `isFindPhoneButtonPressed()`, `isAlwaysConnectedConnectionPressed()`).
2. **Handle-Based Event Subscriptions (`ProgressEvents.subscribe()`)**:
   - Replaced string-named subscriber registrations with handle-based, auto-closeable subscriptions (`AutoCloseable` / `{ job.cancel() }`).
3. **Baseline Change Tracking (`ChangeTracker<T>`)**:
   - Replaced naive "isModified" boolean flags with baseline comparison. A screen or feature is marked modified only when current values differ from the baseline (the values as last read from or sent to the watch).
   - Reverting a value back to baseline automatically clears the unsaved changes flag.
4. **Immutable Settings & Actions Drafts (`SettingsViewModel` & `ActionsDraft`)**:
   - Setting objects use immutable updates (`.copy(...)`) to avoid in-place state mutation issues in reactive state stores.
   - Action configurations use an in-memory draft (`ActionsDraft`) to isolate pending edits until sent.

---

## 2. Session Architecture (`org.avmedia.gshockGoogleSync.session`)

### Domain Models & Equivalents in TypeScript

| Kotlin Model / Class | TypeScript / Web Equivalent Recommendation | Description & Usage |
| :--- | :--- | :--- |
| **`SessionManager`** (`@Singleton`) | `SessionService` / `SessionManager` (Singleton) | Central manager providing `sessionState` (Observable / Signal) and active session access. |
| **`Session`** | `Session` instance | Single-use instance representing one active connection attempt/session. Closed sessions are never reused. |
| **`SessionPhase`** | `type SessionPhase = 'Disconnected' \| 'Connecting' \| { ready: { entryReason } } \| { closed: { reason, isError } }` | Sealed state hierarchy representing connection lifecycle. |
| **`ConnectionButton`** | `enum ConnectionButton { NORMAL, FROM_ACTION, AUTOMATIC_SYNC, FIND, ALWAYS_CONNECTED }` | Encapsulates the physical watch button or trigger that started the connection. Known when session becomes `Ready`. |
| **`EntryReason`** | `enum EntryReason { NORMAL, CONTENT_SELECTOR, RUN_ACTIONS }` | Maps `ConnectionButton` to the initial UI view via `EntryReason.forButton(button)`. |
| **`SessionState`** | `interface SessionState` | Immutable snapshot containing `phase`, `button`, `watch` details, `isSetupComplete`, `isRunningActions`, and `pendingSaves`. |
| **`ComponentId`** | `enum ComponentId { ALARMS, SETTINGS, EVENTS, ACTIONS, TIMER, HEALTH_PROFILE }` | Feature flags tracking pending unsaved user edits. |

### Lifecycle Rules & Invariants
1. **Single-Use Invariant**: Every Web Bluetooth connection attempt creates a new `Session`. Once closed (`close()`), a session is permanently dead; late promises/callbacks from an old connection are ignored (`isActive == false`).
2. **Button Fixation**: When `WatchInitializationCompleted` (or setup complete) fires, `session.setReady()` evaluates the connection trigger **once** and fixes `button` for the duration of the session.
3. **Close Hooks**: `session.registerCloseHook(() => void)` allows components (e.g. phone finder or timers) to attach teardown callbacks that run deterministically when the connection ends.

---

## 3. Event Handling Modernization

### Kotlin Pattern → TypeScript Pattern

**Android Pattern:**
```kotlin
val subscription = ProgressEvents.subscribe(arrayOf(
    EventAction("ConnectionSetupComplete") { ... },
    EventAction("Disconnect") { ... }
))
// Cleanup when unmounting / destroying view:
subscription.close()
```

**TypeScript Pattern:**
```typescript
// Return a teardown / unsubscribe handle from event listeners or RxJS subscriptions
const subscription = progressEvents.subscribe([
    { label: 'ConnectionSetupComplete', action: () => handleSetupComplete() },
    { label: 'Disconnect', action: () => handleDisconnect() }
]);

// Call when component unmounts or view changes
subscription.unsubscribe(); // or subscription.close()
```

> [!IMPORTANT]
> Do not use fixed string-based subscriber maps (`runEventActions("fixedName")`) without cleanup handles. Recreatable views or components will cause duplicate subscriptions or silently ignore new listeners.

---

## 4. Smart Baseline Change Tracking (`ChangeTracker<T>`)

### How `ChangeTracker` Works

In Android, `ChangeTracker<T>` compares current UI state against a baseline:

```kotlin
class ChangeTracker<T : Any>(
    private val component: ComponentId,
    private val sessionManager: SessionManager,
) {
    private var baseline: T? = null

    fun setBaseline(newBaseline: T?, current: T) {
        baseline = newBaseline
        update(current)
    }

    fun update(current: T) {
        if (current == baseline) {
            sessionManager.clearPendingSave(component)
        } else {
            sessionManager.markPendingSave(component)
        }
    }
}
```

### Recommended TypeScript Implementation

```typescript
export class ChangeTracker<T> {
  private baseline: T | null = null;

  constructor(
    private component: ComponentId,
    private sessionManager: SessionManager,
    private equalsFn: (a: T, b: T) => boolean = (a, b) => JSON.stringify(a) === JSON.stringify(b)
  ) {}

  public setBaseline(newBaseline: T, current: T): void {
    this.baseline = structuredClone(newBaseline);
    this.update(current);
  }

  public update(current: T): void {
    if (this.baseline !== null && this.equalsFn(current, this.baseline)) {
      this.sessionManager.clearPendingSave(this.component);
    } else {
      this.sessionManager.markPendingSave(this.component);
    }
  }
}
```

### Screen Integration Matrix

| Screen / Feature | Tracked Data Type (`T`) | Behavior |
| :--- | :--- | :--- |
| **Alarms** | `Alarm[]` | Toggling or changing an alarm time marks `ALARMS` pending. Reverting the change back to baseline clears `ALARMS` pending and disables the Send button. |
| **Settings** | `SettingMap` | Changing Auto-Light, Operation Tone, or Locale marks `SETTINGS` pending. Reverting to baseline disables Send button. |
| **Events / Reminders** | `Event[]` | Changing reminder enablement or content updates `EVENTS` baseline. `keepEnabledFlags` preserves user toggle choices when calendar re-reads occur. |
| **Timer** | `number` (seconds) | Changing timer hours/minutes updates `TIMER` baseline. |
| **Actions** | `ActionsDraft` | Draft changes to photo, phone, or flashlight settings update `ACTIONS` baseline. |

---

## 5. Settings Immutability & Action Engine

### Immutable Settings State
In the web app, settings models (`Light`, `OperationSound`, `Locale`, `TimeAdjustment`, `Font`, `PowerSavingMode`) must be treated as **immutable objects**:

```typescript
// DO NOT mutate properties in place:
// setting.autoLight = true; <--- CAUSES REACTIVITY/STATE SUPPRESSION BUGS

// DO create new copies when updating state:
const updatedLight = { ...currentLight, autoLight: newValue };
```

### Action Configuration Drafts (`ActionsDraft`)
- Edit forms (Photo View, Phone View, etc.) modify a local draft state (`ActionsDraft`).
- The "Send to Watch" button is enabled only when `draft !== baseline`.
- When the write succeeds, `setBaseline(sentDraft, sentDraft)` is called. If the write fails, the previous baseline is restored so the user can retry.

---

## 6. Migration Checklist for `gshock-smart-sync-webapp`

- [ ] **Implement `SessionManager` and `Session` classes** in the web app BLE core.
- [ ] **Replace raw button checks** (`isActionButtonPressed()`, etc.) with `ConnectionButton` / `ConnectionTrigger` evaluated once on setup complete.
- [ ] **Adopt `ChangeTracker`** in Alarms, Settings, Reminders, Timer, and Actions web views to control "Send to Watch" button enabled state.
- [ ] **Enforce Immutability** across all setting data structures.
- [ ] **Ensure handle-based event cleanup** on Web Bluetooth event listeners and UI component unmounts.
