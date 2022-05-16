# Casio G-Shock Phone Sync

This app integrates the Casio G-shock [B5600 series bluetooth watches](https://world.g-shock.com/asia-mea/en/products/standard_digital/gw-b5600/), 
with Google services such as `Google Calendar` events and `Google Alarm Clock`. It is not meant to replace the 
[official](https://play.google.com/store/apps/details?id=com.casio.gshockconnected&hl=en_CA&gl=US) Casio app. Instead it converts your traditional 
G-shock into a semi-smart watch.

## General

<img src="images/ConnectingScreen.png"
alt="Connection Screen"
width=200
style="margin: 10px;" />

The app works by sending commands to the watch via Bluetooth (BLE). The watch data is not persisted on the phone, but instead is read from the watch each time 
a connection is established. Any changes on the app are sent back to the watch, only when the `Send to Watch` button is pressed.  

## Setting Time
<img src="images/TimeScreen.png"
alt="Time Screen"
width=200
style="margin: 10px;" />

The local time can be set from the main screen by pressing the `Send to Watch` button next to the current time display. The app uses your current location to get the local lime.
You can then set the watch time accordingly, without having to change your `World Time` selection on the watch.

This screen also shows your `Home Time` location and battery level.

## Alarms

<img src="images/AlarmsScreen.png"
alt="Alarms Screen"
width=200
style="float: left; margin: 10px;" />

<img src="images/SetAlarmScreen.png"
alt="Set Alarm Screen"
width=200
style="margin: 10px;" />

The GW-B5600 has 5 alarms and a `Signal` or `chime` setting. They are first read from the watch, 
and displayed in the app. The alarms can be updated by pressing on the time display of each alarm. 
A dialog will appear which allows you to select the time.

Once the alarms have been set, you can send them to the watch, or send them to the `Alarm Clock` app on the phone. 
Unfortunately, there is currently no way to read the alarms from the `Alarm Clock` app programmatically, 
so we cannot set the watch alarms from this app. However, we can set the `Alarm Clock` app with the watch's alarms.

## Events
<img src="images/EventsScreen.png"
alt="Events Screen"
width=200
style="margin: 10px;" />

**Make sure you have set your phone to auto-sync to your google account. Otherwise, adding events in your phone will not be reflected to the Google account, and you will not be able to see the events in the app.**

This screen displays `Events` from your `Google Calendar` and allows you to send these events to the watch's `reminders`.
(*don't confuse these with `reminders` in the Google Calendar app. These reminders are special features and are not accessible programmatically*).

There are many ways to set calendar events in `Google Calendar`. Events could be `one time`, repeating `daily`, `weekly`, `monthly` 
or `yearly`, or some complex period such as `every second Thursday of the month`. There are also events which occur number of 
times only (count events), like `repeat this event 12 times every Monday`. Not all event types can be supported on the watch, but this app 
attempts to adopt the calendar events to the watch as much as possible. The only **not** supported event types are `daily` and complex events, 
such as `every second Thursday of the month`. Count events are simulated on the watch with a start and end date, 
matching the event start time, count and frequency. In case the calendar event cannot be adopted to a watch reminder, 
the app will display the event as `Incompatible`. Only future events and recurring events which have not expired are displayed.

The watch only supports `all-day reminders`. However, if the Google calendar event has a specific time,
it will still be used as a day reminder on the watch.

## Actions
<img src="images/ActionsScreen.png"
alt="Events Screen"
width=200
style="margin: 10px;" />

The selected actions are run when the user short-presses the lower-right watch button from disconnected mode (initial screen). Using these actions, the watch acts like a remote control for your phone.

## Playstore
https://play.google.com/store/apps/details?id=org.avmedia.gshockGoogleSync

<img src="https://camo.githubusercontent.com/14aff0715e7c45aa33a99be1c282faf39181e284f3c25fbb60556a25bb9cdcee/68747470733a2f2f63646e2e7261776769742e636f6d2f73746576657269636865792f676f6f676c652d706c61792d62616467652d7376672f6d61737465722f696d672f66725f6765742e737667" width="50%" data-canonical-src="https://cdn.rawgit.com/steverichey/google-play-badge-svg/master/img/en_get.svg" style="max-width: 100%;">

## Credits
- The BLE-related code in this app is based on the `ble-starter-android` https://github.com/PunchThrough/ble-starter-android, but with many modifications.
- Some if the Casio specific code is loosely based on the `Gadgetbridge` https://github.com/Freeyourgadget/Gadgetbridge project
