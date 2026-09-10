# Implementation Plan - Forgiving Voice Commands (Leeway & Corrections)

Enhance the voice command system to handle slower responses, hesitations, and self-corrections (e.g., "Monday, I mean Tuesday") for a more natural interaction.

## Proposed Changes

### [Voice Engine]

#### [MODIFY] [VoiceCommandManager.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/VoiceCommandManager.kt)
- **Increase Silence Timeouts**: Add `RecognizerIntent` extras to the listening intent to give the user more time before the recognizer stops:
    - `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`: Set to 3000ms (standard is usually ~1000-2000ms).
    - `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS`: Set to 2000ms.
    - `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS`: Set to 2000ms to encourage longer capture.

#### [MODIFY] [IntentParser.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/IntentParser.kt)
- **Correction Handling**:
    - Introduce a `handleSelfCorrection(text: String): String` method.
    - Look for "correction markers" like: "i mean", "actually", "no wait", "sorry".
    - If a marker is found, discard everything before the *last* occurrence of the marker and parse only the remainder.
- **Hesitation Filtering**:
    - Strip common hesitation fillers like "um", "uh", "mm", "ah" from the cleaned text before parsing.

## Verification Plan

### Manual Verification
1.  **Slower Response**: Start a command, pause for 2 seconds, then finish. Verify the recognizer doesn't cut you off prematurely.
2.  **Self-Correction**: Say *"Set alarm for Monday, I mean Tuesday."* Verify it parses "Tuesday" and sets the alarm correctly.
3.  **Complex Correction**: Say *"Remind me to buy milk, actually bread, no I mean eggs."* Verify it adds a reminder for "eggs".
4.  **Hesitation**: Say *"Set timer for... uh... mm... three minutes."* Verify it correctly parses "3 minutes".
