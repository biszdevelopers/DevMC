# trueMC Resource Pack

Provides the **`minecraft:mono`** monospaced font used for equal-width socket
brackets.

- Glyph textures are from **Minecraft Seven Mono** ("mono7") by *xllifi*,
  [modrinth.com/resourcepack/mono7](https://modrinth.com/resourcepack/mono7),
  licensed [CC-BY-4.0](https://github.com/xlifi/monospace-seven/blob/main/LICENSE.md).
  Used with credit/link as required.
- `assets/minecraft/font/mono.json` defines the font directly: the mono7 ASCII
  bitmap plus a `space` advance of **8** (applied last, so it's authoritative)
  to match the 8px glyph cells — truly monospace, keeping empty sockets aligned
  with filled ones.

## Install

1. Build: `.\scripts\build-pack.ps1` → `resource-pack.zip`.
2. Load it:
   - **Server:** host the zip and set `resource-pack=<url>` (optionally
     `resource-pack-sha1=<hash>`) in `server.properties`, **or**
   - **Client:** place `resource-pack.zip` in your `resourcepacks` folder and
     enable it.

The socket brackets use `minecraft:mono`; without the pack loaded the font is
missing and socket text shows as boxes — so the pack must be active. The plugin
can serve and auto-send it via `config.yml` → `server.resource-pack` (`enabled`,
`port`, `url`, `required`).
