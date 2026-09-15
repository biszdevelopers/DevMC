package dev.bisz.combat;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.chat.ChatUtils;
import dev.bisz.combat.items.*;
import dev.bisz.enchants.DamageIndicator;
import dev.bisz.enchants.LinearExperience;
import dev.bisz.items.*;
import dev.bisz.menus.*;
import dev.bisz.npc.*;
import dev.bisz.players.locales.Locale;
import dev.bisz.stashes.StashPartition;
import dev.bisz.storage.ItemStackCodec;
import java.util.*;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class DeathManager implements Listener, CombatService {

  private static final int LIFE = 12000;
  private final CombatPlugin plugin;
  private final PVPPassiveFacade pvp;
  private final BundlerPlugin bundler;
  private final NpcService npcs;
  private final StashPartition stash;
  private final NamespacedKey reviveKey;
  private final Map<UUID, Ghost> ghosts = new LinkedHashMap<>();
  private final Map<UUID, Corpse> corpses = new LinkedHashMap<>();
  private final Map<UUID, RecoveryNotice> notices = new LinkedHashMap<>();
  private final CorpseRecoveryRoller roller = new CorpseRecoveryRoller(
    new Random()
  );
  private int ticks;

  public interface PVPPassiveFacade {
    boolean enabled(Player p);
    boolean active(Player p);
  }

  public DeathManager(CombatPlugin plugin, PVPPassiveFacade pvp) {
    this.plugin = plugin;
    this.pvp = pvp;
    bundler = BundlerPlugin.instance();
    npcs = bundler.npcService();
    stash = bundler
      .stashService()
      .registerPartition(plugin, "death_recovery", "combat.stash.partition");
    reviveKey = new NamespacedKey(plugin, "revive_control");
    load();
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
  }

  public StashPartition stash() {
    return stash;
  }

  @Override
  public boolean hasPvpEnabled(Player p) {
    return pvp.enabled(p);
  }

  @Override
  public boolean isInPvp(Player p) {
    return pvp.active(p);
  }

  @Override
  public boolean isGhost(Player p) {
    return ghosts.containsKey(p.getUniqueId());
  }

  @Override
  public Optional<Location> deathAnchor(Player p) {
    Ghost g = ghosts.get(p.getUniqueId());
    return Optional.ofNullable(g == null ? null : g.anchor());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void lethal(EntityDamageEvent event) {
    if (
      !(event.getEntity() instanceof Player p) ||
      isGhost(p) ||
      (p.getGameMode() != GameMode.SURVIVAL &&
        p.getGameMode() != GameMode.ADVENTURE) ||
      p.getHealth() + p.getAbsorptionAmount() > event.getFinalDamage()
    ) return;
    if (totem(p)) return;
    if (enter(p, event.getCause().name(), killer(event))) {
      // The custom death cancels the killing blow, so the lethal damage
      // indicator must be shown here instead of on the damage event.
      if (playerAttributed(event)) DamageIndicator.show(plugin, p, event.getFinalDamage());
      event.setCancelled(true);
    }
  }

  private boolean enter(Player p, String cause, String killer) {
    p.playSound(p, Sound.ENTITY_WITHER_SPAWN, 1f, 1f);

    UUID id = p.getUniqueId();
    Ghost ghost = new Ghost(
      id,
      p.getLocation(),
      p.getAllowFlight(),
      p.isFlying(),
      p.isCollidable(),
      p.getCanPickupItems(),
      p.getGameMode(),
      cause,
      0
    );
    Corpse corpse = new Corpse(
      UUID.randomUUID(),
      id,
      p.getName(),
      p.getLocation(),
      cause,
      killer,
      LIFE,
      snapshot(p, cause, killer)
    );
    try {
      corpses.put(corpse.id, corpse);
      ghosts.put(id, ghost);
      spawn(corpse);
      persist();
    } catch (RuntimeException failure) {
      corpses.remove(corpse.id);
      ghosts.remove(id);
      npcs.destroy("combat:corpse/" + corpse.id);
      plugin
        .getLogger()
        .log(
          java.util.logging.Level.SEVERE,
          "Death transaction was rolled back for " + p.getName(),
          failure
        );
      return false;
    }
    p.closeInventory();
    p.getInventory().clear();
    p.setTotalExperience(0);
    p.setLevel(0);
    p.setExp(0);
    applyGhost(p, ghost);
    p.sendTitle(
      "§e" + Locale.get(p, "combat.death.title"),
      Locale.get(p, "combat.death.subtitle"),
      0,
      70,
      20
    );
    Bukkit.getPluginManager().callEvent(
      new GhostStateChangeEvent(p, true, cause)
    );
    p.addPotionEffect(
      new PotionEffect(
        PotionEffectType.NIGHT_VISION,
        Integer.MAX_VALUE,
        1,
        true
      )
    );
    p.addPotionEffect(
      new PotionEffect(
        PotionEffectType.INVISIBILITY,
        Integer.MAX_VALUE,
        1,
        true
      )
    );
    return true;
  }

  private List<ItemStack> snapshot(Player p, String cause, String killer) {
    List<ItemStack> out = new ArrayList<>(Collections.nCopies(43, null));
    ItemStack[] main = p.getInventory().getStorageContents();
    for (int i = 0; i < Math.min(36, main.length); i++) out.set(
      i,
      copy(main[i])
    );
    out.set(36, copy(p.getInventory().getHelmet()));
    out.set(37, copy(p.getInventory().getChestplate()));
    out.set(38, copy(p.getInventory().getLeggings()));
    out.set(39, copy(p.getInventory().getBoots()));
    out.set(40, copy(p.getInventory().getItemInOffHand()));
    out.set(41, head(p, cause, killer));
    int xp = totalXp(p);
    if (xp > 0) out.set(
      42,
      custom(ExperienceBottleItem.ID, new MetadataPair("xp_points", xp))
    );
    return out;
  }

  private ItemStack head(Player p, String cause, String killer) {
    ItemStack item = custom(
      CorpseHeadItem.ID,
      new MetadataPair("owner", p.getName()),
      new MetadataPair("killer", killer),
      new MetadataPair("cause", cause),
      new MetadataPair("death_time", System.currentTimeMillis())
    );
    ItemMeta raw = item.getItemMeta();
    if (raw instanceof SkullMeta meta) {
      meta.setOwningPlayer(p);
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack custom(ItemId id, MetadataPair... data) {
    DevItemStack stack = ItemsPlugin.instance().factory().create(id, 1, data);
    stack.render(null);
    return stack.bukkitStack();
  }

  private void applyGhost(Player p, Ghost g) {
    for (Player viewer : Bukkit.getOnlinePlayers())
      viewer.hidePlayer(plugin, p);
    p.setAllowFlight(true);
    p.setFlying(true);
    p.setCollidable(false);
    p.setCanPickupItems(false);
    restoreHealth(p);
    p.setFoodLevel(20);
    p.getInventory().setItem(4, reviveItem(p));
  }

  private ItemStack reviveItem(Player p) {
    ItemStack item = new ItemStack(Material.RED_BED);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(Locale.get(p, "combat.revive.control"));
    meta
      .getPersistentDataContainer()
      .set(reviveKey, PersistentDataType.BYTE, (byte) 1);
    item.setItemMeta(meta);
    return ItemsPlugin.instance().factory().suppressRendering(item);
  }

  @Override
  public boolean revive(Player p) {
    Ghost g = ghosts.remove(p.getUniqueId());
    if (g == null) return false;
    p.getInventory().clear();
    p.setGameMode(g.mode);
    p.setAllowFlight(g.allowFlight);
    p.setFlying(g.allowFlight && g.flying);
    p.setCollidable(g.collidable);
    p.setCanPickupItems(g.pickup);
    restoreHealth(p);
    for (Player viewer : Bukkit.getOnlinePlayers())
      viewer.showPlayer(plugin, p);
    p.teleport(safe(g.anchor(), p));
    persist();
    Bukkit.getPluginManager().callEvent(
      new GhostStateChangeEvent(p, false, g.cause)
    );
    p.removePotionEffect(PotionEffectType.NIGHT_VISION);
    p.removePotionEffect(PotionEffectType.INVISIBILITY);
    return true;
  }

  private void restoreHealth(Player p) {
    p.setHealth(20.0);
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (p.isOnline() && !p.isDead()) p.setHealth(20.0);
    });
  }

  private Location safe(Location center, Player p) {
    World w = center.getWorld();
    if (w != null) {
      for (int r = 0; r <= 64; r += 4) for (
        int x = -r;
        x <= r;
        x += Math.max(1, r)
      ) for (int z = -r; z <= r; z += Math.max(1, r)) {
        Location at = new Location(
          w,
          center.getBlockX() + x + .5,
          w.getHighestBlockYAt(center.getBlockX() + x, center.getBlockZ() + z) +
          1,
          center.getBlockZ() + z + .5
        );
        if (
          at.getBlock().isPassable() &&
          at.clone().add(0, 1, 0).getBlock().isPassable() &&
          !at.clone().add(0, -1, 0).getBlock().isLiquid()
        ) return at;
      }
    }
    Location bed = p.getBedSpawnLocation();
    return bed != null
      ? bed
      : (w != null ? w.getSpawnLocation() : p.getWorld().getSpawnLocation());
  }

  @EventHandler
  public void npc(NpcInteractionEvent e) {
    Corpse c = corpses
      .values()
      .stream()
      .filter(v -> ("combat:corpse/" + v.id).equals(e.npc().key()))
      .findFirst()
      .orElse(null);
    if (c != null && !isGhost(e.player())) openCorpse(e.player(), c);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void nearbyCorpseInteraction(PlayerInteractEvent e) {
    if (
      isGhost(e.getPlayer()) ||
      e.getHand() != EquipmentSlot.HAND ||
      (e.getAction() != Action.RIGHT_CLICK_AIR &&
        e.getAction() != Action.RIGHT_CLICK_BLOCK)
    ) return;

    Location clicked = e.getClickedBlock() == null
      ? null
      : e.getClickedBlock().getLocation().add(.5, .5, .5);
    Corpse corpse = nearestCorpse(e.getPlayer(), clicked);
    if (corpse == null) return;
    e.setCancelled(true);
    openCorpse(e.getPlayer(), corpse);
  }

  private Corpse nearestCorpse(Player player, Location clicked) {
    Location playerLocation = player.getLocation();
    Corpse nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (Corpse corpse : corpses.values()) {
      Location location = corpse.location();
      if (!location.getWorld().equals(player.getWorld())) continue;
      double playerDistance = location.distanceSquared(playerLocation);
      double clickedDistance = clicked == null
        ? Double.MAX_VALUE
        : location.distanceSquared(clicked);
      double distance = Math.min(playerDistance, clickedDistance);
      if (distance <= 4.0 && distance < nearestDistance) {
        nearest = corpse;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  public void openCorpse(Player p, Corpse c) {
    SinglePageMenuTemplate.Builder b = SinglePageMenuTemplate.builder(
      Locale.get(p, "combat.corpse.title", c.ownerName),
      6
    );
    for (int i = 0; i < 43; i++) b.storageIndex(i, i, StorageAccess.TAKE_ONLY);
    for (int i = 43; i < 54; i++) if (i != 49) b.item(
      i,
      MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build()
    );
    b.item(
      49,
      MenuItem.builder(Material.BARRIER)
        .localizedName("menu.close")
        .onClick(x -> x.player().closeInventory())
        .build()
    );
    b.onStorageChange(x -> {
      syncNpc(c, x.storageIndex());
      persist();
    });
    bundler.menuManager().open(p, b.build(), provider(c));
  }

  private StorageProvider provider(Corpse c) {
    return new StorageProvider() {
      public int size() {
        return 43;
      }

      public ItemStack getItem(int i) {
        return copy(c.items.get(i));
      }

      public ItemStack getItem(int i, Player viewer) {
        return renderFor(copy(c.items.get(i)), viewer);
      }

      public void setItem(int i, ItemStack value) {
        c.items.set(i, copy(value));
      }
    };
  }

  private void syncNpc(Corpse c, int slot) {
    NpcHandle h = npcs.find("combat:corpse/" + c.id).orElse(null);
    if (h == null) return;
    EquipmentSlot e = switch (slot) {
      case 36 -> EquipmentSlot.HEAD;
      case 37 -> EquipmentSlot.CHEST;
      case 38 -> EquipmentSlot.LEGS;
      case 39 -> EquipmentSlot.FEET;
      case 40 -> EquipmentSlot.OFF_HAND;
      default -> null;
    };
    if (e != null) h.equipment(e, c.items.get(slot));
  }

  public void openStash(Player p, UUID owner, StorageAccess access) {
    int[] slots = java.util.stream.IntStream.range(0, 27).toArray();
    StorageProvider storage = viewerAware(stash.storage(owner, 27));
    PagedStorageMenuTemplate.Builder b = PagedStorageMenuTemplate.builder(
      Locale.get(p, "combat.stash.title"),
      4
    )
      .storage(storage)
      .storageSlots(slots)
      .access(access, slots)
      .previousButton(
        27,
        MenuItem.builder(Material.ARROW).localizedName("menu.previous").build()
      )
      .nextButton(
        35,
        MenuItem.builder(Material.ARROW).localizedName("menu.next").build()
      );
    for (int i = 27; i <= 35; i++) if (i != 31) b.item(
      i,
      MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build()
    );
    b.item(
      31,
      MenuItem.builder(Material.BARRIER)
        .localizedName("locale.menu.close")
        .onClick(x -> x.player().closeInventory())
        .build()
    );
    bundler.menuManager().open(p, b.build());
  }

  private StorageProvider viewerAware(StorageProvider backing) {
    return new StorageProvider() {
      public int size() {
        return backing.size();
      }

      public ItemStack getItem(int index) {
        return backing.getItem(index);
      }

      public ItemStack getItem(int index, Player viewer) {
        return renderFor(backing.getItem(index), viewer);
      }

      public void setItem(int index, ItemStack value) {
        backing.setItem(index, value);
      }
    };
  }

  private ItemStack renderFor(ItemStack item, Player viewer) {
    if (item == null || item.getType().isAir()) return item;
    return ItemsPlugin.instance()
      .factory()
      .refreshIfLocaleChanged(item, viewer)
      .bukkitStack();
  }

  public void openRevive(Player p) {
    if (!isGhost(p)) {
      p.sendMessage(Locale.get(p, "combat.revive.not_ghost"));
      return;
    }
    SinglePageMenuTemplate.Builder b = SinglePageMenuTemplate.builder(
      Locale.get(p, "combat.revive.title"),
      3
    );
    for (int i = 0; i < 27; i++) b.item(
      i,
      MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build()
    );
    b.item(
      11,
      MenuItem.builder(Material.SKELETON_SKULL)
        .localizedName("combat.revive.summary")
        .build()
    );
    b.item(
      13,
      MenuItem.builder(Material.EMERALD)
        .localizedName("combat.revive.fee")
        .localizedLore("combat.revive.future_fee")
        .build()
    );
    b.item(
      15,
      MenuItem.builder(Material.RED_BED)
        .localizedName("combat.revive.confirm")
        .onClick(x -> {
          x.player().closeInventory();
          revive(x.player());
        })
        .build()
    );
    b.item(
      22,
      MenuItem.builder(Material.BARRIER)
        .localizedName("menu.close")
        .onClick(x -> x.player().closeInventory())
        .build()
    );
    bundler.menuManager().open(p, b.build());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void interact(PlayerInteractEvent e) {
    if (!isGhost(e.getPlayer())) return;
    if (
      e.getItem() != null &&
      e.getItem().hasItemMeta() &&
      e
        .getItem()
        .getItemMeta()
        .getPersistentDataContainer()
        .has(reviveKey, PersistentDataType.BYTE)
    ) {
      e.setCancelled(true);
      openRevive(e.getPlayer());
    } else e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void command(PlayerCommandPreprocessEvent e) {
    if (!isGhost(e.getPlayer())) return;
    String cmd = e
      .getMessage()
      .substring(1)
      .split(" ")[0].toLowerCase(java.util.Locale.ROOT);
    if (!Set.of("revive", "locale", "lang").contains(cmd)) {
      e.setCancelled(true);
      e
        .getPlayer()
        .sendMessage(Locale.get(e.getPlayer(), "combat.ghost.command_blocked"));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void damage(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player p && isGhost(p)) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void damageOut(EntityDamageByEntityEvent e) {
    Player p = e.getDamager() instanceof Player x
      ? x
      : e.getDamager() instanceof Projectile projectile &&
        projectile.getShooter() instanceof Player x
        ? x
        : null;
    if (p != null && isGhost(p)) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void target(EntityTargetLivingEntityEvent e) {
    if (e.getTarget() instanceof Player p && isGhost(p)) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drop(PlayerDropItemEvent e) {
    if (isGhost(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void pickup(EntityPickupItemEvent e) {
    if (e.getEntity() instanceof Player p && isGhost(p)) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void consume(PlayerItemConsumeEvent e) {
    if (isGhost(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void breakBlock(BlockBreakEvent e) {
    if (isGhost(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void inventory(InventoryOpenEvent e) {
    if (
      e.getPlayer() instanceof Player p &&
      isGhost(p) &&
      !(e.getInventory().getHolder() instanceof MenuInventoryHolder)
    ) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void inventoryClick(InventoryClickEvent e) {
    if (
      e.getWhoClicked() instanceof Player p &&
      isGhost(p) &&
      !(e.getView().getTopInventory().getHolder() instanceof
        MenuInventoryHolder)
    ) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void inventoryDrag(InventoryDragEvent e) {
    if (
      e.getWhoClicked() instanceof Player p &&
      isGhost(p) &&
      !(e.getView().getTopInventory().getHolder() instanceof
        MenuInventoryHolder)
    ) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void projectile(ProjectileLaunchEvent e) {
    if (
      e.getEntity().getShooter() instanceof Player p && isGhost(p)
    ) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void hunger(FoodLevelChangeEvent e) {
    if (e.getEntity() instanceof Player p && isGhost(p)) e.setCancelled(true);
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    Ghost own = ghosts.get(p.getUniqueId());
    if (own != null) applyGhost(p, own);
    for (UUID id : ghosts.keySet()) {
      Player ghost = Bukkit.getPlayer(id);
      if (ghost != null) e.getPlayer().hidePlayer(plugin, ghost);
    }
    RecoveryNotice notice = notices.remove(p.getUniqueId());
    if (notice != null) {
      notify(p, notice.result());
      persist();
    } else if (!stash.isEmpty(p.getUniqueId())) remind(p);
  }

  private void tick() {
    ticks++;
    boolean changed = false;
    for (Ghost g : ghosts.values()) {
      Player p = Bukkit.getPlayer(g.owner);
      if (p != null && p.isOnline()) boundary(p, g);
    }
    for (Iterator<Corpse> it = corpses.values().iterator(); it.hasNext(); ) {
      Corpse c = it.next();
      if (empty(c)) {
        c.emptyTicks++;
        if (c.emptyTicks >= 200) {
          remove(c, true);
          it.remove();
          changed = true;
        }
      } else {
        c.emptyTicks = 0;
        c.remaining--;
        if (c.remaining <= 0 && expire(c)) {
          it.remove();
          changed = true;
        }
      }
    }
    if (changed || ticks % 200 == 0) persist();
  }

  private void boundary(Player p, Ghost g) {
    Location a = g.anchor();
    if (
      p.getWorld() != a.getWorld() || p.getLocation().distanceSquared(a) > 4096
    ) {
      g.violations++;
      if (g.violations >= 5) {
        p.teleport(a);
        g.violations = 0;
      } else {
        p.sendMessage(Locale.get(p, "combat.ghost.boundary", g.violations));
        Location from = p.getLocation();
        if (from.getWorld() != a.getWorld()) p.teleport(a);
        else {
          org.bukkit.util.Vector v = a.toVector().subtract(from.toVector());
          if (v.lengthSquared() > 0) p.setVelocity(v.normalize().multiply(1.2));
        }
      }
    } else g.violations = 0;
  }

  private boolean expire(Corpse c) {
    CorpseRecoveryPrepareEvent event = new CorpseRecoveryPrepareEvent(
      c.owner,
      .5
    );
    Bukkit.getPluginManager().callEvent(event);
    CorpseRecoveryResult result = roller.roll(
      c.items.stream().filter(Objects::nonNull).toList(),
      event.chance()
    );
    try {
      stash.addAll(c.owner, result.recovered());
    } catch (RuntimeException failure) {
      c.remaining = 20;
      return false;
    }
    remove(c, false);
    Player p = Bukkit.getPlayer(c.owner);
    if (p != null) notify(p, result);
    else notices.put(c.owner, new RecoveryNotice(result));
    return true;
  }

  private void notify(Player p, CorpseRecoveryResult r) {
    TextComponent text = new TextComponent(
      Locale.get(p, "combat.recovery.message")
    );
    ChatUtils.attachCommand(text, "deathstash", summary(r, p));
    p.spigot().sendMessage(text);
  }

  private String summary(CorpseRecoveryResult r, Player p) {
    return (
      Locale.get(p, "combat.recovery.recovered") +
      " " +
      describe(r.recovered()) +
      "\n" +
      Locale.get(p, "combat.recovery.lost") +
      " " +
      describe(r.lost())
    );
  }

  private String describe(List<ItemStack> list) {
    if (list.isEmpty()) return "none";
    return (
      list
        .stream()
        .limit(20)
        .map(i -> i.getAmount() + "x " + human(i.getType().name()))
        .reduce((a, b) -> a + ", " + b)
        .orElse("none") +
      (list.size() > 20 ? " …" : "")
    );
  }

  public void remind(Player p) {
    TextComponent t = new TextComponent(Locale.get(p, "combat.stash.reminder"));
    ChatUtils.attachCommand(
      t,
      "deathstash",
      Locale.get(p, "combat.stash.hover")
    );
    p.spigot().sendMessage(t);
  }

  private void remove(Corpse c, boolean smoke) {
    NpcHandle h = npcs.find("combat:corpse/" + c.id).orElse(null);
    if (smoke && h != null && h.entity() != null) h
      .entity()
      .getWorld()
      .spawnParticle(Particle.SMOKE_NORMAL, h.entity().getLocation(), 30);
    npcs.destroy("combat:corpse/" + c.id);
  }

  private void spawn(Corpse c) {
    NpcDefinition.Builder b = NpcDefinition.builder(
      "combat:corpse/" + c.id,
      EntityType.PLAYER,
      c.location()
    )
      .name(c.ownerName)
      .nameplateVisible(false)
      .pose(NpcPose.SLEEPING);
    b
      .equipment(EquipmentSlot.HEAD, c.items.get(36))
      .equipment(EquipmentSlot.CHEST, c.items.get(37))
      .equipment(EquipmentSlot.LEGS, c.items.get(38))
      .equipment(EquipmentSlot.FEET, c.items.get(39))
      .equipment(EquipmentSlot.OFF_HAND, c.items.get(40));
    npcs.create(b.build());
  }

  public void dispose() {
    persist();
    for (UUID id : ghosts.keySet()) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) for (Player v : Bukkit.getOnlinePlayers())
        v.showPlayer(plugin, p);
    }
    for (Corpse c : corpses.values()) npcs.destroy("combat:corpse/" + c.id);
  }

  private void persist() {
    List<Map<String, Object>> gs = new ArrayList<>(),
      cs = new ArrayList<>(),
      ns = new ArrayList<>();
    for (Ghost g : ghosts.values()) gs.add(g.map());
    for (Corpse c : corpses.values()) cs.add(c.map());
    notices.forEach((id, n) -> ns.add(n.map(id)));
    bundler
      .jsonDatabase()
      .saveDataFromDataBase(
        "combat/deaths.json",
        Map.of("schema", 1, "ghosts", gs, "corpses", cs, "notices", ns)
      );
  }

  private void load() {
    Map<String, Object> root = bundler
      .jsonDatabase()
      .loadDataFromDataBase("combat/deaths.json");
    if (root.get("ghosts") instanceof List<?> list) for (Object o : list)
      if (o instanceof Map<?, ?> m) try {
        Ghost g = Ghost.read(m);
        ghosts.put(g.owner, g);
      } catch (RuntimeException e) {
        plugin
          .getLogger()
          .warning("Skipped invalid ghost record: " + e.getMessage());
      }
    if (root.get("corpses") instanceof List<?> list) for (Object o : list)
      if (o instanceof Map<?, ?> m) try {
        Corpse c = Corpse.read(m);
        corpses.put(c.id, c);
        spawn(c);
      } catch (RuntimeException e) {
        plugin
          .getLogger()
          .warning("Skipped invalid corpse record: " + e.getMessage());
      }
    if (root.get("notices") instanceof List<?> list) for (Object o : list)
      if (o instanceof Map<?, ?> m) try {
        UUID id = UUID.fromString(s(m, "owner"));
        notices.put(id, RecoveryNotice.read(m));
      } catch (RuntimeException e) {
        plugin
          .getLogger()
          .warning("Skipped invalid recovery notice: " + e.getMessage());
      }
  }

  private static boolean empty(Corpse c) {
    return c.items.stream().allMatch(DeathManager::isEmpty);
  }

  private static boolean isEmpty(ItemStack i) {
    return i == null || i.getType().isAir() || i.getAmount() <= 0;
  }

  private static ItemStack copy(ItemStack i) {
    return isEmpty(i) ? null : i.clone();
  }

  private static String killer(EntityDamageEvent e) {
    if (e instanceof EntityDamageByEntityEvent by) {
      Player p = by.getDamager() instanceof Player x
        ? x
        : by.getDamager() instanceof Projectile projectile &&
          projectile.getShooter() instanceof Player x
          ? x
          : null;
      return p == null ? by.getDamager().getName() : p.getName();
    }
    return "none";
  }

  private static boolean playerAttributed(EntityDamageEvent e) {
    if (!(e instanceof EntityDamageByEntityEvent by)) return false;
    return by.getDamager() instanceof Player
      || (by.getDamager() instanceof Projectile projectile &&
        projectile.getShooter() instanceof Player);
  }

  private static boolean totem(Player p) {
    return (
      p.getInventory().getItemInMainHand().getType() ==
        Material.TOTEM_OF_UNDYING ||
      p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING
    );
  }

  public static int totalXp(Player p) {
    return LinearExperience.totalPoints(p);
  }

  private static String human(String s) {
    String t = s.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(t.charAt(0)) + t.substring(1);
  }

  private static final class Ghost {

    final UUID owner;
    final String world, cause;
    final double x, y, z;
    final float yaw, pitch;
    final boolean allowFlight, flying, collidable, pickup;
    final GameMode mode;
    int violations;

    Ghost(
      UUID id,
      Location l,
      boolean a,
      boolean f,
      boolean c,
      boolean p,
      GameMode m,
      String cause,
      int v
    ) {
      this(
        id,
        l.getWorld().getName(),
        l.getX(),
        l.getY(),
        l.getZ(),
        l.getYaw(),
        l.getPitch(),
        a,
        f,
        c,
        p,
        m,
        cause,
        v
      );
    }

    Ghost(
      UUID owner,
      String world,
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      boolean allowFlight,
      boolean flying,
      boolean collidable,
      boolean pickup,
      GameMode mode,
      String cause,
      int violations
    ) {
      this.owner = owner;
      this.world = world;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.pitch = pitch;
      this.allowFlight = allowFlight;
      this.flying = flying;
      this.collidable = collidable;
      this.pickup = pickup;
      this.mode = mode;
      this.cause = cause;
      this.violations = violations;
    }

    Location anchor() {
      World w = Bukkit.getWorld(world);
      return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    Map<String, Object> map() {
      return Map.ofEntries(
        Map.entry("owner", owner.toString()),
        Map.entry("world", world),
        Map.entry("x", x),
        Map.entry("y", y),
        Map.entry("z", z),
        Map.entry("yaw", yaw),
        Map.entry("pitch", pitch),
        Map.entry("allowFlight", allowFlight),
        Map.entry("flying", flying),
        Map.entry("collidable", collidable),
        Map.entry("pickup", pickup),
        Map.entry("mode", mode.name()),
        Map.entry("cause", cause),
        Map.entry("violations", violations)
      );
    }

    static Ghost read(Map<?, ?> m) {
      Object value = m.get("violations");
      return new Ghost(
        UUID.fromString(s(m, "owner")),
        s(m, "world"),
        n(m, "x"),
        n(m, "y"),
        n(m, "z"),
        (float) n(m, "yaw"),
        (float) n(m, "pitch"),
        b(m, "allowFlight"),
        b(m, "flying"),
        b(m, "collidable"),
        b(m, "pickup"),
        GameMode.valueOf(s(m, "mode")),
        s(m, "cause"),
        value instanceof Number number ? number.intValue() : 0
      );
    }
  }

  private static final class Corpse {

    final UUID id, owner;
    final String ownerName, world, cause, killer;
    final double x, y, z;
    final float yaw, pitch;
    int remaining, emptyTicks;
    final List<ItemStack> items;

    Corpse(
      UUID id,
      UUID owner,
      String name,
      Location l,
      String cause,
      String killer,
      int remaining,
      List<ItemStack> items
    ) {
      this(
        id,
        owner,
        name,
        l.getWorld().getName(),
        l.getX(),
        l.getY(),
        l.getZ(),
        l.getYaw(),
        l.getPitch(),
        cause,
        killer,
        remaining,
        0,
        items
      );
    }

    Corpse(
      UUID id,
      UUID owner,
      String name,
      String world,
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      String cause,
      String killer,
      int remaining,
      int emptyTicks,
      List<ItemStack> items
    ) {
      this.id = id;
      this.owner = owner;
      ownerName = name;
      this.world = world;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.pitch = pitch;
      this.cause = cause;
      this.killer = killer;
      this.remaining = remaining;
      this.emptyTicks = emptyTicks;
      this.items = items;
    }

    Location location() {
      return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    Map<String, Object> map() {
      List<String> encoded = new ArrayList<>();
      for (ItemStack i : items)
        encoded.add(i == null ? null : ItemStackCodec.encode(i));
      return Map.ofEntries(
        Map.entry("id", id.toString()),
        Map.entry("owner", owner.toString()),
        Map.entry("name", ownerName),
        Map.entry("world", world),
        Map.entry("x", x),
        Map.entry("y", y),
        Map.entry("z", z),
        Map.entry("yaw", yaw),
        Map.entry("pitch", pitch),
        Map.entry("cause", cause),
        Map.entry("killer", killer),
        Map.entry("remaining", remaining),
        Map.entry("emptyTicks", emptyTicks),
        Map.entry("items", encoded)
      );
    }

    @SuppressWarnings("rawtypes")
    static Corpse read(Map m) {
      List<ItemStack> items = new ArrayList<>();
      for (Object o : (List<?>) m.get("items"))
        items.add(o == null ? null : ItemStackCodec.decode(String.valueOf(o)));
      while (items.size() < 43) items.add(null);
      return new Corpse(
        UUID.fromString(s(m, "id")),
        UUID.fromString(s(m, "owner")),
        s(m, "name"),
        s(m, "world"),
        n(m, "x"),
        n(m, "y"),
        n(m, "z"),
        (float) n(m, "yaw"),
        (float) n(m, "pitch"),
        s(m, "cause"),
        s(m, "killer"),
        ((Number) m.get("remaining")).intValue(),
        ((Number) m.getOrDefault("emptyTicks", 0)).intValue(),
        items
      );
    }
  }

  private record RecoveryNotice(CorpseRecoveryResult result) {
    Map<String, Object> map(UUID owner) {
      return Map.of(
        "owner",
        owner.toString(),
        "recovered",
        encode(result.recovered()),
        "lost",
        encode(result.lost())
      );
    }

    static RecoveryNotice read(Map<?, ?> m) {
      return new RecoveryNotice(
        new CorpseRecoveryResult(
          decode(m.get("recovered")),
          decode(m.get("lost"))
        )
      );
    }

    private static List<String> encode(List<ItemStack> items) {
      return items.stream().map(ItemStackCodec::encode).toList();
    }

    private static List<ItemStack> decode(Object value) {
      if (!(value instanceof List<?> list)) return List.of();
      return list
        .stream()
        .map(String::valueOf)
        .map(ItemStackCodec::decode)
        .toList();
    }
  }

  private static String s(Map<?, ?> m, String k) {
    return String.valueOf(m.get(k));
  }

  private static double n(Map<?, ?> m, String k) {
    return ((Number) m.get(k)).doubleValue();
  }

  private static boolean b(Map<?, ?> m, String k) {
    return Boolean.TRUE.equals(m.get(k));
  }
}
