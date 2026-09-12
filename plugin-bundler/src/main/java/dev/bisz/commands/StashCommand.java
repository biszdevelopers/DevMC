package dev.bisz.commands;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.menus.*;
import dev.bisz.players.*;
import dev.bisz.stashes.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class StashCommand extends DevCommand {

  private final BundlerPlugin plugin;

  public StashCommand(BundlerPlugin p) {
    super("stash", "bundler.stash.admin");
    plugin = p;
    requireRank(Rank.ADMIN);
    requireOperator();
    setUsage("/stash [player] [namespace:path]");
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] args
  ) {
    if (!(sender instanceof Player viewer)) {
      sender.sendMessage("This command requires an in-game administrator.");
      return true;
    }
    if (args.length > 2) {
      viewer.sendMessage("§cUsage: " + getUsage());
      return true;
    }
    if (args.length == 0) {
      plugin
        .profileService()
        .allProfiles()
        .thenAccept(profiles ->
          Bukkit.getScheduler().runTask(plugin, () ->
            openPlayers(viewer, profiles)
          )
        );
      return true;
    }
    plugin
      .profileService()
      .findByName(args[0])
      .thenAccept(found ->
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (found.isEmpty()) {
              viewer.sendMessage("§cUnknown player: " + args[0]);
              return;
            }
            if (args.length == 1) openPartitions(viewer, found.get());
            else openDirect(viewer, found.get(), args[1]);
          })
      );
    return true;
  }

  private void openPlayers(Player viewer, Collection<PlayerProfile> profiles) {
    int[] content = java.util.stream.IntStream.range(0, 45).toArray();
    PagedMenuTemplate.Builder<PlayerProfile> b = PagedMenuTemplate.<
        PlayerProfile
      >builder("Stash Players", 6)
      .entries(profiles)
      .contentSlots(content)
      .renderItem((player, profile) -> {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        meta.setDisplayName("§e" + profile.currentName());
        head.setItemMeta(meta);
        return MenuItem.builder(head)
          .onClick(c -> openPartitions(c.player(), profile))
          .build();
      })
      .previousButton(
        45,
        MenuItem.builder(Material.ARROW).name("Previous").build()
      )
      .nextButton(53, MenuItem.builder(Material.ARROW).name("Next").build());
    pagedFrame(b);
    plugin.menuManager().open(viewer, b.build());
  }

  private void openPartitions(Player viewer, PlayerProfile profile) {
    int[] content = java.util.stream.IntStream.range(0, 45).toArray();
    PagedMenuTemplate.Builder<StashPartition> b = PagedMenuTemplate.<
        StashPartition
      >builder("Stashes: " + profile.currentName(), 6)
      .entries(plugin.stashService().partitions())
      .contentSlots(content)
      .renderItem((player, partition) ->
        MenuItem.builder(Material.CHEST)
          .name("§e" + partition.key())
          .lore("§7Items: " + partition.items(profile.playerId()).size())
          .onClick(c -> openEditor(c.player(), profile, partition))
          .build()
      )
      .previousButton(
        45,
        MenuItem.builder(Material.ARROW).name("Previous").build()
      )
      .nextButton(53, MenuItem.builder(Material.ARROW).name("Next").build());
    pagedFrame(b);
    plugin.menuManager().open(viewer, b.build());
  }

  private void openDirect(Player viewer, PlayerProfile profile, String value) {
    List<StashPartition> matches = plugin
      .stashService()
      .partitions()
      .stream()
      .filter(
        p ->
          p.key().toString().equalsIgnoreCase(value) ||
          p.key().getKey().equalsIgnoreCase(value)
      )
      .toList();
    if (matches.size() != 1) {
      viewer.sendMessage(
        matches.isEmpty()
          ? "§cUnknown stash: " + value
          : "§cAmbiguous stash; use namespace:path."
      );
      return;
    }
    openEditor(viewer, profile, matches.get(0));
  }

  private void openEditor(
    Player viewer,
    PlayerProfile profile,
    StashPartition partition
  ) {
    int[] slots = java.util.stream.IntStream.range(0, 27).toArray();
    PagedStorageMenuTemplate.Builder b = PagedStorageMenuTemplate.builder(
      "Edit " + profile.currentName() + " / " + partition.key(),
      4
    )
      .storage(partition.storage(profile.playerId(), 27))
      .storageSlots(slots)
      .previousButton(
        27,
        MenuItem.builder(Material.ARROW).name("Previous").build()
      )
      .nextButton(35, MenuItem.builder(Material.ARROW).name("Next").build());
    for (int i = 28; i < 35; i++) if (i != 31) b.item(
      i,
      MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build()
    );
    b.item(
      31,
      MenuItem.builder(Material.BARRIER)
        .name("Close")
        .onClick(c -> c.player().closeInventory())
        .build()
    );
    plugin.menuManager().open(viewer, b.build());
  }

  private <T> void pagedFrame(PagedMenuTemplate.Builder<T> b) {
    for (int i = 45; i <= 53; i++) b.item(
      i,
      MenuItem.builder(
        i == 49 ? Material.BARRIER : Material.BLACK_STAINED_GLASS_PANE
      )
        .name(i == 49 ? "Close" : " ")
        .onClick(c -> {
          if (c.slot() == 49) c.player().closeInventory();
        })
        .build()
    );
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] args
  ) {
    if (args.length == 1) return Bukkit.getOnlinePlayers()
      .stream()
      .map(Player::getName)
      .toList();
    if (args.length == 2) return plugin
      .stashService()
      .partitions()
      .stream()
      .map(p -> p.key().toString())
      .toList();
    return List.of();
  }
}
