# Notifications on the watch (Home Assistant)

The app's Home Assistant plugin is the MQTT one of
[smartsync-plugins](https://github.com/ExcuseMi/smartsync-plugins/blob/main/homeassistant/README.md). The old mobile
app integration (`notify.mobile_app_<device_name>`) is gone from the app; delete its device in Home Assistant.

| Send | How |
|---|---|
| title and message | `notify.send_message` on the watch's notify entity |
| title, message, app label, type | `mqtt.publish` of JSON to `smartsync/<watch>/set/notify` |

```yaml
action: mqtt.publish
data:
  topic: smartsync/gshock-c51422f1e489/set/notify
  payload: >-
    {{ {"title": "Door", "message": "Front door opened", "data": {"app": "HA", "type": "message"}} | tojson }}
```

- `app` and `type` may sit at the top level or in `data` (as the mobile app's `data:` had them); the top level wins.
- `type`: `message`, `calendar`, `email`, `call`, `generic` (sets the icon). Without one: `generic`; without `app`:
  "Home Assistant".
- Keep titles to 32 bytes (UTF-8); the pop-up shows about 10 characters. The text is cut at 193 bytes.
