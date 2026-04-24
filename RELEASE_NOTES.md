# Release Notes - Casio G-Shock Smart Sync v40.8 — April 24, 2026

## ✨ Highlights

### 🛡️ GrapheneOS & Privacy-Focused Android Support
Full compatibility and improved reliability for GrapheneOS. We've overhauled how the app handles background connections to ensure your watch stays synced even on the most restrictive privacy settings.

### 📶 Enhanced Background Connectivity
*   **Fallback BLE Scanning**: Introduced a robust `PendingIntent`-based BLE scanning mechanism. This acts as a reliable fallback if the standard Companion Device Manager fails to report device appearance.
*   **Event Deduplication**: Added a smart "event gate" to filter out duplicate connection events, preventing redundant sync cycles and improving battery efficiency.
*   **Service Reliability**: The connection service now automatically re-arms presence observation, ensuring the app remains responsive to your watch even after long periods of inactivity.

### ⚙️ Android 14+ Optimizations
*   **Foreground Service Improvements**: Updated the connection service to use the latest `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` standards required by Android 14 (Upside Down Cake).
*   **Modernized API Usage**: Refined device presence observation for Android 13+ (Tiramisu) using explicit request builders for better system integration.

## 🛠 Improvements & Bug Fixes

### ⌚ Watch Management
*   **Automatic Sync**: Watch associations are now synchronized immediately when added or removed via the UI. This ensures the background scanner always has the correct list of devices to watch for.
*   **Connection Stability**: Fixed several edge cases where the app could miss a connection event when multiple watches were associated.

### 📚 Developer & Technical Updates
*   **Refactored Scanner Logic**: The BLE scanning logic has been moved into the core API layer for better maintainability and consistency.
*   **API Lint Fixes**: Resolved numerous Android Lint warnings across the `api` module to ensure better stability and performance.
*   **Documentation**: Added comprehensive KDoc documentation for the `GShockAPI` library, making it easier for contributors and future integrations.

---

# Release Notes — April 2, 2026


## 🐛 Bug Fixes

### Prayer Alarm Names Misalignment (Critical)
Fixed a critical bug where prayer alarm names were incorrectly assigned to alarm slots on the watch. 

**Root cause:** The app selects the next upcoming prayers based on the current time of day, which means the first alarm may not be Fajr — it could be any prayer (e.g., Maghrib at 3 PM). However, the watch displays alarms sorted by time of day (hour:minute), while the names were stored by slot index in the order they were generated. This caused a mismatch between displayed times and prayer names.

**Fix:** Alarms are now sorted by time of day (`hour`, `minute`) before being assigned to watch slots and saved to `AlarmNameStorage`. This ensures the slot order matches the watch's display order, so names always correctly correspond to their prayer times.

**File changed:** `PrayerAlarmsHelper.kt`

### Connection Issues for Some Watches
Improved the reliability of the `CompanionDevicePresenceMonitor` which handles automatic reconnection when a watch comes in and out of Bluetooth range.

**Changes:**
- Added **null-safety** for `DeviceAppeared` and `DeviceDisappeared` event payloads — previously, a null or unexpected payload type would cause a crash.
- Added **error handling** (`try/catch`) around the connection attempt in `DeviceAppeared`, preventing unhandled exceptions from crashing the monitor.
- Added **Timber logging** throughout the monitor for easier debugging of connection lifecycle events (`Device appeared`, `Device not connected. Attempting to connect...`, `Device already connected`, `Device disappeared`).

**File changed:** `CompanionDevicePresenceMonitor.kt`

---

## ✨ Improvements

### Events Sent Confirmation
Added an `AppSnackbar` notification ("Events sent to watch") when calendar events are automatically sent to the watch via the Actions system (e.g., during auto time adjustment or button press). Previously, only the manual send from the Events screen showed feedback.

**File changed:** `ActionViewModel.kt`

### Localization
Added the new `events_sent_to_watch` string resource with translations for all 10 supported languages:

| Language | Translation |
|----------|------------|
| English | Events sent to watch |
| Arabic | تم إرسال الأحداث إلى الساعة |
| Bulgarian | Събитията са изпратени към часовника |
| Catalan | Esdeveniments enviats al rellotge |
| German | Ereignisse an die Uhr gesendet |
| Spanish | Eventos enviados al reloj |
| French | Événements envoyés à la montre |
| Hungarian | Események elküldve az órára |
| Japanese | イベントを時計に送信しました |
| Russian | События отправлены на часы |
| Chinese (Simplified) | 事件已发送到手表 |

---

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
