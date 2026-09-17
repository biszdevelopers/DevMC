package dev.bisz.city.wilderness;

import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.World;

/** Regenerates chunks through WorldEdit, preserving the world seed. */
public final class WorldEditChunkRegenerator implements ChunkRegenerator {

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

  @Override
  public boolean regenerate(World world, int chunkX, int chunkZ) {
    Objects.requireNonNull(world, "world");
    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
    com.sk89q.worldedit.EditSession session =
      WorldEdit.getInstance().newEditSession(weWorld);
    try {
      RegenOptions options = RegenOptions.builder().build();
      return weWorld.regenerate(
        chunkRegion(weWorld, chunkX, chunkZ),
        session,
        options
      );
    } finally {
      session.close();
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
    return "WorldEdit";
  }
}
