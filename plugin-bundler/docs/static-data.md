# JSON locale data

Bundler installs locale defaults into `D:/ServerData/lang` only when a file is missing, so server edits survive rebuilds and restarts. These are UTF-8, flat JSON objects matching the legacy Bundler format.

Server messages live in `en_us.json`, `zh_cn.json`, `zh_tw.json`, `ja_jp.json`, `ko_kr.json`, and `es_es.json`. The corresponding `*_mojang.json` files contain the official Minecraft 26.2 language mappings; `zh_cn_mojang.json` is included. Missing server-message keys fall back to `zh_cn`, while missing Mojang keys fall back to `en_us_mojang`.

Use `/locale reload` with `bundler.admin` after editing locale files. Players select a persisted language with `/locale <language>` or `/lang <language>`.

The older versioned catalogs under `plugins/Bundler/catalogs` remain available for non-player static module data.
