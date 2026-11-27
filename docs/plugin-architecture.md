# How plugins work

This document explains the plugin system of G-Shock Smart Sync: what a plugin is, how the app finds, starts and limits
one, what it may see and do, and how a new one is written. It is for developers. For a user's view, see
[Plugins](../README.md#plugins) in the README.

The plugin system was contributed by [ExcuseMi](https://github.com/ExcuseMi). The interface and the plugins live in
[ExcuseMi/smartsync-plugins](https://github.com/ExcuseMi/smartsync-plugins); the app's side of it lives in
`app/src/main/java/org/avmedia/gshockGoogleSync/plugins/`.

## 1. Overview

A **plugin** is an Android library that the app loads when it starts. It connects the watch to something else: Home
Assistant, Android Health Connect, a TRMNL display, Tasker. The app is the **host**.

```
  +------------------------------ Smart Sync app (host) -----------------------------+
  |                                                                                  |
  |  watch link  ->  PluginHub  ->  one event queue per plugin  ->  onEvent(...)     |
  |  (BLE)           |   |                                                          |
  |                  |   +-- AccessGuard: what the plugin declared, nothing more     |
  |                  |                                                              |
  |                  +-- PluginHost (a view of the app, one per activation)          |
  |                         values, history, settings, storage, permissions          |
  |                         notify / command  ->  CommandScheduler  ->  watch queue  |
  +------------------------------------------------------------------------------------+
                    ^                                         |
                    | depends on (compileOnly)                v
              +-----------+                          +--------------------+
              | plugin-api|  <-- the only thing -->  | a plugin (library) |
              +-----------+      a plugin sees       +--------------------+
```

Three rules shape everything below:

1. **A plugin sees the app only through `plugin-api`.** No Casio, Bluetooth or app classes. The same plugin works with
   any watch that has the capabilities it asks for.
2. **A plugin declares what it reads, controls and sends, and the host keeps it to that.** The person sees the
   declaration before turning the plugin on.
3. **A plugin cannot hurt the app by failing.** Its calls are time limited, its events are queued per plugin, and a
   plugin that keeps failing is turned off.

## 2. The pieces

| Piece | Where | What it is |
|---|---|---|
| `plugin-api` | `smartsync-plugins/plugin-api` | The interface: `WatchPlugin`, `PluginHost`, `WatchEvent`, `PluginAccess`, the data and command types. Pure Kotlin plus Android `Context` and Compose. |
| A plugin | `smartsync-plugins/plugins/<name>` | A library with one class implementing `WatchPlugin`. Home Assistant, Health Connect, TRMNL and Tasker are plugins. |
| The host | `app/.../plugins/` | `PluginHub` and its helpers: finds plugins, runs them, turns app events into plugin events, serves their reads, runs their writes. |

Plugins are built against `plugin-api` and added to the app as ordinary Gradle dependencies from JitPack
(`app/build.gradle`, `gradle/libs.versions.toml`). They are **compiled into the app**; there is no download or install
of code at run time.

## 3. Discovery and start

- The plugin class has a public constructor without arguments and is named in
  `META-INF/services/org.avmedia.smartsync.plugin.api.WatchPlugin`.
- `PluginHub` finds every plugin with `java.util.ServiceLoader` and is started from the `Application`, so plugins run
  without any screen open.
- Each plugin has an **on/off switch** (`PluginSwitchboard`), stored so it survives restarts. A plugin found for the
  first time is **off**. At app start every plugin that is on is activated.
- A plugin carries the interface version it was built with (`apiVersion`). If the host cannot run that version
  (section 12) the plugin is listed as incompatible and stays off.
- The state of a plugin is one of: Off, Active, Incompatible, or Failed (turned off by the host after repeated
  failures; the person turns it on again with the switch).

## 4. Lifecycle and limits

A plugin implements `activate`, `onEvent` and `deactivate`. The host runs each on the plugin's own coroutine with these
limits (`PluginLimits`):

| Callback | Time limit |
|---|---|
| `activate` | 15 s |
| `onEvent` | 30 s |
| `deactivate` | 5 s |

- Time spent waiting inside a host call does not count against the limit.
- An exception or a time limit is caught and counted. **Five in a row turn the plugin off**; the Settings card shows
  why. A callback that ignores its cancellation turns the plugin off at once.
- `deactivate` never runs while `onEvent` does.
- After `deactivate` the host object is **revoked**: suspend calls give `Failed(STOPPED)`, reads return nothing, and
  storage still works.
- No host call throws. A call that can fail returns `PluginResult`: `Ok(value)` or `Failed(reason, message)`.

## 5. What a plugin implements

`WatchPlugin` has:

- **Identity and description**: `id`, `name`, `version`, `apiVersion`, `summary` (one line for the list), and the
  optional `description`, `setupNeeds`, `infoUrl`, `sourceUrl`, `issuesUrl`, `author`, `license`, `icon`. The host draws
  the plugin card, the on/off switch and the details page from these.
- **`access`**: the declaration (section 7).
- **`activate`, `deactivate`, `onEvent`**: the lifecycle (section 4).
- **`ui`** (optional): a settings screen and a row on the Actions tab (section 10).
- **`permissionIntent`, `missingPermissions`**: for a permission flow of the plugin's own (section 9).

## 6. What the host gives a plugin (`PluginHost`)

One `PluginHost` is created for each activation of a plugin. Through it a plugin can:

| Need | Calls |
|---|---|
| Know the watches | the paired watches with their capabilities, the connected and the current one, their names |
| Read values | `lastValues` (the newest value of each key and when it was read), `valueCatalog()` (what each key means: description, kind of data, type, unit), `measurementCatalog(watch)` (the watch family's measurements and how to start them) |
| Read history | typed records (day, hour, heart rate, night, workout) for a half-open date range |
| Read settings | a snapshot by setting key, and a catalogue of the settings the watch has |
| Act on the watch | `notify` (a notification on the watch), `command` (change settings, all or nothing) |
| Keep data | plugin storage and per-watch storage (section 8) |
| Permissions | which Android permissions are missing, ask for them |
| Tell the person | `status(...)`: the plugin's line in Settings |
| Ask for work | `declareNeeds` (section 7.2) |

## 7. Access and its enforcement

### 7.1 The declaration

`WatchPlugin.access` (`PluginAccess`) states:

| Field | Meaning |
|---|---|
| `reads` | the kinds of data it reads (`DataUse`: steps, heart rate, sleep, blood oxygen, workouts, battery, ...; `DataUse.ALL` for everything) |
| `controls` | what it may change on the watch (`Control`; `Control.ALL` for all) |
| `sendsTo` | where data goes, in plain words ("your Home Assistant server") |
| `stores` | what it keeps itself |
| `requires` | capabilities a watch must have for the plugin to work with it |
| `uses` | capabilities it uses where present |
| `androidPermissions` | Android permissions it owns, each with a reason |

The person sees this in Settings > Plugins, and once more before a plugin is turned on.

### 7.2 What the host does with it

`AccessGuard` applies the declaration to everything that goes through the interface:

- A value or history record of a kind the plugin did not declare is **not served**: a call returns
  `Failed(NOT_DECLARED)`, an event carries nothing for it. A record is cut down to the declared fields (a day also holds
  heart rate and blood oxygen).
- **Fail closed**: the host states the kind of every value key; a key with no stated kind is served to no plugin.
- Phone data the host has (coarse last-known location, phone battery) goes through the host, which asks for the Android
  permission itself.
- `notify` and `command` need their `Control`.
- A watch is matched by **capabilities, never by model name**. For each watch: all of `uses` present is full support,
  `requires` met is partial, `requires` missing is not supported (no events; calls give `Failed(NOT_SUPPORTED)`). A new
  model with the same capabilities works without a change to the plugin.

`PluginNeeds` (given to `declareNeeds` in `activate`) tells the host what the plugin wants delivered: values, button
presses, health reads, settings snapshots, and `wakeEveryMinutes` (section 8).

### 7.3 What it is not

**The declaration is a checked promise, not a sandbox.** Plugins are compiled into the app, run in its process and get
the application `Context`. The host checks everything that goes through the interface, and plugins are reviewed code;
a plugin could still do more through the Android APIs it can reach. Plugins are therefore added to the app at build
time, as reviewed code, and never loaded from anywhere else.

## 8. Events, writes and background work

### Events

The app's events reach the plugins through one ordered line (`SerialEvents`), so a plugin sees the events of a watch in
the order the app saw them: a read of values never passes the disconnect that followed it.

Plugin events include: connected, disconnected, values updated (with what was read now), health read (with its range),
workout stored, button pressed, setting changed, background started and stopped, watch removed, watch re-paired,
permissions changed, events lost, and wake. The host never sends an event type newer than the plugin's `apiVersion`.

Each plugin has its **own bounded queue** (64 events at least) on its own coroutine (`PluginEventQueue`). When it is
full, the oldest event that a newer one makes stale (values, a setting) is dropped first, and the plugin receives
`EventsLost` so it knows to read the state again. A slow plugin never delays another.

Events are not kept across a process death. A plugin reconciles from `history()` and `lastValues()` in `activate`.

### Writes

Everything a plugin writes to the watch (`notify`, `command`) goes through the `CommandScheduler`:

- **one at a time**, taken **round robin** between plugins, each plugin's own order kept;
- **never while a health or workout transfer holds the link** (after two minutes the call returns `BUSY`);
- each with a **time limit**; `TIMEOUT` means "may or may not have been written";
- at most **32 waiting per plugin**, then `BUSY` at once;
- the writes themselves go through the library's single watch queue, like the app's own.

`Delivery.Now` writes now. `Delivery.NextConnection` keeps a write for a watch that is not connected and sends it at
the next connection. It is held **in memory only**: lost when the app process ends, the plugin stops or the watch is
removed. `Ok(AT_NEXT_CONNECTION)` means accepted, not delivered.

Setting changes (`ChangeSettings`) are checked against the watch's setting registry first and are all or nothing; a
refusal names its key. `HostSettingsWriter` writes them one at a time and waits for the link to be ready (connected, and
for the DW-H5600 family its setup done).

### Background work

`PluginNeeds.wakeEveryMinutes` (15 at least) asks for a periodic wake-up. The host has **one** periodic job
(`PluginWakeWorker`) for all plugins. It runs also when the app was stopped, waits for the plugins that are on to start,
sends `Wake` to those whose interval has passed, and keeps the process and network until they finish (60 s at most).

## 9. Storage and permissions

### Storage

- Each plugin has its own storage and a per-watch storage; keys use `[A-Za-z0-9._-]`.
- A value is durable when the call returns; calls may block, so never use them from the main thread; `putAll` writes a
  batch.
- Limits: at most 2000 keys and 64 KiB per value.
- **Not encrypted**, and backed up with the app's data (`PluginStores` keeps each plugin's keys apart).
- The person can delete a plugin's data in Settings > Plugins, also for a plugin no longer in the app.

Because it is not encrypted, a plugin does not keep secrets it can avoid, and never logs one: the interface provides
`Redact.secret(...)` and `Redact.url(...)`.

### Android permissions

The app contains the manifest permissions of the plugins it is built with, so a plugin declares only the permissions it
uses itself, never one for data the host serves (location, battery).

The host shows a plugin's permissions, asks for the runtime ones when the plugin is turned on (or when the plugin calls
`requestPermissions()`), sends `PermissionsChanged` with the answer, and shows "Needs permission: ..." while one is
missing. A plugin with its own flow (Health Connect has one) provides `permissionIntent`.

## 10. The plugin's screens

The app draws the plugin list, each plugin's card and its on/off switch, and a details page with what it does, what it
needs, and what it reads, controls and sends. The plugin's own settings screen is a Compose function the plugin
supplies (`PluginUi`); the app shows it full screen with a back callback. A plugin may also add a row to the Actions tab
and ask to open its screen each time it is turned on.

Screens run on the main thread, must not block, and may be shown while the plugin is off. **An exception in a plugin's
composable is not caught** (Compose cannot isolate it) and ends the app, so plugin UI code is kept small and tested.
Only the three lifecycle callbacks are caught.

The plugin's `status(...)` line appears on its card (`Ok("Connected")`, or `Error` after its own retries).

## 11. Logging

Plugins log with Timber, tagged with the plugin id. The app shows its log in Settings > About > App log, filtered by
tag. A plugin never logs a token, a webhook id, a URL path or query, or a notification's text.

## 12. Versions and compatibility

The interface has a version (`PluginApi.VERSION`); a plugin carries the one it was compiled against. A host runs a
plugin when `PluginApi.compatible(...)` says so:

| Release of the interface | May | The host runs |
|---|---|---|
| before 1.0, a new patch (0.1.x) | only add | a plugin of its own minor whose patch is not newer |
| before 1.0, a new minor (0.2.0) | break | only plugins of that minor |
| from 1.0, a new minor | only add | every plugin of its major whose minor is not newer |
| from 1.0, a new major | break | only plugins of that major |

What may be added in a compatible release: new event types, new values of every enum, new fields on records the host
makes, new `PluginHost` calls, and new members of `WatchPlugin` and `PluginUi` that have a default. So a plugin writes
`else ->` in every `when` over these types. A dump of the public interface is checked in CI (Kotlin
binary-compatibility-validator): any change that is not in the dump fails the build.

## 13. The plugins today

| Plugin | What it does | In the app |
|---|---|---|
| **Home Assistant** | The watch in Home Assistant through MQTT discovery: sensors, controls, notifications to the watch, the watch button as a trigger, history in long-term statistics | bundled |
| **Health Connect** | Writes steps, heart rate, resting heart rate, oxygen saturation, sleep sessions and exercise sessions to Android Health Connect. Write only; it never reads Health Connect | bundled |
| **TRMNL** | Pushes the watch's values to a TRMNL private plugin by webhook, within TRMNL's rate and size limits | bundled |
| **Tasker** | Watch events and states for Tasker (also MacroDroid, Automate) profiles, and watch actions for their tasks | in the plugin repository |

Each plugin has its own README and changelog in the plugin repository.

## 14. Writing a plugin

1. Add an Android library module `plugins/<name>/` to the plugin repository, with
   `compileOnly` and `testImplementation` of `plugin-api`.
2. Implement `WatchPlugin` with a public no-argument constructor and list it in the `META-INF/services` file.
3. Declare `access` as narrowly as possible, and in `activate` declare the needs with `PluginHost.declareNeeds`.
4. Write unit tests against a fake `PluginHost`; every plugin in the repository has them.
5. Make a release of the module; the app takes it as a Gradle dependency.

The rules of the interface are in `plugin-api/README.md` of the plugin repository.

## 15. Where things are in the app

| File (`app/.../plugins/`) | Role |
|---|---|
| `PluginHub.kt` | Finds plugins (ServiceLoader), starts those that are on, turns the app's events into plugin events, reads the watch values for them, runs their writes |
| `PluginRuntime.kt` | The state of each plugin, the limits, failure counting |
| `PluginSwitchboard.kt` | Each plugin's on/off switch |
| `AppPluginHost.kt` | One plugin's `PluginHost`, one per activation; revoked on stop |
| `AccessGuard.kt` | Applies the declaration to values, history and events |
| `SerialEvents.kt`, `PluginEventQueue.kt` | The ordered line of events and each plugin's bounded queue |
| `CommandScheduler.kt` | The one write queue for `notify` and `command` |
| `HostSettingsWriter.kt` | Writes a plugin's setting changes |
| `WatchValuesHost.kt`, `HistoryRecords.kt` | The values and history served to plugins |
| `Capabilities.kt`, `WatchSupport.kt` | A watch's capabilities and how far a plugin works with it |
| `PluginStores.kt`, `PluginPermissions.kt` | A plugin's storage keys and its Android permissions |
| `PluginWakeWorker.kt` | The one periodic job for `wakeEveryMinutes` |
| `PluginsScreen.kt`, `PluginCards.kt`, `PluginDetails.kt` | Settings > Plugins: list, cards, details |
