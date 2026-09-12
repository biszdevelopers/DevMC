/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.bundler;

import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.bundler.internal.ModuleManager;
import dev.bisz.bundler.internal.ServiceRegistry;
import dev.bisz.catalogs.StaticCatalogService;
import dev.bisz.catalogs.internal.JsonCatalogService;
import dev.bisz.commands.BundlerCommand;
import dev.bisz.commands.DevCommandRegistry;
import dev.bisz.commands.StashCommand;
import dev.bisz.menus.MenuManager;
import dev.bisz.menus.internal.MenuModule;
import dev.bisz.nbt.ItemDataService;
import dev.bisz.nbt.PersistentItemDataService;
import dev.bisz.npc.NpcService;
import dev.bisz.npc.internal.NpcModule;
import dev.bisz.players.ProfileService;
import dev.bisz.players.RankService;
import dev.bisz.players.internal.ProfileModule;
import dev.bisz.players.locales.LocaleService;
import dev.bisz.players.locales.internal.LocaleModule;
import dev.bisz.players.moderation.internal.ModerationModule;
import dev.bisz.players.ranks.internal.RankModule;
import dev.bisz.players.web.AuthenticationGateway;
import dev.bisz.players.web.AuthenticationModule;
import dev.bisz.stashes.StashService;
import dev.bisz.stashes.internal.JsonStashService;
import dev.bisz.storage.JsonDatabase;
import dev.bisz.tablist.internal.TabListModule;
import dev.bisz.uptime.ServerHealthModule;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class BundlerPlugin extends JavaPlugin {

  private static BundlerPlugin instance;
  private final ServiceRegistry services = new ServiceRegistry();
  private final ModuleManager modules = new ModuleManager();
  private JsonDatabase database;
  private DevCommandRegistry commandRegistry;
  private volatile AuthenticationGateway authenticationGateway;

  public static BundlerPlugin instance() {
    return Objects.requireNonNull(instance, "Bundler plugin is not loaded");
  }

  public DevCommandRegistry commandRegistry() {
    return Objects.requireNonNull(
      this.commandRegistry,
      "Bundler command registry is not initialized"
    );
  }

  public LocaleService localeService() {
    return this.services.require(LocaleService.class);
  }

  public ProfileService profileService() {
    return this.services.require(ProfileService.class);
  }

  public RankService rankService() {
    return this.services.require(RankService.class);
  }

  /** Returns Bundler's concrete menu runtime. */
  public MenuManager menuManager() {
    return this.services.require(MenuManager.class);
  }

  public JsonDatabase jsonDatabase() {
    return Objects.requireNonNull(
      this.database,
      "Bundler JSON database is not initialized"
    );
  }

  public StashService stashService() {
    return this.services.require(StashService.class);
  }

  public NpcService npcService() {
    return this.services.require(NpcService.class);
  }

  /**
   * Opens a plugin resource, including on hybrid servers whose plugin classloader
   * does not expose non-class entries through Bukkit's normal resource method.
   */
  public InputStream bundledResource(String name) {
    InputStream standard = this.getResource(name);
    if (standard != null) return standard;
    InputStream direct = resourceFromArchive(this.getFile(), name);
    if (direct != null) return direct;
    File pluginsDirectory = this.getDataFolder().getParentFile();
    File[] candidates = pluginsDirectory.listFiles(
      file ->
        file.isFile() &&
        file.getName().toLowerCase(java.util.Locale.ROOT).contains("bundler") &&
        file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")
    );
    if (candidates != null) {
      for (File candidate : candidates) {
        direct = resourceFromArchive(candidate, name);
        if (direct != null) return direct;
      }
    }
    return null;
  }

  private InputStream resourceFromArchive(File file, String name) {
    try (ZipFile archive = new ZipFile(file)) {
      var entry = archive.getEntry(name);
      if (entry == null) return null;
      try (InputStream input = archive.getInputStream(entry)) {
        return new ByteArrayInputStream(input.readAllBytes());
      }
    } catch (IOException exception) {
      this.getLogger().log(
        java.util.logging.Level.WARNING,
        "Cannot read bundled resource " + name + " from " + file,
        exception
      );
      return null;
    }
  }

  public void authenticationGateway(AuthenticationGateway gateway) {
    this.authenticationGateway = gateway;
  }

  public AuthenticationGateway authenticationGateway() {
    return this.authenticationGateway;
  }

  public void onEnable() {
    instance = this;
    this.saveDefaultConfig();
    try {
      String configuredDirectory = this.getConfig().getString(
        "storage.directory",
        "D:/ServerData"
      );
      if (configuredDirectory == null || configuredDirectory.isBlank()) {
        throw new IllegalArgumentException("storage.directory cannot be blank");
      }
      this.database = new JsonDatabase(
        Path.of(configuredDirectory, new String[0])
      );
      this.getLogger().info(
        "JSON database directory: " + String.valueOf(this.database.root())
      );
      JsonCatalogService catalogs = new JsonCatalogService(this);
      this.services.register(StaticCatalogService.class, catalogs);
      this.services.register(
        ItemDataService.class,
        new PersistentItemDataService((Plugin) this)
      );
      this.services.register(
        StashService.class,
        new JsonStashService(this, this.database)
      );
      this.commandRegistry = new DevCommandRegistry(this.getServer());
      this.modules.add(new ProfileModule());
      this.modules.add(new RankModule());
      this.modules.add(new LocaleModule());
      this.modules.add(new AuthenticationModule());
      this.modules.add(new ModerationModule());
      this.modules.add(new MenuModule());
      this.modules.add(new ServerHealthModule());
      this.modules.add(new TabListModule());
      this.modules.add(new NpcModule());
      this.modules.startEnabled(
        this.enabledModules(),
        new ModuleContext(this, this.services, this.database, catalogs)
      );
      this.getCommand("bundler").setExecutor(
        (CommandExecutor) new BundlerCommand(this.modules, catalogs)
      );
      this.commandRegistry.register(this, new StashCommand(this));
    } catch (RuntimeException exception) {
      this.getLogger().severe(
        "Bundler could not start: " + exception.getMessage()
      );
      this.getServer().getPluginManager().disablePlugin((Plugin) this);
    }
  }

  public void onDisable() {
    this.modules.stopAll();
    if (this.commandRegistry != null) {
      this.commandRegistry.unregisterAll((Plugin) this);
    }
    if (this.database != null) {
      this.database.close();
    }
    if (instance == this) {
      instance = null;
    }
  }

  private Set<String> enabledModules() {
    HashSet<String> enabled = new HashSet<String>();
    for (String id : new String[] {
      "profiles",
      "ranks",
      "locales",
      "authentication",
      "moderation",
      "menus",
      "server-health",
      "tablist",
      "npc",
    }) {
      boolean defaultEnabled =
        id.equals("ranks") || id.equals("menus") || id.equals("npc");
      boolean moduleEnabled = this.getConfig().getBoolean(
        "modules." + id,
        defaultEnabled
      );
      if (
        id.equals("npc") &&
        this.getServer().getPluginManager().getPlugin("Citizens") != null
      ) moduleEnabled = true;
      if (!moduleEnabled) continue;
      enabled.add(id);
    }
    if (enabled.contains("locales") && enabled.add("menus")) {
      this.getLogger().info(
        "Enabling menus because the locale module requires the language selector."
      );
    }
    return enabled;
  }
}
