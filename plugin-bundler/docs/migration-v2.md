# Migrating from the legacy Bundler

This is a clean break for Bundler's own modules; there are no compatibility adapters.

| Legacy type | v2 replacement |
| --- | --- |
| `JSON` | `BundlerPlugin.instance().jsonDatabase()` for mutable JSON files |
| `Profile` | `ProfileService` and immutable `PlayerProfile` |
| `Locale` / `LocaleSession` | `LocaleService` and `LocalizedAudience` |
| `ClassicMenu`, `Menu2`, templates | `MenuManager`, `SinglePageMenuTemplate`, `PagedMenuTemplate<T>`, `MenuSession` |
| `NBT` | `ItemDataService` and Bukkit persistent data containers |
| `TPSUtils` / `TPSService` | `ServerHealthService` |
| `EventRegistery`, `TaskDelay` | Bukkit APIs or module-owned schedulers |
| Legacy command base | Standard commands declared in each plugin's `plugin.yml` |

The legacy debug command set, direct NMS helpers, raw packet broadcasts, and generated docs are intentionally removed. The legacy menu behavior now has a class-based replacement; see [menus.md](menus.md). File-backed JSON persistence remains available through `BundlerPlugin.instance().jsonDatabase()`.
