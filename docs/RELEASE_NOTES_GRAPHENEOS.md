# Release Notes: `grapheneos` Branch

These changes focus on improving Bluetooth background connection reliability, particularly for GrapheneOS and devices running Android 14+ (Upside Down Cake).

### ✨ New Features & Improvements
* **Fallback Background BLE Scanning:** Introduced a new `PendingIntent`-based `BleScanReceiver` to serve as a fallback. This ensures device connection events are reliably detected even if the system's Companion Device Manager callbacks are delayed or fail.
* **Foreground Service Promotion:** The Companion Device Service now automatically promotes itself to a foreground service (using `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`) upon detecting the watch. This prevents the system from prematurely killing the service on Android 14+.
* **Event Deduplication:** Added logic to `GShockCompanionDeviceService` to filter out duplicate `DeviceAppeared` and `DeviceDisappeared` events occurring within a 1-second window, preventing redundant processing.
* **Optimized Presence Observation:** Modernized the device presence observation setup specifically for Android 13+ (Tiramisu) using explicit `ObservingDevicePresenceRequest` builders.

### 🔧 Fixes & Maintenance
* **Permissions Updates:** Added `BLUETOOTH_SCAN`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_CONNECTED_DEVICE` permissions to the manifest and runtime checks to comply with recent Android background processing restrictions.
* **Notification Tweaks:** Reduced the default importance of the persistent connection notification channel to `LOW` to make it less intrusive for users.
