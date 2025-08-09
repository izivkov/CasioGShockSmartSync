# Casio G-Shock Smart Sync

## Announcement
We now support sending notifications to watches that support this feature. It has been tested with 
the DW-H5600 only. If you have a watch that supports notifications, such as the GBA or GBD series, 
please let us know if it works for you.

## What is it?
I think we can do better than the official Casio G-Shock App! This app provides the following extra features:

- Sets watch's reminders from Google Calendar
- Automatically sets correct timezone when travelling. No need to switch between Word Time and Home time
- Use your watch to trigger actions on your phone remotely, like taking pictures, dialling a phone number, etc.
- Allows you to set when auto-time adjustment will run.
- Auto-configure most watch settings from phone's configuration.
- Phone's alarms can be synced with watch's alarms.
- Super fast connection time: 3.5 seconds vs 12 seconds for the official app.

### Here is what Google Search thinks about it:

**The G-Shock Smart Sync** app is a third-party application that allows users to connect and control certain G-Shock watches with their smartphones
It offers features like setting watch reminders from Google Calendar, automatically adjusting timezones when traveling, and acting as a remote control for your phone (e.g., taking pictures). It's an alternative to the official Casio G-Shock Connected app and is available on F-Droid.
Here's a more detailed breakdown:

#### Functionality:

- Google Calendar Integration:
The app allows you to sync your Google Calendar events with your G-Shock watch, displaying reminders and all-day events.
- Automatic Timezone Adjustment:
When traveling, the app can automatically update your watch's time based on your location.
- Remote Control:
The app can turn your watch into a remote control for your phone, enabling actions like taking pictures, music playback control, and more.
- Customization:
It allows you to configure various watch settings, including when to automatically update time. Can autofill some values from your phone.
- No Casio Account Required:
Unlike the official Casio app, the G-Shock Smart Sync app does not require a Casio account to use. Protects your privacy by not requiring a Casio account.

#### Key Differences from the Official App:

Third-Party: It's not an official Casio app, but rather an independent project.
Open Source: The Smart Sync app is open-source and available on F-Droid.
Google Services Integration: It specifically focuses on integrating with Google services like Calendar and Alarms.
No Casio Account: Unlike the official app, it doesn't require a Casio account for use.

#### Availability:

The G-Shock Smart Sync app is available on F-Droid, an open-source app store.
It is only for Android devices. 

In essence, the G-Shock Smart Sync app offers a unique set of features, particularly for users who want to integrate their G-Shock watches with Google services and enjoy a more customized experience than the official app provides.

# Supported Watches

The app will try to connect and adopt to any Casio watch that wants to connect to it (not just G-Shock). Surprisingly, many models will work "right off the bat". Here are some watches which are reported to work with the app:

G(M)W-5600, G(M)W-5000, GA-B2100, GA-B001-1AER, GST-B500, GST-B200, MSG-B100, G-B001, GBD-H1000 (Partial support), MRG-B5000, GST-B600, GCW-B5000, GG-B100, ABL-100WE, Edifice ECB-30, ECB-10, ECB-20, most Edifice watches, most Protrek models.

[Let us know](mailto:izivkov@gmail.com) if it works with other watches, and if you like us to add support for your watch.

### 📢 Help Expand GShock Smart Sync!

Do you have an old or new G-Shock watch that isn’t yet supported by GshockSmartSync? Your contribution can help improve the project!

If you’re willing to donate a watch (new or used), it would greatly assist in expanding compatibility and adding support for more models. Every donation helps bring the app to a wider range of users.

Please reach out to us at izivkov@gmail.com if you're interested in contributing. Your support is truly appreciated!


[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/org.avmedia.gshockGoogleSync/)

## General

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/mainscreen-b5600.png"
     alt="B5600 Connection Screen"
     width=180
     style="margin: 10px;" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/mainscreen-b2100.png"
     align="left"
     alt="B2100 Connection Screen"
     width=180
     style="margin: 10px;" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/mainscreen-dw-b5600.png"
     align="left"
     alt="B2100 Connection Screen"
     width=180
     style="margin: 10px;" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/mainscreen-edifice-ecb-30.png"
     align="left"
     alt="B2100 Connection Screen"
     width=180
     style="margin: 10px;" />


