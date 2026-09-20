# World generation

Status: authoritative. This describes how the `world` plugin shapes the managed
world. It replaces the earlier custom-terrain "rust map" generator.

## 1. Goals

- **Vanilla terrain.** Hills, mountains, oceans, rivers, aquifers, and real caves
  come from the vanilla generation pipeline.
- **A bounded island.** The playable world is an island of about `size` blocks
  across (default 2048), with a coast and ocean ring inside the world border.
- **Every configured biome appears.** Biome placement follows the vanilla
  climate, but is guaranteed to include the whole configured set.
- **Deterministic.** Generation is a pure function of the world seed, so the
  scratch regeneration world matches the live world exactly.

## 2. Terrain pipeline

`IslandWorldGenerator extends ChunkGenerator` delegates every vanilla stage and
then applies a coastal mask:

1. `shouldGenerateNoise() = true` — vanilla noise fills the chunk.
2. `shouldGenerateSurface() = true` — vanilla surface places biome surface
   blocks (and bedrock).
3. `generateSurface(...)` (our hook) runs **after** vanilla surface and applies
   `IslandMask`: only in the outer band, each column is vertically compressed
   toward the sea floor and flooded to sea level. Material order is preserved,
   so vanilla surface layers simply move down. Core columns are untouched.
4. `shouldGenerateCaves() = true` — vanilla carvers carve the compressed
   terrain, so caves are real.
5. `shouldGenerateDecorations()/Structures()/Mobs() = false` — the plugin's
   feature pass still owns ores, plants, animals, POIs, and monuments.

`IslandMask` geometry:

- `islandRadius = size / 2 - ocean_margin`
- `landRadius = islandRadius * (1 - coast_fraction)` (default coast 15%)
- `falloff(x, z)` is `1` inside `landRadius`, smoothly reaches `0` at
  `islandRadius`, and is `0` beyond. The compressed column height is
  `seaFloor + (y - seaFloor) * falloff` above the sea floor.

## 3. Biomes

`ClimateBiomeProvider extends BiomeProvider` overrides
`getBiome(WorldInfo, x, y, z, BiomeParameterPoint)`:

1. **Ocean ring.** If `falloff(x, z)` is in the carved-ocean band, return
   `OCEAN`/`DEEP_OCEAN` so the sculpted coast reads as sea.
2. **Natural water.** Otherwise, if the vanilla `continentalness`/`depth` says
   ocean, return `OCEAN`/`DEEP_OCEAN`. Oceans and rivers are never moved.
3. **Land.** The vanilla temperature and humidity are augmented by large-scale,
   **domain-warped, perpendicular gradients plus fractal (multi-octave) noise**.
   The gradients span the island, so the adjusted climate visits the full range
   and every configured biome appears; the fractal noise makes biome borders
   contours of a noisy field — organic and irregular, like real borders.
   `BiomeClimateTable` then picks the nearest configured biome by temperature,
   humidity, and erosion. Erosion is left vanilla, so mountain biomes
   (`windswept_hills`, `stony_peaks`) only win where the terrain is actually
   high.

`getBiomes(...)` returns the configured set plus `OCEAN`/`DEEP_OCEAN`.

## 4. Configuration

| Key | Meaning |
|---|---|
| `worldgen.island.enabled` | Use the island generator (else vanilla `BareboneGenerator`). |
| `worldgen.island.size` | Map diameter in blocks. |
| `worldgen.island.coast_fraction` | Fraction of the radius used for the coast. |
| `worldgen.island.ocean_margin` | Ocean blocks between the coast and the border. |
| `worldgen.island.sea_level` | Sea level used by the mask. |
| `worldgen.biomes` | Biomes guaranteed to appear. |
| `worldgen.climate.sweep` | How far the map spans the climate range. |
| `worldgen.climate.scale` | Base frequency of the climate noise (smaller = larger biomes). |
| `worldgen.climate.warp` | Domain warp strength before sampling, in blocks. |
| `worldgen.climate.octaves` | Fractal octaves in the climate field. |

Default biome set: `plains, forest, birch_forest, taiga, snowy_plains, desert,
savanna, jungle, swamp, badlands, windswept_hills, stony_peaks`. Badlands and a
mountain biome are required so the gold/emerald ore filters in `OreReseeder`
have somewhere to apply.

## 5. Regeneration

The generator, mask, and biome provider are pure functions of the seed and
coordinates, so `ScratchRegenerator` produces identical terrain and biomes. The
existing diff-based chunk restore and per-tick budget are unchanged.
