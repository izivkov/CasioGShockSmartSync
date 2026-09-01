# Voice Command Layer — Feature Spec

## Overview

Add a voice-driven command layer to CasioGShockSmartSync. First iteration scope:

- A "Tell me what to do" button on the main watch/time screen.
- On tap, capture speech, parse it into a known intent, and execute the
  matching action.
- Supported intent categories for v1:
  - **Alarms** — e.g. "wake me up at 6am tomorrow"
  - **Reminders** — e.g. "remind me dentist appointment on Tuesdays"
  - **Settings** — e.g. "enable auto light"
- Per-action enable/disable, so specific voice-triggered actions can be
  turned off independently (e.g. allow alarms, disable photo capture).

## Constraints

- **No proprietary/heavy AI libraries.** No bundled LLM. Use native
  Android speech-to-text and a rule-based (regex / keyword) intent
  parser — no cloud NLU service.
- **F-Droid compatibility.** The app must remain buildable and
  distributable via F-Droid:
  - `android.speech.SpeechRecognizer` / `RecognizerIntent` are part of
    AOSP itself — no proprietary code is linked into the APK, so this
    doesn't block F-Droid inclusion.
  - The actual recognition service (typically Google's, via Play
    Services) runs outside the app, on-device. F-Droid doesn't vet what
    other apps/services are installed — only what's bundled in ours.
  - Expect F-Droid to tag the app with a `NonFreeNet` (or similar)
    anti-feature flag, disclosing that it may connect to a non-free
    network service when that service is present. This is a disclosure,
    not a rejection, and is common practice for apps with optional
    Play-Services-backed features.
  - Must **gracefully degrade**: check
    `SpeechRecognizer.isRecognitionAvailable()` and show "voice input
    unavailable" when no recognition service is present (e.g. on
    de-Googled devices). Do not hard-require Google Play Services.
  - Optional future path: swap in **Vosk** (Apache 2.0, fully offline,
    on-device) if strict FOSS/no-non-free-network-dependency is ever
    required instead of the graceful-degradation approach.

## Existing codebase — what's already there

Location: `app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/`

The action-registry and enable/disable infrastructure this feature needs
**already exists** — no new registry should be built.

- **`ActionsViewModel.Action`** (abstract inner class) — base class for
  every action. Already carries:
  - `title: String`
  - `enabled: Boolean`
  - `run(context: Context)`
  - `save()` / `load()` — persistence via `ActionsStorage` /
    `LocalDataStorage`
  - `shouldRun(runEnvironment: RunEnvironment): Boolean` — gates
    execution based on how the action was triggered
- **`RunEnvironment`** (enum) — existing trigger sources:
  `NORMAL_CONNECTION`, `ACTION_BUTTON_PRESSED`, `AUTO_TIME_ADJUSTMENT`,
  `FIND_PHONE_PRESSED`, `ALWAYS_CONNECTED`
- **`ActionItem.kt`** — Compose UI row with a title and an `AppSwitch`
  bound to `isEnabled` / `onEnabledChange`. This is the existing
  enable/disable UI; reuse it, don't build a new settings screen for
  voice actions specifically.
- **`ActionsViewModel`**:
  - `getAction(type: Class<T>): T` — look up a registered action by
    class.
  - `updateAction(updatedAction: T)` — update + persist an action.
  - `runFilteredActions()` — existing dispatch pattern, filters
    `_actions.value` by `shouldRun(environment)` and runs them (SYNC
    actions first, then ASYNC via coroutine).
  - Example existing actions: `SetTimeAction`, `SetEventsAction`
    (reminders — backed by `CalendarEvents` / `api.setEvents()`),
    `FindPhoneAction`, `PhotoAction`, `ToggleFlashlightAction`,
    `PrayerAlarmsAction`, `PhoneDialAction`.
- **`ActionRunner.kt`** — subscribes to app-wide events
  (`ButtonPressedInfoReceived`, `RunActions`) and calls the matching
  `actionsViewModel.runActionsFor...()` method. This is the pattern a
  voice trigger should follow.

## What to build

### 1. Speech capture
- `android.speech.SpeechRecognizer` / `RecognizerIntent` for STT.
- Check `isRecognitionAvailable()` before offering the feature; show a
  clear "voice input unavailable" state otherwise.
- New UI: "Tell me what to do" button on the main time screen, launching
  a listening state and showing the recognized text.

### 2. Intent parser (new)
- Small, rule-based (regex + keyword matching + `java.time` for
  date/time parsing — no ML/LLM).
- Input: recognized text string.
- Output: a resolved intent — target `Action` class (or a new
  action-like handler for settings) plus extracted parameters (time,
  day-of-week, label, etc.).
- Unmatched/ambiguous phrases should fail gracefully (e.g. "sorry, I
  didn't understand that").

### 3. New `RunEnvironment.VOICE_COMMAND`
- Add to the existing `RunEnvironment` enum.
- Each relevant `Action.shouldRun()` override adds a `VOICE_COMMAND ->
  enabled` branch (mirroring the existing pattern used for
  `ACTION_BUTTON_PRESSED`, etc.), so the existing per-action `enabled`
  flag also gates voice execution — no new enable/disable mechanism
  needed.

### 4. Voice dispatcher (new, alongside `ActionRunner.kt`)
- On a resolved intent: `actionsViewModel.getAction(type)`, check
  `enabled`, and either call `run(context)` directly or route through
  the existing `runFilteredActions()` pattern with the new
  `VOICE_COMMAND` environment.
- If the action is disabled, respond to the user (toast/snackbar/spoken
  reply) that it's turned off, rather than silently ignoring the
  command.

### 5. New action types needed
- **`SetAlarmAction`** (new) — wraps `AlarmManager`. Params: time
  (and optionally date, for one-off vs. recurring).
- **Reminders** — largely covered by existing `SetEventsAction` /
  `CalendarEvents` / `api.setEvents()`. The parser just needs to
  produce an `Event` (title + recurrence, e.g. "Tuesdays") to feed into
  the existing flow.
- **Settings** (e.g. "enable auto light") — likely *not* a new `Action`
  subclass. More likely a direct toggle against
  `LocalDataStorage`/`watchFeatureManager`-style settings storage,
  parsed and applied by the voice dispatcher directly.

## Effort estimate

Given the existing action/registry infrastructure is reused rather than
rebuilt:

| Piece | Estimate |
|---|---|
| STT capture + "Tell me what to do" UI | 2–3 days |
| Intent parser (alarm, reminder, settings — rule-based) | ~1 week |
| `VOICE_COMMAND` environment + dispatcher + `SetAlarmAction` | 3–4 days |
| Integration testing (Hilt/DI singleton pattern, persistence) | a few days |
| **Total** | **~2–3 weeks**, one developer familiar with the codebase |

## Open items / follow-ups
- Decide phrasing coverage for v1 (how much date/time fuzziness to
  support — "Tuesdays" vs "next Tuesday" vs "every Tuesday").
- Decide user feedback channel for disabled/unmatched commands (visual
  only, or also spoken via TTS).
- Confirm whether "settings" voice commands (e.g. auto light) map to
  existing `watchFeatureManager` toggles or need new storage keys.
