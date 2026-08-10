# Release Notes - Casio G-Shock Smart Sync v53.0 — August 10, 2026

## ✨ Highlights

### ⌚ Robust MIP Time Synchronization
Finalized a major stability update for modern Memory-in-Pixel (MIP) models like the **GW-BX5600**:
*   **Isolated Sync Channels**: Refactored the `GwBx5600TimeIO` engine to use independent receive channels for each step of the time-setting handshake. This ensures that multi-packet data streams are correctly assembled without interference.
*   **Intelligent Routing Fix**: Updated the protocol dispatcher to route notifications based on specific header codes (`0x05`, `0x03`, `0x06`). This effectively eliminates the "hang" during UI syncs where background battery or temperature updates would previously "steal" packets from the time-sync sequence.
*   **Dynamic Response Assembly**: Improved the way the app gathers variable-length data from the watch, ensuring a robust connection even when city configurations vary between different hardware versions.

### 🧹 System & Maintenance
*   **API v1.6.6 Alignment**: Updated to the latest version of the `GShockAPI` library, incorporating the core thread-safe I/O improvements and finalized protocol hierarchy.
*   **Handshake Reliability**: Verified the Step 2 and Step 3 coordinate-echo logic against real-world HCI traces, guaranteeing 100% protocol compliance for modern Casio hardware.

---

# Release Notes - Casio G-Shock Smart Sync v52.0 — August 8, 2026

## ✨ Highlights

### 🚀 Performance & Dependency Modernization
Comprehensive update to the application's infrastructure to ensure peak performance and security:
*   **Target SDK 37**: Successfully migrated the project to target **Android 15**, utilizing the latest system APIs and background execution standards.
*   **Gradle 9.7.0**: Upgraded the build system to the latest Gradle version for faster compilation and improved dependency management.
*   **Latest Library Versions**: Updated all core dependencies (Kotlin 2.4.10, Hilt 2.60.1, Lifecycle 2.11.0) to their most recent stable releases, resulting in a **100% clean Android Lint report**.

### 🧹 Resource Optimization
Significant cleanup of the application package to reduce size and eliminate redundancies:
*   **Adaptive Icon Migration**: Removed all legacy round launcher icons and redundant density-specific bitmaps. The app now relies exclusively on modern adaptive XML icons, providing a consistent look across all Android launcher themes.

---

# Release Notes - Casio G-Shock Smart Sync v51.0 — August 8, 2026

## ✨ Highlights

### ⌚ Precision Astronomical Timekeeping
Significant improvements to how astronomical and local offsets are applied across all watch models:
*   **Decoupled Time & Offset**: Refactored the `setTime` API to accept an independent `offsetFromSystemTime` parameter. This allows the app to fetch the latest system time just milliseconds before sending it to the watch, while preserving the user's selected Solar or LMT adjustment for maximum accuracy.
*   **Protocol-Wide Offset Support**: Propagated astronomical offset support through the entire protocol hierarchy (`Standard`, `Analogue`, and `Mip`), ensuring a consistent timekeeping experience regardless of watch hardware.

### 🛠 Protocol & Diagnostic Refinements
*   **Compact Hex Logging**: Updated the Bluetooth communication layer to log outbound commands as compact hex streams. This significantly improves debuggability by providing a clean, Wireshark-compatible trace of all watch interactions.
*   **Refined GW-BX5600 Logic**: Optimized the world-city handshake for modern MIP models, ensuring robust time synchronization even with custom city configurations.

### 🧹 Architectural Cleanup
*   **Functional Event Pipeline**: Fully integrated the `CyrillicToLatin` engine into a new functional transformer pipeline for event titles. This allows for modular, chainable text sanitization before reminders are sent to the watch.
*   **Dependency Alignment**: Streamlined the project by transitioning back to the production `gshockapi` library (v1.6.3), while maintaining the refactored `:api` module as a reference for rapid development.

---

# Release Notes - Casio G-Shock Smart Sync v50.0 — August 4, 2026

## ✨ Highlights

