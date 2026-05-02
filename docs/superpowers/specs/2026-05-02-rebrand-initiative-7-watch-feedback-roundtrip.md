# Initiative #7 — Watch-Side Feedback Round-Trip

> Sub-spec under the umbrella roadmap `docs/superpowers/specs/2026-05-02-rebrand-overhaul-roadmap.md`. The last initiative of the rebrand. Lands on top of #1–#6 and depends on #4's broadcast emission, #5's sync-history store, and #6's `CurrentWatchSelector`. After acceptance, the executable plan is produced via `superpowers:writing-plans`. The roadmap explicitly flags this initiative as needing **API research before design** — the spec encodes a Phase 0 research step with concrete experiments to run before any code changes.

## Context

Initiatives #4 made the watch's button presses emit broadcast intents to the user's automation app (Tasker / MacroDroid / Automate). The current direction is one-way: press → broadcast → automation runs. Initiative #7 closes the loop: the automation app sends a numeric response back, and our app **writes that number to the watch** via a display channel the user can read. The watch model the user owns (Casio ABL-100WE) does not support phone notifications, so the display channel must be a built-in watch field — the timer face is the obvious choice.

This is a small feature with high research-uncertainty: GShockAPI exposes a `setTimer(...)` call, but its persistence semantics, value range, and behaviour mid-running-timer need empirical confirmation. The user has offered to do device-side testing.

## Confirmed decisions (from brainstorm)

