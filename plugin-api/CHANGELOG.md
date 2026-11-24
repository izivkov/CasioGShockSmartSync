# plugin-api changelog

Coordinates: `com.github.ExcuseMi:CasioGShockSmartSync:plugin-api-v<version>` (JitPack names the one published module after the repository; a commit works too: `...:CasioGShockSmartSync:<commit>`) (JitPack). Tags `plugin-api-v<version>`.
While the major is 0, a new minor may break plugins (the host needs the same minor); see `PluginApi.compatible`.

## 0.1.0 (not tagged yet: after the review)
- `WatchPlugin`: id, name, version, apiVersion, access (`PluginAccess`: reads, controls, sendsTo, stores, requires,
  uses), icon (`PluginIcon`), activate, deactivate, onEvent, ui. `PluginUi`: status line, settings screen, Actions row,
  `opensOnTurnOn`. The host draws the cards, the on/off switch and the details.
- `PluginHost`: watches with capabilities, connected/current watch, names, `notify` and `command` through the host's
  watch queue (`Delivery.Now` / `NextConnection`), value keys, last values, settings snapshot and catalog, typed
  history by range, plugin and per-watch storage, moving a re-paired watch's data, needs, status. No call throws:
  `PluginResult` (`Ok` / `Failed` with `FailReason`, including `NOT_DECLARED`).
- `WatchEvent`: connected, disconnected, values updated (with what was read now), health read (range), workout
  stored, button pressed, setting changed, condition, background started/stopped, watch removed.
- `Capability` (including `ALWAYS_CONNECTED`), `WatchSupport` per watch, `DataKind` with day, hour, heart rate, night
  (stage kinds) and workout records (`distanceM` only when proven, `unconfirmed` property names).
- `Redact` for log lines. Logging is Timber with the plugin id as tag (README).
- `Delivery.NextConnection` entries are kept in memory (not across an app restart).
