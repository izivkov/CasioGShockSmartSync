# ActionsStorage Migration Summary

## Overview
Migrated `ActionViewModel` to use `ActionsStorage` (backed by `ScratchpadManager`) instead of `LocalDataStorage` for persisting action enabled/disabled states.

## Key Changes

### 1. **Action.load() Method Signature**
Changed from synchronous to asynchronous and added `ActionsStorage` parameter:

**Before:**
```kotlin
open fun load(context: Context) {
    val key = this.javaClass.simpleName + ENABLED
    enabled = LocalDataStorage.get(context, key, "false").toBoolean()
}
```

**After:**
```kotlin
open suspend fun load(context: Context, actionsStorage: ActionsStorage) {
    val actionEnum = when (this) {
        is SetTimeAction -> ActionsStorage.Action.SET_TIME
        is SetEventsAction -> ActionsStorage.Action.REMINDERS
        // ... etc
        else -> null
    }
    
    actionEnum?.let {
        enabled = actionsStorage.getAction(it)
    }
}
```

### 2. **loadData() Method**
Now ensures `ActionsStorage` has loaded data from `ScratchpadManager` before reading:

```kotlin
private suspend fun loadData(context: Context): List<Action> {
    // Ensure ActionsStorage has loaded data from ScratchpadManager first
    actionsStorage.load()
    
    _actions.value.forEach {
        it.load(context, actionsStorage)
    }
    return _actions.value
}
```

### 3. **All Action Implementations Updated**
Each action class now loads from `ActionsStorage`:

- `SetTimeAction` → `ActionsStorage.Action.SET_TIME`
- `SetEventsAction` → `ActionsStorage.Action.REMINDERS`
- `FindPhoneAction` → `ActionsStorage.Action.PHONE_FINDER`
- `PhotoAction` → `ActionsStorage.Action.TAKE_PHOTO`
- `ToggleFlashlightAction` → `ActionsStorage.Action.FLASHLIGHT`
- `StartVoiceAssistAction` → `ActionsStorage.Action.VOICE_ASSIST`
- `NextTrack` → `ActionsStorage.Action.SKIP_TO_NEXT_TRACK`
- `PrayerAlarmsAction` → `ActionsStorage.Action.PRAYER_ALARMS`
- `PhoneDialAction` → `ActionsStorage.Action.PHONE_CALL`

### 4. **Event-Driven Initialization**
Data loading now happens **after** watch connection is established:

**Before:**
```kotlin
init {
    loadInitialActions()
    viewModelScope.launch {
        updateActionsAndMap(loadData(appContext))  // ❌ No watch connection yet!
    }
}
```

**After:**
```kotlin
init {
    loadInitialActions()  // Creates action list structure
    setupEventSubscription()  // Subscribes to watch events
}

private fun setupEventSubscription() {
    ProgressEvents.runEventActions(
        "ActionViewModel",
        arrayOf(
            EventAction("WatchInitializationCompleted") {
                viewModelScope.launch {
                    updateActionsAndMap(loadData(appContext))  // ✅ Watch is connected!
                }
            }
        )
    )
}
```

**Why this matters:**
- `loadData()` calls `actionsStorage.load()` which calls `ScratchpadManager.load()`
- `ScratchpadManager.load()` needs to read from the watch via `api.getScratchpadData()`
- This requires an active watch connection
- By waiting for `WatchInitializationCompleted`, we ensure the connection is ready

### 5. **Coroutine Scopes Added**
All methods calling `loadData()` now launch coroutines:

- `init` block
- `runActionsForActionButton()`
- `runActionForConnection()`
- `runActionForAlwaysConnected()`
- `runActionsForAutoTimeSetting()`

## Data Flow

### Initialization Flow
```
App starts
    ↓
ActionViewModel.init
    ↓
loadInitialActions() - Creates action list with default values
    ↓
setupEventSubscription() - Subscribes to WatchInitializationCompleted
    ↓
[Wait for watch connection...]
    ↓
WatchInitializationCompleted event fired
    ↓
loadData(context) triggered
    ↓
actionsStorage.load()
    ↓
ScratchpadManager.load()
    ↓
Watch scratchpad buffer read
    ↓
ActionsStorage buffer updated
    ↓
Action.load(context, actionsStorage)
    ↓
enabled = actionsStorage.getAction(actionEnum)
    ↓
updateActionsAndMap() - Updates UI with loaded states
```

### Save Flow
```
User toggles action
    ↓
Action.save(context, actionsStorage)
    ↓
actionsStorage.update(action, enabled)
    ↓
actionsStorage.save()
    ↓
ScratchpadManager.save()
    ↓
Watch scratchpad buffer updated
```

### Load Flow
```
App starts / Action triggered
    ↓
loadData(context)
    ↓
actionsStorage.load()
    ↓
ScratchpadManager.load()
    ↓
Watch scratchpad buffer read
    ↓
ActionsStorage buffer updated
    ↓
Action.load(context, actionsStorage)
    ↓
enabled = actionsStorage.getAction(actionEnum)
```

## Benefits

1. **Centralized Storage**: All action states stored in a single scratchpad buffer on the watch
2. **Persistent Across Restarts**: Data survives app restarts via the watch's scratchpad
3. **Type-Safe**: Uses enum-based action identifiers instead of string keys
4. **Efficient**: Bit-packed storage (9 actions in 2 bytes)
5. **Consistent**: Fixed buffer layout ensures data integrity

## Migration Notes

- **LocalDataStorage still used for**: Phone numbers (`PhoneDialAction`) and camera orientation (`PhotoAction`) - these are not boolean flags
- **Backward compatibility**: Old `LocalDataStorage` values are not migrated automatically
- **Initial state**: Actions will default to `false` until explicitly enabled by the user

## Testing Checklist

- [ ] Verify action states persist after app restart
- [ ] Verify action states persist after watch reconnection
- [ ] Test enabling/disabling each action type
- [ ] Verify scratchpad buffer is correctly written to watch
- [ ] Verify scratchpad buffer is correctly read from watch
