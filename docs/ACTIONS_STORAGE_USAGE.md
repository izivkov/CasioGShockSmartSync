# ActionsStorage Usage Guide

This document demonstrates how to use `ActionsStorage` methods from `Action.save` to update and persist action states.

## Overview

The `ActionsStorage` class manages boolean action settings using a typesafe enum and persists them to the watch via `ScratchpadManager`. Each action's enabled/disabled state is stored as a single bit in a compact byte array.

## Architecture

```
Action.save() 
    ↓
ActionsStorage.saveAction()
    ↓
ActionsStorage.setAction() (updates in-memory buffer)
    ↓
ScratchpadManager.save() (persists to watch)
```

## Key Components

### 1. ActionsStorage.Action Enum

Defines all available actions:

```kotlin
enum class Action {
    SET_TIME,
    REMINDERS,
    PHONE_FINDER,
    TAKE_PHOTO,
    FLASHLIGHT,
    VOICE_ASSIST,
    SKIP_TO_NEXT_TRACK,
    PRAYER_ALARMS,
}
```

### 2. ActionsStorage Methods

#### `setAction(action: Action, enabled: Boolean)`
- Updates the action's state in the **in-memory buffer** only
- Fast, synchronous operation
- Does NOT persist to the watch

```kotlin
actionsStorage.setAction(ActionsStorage.Action.FLASHLIGHT, true)
```

#### `saveAction(action: Action, enabled: Boolean)` (suspend)
- Updates the action's state in memory AND persists to the watch
- Calls `setAction()` internally, then `manager.save()`
- This is what you typically want to use

```kotlin
suspend fun example() {
    actionsStorage.saveAction(ActionsStorage.Action.FLASHLIGHT, true)
}
```

#### `getAction(action: Action): Boolean`
- Retrieves the current state from the in-memory buffer
- Fast, synchronous operation

```kotlin
val isFlashlightEnabled = actionsStorage.getAction(ActionsStorage.Action.FLASHLIGHT)
```

#### `load()` (suspend)
- Loads data from the watch into the local buffer
- Call this when initializing or refreshing state

```kotlin
suspend fun init() {
    actionsStorage.load()
}
```

## How Action.save Uses ActionsStorage

The `Action.save()` method has been updated to use `ActionsStorage`:

```kotlin
abstract class Action(...) {
    open suspend fun save(context: Context, actionsStorage: ActionsStorage) {
        // 1. Save to LocalDataStorage (legacy persistence)
        val key = this.javaClass.simpleName + ENABLED
        val value = enabled
        LocalDataStorage.put(context, key, value.toString())

        // 2. Map the Action class to ActionsStorage.Action enum
        val actionEnum = when (this) {
            is SetTimeAction -> ActionsStorage.Action.SET_TIME
            is SetEventsAction -> ActionsStorage.Action.REMINDERS
            is FindPhoneAction -> ActionsStorage.Action.PHONE_FINDER
            is PhotoAction -> ActionsStorage.Action.TAKE_PHOTO
            is ToggleFlashlightAction -> ActionsStorage.Action.FLASHLIGHT
            is StartVoiceAssistAction -> ActionsStorage.Action.VOICE_ASSIST
            is NextTrack -> ActionsStorage.Action.SKIP_TO_NEXT_TRACK
            is PrayerAlarmsAction -> ActionsStorage.Action.PRAYER_ALARMS
            else -> null
        }

        // 3. Save to ActionsStorage (persists to watch)
        actionEnum?.let {
            actionsStorage.saveAction(it, enabled)
        }
    }
}
```

## Complete Example: Updating an Action

### Scenario: User toggles the Flashlight action

