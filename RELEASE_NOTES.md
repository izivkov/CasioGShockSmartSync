# Release Notes - Casio G-Shock Smart Sync v42.5 — October 24, 2024

## ✨ Highlights

### 🌍 Local Mean & Solar Time Support
Introduced advanced time synchronization options for users interested in astronomical and local geographic time:
*   **New Time Options**: You can now select between **System Time**, **Local Mean Time (LMT)**, and **Local Solar Time (LST)** directly from the Time screen.
*   **LMT Calculation**: Automatically calculates time based on your exact longitude (1° = 4 minutes offset from UTC).
*   **Local Solar Time**: Uses the `adhan2` library to calculate the "True Solar Time" based on the sun's actual position (Equation of Time).
*   **Watch Synchronization**: When you press "Send to Watch" or when the app syncs automatically, it now respects your selected time type, allowing your G-Shock to display LMT or LST.
*   **Persistent Settings**: Your time type selection is securely stored in the watch's internal "scratchpad" memory and persists across sessions.

### ℹ️ Educational Info Dialog
*   **Interactive Info Button**: Added a new information button next to the time zone selector with clear, brief explanations of what each time type represents.
*   **Multilingual Support**: The info dialog and new time labels are fully translated into **11 languages**, including Arabic, Bulgarian, Catalan, Chinese, French, German, Hungarian, Japanese, Russian, and Spanish.

### 🛠 Reliability & Performance
*   **Connection Stability**: Added a brief 0.5s safety delay during the connection handshake to improve reliability with certain watch models.
*   **Real-Time Offset Clock**: The on-screen clock now dynamically updates to show the selected LMT or LST in real-time.

---

# Release Notes - Casio G-Shock Smart Sync v42.1 — June 29, 2026

## ✨ Highlights

### ⌚ Enhanced Watch Support
*   **GW-BX5600 and MTG-B1000 Integration**: Added robust support for the new GW-BX5600 and MTG-B1000 watch models.
*   **Protocol Improvements**: Implemented dynamic time-synchronization protocols and secondary dial synchronization tailored for the new watch models.
*   **API Update**: Updated the internal `gshockapi` library to version `1.4.77` to leverage new I/O modules, dynamic characteristic initialization, and enhanced protocol stability.

---

# Release Notes - Casio G-Shock Smart Sync v41.7 — May 18, 2026

## ✨ Highlights

### 📅 Calendar Sync Improvements
*   **Birthday Filter**: The Google Calendar synchronization now intelligently filters out calendars containing "Birthday" in their name, as well as individual events titled "Birthday" or "Birthday Vents". This prevents the watch's limited reminder slots from being cluttered with auto-generated contact birthdays.
*   **Read-Only Calendar Filter**: Improved calendar filtering to correctly exclude read-only Google calendars (e.g., subscribed holidays or view-only shared calendars) by verifying calendar access levels. This ensures only relevant, user-managed events are synced while preserving compatibility with non-Google calendar providers.
*   **API Compatibility**: Fixed a compilation error and improved the stability of calendar queries by utilizing `CalendarContract.Instances.CALENDAR_ID` for precise event filtering and association, ensuring smoother syncs across all supported Android versions.
*   **Enhanced Debugging**: Added detailed logging for calendar event fields (organizer, package, description) to aid in troubleshooting future syncing anomalies.

---

# Release Notes - Casio G-Shock Smart Sync v41.6 — May 18, 2026

## ✨ Highlights

### 🛡️ OEM Compatibility & Stability
*   **Samsung A52s & Android 14 Fix**: Addressed a critical crash on certain OEM builds (e.g., Samsung) where the system falsely advertises support for modern Companion Device Manager (CDM) APIs but lacks the underlying framework classes.
*   **Robust Error Handling**: Upgraded background presence observation error handling to catch framework-level errors (`NoClassDefFoundError`), preventing crashes on non-standard Android distributions.
*   **API Update**: Updated the internal `gshockapi` library to version `1.4.73`.

---

