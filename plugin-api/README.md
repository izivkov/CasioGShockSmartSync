# plugin-api

The interface between Smart Sync (the host) and its plugins (Home Assistant, Health Connect, a TRMNL webhook, ...).
Pure Kotlin plus Android `Context` and Compose for a plugin's screens. No Casio, Home Assistant or app classes.

Coordinates (JitPack): `com.github.ExcuseMi:CasioGShockSmartSync:plugin-api-v<version>` (JitPack names the one published module after the repository; a commit works too: `...:CasioGShockSmartSync:<commit>`). Changes: `CHANGELOG.md`.

Plugins are compiled into the app and get the application `Context`. The access declaration is therefore a checked
promise to the person, not a sandbox: the host checks what goes through this interface; plugins are reviewed code.

## A plugin
- Implements `WatchPlugin` (public no-argument constructor) and lists it in
  `src/main/resources/META-INF/services/org.avmedia.smartsync.plugin.api.WatchPlugin`.
- `apiVersion = PluginApi.VERSION` (compiled in). The host runs it only when `PluginApi.compatible(...)` (below).
- Depends on the interface as `compileOnly("com.github.ExcuseMi:CasioGShockSmartSync:plugin-api-v<version>")` (the
  host brings the classes; `api` or `implementation` would put a second copy in the app) and `testImplementation` of the
  same for its tests. Built with Kotlin 2.4 or newer, Java 21 bytecode.
- Logs with Timber 5.x (the host's; `compileOnly` as well).
- Manifest: the app contains the permissions of the plugins it is built with, so a plugin declares only the Android
  permissions it uses, never one for data the host serves (location, battery).

## Versions and compatibility
| Release | May | A host runs |
|---|---|---|
| until 1.0: new patch (0.1.x) | only add (next section) | a plugin of its own minor whose patch is not newer than its own |
| until 1.0: new minor (0.2.0) | break | only plugins of that minor |
| from 1.0: new minor | only add | every plugin of its major whose minor is not newer |
| from 1.0: new major | break | only plugins of that major |

### What may change in a compatible release
- New `WatchEvent` types, new values of every enum (`FailReason`, `Capability`, `DataUse`, `Control`, `DataKind`,
  `ValueType`, `ValueCause`, ...), new fields of records the host makes (`WatchValues`, `ValueInfo`, `SettingInfo`,
  `PairedWatch`, history records), new `PluginHost` calls, new `WatchPlugin` and `PluginUi` members with a default.
- So a plugin writes `else ->` in every `when` over these. The host never sends an event type newer than the
  plugin's `apiVersion`.
- Data classes a plugin constructs (`PluginNeeds`, `PluginAccess`, `AndroidPermission`, `WatchNotification`,
  `Delivery.NextConnection`, `HistoryRange`) never get a new constructor parameter in a compatible release: a new type
  comes instead.

## Declaration (`WatchPlugin.access`)
| Field | Meaning | Example (Home Assistant) |
|---|---|---|
| `reads` | `DataUse` kinds it reads | all but the phone's location |
| `controls` | `Control`s it may use | notifications, watch settings, watch face, button press |
| `sendsTo` | where data goes, plain words | "your Home Assistant server" |
| `stores` | what it keeps itself | "Home Assistant address and token" |
| `requires` | `Capability`s a watch must have | none (any watch) |
| `uses` | `Capability`s used where present | notifications, health log, battery, settings, ... |
| `androidPermissions` | permissions it owns, each with a reason | none |

The person sees this in Settings > Plugins (and once before turning a plugin on). The host keeps to it:
- values, history and events of an undeclared `DataUse` are not served (`Failed(NOT_DECLARED)` for a call, nothing for an
  event). The host states the kind of every value (`valueCatalog()`); a value without one is served to no plugin;
- phone data the host has goes through the host: `PHONE_LOCATION` (coarse, last known; the host asks for the location
  permission), `PHONE_BATTERY`;
- `notify` and `command` need their `Control` (`SettingInfo.control`: the face rows are `WATCH_FACE`);
- per watch: all of `uses` = full, `requires` met = partial, `requires` missing = not supported (no events, calls give
  `Failed(NOT_SUPPORTED)`). Capabilities only, never model names: a new model with the same capabilities works.
- Android permissions: the host shows them, asks for the runtime ones when the plugin is turned on (or on
  `host.requestPermissions()`; `WatchPlugin.permissionIntent` for a flow of its own, such as Health Connect's), sends
  `WatchEvent.PermissionsChanged` with the answer and shows "Needs permission: ..." while one is missing.

## Calls and events
- No call throws: a call that can fail returns `PluginResult` (`Ok(value)` / `Failed(reason, message)`).
- One key per setting: `SettingInfo.key` in `ChangeSettings`, `settings()` and `SettingChanged`. `ChangeSettings` is
  all or nothing; a refusal names its key.
- Values: a whole number is a `Long`, a fraction a `Double`, fresh and after a restart. `HistoryRange` is half open
  (`to` exclusive).
- Writes go through the host's one watch queue: one at a time, never during a health or workout transfer, round robin
  between plugins, with a time limit; at most 32 waiting per plugin (then `BUSY`). `TIMEOUT` means "may or may not have
  been written". `Delivery.NextConnection` keeps a write in memory only (lost when the app process ends, the plugin stops
  or the watch is removed): `Ok(AT_NEXT_CONNECTION)` means accepted, not delivered.
- Each plugin has its own ordered, bounded event queue (64 at least) on its own coroutine. Full: the oldest stale events
  go first and the plugin gets `EventsLost`. Events are not kept across a process death: reconcile from `history()` and
  `lastValues()` in `activate`.
- Limits, at least: `activate` 15 s, `onEvent` 30 s, `deactivate` 5 s; time waiting in host calls does not count. A
  throw or a time limit is caught and counted; 5 in a row turn the plugin off (shown in Settings, the switch turns it on
  again). A callback that ignores its cancellation turns the plugin off at once. `deactivate` never runs while `onEvent`
  does. After `deactivate` the host object is revoked (`Failed(STOPPED)`, empty reads; storage still works).
- Later work: `PluginNeeds.wakeEveryMinutes` (15 at least) and `WatchEvent.Wake`, also in a process the app was
  stopped in. A WorkManager job of the plugin's own may run where the plugin is not started.
- Storage: keys `[A-Za-z0-9._-]`, durable on return, may block (never from the main thread), `putAll` for a batch, at
  most 2000 keys and 64 KiB a value. Not encrypted, and backed up with the app's data. The person deletes a plugin's
  data in Settings > Plugins (also of a plugin no longer in the app).
- UI: composables run on the main thread, must not block and may run while the plugin is off. A throw in a composable
  is not caught (Compose cannot isolate it): it ends the app. Only the three callbacks are caught.
- Status: `host.status(PluginStatus.Ok("Connected"))` is the plugin's line in Settings; `Error` after your own retries,
  `Ok` when it works again.

## Logging
Log with Timber tagged with your plugin id: `Timber.tag("home_assistant").i("...")`. Smart Sync shows its own logcat
(info and above) in Settings > About > App log, filtered by tag; another host may keep Timber's default trees only.
Never log a token, a webhook id, a URL with a path or query, or a notification's text: use `Redact.secret(value)` and
`Redact.url(url)`.
