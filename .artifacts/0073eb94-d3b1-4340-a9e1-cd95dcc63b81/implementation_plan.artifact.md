# Implementation Plan - Voice Help Command

Implement a "Help" voice command that provides users with a detailed explanation of supported features and examples. Also, update the initial prompt in verbose mode to guide users towards this help command.

## User Review Required

> [!IMPORTANT]
> **Help Command Availability**: The "Help" command will be available from any screen, not just in verbose mode. When triggered, it will speak a comprehensive guide and then automatically start listening again for a follow-up command.

## Proposed Changes

### [Voice Engine]

#### [MODIFY] [VoiceCommand.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceCommand.kt)
- Add `object Help : VoiceCommand()` to the sealed class.

#### [MODIFY] [IntentParser.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/IntentParser.kt)
- Add `helpPattern = Regex("help", RegexOption.IGNORE_CASE)` to recognize the "Help" command.

#### [MODIFY] [VoiceCommandTable.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceCommandTable.kt)
- Add an entry for `VoiceCommand.Help::class` (using `SetTimeAction` as a placeholder for the required `actionClass`, as it won't be used).

#### [MODIFY] [VoiceDispatcher.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceDispatcher.kt)
- Update `dispatch()` to handle the `Help` command:
    - When recognized, speak the detailed help text provided by the user (with minor typo corrections for clarity).
    - After speaking the help text, call `listenAgain()` so the user can immediately follow up with a real command.

### [UI & ViewModels]

#### [MODIFY] [TimeViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt)
- Update the `StartVoiceCommand` action in `onAction()`:
    - Change the verbose mode prompt from "Tell me what to do" to "Tell me what to do or say 'Help'".

## Verification Plan

### Automated Tests
- Build `app:assembleGithubDebug` to ensure all components are correctly integrated.
- Run unit tests in `IntentParserTest.kt` to verify "help" recognition.

### Manual Verification
1.  **Initial Prompt**: Start voice command in verbose mode. Verify the app says "Tell me what to do or say 'Help'".
2.  **Help Command**: Say "Help" during the session. Verify the app speaks the full detailed guide and then starts the microphone again (red indicator).
3.  **Help Everywhere**: Ensure that even if verbose mode is OFF, saying "Help" still provides the audio guide.
4.  **Abandonment**: Verify "Cancel", "Abort", or "Stop" still work as expected during the help flow or any other multi-turn flow.
