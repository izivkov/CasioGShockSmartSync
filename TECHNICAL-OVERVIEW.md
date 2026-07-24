# Technical Overview: Casio G-Shock Smart Sync

This document provides a deep-dive into the architecture, communication protocols, and core integration engines of the **Casio G-Shock Smart Sync** application.

## 🚀 Mission & Technology Stack

The app's mission is to provide a fast, privacy-focused alternative to the official Casio application, enabling advanced features like Calendar Sync and custom Remote Actions without requiring a cloud account.

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Declarative UI)
- **Dependency Injection**: Hilt (Dagger-based)
- **Asynchronous Logic**: Kotlin Coroutines & Flows
- **Architecture**: MVVM (Model-View-ViewModel)

---

## 🏗 The Connection Lifecycle

Communication with the watch is handled in two distinct modes:

### 1. Foreground Connection (`BluetoothHelper`)
Triggered when the user is actively using the app. It handles:
- Bluetooth adapter state management.
- Runtime permission verification.
- Direct BLE discovery and pairing.

### 2. Background Reconnection (`GShockCompanionDeviceService`)
The "magic" of the app lies in its ability to reconnect silently when the watch comes back into range.
- **CompanionDeviceManager**: Uses Android's native framework to wake the app when the watch's MAC address is detected nearby.
- **GShockCompanionDeviceService**: A Foreground Service that ensures the connection is maintained long enough to perform high-priority tasks like "Phone Finder" or "Auto Time Sync," even if the phone is locked.

### 3. Service Discovery & `WatchInfo`
The app waits for the `ConnectionSetupComplete` event before evaluating the watch's identity. Once the BLE handshake is done, the **`GShockAPI`** resolves the specific model (e.g., `GW-B5600`) and populates the `WatchInfo` singleton with hardware capabilities.

---

## 📅 Calendar Synchronization Engine

One of the app's unique value propositions is pushing phone calendar events to the watch's "Reminders" feature.

- **Data Source**: `CalendarEvents.kt` queries the Android `ContentResolver` to fetch events from Google, Samsung, or other system calendars.
- **Filtering**: Intelligent logic excludes read-only calendars, holidays, and auto-generated "Birthday" events to save limited watch memory slots.
- **Mapping**: Since G-Shock memory is binary-packed, the app converts standard `CalendarContract` data into bit-packed `Event` objects compatible with the watch hardware.
- **Sync Trigger**: Updates are sent to the watch during every successful time-sync event or when a "CalendarUpdated" event is received via the `CalendarObserver`.

---

## 🎮 The "Actions" Pipeline (Remote Control)

The app allows the watch to control the phone (e.g., Take Photo, Find Phone).

### 1. The Trigger
When a user presses a button on the watch (usually the lower-right button), the watch sends a specialized notification over BLE.

### 2. Routing (`ActionRunner`)
The `ActionRunner` component listens for the `ButtonPressedInfoReceived` event via the global `ProgressEvents` bus. It decodes the type of button press:
- **Action Button**: Triggers user-defined routines (Flashlight, Camera, etc.).
- **Auto Time**: Triggers the background time-adjustment flow.
- **Find Phone**: Triggers the high-volume phone alarm.

### 3. Execution (`ActionsViewModel`)
Mapped actions are executed as standard Android Intents. For example:
- **Next Track**: Dispatches a `KeyEvent.KEYCODE_MEDIA_NEXT` to the system audio manager.
- **Take Photo**: Leverages the `CameraX` library to capture an image in the background or foreground.

---

## 🛠 Data Management & GShockAPI

### The Role of GShockAPI
The **[GShockAPI](https://github.com/izivkov/GShockAPI)** is the core translation layer. 
- **App Layer**: Decides *what* to do (e.g., "Set the alarm for 7:00 AM").
- **API Layer**: Handles *how* to do it (calculating the correct binary packet, identifying the right BLE characteristic, and handling the low-level handshake).

### Scratchpad Memory
Most watches have a small non-volatile memory called the **Scratchpad**. The app uses a `ScratchpadManager` to store custom data that the official Casio firmware doesn't support, such as:
- **Custom Alarm Names** (encoded in 3-bit strings).
- **Persistent Action Settings**.
- **User-selected Timezone Offsets** (Solar Time, Sidereal Time).

---

## 🎨 Reactive UI Visibility

Because G-Shock hardware varies, the `WatchFeatureManager` ensures the UI remains relevant:
- **Reactive Recomposition**: Screens like `Settings` and `Actions` automatically re-evaluate their visibility maps as soon as the `ConnectionSetupComplete` event confirms the watch model.
- **WatchFeature Wrapper**: A clean Compose component that hides entire visual trees if a hardware feature (like a step counter or power-saving mode) is missing.
