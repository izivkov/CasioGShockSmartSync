# DW-H5600: capture session with the official CASIO WATCHES app

What to check in the stock app, and what each finding would let us add to this fork.
Method (same as for the sound mode / watch face): change **one setting at a time** in the
CASIO app while Android's Bluetooth HCI snoop log is recording, write down the order (and ideally
the time) of every change, then decode the packets with `~/h5600-probe/btsnoop_parse.py`.

## Before the session

- [ ] Write down the current CUSTOM per-function choices, alarms and settings (pairing may reset them).
- [ ] Developer options → **Enable Bluetooth HCI snoop log → Enabled** (not "Filtered"), then toggle phone Bluetooth.
- [ ] Watch: SETTING → GENERAL SETTINGS → PAIRING → **UNPAIRING**. Phone: forget the watch in Bluetooth settings.
- [ ] Give the CASIO app its Bluetooth permission back (`pm grant com.casio.wac_app android.permission.BLUETOOTH_CONNECT` and `…BLUETOOTH_SCAN`), pair it.
- [ ] Note the time when the CASIO app has finished its first connection (its initial sync is a useful capture too).

## Checklist: one change at a time

| # | Change in the CASIO app | Suspected packet | Feature for the fork |
|---|---|---|---|
| 1 | **CUSTOM / [USER] per-function sound**: for each function (alarm/timer, step reminder, interval timer, app notifications, low battery alert, any others listed) set **sound only → vibe only → none → back** | `0x42` bytes 2–5 (only known: byte 5 bit 0x10 = low-battery sound) | Full CUSTOM editor in the Custom tab |
| 2 | **Hourly time signal** on → off → on (not settable on the watch itself) | `0x42` byte 1 bit 0x20? or `0x13` byte 12 | Hourly chime switch |
| 3 | **Watch face display item**: every option the app offers, especially **weekday** | `0x51` bytes 2–5 (known: `0x12` sun, `0x00` steps) | Enable the "Weekday" button (+ any other items) |
| 4 | **Location / sunrise-sunset** settings (if the app has them), and how it answers the watch's `3D …` request on connect | `0x24`, `0x3D` (app writes `3d 32 …`) | Understand the 0x3D exchange; maybe drop our re-send workaround |
| 5 | **User profile**: height, weight, sex, birth year, stride (one at a time) | `0x45` | Real profile on the watch (our Health profile never reaches it; 0x2D is unanswered) |
| 6 | **Daily step goal** | `0x4F` / `0x3A` / `0x3B`? | Step goal synced to the watch |
| 7 | **Step / move reminder** (inactivity alert): on/off, interval, quiet hours | `0x3A` / `0x3B` / `0x43`? | Move-reminder settings |
| 8 | **Interval timer**: number of segments, each duration, repeats | `0x3D` (`28 00 28 00 28 00` = 3 × 40 s?) | Interval timer editor |
| 9 | **Countdown timer** set from the app | `0x18` (15 bytes; byte 5 = `0x03` unknown) | Confirm our timer fix, name byte 5 |
| 10 | **Alarm snooze** on/off and any **one-time** alarm option | `0x15` / `0x16` flags `0x10` / `0x20` | Confirm the snooze bit (already used) |
| 11 | **World time / second city** (e.g. Minsk) | `0x1D` / `0x1E` / `0x1F` | Keep world city 2 instead of Smart Sync blanking it |
| 12 | **Light**: auto light, light duration; **button tone**; **power saving** | `0x13` bits | Verify the stock Settings card's bit names on this model |
| 13 | **Notifications**: per-app selection, categories, vibration pattern (does the app write anything to the watch?) | `0x1B` handle / `26eb0030`? | Know whether filtering is phone-only (as we assume) |
| 14 | **Do Not Disturb / quiet hours**, if offered | ? | DND schedule |
| 15 | **Health history screen**: open previous days; note the app's daily steps and **calories** for a few days | LifeLog frames `0x63` (daily summary), `0x51` (energy) | Previous days in Health; kcal scale |
| 16 | Anything else that changes a packet we don't know yet | `0x25`, `0x2F`, `0x40`, `0x41`, `0x43`, `0x52`, `0x5A` | Name them |

## After the session

- [ ] Phone: pull the log with `adb bugreport` (the full `btsnoop_hci.log` is inside `FS/data/misc/bluetooth/logs/`).
- [ ] Watch: UNPAIRING again; phone: forget the watch; block the CASIO app again (`pm revoke …BLUETOOTH_CONNECT` / `…SCAN`).
- [ ] Pair Smart Sync again (its alarms, labels and settings are stored on the phone and survive).
- [ ] Set the CUSTOM choices / alarms back to what was written down, if pairing reset them.
- [ ] Do **not** commit the full snoop log (it contains other devices and notification texts); keep only trimmed excerpts.

Background and earlier results: `~/h5600-probe/FINDINGS.md`.
