# Casio G-Shock Smart Sync

<p align="center">
  <img src="images/gshock-smart-sync.jpg" alt="G-Shock Smart Sync Logo" width="600">
</p>

[![F-Droid](https://img.shields.io/badge/F--Droid-Available-brightgreen)](https://f-droid.org/en/packages/org.avmedia.gshockGoogleSync/)
[![GitHub](https://img.shields.io/badge/GitHub-Source-blue)](https://github.com/izivkov/CasioGShockSmartSync)
[![Version](https://img.shields.io/badge/version-25.7-blue)](https://github.com/izivkov/CasioGShockSmartSync/releases)
[![Connection Speed](https://img.shields.io/badge/Connection-Fast-green)](#)

**G-Shock Smart Sync** is an unofficial, open-source Android app for G-Shock, Edifice, and Pro Trek watches. It allows you to control your watch, sync data, and configure settings **without a Casio ID**.

It offers a faster, privacy-focused, and more powerful alternative to the official Casio app.

---

## Key Features

- **No Account Required**: Skip the Casio ID login entirely.
- **Calendar Sync**: Push your calendar events (Google, Samsung, etc.) to your watch as reminders.
- **Auto Timezone**: Automatically adjusts watch time when you travel.
- **Remote Control**: Use your watch buttons to take photos, control music, or launch voice assistants.
- **Alarm Sync**: Configure watch alarms easily from your phone.
- **Phone Finder**: Trigger a loud alarm on your phone to find it.

---

## Supported Watch Models

The app works with many Bluetooth-enabled G-Shock, Edifice, and Pro Trek models.

<p align="center">
  <img src="images/gw_b5600.png" width="150" alt="GW-B5600" style="margin: 10px;">
  <img src="images/ga_b2100.png" width="150" alt="GA-B2100" style="margin: 10px;">
  <img src="images/dw-b5600.png" width="150" alt="DW-B5600" style="margin: 10px;">
  <img src="images/ecb_30d.png" width="150" alt="ECB-30" style="margin: 10px;">
</p>

| Series | Compatible Models (Examples) | Note |
|:---|:---|:---|
| **Square** | GW-B5600, GMW-B5000, GW-5000, DW-B5600 | Classic square design support |
| **CasiOak** | GA-B2100, GBM-2100 | Solar and Bluetooth variants |
| **G-Steel** | GST-B500, GST-B400, GST-B200 | Metal series |
| **Edifice** | ECB-10, ECB-20, ECB-30 | Bluetooth Edifice models |
| **Others** | GBD-800, GBD-H1000, GG-B100 (Mudmaster) | Step trackers and sensors |

> **Note**: While these watches connect, not all specific hardware features (like fitness tracking steps on GBD models) may be fully supported yet.

---

## App Walkthrough

Here is a guide to the main screens of the application.

### 1. Connection Screen

<img src="images/mainscreen-b5600.png" width="200" align="right" style="margin-left: 20px;">

This is the first screen you see. It handles the Bluetooth connection to your watch.

- **Status**: Shows generic connection status.
- **Scan/Connect**: Automatically scans for your watch.
- **Paired Devices**: Lists your previously connected watches for quick access.

The app communicates directly with the watch via BLE. Connection is typically much faster than the official app (~3.5s vs 12s).

<br clear="all"/>

### 2. Time Setting

<img src="images/TimeScreen.png" width="200" align="right" style="margin-left: 20px;">

Manage your watch's timekeeping effortlessly.

- **Local Time**: Displays the current time from your phone.
- **Send to Watch**: Tap the button to sync your phone's time, timezone, and DST settings to the watch immediately.
- **Home Time**: Shows the 'Home' city configured on the watch.
- **Battery**: Displays the current battery level of the watch (e.g., High, Medium, Low).

<br clear="all"/>

### 3. Alarms

<img src="images/AlarmsScreen.png" width="200" align="right" style="margin-left: 20px;">

Read and configure the alarms stored on your watch.

- **View Alarms**: See the current settings for all 5 alarms and the hourly signal (Chime).
- **Edit**: Tap any alarm to change its time.
- **Sync**: You can send these alarm settings to the watch. 

*Note: Due to Android limitations, we cannot read alarms set in your phone's native Clock app, but we can set the watch's internal alarms.*

<br clear="all"/>

### 4. Events (Calendar Sync)

<img src="images/EventsScreen.png" width="200" align="right" style="margin-left: 20px;">

Sync your Android calendars to your watch!

- **Upcoming Events**: Lists future events from your phone's calendars.
- **Send to Watch**: Pushes these events to the watch's Reminder feature.
- **Compatibility**: Supports standard Android calendar events. Complex recurrence rules might show as "Incompatible".

The watch will display these as reminders with the date and title.

<br clear="all"/>

### 5. Actions (Remote Control)

<img src="images/ActionsScreen.png" width="200" align="right" style="margin-left: 20px;">

Turn your watch into a remote control. Assign actions to button presses on the watch.

- **Available Actions**:
  - **Find Phone**: Make your phone ring loudly.
  - **Take Photo**: Snap a picture with your phone's camera.
  - **Flashlight**: Turn on your phone's flashlight.
  - **Next Track**: Skip music tracks on your phone.
  - **Voice Assist**: Launch Google Assistant.
  - **Set Prayer Alarms**: Set the watch's 5 alarms to Islamic Prayer Times
- **How to use**: Short-press the lower-right button on the watch (when in Time mode) to trigger the selected action.

*Note*: The **Take Photo** action requires the app to be in the foreground. If the app is in the background, the action will not work.

<br clear="all"/>

### 6. Settings

<img src="images/Settings.png" width="200" align="right" style="margin-left: 20px;">

Configure app and watch preferences.

- **Watch Settings**: Adjust specific watch configurations (light duration, power saving, etc.).
- **App Settings**: Configure theme, behavior, and advanced options.

<br clear="all"/>

---

## Installation

- **F-Droid**: [Download here](https://f-droid.org/en/packages/org.avmedia.gshockGoogleSync/)
- **GitHub**: [Download APK from Releases](https://github.com/izivkov/CasioGShockSmartSync/releases/tag/v26.6)

## Contributing

We welcome contributions!
- **Code**: Submit a PR to add features or fix bugs.
- **Watches**: If you have a G-Shock model not yet supported, functionality can often be added. Donations of test units are also highly appreciated to help expand compatibility.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
