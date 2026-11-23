# Change watch settings from Home Assistant (DW-H5600)

Send a notification through the companion channel with the message `command_watch_setting`. It is never shown on the watch.

```yaml
action: notify.mobile_app_<device_name>
data:
  message: command_watch_setting
  data:
    setting: watch_face
    value: steps
```

Several at once: `data: {settings: {watch_face: steps, step_goal: 9000}}`.

- An unknown key or a bad value is refused: one line in the App log (Settings > App log) with the reason, nothing is written.
- Watch not connected: the latest value per key waits and is written after the next connection, once the watch's setup is done.
- After a write the app reads the value back and logs `Home Assistant set watch_face to steps`.
- Values are sent back after each connection: sensor `Watch settings (writable)` (state = how many were read, every value an attribute), plus `Watch face name` and `Custom face slot`.
- Not settable: time, pairing, reset, firmware, light duration, home city, per-activity items, notification language and off-at-night (not read yet).
- The `device_name` of the registration never changes once the watch is registered, so the notify action name stays.

| Key | Allowed values |
|---|---|
| `watch_face` | time_only, custom, steps, tide |
| `custom_slot` | step item names, e.g. steps, heart_rate, sleep_score_and_level, sunrise_sunset, moon_age (top-right item of the custom face) |
| `hr_measurement` | off, activities, always |
| `bedtime, wake_time` | HH:MM |
| `night_start, night_end` | HH:MM |
| `step_reminder` | true, false |
| `length_unit` | km, miles |
| `step_goal` | 100 to 200000 |
| `profile_height_cm` | 100 to 250 |
| `profile_weight_kg` | 20 to 300 |
| `profile_sex` | male, female |
| `smart_alarm, smart_alarm_snooze` | true, false (snooze needs the alarm on) |
| `smart_alarm_time` | HH:MM |
| `smart_alarm_window_min` | 15, 30, 45, 60 |
| `training_background` | not_set, occasional, regular, frequent, heavy, semi_pro, pro |
| `max_heart_rate` | 100 to 230 |
| `resting_heart_rate` | 30 to 120 |
| `vo2max` | 10 to 90 |
| `notification_sound_mode, notification_vibration_mode` | off, custom_per_event, on |
| `notification_pattern` | mute, pattern_a, pattern_b, pattern_c |

Words are case-insensitive; spaces count as `_`.

## Example: Steps at 07:00, Time only at 22:00

```yaml
automation:
  - alias: Watch face by time
    triggers:
      - trigger: time
        at: "07:00:00"
        variables: {face: steps}
      - trigger: time
        at: "22:00:00"
        variables: {face: time_only}
    actions:
      - action: notify.mobile_app_<device_name>
        data:
          message: command_watch_setting
          data:
            setting: watch_face
            value: "{{ face }}"
```
