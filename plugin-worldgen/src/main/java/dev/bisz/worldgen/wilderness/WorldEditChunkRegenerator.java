package dev.bisz.worldgen.wilderness;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import dev.bisz.worldgen.WorldGenPlugin;
import dev.bisz.worldgen.config.WorldSettings;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.World;

/**
 * Regenerates chunks from a same-seed scratch world using WorldEdit's bulk
 * extent copy, and strips natural ores through WorldEdit.
 */
public final class WorldEditChunkRegenerator implements ChunkRegenerator {

  private final WorldGenPlugin plugin;

  public WorldEditChunkRegenerator(WorldGenPlugin plugin, WorldSettings settings) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(settings, "settings");
  }

  private static final List<BlockType> STONE_ORES = List.of(
    BlockTypes.COAL_ORE,
    BlockTypes.IRON_ORE,
    BlockTypes.COPPER_ORE,
    BlockTypes.GOLD_ORE,
    BlockTypes.REDSTONE_ORE,
    BlockTypes.LAPIS_ORE,
    BlockTypes.DIAMOND_ORE,
    BlockTypes.EMERALD_ORE
  );

  private static final List<BlockType> DEEPSLATE_ORES = List.of(
    BlockTypes.DEEPSLATE_COAL_ORE,
    BlockTypes.DEEPSLATE_IRON_ORE,
    BlockTypes.DEEPSLATE_COPPER_ORE,
    BlockTypes.DEEPSLATE_GOLD_ORE,
    BlockTypes.DEEPSLATE_REDSTONE_ORE,
    BlockTypes.DEEPSLATE_LAPIS_ORE,
    BlockTypes.DEEPSLATE_DIAMOND_ORE,
    BlockTypes.DEEPSLATE_EMERALD_ORE
  );

  private static final List<BlockType> NETHER_ORES = List.of(
    BlockTypes.NETHER_GOLD_ORE,
    BlockTypes.NETHER_QUARTZ_ORE,
    BlockTypes.ANCIENT_DEBRIS
  );

  /**
   * Overwrites the whole chunk with the scratch terrain in a single bulk
   * WorldEdit operation. This replaces the old per-block capture/compare loop,
   * which was the dominant cost of regeneration.
   */
  @Override
  public boolean regenerate(World world, int chunkX, int chunkZ) {
    Objects.requireNonNull(world, "world");
    World scratch = ScratchRegenerator.scratchWorld(plugin, world);
    if (scratch == null) return false;
    world.getChunkAt(chunkX, chunkZ);
    scratch.getChunkAt(chunkX, chunkZ);
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    int minY = world.getMinHeight();
    int maxY = world.getMaxHeight() - 1;
    com.sk89q.worldedit.world.World weSource = BukkitAdapter.adapt(scratch);
    com.sk89q.worldedit.world.World weTarget = BukkitAdapter.adapt(world);
    Region region = new CuboidRegion(
      weSource,
      BlockVector3.at(baseX, minY, baseZ),
      BlockVector3.at(baseX + 15, maxY, baseZ + 15)
    );
    EditSession session = WorldEdit.getInstance()
      .newEditSessionBuilder()
      .world(weTarget)
      .maxBlocks(-1)
      .build();
    try {
      ForwardExtentCopy operation = new ForwardExtentCopy(
        weSource,
        region,
        session,
        BlockVector3.at(baseX, minY, baseZ)
      );
      operation.setCopyingEntities(false);
      operation.setCopyingBiomes(false);
      Operations.complete(operation);
      return true;
    } catch (WorldEditException exception) {
      plugin
        .getLogger()
        .warning(
          "Terrain restore failed for " +
          chunkX +
          "," +
          chunkZ +
          ": " +
          exception.getMessage()
        );
      return false;
    } finally {
      session.close();
      scratch.unloadChunk(chunkX, chunkZ, true);
    }
  }

  @Override
  public boolean stripOres(World world, int chunkX, int chunkZ) {
    Objects.requireNonNull(world, "world");
    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
    com.sk89q.worldedit.EditSession session =
      WorldEdit.getInstance().newEditSession(weWorld);
    try {
      Region region = chunkRegion(weWorld, chunkX, chunkZ);
      boolean changed = false;
      changed |= replace(session, region, STONE_ORES, BlockTypes.STONE);
      changed |=
        replace(session, region, DEEPSLATE_ORES, BlockTypes.DEEPSLATE);
      changed |=
        replace(session, region, NETHER_ORES, BlockTypes.NETHERRACK);
      return changed;
    } finally {
      session.close();
    }
  }

  private static Region chunkRegion(
    com.sk89q.worldedit.world.World weWorld,
    int chunkX,
    int chunkZ
  ) {
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    return new CuboidRegion(
      weWorld,
      BlockVector3.at(minX, weWorld.getMinY(), minZ),
      BlockVector3.at(minX + 15, weWorld.getMaxY(), minZ + 15)
    );
  }

  private static boolean replace(
    com.sk89q.worldedit.EditSession session,
    Region region,
    List<BlockType> from,
    BlockType to
  ) {
    Set<BaseBlock> sources = from
      .stream()
      .map(type -> type.getDefaultState().toBaseBlock())
      .collect(Collectors.toSet());
    try {
      return (
        session.replaceBlocks(
          region,
          sources,
          new BlockPattern(to.getDefaultState())
        ) >
        0
      );
    } catch (MaxChangedBlocksException exception) {
      return false;
    }
  }

  @Override
  public String name() {
    return "Paper+WorldEdit";
  }
}
