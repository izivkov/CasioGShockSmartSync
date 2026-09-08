# Implementation Plan - Voice Command Fixes (Error 11 & UI Refresh)

Address the "Unknown error (11)" issue and ensure the UI correctly refreshes after a voice command by optimizing the data reload logic.

## User Review Required

> [!NOTE]
> **Error 11 (Server Disconnected)**: This error is often a transient system message from the Google Speech engine after delivering a result. I will implement a guard to ignore errors once a valid voice command has been captured.

## Proposed Changes

### [Voice Engine]

#### [MODIFY] [VoiceCommandManager.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceCommandManager.kt)
- Add a `resultDelivered: Boolean` flag to the `RecognitionListener`.
- Set the flag to `true` in `onResults`.
- In `onError`, only call the `onError` callback if `resultDelivered` is `false`. This prevents system-level cleanup errors from showing up as user-facing "Unknown errors."

### [UI Components & ViewModels]

#### [MODIFY] [TimeViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt)
- **Granular Refresh**: Implement a `refreshTimer()` private method that only fetches the timer value from the watch.
- **Efficient Refresh**: Update the `TimerUpdated` event listener to call `refreshTimer()` instead of the full `refreshState()`. This ensures the timer updates instantly without waiting for battery, temperature, and steps to be fetched.
- **Event Handling**: Ensure the `ProgressEvents` subscription is robust.

## Verification Plan

### Manual Verification
1.  **Error 11 Suppression**: Use a voice command. Verify that after the command is understood, no "Unknown error (11)" snackbar appears.
2.  **Timer Refresh**: Say "Set timer for 3 seconds." Verify that the Time screen's timer display updates to 0:03 almost immediately after the watch write completes.
3.  **Stability**: Verify that other watch info (battery, name) remains visible and doesn't flicker during the timer refresh.
