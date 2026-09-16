# Implementation Plan - Support "X weeks from [day of week]" in Voice Reminders

Add support for fuzzy date phrases like "(One) week (from) monday" to the voice command intent parser. This allows users to set reminders more naturally when asked "when".

## User Review Required

> [!NOTE]
> The implementation will support variations such as:
> - "week from monday"
> - "one week from monday"
> - "2 weeks from tuesday"
> - "week monday"
>
> "monday" will be interpreted as the closest upcoming Monday (including today). "week from monday" will be that Monday plus one week.

## Proposed Changes

### [Voice Engine]

#### [MODIFY] [IntentParser.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/voice/IntentParser.kt)
- Define `weeksFromPattern` regex to capture week counts and day names.
- Update `parseDate` function:
    - Define a `daysOfWeek` map at the top of the function to be shared.
    - Check for `weeksFromPattern` matches before the general day-of-week loop.
    - Calculate the target date by finding the upcoming specified day and adding the requested number of weeks.

## Verification Plan

### Automated Tests
- Add a new test method `testParseDateFuzzyWeeks()` to [IntentParserTest.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/test/java/org/avmedia/gshockGoogleSync/voice/IntentParserTest.kt).
- Mock or assume the current date to verify:
    - "week from monday" -> Sep 21 (assuming today is Mon Sep 14)
    - "one week from monday" -> Sep 21
    - "2 weeks from tuesday" -> Sep 29 (assuming today is Mon Sep 14)
- Run `app:testGithubDebugUnitTest` to verify all tests pass.

### Manual Verification
- Deploy the app.
- Use a voice reminder command: "Remind me to buy milk".
- When asked "when", say "week from monday".
- Verify the reminder is created with the correct date on the watch.
