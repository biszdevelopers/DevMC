# Bundler

Bundler is a Java 25, Paper/Youer 26.2 shared-services plugin. Dependent plugins use its concrete classes directly.

## Build

Run `./mvnw clean verify`. Bundler creates and uses the file-backed JSON database at `D:/ServerData` by default. Change `storage.directory` in Bundler's `config.yml` if the server data belongs elsewhere.

Player profiles use the legacy `players.json` structure and moderation uses `banned.json`. `PlayerProfile` exposes an immutable custom-metadata snapshot; use `ProfileService.setMetadata(playerId, key, value)` to atomically write JSON-compatible metadata, or pass `null` to remove it. Metadata is stored alongside the legacy fields, while standard profile keys (`name`, `displayName`, `nameHistory`, `rank`, `level`, `lang`, and `subscription`) are reserved. Dependent plugins can directly use `BundlerPlugin.instance().jsonDatabase()` and its `loadDataFromDataBase`, `saveDataFromDataBase`, or atomic `updateObject` methods.

The legacy chat/string helper has been migrated as `dev.bisz.chat.ChatUtils`. It provides system and broadcast message prefixes, clickable/hoverable `TextComponent` helpers, color-aware wrapping, and the retained basic formatting utilities.

Editable server translations and the Minecraft 26.2 Mojang language mappings live in `D:/ServerData/lang`. Players use `/locale` (or `/lang`) to open the flag-head language selector, or `/locale <code>` to change directly. Administrators can reload edited files with `/locale reload`.

The concrete menu runtime is available from `BundlerPlugin.instance().menuManager()`.
Use `SinglePageMenuTemplate`, list-backed `PagedMenuTemplate<T>`, or provider-backed
`PagedStorageMenuTemplate`. Content and storage pagination are deliberately separate
concrete types. Storage providers, localized/wrapped items, callbacks, live session
mutation, and tagged-item cleanup are documented in [menus.md](docs/menus.md).

## Direct integration

```java
LocaleService locales = BundlerPlugin.instance().localeService();
locales.translate(locales.audience(player), "item.example.name");
locales.minecraft(player, "item.minecraft.diamond");
```

See [architecture.md](docs/architecture.md), [menus.md](docs/menus.md), [static-data.md](docs/static-data.md), and [migration-v2.md](docs/migration-v2.md).
