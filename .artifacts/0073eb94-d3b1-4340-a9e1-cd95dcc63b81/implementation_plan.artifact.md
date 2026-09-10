# Implementation Plan - Disable Alarms Voice Command

Add a new "Disable Alarms" voice command that disables all alarms on the watch without resetting their times to 12:00 AM.

## Proposed Changes

### [Voice Engine]

#### [NEW] [VoiceCommand.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceCommand.kt)
- Add `DisableAllAlarms` to `VoiceCommand`.

#### [MODIFY] [IntentParser.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/IntentParser.kt)
- Update `clearAlarmsPatterns` or create a new `disableAlarmsPatterns` to recognize "Disable Alarms".
- Map "disable alarms", "turn off alarms", "stop alarms" to `VoiceCommand.DisableAllAlarms`.

#### [MODIFY] [VoiceCommandTable.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceCommandTable.kt)
- Add a mapping for `VoiceCommand.DisableAllAlarms`.
- It will route to the same `ClearAllAlarmsAction` but with a flag or separate action class.

### [Actions]

#### [MODIFY] [ActionViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt)
- **`DisableAllAlarmsAction`**: Create a new inner class `DisableAllAlarmsAction` (or modify `ClearAllAlarmsAction` to accept a parameter).
- Logic:
    1. Fetch current alarms.
    2. Set `enabled = false` for all slots.
    3. **Crucial**: Keep existing `hour` and `minute` values.
    4. Write back to watch and emit `AlarmsUpdated`.

## Verification Plan

### Manual Verification
1.  **Disable Alarms**:
    - Have some alarms set at non-12:00 times (e.g., 7:30 AM).
    - Say *"Disable all alarms."*
    - Verify they are disabled but still show "07:30" on the Alarms screen.
2.  **Clear Alarms (Regression)**:
    - Say *"Clear all alarms."*
    - Verify they are disabled AND reset to 12:00 AM.
3.  **Voice Feedback**: Verify app says "All alarms disabled" for the new command.