# Release Notes - Casio G-Shock Smart Sync v41.4 — May 12, 2026

## ✨ Highlights

### 📶 Improved Boot-Time Reliability
Fixed an issue where the background synchronization service might fail to start if the device booted up faster than the Bluetooth adapter could initialize.
*   **Bluetooth State Listener**: Added a dedicated `BluetoothStateReceiver` to automatically re-trigger device discovery the moment Bluetooth becomes available. This resolves race conditions caused by the system's `BOOT_COMPLETED` broadcast firing too early.

### 📚 Developer & Technical Updates
*   **Wireshark BLE Documentation**: Added a comprehensive guide to `README.md` explaining how developers and contributors can capture and analyze raw Bluetooth Low Energy (BLE) packets between the phone and watch using Android's HCI Snoop Log and Wireshark.
*   **API Update**: Updated the internal `gshockapi` library to version `1.4.71`, which adds support for setting and retrieving reminders for the **DW-B5600** watch model.

---

# Release Notes - Casio G-Shock Smart Sync v41.2 — May 8, 2026

## ✨ Highlights

### 🌙 Dark Mode Splash Screen
Fixed a long-standing issue where a bright white splash screen would appear on app startup even when the device was in dark mode.
*   **Modern Splash API**: Implemented the official `androidx.core:core-splashscreen` library for a smoother, system-integrated startup experience.
*   **Theme Awareness**: The splash screen now dynamically respects your system's dark/light theme settings, providing a comfortable transition in dark environments.
*   **Visual Consistency**: Matched the splash screen background with the app's internal color palette for both light and dark modes.

---

# Release Notes - Casio G-Shock Smart Sync v41.0 — May 6, 2026

## ✨ Highlights

### 🕋 Advanced Regional Prayer Alarms
Significant overhaul of the prayer alarm system to support complex regional requirements:
*   **Jammu & Kashmir Support**: Added a specialized geofence for the J&K region, automatically applying the **Karachi calculation method** and **Hanafi Madhab** for precise local timings.
*   **Southeast Asia "Safety Buffer"**: For users in **Indonesia, Malaysia, and Brunei**, the app now includes a 2-minute safety buffer for all prayer times (and -2m for sunrise) to align with local religious standards.
*   **Intelligent Madhab Selection**: The system now automatically switches between **Shafi** and **Hanafi** schools of thought based on the user's detected location and regional defaults.
*   **Refined Regional Defaults**: Improved default calculation methods for various countries to ensure out-of-the-box accuracy.

### 🛠 Improvements & Stability
*   **Code Optimization**: Internal refactoring of the `PrayerAlarmsHelper` for better maintainability and performance.
*   **Cleanups**: General code cleanup across the `pairing` and `ui` modules to improve app responsiveness.

---

# Release Notes - Casio G-Shock Smart Sync v40.9 — April 25, 2026

## ✨ Highlights

### 🛡️ Custom ROM & Legacy Compatibility
*   **crDroid & LineageOS Support**: Fixed an issue where the app would fail to start background observation on certain custom ROMs. We've implemented a robust fallback mechanism that switches to MAC-based tracking if the system's Companion Device Manager (CDM) lacks modern request-based APIs.
*   **Improved Service Reliability**: Enhanced the connection monitor to gracefully handle "Method Not Found" errors on non-standard Android distributions.

### ⌚ Enhanced Watch Management
*   **Multi-Watch Support**: Significant improvements to how the app handles multiple associated G-Shock watches. The background scanner is now more efficient at tracking and connecting to whichever watch is nearby.
*   **Paired Device UI**: Introduced a new, scrollable list for paired devices on the connection screen, making it much easier for users with large collections to manage their watches.

### ⚙️ Pairing & Connectivity
*   **Modernized Pairing**: Updated the pairing flow for Android 11 through 14+ to ensure compatibility with the latest system security requirements.
*   **Presence Detection**: Refined the `onDeviceAppeared` logic to reduce connection latency when a watch comes into range.

---

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
