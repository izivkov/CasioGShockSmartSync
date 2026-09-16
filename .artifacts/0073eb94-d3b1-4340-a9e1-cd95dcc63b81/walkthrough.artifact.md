# Walkthrough - Fuzzy Week-Based Date Parsing for Voice Reminders

I have implemented support for natural language phrases like **"(One) week (from) monday"** in the voice command intent parser. This makes setting reminders much more intuitive when the app asks "when".

## Changes

### Voice Engine

#### [IntentParser.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/IntentParser.kt)
- Added `weeksFromPattern` regex to capture week counts (digits or words like "one", "two") and the target day of the week.
- Updated the `parseDate` function to detect these phrases.
- The logic finds the next occurrence of the specified day (including today) and then offsets it by the requested number of weeks.
- Supported variations include:
    - "week from monday" (defaults to 1 week)
    - "one week from tuesday"
    - "2 weeks from wednesday"
    - "week friday"

## Verification Results

### Automated Tests
- Added a new unit test `testParseDateFuzzyWeeks()` to `IntentParserTest.kt`.
- Verified that phrases like "week from monday" and "2 weeks from tuesday" are correctly parsed into the expected future dates relative to today.
- Successfully executed `:app:testGithubDebugUnitTest` with all 10 tests passing.

### Manual Verification
- Users can now use these phrases when interactively setting reminders via voice.
- Example flow:
    - User: "Remind me to call Mom"
    - App: "When do you want to be reminded?"
    - User: "Week from Monday"
    - Result: A reminder is set for the Monday following the next upcoming Monday.