| Topic | Decision |
|-------|----------|
| Display channel (v1) | **Timer** (`api.setTimer(...)`). Step-target / step-count are documented fallbacks if the API blocks the timer path during research. |
| Response broadcast | New action `com.beamburst.casswatch.FEEDBACK` with a single `value: Int` extra. Symmetric with outbound `RUN_SHORT` / `RUN_LONG`. |
| Trigger | **Watch button press only.** No phone-side manual trigger UI. The press cards from #4 own the outbound side; #7 only adds the listener. |
| Timeout | **5 seconds, silent on miss.** No sentinel value written; sync-history entry records the miss. |
| Master switch | **New Settings card "Feedback round-trip"** with a single toggle, default OFF. When OFF, inbound FEEDBACK broadcasts are dropped at the receiver. |
| Per-watch dimension | The round-trip writes to `CurrentWatchSelector.currentAddress()` (#6) — the same watch that triggered the press. |
| Correlation | **None needed.** Time-window-based (5s after each press); no `request_id` extra. Simplicity wins. |
| Receiver export | `android:exported="true"` (any app can send `FEEDBACK`). Same wide-open philosophy as outbound. |

## Goals

1. Let the user wire a "watch press → automation app computes → number on watch" loop.
2. Survive the API-research step gracefully — if `setTimer` doesn't behave as expected, the spec already names the fallbacks.
3. Reuse #4's broadcast infra and #5's history store; no new persistence framework.
4. Stay opt-in (default-OFF master switch) so users who don't want this feature don't see watch-state churn on every press.

## Non-goals

- Bidirectional rich messages (text, structured JSON, images). Single integer is the design.
- Multiple simultaneous round-trips. One press = one window; concurrent windows are not tracked.
- Phone-side display of the response (e.g. a snackbar). The whole point is the watch is the display.
- Watches without a timer face (capability-gated; #7 silently no-ops).

---

## 0. Phase 0 — API research (gate before any code)

The implementation plan is **blocked** on these answers. Do them on the user's hardware (Casio ABL-100WE) before writing production code.

| Experiment | Concrete API call | What we want to know | Outcome |
|------------|-------------------|----------------------|---------|
| R-1 | `api.setTimer(seconds = 60)` while watch is on its main face | Does the watch's timer field update without entering timer mode? Or does the watch jump to the timer screen? | Required for v1 — the watch must accept the value silently. |
| R-2 | `api.setTimer(seconds = 86399)` | What's the maximum value accepted? Does it clamp, wrap, or reject? | Defines the value-cap in code. |
| R-3 | `api.setTimer(seconds = 0)` | Does the watch accept 0? Or does it reset/clear instead? | Determines whether `0` is a valid sentinel ("no response"). |
| R-4 | `api.setTimer(seconds = -1)` (invalid) | Does the API throw or silently clamp? | Tells us whether to validate before the call. |
| R-5 | `setTimer(60)` while a timer is running | Does it overwrite, or is it ignored? | If overwriting cancels a running timer, document it as a side-effect. |
| R-6 | Two consecutive `setTimer(60)` calls 1s apart | Both apply, or last-write-wins? | Confirms latest-press semantics. |
| R-7 | `getTimer()` after `setTimer(60)` | Does the round-trip read confirm the value? | Useful for the sync-history "details" payload. |
| R-8 | Capability flags: `WatchInfo.hasTimer`? `hasStopwatch`? Inspect `WatchInfo.kt` (external library) | Find the right capability flag | Code uses this to gate the entire feature. |

If R-1 fails (the watch jumps screens, blocks main use), fall back to step-target (R-1' / R-2' on `api.setStepTarget`) or step-count. If both fail, the spec is moot and #7 is shelved with a documented workaround in `docs/`. Track the outcome in `§13` Open questions.

The research should land in the repo as a small markdown note at `docs/superpowers/research/2026-05-XX-feedback-channel-experiments.md` (or similar), referenced from this spec. The note includes raw observations and the chosen channel.

---

## 1. Architecture

```
External app (Tasker / MacroDroid / Automate)
   │
   │ user presses watch action button
   ▼
WatchButtonDispatcher  ──► sendBroadcast(RUN_SHORT)        ◄──── Initiative #4
   │
   │ (within 5s)
   │
   ▼
sendBroadcast(FEEDBACK, value=42)   ◄── external app's automation
   │
   ▼
FeedbackReceiver (Manifest-registered)
   │
   │ if master switch ON, and a window is open
   ▼
api.setTimer(value)           via FeedbackApplier
   │
   ▼
sync-history entry: trigger=WATCH_BUTTON_FEEDBACK, kinds={TIMER}, success
```

### 1.1 The 5-second window

`WatchButtonDispatcher` from #4 gains a tiny extension: when it emits `RUN_SHORT` or `RUN_LONG`, it tells `FeedbackReceiver` "a press just happened — open a window." The window is a `MutableStateFlow<Long?>` (the deadline timestamp); set on press, cleared after 5s by a `delay(5000)` coroutine.

`FeedbackReceiver`, on receiving a `FEEDBACK` broadcast:

1. If master switch is OFF → return.
2. If `currentAddress()` is null (no watch connected) → log to history as failed, return.
3. If the window is closed (no recent press, or already consumed) → log as "stale FEEDBACK ignored", return.
4. Validate the `value` extra (Int, in `[0, MAX_TIMER_SECONDS]` per Phase 0's R-2 outcome).
5. Call `FeedbackApplier.apply(value)`.
6. Close the window so subsequent FEEDBACKs in the same press cycle are ignored.

Concurrent presses: each new press resets the window. If a press comes while the previous window is still open, the previous is closed (older feedback is dropped — last press wins).

### 1.2 Master switch in Settings

Reuse #4's `PressCard` shared composable. New card titled "Feedback round-trip" with subtitle "Receive numeric feedback from automation apps and show it on the watch's timer." Toggle drives `LocalDataStorage.putBoolean(context, "FeedbackEnabled", v)`. No editor dialog; just on/off.

The card lives in Settings, immediately after the four press cards (Short / Long / Find phone / Flashlight). Settings tab order updates accordingly.

---

## 2. New components

### 2.1 `FeedbackReceiver`

```kotlin
// app/.../FeedbackReceiver.kt   (new)

@AndroidEntryPoint
class FeedbackReceiver : BroadcastReceiver() {
    @Inject lateinit var applier: FeedbackApplier
    @Inject lateinit var window: FeedbackWindow
    @Inject lateinit var history: SyncHistoryLogger
    @ApplicationContext lateinit var context: Context

    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (!LocalDataStorage.getBoolean(c, "FeedbackEnabled")) {
            return
        }
        val value = intent.getIntExtra(EXTRA_VALUE, -1).takeIf { it >= 0 } ?: return
        if (!window.consume()) {
            // Stale or no window open — log a 'miss' and exit.
            CoroutineScope(Dispatchers.IO).launch {
                history.record(
                    trigger = SyncTrigger.WATCH_BUTTON_FEEDBACK,
                    kinds = setOf(SyncKind.TIMER),
                    success = false,
                    errorMessage = "no open press window",
                )
            }
            return
        }
        CoroutineScope(Dispatchers.IO).launch { applier.apply(value) }
    }

    companion object {
        const val ACTION = "com.beamburst.casswatch.FEEDBACK"
        const val EXTRA_VALUE = "value"
    }
}
```

Manifest entry:

```xml
<receiver
    android:name=".FeedbackReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.beamburst.casswatch.FEEDBACK" />
    </intent-filter>
</receiver>
```

### 2.2 `FeedbackWindow`

```kotlin
// app/.../FeedbackWindow.kt   (new)

@Singleton
class FeedbackWindow @Inject constructor() {
    private val mutex = Mutex()
    private var deadline: Long = 0L
    private var consumed = false

    suspend fun open() = mutex.withLock {
        deadline = System.currentTimeMillis() + 5_000
        consumed = false
    }

    /** Returns true if a window is open and we successfully claim it. Also closes the window. */
    fun consume(): Boolean = synchronized(mutex) {
        val now = System.currentTimeMillis()
        if (consumed || now > deadline) return false
        consumed = true
        deadline = 0L
        return true
    }
}
```

`WatchButtonDispatcher.handleShort()` and `handleLong()` (Initiative #4) gain a `feedbackWindow.open()` call right after `sendBroadcast(...)`. The window is opened **regardless of whether the master switch is on** — the receiver checks the switch. This keeps the dispatcher unaware of feedback-enable state.

### 2.3 `FeedbackApplier`

```kotlin
// app/.../FeedbackApplier.kt   (new)

@Singleton
class FeedbackApplier @Inject constructor(
    private val api: GShockRepository,
    private val selector: CurrentWatchSelector,
    private val history: SyncHistoryLogger,
) {
    suspend fun apply(seconds: Int) {
        val addr = selector.currentAddress() ?: run {
            history.record(
                trigger = SyncTrigger.WATCH_BUTTON_FEEDBACK,
                kinds = setOf(SyncKind.TIMER),
                success = false,
                errorMessage = "no watch selected",
            )
            return
        }
        if (!WatchInfo.hasTimer) {
            history.record(
                trigger = SyncTrigger.WATCH_BUTTON_FEEDBACK,
                kinds = setOf(SyncKind.TIMER),
                success = false,
                errorMessage = "watch lacks timer capability",
            )
            return
        }
        val clamped = seconds.coerceIn(0, MAX_TIMER_SECONDS)
        val result = runCatching { api.setTimer(clamped) }
        history.record(
            trigger = SyncTrigger.WATCH_BUTTON_FEEDBACK,
            kinds = setOf(SyncKind.TIMER),
            success = result.isSuccess,
            errorMessage = result.exceptionOrNull()?.message,
            details = SyncDetails(timerSeconds = clamped),
        )
    }

    private companion object {
        // Confirm during Phase 0 R-2; placeholder until then.
        const val MAX_TIMER_SECONDS = 86_399
    }
}
```

### 2.4 New `SyncTrigger` and `SyncKind` values

Initiative #5 defined `SyncTrigger` and `SyncKind`. Add:

- `SyncTrigger.WATCH_BUTTON_FEEDBACK`
- `SyncKind.TIMER`

`SyncDetails` gets a new optional field:

```kotlin
data class SyncDetails(
    val alarmHashes: Set<String>?,
    val eventCount: Int?,
    val hourlySignalTarget: Boolean?,
    val timeMs: Long?,
    val timerSeconds: Int?,    // NEW — from #7
)
```

Backwards-compatible with #5's JSON encoding: missing fields read as null.

The Sync-history compact log line (#5 §5.2) gains a new tag:

```
2026-05-02 18:42:00  FEEDBACK  timer  ✓
2026-05-02 18:42:00  FEEDBACK  timer  ✗  no open press window
```

`FEEDBACK` slots in alongside `AUTO`, `RECON`, `MANUAL` in the trigger column. Pad to 8 chars instead of 6 for the wider word.

---

## 3. Wiring at existing call-sites

### 3.1 `WatchButtonDispatcher.kt` (#4)

Two-line change in each of `handleShort()` and `handleLong()`:

```kotlin
// before
private fun handleShort() {
    val cards = cardRepository.shortPress()
    if (cards.broadcast.enabled) sendBroadcast(BroadcastSpec.Short, cards.broadcast.extras)
    if (cards.flashlight.enabled) FlashlightHelper.toggle(context)
}

// after
private fun handleShort() {
    val cards = cardRepository.shortPress()
    if (cards.broadcast.enabled) {
        sendBroadcast(BroadcastSpec.Short, cards.broadcast.extras)
        feedbackWindow.open()                             // NEW
    }
    if (cards.flashlight.enabled) FlashlightHelper.toggle(context)
}
```

The window only opens if a broadcast was actually emitted (no point opening it when the press card is OFF). DI: inject `FeedbackWindow` into the dispatcher.

### 3.2 `Settings` UI (#4)

Append a fifth `PressCard` after the four from #4, with title "Feedback round-trip" and subtitle text describing the loop. The card has no editor dialog; tap-to-toggle behaviour same as the rest. Persistence: `LocalDataStorage.putBoolean(context, "FeedbackEnabled", …)`.

### 3.3 `SyncHistoryScreen.kt` (#5)

The expanded-row details pane gets a new branch:

```kotlin
when (kind) {
    SyncKind.TIMER -> Text("timer = ${formatHms(entry.details.timerSeconds ?: 0)}")
    …
}
```

`formatHms(seconds)` renders `01:30:00` etc. Used by the inline expansion in #5 §5.3.

---

## 4. Critical files

**New:**
- `app/.../FeedbackReceiver.kt` — broadcast receiver (manifest-registered, exported).
- `app/.../FeedbackApplier.kt` — calls `api.setTimer(...)` and logs.
- `app/.../FeedbackWindow.kt` — 5-second window primitive.
- `app/.../di/FeedbackModule.kt` — Hilt provides for the singletons.
- `docs/superpowers/research/2026-05-XX-feedback-channel-experiments.md` — Phase 0 outcomes.

**Modify:**
- `app/src/main/AndroidManifest.xml` — add the `<receiver>` element for `FeedbackReceiver`.
- `app/.../WatchButtonDispatcher.kt` (#4) — open the window after each press broadcast.
- `app/.../data/local/SyncEntry.kt` (#5) — extend `SyncTrigger` and `SyncKind`; add `timerSeconds` to `SyncDetails`.
- `app/.../data/local/SyncHistoryStore.kt` (#5) — extend the JSON encoder with `timerSeconds` (`tS` field).
- `app/.../ui/history/SyncHistoryScreen.kt` (#5) — render the new tag (`FEEDBACK`) and the new expanded-row detail line for `TIMER`.
- `app/.../ui/settings/SettingsScreen.kt` (#4) — append the fifth `PressCard`.
- `res/values/strings.xml` + 10 locales — new strings: `feedback_card_title` ("Feedback round-trip"), `feedback_card_subtitle`, `feedback_history_no_window` ("no open press window"), `feedback_history_no_watch` ("no watch selected"), `feedback_history_no_timer_capability` ("watch lacks timer capability").

**Tests (new):**
- `app/src/test/java/.../FeedbackWindowTest.kt` — open/consume/expire flow; concurrent presses; consume returns false when window closed.
- `app/src/test/java/.../FeedbackReceiverTest.kt` — master switch OFF → no-op; switch ON + open window → applier called; stale → applier not called; missing extra → no-op.
- `app/src/test/java/.../FeedbackApplierTest.kt` — clamping; `WatchInfo.hasTimer == false` → history failure; success path logs entry.

---

## 5. Reuse from existing code

- `WatchButtonDispatcher` (#4) — minimal extension; no restructuring.
- `LocalDataStorage.getBoolean / putBoolean` — for the `FeedbackEnabled` master switch.
- `CurrentWatchSelector` (#5/#6) — `currentAddress()` for the per-watch context of `setTimer(...)`.
- `SyncHistoryLogger` (#5) — every round-trip (success or failure) logs an entry. Reuses the existing logger; no new persistence.
- `SyncHistoryStore.serialize` — extended to include `timerSeconds`. Schema-tolerant decoding (#5's JSON precedent) means older entries without the field decode cleanly.
- `WatchInfo.hasTimer` (or whichever capability flag exists in GShockAPI) — gate the apply step.
- `PressCard` shared composable (#4) — render the master-switch card.
- `Spacing` token + M3 typography (#1) — every new composable uses these.

---

## 6. Verification

1. **Build/lint/tests green:** `./gradlew ktlint lint test assembleDebug` passes.
2. **Phase 0 outcomes documented:** `docs/superpowers/research/2026-05-XX-feedback-channel-experiments.md` exists, includes raw observations from R-1 through R-8, and either (a) confirms timer is the v1 channel, or (b) names the fallback. Spec body is updated if a fallback is chosen.
3. **Master switch default-off:** Fresh install. Settings → Feedback round-trip card is OFF. Press the watch's action button. External app receives `RUN_SHORT`. External app sends `FEEDBACK`. **Watch's timer is NOT updated.** Sync history records nothing for the FEEDBACK miss (stale = master OFF, dropped silently per §2.1 step 1).
4. **Happy path:**
   - Toggle the master switch ON.
   - Toggle a press card (Short or Long) ON.
   - Use a tiny Tasker / test app: receive `RUN_SHORT`, immediately send `FEEDBACK` with `value = 75`.
   - Press the watch's action button.
   - Within 1 second, the watch's timer face shows `00:01:15`.
   - Sync history shows `<now> FEEDBACK timer ✓`. Expand → `timer = 00:01:15`.
5. **Timeout:**
   - Same setup, but configure Tasker to wait 10 seconds before sending `FEEDBACK`.
   - Press the watch's button. Wait 6 seconds.
   - Tasker fires `FEEDBACK`. Receiver logs `<now> FEEDBACK timer ✗ no open press window`. Watch unchanged.
6. **Stale FEEDBACK with no preceding press:**
   - Manually broadcast `com.beamburst.casswatch.FEEDBACK value=10` from `adb shell am broadcast …` without pressing the watch first.
   - Receiver records a `no open press window` failure. No watch update.
7. **Concurrent presses:**
   - Press the action button twice within 1 second.
   - Tasker responds twice with `value=20` then `value=99`.
   - Watch ends up with `00:01:39` (the second value, last-press-wins).
8. **No watch connected:**
   - Disconnect the watch. Master switch ON. Trigger the loop manually (broadcast `RUN_SHORT` followed by `FEEDBACK`).
   - Receiver records `no watch selected`. Reconnect — watch's timer is still its previous value (no queued state).
9. **Master switch ON, but press card OFF:**
   - Press the watch's action button. The dispatcher's `feedbackWindow.open()` is **skipped** (because the broadcast wasn't emitted). Even if Tasker independently sends `FEEDBACK`, receiver logs `no open press window`. Confirms the window is gated on the press broadcast actually firing.
10. **No raw sp/dp leftovers:** `git grep -nE 'fontSize\s*=\s*[0-9]+\.sp|\.dp\)' app/src/main/java/com/beamburst/casswatch/{FeedbackReceiver.kt,FeedbackApplier.kt,FeedbackWindow.kt}` returns nothing.
11. **Receiver export:** `grep -A 5 'FeedbackReceiver' app/src/main/AndroidManifest.xml` shows `android:exported="true"` and the `<intent-filter>`.
12. **Watch capability gating:** On a watch where `WatchInfo.hasTimer` is false (verify by mocking the value during a unit test), the applier records a `watch lacks timer capability` failure. No `setTimer` call ever happens.

---

## 7. Cross-cutting / dependencies

- **Depends on Initiative #1**: package id and tokens. `com.beamburst.casswatch.FEEDBACK` action string.
- **Depends on Initiative #4**: `WatchButtonDispatcher`, `PressCard` shared composable, `LocalDataStorage` boolean keys.
- **Depends on Initiative #5**: `SyncHistoryLogger`, `SyncEntry` schema (extended here); compact log-line rendering (extended here).
- **Depends on Initiative #6**: `CurrentWatchSelector.currentAddress()` for routing the timer write to the correct watch.
- **Independent of Initiative #2** and **#3**: no alarm or hourly-signal interaction.

---

## 8. PR shape

Single PR titled `Initiative #7 — Watch-side feedback round-trip`. Suggested commit split:

1. `docs: Phase 0 research notes for the feedback display channel` — adds the research markdown; pure docs.
2. `refactor: extend SyncEntry schema with TIMER kind, WATCH_BUTTON_FEEDBACK trigger, timerSeconds detail` — schema-tolerant change to #5's data layer.
3. `feat: FeedbackWindow + FeedbackApplier + FeedbackReceiver` — receiver registered in manifest; apply path goes through the new singletons.
4. `feat: WatchButtonDispatcher opens feedback window on press broadcasts` — wires the dispatcher.
5. `feat: Feedback round-trip Settings card (master switch, default OFF)` — visible UI lands.
6. `feat: SyncHistoryScreen renders FEEDBACK trigger and timer detail rows` — display polish in the history screen.

Each commit builds and tests on its own. Commit 1 is doc-only; 2 is data-only; 3–6 ship the visible feature.

---

## 9. External-developer documentation

A short reference to drop into `docs/` (or as a section of the broadcast intents doc from #4). Final wording during implementation; the substance is:

```
Sending FEEDBACK to Cassiopeia Watch
====================================

When Cassiopeia Watch broadcasts a press event:

    com.beamburst.casswatch.RUN_SHORT
    com.beamburst.casswatch.RUN_LONG

your automation app has 5 seconds to send back:

    com.beamburst.casswatch.FEEDBACK
    extras: value: Int   (seconds; 0..86399)

The watch's timer is set to that many seconds.

Notes:
  - Master switch must be ON in Cassiopeia → Settings → Feedback round-trip.
  - Only the latest press's window is active; older windows close on new presses.
  - Out-of-range values are clamped to [0, 86399]. Negative values are ignored.
  - Concurrent FEEDBACK broadcasts within the same window: first-write-wins
    (the window is consumed and closes on first valid value).
  - If no watch is connected when the response arrives, the value is dropped
    (no queue).
```

Localised in English only for v1 (this is developer-facing documentation; no translator review needed).

---

## 10. Open questions / follow-ups

- **Phase 0 outcome may force the spec's hand.** If `setTimer` doesn't behave as documented above, this spec changes meaningfully (different display channel, different value range, different capability flag). Re-do the affected sections inline rather than ship a stale spec.
- **Discoverability.** The user has to read the broadcast-intent docs to know `FEEDBACK` exists. Consider a one-line caption inside the Feedback card pointing to the docs once they're written.
- **Retries.** If `api.setTimer(...)` fails (BLE flake), do we retry once? Probably not — the user would see a different value than expected. Log the failure and stop. Document explicitly.
- **More display channels.** Future expansion: a Settings dropdown to choose timer vs step-target. Out of scope for v1; revisit if users report timer-jumping-screens issues on specific watches.
- **Telemetry.** No external telemetry; the Sync history is the only artefact. Sufficient for self-service troubleshooting.
- **Replace 'value' extra name.** Some automation apps reserve `value` as a built-in extra. If R-* surfaces a conflict during testing, switch to `feedback_value` and update the docs. Not blocking.
