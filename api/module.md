# Module casio-g-shock-smart-sync-app

# Package org.avmedia.gshockapi
Casio G-Shock B5000/B5600/B2100/GA-B2100 API

## Description
This library provides a comprehensive API to communicate and issue commands to the Casio G-Shock series of watches via Bluetooth. It is designed to work with various models, including B5000, B5600, B2100, GA-B2100, and more.

### Key Features:
- **Time Synchronization**: Set the watch's time and timezone based on the phone's current state.
- **Connection Discovery**: Support for both manual BLE scanning and background device discovery (Companion Device Manager fallback).
- **Watch Features**:
    - Manage up to 5 **Alarms**.
    - Configure up to 5 **Reminders/Events**.
    - Control **Settings** (Date format, Language, Button tones, etc.).
    - Access **World Cities** and DST configurations.
- **Sensor Data**: Read **Battery Level** and **Temperature** from the watch.
- **Notifications**: Send rich notifications to the watch display (supported models).

### Quick Start
All the core API functions are located in the **[GShockAPI]** class. 

```kotlin
val api = GShockAPI(context)

// Wait for a connection
api.waitForConnection()

// Perform actions
val battery = api.getBatteryLevel()
api.setTime()
```

For detailed implementation examples, see the `/app` directory of this project.
