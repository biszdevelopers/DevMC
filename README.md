# NewServer
DevClub MC

## Build

```shell
mvn clean package
```

Reactor order: `plugin-bundler` → `plugin-items`/`plugin-currency` →
`plugin-enchants` → `plugin-combat` → `plugin-smp`.

## Documentation

- [Enchanting, socket & XP system](docs/enchanting.md)
- [Item lore display standard](docs/item-lore-standard.md)
