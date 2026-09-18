package dev.bisz.world.structure;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.integration.BlockRegion;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * File-backed structure previews. Opening a structure from the admin book
 * pastes it into the storage/testing dimension at its own slot; the operator
 * edits it there and saves it back to a schematic file, which is registered as
 * a custom structure definition.
 */
public final class StructurePreviewService {

  /** The structure currently being previewed by a player. */
  public record Preview(
    String entryId,
    String name,
    boolean vanilla,
    PlacedStructure bounds,
    Path file,
    Location origin
  ) {}

  private static final int SLOT_SPACING = 512;
  private static final int VANILLA_MARGIN_XZ = 48;
  private static final int VANILLA_MARGIN_DOWN = 16;
  private static final int VANILLA_MARGIN_UP = 80;

  private final WorldPlugin plugin;
  private final Map<UUID, Preview> previews = new HashMap<>();
  private int slot;

  public StructurePreviewService(WorldPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  /** Opens (or re-opens) a structure preview for a player. */
  public Preview open(Player player, String id) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(id, "id");
    World admin = plugin.adminDimension().ensure();
    if (admin == null) {
      player.sendMessage("§cCould not open the test dimension.");
      return null;
    }
    Location origin = nextOrigin(admin);
    StructureDefinition definition = plugin.structures().registry().get(id);
    PlacedStructure bounds;
    Path file;
    boolean vanilla;
    String name;
    if (definition != null) {
      Path existing = plugin.structures().registry().fileFor(definition);
      Object handle = plugin
        .structures()
        .bridge()
        .load(definition, existing);
      if (handle == null) {
        player.sendMessage("§cCould not load §f" + id + "§c.");
        return null;
      }
      bounds = plugin
        .structures()
        .bridge()
        .paste(
          handle,
          admin,
          origin,
          definition.rotationY(),
          definition.ignoreAir(),
          definition.copyEntities()
        );
      if (bounds == null) {
        player.sendMessage("§cCould not paste §f" + id + "§c.");
        return null;
      }
      file = existing;
      name = definition.name();
      vanilla = false;
    } else {
      VanillaStructure entry = plugin.vanillaStructures().get(id);
      if (entry == null) {
        player.sendMessage("§cNo structure named §f" + id + "§c.");
        return null;
      }
      if (!plugin.adminDimension().placeVanillaStructure(id, origin)) {
        player.sendMessage("§cCould not place §f" + id + "§c.");
        return null;
      }
      bounds = new PlacedStructure(
        admin.getName(),
        origin.getBlockX() - VANILLA_MARGIN_XZ,
        origin.getBlockY() - VANILLA_MARGIN_DOWN,
        origin.getBlockZ() - VANILLA_MARGIN_XZ,
        origin.getBlockX() + VANILLA_MARGIN_XZ,
        origin.getBlockY() + VANILLA_MARGIN_UP,
        origin.getBlockZ() + VANILLA_MARGIN_XZ
      );
      file = plugin
        .structures()
        .registry()
        .directory()
        .resolve(safeName(id) + ".schem");
      name = entry.name();
      vanilla = true;
    }
    Preview preview = new Preview(id, name, vanilla, bounds, file, origin);
    previews.put(player.getUniqueId(), preview);
    player.teleport(origin.clone().add(0.5, 6.0, 0.5));
    player.sendMessage(
      "§aPreviewing §f" +
      name +
      "§a. Edit it, then §f/structure save§a to write it to §f" +
      file.getFileName() +
      "§a."
    );
    return preview;
  }

  /**
   * Saves the current preview to its schematic file. The player's WorldEdit
   * selection is used as the capture bounds when present, otherwise the bounds
   * recorded when the preview was opened.
   */
  public boolean save(Player player) {
    Preview preview = previews.get(player.getUniqueId());
    if (preview == null) {
      player.sendMessage("§cNo structure preview is open.");
      return false;
    }
    if (!plugin.structures().bridge().available()) {
      player.sendMessage("§cNo structure backend; WorldEdit is required.");
      return false;
    }
    PlacedStructure bounds = preview.bounds();
    BlockRegion selection = plugin.selection().blockSelection(player);
    if (selection != null && selection.world().equals(bounds.world())) {
      bounds = new PlacedStructure(
        selection.world(),
        selection.minX(),
        selection.minY(),
        selection.minZ(),
        selection.maxX(),
        selection.maxY(),
        selection.maxZ()
      );
    }
    World world = Bukkit.getWorld(bounds.world());
    if (world == null) {
      player.sendMessage("§cThe preview world is not loaded.");
      return false;
    }
    if (!plugin.structures().bridge().save(world, bounds, preview.file())) {
      player.sendMessage("§cCould not write the schematic file.");
      return false;
    }
    String relative = relativeName(preview.file());
    if (preview.vanilla()) {
      registerFromVanilla(preview, relative);
    }
    previews.remove(player.getUniqueId());
    player.sendMessage(
      "§aSaved §f" + preview.name() + "§a to §f" + relative + "§a."
    );
    return true;
  }

  /** Discards the current preview. */
  public boolean cancel(Player player) {
    Preview preview = previews.remove(player.getUniqueId());
    if (preview == null) return false;
    player.sendMessage("§eDiscarded preview of §f" + preview.name() + "§e.");
    return true;
  }

  /** The player's current preview, or null. */
  public Preview active(Player player) {
    return previews.get(player.getUniqueId());
  }

  private void registerFromVanilla(Preview preview, String relative) {
    VanillaStructure entry = plugin.vanillaStructures().get(preview.entryId());
    StructureType type = entry == null ? StructureType.POI : entry.type();
    List<String> biomes = entry == null ? List.of() : entry.biomes();
    StructureDefinition definition = new StructureDefinition(
      safeName(preview.entryId()),
      preview.name(),
      relative,
      type.name().toLowerCase(Locale.ROOT),
      type,
      biomes,
      null,
      0L,
      0,
      false,
      true,
      null,
      type == StructureType.MONUMENT
    );
    plugin.structures().registry().put(definition);
  }

  private Location nextOrigin(World admin) {
    Location base = plugin.adminDimension().stagingOrigin();
    long offset = (long) (++slot) * SLOT_SPACING;
    return new Location(
      admin,
      base.getBlockX() + offset,
      base.getBlockY(),
      base.getBlockZ()
    );
  }

  private static String relativeName(Path file) {
    Path directory = file.getParent();
    if (directory != null) {
      try {
        return directory
          .relativize(file.toAbsolutePath().normalize())
          .toString()
          .replace('\\', '/');
      } catch (IllegalArgumentException ignored) {
        // Fall through to the bare file name.
      }
    }
    return file.getFileName().toString();
  }

  private static String safeName(String id) {
    return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
  }
}
