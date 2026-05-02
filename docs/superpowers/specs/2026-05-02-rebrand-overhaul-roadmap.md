# CasioSmartSync — Rebrand & UX Overhaul Roadmap

## Context

The user is forking the upstream `org.avmedia.gshockGoogleSync` Android app into a rebranded, redesigned product under the **`com.beamburst.casiosync`** namespace. The fork has accumulated a long list of UX, feature, and architectural changes that span almost every screen and several new subsystems (broadcast intents to external automation apps, sync history persistence, multi-watch management, watch-side feedback round-trip).

This is **not a single change** — it is roughly **seven independent initiatives**, each large enough to warrant its own spec → plan → implementation cycle. Trying to design all of them at once would produce a doc that is vague everywhere and precise nowhere. **This file is the umbrella roadmap only.** Each initiative below will be brainstormed individually (its own design doc + implementation plan) when we start it.

The user has confirmed:
- The decomposition approach is correct.
- The execution order below is correct.
- The new package id is `com.beamburst.casiosync`.
- "Auto-sync" means the watch's own 4×/day reconnect behavior (~midnight, ~6am, ~noon, ~6pm). No new background scheduler is required for the Hourly Signal time-window feature.
- The watch-name-on-disconnect bug rides along with Initiative #1.

## Suggested package & app rename

- New `applicationId` and `namespace`: **`com.beamburst.casiosync`**
- Current value (both): `org.avmedia.gshockGoogleSync` — at `app/build.gradle:10` and `app/build.gradle:14`.
- Recommend keeping the rename as a dedicated, atomic commit (separate from any feature work) inside Initiative #1. Touches the manifest, Hilt DI graph (no string refs to fix), and the `package` declaration in every `.kt` file. Update Fastlane metadata + GitHub release script (`release.sh`) in the same commit.
- App display name: **TBD** with the user — write current `CasioSmartSync` name into the spec for Initiative #1 unless the user picks a new one before then.

## The seven initiatives

### Initiative #1 — Visual refresh + watch-name bug fix
**Goal:** Make the app feel professional. Consistent paddings, a tightened type scale (some fonts are too large today, e.g. the 48sp watch-name label), aligned items across screens. Bundles the package rename and the connection-state bug fix.

**In scope:**
- Audit + standardize paddings/typography/alignment across `ui/time/`, `ui/alarms/`, `ui/settings/`, `ui/events/`, and the bottom-nav.
- **Bug fix:** `TimeViewModel.refreshState()` (`app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt:100-125`) only runs at init and on `SendTimeToWatch`. It never re-runs on `WatchInitializationCompleted` or `Disconnect`, so the displayed watch name is whichever value was set when the screen was first composed. Subscribe to those two events in `TimeViewModel.init` (using the existing `ProgressEvents.runEventActions` + `Utils.AppHashCode()` pattern — see `ActionRunner.kt:19` for the canonical example) and call `refreshState()` from each handler.
- **Package rename** to `com.beamburst.casiosync`. Update `applicationId`, `namespace`, all `package ...` declarations, manifest entries, `release.sh`, Fastlane configs.
- App name decision — confirm with user during Initiative #1 brainstorm.

