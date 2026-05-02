# Initiative #1 — Visual Refresh + Watch-Name Bug + Package Rename

> Sub-spec under the umbrella roadmap `docs/superpowers/specs/2026-05-02-rebrand-overhaul-roadmap.md`. After acceptance, the executable plan is produced via `superpowers:writing-plans`.

## Context

The fork is rebranding `org.avmedia.gshockGoogleSync` ("Casio G-Shock Smart Sync") into a polished product called **Cassiopeia Watch** under the package id **`com.beamburst.casswatch`**. Initiative #1 is the foundational visual pass: a per-screen redesign of layouts, paddings and typography; a small color tweak (deep-blue primary); the package/app rename as a dedicated atomic commit; and a one-line bug fix that keeps the watch-name label stale across reconnects. Everything here is deliberately bounded so that later initiatives (#2 alarms overhaul, #3 hourly signal, etc.) inherit a clean, tokenized foundation.

## Confirmed decisions (from brainstorm)

| Topic | Decision |
|-------|----------|
| App display name | **Cassiopeia Watch** (replaces `Casio G-Shock Smart Sync` in `strings.xml`) |
| `applicationId` / `namespace` | **`com.beamburst.casswatch`** |
| Typography scale | Keep `theme/Type.kt` as M3 default (already correct sp values) — call sites must use it instead of raw `fontSize = …sp` |
| AppText wrappers | Re-wire `AppText` / `AppTextLarge` / `AppTextVeryLarge` / `AppTextExtraLarge` / `AppTextLink` to M3 typography tokens. **Drop the `fontSize / currentFontScale` anti-accessibility division** in `AppText.kt:42` and `AppTextLink.kt:38-41`. |
| Color scheme | Keep current palette and dynamic-color-on-A12+; **change primary ("accent") to deep blue**. Secondary (teal) and tertiary (pink) stay. |
| Audit depth | Per-screen redesign — propose new layouts, not just retune values |
| Working style | Bundled spec with all 5 screens (this doc), reviewed once |
| Package rename | Ships as a **separate first commit** inside the same PR |

## Goals

1. Make the app feel professional: consistent rhythm, no oversized text, predictable card layout across screens.
2. Establish a shared **Spacing** token + a single typography path through `MaterialTheme.typography` so future initiatives stop ad-hoc-ing dp/sp.
3. Rebrand identity (name, package, accent) cleanly with the rename isolated to one commit.
4. Fix the stale watch-name label.

## Non-goals

