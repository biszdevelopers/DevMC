# Minecraft Development Launcher

A Windows-first, dependency-free Tkinter launcher for the local Mohist/Spigot 1.20.1 plugin development loop.

## Start

Double-click `launch.bat`, or run:

```powershell
python main.py
```

The launcher defaults to:

- Mohist source: `%USERPROFILE%\Downloads\server.jar`
- Spigot source: `%USERPROFILE%\Downloads\spigot-1.20.1.jar`
- Plugin project root: `D:\NewServer`
- Java: `C:\Program Files\Java\jdk-17\bin\java.exe`
- Initial/maximum heap: 1 GB / 4 GB

## Workflow

1. Choose the isolated Mohist or Spigot server profile.
2. Adjust initial/maximum memory, optional JVM flags, and server arguments. The exact Java command updates in the read-only preview.
   Enable **Java debug mode** to add a local-only JDWP listener (default port `5005`), then create an IntelliJ **Remote JVM Debug** configuration for host `127.0.0.1` and that port. Select **Suspend server until debugger connects** when you need to debug startup; otherwise it starts normally and supports standard HotSwap replacements for compatible method-body changes.
   Choose **Online mode** or **Offline mode** for each server profile. Offline mode writes `online-mode=false` before launch and is intended for local development; do not expose an offline-mode server to untrusted players.
   Use **Dimensions** to keep the required Overworld (`0`) and selectively enable Nether (`-1`) and The End (`1`). The launcher writes `allow-nether` and Bukkit's `settings.allow-end` before launch; disabled dimension folders remain on disk for later re-enable.
3. Accept the Minecraft EULA for that profile.
4. Open **Plugins**, refresh discovery, and assign each project a mode:
   **Enabled** participates in incremental build and deployment, **Inactive** keeps its currently installed launcher-managed JAR without building or replacing it, and **Disabled** removes its launcher-managed JAR from the server.
5. Click **Build, Deploy & Run**. With **On launch, only build changed plugins** enabled, the launcher fingerprints relevant sources and build files, rebuilds changed projects and their local dependants, reuses verified build outputs for everything else, and avoids rewriting JARs that are already current. It then streams the server console.

Spigot is imported directly from the selected local jar; it does not invoke BuildTools or require the launcher to use a network proxy. The default source is `%USERPROFILE%\Downloads\spigot-1.20.1.jar`. Use **Import/Reimport Spigot** after replacing that download.

Configuration files are isolated per profile and become available in **Configurations** after that server has generated them on its first launch. **Build & Test** runs project test suites without deploying or launching.

The **Configurations** area gives `server.properties` its own frequent-use editor and keeps `bukkit.yml` and `spigot.yml` in separate Advanced tabs. Each editor has independent reload, save, scrolling, and unsaved-change tracking.

Use **Locale Editor** to browse and edit every JSON language file in `D:\ServerData\lang`. Select a file, double-click a translation value to edit it, then save that file or all changed files. The editor loads one language file at a time, so the larger `_mojang.json` reference files remain practical to inspect and edit.

Use **Economy** to inspect the session journals in `D:\ServerData\transactions\currency`.
Select an individual server session to review its created and destroyed Nits, net
change, gross flow, transaction counts, active players, daily and reason breakdowns,
and per-player activity. **Overall & Trends** reports all-time totals, current purse
supply, flow-to-supply ratio, concentration, and balance inequality alongside
chronological session charts for net change, creation and destruction, volume, and
transaction count. Malformed transaction entries are skipped and reported without
preventing the remaining history from loading.

Use **Menu Maker** to design a one-to-six-row Bundler chest GUI against the bundled
Minecraft 1.20.1 textures. Choose single-page, paged-content, or paged-storage mode;
each mode exposes only its compatible static, storage, content, and navigation roles.
Configure literal or localized item text and
wrapping, and copy the validated Java method from the live preview. Paged mode keeps a
base layout plus per-page static overrides. The generator never writes plugin sources.
All required models and textures are versioned under `assets/minecraft`; the finished
runner does not read the Downloads folder.

Single-page storage cells target exact `StorageProvider` indices. Paged-storage cells
define an ordered viewport; page changes automatically render consecutive provider
excerpts without a 54-item data limit. Generated storage templates bind their provider
in the builder, while Bukkit inventories remain available through
`StorageProvider.fromInventory(...)`.

Configured items can be copied and pasted between cells without losing their material,
amount, localized text, ordered lore, wrapping, or generated click callback. Hover an
item in the chest preview to see a Minecraft-style name and lore tooltip. The Tooltip
language selector resolves locale keys and applies the same fixed or language-aware
wrapping used by the generated Bundler menu.

After clicking a chest cell, use `Ctrl+C` and `Ctrl+V` to copy or paste its item, or
`Delete` to clear the cell. These shortcuts are scoped to the chest preview so normal
copy and paste continue to work in title, item-name, lore, and generated-code fields.
Arrow keys navigate the focused chest. `Alt+S` marks the selected cell as storage and
assigns its next provider/viewport order; `Alt+C` does the same for paged content.
Bulk tools fill every empty cell as ordered storage/content in horizontal or vertical
order, or replace all cells from a complete item-configuration popup. Irrelevant item,
storage, and content controls are disabled for the selected role.

Reusable item and full-menu presets are stored as schema-versioned JSON. Tracked
built-ins demonstrate black-glass filler, a localized close barrier, and a hollow
six-row menu; personal presets live under `data/menu-presets`. Built-ins are
read-only but can be duplicated with **Save As**. Menu presets retain the complete
layout, callbacks, and exact Java source.

The Java pane is an editable, syntax-highlighted workspace with line numbers, brace
and current-line feedback, undo/redo, indentation, `Ctrl+/` comments, and `Ctrl+F`
search. Callback edits inside marker regions stay synchronized with the canvas.
Structural Java edits enter **Detached** mode, protecting hand-written code from
visual changes until **Regenerate** is chosen. `Ctrl+S` updates the active personal
menu preset or opens Save As for an unnamed design.

Bundler uses its file-backed JSON database at `D:\ServerData`; the launcher requires no database credentials or JVM database options.

Runtime data, server files, settings, and launcher logs are stored under `data/` and intentionally ignored by Git.

## Supported projects

Discovery requires both:

- `plugin.yml` or `paper-plugin.yml` in the project root or `src/main/resources`; and
- `pom.xml`, `build.gradle`, or `build.gradle.kts`.

Projects with their own Maven or Gradle wrapper use it. Wrapper-less projects use the build tool from `PATH`, or a compatible wrapper from a sibling project under the same plugin root.

Right-click any Maven project in the **Plugins** table to run Clean, Compile, Test, Package, Verify, Install, Dependency Tree, or a fast Clean & Install operation for that project. Output streams to the Console without deploying the resulting JAR.

The plugin Status column distinguishes source changes, local dependency changes, missing build output, and profile-specific installation changes. Use **Force rebuild selected** or the right-click force option when you deliberately need a clean rebuild. Fingerprints and successful artifact hashes are stored in `data/build-state.json`; generated `target`, `build`, IDE, Git, log, and test-output files do not make a plugin dirty during normal launch.

Maven projects are ordered by discovered inter-project coordinates. A selected project cannot launch without another discovered plugin project it declares as a dependency.

## Tests

```powershell
python -m unittest discover -v
```
