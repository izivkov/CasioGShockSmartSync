# Implementation Plan - Split ActionsViewModel into Container and ViewModel

Refactor the actions architecture by moving the process-lived state and logic into a `Singleton` `ActionContainer`, while keeping `ActionsViewModel` as a thin, screen-scoped wrapper for Compose.

## User Review Required

> [!IMPORTANT]
> **Refactoring Strategy**:
> 1.  **`ActionContainer` (Singleton)**: Will host all `Action` subclasses, the master `actions` flow, and the logic for running/saving/loading actions. It will be injected by background components and other ViewModels.
> 2.  **`ActionsViewModel` (ViewModel)**: Will stay as the UI entry point for the Actions screen, but it will delegate all its data and operations to the `ActionContainer`.

## Proposed Changes

### [Actions Architecture]

#### [MODIFY] [ActionContainer.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionContainer.kt)
- Ensure all logic (registration, runFilteredActions, runSingleAction, etc.) is fully moved here from the old `ActionsViewModel`.
- Ensure all inner `Action` subclasses (like `SetAlarmAction`) are defined inside this class.

#### [MODIFY] [ActionsViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt)
- Refactor to inject `ActionContainer`.
- Expose properties like `actions` and `uiEvents` by delegating to the container.

### [Dependency Injection Refactoring]

#### [MODIFY] Background Components
Update these components to inject `ActionContainer` instead of `ActionsViewModel`:
- **[ActionRunner.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionRunner.kt)**
- **[VoiceDispatcher.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceDispatcher.kt)**
- **[VoiceCommandTable.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceCommandTable.kt)** (Update casting and class references).

#### [MODIFY] Feature ViewModels
Update these ViewModels to inject `ActionContainer`:
- **[AlarmViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmViewModel.kt)**
- **[EventViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/events/EventViewModel.kt)**
- **[SettingsViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/settings/SettingsViewModel.kt)**
- **[TimeViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt)**

#### [MODIFY] UI Components
- **[ActionsScreen.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionsScreen.kt)** and individual action views (e.g., `FlashlightView.kt`, `PhoneView.kt`): Update references to inner classes, which are now `ActionContainer.ToggleFlashlightAction` instead of `ActionsViewModel.ToggleFlashlightAction`.

### [Build & System]

#### [MODIFY] [proguard-rules.pro](file:///home/izivkov/projects/CasioGShockSmartSync/app/proguard-rules.pro)
- Update `-keep` rules to target `ActionContainer` and its inner classes.

## Verification Plan

### Automated Tests
- Run `app:assembleGithubDebug` to ensure all type references and DI injections are correct.

### Manual Verification
1.  **Watch Actions**: Verify that pressing the watch action button still triggers the correct action (verifies `ActionRunner` + `ActionContainer` singleton state).
2.  **Voice Interaction**: Verify voice commands still work (verifies `VoiceDispatcher` + `ActionContainer`).
3.  **UI Updates**: Verify that the Actions screen still displays the current enabled state of actions and reflects updates immediately.
