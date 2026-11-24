# plugin-api changelog

Coordinates: `com.github.ExcuseMi.CasioGShockSmartSync:plugin-api:plugin-api-v<version>` (JitPack). Tags `plugin-api-v<version>`.
While the major is 0, a new minor may break plugins (the host needs the same minor); see `PluginApi.compatible`.

## 0.1.0 (2026-11-24)
- `WatchPlugin` (id, name, apiVersion, activate, deactivate, onEvent, ui) and `PluginUi` (icon, status line, settings
  screen, Actions row; the host draws the cards and the on/off switch).
- `PluginHost`: watches with capabilities, connected/current watch, watch names, notify and command through the host's
  watch queue (`Delivery.Now` / `Delivery.NextConnection`, `CommandResult`), last values, settings snapshot and catalog,
  typed history by range, plugin and per-watch storage, moving a re-paired watch's data, needs, log.
- `WatchEvent`: connected, disconnected, values updated, health read (range), workout stored, button pressed, setting
  changed, condition, background started/stopped, watch removed.
- `Capability` (including `ALWAYS_CONNECTED`), `DataKind` with day, hour, heart rate, night and workout records.
- `Delivery.NextConnection` entries are kept in memory (not across an app restart).
