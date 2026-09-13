# Implementation Plan - Allow Cyrillic Alphabet in Reminders

Update the reminder title sanitization logic to allow Cyrillic characters in the UI. This enables users to type reminders in their native language, which will then be automatically transliterated to Latin for the watch's display.

## User Review Required

> [!NOTE]
> **Transliteration Behavior**: Although Cyrillic is allowed in the app UI, the title will still be transliterated to plain ASCII when sent to the watch, as G-Shock hardware only supports a limited Latin character set.

## Proposed Changes

### [Events Component]

#### [MODIFY] [EventUtils.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/events/EventUtils.kt)
- Update the `filterAllowedCharacters` regex to include the Cyrillic character range (`\u0400-\u04FF`).

## Verification Plan

### Manual Verification
1. Open the "Events" screen and edit a reminder.
2. Enter a title using Cyrillic characters (e.g., "Купить молоко").
3. Verify that the characters are successfully entered and not filtered out.
4. Save the reminder and verify it displays correctly in the app's event list.
5. (Optional) "Send to Watch" and verify it appears transliterated on the watch display.
