# Exchange review: our watch link against the exchange rules

Rules: one queue for reads and writes, 10 s per request, write result awaited, `FF` fails a waiting read only, write frame cut to
20 bytes, answers matched by the first bytes, unasked 28/39/3D/47/49 accepted at any time. All rules live in one table:
`api/.../casio/CommandSpec.kt` (`CommandTable`); every request goes through `WatchLink` (`WatchQueue.kt`).
Status: fixed = guard exists; open = not changed, reason given. Guards are in `api/src/test/.../casio` and `.../io/TideChannelTest.kt`.

| # | Command or area | What we did | What it should be | Sev | State |
|---|---|---|---|---|---|
| 1 | Every request | each write started in its own coroutine, order not guaranteed (alarm 0x15 then 0x16 could swap) | one queue, one at a time, in the order asked | High | fixed (WatchQueue; concurrency test) |
| 2 | Every write | fire and forget, result never awaited | wait for the write result, up to 10 s | High | fixed (writeChecked; failed-write test) |
| 3 | Link Loss (47 02) | written to the standard Link Loss characteristic | frame `00 01` on all-features | High | fixed (linkLossFrame; test) |
| 4 | Reads | no timeout or `FF` handling below the callers; callers wait on their own deferred without a limit | read ends on answer, `FF` or 10 s | High | queue fixed; the callers' unbounded waits are open (needs error handling in 15 IO objects) |
| 5 | Write frames | whole frame sent, whatever the length | cut to 20 bytes | Med | fixed (CommandTable.check; cut test) |
| 6 | Checked writes (settings screens) | write, then read back; no answer to the read-back reported the write as failed | read-back only after a confirmed write; a silent watch is not a failure | Med | fixed (`Written` result) |
| 7 | Checked writes | write result ignored | an unconfirmed write is reported | Med | fixed (WriteNotConfirmed) |
| 8 | Notification texts | one write of the whole text, no spacing, no hold-back | chunks of MTU-4 (20 before 0x28), 2000 ms after each result, held back during transfers and after a 3D pause or stop | Med | fixed (NotificationSender; tests). Home city and update check conditions not checked: open, Low |
| 9 | 0x3D, 0x39 unasked | during a LifeLog, workout or tide transfer routed by first-byte guess, so never handled; 0x3D had no handler | handled at any time | Med | fixed (UNSOLICITED_ALWAYS, NotificationGate) |
| 10 | 0x11, 0x3A in the LifeLog setup | echoed as read (undoes bit 2 and `DC 05` of the setup) | same bytes as the connection setup | Med | fixed (writeBackValue). The LifeLog setup still reads more codes than Casio (0x3B, 0x2F, 0x43, 0x13): open, Low |
| 11 | Read of write-only code (09, 25, 49), bare code that needs an option (1F, 24, 2E, 30, 31, 44, 4E, 54, 56, 58), 3D not `3D 30` | nothing stopped it | never sent | Med | fixed (table refuses; test) |
| 12 | 0x42, 0x3D minimum length | 6 and 17 value bytes; the GBX-H5600 answers 5 and 16 | at least what is decoded | Med | fixed (5 and 16; sound pattern and pop-up size hidden when absent; tests from the real bytes) |
| 13 | Tide transfer end | end message routed by its first byte, so an unknown first byte was dropped | the data-request characteristic decides; next piece 400 ms after the write result | Med | fixed (channelOf by source; confirmed chunks; every message logged). Real cause on the watch unproven |
| 14 | 0x45 profile | reads nothing on the GBX-H5600 | any length accepted, every answer logged | Med | logging added (settings read answers, queue rx, parse failure text); cause unproven |
| 15 | Process limits (LifeLog 5/10 s, workout 5/10/30 s, tide 5 s, 400 ms, 70 s) | constants in several files | one table | Low | fixed (LongProcess; test) |
| 16 | 0x39 exchange 30 s, setup steps 20 s | no limit | limits | Med | open: needs a timer in ConnectionSetup and a device run |
| 17 | 0x1D, 0x1E in the time exchange | we send `1D 00`, `1E 00`, `1E 01` | Casio sends bare `1D`, `1E`, `1E` (second answer unknown) | Med | open: works on devices today; table allows both forms |
| 18 | LifeLog busy retry, attempts | 1 s retry, 3 whole attempts | 2 s, no retry of a chunk | Low | open: proven sync, no device test |
| 19 | RawWatchRequest | own mutex and answer wait beside the queue | one waiter | Low | open: serialised by the queue now; merge later |
| 20 | Minimum lengths | table and `SettingRequests` both hold them | one place | Low | open: `SettingRequests` has 3 codes pinned by tests |
| 21 | Time exchange as long process | not gated against other processes | one process at a time | Med | open (LifeLog, workout and tide are gated by WatchExchange) |
| 22 | Read-back policy field | `readBack` in the table is documentation; SafeSettingWrite always reads back | table decides | Low | open |
| 23 | 3D before the first command | sending allowed | Casio waits for a start | Low | by design: a watch that sent its start before we connected would hold texts forever |
| 24 | LifeLog at connect (0x28 index 8 bit 0) | we pull on user action only | at connect when the bit is set | Low | by design |
| 25 | 0x44 cache | not used by our code | n/a | Low | n/a |