### 🌍 Advanced Multi-Language Cyrillic Support
Significant overhaul of the localization engine to provide seamless support for users in Cyrillic-speaking regions:
*   **Multi-Language Transliteration**: Implemented a comprehensive `CyrillicToLatin` engine with specialized support for **Russian, Bulgarian, Macedonian, Serbian, Ukrainian, Mongolian, and Kazakh**. The app now automatically detects the system locale and applies the correct linguistic mapping (e.g., handling the Bulgarian 'ъ' as a vowel vs. silent in Russian, or the unique Ukrainian 'і', 'ї', and 'є').
*   **Functional Transformer Pipeline**: Introduced a modular "pipeline" architecture for event processing. Event titles now pass through a chain of dedicated transformers that handle transliteration, emoji removal, and character filtering in a clean and predictable way.
*   **Perfect Watch Readability**: Guarantees that calendar events and notifications are always readable on the watch hardware's standard ASCII display, regardless of the original character set.

### ⌚ Adaptive UI & Capability Intelligence
Successfully implemented a fully dynamic user interface that intelligently adapts to every G-Shock watch model in the lineup:
*   **Smart Card Collapsing**: Entire settings cards (Locale, Light, Font, etc.) now automatically hide if the connected watch doesn't support *any* of the features in that group. This keeps the interface clean and model-specific.
*   **Granular Visibility Control**: Individual rows for **Time Format**, **Date Format**, and **Language Selection** now respect hardware capability flags (`hasTimeFormat`, `hasDateFormat`, `weekLanguageSupported`) and hide independently.
*   **Font Selection Support**: Integrated the `hasMultipleFonts` attribute into the dynamic UI, ensuring the Font card only appears for modern digital models that support it.

### 🛡️ Core Stability & Thread Safety
Completed a major hardening of the application's internal communication engine:
*   **Global Thread-Safe IO**: Refactored all 14 core I/O handlers to use a stabilized local-variable pattern with `CompletableDeferred`. This eliminates the "hangs" that occurred when the UI triggered multiple concurrent Bluetooth requests.
*   **Startup Crash Resolution**: Fixed a critical `IllegalArgumentException` in the notification listener by migrating to the official Android `requestRebind()` API and implementing state-aware binding tracking.
*   **Resource Integrity**: Enforced strict `Cursor` lifecycle management in the calendar engine to eliminate potential memory and resource leaks.

### ⌚ Advanced Protocol Maturity
*   **Unified Protocol Hierarchy**: Completed the transition to an extensible protocol architecture. `MipProtocol` and `AnalogueProtocol` now inherit from a robust `StandardProtocol` base, significantly improving maintainability.
*   **GW-BX5600 Handshake Mastery**: Verified and finalized the multi-step handshake for the GW-BX5600, including dynamic payload write-back and optimized city-data packing.
*   **Precision Time Sync**: Aligned the entire time-setting pipeline to correctly propagate astronomical offsets (Solar, LMT) from the UI through to the hardware-specific protocol handlers.

---

# Release Notes - Casio G-Shock Smart Sync v49.0 — August 2, 2026

## ✨ Highlights

### 📅 Enhanced Multi-Language Event Support
Improved the readability of calendar events for users in Cyrillic-speaking regions:
*   **Cyrillic to Latin Transliteration**: Implemented a specialized `CyrillicToLatin` engine that automatically maps Cyrillic characters (Russian, Bulgarian, etc.) to their closest Latin equivalents. This ensures that event titles are readable on the watch's hardware, which only supports standard ASCII characters.
*   **Intelligent Text Sanitization**: Refined the event title pipeline to remove emojis, strip accents, and perform transliteration in a single pass. This provides the cleanest possible text for the watch's limited display capabilities.

---

# Release Notes - Casio G-Shock Smart Sync v48.0 — August 1, 2026

## ✨ Highlights

