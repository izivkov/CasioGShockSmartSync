# plugin-api changelog

Coordinates: `com.github.ExcuseMi:CasioGShockSmartSync:plugin-api-v<version>` (JitPack names the one published module after the repository; a commit works too: `...:CasioGShockSmartSync:<commit>`) (JitPack). Tags `plugin-api-v<version>`.
Until 1.0 a new patch only adds and a new minor may break; a host runs a plugin of its own minor whose patch is not
newer than its own. What may be added: README "What may change in a compatible release". See `PluginApi.compatible`.

## 0.1.0 (not tagged yet: after the second review)
- `WatchPlugin`: id, name, summary, version, apiVersion, access (`PluginAccess`: reads, controls, sendsTo, stores,
  requires, uses, androidPermissions), icon (`PluginIcon`), activate, deactivate, onEvent, ui, permissionIntent,
  missingPermissions. Limits stated in KDoc (15 s, 30 s, 5 s, 5 failures). `PluginUi`: settings screen, Actions row,
  `opensOnTurnOn` (every turn-on, once active). The host draws the cards, the on/off switch and the details page.
- `PluginHost`: watches with capabilities, connected/current watch, names, `notify` and `command` through the host's
  watch queue (`Delivery.Now` / `NextConnection`), `valueCatalog()` (`ValueInfo`: key, description, `DataUse`,
  `ValueType`, unit), last values, settings snapshot by `SettingInfo.key` and catalog (`SettingInfo` with label and
  control), typed history by half-open range, plugin and per-watch storage (`putAll`), permissions (missing, request),
  needs, status. No call throws: `PluginResult` (`Ok` / `Failed` with `FailReason`, including `NOT_DECLARED` and
  `STOPPED`). Revoked after deactivate.
- `WatchEvent`: connected, disconnected, values updated (with what was read now), health read (range), workout
  stored, button pressed, setting changed, background started/stopped (likely to go), watch removed, watch re-paired,
  events lost, permissions changed, wake.
- `PluginNeeds`: values, buttonPress, healthReads, wakeEveryMinutes.
- `DataUse` (including `BODY_PROFILE`, `PHONE_BATTERY`, `PHONE_LOCATION`), `Control`, `Capability` (including
  `ALWAYS_CONNECTED`), `DataKind` with day, hour, heart rate, night (stage kinds) and workout records (`distanceM` only
  when proven, `unconfirmed` property names).
- `Redact` for log lines. Logging is Timber 5.x with the plugin id as tag (README).
- Changed by the first review (2026-11-24), before the tag: removed `moveWatchData` (the host finds a re-paired watch),
  `valueKeys()`, `keepConnected`, `undecodedSettings`, `WatchEvent.Condition`, `ValueCause.wire`, `WatchSupport`,
  `PluginUi.statusLine()`, `DataUse.words`, `Control.words`, `DataUse.ofValueKey`, `Control.ofSetting`,
  `PluginResult.failed`.
- Changed by the second review (2026-11-24), before the tag: `WatchPlugin.missingPermissions` is `suspend` (the host
  caches the answer); `PluginNeeds.settings`; `ValueType.LOCAL_DATE_TIME` (7 keys moved to it, `DATE_TIME` always has
  an offset); `WatchPlugin.description`, `setupNeeds`, `infoUrl`, `sourceUrl`, `issuesUrl`, `author`, `license`;
  `DataUse.ALL`, `DataUse.WATCH`, `Control.ALL`. KDoc: location may be old, `WATCH_SETTINGS` covers the profile rows,
  `SLEEP` carries the night's heart rate, `PHONE` names its values, no constructor or `copy()` of host records.