- App icon redesign (out of scope; track as a follow-up if user wants).
- Redesigning the `ui/actions/` tab (Initiative #4 deletes it; just normalize tokens here so it doesn't look out of place during the interim).
- Color changes beyond the primary swap.
- Touching the alarm modal / modal bottom sheet (that's Initiative #2).
- Connection / pairing flow logic — the existing `WatchConnectionDialog.kt` keeps its behavior; only its paddings/typography get normalized.

---

## 1. Theme refresh

### 1.1 New deep-blue primary

`theme/Color.kt` — replace the Electric Violet primary tonal set. Keep secondary (teal) and tertiary (pink) untouched. Replace these constants:

```kotlin
// Primary — Deep Blue (was Electric Violet)
val PrimaryLight              = Color(0xFF1A47B0)   // was 0xFF6200EE
val OnPrimaryLight            = Color(0xFFFFFFFF)
val PrimaryContainerLight     = Color(0xFFDCE4FF)   // was 0xFFEADDFF
val OnPrimaryContainerLight   = Color(0xFF001A41)   // was 0xFF21005D

val PrimaryDark               = Color(0xFFB0C7FF)   // was 0xFFD0BCFF
val OnPrimaryDark             = Color(0xFF002B72)   // was 0xFF381E72
val PrimaryContainerDark      = Color(0xFF143F9C)   // was 0xFF4F378B
val OnPrimaryContainerDark    = Color(0xFFDCE4FF)   // was 0xFFEADDFF
```

Hex values come from M3's tonal-palette generator for hue ~243° (deep blue). Implementation can fine-tune ±2 lightness; the exact decision can shift inside the implementation PR if a render review prefers a slightly different shade.

> **Note on Material You.** `dynamicColor = true` is on by default in `GShockSmartSyncTheme`. On Android 12+ with dynamic color, the user's system wallpaper still drives the palette and the static deep-blue is bypassed. That is intentional. The static palette is the brand fallback for Android 8–11 and for users whose wallpaper produces an unappealing palette. No code change to the dynamic-color branch.

### 1.2 Theme symbol rename

- `GShockSmartSyncTheme` → **`CassiopeiaWatchTheme`** (theme/Theme.kt:82, ~30 call sites). Bundled with the package-rename commit (§5) so symbol renames are atomic.
- File `theme/Theme.kt` keeps name; the function inside renames.

### 1.3 New `theme/Spacing.kt` token

Single source of truth for paddings. 4-dp grid:

```kotlin
package com.beamburst.casswatch.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 24.dp
    val xxl = 32.dp
}
```

Used as `Spacing.lg` everywhere. No ad-hoc literals in screen code after the migration. Audit script for the implementation phase: `git grep -n '\.dp' app/src/main/java | grep -v theme/Spacing.kt | wc -l` should drop sharply.

### 1.4 Typography — keep `Type.kt` as-is

`theme/Type.kt` already declares the M3 default scale. The change is at *call sites*: stop hardcoding `fontSize = Xsp` and start passing `style = MaterialTheme.typography.X` (or letting the AppText wrappers do it — see §2).

---

## 2. AppText wrappers migration

The five `ui/common/AppText*.kt` files currently hardcode font sizes and (for `AppText` / `AppTextLink`) divide by `LocalDensity.current.fontScale` to neuter the user's system font-size accessibility setting. We're removing the division (it breaks accessibility) and re-pointing the wrappers at M3 tokens.

| Wrapper | Today | After |
|---------|-------|-------|
| `AppText` | `fontSize = 16.sp`, divides by fontScale | `style = MaterialTheme.typography.bodyLarge` (16sp) — **no division** |
| `AppTextLarge` | `fontSize = 20.sp` | `style = MaterialTheme.typography.titleLarge` (22sp) |
| `AppTextVeryLarge` | `fontSize = 24.sp` | `style = MaterialTheme.typography.headlineSmall` (24sp) |
| `AppTextExtraLarge` | `fontSize = 36.sp` | `style = MaterialTheme.typography.displaySmall` (36sp) |
| `AppTextLink` | `fontSize = 16.sp`, divides by fontScale, underlined | `style = MaterialTheme.typography.bodyLarge` + `textDecoration = Underline` |

The 2-sp shift on `AppTextLarge` (20 → 22) is fine; titleLarge is the canonical M3 token there.

Call sites that override the wrapper's `fontSize` parameter (e.g. `WatchNameView.kt:68 fontSize = 48.sp`) must be modified — see §3 per-screen.

---

## 3. Per-screen redesign

Screens redesigned in order of how user-facing they are.

### 3.1 Time tab (`ui/time/`)

Today the screen is a `ConstraintLayout` of four stacked `AppCard`s in a fixed sequence: `LocalTimeView`, `TimerView`, `WatchNameView`, `WatchInfoView`. The watch name uses 48sp with a `Regex.replace("(CASIO)", "$1\n")` hack to force a line break. `WatchInfoView` is a 50/50 split with `Home Time` on the left and `Battery + Temperature` on the right; battery is a rotated 20-dp progress bar.

**Problems:** the 48-sp watch name is by far the loudest element on screen and visually outweighs the actual time; the regex word-wrap is a code smell; `WatchInfoCard1` and `WatchInfoCard2` use different inner paddings (`vertical=8.dp` vs `0.dp`); the rotated battery icon is hard to read.

**Proposed layout (ASCII mockup):**

```
┌──────────────────────────────────────────────┐
│  Time                                        │  ← screenTitle (titleLarge)
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐  │
│  │  Local Time                            │  │  ← AppCard, padding lg
│  │  10:42:15                              │  │  ← displaySmall (36sp)
│  │  Europe/Warsaw            [Send]       │  │  ← bodyMedium + filled-tonal btn
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │  Timer                                 │  │
│  │  00:30:00                  [Send]      │  │  ← displaySmall (was 40sp picker)
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │  GW-B5600BC                            │  │  ← headlineSmall (24sp), no \n
│  │  ────────────────────────              │  │  ← thin Divider
│  │  Home   Tokyo 18:42        🔋 87%  21°C │  │  ← single row, label/value pairs
│  │  [ Manage watches ]                    │  │  ← OutlinedButton
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

**Specific changes:**

- `WatchNameView.kt:68` — `fontSize = 48.sp` removed; uses `AppTextVeryLarge` (24sp). Drop the `text.replace(Regex("(CASIO)"), "$1\n")` and the `wrapContentWidth(CenterHorizontally)`. With 24sp the name fits one line on every supported phone width.
- Merge `WatchNameView` + `WatchInfoView` into a single `WatchSummaryCard` composable (new file `ui/time/WatchSummaryCard.kt`). Old `WatchInfoView.kt` and the 50/50 ConstraintLayout split are deleted; `WatchInfoCard1`/`WatchInfoCard2` are absorbed.
- Battery: replace the rotated `BatteryView` AndroidView with a horizontal text + small Material icon (`Icons.Outlined.Battery6Bar` etc., picked by % bucket). Keeps screen real-estate; reads left-to-right.
- Temperature: still text, but rendered next to battery on the same row.
- Home Time: moves into the same WatchSummaryCard's row as a "Home: Tokyo 18:42" pair (only shown if `WatchInfo.hasHomeTime`).
- "Manage Watches" button: stays as `OutlinedButton`, full width inside the card, padding `Spacing.md`.
- `LocalTimeView` and `TimerView` row layouts: padding standardized to `Spacing.lg` outer, `Spacing.md` between label and clock, `Spacing.sm` between clock and `[Send]` button. Replace the `weight(2f)` / `weight(1f)` asymmetry with `Modifier.weight(1f)` on the left + `Modifier.wrapContentWidth()` on the button.
- `TimerPicker.kt` 40sp inputs → `MaterialTheme.typography.headlineMedium` (28sp). The colons that have hardcoded `padding(horizontal = 2.dp)` move to `Spacing.xxs`.

### 3.2 Alarms tab (`ui/alarms/`)

This screen already has a `Simple/Weekly` segmented toggle (recently added — see git log `518709c7`). Initiative #2 will overhaul the alarms UX (modal editor, fire-once, etc.). For Initiative #1 we *only* normalize visuals.

```
┌──────────────────────────────────────────────┐
│  Watch Alarms                                │
├──────────────────────────────────────────────┤
│   [ Daily ]  [ Weekly ]                      │  ← rename "Simple"→"Daily" stays in #2
│  ┌────────────────────────────────────────┐  │
│  │  06:30          Wake-up        [ ON ]  │  │
│  │  Mo Tu We Th Fr · ·                    │  │  ← only in Weekly mode
│  └────────────────────────────────────────┘  │
│  …                                            │
│  ┌────────────────────────────────────────┐  │
│  │  Hourly Chime                  [ ON ]  │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  [ Send to phone ]   [ Send to watch ]       │
└──────────────────────────────────────────────┘
```

**Specific changes (visual only):**

- `AlarmItem.kt:80` outer card padding `0.dp` → `Spacing.xs` (matches other AppCards).
- `AlarmItem.kt:86` row padding `4.dp` → `Spacing.md`.
- `AlarmItem.kt:101` time-clickable nested padding `4.dp` → removed (rely on outer row padding only).
- `AlarmItem.kt` time text: drop `AppTextExtraLarge` (36sp); use `AppTextVeryLarge` (24sp). 36sp dwarfs the alarm name today.
- `AlarmItem.kt:168` day-circle `size(32.dp)` → `40.dp` (M3 minimum tap target with margin). Border `1.dp` → keep.
- `AlarmItem.kt:160` DaySelector row padding → `Spacing.sm` h, `Spacing.xs` v.
- `AlarmsScreen.kt:102` mode-toggle padding → `Spacing.lg` h, `Spacing.sm` v (was 16/4 — fixes asymmetry).
- `AlarmChaimeSwitch.kt:38` column padding `0.dp` → `Spacing.md`. Switch text `22.sp` raw → `titleMedium` (16sp).
- `ScreenTitle.kt:23` `24.sp` raw → `MaterialTheme.typography.titleLarge` (22sp).

> Initiative #2's modal editor, fire-once rule, and footer chips are explicitly *not* in scope here. The Simple→Daily rename also waits for #2.

### 3.3 Events tab (`ui/events/`)

```
┌──────────────────────────────────────────────┐
│  Events                                      │
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐  │
│  │  Doctor appointment           [ ON ]   │  │  ← title: titleLarge, switch right
│  │  Tue 14:00 – 15:00 · weekly             │  │  ← supporting: bodyMedium, muted
│  └────────────────────────────────────────┘  │
│  …                                            │
│                                              │
│  [        Send events to watch        ]       │
└──────────────────────────────────────────────┘
```

**Specific changes:**

- `EventItem.kt:55` `fontSize = 24.sp` → `style = MaterialTheme.typography.titleLarge`.
- `EventItem.kt:41` row padding `4.dp` → `Spacing.md`. Inner column `start=6.dp, end=6.dp` → unify with row (drop the column padding).
- `EventItem.kt:75-82` ConstraintLayout for period/frequency → replace with a `Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm))` with `bodyMedium` + `colorScheme.onSurfaceVariant` for the muted look. Removes the start/end=0 padding zoo.
- `EventsScreen.kt:60` events column padding `0.dp` → `Spacing.lg` (matches Time tab card spacing).

### 3.4 Settings tab (`ui/settings/`)

Settings has 9 small composables (BasicSettings, TimeAdjustment, FineAdjustmentRow, Font, Light, Locale, OperationTone, PowerSavings, +screen container). They share an unspoken contract: card with 12dp horizontal padding, label + control row.

```
┌──────────────────────────────────────────────┐
│  Settings                                    │
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐  │
│  │  Notify me                    [ ON ]   │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │  Fine time adjustment                  │  │
│  │  +0 ms                          ⋮       │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │  Light                                 │  │
│  │  Auto                          [ ON ]  │  │
│  │  · 1.5 s                               │  │
│  │  · 3 s                                 │  │
│  └────────────────────────────────────────┘  │
│  …                                            │
└──────────────────────────────────────────────┘
```

**Specific changes:**

- All settings cards: `padding(horizontal = 12.dp, vertical = 4.dp)` → `Spacing.lg` h, `Spacing.sm` v. Single value, applied via a small reusable `SettingCard` composable in `ui/settings/SettingCard.kt` (eliminates ~9 copies of the same padding line).
- `TimeAdjustment.kt:120` and `FineAdjustmentRow.kt:36` `20.sp` raw → `titleMedium` (16sp).
- `Locale.kt:95, 143` radio button spacers `10.dp` → `Spacing.sm` (8.dp). The 2-dp difference is invisible and the token wins.
- `OperationTone.kt:55` `start = 12.dp` → `Spacing.lg`.
- `Font.kt`, `Light.kt`, `Locale.kt` `(h=12, v=4)` → `(h=Spacing.lg, v=Spacing.sm)`.
- "Notify me" row: restyled to match other cards (uses `SettingCard` like the rest). The roadmap flagged this; the styling fix lands here, the broadcast-intent rewiring is Initiative #4.

### 3.5 Bottom navigation (`BottomNavigationBarWithPermissions.kt` at root package)

Today: `NavigationBar` with `Modifier.padding(0.dp)`, `alwaysShowLabel = false` so labels appear on selection only. Five tabs.

**Specific changes:**

- Drop the `padding(0.dp)` modifier (M3's NavigationBar already has correct padding; the explicit zero is fighting the framework).
- `alwaysShowLabel = true` for clarity. The five labels (Time / Alarms / Events / Actions / Settings) all fit on a phone width.
- Selected indicator color: `colorScheme.primaryContainer` (gives the deep-blue tint a clear home).

---

## 4. Watch-name bug fix

`TimeViewModel.refreshState()` (lines 100–125) runs only on init and on `TimeAction.SendTimeToWatch` / `TimeAction.RefreshState`. So if the screen is composed before reconnect or after disconnect, `watchName` stays at whatever value `getWatchName()` returned at first composition (or `R.string.no_watch`).

**Fix:** in `TimeViewModel.init`, register a `ProgressEvents.runEventActions(...)` subscription for two events and call `refreshState()` from each handler:

- `WatchInitializationCompleted` — re-read name + battery + temp + home time + timer
- `Disconnect` — reset state to disconnected (just the cached `LastDeviceName`)

The pattern is exactly what `ActionRunner.kt:19` already uses with `Utils.AppHashCode()`. One subscription, two `EventAction` entries.

```kotlin
// in TimeViewModel.init
ProgressEvents.runEventActions(Utils.AppHashCode(), arrayOf(
    EventAction("WatchInitializationCompleted") { refreshState() },
    EventAction("Disconnect")                  { refreshState() }
))
```

`refreshState()` is private but called only inside the same class, so visibility is unchanged. No other VM logic is touched.

---

## 5. Package & app rename — first commit of the PR

This commit is **atomic and behavior-preserving**: no UI logic changes, only renames. Its diff should be reviewable in 5 minutes.

### 5.1 What changes (single commit)

| File | Change |
|------|--------|
| `app/build.gradle:10` | `namespace = 'org.avmedia.gshockGoogleSync'` → `'com.beamburst.casswatch'` |
| `app/build.gradle:14` | `applicationId = "org.avmedia.gshockGoogleSync"` → `"com.beamburst.casswatch"` |
| `app/src/main/AndroidManifest.xml` | Any `android:name` referencing fully-qualified `org.avmedia.gshockGoogleSync.*` → `com.beamburst.casswatch.*` (services, receivers, application class, providers if any) |
| `app/src/main/res/values/strings.xml:2` | `app_name` value → `Cassiopeia Watch`. Stays `translatable="false"`. |
| Every `.kt` under `app/src/main/java/org/avmedia/gshockGoogleSync/...` | `package org.avmedia.gshockGoogleSync...` → `package com.beamburst.casswatch...`; corresponding `import` statements updated |
| Source directory move | `app/src/main/java/org/avmedia/gshockGoogleSync/` → `app/src/main/java/com/beamburst/casswatch/` |
| Test directories | `app/src/test/java/org/avmedia/gshockGoogleSync/` → `…/com/beamburst/casswatch/`; same for `androidTest` if present |
| `release.sh` | Any reference to old artifact name / package id |
| `fastlane/metadata/android/en-US/title.txt` | New title `Cassiopeia Watch` |
| `fastlane/metadata/android/en-US/short_description.txt` | Drop "Casio G-Shock" from the lede; reword once |
| `fastlane/metadata/android/en-US/full_description.txt` | Same — replace branded mentions |
| Theme function rename | `GShockSmartSyncTheme` → `CassiopeiaWatchTheme` (theme/Theme.kt:82 + ~30 callers) |

### 5.2 What does **not** change

- Hilt DI graph: `@Module`/`@Binds` use class refs not strings. No additional fixes needed beyond import updates handled by the package rename.
- DataStore filename: `CASIO_GOOGLE_SYNC_STORAGE.preferences_pb`. Renaming this would orphan existing user prefs on upgrade. **Keep the existing filename.** Add a TODO comment in `LocalDataStorage.kt` noting the historical name is preserved for upgrade compatibility.
- `SCRATCHPAD_LAYOUT.md` and other docs that mention the old package name — leave unless the rename trivially affects them; treat doc updates as a separate housekeeping commit if needed.
- GShockAPI dependency: external library, package stays `org.avmedia.gshockapi.*`.

### 5.3 Mechanical execution

1. Use Android Studio's "Refactor → Rename Package" on the root package, then verify with `./gradlew assembleDebug`.
2. Update `app/build.gradle` (`namespace`, `applicationId`) by hand.
3. Update `strings.xml` `app_name`.
4. Update fastlane title/description files.
5. Update `release.sh`.
6. Rename `GShockSmartSyncTheme` symbol — IDE refactor.
7. Run `./gradlew ktlint lint test assembleDebug` — full green required.
8. Commit titled `chore: rename package to com.beamburst.casswatch and rebrand to Cassiopeia Watch`.

The visual-refresh / typography / bug-fix changes land in a **second commit** in the same PR.

---

## 6. Critical files to modify

**Theme:**
- `app/src/main/java/org/avmedia/gshockGoogleSync/theme/Color.kt` — primary tonal set.
- `app/src/main/java/org/avmedia/gshockGoogleSync/theme/Theme.kt:82` — symbol rename.
- `app/src/main/java/org/avmedia/gshockGoogleSync/theme/Spacing.kt` — **new** file.
- `app/src/main/java/org/avmedia/gshockGoogleSync/theme/Type.kt` — no change (keep as-is).

**AppText wrappers:**
- `ui/common/AppText.kt`, `AppTextLarge.kt`, `AppTextVeryLarge.kt`, `AppTextExtraLarge.kt`, `AppTextLink.kt` — re-route to M3 tokens; drop fontScale division.

**Time tab:**
- `ui/time/TimeScreen.kt` — restructure children to use new `WatchSummaryCard`.
- `ui/time/WatchNameView.kt` — delete or fold into `WatchSummaryCard`.
- `ui/time/WatchInfoView.kt` — delete (functionality moves to `WatchSummaryCard`).
- `ui/time/WatchSummaryCard.kt` — **new** file.
- `ui/time/Battery.kt` — replace rotated AndroidView with M3 icon + text.
- `ui/time/LocalTimeView.kt`, `TimerView.kt`, `TimerPicker.kt` — token migration.
- `ui/time/TimeViewModel.kt:100-125` — bug fix in `init` block.

**Alarms tab:**
- `ui/alarms/AlarmsScreen.kt`, `AlarmItem.kt`, `AlarmChaimeSwitch.kt` — token migration.
- `ui/common/ScreenTitle.kt` — typography migration (used by every screen).

**Events tab:**
- `ui/events/EventsScreen.kt`, `EventItem.kt` — token migration.

**Settings tab:**
- `ui/settings/SettingsScreen.kt`, `BasicSettings.kt`, `TimeAdjustment.kt`, `FineAdjustmentRow.kt`, `Font.kt`, `Light.kt`, `Locale.kt`, `OperationTone.kt`, `PowerSavings.kt` — token migration.
- `ui/settings/SettingCard.kt` — **new** reusable card wrapper.

**Bottom nav:**
- `BottomNavigationBarWithPermissions.kt` (root package, sibling of `GShockApplication.kt`) — drop `padding(0.dp)`, set `alwaysShowLabel = true`.

**Actions tab (light touch):**
- `ui/actions/ActionItem.kt`, `PhotoView.kt`, `PhoneView.kt`, etc. — minimal token migration to keep visually consistent until Initiative #4 deletes them.

**Build / branding:**
- `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `release.sh`, `fastlane/metadata/android/en-US/{title,short_description,full_description}.txt`.

