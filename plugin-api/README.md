# plugin-api

The interface between Smart Sync (the host) and its plugins (Home Assistant, Health Connect, a TRMNL webhook, ...).
Pure Kotlin plus Android `Context` and Compose for a plugin's screens. No Casio, Home Assistant or app classes.

Coordinates (JitPack): `com.github.ExcuseMi.CasioGShockSmartSync:plugin-api:plugin-api-v<version>`. Changes: `CHANGELOG.md`.

## A plugin
- Implements `WatchPlugin` (public no-argument constructor) and lists it in
  `src/main/resources/META-INF/services/org.avmedia.smartsync.plugin.api.WatchPlugin`.
- `apiVersion = PluginApi.VERSION` (compiled in). The host runs it only when `PluginApi.compatible(...)`: same major,
  and while the major is 0 the same minor.
- Depends on `plugin-api` only.

## Declaration (`WatchPlugin.access`)
| Field | Meaning | Example (Home Assistant) |
|---|---|---|
| `reads` | `DataUse` kinds it reads | all |
| `controls` | `Control`s it may use | notifications, watch settings, watch face, button press |
| `sendsTo` | where data goes, plain words | "your Home Assistant server" |
| `stores` | what it keeps itself | "Home Assistant address and token" |
| `requires` | `Capability`s a watch must have | none (any watch) |
| `uses` | `Capability`s used where present | notifications, health log, battery, settings, ... |

The person sees this in Settings > Plugins (and once before turning a plugin on). The host keeps to it:
- values, history and events of an undeclared `DataUse` are not served (`Failed(NOT_DECLARED)` for a call, nothing for an event);
- `notify` and `command` need their `Control` (`watch_face` and `custom_slot` are `WATCH_FACE`);
- per watch: all of `uses` = full, `requires` met = partial, `requires` missing = not supported (no events, calls give
  `Failed(NOT_SUPPORTED)`). Capabilities only, never model names: a new model with the same capabilities works.

## Calls and events
- No call throws: a call that can fail returns `PluginResult` (`Ok(value)` / `Failed(reason, message)`).
- Writes go through the host's one watch queue: one at a time, never during a health or workout transfer, round robin
  between plugins, with a time limit. `Delivery.NextConnection` keeps a write for a watch that connects by its button.
- Each plugin has its own ordered, bounded event queue on its own coroutine. A throw or a time limit in a callback is
  caught and counted; repeated failures turn the plugin off (shown in Settings, the switch turns it on again).
- Report your own trouble with `host.status(PluginStatus.Error(...))` after your own retries, `Ok` when it works again.

## Logging
Log with Timber tagged with your plugin id: `Timber.tag("home_assistant").i("...")`. Smart Sync shows its own logcat
(info and above) in Settings > About > App log, filtered by tag; another host may keep Timber's default trees only.
Never log a token, a webhook id, a URL with a path or query, or a notification's text: use `Redact.secret(value)` and
`Redact.url(url)`.
