# UI Screen Management Guide

This document explains the centralized system for managing UI element visibility based on the hardware capabilities of the connected Casio G-Shock watch.

## Overview

The application uses a centralized `WatchFeatureManager` to decouple the UI from the low-level `WatchInfo` API. This allows for a more declarative and maintainable way to handle feature-specific UI components.

## Key Components

### 1. `IWatchFeatureManager`
An injectable interface that provides methods to check if a feature or a card (group of features) is supported by the current watch.

- `isFeatureSupported(featureId: String)`: Returns true if the specific feature is available.
- `isCardSupported(cardId: String)`: Returns true if any of the features within a card group are available.
- `getString(id: String)`: Retrieves watch-specific strings (e.g., light duration).

### 2. `WatchFeature` Composable
A wrapper component used in Jetpack Compose to conditionally render UI blocks.

```kotlin
WatchFeature(id = "locale.date_format") {
    Row(...) {
        // This Row will only be composed if the watch supports date format settings
    }
}
```

### 3. `WatchAppCard` Composable
A specialized version of `AppCard` that automatically handles its own visibility. If the features it contains are not supported, it can either hide completely or display an "N/A" placeholder.

## How to Add a New Feature

If a new attribute is added to `WatchInfo` (e.g., `hasTideGraph`), follow these steps:

### Step 1: Add a Unique ID
Choose a unique string ID for the feature, such as `"settings.tide_graph"`.

### Step 2: Update `WatchFeatureManager.kt`
Add the mapping between your new ID and the `WatchInfo` property in the `featureMap`:

```kotlin
private val featureMap = mapOf(
    // ...
    "settings.tide_graph" to { WatchInfo.hasTideGraph }
)
```

### Step 3: Update `cardGroups` (Optional)
If this feature belongs to a specific card, add the ID to the corresponding group in `cardGroups`.

### Step 4: Use in UI
Wrap your UI components with `WatchFeature`:

```kotlin
WatchFeature(id = "settings.tide_graph") {
    TideGraphSettingsRow(...)
}
```

## ViewModel Usage

ViewModels should not access `WatchInfo` directly. Instead, inject `IWatchFeatureManager`:

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val watchFeatureManager: IWatchFeatureManager
) : ViewModel() {
    
    fun someLogic() {
        if (watchFeatureManager.isFeatureSupported("some_id")) {
            // Perform feature-specific logic
        }
    }
}
```

## Benefits
- **Decoupling**: UI doesn't need to know about the internal structure of `WatchInfo`.
- **Testability**: You can easily mock `IWatchFeatureManager` for unit tests and Compose previews.
- **Consistency**: Centralized logic ensures that the same feature is hidden/shown consistently across all screens.