The app works by sending commands to the watch via Bluetooth (BLE). The watch data is not persisted on the phone, but instead is read from the watch each time 
a connection is established. Any changes on the app are sent back to the watch, only when the `Send to Watch` button is pressed.  

## Setting Time
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/TimeScreen.png"
alt="Time Screen"
width=200
style="margin: 10px;" />

The local time can be set from the main screen by pressing the `Send to Watch` button next to the current time display. The app uses your current location to get the local lime.
You can then set the watch time accordingly, without having to change your `World Time` selection on the watch.

This screen also shows your `Home Time` location and battery level.

## Alarms

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/AlarmsScreen.png"
alt="Alarms Screen"
width=200
style="float: left; margin: 10px;" />

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/SetAlarmScreen.png"
alt="Set Alarm Screen"
width=200
style="margin: 10px;" />

The B5600/B5000 watches have 5 alarms and a `Signal` or `chime` setting. They are first read from the watch,
and displayed in the app. The alarms can be updated by pressing on the time display of each alarm. 
A dialog will appear which allows you to select the time.

Once the alarms have been set, you can send them to the watch, or send them to the `Alarm Clock` app on the phone. 
Unfortunately, there is currently no way to read the alarms from the `Alarm Clock` app programmatically, 
so we cannot set the watch alarms from this app. However, we can set the `Alarm Clock` app with the watch's alarms.

## Events
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/EventsScreen.png"
alt="Events Screen"
width=200
style="margin: 10px;" />

>Syncing Your Calendar
Ensure that your phone is set to auto-sync with your Google Account. Without this setting, events added to your phone will not sync with your Google account and will not appear in the app.

# Syncing Your Calendar

Ensure that your phone is set to **auto-sync with your Google Account**. Without this setting, events added to your phone will not sync with your Google account and will not appear in the app.

## About This Screen

This screen displays events from your Google Calendar and allows you to send these events to your watch's reminders. *(Note: These are distinct from reminders in the Google Calendar app. The watch reminders are a unique feature and cannot be accessed programmatically.)*

## Event Types and Compatibility

Google Calendar supports various types of events:

- **One-time events**: Single occurrences.
- **Repeating events**: Daily, weekly, monthly, yearly, or complex patterns (e.g., "every second Thursday of the month").
- **Count events**: Limited repetitions, such as "repeat 12 times every Monday."

While the app does its best to adapt calendar events to the watch, some limitations exist:

- **Not Supported**: Daily events and complex patterns (e.g., "every second Thursday of the month").
- **Count Events**: Simulated on the watch using a start and end date, adjusted to match the event's start time, frequency, and count.

Events that cannot be adapted will appear as **Incompatible**. Only future events and ongoing recurring events are displayed.

## Watch-Specific Behavior

The watch only supports **all-day reminders**. For Google Calendar events with a specific time, the app will convert them into all-day reminders on the watch while still retaining their date.


## Actions
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/ActionsScreen.png"
alt="Events Screen"
width=200
style="margin: 10px;" />

The selected actions are run when the user short-presses the lower-right watch button from disconnected mode (initial screen). Using these actions, the watch acts like a remote control for your phone.

## Settings
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Settings.png"
alt="Set Alarm Screen"
width=200
style="margin: 10px;" />

This screen allows you to set up the watch's settings. You can auto prefill the values using information from your phone.

## Where are my World Cities?
Using an app to manually swap between Home Time and World Time is a bit silly. Your phone already knows where you are. 
When setting time, this app will also set the Home Time, Timezone and DST state to your current location. 
So when travelling to another timezone, just set time and you are good to go.

## Build Your Own App
For those who want to build their own Android app for interfacing with the G-Shock 5000/5600 watches, I have also created an API (library) project [here](https://github.com/izivkov/GShockAPI). 

## Similar Project

If you’d prefer not to use a mobile app but still want to set the correct time on your G-Shock, check out [this Python project](https://github.com/izivkov/GShockTimeServer). It can run as a server on a regular PC or Raspberry Pi with a Bluetooth interface. We’ve recently added support for an LCD display on the Pi—take a look!



