# Implementation Plan - Disable Obfuscation & Shrinking

Disable R8 minification and resource shrinking to ensure application stability and prevent reflection-related failures in the release build.

## Proposed Changes

### [Build Configuration]

#### [MODIFY] [build.gradle](file:///home/izivkov/projects/CasioGShockSmartSync/app/build.gradle)
- Set `minifyEnabled false` in the `release` build type.
- Set `shrinkResources false` in the `release` build type.

#### [DELETE] [proguard-rules.pro](file:///home/izivkov/projects/CasioGShockSmartSync/app/proguard-rules.pro)
- Remove the ProGuard rules file as it is no longer required when minification is disabled.

## Verification Plan

### Automated Tests
- Run `app:assembleGithubRelease` to verify that the release APK/Bundle is generated successfully without shrinking.

### Manual Verification
- **Stability Check**: Verify that the release build no longer crashes at startup (resolving the `IllegalArgumentException` in `ScratchpadManager`).
- **Feature Check**: Confirm that settings and reminders work correctly, as their identifiers and keys will no longer be obfuscated.
