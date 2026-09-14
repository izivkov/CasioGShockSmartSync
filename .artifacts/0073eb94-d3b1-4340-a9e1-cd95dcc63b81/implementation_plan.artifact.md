# Implementation Plan - Lint and Fix

Address various lint warnings across the project including unused imports, deprecated API usages, legacy `delay` overloads, and unused variables to improve code quality and maintainability.

## User Review Required

> [!NOTE]
> I will be removing several classes and functions that are marked as "unused" by the analyzer. If any of these are intended for future use, please let me know.
> - `SetLocationAction`, `MapAction` in `ActionContainer.kt`
> - `runFilteredActions`, `runSingleAction`, `emitUiEvent`, `saveWithMessage` in `ActionContainer.kt`
> - `DnD` in `SettingsViewModel.kt`
> - `UpdateSetting`, `SetSmartDefaults`, `SendToWatch` actions in `SettingsViewModel.kt` (if they are truly unused by any Compose triggers)

## Proposed Changes

### [Core Actions]

#### [MODIFY] [ActionContainer.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionContainer.kt)
- Remove unused import `androidx.hilt.navigation.compose.hiltViewModel`.
- Lowercase `ENABLED` to `enabled`.
- Remove unused variables `key` and `value` inside `save()`.
- Remove unused action classes: `SetLocationAction`, `MapAction`.
- Remove unused public functions: `runFilteredActions`, `runSingleAction`, `emitUiEvent`, `saveWithMessage`.
- Fix `SimpleDateFormat` by adding `Locale.US`.
- Convert `delay(0)` to `delay(Duration.ZERO)`.
- Fix lambda argument placement.

#### [MODIFY] [ActionViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt)
- Remove unused import `androidx.hilt.navigation.compose.hiltViewModel`.
- Add missing trailing comma.

#### [MODIFY] [ActionsScreen.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionsScreen.kt)
- Update `hiltViewModel` import to `androidx.hilt.navigation.compose.hiltViewModel`.
- Remove unused `actions` variable in `createActionItems`.

### [Voice Engine]

#### [MODIFY] [VoiceDispatcher.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceDispatcher.kt)
- Convert `delay(1000)` to `1.seconds`.
- Replace cascade `if` with `when` for date feedback.
- Add clarifying parentheses to `hour12` calculation.
- Clean up minute string template.

### [Feature ViewModels]

#### [MODIFY] [AlarmViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/AlarmViewModel.kt)
- Remove unused import `org.avmedia.gshockGoogleSync.R`.
- Convert `delay(1000L)` to `1.seconds`.
- Remove unused `index` in `forEach` loop.

#### [MODIFY] [EventViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/events/EventViewModel.kt)
- Remove redundant `super.onCleared()`.
- Remove unused `ShowSnackbar` UI event.

#### [MODIFY] [SettingsViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/settings/SettingsViewModel.kt)
- Remove unused import `org.avmedia.gshockGoogleSync.R`.
- Remove unused `DnD` class.
- Remove unused `SettingsAction` subclasses if confirmed.
- Replace explicit `jsonObj.get(key)` with index access `jsonObj[key]`.
- Add clarifying parentheses to `enablePowerSetting` logic.

#### [MODIFY] [TimeViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt)
- Remove redundant `super.onCleared()`.
- Fix `delay` duration overload.
- Add clarifying parentheses to timer calculation.

## Verification Plan

### Automated Tests
- Run `app:assembleGithubDebug` to ensure no syntax errors were introduced during cleanup.
- Run `app:testGithubDebugUnitTest` to verify no regressions in `IntentParser`.

### Manual Verification
- Verify that the app still connects and syncs correctly.
- Verify that voice commands and action button triggers still function.
