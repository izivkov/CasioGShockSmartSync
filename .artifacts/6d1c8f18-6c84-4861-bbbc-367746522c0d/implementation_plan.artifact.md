# Implementation Plan - Move Voice Command Button to WatchName Card

Relocate the "Tell me what to do" (voice command) trigger from its standalone card in `TimeScreen` into the `WatchNameView` card, positioned in the upper-right corner.

## Proposed Changes

### UI Components

#### [MODIFY] [TimeScreen.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeScreen.kt)
- Remove the `voiceCard` `AppCard` implementation.
- Update the `watchName` constraint to link its bottom to `watchInfo.top` directly.
- Clean up `createRefs()` by removing `voiceCard`.

#### [MODIFY] [WatchNameView.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/WatchNameView.kt)
- Add necessary imports for icons and animations (if any).
- If `isVoiceCommandSupported` is true, add a mic trigger in the `TopEnd` of the card's `Box`.
- Implement the same logic as the original button:
    - `clickable` calls `timeModel.onAction(TimeAction.StartVoiceCommand)`.
    - Show a `CircularProgressIndicator` overlay when `state.isListening` is true.
    - Use `R.drawable.voice_assist` for the icon.

## Verification Plan

### Automated Tests
- Build the app to ensure no compilation errors.

### Manual Verification
- Verify the "Tell me what to do" button is no longer a large card at the bottom.
- Verify a mic icon appears in the upper-right corner of the `WatchName` card.
- Verify that clicking the mic icon triggers voice recognition (shows listening state with indicator).
- Verify the listening state works correctly (icon tint changes, indicator appears).
