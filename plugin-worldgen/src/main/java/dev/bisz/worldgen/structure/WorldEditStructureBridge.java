package dev.bisz.worldgen.structure;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Location;
import org.bukkit.World;

/** WorldEdit-backed structure loading, pasting, and clearing. */
public final class WorldEditStructureBridge implements StructureBridge {

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public Object load(StructureDefinition definition, Path file) {
    ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
    if (format == null) return null;
    try (
      InputStream input = Files.newInputStream(file);
      ClipboardReader reader = format.getReader(input)
    ) {
      return reader.read();
    } catch (IOException exception) {
      return null;
    }
  }

  @Override
  public PlacedStructure paste(
    Object handle,
    World world,
    Location location,
    int rotationY,
    boolean ignoreAir,
    boolean copyEntities
  ) {
    if (!(handle instanceof Clipboard clipboard)) return null;
    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
    EditSession session = WorldEdit.getInstance().newEditSession(weWorld);
    try {
      ClipboardHolder holder = new ClipboardHolder(clipboard);
      AffineTransform transform = new AffineTransform();
      if (rotationY % 360 != 0) {
        transform = transform.rotateY(rotationY);
        holder.setTransform(transform);
      }
      BlockVector3 target = BlockVector3.at(
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ()
      );
      Operation operation = holder
        .createPaste(session)
        .to(target)
        .ignoreAirBlocks(ignoreAir)
        .copyEntities(copyEntities)
        .build();
      Operations.complete(operation);
      return bounds(clipboard, target, transform, world.getName());
    } catch (Throwable failure) {
      return null;
    } finally {
      session.close();
    }
  }

  @Override
  public void clear(World world, PlacedStructure bounds) {
    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
    EditSession session = WorldEdit.getInstance().newEditSession(weWorld);
    try {
      Region region = new CuboidRegion(
        weWorld,
        BlockVector3.at(bounds.minX(), bounds.minY(), bounds.minZ()),
        BlockVector3.at(bounds.maxX(), bounds.maxY(), bounds.maxZ())
      );
      session.setBlocks(region, BlockTypes.AIR.getDefaultState());
    } catch (Throwable failure) {
      // A failed clear must not break despawn bookkeeping.
    } finally {
      session.close();
    }
  }

  @Override
  public boolean save(World world, PlacedStructure bounds, Path file) {
    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
    EditSession session = WorldEdit.getInstance().newEditSession(weWorld);
    try {
      CuboidRegion region = new CuboidRegion(
        weWorld,
        BlockVector3.at(bounds.minX(), bounds.minY(), bounds.minZ()),
        BlockVector3.at(bounds.maxX(), bounds.maxY(), bounds.maxZ())
      );
      BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
      clipboard.setOrigin(region.getMinimumPoint());
      Operations.complete(
        new ForwardExtentCopy(
          session,
          region,
          clipboard,
          region.getMinimumPoint()
        )
      );
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      try (
        OutputStream output = Files.newOutputStream(file);
        ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(
          output
        )
      ) {
        writer.write(clipboard);
      }
      return true;
    } catch (Throwable failure) {
      return false;
    } finally {
      session.close();
    }
  }

  private static PlacedStructure bounds(
    Clipboard clipboard,
    BlockVector3 target,
    AffineTransform transform,
    String world
  ) {
    BlockVector3 origin = clipboard.getOrigin();
    BlockVector3 min = clipboard.getRegion().getMinimumPoint().subtract(origin);
    BlockVector3 max = clipboard.getRegion().getMaximumPoint().subtract(origin);
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (int cornerX = 0; cornerX <= 1; cornerX++) {
      for (int cornerY = 0; cornerY <= 1; cornerY++) {
        for (int cornerZ = 0; cornerZ <= 1; cornerZ++) {
          Vector3 point = transform.apply(
            Vector3.at(
              cornerX == 0 ? min.getX() : max.getX(),
              cornerY == 0 ? min.getY() : max.getY(),
              cornerZ == 0 ? min.getZ() : max.getZ()
            )
          );
          int x = target.getX() + (int) Math.floor(point.getX());
          int y = target.getY() + (int) Math.floor(point.getY());
          int z = target.getZ() + (int) Math.floor(point.getZ());
          minX = Math.min(minX, x);
          minY = Math.min(minY, y);
          minZ = Math.min(minZ, z);
          maxX = Math.max(maxX, x);
          maxY = Math.max(maxY, y);
          maxZ = Math.max(maxZ, z);
        }
      }
    }
    return new PlacedStructure(world, minX, minY, minZ, maxX, maxY, maxZ);
  }

  @Override
  public String name() {
    return "WorldEdit";
  }
}
