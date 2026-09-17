# Currency

Paper currency plugin. Its official currency is **nits**.

## Commands

`/nits <give|remove> <amount>` adds or removes a positive whole-number amount of nits from the executing player's purse. Amounts accept mathematical expressions, including `2k * 5`, `2m`, and `50% * 200`; their final result must be a positive whole number. It is an administrator-only debug command: the player must be both a Bukkit OP and hold Bundler's `ADMIN` rank. Its permission is `currency.nits.debug` (default: OP).

## Requirements

- Java 25 or newer
- Paper API 26.2
- Bundler 2.0.0-SNAPSHOT

## Build

```shell
mvn package
```