```kotlin
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val actionsStorage: ActionsStorage,
    // ... other dependencies
) : ViewModel() {

    // User toggles the flashlight switch in the UI
    fun <T : Action> updateAction(updatedAction: T) {
        val currentList = _actions.value
        val index = currentList.indexOfFirst { it::class == updatedAction::class }
        
        if (index != -1) {
            // Update the in-memory list
            currentList[index] = updatedAction
            updateActionsAndMap(currentList)
            
            // Save to both LocalDataStorage and ActionsStorage (watch)
            viewModelScope.launch {
                updatedAction.save(appContext, actionsStorage)
                // This will:
                // 1. Save to LocalDataStorage
                // 2. Map ToggleFlashlightAction -> ActionsStorage.Action.FLASHLIGHT
                // 3. Call actionsStorage.saveAction(FLASHLIGHT, enabled)
                //    which updates the buffer and persists to the watch
            }
        }
    }
}
```

### What happens step-by-step:

1. **User toggles switch** → `updateAction(ToggleFlashlightAction("...", true))` is called
2. **Update in-memory list** → The action list is updated with the new state
3. **Save to LocalDataStorage** → Legacy persistence (SharedPreferences)
4. **Map to enum** → `ToggleFlashlightAction` → `ActionsStorage.Action.FLASHLIGHT`
5. **Call saveAction()** → `actionsStorage.saveAction(FLASHLIGHT, true)`
6. **Update buffer** → Bit for FLASHLIGHT is set to 1 in the byte array
7. **Persist to watch** → `ScratchpadManager.save()` encodes and writes to the watch

## Data Flow Diagram

```
UI Toggle Switch
    ↓
updateAction(ToggleFlashlightAction(enabled=true))
    ↓
viewModelScope.launch {
    updatedAction.save(appContext, actionsStorage)
}
    ↓
LocalDataStorage.put("ToggleFlashlightAction.enabled", "true")
    ↓
Map: ToggleFlashlightAction → ActionsStorage.Action.FLASHLIGHT
    ↓
actionsStorage.saveAction(FLASHLIGHT, true)
    ↓
actionsStorage.setAction(FLASHLIGHT, true)  // Updates bit in buffer
    ↓
manager.save()  // Persists to watch via ScratchpadManager
```

## Loading Data from the Watch

To load action states from the watch on app startup:

```kotlin
class ActionsViewModel @Inject constructor(
    private val actionsStorage: ActionsStorage,
    // ...
) : ViewModel() {

    init {
        viewModelScope.launch {
            // Load from watch
            actionsStorage.load()
            
            // Now you can read the states
            val isFlashlightEnabled = actionsStorage.getAction(
                ActionsStorage.Action.FLASHLIGHT
            )
            
            // Update your UI accordingly
            // ...
        }
    }
}
```

## Custom Actions with Additional Data

Some actions store additional data (e.g., `PhoneDialAction` stores a phone number):

```kotlin
data class PhoneDialAction(
    override var title: String,
    override var enabled: Boolean,
    var phoneNumber: String
) : Action(title, enabled) {

    override suspend fun save(context: Context, actionsStorage: ActionsStorage) {
        // Save the enabled state to ActionsStorage
        super.save(context, actionsStorage)
        
        // Save the phone number to LocalDataStorage
        val key = this.javaClass.simpleName + ".phoneNumber"
        LocalDataStorage.put(context, key, phoneNumber)
    }
}
```

## Benefits of This Approach

1. **Dual Persistence**: Data is saved both locally (LocalDataStorage) and to the watch (ActionsStorage)
2. **Type Safety**: Using enums prevents typos and provides compile-time checking
3. **Compact Storage**: Each action uses only 1 bit, so 8 actions fit in 1 byte
4. **Automatic Sync**: Changes are automatically persisted to the watch
5. **Separation of Concerns**: ActionsStorage handles watch communication, Actions handle business logic

## Summary

- **To save an action state**: Call `action.save(context, actionsStorage)` (suspend function)
- **ActionsStorage handles**: Mapping to enum, updating buffer, persisting to watch
- **The flow**: Action.save() → map to enum → saveAction() → setAction() + manager.save()
- **Result**: Action state is saved locally AND synced to the watch
