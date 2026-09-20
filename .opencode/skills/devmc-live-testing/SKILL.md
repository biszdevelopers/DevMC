---
name: devmc-live-testing
description: Use when verifying plugin behaviour on the running DevMC test server over RCON - console commands, worldinfo probes, block/chunk inspection, log searching, and waiting for long-running operations (pregeneration, regeneration bursts). Trigger on "test on the server", "verify in-game", "run a command", "check the log".
---

# DevMC live testing over RCON

Drive the running server with the **devmc MCP** tools. Always check
`server_status` first; if it is stopped, `server_start` (or `rebuild`).

## Tools, in order of preference

- `command_batch(cmds=[...])` — several console commands over one connection.
  Prefer this over repeated `command` calls.
- `command(cmd)` — a single console command.
- `log_grep(pattern, lines)` — search the log, return only matches. Use this
  instead of `log_tail` when the log is large.
- `wait_log(pattern, timeout)` — block until a pattern appears in **new** log
  output. Use it for long operations instead of polling `log_tail`.
- `probe_chunk(cx, cz)` / `probe_block(x, y, z)` — terrain reads.
- `log_tail(lines)` — only when you truly need the raw tail.

RCON output may contain Minecraft colour codes (`§a`, `§7`); that is normal.

## The managed world

The plugin's managed world is `devmc` (a dimension, not `world/`). Its
diagnostics are on the `worldinfo` command:

- `worldinfo seed` — world/seed/generator.
- `worldinfo probe <cx> <cz>` — surface + column samples.
- `worldinfo block <x> <y> <z>` — one block.
- `worldinfo biomes` / `biome <x> <z>` / `biomecheck <step>` — biome coverage.
- `worldinfo caves <cx> <cz>` — air below y=60 (cave check).
- `worldinfo spawn` — spawn location and whether it is land.
- `worldinfo regenstatus` / `regennext` / `chunk <cx> <cz>` — regeneration state.
- `worldinfo regencheck <cx> <cz>` — zone, state, ore count, and non-air count in
  one call (use before/after a regeneration to prove terrain + resources reset).
- `worldinfo explode <cx> <cz> [power] [y]` — spawn a non-player explosion in a
  chunk to trigger the dirty-regeneration path.
- `worldinfo extract <cx> <cz> [n]` / `deplete <cx> <cz>` — simulate farming /
  force a reset.
- `worldinfo cycle <seconds|off>` — override the regeneration cycle for testing
  (also pulls already-scheduled chunks in). Same as `/worldadmin cycle`.
- `worldinfo pregen [radius|stop]` / `pregenstatus` — pregeneration control.

The `devmc` MCP exposes regeneration helpers over these: `regen_check`,
`regen_explode`, `regen_wait`, `regen_cycle`, and `regen_force`.

## Long-running operations

- Full-map pregeneration is **expensive** (vanilla terrain + features on the main
  thread; roughly tens of minutes for the whole island). Start it with
  `worldinfo pregen <radius>` and confirm completion with
  `wait_log("Generation complete", timeout=...)` rather than polling.
- Regeneration bursts are bounded per tick; check `worldinfo regenstatus`.

## Rules

- **Never run `start.bat` while the MCP server is running** — the world is
  locked (`session.lock`). Use `server_stop`/`server_start`/`restart`.
- **Stop the server when you are done** unless the user is actively testing.
- RCON has a long-command timeout; avoid commands that generate many chunks
  synchronously (e.g. scanning the whole map with `getBiome`).
- Prefer reading the world through the plugin's `worldinfo` diagnostics; they
  are cheap and avoid accidental chunk generation.