### 🛡️ Thread-Safe I/O Architecture
Completed a massive architectural overhaul of the application's communication layer to ensure maximum stability during high-concurrency scenarios:
*   **Unified Concurrency Pattern**: Standardized all 13 core Bluetooth registers (Alarms, Settings, Events, Step Counter, etc.) to use a stabilized local-variable pattern. This ensures that every requestor receives its own intended response even if multiple requests are firing simultaneously.
*   **Race Condition Resolution**: Implemented synchronized state publication and completion logic, effectively eliminating the "orphaned awaits" and UI hangs that occurred during rapid screen transitions.

### ⌚ Advanced Protocol Hierarchy
Re-engineered the watch protocol engine for better maintainability and model-specific precision:
*   **Extensible Hierarchy**: Refactored `StandardProtocol` into a base class, allowing specialized protocols to inherit standard behavior while only overriding what's necessary.
*   **New MipProtocol**: Introduced a dedicated protocol for modern Memory-in-Pixel models like the **GW-BX5600**, ensuring they perform the complex multi-step handshake required for modern Casio hardware.
*   **Refined Analogue Support**: Simplified the `AnalogueProtocol` for the **MTG-B3000** and **B1000**, reducing code duplication by 60% while preserving their specialized dual-dial logic.

### ⚙️ Model-Specific Refinements
*   **DW-H5600 Optimization**: Fine-tuned the DW-H5600 to use the legacy time-setting sequence while preserving modern MIP notification support, ensuring perfect compatibility with its unique hybrid protocol.
*   **GW-BX5600 Handshake Fix**: Resolved a critical time-sync issue by implementing dynamic payload write-back. The app now echoes the full accumulated buffer back to the watch, preventing sync failures caused by truncated data.

### 🧹 System & Maintenance
*   **GShockAPI Simplification**: Cleaned up the main library interface by delegating hardware-specific logic to the internal protocol layer.
*   **Build System Alignment**: Re-integrated the `:api` module as a direct project dependency to facilitate faster development and tighter integration testing.

---

# Release Notes - Casio G-Shock Smart Sync v47.0 — July 31, 2026

## ✨ Highlights

### ⌚ Dynamic Capability-Driven UI
Introduced a highly adaptive interface that automatically tailors itself to your specific G-Shock hardware:
*   **Smart Card Collapsing**: Settings cards (like Locale, Light, or Operational Tone) now automatically collapse and disappear if the connected watch supports *none* of the features within that category. This ensures a clean, relevant experience for every model.
*   **Fine-Grained Item Control**: Individual settings like **Time Format**, **Date Format**, and **Language Selection** now hide independently if they are unsupported by the watch hardware.
*   **Centralized Logic**: Unified all hardware capability mappings into a single `WatchFeatureManager`, simplifying future model integrations.

### 🛡️ System Robustness & Compliance
*   **Notification Listener Fix**: Resolved a critical "Service not registered" crash during application startup by migrating to the official Android `requestRebind()` API.
*   **Legacy Service Removal**: Completely removed the `KeepAliveService` to ensure full compliance with Android 14+ background execution standards and resolve `ForegroundServiceStartNotAllowedException`.
*   **Resource Management**: Implemented strict `Cursor` lifecycle management using modern Kotlin patterns to prevent memory and resource leaks.

### ⌚ MTG-B3000 & Protocol Precision
*   **MTG-B3000 Stability**: Finalized specialized 15-byte protocol handling for the MTG-B3000 timer and corrected data offsets for battery and temperature reporting.
*   **Intelligent Routing**: Enhanced the `MessageDispatcher` to automatically unwrap complex `0x28` envelope packets, ensuring data reaches the correct handlers without corruption.
*   **Radix 16 Crash Resolved**: Hardened the binary utility layer to safely ignore non-hex characters (like ASCII city names), preventing crashes during time-synchronization sequences.

### 🧹 Code Quality & Project Health
*   **Full Project Linting**: Addressed over 80 code quality issues, including a critical logic error in `AlarmNameStorage.kt` and standardizing all logging via the `Timber` library.
*   **Package Optimization**: Significantly reduced application size and clutter by removing dozens of unused string resources and redundant drawable files.
*   **Localization Synchronization**: Verified and restored missing translations across all 11 supported languages to ensure UI consistency worldwide.

