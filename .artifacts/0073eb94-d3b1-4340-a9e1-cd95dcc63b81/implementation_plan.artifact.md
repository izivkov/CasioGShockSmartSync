# Implementation Plan - Refined Voice UI & Verbose Mode

Modify the voice command UI to include a "Verbose" mode toggle and wrap the triggers in a nested card.

## User Review Required

> [!IMPORTANT]
> **Nested Card UI**: I will implement a small nested `AppCard` in the top-right of the Watch Name card to house both the "Verbose" switch and the mic icon.

## Proposed Changes

### [Persistence]

#### [MODIFY] [LocalDataStorage.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/utils/LocalDataStorage.kt)
- Add `getVoiceVerbose(context: Context): Boolean` (defaults to `true`).
- Add `setVoiceVerbose(context: Context, value: Boolean)` method.

### [Domain Model & Logic]

#### [MODIFY] [TimeViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt)
- Update `TimeState` to include `isVoiceVerbose: Boolean`.
- Load the verbose setting in `refreshState()`.
- Add `SetVoiceVerbose(enabled: Boolean)` action to `TimeAction`.
- Handle `SetVoiceVerbose` in `onAction`: update state and save to `LocalDataStorage`.

#### [MODIFY] [VoiceDispatcher.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceDispatcher.kt)
- Update `dispatch()` and other speaking logic to check `LocalDataStorage.getVoiceVerbose()` before calling `speechFeedback.speak()`.
- Ensure the initial "Tell me what to do" prompt also respects this setting if applicable (though usually, if you pressed the button, you want the prompt. The user said "Verbose" switch, usually implies the *output* feedback).

### [UI Components]

#### [MODIFY] [WatchNameView.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/WatchNameView.kt)
- Add "Verbose" label and switch.
- Wrap the Switch and `VoiceCommandTrigger` in a small nested `AppCard`.
- Position this nested card in the `TopEnd` of the main `WatchNameView`.

## Verification Plan

### Automated Tests
- Build `app:assembleGithubDebug` to ensure no UI or logic regressions.

### Manual Verification
1.  **UI Layout**: Verify the mic and switch are in a small card in the top-right.
2.  **Persistence**: Toggle "Verbose" to false, restart app, verify it's still false.
3.  **Functionality**:
    *   Verbose ON: App speaks prompt and confirmations.
    *   Verbose OFF: App remains silent but still executes commands.
