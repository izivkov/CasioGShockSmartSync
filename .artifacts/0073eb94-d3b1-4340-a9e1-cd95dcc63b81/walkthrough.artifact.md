# Walkthrough - New Voice Command: "Set settings to default"

I have added a new voice command that allows you to reset your watch settings to their smart defaults hands-free.

## Changes

### Voice Engine

#### [VoiceCommand.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceCommand.kt)
- **New Command**: Added `SetSettingsToDefault` to the `VoiceCommand` sealed class.

#### [IntentParser.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/IntentParser.kt)
- **Pattern Recognition**: Added a regex to recognize phrases like *"Set settings to default"*, *"Reset settings to defaults"*, and *"Settings to default"*.

#### [VoiceDispatcher.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceDispatcher.kt)
- **Audio Feedback**: Added spoken confirmation: *"Settings reset to defaults"*.
- **Help Update**: Added the new command to the "Help" guide so users can discover it.

### Voice Actions

#### [ActionViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt)
- **New Action**: Implemented `SetSettingsToDefaultAction` which calculates the smart defaults (matching the logic in `SettingsViewModel`) and writes them directly to the watch hardware.

## Verification Results

### Automated Tests
- Successfully ran unit tests in `IntentParserTest.kt` (9 passed, 0 failed).
- Verified the build with `app:assembleGithubDebug`.

### Manual Interaction
- **Reset Flow**: Say *"Set settings to default"*. The app calculates the best settings for your current locale and watch model, sends them to the watch, and speaks *"Settings reset to defaults"*.
- **UI Sync**: The Settings screen automatically refreshes to show the new default values.
