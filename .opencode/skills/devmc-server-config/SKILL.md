---
name: devmc-server-config
description: Use when changing DevMC plugin settings, when a config change does not take effect, or when bundled defaults are not applied. Covers ServerData JSON config, the merge-defaults behaviour, and when a restart is required.
---

# DevMC server config

Runtime config lives under the server's ServerData directory:

```
Y:\Games\Minecraft\devServer\ServerData\<plugin>\...
```

For the worldgen plugin that is `ServerData\worldgen\settings.json`, plus data
files (`wilderness.json`, `state.json`, `snapshots.json`, `structures.json`,
`loot.json`).

## Behaviour you must know

- **Settings load once, on plugin enable.** Editing `settings.json` does nothing
  until a restart. Use the `restart` (or `rebuild`) MCP tool.
- **Defaults are merged, not replaced.** On enable the plugin calls
  `JSON.mergeMissingDefaults(file, bundledDefaults)`, which only **adds missing
  keys**. Existing keys keep their current values, and keys removed from the code
  stay behind harmlessly. So changing a default in code will not change an
  existing server file.
- **To adopt a new default**, either edit the value in `ServerData\...` directly,
  or delete the file and restart (it is recreated from the bundled defaults).
- **`/worldadmin reload` reloads data, not settings.** It re-reads chunk
  indicators and POI definitions; it does not re-read `settings.json`.

## Changing a setting

1. Edit the value in the ServerData file (or delete the file to take defaults).
2. `restart` the server (or `rebuild` if the plugin also changed).
3. Verify with a diagnostic, e.g. `worldinfo regenstatus` or `/worldadmin diag`.

## Common knobs (worldgen plugin)

- `regen.cycle_seconds`, `regen.depletion_nodes`, `regen.grace_seconds` —
  regeneration timing and trigger.
- `regen.player_radius_chunks`, `regen.chunks_per_tick`,
  `regen.budget_millis_per_tick` — safety limits.
- `worldgen.*` — island/biome generation.