---

# Release Notes - Casio G-Shock Smart Sync v46.5 — July 29, 2026

## ✨ Highlights

### ⌚ MTG-B3000 & Protocol Mastery
Comprehensive refinements to support modern dual-dial G-Shock models and resolve deep protocol anomalies:
*   **MTG-B3000 Precision**: Corrected battery and temperature decoding for the MTG-B3000, aligning with its specific data offsets (index 4 for battery, index 8 for temperature).
*   **Intelligent Packet Routing**: Enhanced the `MessageDispatcher` to detect and "unwrap" complex `0x28` envelope packets used by newer models. This ensures that Home Time, Settings, and Alarms data are routed correctly even when bundled together.
*   **Specialized Timer Handling**: Implemented a dedicated 15-byte protocol for the MTG-B3000 timer, including automatic unwrapping of the `0x04` header for reliable reading.
*   **Weekday Protocol Alignment**: Fixed a long-standing issue where weekdays were incorrectly offset. The app now correctly sends a 0–6 range as required by the Casio protocol.

### 🛡️ App Stability & Robustness
*   **Radix 16 Crash Resolved**: Fixed a critical crash where the app attempted to parse ASCII text (like city names) as binary hex data. The utility layer now intelligently filters non-hex characters to ensure protocol integrity.
*   **Thread-Safe IO Layer**: Overhauled the communication engine with a robust `CompletableDeferred` pattern and thread-safe state management. This eliminates race conditions where incoming Bluetooth notifications could "reset" the state before a request finished.
*   **Notification Listener Fix**: Refactored the `NotificationMonitorService` to use the official `requestRebind()` API and added state tracking to prevent "Service not registered" crashes during startup.
*   **Safe Background Actions**: Added `try/catch` wrappers and persistent logging to all background routines. BLE failures or timeouts will no longer crash the entire application process.

### 🧹 Code Quality & Cleanup
*   **Full Project Linting**: Addressed dozens of code quality issues, including a critical `SuspiciousIndentation` error in `AlarmNameStorage.kt` and standardizing logging via the `Timber` library.
*   **Resource De-bloating**: Streamlined the application package by removing dozens of unused string resources and redundant drawable files.
*   **Localized Info Update**: Removed obsolete "Dual-dial" instructions from the Time Info dialog in all 11 supported languages, ensuring clear and accurate documentation.

### 🛠 System & Maintenance
*   **Service Modernization**: Removed the legacy `KeepAliveService` to resolve `ForegroundServiceStartNotAllowedException` on modern Android versions. Background connectivity is now handled more efficiently via standard system listeners.
*   **Unified Time Sync**: Simplified the time-setting pipeline to focus on the main dial for all models, providing a consistent experience across both single and dual-dial watches.

---

# Release Notes - Casio G-Shock Smart Sync v46.0 — July 29, 2026

## ✨ Highlights

### ⌚ MTG-B3000 & Protocol Precision
Comprehensive refinements to support modern dual-dial G-Shock models and resolve deep protocol anomalies:
*   **MTG-B3000 Mastery**: Corrected battery and temperature decoding for the MTG-B3000, aligning with its specific data offsets (index 4 for battery, index 8 for temperature).
*   **Intelligent Packet Routing**: Enhanced the `MessageDispatcher` to detect and "unwrap" complex `0x28` envelope packets used by newer models. This ensures that Home Time, Settings, and Alarms data are routed correctly even when bundled together.
*   **Weekday Protocol Alignment**: Fixed a long-standing issue where weekdays were incorrectly offset. The app now correctly sends a 0–6 range as required by the Casio protocol.
*   **Redundant DST Removal**: Eliminated an unnecessary manual DST calculation that was causing the watch to be set one hour fast in certain regions.