---

## 7. Reuse from existing code

- `ProgressEvents.runEventActions(Utils.AppHashCode(), …)` — canonical event-subscription pattern. Used by `ActionRunner.kt:19`. Used again in `TimeViewModel.init` for the bug fix.
- `MaterialTheme.typography` — already wired through `Type.kt`. Just stop bypassing it.
- `MaterialTheme.colorScheme` — already wired. The deep-blue change is in `Color.kt` only; nothing else needs to know.
- `AppCard` — keep as the canonical card wrapper. Update its default padding to `Spacing.lg` once; cascades.
- `AppButton`, `AppSwitch` — unchanged. Just confirm they read from `colorScheme.primary` so the new deep-blue propagates automatically.
- `WatchInfo.hasHomeTime` capability flag — used to conditionally render the home-time row in the new `WatchSummaryCard`.

---

## 8. Verification

End-to-end checks for the implementation phase:

1. **Build/lint/tests green:** `./gradlew ktlint lint test assembleDebug` passes after both commits.
2. **Package rename clean:** `git grep -l 'org\.avmedia\.gshockGoogleSync' app/src/main` returns empty after commit 1 (the `LocalDataStorage` `CASIO_GOOGLE_SYNC_STORAGE` filename is the only allowed reference and lives in a string literal, not a package).
3. **Install + launch:** install debug APK on a phone or emulator; launcher icon label reads "Cassiopeia Watch"; app boots to PRE_CONNECTION.
4. **Watch-name bug fixed:**
   - Connect a paired watch → Time tab shows correct watch name.
   - Disconnect (via Settings → Disconnect, or move out of range) → Time tab updates to `LastDeviceName` (cached).
   - Reconnect → Time tab updates again to live name.
   - Repeat once. Name must reflect current connection state every time.
