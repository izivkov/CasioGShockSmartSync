# Sleep data of the GBX-H5600 (research)

Goal: bedtime, wake time, duration and quality in Home Assistant. No app change yet.
Method: Casio's public manuals and two recorded DW-H5600 transfers.

## Answers

| # | Question | Answer | Status |
|---|---|---|---|
| 1 | Does the watch record sleep itself? | Yes. The watch measures sleep at night and shows the result on its own screen (Nightly Recharge, by Polar). The phone app only shows more detail. | proven (manual) |
| 2 | Where is it? | In the LifeLog stream we already transfer: frame `0x53` (sleep stage timeline) and frame `0x5A` (one sleep record per day). Not in the ActivityLog. | frames proven, field layout likely (0x53) and unknown (0x5A) |
| 3 | Can we estimate it ourselves? | Yes, a rough bedtime and wake time from the per-minute movement codes (`0x56`) and heart rate (`0x52`). No stages, no score. Only worth it if 2 fails. | likely |
| 4 | Next step | One night with the watch on, then one capture in the morning (see below). | |

## 1. What the manuals say (proven)

| Module | Statement | Source |
|---|---|---|
| 3586 (GBX-H5600) | Nightly Recharge screen on the watch: Sleep Charge score and status, ANS Charge score and status, sleep status. Result appears on the watch "about one to two hours after you wake up". | [Nightly Recharge](https://support.casio.com/global/en/wat/manual/3586_en/NHCXSYhsjotorq.html) |
| 3586 | Needs: heart rate measurement ON or ACTIVITY & NIGHT TIME ON, watch firmly on the wrist, bedtime and wake-up time set, at least three measured nights in the past 28 days (one of them last night) for the scores. Under 4 hours of sleep: no or poor data. | same page |
| 3586 | Bedtime and wake-up time can only be set in the phone app (My Watch, LIFE LOG, Sleep). | [App settings](https://support.casio.com/global/en/wat/manual/3586_en/NHCXSYpspbhfbb.html) |
| 3586 | Smart alarm: the watch itself estimates the sleep state from accelerometer and heart rate. | [Smart Alarm](https://support.casio.com/global/en/wat/manual/3586_en/NHCXSYgcjqgiyj.html) |
| 3516 (DW-H5600) | Same feature set: Nightly Recharge, sleep status 5 levels, sleep score 1 to 100, detail only in the app. | [3516 manual](https://support.casio.com/global/en/wat/manual/3516_en/) |

Since the watch shows the result without a phone, the sleep analysis runs on the watch. The phone app reads it.

## 2. Where it is in the transfer

Evidence: two full recorded transfers of a DW-H5600, a long one (16.5 hours) and a short one (4.7 hours). The unit tests
hold hand-built streams, so they say nothing about sleep.

| Frame | Size | Content | Status |
|---|---|---|---|
| `0x53` | 2016 bytes: 252 entries of 8 | Entry 0: start as BCD `yy mm dd hh mm ss` + 2 flag bytes. Entries 1..250: `u32 LE seconds`, stage byte, `ff ff ff`. Entry 251: 8 x `ff`. All `fe` in entry 0 = no sleep. | structure proven on the long transfer (entry 0 and 251 match); fields likely |
| stage byte in `0x53` | 1 | `0xFA` deep, `0xFB` light, `0xFD` REM, `0xFE` interruption | likely; whether seconds is an offset or a duration is unknown |
| `0x5A` | 55 bytes per day | BCD date `yy mm dd`, 2 flag bytes, 50 bytes of fields. Probably the day's summary: times, durations, scores. | frame proven, fields unknown |
| `0x73` | 395 | smart alarm (in neither transfer) | unknown |
| `0x56` | 3 bytes per minute | movement code + flag: `0x10` no movement, `0x20` little, `0x80` walk, `0xA0` run, `0x60` bike | values seen in both transfers; meanings likely |
| `0x52` | 4 bytes per 2 minutes | bpm, `0xFE` = no reading (watch off the wrist or measurement off) | proven |

What the recorded data shows:
- Long transfer (16.5 hours, a whole night): `0x53` and `0x5A` are present but empty
  (`0x53` entry 0 = `fe fe fe fe fe fe f0 08`; `0x5A` = `26 10 28 f0 08` then only `fe`/`ff`).
  In that night the heart rate has no readings for five hours and the movement code is `0x10` throughout: the watch was not
  worn. So empty is the expected content, and no recording with real sleep exists yet.
- Short transfer (4.7 hours, evening): no `0x53`, `0x5A`, `0x5B`, `0x63` at all. The per-day frames only come in a sync that
  crosses a day boundary. Likely, not proven.
- Unknown: whether our own transfer marks the LifeLog as read, so that the CASIO WATCHES app no longer gets that night.
- Unknown: whether the GBX-H5600 uses exactly the same frames. Likely (same platform, same manual text).

### Found on the way: the stream is put together wrongly (affects today's values)

Proven by arithmetic on all 18 blocks of both transfers: a block is sent in chunks of 512 data bytes followed by a 2-byte
checksum, and a chunk spans several notifications. `H5600HealthDecoder.lifeLogStream` removes 2 bytes
from every notification instead. With Android's 247-byte notifications that drops 4 real bytes in every 512.

| Check | Result |
|---|---|
| raw bytes per block minus announced length | exactly 2 x ceil(length / 512) in all 18 blocks |
| rebuilt with the chunk rule | every block has exactly its announced length, the header is 20 bytes, steps read as a clean `i32` |
| current rule | the long transfer's header loses 2 bytes ("header layout not recognised"), frames come out shorter than announced |

Effect today: blocks longer than about 244 bytes lose bytes, so step slots older than about 5 hours shift and are dropped by the
range check, and heart rate samples shift. The 8-byte sleep entries cannot be read at all before this is fixed.
The last two bytes of the `06 ..` setup reply (`00 02` = 512) are likely the chunk size.

## 3. Estimating sleep ourselves (fallback)

| Point | Assessment |
|---|---|
| Data | per-minute movement code (`0x56`), heart rate every 2 minutes (`0x52`), steps per 5 minutes. Neither `0x56` nor sample times are decoded yet. |
| Method | Threshold on immobility, as in wrist actigraphy: sleep starts after N (say 15) minutes of code `0x10` with a valid heart rate, ends at the first sustained run of `0x20` or higher or steps; the longest such block in the night is the sleep. Missing heart rate = not worn = no sleep. |
| Why not Cole-Kripke or Sadeh | They weigh per-minute activity counts. We have three coarse classes, not counts, so their published weights do not apply. |
| Accuracy to expect | Bedtime and wake time within roughly 15 to 30 minutes on a normal night. Lying still awake counts as sleep (the known weakness of actigraphy), so duration reads high. No stages, no quality score. Will not match the watch's own numbers. |
| Condition | The sync must cover the night: the watch only sends what is new since the last transfer. |

## 4. Next step

| Order | Step | Who |
|---|---|---|
| 1 | Fix the chunk reassembly (guard: a test on the long recorded transfer, red with the current rule) and write the raw notifications of each transfer to a file. | agent |
| 2 | Wear the watch for a night: heart rate measurement ON, bedtime and wake-up time set in the CASIO WATCHES app. | owner |
| 3 | In the morning, one to two hours after getting up, when the watch shows its sleep result: capture one sync. Either a Bluetooth HCI snoop log while the CASIO WATCHES app syncs (Developer options, "Enable Bluetooth HCI snoop log", Bluetooth off and on, open CASIO WATCHES, wait for the sync, open My Watch, LIFE LOG, Sleep, screenshot it, then a bug report), or the raw dump from our app from step 1. | owner |
| 4 | Note what the watch and the app show: bedtime, wake time, time per stage, sleep score. That is the truth the bytes are matched against. | owner |

The snoop log of the CASIO WATCHES app is the safer first capture: it cannot lose a night if our own read turns out to consume the data,
and it comes with the app's numbers. Scores need three measured nights; times and stages may come after the first.