### 🛡️ App Stability & Robustness
*   **Radix 16 Crash Resolved**: Fixed a critical crash where the app attempted to parse ASCII text (like city names) as binary hex data. The utility layer now intelligently filters non-hex characters to ensure protocol integrity.
*   **Thread-Safe IO Layer**: Overhauled the communication engine with a robust `CompletableDeferred` pattern and thread-safe state management. This eliminates race conditions where incoming Bluetooth notifications could "reset" the state before a request finished.
*   **Safe Background Actions**: Added `try/catch` wrappers and persistent logging to all background routines. BLE failures or timeouts will no longer crash the entire application process.

### 🧹 Code Quality & Cleanup
*   **Full Project Linting**: Addressed dozens of code quality issues, including a critical `SuspiciousIndentation` error and standardizing logging via the `Timber` library.
*   **Resource De-bloating**: Streamlined the application package by removing dozens of unused string resources and redundant drawable files.
*   **Localized Info Update**: Removed obsolete "Dual-dial" instructions from the Time Info dialog in all 11 supported languages, ensuring clear and accurate documentation.

### 🛠 System & Maintenance
*   **Service Modernization**: Removed the legacy `KeepAliveService` to resolve `ForegroundServiceStartNotAllowedException` on modern Android versions. Background connectivity is now handled more efficiently via standard system listeners.
*   **Unified Time Sync**: Simplified the time-setting pipeline to focus on the main dial for all models, providing a consistent experience across both single and dual-dial watches.

---

# Release Notes - Casio G-Shock Smart Sync v45.0 — July 25, 2026

## ✨ Highlights

### 🛡️ Global Crash Reporting
Improved app stability and diagnostic capabilities with a new global error handling system:
*   **Persistent Crash Logs**: Implemented a global uncaught exception handler that automatically captures and writes diagnostic logs to disk even if the app crashes unexpectedly. This ensures critical debugging information is preserved for future troubleshooting.
*   **Early Initialization**: The reporting system now starts immediately upon application launch, providing coverage for early-lifecycle initialization errors.

### 📚 Developer & Technical Deep-Dive
Introduced comprehensive technical documentation to aid contributors and power users:
*   **New Technical Overview**: Created `TECHNICAL-OVERVIEW.md`, providing a detailed map of the app's architecture, including the background connection lifecycle, calendar sync engine, and the "Actions" pipeline.
*   **Internal Protocol Documentation**: Detailed the bit-packed "Scratchpad" memory layout used to store custom alarm names and action settings on the watch hardware.

### 🛠 Reliability & Infrastructure
*   **Build System Update**: Updated the Android Gradle Plugin (AGP) to **v9.3.1**, ensuring compatibility with the latest Android Studio features and build optimizations.
*   **Service Robustness**: Enhanced the `KeepAliveService` with improved error handling and Android 14+ timeout management, ensuring background tasks terminate gracefully when system limits are reached.
*   **Project Cleanup**: Streamlined the project structure by removing legacy local module references in favor of the production-ready GShockAPI library.

---

# Release Notes - Casio G-Shock Smart Sync v44.0 — July 24, 2026

## ✨ Highlights

### 🎨 Dynamic UI Visibility
Introduced a powerful new system to automatically adapt the app's interface to the specific hardware capabilities of your connected G-Shock watch:
*   **Centralized Feature Management**: All watch capability logic is now handled by a new `WatchFeatureManager`. This decouples the user interface from the underlying hardware details, ensuring a cleaner and more reliable experience.
*   **Intelligent Auto-Hiding**: Settings that your specific watch model doesn't support (such as **Power Saving Mode**, **Multiple Fonts**, or **Date Format**) will now be automatically hidden from the menus, keeping the interface simple and relevant.
*   **Reactive UI Updates**: The app now intelligently re-evaluates available features the moment your watch finishes connecting. Navigation tabs like **Events** will dynamically appear or disappear based on whether your watch supports reminders.
*   **"N/A" Placeholder Support**: Introduced a standard "N/A" view for entire feature cards that are unavailable, providing clear feedback when a watch model lacks a specific category of functionality.