5. **Visual sweep — each tab on Pixel 6 emulator:**
   - Time: watch name no longer overflows or word-wraps mid-CASIO; battery + temp on one row; "Manage watches" full-width button.
   - Alarms: time label is 24sp not 36sp; day circles 40dp; chime row matches other rows.
   - Events: title 22sp; period/frequency on one row in `onSurfaceVariant` color.
   - Settings: every card has identical horizontal padding; Notify-me row visually matches the rest.
   - BottomNav: all five labels visible always; selected tab uses deep-blue indicator.
6. **Dark mode:** toggle system dark mode, verify each tab still passes the same visual sweep.
7. **Dynamic color:** on Android 12+, change wallpaper to a green/orange image, relaunch app — palette adapts (deep-blue is overridden, as designed).
8. **Accessibility:** set system font size to "Largest" in Settings → Display. Open the app. Text should now scale (proving the `fontScale` division was removed).
9. **No raw sp/dp leftovers in scope:** `git grep -nE 'fontSize\s*=\s*[0-9]+\.sp' app/src/main/java/com/beamburst/casswatch/{ui/time,ui/alarms,ui/events,ui/settings}` returns nothing or only intentional exceptions. (Path uses the new package after commit 1.)

---

## 9. Open questions / followups

- **App icon redesign**: out of this initiative. Recommend tracking as a separate ticket for after Initiative #2 (when the user has lived with the new colors a while).
- **Exact deep-blue hex**: `#1A47B0` light, `#B0C7FF` dark are first-pass values. Implementation can fine-tune ±2 lightness during the render review without coming back to this spec.
- **Translations of the new app name**: `app_name` is `translatable="false"` so all 10 locales pick up "Cassiopeia Watch" automatically. No per-locale work.

---

## 10. PR shape

Single PR titled `Initiative #1 — Visual refresh, package rename, watch-name bug fix`. Two commits:

1. `chore: rename package to com.beamburst.casswatch and rebrand to Cassiopeia Watch` — package + namespace + display name + theme symbol rename + fastlane metadata. Behavior-preserving.
2. `feat: visual refresh — spacing tokens, M3 typography, deep-blue accent, watch-name bug` — everything in §1–§4.

This is what the user explicitly requested ("the package rename ships as a separate first commit inside the PR — yes").
