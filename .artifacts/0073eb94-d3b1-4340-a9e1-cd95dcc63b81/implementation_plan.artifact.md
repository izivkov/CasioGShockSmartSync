# Implementation Plan - Fix Settings "Send to Watch" Logic

Address the issue where `SettingsViewModel` triggers multiple unintended actions by calling a batch action runner. Aligns settings updates with the direct API pattern used by `TimerView` and `TimeViewModel`.

## User Review Required

> [!IMPORTANT]
> I will bypass `ActionsViewModel` for the direct "Send to Watch" button in the Settings screen. This matches the pattern established in the Timer screen and prevents the "action storm" caused by the `DIRECT_INVOCATION` batch filter.

## Proposed Changes

### [Settings]

#### [MODIFY] [SettingsViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/settings/SettingsViewModel.kt)
- In `sendToWatch()`:
    - Remove the dependency on `ActionsViewModel` for performing the write.
    - Launch a coroutine to call `api.setSettings(settings)` directly.
    - Emit `ProgressEvents.onNext("SettingsUpdated")` to refresh the UI.
    - Show the success Snackbar: `"Settings sent to watch"`.

### [Actions]

#### [MODIFY] [ActionViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt)
- **`SetSettingsAction`**:
    - Remove the `fullSettings` property.
    - Clean up `runSuspend()` to only handle individual setting updates (used by voice commands).
- **Gating cleanup**:
    - Remove `RunEnvironment.DIRECT_INVOCATION` from all `shouldRun()` overrides.
    - **Reasoning**: Direct triggers from code should use `actionsViewModel.runSingleAction(action)`, which bypasses the `shouldRun()` filter entirely. This prevents `runFilteredActions()` from ever triggering these actions as a batch.

## Verification Plan

### Automated Tests
- Run `app:assembleGithubDebug` to verify compilation.

### Manual Verification
1.  **Settings Sync**:
    - Change a setting (e.g., Light Duration) in the Settings screen.
    - Tap "Send to Watch".
    - Verify that **only** settings are updated (no accidental Alarms/Timer reset).
    - Verify the "Settings sent to watch" Snackbar appears.
2.  **Voice Commands (Regression)**:
    - Say *"Set language to Spanish"*.
    - Verify that individual setting updates via voice still work correctly through `ActionsViewModel`.
3.  **Timer (Regression)**:
    - Verify that sending a Timer to the watch still works as before.