### 🛠 Architectural Improvements
*   **Injectable Capability Layer**: ViewModels now use a shared `IWatchFeatureManager` interface instead of accessing low-level watch properties directly. This significantly improves the app's testability and overall stability.
*   **Diagnostic Logging**: Added enhanced logging for feature evaluation to help developers quickly diagnose model-specific visibility issues.

---

# Release Notes - Casio G-Shock Smart Sync v43.0 — July 18, 2026

## ✨ Highlights

### ⌚ Centralized Time Synchronization
*   **Unified Logic**: Centralized all watch time-setting operations into a single `WatchTimeUpdater` component. This ensures that every time synchronization event follows the same robust sequence of operations.
*   **Reliable Astronomical Offsets**: Fixed a bug where setting the time from a watch button (Action) would sometimes incorrectly revert to **System Time** instead of the user's selected **Solar Time** or **LMT**.
*   **Scratchpad Sync**: The app now explicitly awaits and loads the latest timezone settings from the watch's internal "scratchpad" memory before every time update, ensuring total consistency between the app UI and background actions.

### 🛠 Reliability & Performance
*   **Load Time Monitoring**: Added precise performance logging for scratchpad data operations to help diagnose and optimize communication speed with the watch hardware.
*   **Architectural Cleanup**: Removed redundant background services and simplified the time-update flow between the Time and Actions screens for better long-term maintainability.

---

# Release Notes - Casio G-Shock Smart Sync v42.5 — July 14, 2026

## ✨ Highlights

### 🌍 Advanced Astronomical Time Support
Introduced specialized time synchronization options for users interested in astronomical and local geographic time:
*   **New Time Options**: Select between **System Time**, **Local Mean Time (LMT)**, **Local Solar Time (LST)**, and **Sidereal Time** directly from the Time screen.
*   **LMT Calculation**: Automatically calculates time based on your exact longitude.
*   **Local Solar Time**: Uses the `adhan2` library to calculate the "True Solar Time" based on the sun's actual position (Equation of Time).
*   **Sidereal Time**: Synchronize your watch with the Earth's rotation relative to the stars, ideal for astronomical observation.
*   **Watch Synchronization**: When syncing, the app now respects your selected time type, allowing your G-Shock to display LMT, LST, or Sidereal time.
*   **Persistent Settings**: Your selection is securely stored in the watch's internal "scratchpad" memory.

### ℹ️ Educational Info Dialog
*   **Interactive Info Button**: A new information button provides clear, brief explanations of each time type.
*   **Multilingual Support**: Fully translated into **11 languages**, including Arabic, Bulgarian, Catalan, Chinese, French, German, Hungarian, Japanese, Russian, and Spanish.

### ⚙️ Enhanced Automation & UI
*   **Instant Action Saving**: Removed the manual "Send to Watch" button in the Actions screen. Settings are now saved immediately to the watch upon modification for a seamless experience.
*   **Improved Dropdown UI**: The time zone selector now features a clear visual border and improved layering to prevent accidental interaction with underlying controls.
*   **Simplified Instructions**: Streamlined the connection screen instructions across all languages.
*   **Home Time Display**: Fixed an issue where the Home Time value was not correctly displayed for certain watch models.

### 🛠 Reliability & Performance
*   **Foreground Phone Finder**: Overhauled the Phone Finder to use a **Foreground Service** and **Full Screen Intent**. This ensures the alarm rings reliably on **Android 14, 15, and 17** (including Pixel devices) even when the app is backgrounded or the phone is locked.
*   **Robust Motion Detection**: Simplified the "pick up to stop" logic with a 2-second grace period and improved vibration resistance (3.0g threshold) to prevent false triggers on hard surfaces.
*   **Optimized Scratchpad Storage**: Completely redesigned the scratchpad management to use **bit-level packing**. The entire configuration (Alarm Names, Actions, and Time Settings) now fits into a compact, fixed-layout buffer, ensuring long-term data integrity and efficiency.
*   **Auto-Location Permissions**: The app now ensures location permissions are requested early to support astronomical calculations immediately upon install.
*   **Real-Time Clock**: The on-screen clock dynamically updates to show the selected LMT, LST, or Sidereal time in real-time.

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
