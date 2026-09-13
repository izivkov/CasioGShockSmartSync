# Walkthrough - Cyrillic Support in Reminders

I have updated the event title sanitization logic to allow Cyrillic characters in the reminder edit dialog. This enables users to enter reminders in their native Cyrillic-based languages.

## Changes

### Events Component

#### [EventUtils.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/events/EventUtils.kt)
- Updated the `filterAllowedCharacters` function's regular expression to include the Cyrillic Unicode range (`\u0400-\u04FF`).
- This allows characters from Russian, Bulgarian, Ukrainian, and other Cyrillic alphabets to be preserved during title sanitization.

## Verification Results

### Automated Tests
- Successfully ran `app:assembleGithubDebug` to verify that the project still compiles correctly with the updated logic.

### Manual Verification
- You can now type Cyrillic titles like "Купить молоко" in the Reminder edit dialog.
- The app will display these titles in its internal list.
- When "Sending to Watch", the existing `CyrillicToLatin` engine will automatically transliterate these to plain Latin characters for the watch's display.
