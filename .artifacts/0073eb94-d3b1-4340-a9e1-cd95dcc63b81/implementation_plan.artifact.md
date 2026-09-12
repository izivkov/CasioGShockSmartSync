# Implementation Plan - Fix Obfuscation-Related Action Button Failure

Investigate and fix the issue where obfuscation prevents actions from running when the watch's action button is pressed.

## User Review Required

> [!IMPORTANT]
> **Root Cause Identified**: The investigation points to two primary obfuscation-related failures:
> 1.  **Event Bus Collisions**: `Utils.AppHashCode()` relies on the calling function's name from the stack trace. In release builds, R8 mangles these names (e.g., several different setup methods might be renamed to `a`), causing multiple components to share the same event subscription ID. This results in the last component to initialize overwriting the previous ones, silently breaking their event listeners.
> 2.  **Persistence Key Collisions**: `ActionsViewModel` uses `javaClass.simpleName` to generate keys for saving action states. If multiple action classes are renamed to the same name (e.g., `b`), their enabled states and settings will collide in local storage, leading to incorrect behavior or "disabled" actions.

## Proposed Changes

### [Core Utilities]

#### [MODIFY] [Utils.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/utils/Utils.kt)
- **Deprecate `AppHashCode()`**: It is too fragile for obfuscated builds.
- Recommend using `subscribeToProgressEvents()` instead.

### [Event Bus Refactoring]

#### [MODIFY] [ActionRunner.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionRunner.kt)
- Use `subscribeToProgressEvents()` with unique base names for both the button actions and message-based actions. This ensures subscriptions are unique and never collide.

#### [MODIFY] [MainEventHandler.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/MainEventHandler.kt)
- Use `subscribeToProgressEvents()` for global event handling.

#### [MODIFY] (Other components)
- Update `CompanionDevicePresenceMonitor`, `DeviceManager`, and `NotificationMonitorService` to use the same unique-ID-safe subscription method.

### [Persistence Hardening]

#### [MODIFY] [ActionViewModel.kt](file:///home/izivkov/projects/CasioGShockSmartSync/app/src/main/java/org/avmedia/gshockGoogleSync/ui/actions/ActionViewModel.kt)
- Add a stable `val id: String` property to the `Action` base class.
- Initialize each action with a hardcoded, unique ID (e.g., "ToggleFlashlight", "FindPhone").
- Update `save()` and `load()` to use this `id` instead of `javaClass.simpleName`. This guarantees keys remain consistent and unique regardless of obfuscation.

### [Obfuscation Rules]

#### [MODIFY] [proguard-rules.pro](file:///home/izivkov/projects/CasioGShockSmartSync/app/proguard-rules.pro)
- Add rules to preserve the names of inner classes and enums within `ActionsViewModel` to ensure `when` expressions and type checks (`is SetTimeAction`) remain reliable.

## Verification Plan

### Automated Tests
- Run `app:assembleGithubRelease` to verify the build process.
- Run unit tests for `IntentParser` (already safe).

### Manual Verification
1.  **Action Button**: Connect a watch, press the lower-right button. Verify the assigned actions (e.g., Flashlight) trigger correctly on the `RunActionsScreen`.
2.  **Persistence**:
    - Set a custom phone number in a Dialer action.
    - Restart the app.
    - Verify the phone number is preserved, confirming the new stable `id` keys are working.
3.  **UI Updates**: Verify that snackbars and other UI events still work, confirming the new `ProgressEvents` subscription IDs are stable.
