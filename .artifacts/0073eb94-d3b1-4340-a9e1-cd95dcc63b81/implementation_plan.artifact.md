# Implementation Plan - Enable Code Obfuscation and Shrinking

Configure the project to build successfully with R8 minification, obfuscation, and resource shrinking enabled. This involves setting up robust ProGuard rules to protect code that relies on reflection and stack trace analysis.

## User Review Required

> [!IMPORTANT]
> **Reflection & Stack Trace Fragility**: The application uses several patterns that are vulnerable to obfuscation:
> 1.  **`javaClass.simpleName`**: Used in `ScratchpadManager` to identify bit-packing order and in `ActionViewModel` for persistence keys.
> 2.  **`Thread.currentThread().stackTrace`**: Used in `Utils.AppHashCode()` to generate unique event subscription IDs.
> 3.  **`javaClass.canonicalName`**: Used in `SettingsViewModel` for event subscriptions.
> 4.  **`GShockAPI` Library**: Internal logic for packet routing and capability detection.
> 5.  **`Gson`**: Serialization of settings and models.

> [!WARNING]
> I will add explicit `-keep` rules to protect these areas. Without these rules, the app would crash or lose user settings in a release build.

## Proposed Changes

### [Build Configuration]

#### [MODIFY] [build.gradle](file:///home/izivkov/projects/CasioGShockSmartSync/app/build.gradle)
- Set `minifyEnabled true` and `shrinkResources true` in the `release` build type.

#### [MODIFY] [proguard-rules.pro](file:///home/izivkov/projects/CasioGShockSmartSync/app/proguard-rules.pro)
- Add a comprehensive set of rules:
    - Preserve `ScratchpadClient` and `Action` implementations to keep their original names.
    - Preserve `ViewModel` names used for event bus IDs.
    - Preserve method names of critical classes calling `Utils.AppHashCode()`.
    - Protect the `GShockAPI` library.
    - Protect `Gson` and data models.

### [Component Specific Rules]

#### Scratchpad & Actions
- Keep all classes implementing `ScratchpadClient`.
- Keep all subclasses of `Action`.

#### Event Bus (`ProgressEvents`)
- Keep `ViewModel` class names.
- Keep method names for classes using `AppHashCode`.

#### Library Protection
- Keep `org.avmedia.gshockapi.**`.
- Standard rules for `Gson`.

## Verification Plan

### Automated Tests
- Run `app:assembleGithubRelease` (or a similar task) to verify that the build completes successfully with R8 enabled.

### Manual Verification
- **APK Inspection**: Verify that the generated APK is significantly smaller.
- **Functionality (Release Build)**: If a release build can be deployed, verify:
    - Watch connection (ScratchpadManager logic).
    - Persistence (Action settings).
    - UI updates (ProgressEvents logic).
- **Log Verification**: Ensure `simpleName` usage in logs still shows readable names where intended for debugging.
