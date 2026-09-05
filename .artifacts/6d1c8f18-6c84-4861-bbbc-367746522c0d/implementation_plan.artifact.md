# Implementation Plan - Rich Step Counter UI

Update the `StepCounterView` and `TimeViewModel` to incorporate high-precision activity data (intensity buckets and committed distances) from the latest API version.

## Proposed Changes

### [Features]

#### [MODIFY] [TimeViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt)
- **State Integration**: No changes needed to `TimeState` as it already contains the full `StepCounterData`.
- **Metric Logic Refinement**:
    - Update `calculateMetrics` to use `stepData.distanceMeters` if available, instead of always estimating from steps.
    - If `distanceMeters` is available, convert to km. If not, use the `steps * 0.76f` estimate.

### [UI Components]

#### [MODIFY] [StepCounterView.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/time/StepCounterView.kt)
- **Hourly Data Source**:
    - Update `StepDataOption.HOURLY` view to use `state.stepCounterData.hourlyByHour` (the 24-slot day view) or `hourlyIntervals` for fine-grained 10-minute records.
    - [DECISION] Since the user mentioned "hourly data seems to be 10-minute or 5-minute period", I will use `hourlyIntervals` to display the high-resolution activity records.
- **Intensity Visualization**:
    - For each hourly/10-minute record, display the 5-bucket intensity in a human-readable form.
    - Visualization: A small horizontal stacked bar or a text string (e.g., "Int: 5-20-10-0-0") below the step count for each period.
- **Distance Amalgamation**:
    - Display the committed distance for each activity period if available in `ActivityPeriod.distanceMeters`.
- **General Styling**:
    - Ensure all metrics (Steps, Distance, Calories) are clearly visible and logically grouped.

## Verification Plan

### Manual Verification
1. **Dynamic Metrics**: Connect an ABL-100 watch and verify that the distance displayed matches the `distanceMeters` reported by the watch (or falls back to the improved estimate).
2. **Historical Detail**: Switch to the **Hourly** view and verify that the chart shows high-resolution records (10-minute slots) with associated intensity data.
3. **Intensity Readability**: Verify that the "human readable" intensity buckets are informative and correctly aligned with each activity period.
4. **Consistency**: Verify that "Clear History" still works and wipes the new high-precision metrics.
