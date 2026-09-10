# Implementation Plan - Refined Alarm Voice Logic

Optimize the alarm-setting voice commands to handle duplicates correctly, sort alarms chronologically, and improve the "Clear All" behavior.

## User Review Required

> [!IMPORTANT]
> **Clear All Behavior**: In addition to disabling all alarms, I will reset their times to **12:00 AM** to provide a consistent "factory reset" state for the alarm slots.

## Proposed Changes

### [Voice Engine]

#### [MODIFY] [ActionViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt)

- **`SetAlarmAction`**:
    - Refactor `runSuspend` logic:
        1.  Fetch current alarms from the watch.
        2.  **Check for Duplicates**: If an alarm with the *same hour and minute* already exists, just ensure it is set to `enabled = true`.
        3.  **Intelligent Updates**: If no match exists, find the first disabled slot to update. If all slots are full, overwrite the first one (standard behavior).
        4.  **Chronological Sorting**: Before sending to the watch, sort the entire alarm list by time (`hour`, then `minute`).
    - Emit the `AlarmsUpdated` signal with the sorted list.

- **`ClearAllAlarmsAction`**:
    - Update logic to not only set `enabled = false` but also reset `hour = 0` and `minute = 0` for all 5 slots.
    - Emit the `AlarmsUpdated` signal.

## Verification Plan

### Manual Verification
1.  **Duplicate Handling**:
    - Set alarm for 7:30 AM manually.
    - Say *"Set alarm for 7:30 AM."*
    - Verify no duplicate entry is created; the existing one should just stay enabled.
2.  **Chronological Sorting**:
    - Set alarms for 9:00 AM and 6:00 AM.
    - Verify they appear as [6:00 AM, 9:00 AM] on the watch/app.
3.  **Clear All**:
    - Say *"Clear all alarms."*
    - Verify all alarms are disabled AND their times are reset to 12:00 AM.
4.  **UI Sync**: Verify the Alarms screen updates immediately after these commands.
