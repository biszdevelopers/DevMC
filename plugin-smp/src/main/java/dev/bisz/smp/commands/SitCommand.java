/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  dev.bisz.commands.DevCommand
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.ArmorStand
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Silverfish
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.spigotmc.event.entity.EntityDismountEvent
 */
package dev.bisz.smp.commands;

import dev.bisz.commands.DevCommand;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.plugin.Plugin;

public class SitCommand extends DevCommand implements Listener {

  public static Map<Player, ArmorStand> map = new HashMap<Player, ArmorStand>();
  public static Map<Player, Location> map2 = new HashMap<Player, Location>();
  public static Map<Player, LivingEntity> map3 = new HashMap<
    Player,
    LivingEntity
  >();

  public SitCommand() {
    super("sit", "lets u to sit down!");
  }

  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] args
  ) {
    if (sender instanceof Player) {
      this.onPlayerExecute((Player) sender, args);
    } else {
      sender.sendMessage("You cant do that");
    }
    return true;
  }

  public void onPlayerExecute(Player player, String[] args) {
    if (map.keySet().contains(player)) {
      player.sendMessage("\u00a7c\u4f60\u5df2\u7ecf\u5750\u4e0b\u4e86\uff01");
      return;
    }
    if (!player.isOnGround()) {
      player.sendMessage(
        "\u00a7c\u4f60\u5fc5\u987b\u7ad9\u7a33\u624d\u80fd\u5750\u4e0b\uff01"
      );
      return;
    }
    if (args.length >= 1) {
      String name = args[0];
      Player p = Bukkit.getPlayer((String) name);
      if (player.getName().equalsIgnoreCase(name)) {
        player.sendMessage(
          "\u00a7c\u4f60\u4e0d\u80fd\u505a\u4f60\u81ea\u5df1\u8eab\u4e0a\uff01"
        );
        return;
      }
      if (p == null) {
        player.sendMessage("\u00a7c" + name + "\u4e0d\u5728\u7ebf\uff01");
        return;
      }
      if (p.getLocation().distance(player.getLocation()) > 5.0) {
        player.sendMessage(
          "\u00a7c" +
          name +
          " \u5fc5\u987b\u5728\u4f60 5 \u683c\u4e4b\u5185\uff01"
        );
        return;
      }
      LivingEntity ent = (LivingEntity) player
        .getWorld()
        .spawnEntity(
          player.getLocation().add(0.0, -1.65, 0.0),
          EntityType.SILVERFISH
        );
      ent.setInvisible(true);
      ent.setAI(false);
      ent.setInvulnerable(true);
      ent.setGravity(false);
      ent.setPassenger((Entity) player);
      p.setPassenger((Entity) ent);
      map3.put(player, ent);
    } else {
      ArmorStand ent = (ArmorStand) player
        .getWorld()
        .spawnEntity(
          player.getLocation().add(0.0, -1.65, 0.0),
          EntityType.ARMOR_STAND
        );
      ent.setVisible(false);
      ent.setInvulnerable(true);
      ent.setGravity(false);
      map.put(player, ent);
      map2.put(player, player.getLocation());
      ent.setPassenger((Entity) player);
    }
  }

  public static void enable(Plugin plugin) {
    Bukkit.getPluginManager().registerEvents(
      (Listener) new SitCommand(),
      plugin
    );
  }

  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    return Bukkit.getOnlinePlayers()
      .stream()
      .map(String::valueOf)
      .collect(Collectors.toList());
  }

  @EventHandler
  public void exit(EntityDismountEvent event) {
    if (event.getDismounted() instanceof ArmorStand) {
      if (event.getEntity() instanceof Player) {
        Player player = (Player) event.getEntity();
        if (map.keySet().contains(player)) {
          ArmorStand entity = (ArmorStand) event.getDismounted();
          entity.remove();
          map.remove(player);
          Location loc = map2.get(player);
          player.teleport(loc);
          map2.remove(player);
        }
      }
    } else if (
      event.getDismounted() instanceof Silverfish &&
      event.getEntity() instanceof Player
    ) {
      Player player = (Player) event.getEntity();
      if (map3.keySet().contains(player)) {
        Silverfish entity = (Silverfish) event.getDismounted();
        entity.remove();
        map3.remove(player);
      }
    }
  }
}
