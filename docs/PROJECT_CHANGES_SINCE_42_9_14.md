# Changes Since 42.9.14 to 42.9.16

This document is intentionally scoped to the changes between the 42.9.14 baseline and the current 42.9.16 release, with emphasis on the Exercise Profile work and the rewritten distance/calorie logic.

## Summary

The main functional change in this window is the introduction of the Exercise Profile system for the step counter. This was not a minor UI tweak; it changed how step-goal, height, and weight values are stored, validated, and reused in the daily activity calculations.

The second major change is that distance and calories are now treated as derived values that must be recalculated from the latest valid profile and watch-reported data, instead of being blindly trusted or left stale.

## 1. Exercise Profile dialog added

In v42.9.16, the app added an Exercise Profile dialog inside `StepCounterView`.

This dialog allows the user to configure:

- Step Goal
- Height
- Weight

The profile is a single place for these values instead of scattering them across separate controls or hidden state.

### Why it matters for the web app

The TypeScript project should model this as one coherent data object, for example:

- `stepGoal`
- `height`
- `weight`
- `units` (metric or imperial)
- `locale`

This should be persisted as a single profile payload and written to the watch in one consistent flow.

## 2. Locale-aware units

The app now detects the device locale and presents height and weight in the appropriate unit system:

- Imperial: inches / pounds
- Metric: centimeters / kilograms

This is not only a UI decision; it affects validation and how values are converted before being sent to hardware.

### Web app implication

When mirroring this behavior:

- detect locale or user preference once
- normalize values into canonical internal units
- display the correct UI labels and ranges
- convert only at the edges of the input/output boundary

## 3. Validation added for profile data

The Exercise Profile flow now enforces reasonable ranges before writing the data to the watch.

Examples:

- Step Goal: 1,000–30,000
- Height: valid hardware-appropriate range
- Weight: valid hardware-appropriate range

This prevents invalid values from being sent to the watch and avoids corrupting the stored scratchpad data.

### Recommended TypeScript behavior

Use a guarded validation layer before sending profile updates:

- step goal must be in range
- height must be numeric and supported
- weight must be numeric and supported
- invalid values should be blocked with clear UI feedback

## 4. Profile values are persisted to watch and local scratchpad

The update path for Exercise Profile is now more than a local UI change. The app writes the updated values:

- back to the watch storage/scratchpad
- into the app’s local scratchpad state

This ensures the profile follows the device and remains consistent even when the app state changes or the user switches devices.

### Web app implication

The web app should treat this as a sync boundary, not just a form save. The flow is effectively:

1. User edits profile
2. App validates payload
3. App writes to watch
4. App updates local state
5. App recalculates dependent metrics

## 5. Distance and calories are now recalculated from the updated profile

This is the biggest behavior change in the release window.

The app now recalculates calories and distance from fresh profile data after updating the Exercise Profile. This means the profile is not just metadata; it directly affects the derived fitness metrics displayed to the user.

### Relevant change in behavior

- recalculated values update immediately after profile changes
- steps, calories, and distance stay consistent with the latest valid data
- stale values are not allowed to silently overwrite a new profile state

## 6. Distance handling is now more defensive

The app now rejects obviously implausible watch-reported distances and falls back to the normal stride-based estimate instead of showing bogus values.

This matters because the watch can sometimes report a distance that is clearly incorrect or inconsistent with the step data.

### Correct behavior model

- prefer direct watch distance when it is valid
- fall back to stride-based estimate when the reported value is implausible
- avoid displaying obviously bad distance values

### TypeScript web app equivalent

Use a validation gate such as:

- check minimum/maximum plausibility
- compare with expected step-to-distance ratio
- if invalid, use estimated distance from stride formula

## 7. Standard stride-based estimation is used for calorie/distance math

The step counter logic now uses a standard stride estimate as part of the fitness model. In practice, this means distance and calories are based on a more consistent model rather than raw assumptions that drift or become stale.

The Android app also uses a direct `distanceMeters` value from the watch when it is acceptable.

### Recommendation for TypeScript

Use a normalized formula along these lines:

- estimatedDistance = steps × strideLength
- calories = function of distance, weight, and activity assumptions
- if watch distance is valid, prefer it over the estimate
- if watch distance is invalid, fall back to stride-based estimate

## 8. What changed in practice for the user-facing app

The Exercise Profile work changed the app from a simple “store a value and show it” model into a stateful sync model where the profile drives the daily activity metrics.

The user now sees:

- a single profile editor
- locale-aware units
- validated values only
- consistent result updates after saving
- distance and calories that track the new profile rather than stale data

## 9. Migration guidance for the TypeScript project

If you are updating `gshock-smart-sync-webapp`, the key changes to mirror are:

1. Add an `ExerciseProfile` model with step goal, height, weight, locale, and units.
2. Validate profile input before any watch sync.
3. Persist it in both local app state and watch-backed storage.
4. Recalculate derived metrics after save.
5. Prefer valid watch distance; reject impossible values.
6. Fall back to stride estimation when data is invalid or stale.
7. Treat calories and distance as derived, not independent fields.

## 10. Bottom line

Between 42.9.14 and 42.9.16, the project pivoted to a profile-driven health model. The Exercise Profile dialog and its sync logic are the real center of the change, and the updated distance/calorie behavior is a direct consequence of making the profile authoritative.

For a web app, the most important compatibility target is this:

- Exercise Profile = authoritative source for step-goal and body metrics
- watch distance = high-priority raw input when valid
- fallback = stride-based distance estimate
- calories = derived from the latest valid profile and movement data
