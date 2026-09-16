# Enchants

trueMC's custom enchanting, socket, XP, and fishing system, integrated with the
DevMC **Bundler** and **Items** plugins on Paper/Youer 26.2.

Socket layouts and the enchantment catalog are hardcoded Java tables; costs,
fishing loot, and display/XP settings are JSON-backed through Bundler's
ServerData store (`D:/ServerData/enchants`). Item state is stored through the
Items PDC.

See [docs/enchanting.md](../docs/enchanting.md) for the full specification.

## Commands

`/enchants <give|info|xp|reload>` — permission `enchants.admin` (OP + Bundler
`ADMIN`).

## Build

Build the whole suite from the repository root:

```shell
mvn clean package
```
