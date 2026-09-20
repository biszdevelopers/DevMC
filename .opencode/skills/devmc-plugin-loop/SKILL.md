---
name: devmc-plugin-loop
description: Use when building, compiling, deploying, or restarting any DevMC plugin (plugin-worldgen, plugin-bundler, plugin-items, plugin-currency, plugin-enchants, plugin-combat, plugin-smp), or when the server needs the new jar. Covers the devmc MCP build/rebuild tools, reactor order, and the one-instance server lock rule.
---

# DevMC plugin build/deploy loop

The workspace is a Maven reactor at `Y:\projects\DevMC`; the test server lives at
`Y:\Games\Minecraft\devServer`. Use the **devmc MCP** tools instead of shelling
out to Maven, so build output stays small.

## The one-call loop

```
rebuild(module="plugin-worldgen")  # build -> deploy jar -> stop -> start -> wait ready
```

`rebuild` aborts before deploying if the build fails, so a broken build never
replaces a working jar. It returns a short summary: the Maven result line, the
deployed jar and size, and `stopped/started/ready`.

## Build only (token-efficient)

```
build(module="plugin-worldgen")     # returns BUILD SUCCESS/FAILURE + error lines only
build(module="all")                 # whole reactor
build(module="plugin-worldgen", clean=true, skip_tests=false)
```

Never paste raw `mvn` output; `build` already condenses it to the result and the
`[ERROR]` lines.

## Rules and gotchas

- **One server instance only.** The MCP starts the server detached; if it is
  already running, launching `start.bat` fails with a `session.lock` error. Use
  `server_status` first, and let `rebuild`/`restart` own the lifecycle.
- **Jars are named `plugin-<name>-<version>.jar`** (e.g. `plugin-worldgen-26.2.jar`).
  `rebuild` picks the newest non-`original-`/sources/javadoc jar automatically.
- **Deploying while the server runs can lock the jar.** `rebuild` stops the
  server first, so prefer it over `deploy_jar` + manual restart.
- **Reactor order** (dependencies must be installed first):
  `plugin-bundler` → `plugin-currency` / `plugin-items` → `plugin-enchants` →
  `plugin-combat` → `plugin-smp` → `plugin-worldgen`. Use `install` when another
  plugin depends on the module you changed.
- **Verify after restart**: `log_grep("WorldGen enabled|BUILD FAILURE|Exception")`
  or `server_status`.

## If the server will not start

- `server_status` — is it already up?
- `log_grep("session.lock|Failed to start")` — another instance holds the world.
- `server_stop` then `server_start`.
