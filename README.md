# Casio G-Shock Phone Sync

This app integrates the Casio G-shock Bluetooth-enabled watch, model GW-B5600, with Google services  
like `Google Calendar` events and `Google Alarm Clock`. It also allows you to set your watch's time.

## General
The app works by sending commands to the watch via Bluetooth (BLE). The app does not keep any information on the phone. 
Instead, watch settings are read and displayed on the app. Then any changes are sent back to the watch, 
only when the `Send to Watch...` button is pressed.  

## Setting Time
The time can be set from the main screen by pressing the `Send to Watch` button next to the current time display. 
This screen also shows your `Home Time` location and battery level.

In order to set the time, we first must read `DST` and `World Time` setting from the watch, and send them back. 
This is how the GW-B5600 operates, and will not set the time if this step is not performed first.

## Alarms
The GW-B5600 has 5 alarms and a `Signal` or `chime` setting. They are first read from the watch, 
and displayed in the app. The alarms can be updated by pressing on the time display of each alarm. 
A dialog will appear which allows you to select the time.

Once the alarms have been set, you can send them to the watch, or send them to the `Alarm Clock` app on the phone. 
Unfortunately, there is currently no way to read the alarms from the `Alarm Clock` app programmatically, 
so we cannot set the watch alarms from this app. However, we can set the `Alarm Clock` app with the watch's alarms.

## Events
This screens displays `Events` set in your `Google Calendar` and allows you to send these events to the watch's `reminders`. 
(*don't confuse these with `reminders` in the Google Calendar app. These reminders are special features and are not accessible programmatically*).

There are many ways to set calendar events in `Google Calendar`. Events could be `one time`, repeating `daily`, `weekly`, `monthly` 
or `yearly`, or some complex period such as `every second Thursday of the month`. There are also events which occur number of 
times only (count events), like `repeat this event 12 times every Monday`. Not all event types can be supported on the watch, but this app 
attempts to adopt the calendar events to the watch as much as possible. The only **not** supported event types are `daily` and complex events, 
such as `every second Thursday of the month`. Count events are simulated on the watch with a start and end date, 
matching the event start time, count and frequency. In case the calendar event cannot be adopted to a watch reminder, 
the app will display the event as `Incompatible`.

##Credits
- The BLE-related code in this app is based on the `ble-starter-android` https://github.com/PunchThrough/ble-starter-android, but with many modifications.
- Some if the Casio specific code is loosely based on the `Gadgetbridge` https://github.com/Freeyourgadget/Gadgetbridge project