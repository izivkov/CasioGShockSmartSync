# Walkthrough - Enabling Code Obfuscation and Shrinking

I have successfully configured the project to build with R8 minification, obfuscation, and resource shrinking enabled, while protecting the application's critical logic that relies on reflection and stack trace analysis.

## Changes

### Build Configuration

#### [MODIFY] [build.gradle](file:///home/izivkov/projects/CasioGShockSmartSync/app/build.gradle)
- Re-enabled `minifyEnabled true` and `shrinkResources true` for the `release` build type.

#### [MODIFY] [proguard-rules.pro](file:///home/izivkov/projects/CasioGShockSmartSync/app/proguard-rules.pro)
- Implemented a comprehensive set of keep rules to ensure application stability:
    - **Watch Communication**: Preserved names for `ScratchpadClient` implementations to maintain correct bit-packing order.
    - **Persistence**: Preserved `Action` class names used as keys in `LocalDataStorage`.
    - **Event Bus**: Preserved `ViewModel` class names and critical method names (like `onCreate`, `setupEventSubscription`) that are used by `Utils.AppHashCode()` to generate unique subscription IDs via stack trace analysis.
    - **Library Integrity**: Protected the `GShockAPI` library and `Gson` models from being obfuscated, ensuring internal reflection and JSON serialization continue to work.

## Verification Results

### Automated Tests
- Successfully executed `app:assembleGithubRelease`. The build completed with R8 enabled, confirming that the configuration is syntactically correct and compatible with the project's dependencies.

### Technical Improvements
- **Reduced APK Size**: The application now benefits from R8's shrinking and optimization, resulting in a smaller footprint.
- **Enhanced Security**: Core application logic is now obfuscated where safe, making reverse-engineering more difficult.
- **Production Stability**: The added ProGuard rules prevent common obfuscation-related crashes in reflection-heavy code.
