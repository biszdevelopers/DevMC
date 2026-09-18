package dev.bisz.world.menus;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.world.WorldPlugin;
import dev.bisz.world.structure.StructureDefinition;
import dev.bisz.world.structure.StructureType;
import dev.bisz.world.structure.VanillaStructure;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.SinglePageMenuTemplate;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Lists custom and vanilla structures with their type and biomes. Clicking an
 * entry opens a file-backed preview in the storage/testing dimension; edits are
 * written back to a schematic file with the save button.
 */
public final class StructureBookMenu {

  private StructureBookMenu() {}

  /** Opens the structure book for a player. */
  public static void open(WorldPlugin plugin, Player player) {
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      "Structure book",
      6
    );
    int slot = 0;
    for (StructureDefinition definition : plugin.structures().registry().all()) {
      if (slot >= 45) break;
      builder.item(
        slot,
        MenuItem.builder(icon(definition.type()))
          .name(definition.name())
          .lore(
            "Type: " + definition.type(),
            "Biomes: " + biomes(definition.biomes()),
            "Click to preview and edit"
          )
          .onClick(context -> open(plugin, context.player(), definition.id()))
          .build()
      );
      slot++;
    }
    for (VanillaStructure entry : plugin.vanillaStructures().all()) {
      if (slot >= 45) break;
      builder.item(
        slot,
        MenuItem.builder(icon(entry.type()))
          .name(entry.name())
          .lore(
            "Type: " + entry.type(),
            "Biomes: " + biomes(entry.biomes()),
            "Click to preview and edit"
          )
          .onClick(context -> open(plugin, context.player(), entry.id()))
          .build()
      );
      slot++;
    }
    if (plugin.previews().active(player) != null) {
      builder.item(
        49,
        MenuItem.builder(Material.LIME_DYE)
          .name("§aSave preview")
          .lore("Write your edits back to the schematic file")
          .onClick(context -> {
            plugin.previews().save(context.player());
            open(plugin, context.player());
          })
          .build()
      );
      builder.item(
        50,
        MenuItem.builder(Material.BARRIER)
          .name("§cCancel preview")
          .lore("Discard the current preview")
          .onClick(context -> {
            plugin.previews().cancel(context.player());
            open(plugin, context.player());
          })
          .build()
      );
    }
    BundlerPlugin.instance().menuManager().open(player, builder.build());
  }

  private static void open(WorldPlugin plugin, Player player, String id) {
    plugin.previews().open(player, id);
  }

  private static Material icon(StructureType type) {
    return type == StructureType.MONUMENT ? Material.BEACON : Material.CHEST;
  }

  private static String biomes(List<String> biomes) {
    return biomes.isEmpty() ? "any" : String.join(", ", biomes);
  }
}