**Critical files:** `app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt`, `WatchNameView.kt`, `app/src/main/res/values/strings.xml`, `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `release.sh`, `fastlane/`.

**Reuse:** existing `ProgressEvents.runEventActions(...)` pattern; `MaterialTheme.typography` from Compose Material3 (already wired).

### Initiative #2 — Alarms overhaul
**Goal:** Bring the Alarms screen to clock-app polish. Tap-to-edit modal with hour + label + days-of-week. Zero-padded hours (`09:15`). "Fire once" semantics for an enabled alarm with no days selected. Footer showing last sync state with active/inactive chips. When disconnected, replace "Send to watch" with an info card ("alarms saved on phone, will sync at next time-sync") + caveat that weekly alarms require auto-sync.

**In scope:**
- New tap-to-edit modal (Compose `ModalBottomSheet` or `AlertDialog`) replacing inline editing.
- Zero-pad hours in display (single point of change in the formatter).
- "Fire once" rule — when an enabled alarm has no days selected, after firing the watch and the app must both clear the enabled flag. Watch-side clearing happens at the next sync.
- Rename "Simple" → "Daily"; remove the "daily" tag from per-alarm weekly view (redundant inside Daily/Weekly mode toggle).
- Disconnected-state copy + footer with last-sync chips.

**Critical files:** `app/src/main/java/org/avmedia/gshockGoogleSync/ui/alarms/` (all files), `WeeklyAlarmScheduler.kt:57` (sync call-site for history hooks later), `AlarmSyncStorage.kt`, `AlarmSchedulePlanner.kt`.

**Cross-cutting note:** The "Fire once" rule and the footer's "last sync time + active/inactive chips" both want the sync-history data introduced in Initiative #4 — but they can ship before #4 using a transient in-memory `lastSyncTimestamp` that gets persisted later. **Don't block #2 on #4.**

### Initiative #3 — Hourly Signal time-window card
**Goal:** A single card on the Alarms screen titled "Hourly signal". Shows current window (default 7:00–18:00) under the title. Tap-anywhere-but-toggle opens a config dialog with start/end hours + an explanation that it requires automatic sync. Toggle on/off via the card switch.

**Behavior:** No new background scheduler needed. Hook into the existing `AUTO_TIME_ADJUSTMENT` `RunEnvironment` in `ActionViewModel.kt` (the watch's 4×/day auto-reconnect path). At each auto-sync the app evaluates: if current local hour is **>= window start AND < window end**, send "hourly signal ON"; else send "hourly signal OFF". Manual sync also applies the rule.

**Critical files:** new `ui/alarms/HourlySignalCard.kt`, new `HourlySignalSettings` DataStore entries in `LocalDataStorage`, hook in `ActionViewModel`/`AlarmViewModel` to apply state at sync time.

**Reuse:** `LocalDataStorage` DataStore plumbing; existing chime/hourly signal API call in GShockAPI (verify exact name during this initiative's brainstorm).

### Initiative #4 — Actions tab removal + Settings broadcast intents
**Goal:** Delete the entire Actions tab and 18 files in `ui/actions/`. Migrate the only feature still wanted ("Set time") into Settings as a one-tap action. Add **Short button press** and **Long button press** broadcast-intent cards in Settings (replacing "Find my phone" with the long-press version).

**In scope — deletions:**
- `app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/` (all 18 files).
- `ActionRunner` registration at `GShockApplication.kt:56`.
- `Screens.Actions.route` from the bottom-nav.
- Permissions removable from `AndroidManifest.xml` (lines noted in exploration): `CAMERA`, `CALL_PHONE`, `ACCESS_COARSE_LOCATION`, `MODIFY_AUDIO_SETTINGS`. **Keep `READ_CALENDAR`** (still used by the Events tab).
- Drop dependencies if and only if nothing else imports them: `Adhan2` (prayer times), CameraX (only used by Actions photo). Verify before deleting from `build.gradle`.

**In scope — additions to Settings:**
- "Short button press" card. Subtitle = the broadcast action string `com.beamburst.casiosync.RUN_SHORT`. Right-side toggle. Tapping the body opens a modal with: copy-to-clipboard button for the action string, an editable extras list (key/value pairs), and a short integration guide for Automate / MacroDroid / Tasker.
- "Long button press" card. Same pattern, action `com.beamburst.casiosync.RUN_LONG`. Replaces "Find my phone" semantically.
- "Set time" → migrate the one-line action from `ActionViewModel.kt:302-346` into a Settings row. Already callable from `TimeViewModel.kt:87` so the underlying call is `repository.setTime(timeMs)`; the Settings row just exposes a button.
- "Notify me" — restyle to match the rest of the settings rows (font + switch).

**Critical files:** new `ui/settings/BroadcastIntentCard.kt`, new `ui/settings/BroadcastIntentDialog.kt`, modifications to `SettingsScreen.kt`, `BasicSettings.kt`, `SettingsViewModel.kt`. Wiring of outbound broadcast at the `ButtonPressedInfoReceived` event handler currently in `ActionRunner.kt:19` — that handler must be repurposed (NOT deleted) to emit the broadcast.

**Reuse:** `api.isActionButtonPressed()` / `api.isFindPhoneButtonPressed()` already distinguish short vs long press today.

### Initiative #5 — Sync history (last 25)
**Goal:** Persistent ring-buffer of the last 25 sync events. Each entry stores: timestamp, trigger (auto vs manual), what was synced (time / alarms / events / hourly-signal), and a compact details payload (e.g. for alarms: a sorted list of `HH:MM±` strings). Surface as a simple text list in a new "Sync history" screen reachable from Settings.

**Sync call-sites that must log a history entry** (from exploration):
- `TimeViewModel.kt:87` — manual time sync.
- `WeeklyAlarmScheduler.kt:57` — auto alarm sync on watch init.
- `AlarmViewModel.kt` — manual alarm send.
- `EventViewModel.kt` — manual events send.
- New "Set time from Settings" row (Initiative #4).
- Hourly-signal application at AUTO_TIME_ADJUSTMENT (Initiative #3).

**Storage:** Reuse the DataStore pattern already in `LocalDataStorage.kt` — add a JSON-encoded ring-buffer key. No need to introduce Room. The existing `toJsonObject` / `prettyPrintJson` helpers already in `LocalDataStorage.kt` are the right precedent.

**Critical files:** new `data/local/SyncHistoryStore.kt`, new `ui/history/SyncHistoryScreen.kt`, hooks at every call-site above. Settings entry point.

### Initiative #6 — Multi-watch management on Home
**Goal:** Replace the single watch-name display with a list of paired watches and an "Add watch" button. Each row: red/green dot for connection, watch name, ⋮ menu (Connect / Disconnect / Forget). Tap a row to "select" → loads that watch's cached settings, history, alarms.

**In scope:**
- UI rework on the Home/Time screen — list-driven instead of single-name-driven.
- Per-watch cached state: alarms, settings, last sync history. Stored keyed by device address (the LocalDataStorage `getDeviceAddresses()` infrastructure already exists at `LocalDataStorage.kt:125-151` and is multi-device-ready).
- Selection state: which paired watch is "currently shown". Persists across app restarts.
- Connect / Disconnect actions wired to `repository.waitForConnection(address)` / a disconnect API call.

**Critical files:** `ui/time/TimeScreen.kt`, `WatchInfoView.kt`, `WatchNameView.kt` (likely deleted/superseded), new `ui/devices/PairedWatchList.kt`, new `data/local/PerWatchCache.kt`. Touches `DeviceAssociationManager`, `CompanionDevicePresenceMonitor`.

**Risk:** This is the largest data-model shift. Caching per-watch state means every other ViewModel that reads "the currently selected watch" needs a single source of truth. Worth a dedicated brainstorm session.

### Initiative #7 — Watch-side feedback round-trip
**Goal:** External app responds to the broadcast intent (Initiative #4) with a numeric extra; the app then writes that number to the watch in a way the user can see. The user's watch (Casio ABL-100WE) does **not** support notifications, so we need a creative display channel.

**Display options to evaluate during this initiative's brainstorm** (in order of expected feasibility — verify each against the GShockAPI surface):
1. **Set the timer to N seconds/minutes** — gives 4 digits of display. User reads timer value.
2. **Set the step-target** — if the API allows arbitrary numeric targets (not just round thousands), this is permanent until next sync.
3. **Set the current step count** — same idea but using the `now` value rather than the target.
4. **Use the step "progress bar"** — set target = current_steps + N for a relative reading.

This initiative requires upfront API research/RE before design. The user has offered to do device-side testing.

**Built last** because it depends on Initiatives #4 (broadcast infra) and #5 (history of round-trips for debugging).

## Recommended order (confirmed with user)

1. **Visual refresh + watch-name bug + package rename** (foundation; small bug fix bundled).
2. **Alarms overhaul** — UX wins; well-bounded.
3. **Hourly Signal card** — small follow-up to Alarms.
4. **Actions removal + Settings broadcast intents** — large delete + new Settings feature.
5. **Sync history** — needed by #6 and #7 for proper UX.
6. **Multi-watch management** — biggest data-model shift, build on stable foundations.
7. **Watch feedback round-trip** — depends on #4 and #5; needs API research first.

## Process for each initiative

For each initiative we will:
1. Open a brainstorming session: ask clarifying questions, propose 2–3 approaches, agree on a design.
2. Write the spec to `docs/superpowers/specs/YYYY-MM-DD-<initiative>-design.md` and commit.
3. Use `superpowers:writing-plans` to produce an executable implementation plan.
4. Implement using `superpowers:executing-plans` or `superpowers:test-driven-development`.
5. Verify per the per-initiative verification section, ship, move to the next.

## Verification (this roadmap doc)

This file itself does not change application code. Its "verification" is a user review:

- Does the decomposition into seven initiatives match the user's mental model?
- Is the recommended order acceptable?
- Are the cross-cutting notes (especially #2 needing the not-yet-built #5, and #3 not requiring a new scheduler) correctly captured?
- Is the package name `com.beamburst.casiosync` confirmed?

If yes, the next step is brainstorming Initiative #1 — Visual refresh + watch-name bug + package rename — as its own spec.

## Open questions deferred to per-initiative brainstorms

- App display name (Initiative #1).
- Exact Compose typography scale (Initiative #1).
- Exact GShockAPI call for hourly signal toggle (Initiative #3).
- Whether the broadcast extras editor stores per-card or globally (Initiative #4).
- Whether the "Fire once" rule clears the alarm immediately on the phone or waits for sync confirmation (Initiative #2).
- API surface for setting timer/step-target/step-count from the app (Initiative #7 research task).
