# Implementation Plan - Early Feature Gating & Expanded Abandon Keywords

Ensure all voice commands are validated against watch hardware capabilities *before* starting any interaction, and add more ways to terminate a multi-turn conversation.

## User Review Required

> [!IMPORTANT]
> **Proactive Gating**: The app will now check if your watch supports reminders (e.g., ABL-100 doesn't) immediately after you say "Add a reminder", preventing it from asking for details it can never save.

## Proposed Changes

### [Voice Engine]

#### [MODIFY] [VoiceDispatcher.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceDispatcher.kt)
- **Expanded Exit Keywords**: Add "stop", "abandon", "abort", and "forget it" to the termination check.
- **Early Validation**:
    - Reorder logic in `dispatch()` to perform `voiceCommandTable` lookup and `isSupported` check *before* handling any specific command types (including multi-turn Reminders).
    - If a feature is not supported by the hardware, the app will inform you immediately by voice: **"This feature is not supported on the watch"** and stop execution.

## Verification Plan

### Manual Verification
1.  **Gating (Reminders)**: Connect an `ABL-100`. Say *"Add a reminder."* Verify the app immediately responds *"This feature is not supported on the watch"* instead of asking *"What is the reminder for?"*.
2.  **Gating (Settings)**: Say *"Enable auto light"* on a watch that doesn't support it. Verify the same generic "not supported" voice feedback.
3.  **Abandonment**: Start a reminder flow. When asked for the date, say *"Forget it."* Verify the conversation ends and the app responds *"Canceled."* (Also test *"Stop"*, *"Abort"*, and *"Abandon"*).
