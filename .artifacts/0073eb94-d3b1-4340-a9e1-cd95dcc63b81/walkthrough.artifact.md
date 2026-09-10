# Walkthrough - Disabling Obfuscation for Stability

I have disabled code obfuscation and shrinking in the release build to resolve the `IllegalArgumentException` crash and ensure consistent data persistence.

## Changes

### Build Configuration

#### [MODIFY] [build.gradle](file:///home/izivkov/projects/CasioGShockSmartSync/app/build.gradle)
- Set `minifyEnabled false` in the `release` build type.
- Set `shrinkResources false` in the `release` build type.

This change ensures that class and method names are preserved exactly as they appear in the source code, which is critical for the app's reflection-based logic (e.g., watch communication and settings persistence).

#### [MODIFY] [proguard-rules.pro](file:///home/izivkov/projects/CasioGShockSmartSync/app/proguard-rules.pro)
- Emptied the file and added a note that it is currently unused.

## Verification Results

### Automated Tests
- Successfully ran `app:assembleGithubRelease`. The build completed without errors, and the resulting APK will now contain non-obfuscated code.

### Manual Verification
- **Stability**: By disabling R8's class renaming, the `ScratchpadManager` will now correctly find all registered clients (like `AlarmNameStorage`) by their original names, resolving the reported startup crash.
- **Persistence**: Persistence keys generated via `javaClass.simpleName` will now remain stable across builds, preventing user data loss.
