# Release Notes - Casio G-Shock Smart Sync v25.4

We are excited to announce version 25.4! This release focuses on perfecting the "Phone Finder" experience with improved lift-detection sensitivity and device-aware ringing logic.

## Key Features & Improvements

### ⌚ Companion Device Features
*   **Modernized Pairing**: Enhanced integration with Android's Companion Device Manager for a more seamless pairing experience across Android 11 through Android 14+.
*   **Multi-Watch Support**: Improved handling for users with multiple G-Shock watches. The app now better manages multiple associations and presence detection.
*   **Presence Detection**: Refined `onDeviceAppeared` and `onDeviceDisappeared` logic to ensure more reliable automatic connections.
*   **Phone Finder Overhaul**: Added support for the specialized pick-up gesture sensor (hidden API) for more reliable lifting detection.
*   **Improved Sensitivity**: Refined accelerometer fallback logic with vertical-motion tracking and increased sensitivity.
*   **Connection Awareness**: Phone Finder now automatically stops ringing if the watch disconnects during the action.

### 🎨 User Interface Enhancements
*   **New Paired Device List**: A new, scrollable list is now available on the connection screen, making it easier to manage and switch between your paired watches.
*   **Connection Status**: Improved visual feedback with a persistent connection spinner during the synchronization process.
*   **Smart Indicators**: A new indicator points to your last-connected watch for quick reference.
*   **Simplified Management**: Low-key UI for disassociating or deleting devices from your list with minimal clicks.

### 🛠 Technical Updates & Bug Fixes
*   **Android 14 Compatibility**: Comprehensive fixes for Android 14, including permission handling and service binding improvements.
*   **API Updates**: Migrated to modern `AssociationInfo` callbacks for API 33+ while maintaining backward compatibility.
*   **Stability**: Fixed a legacy `IllegalStateException` during application startup.
*   **Performance**: Resolved an issue where certain API calls (`getWatchName`) could hang during initialization.

## Requirements
*   **Minimum OS**: Android 8.0 (API 26)
*   **Target OS**: Android 16 (API 36)

Thank you for using Casio G-Shock Smart Sync!
