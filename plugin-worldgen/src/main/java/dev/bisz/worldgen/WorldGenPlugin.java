package dev.bisz.worldgen;

import dev.bisz.bundler.JSON;
import dev.bisz.worldgen.commands.WorldAdminCommand;
import dev.bisz.worldgen.commands.WorldInfoCommand;
import dev.bisz.worldgen.config.WorldSettings;
import dev.bisz.worldgen.feature.FeatureGenerator;
import dev.bisz.worldgen.listener.IndicatorHud;
import dev.bisz.worldgen.listener.WildernessIntegrityListener;
import dev.bisz.worldgen.listener.WildernessListener;
import dev.bisz.worldgen.listener.WorldJoinListener;
import dev.bisz.worldgen.loot.LootTables;
import dev.bisz.worldgen.setup.AreaRegenerator;
import dev.bisz.worldgen.setup.SetupService;
import dev.bisz.worldgen.setup.SetupState;
import dev.bisz.worldgen.sim.AdminDimensionService;
import dev.bisz.worldgen.sim.RegenSelfTest;
import dev.bisz.worldgen.sim.RegenerationSimulator;
import dev.bisz.worldgen.snapshot.SnapshotStore;
import dev.bisz.worldgen.structure.PoiService;
import dev.bisz.worldgen.wilderness.ChunkIndicators;
import dev.bisz.worldgen.wilderness.ChunkRegenerator;
import dev.bisz.worldgen.wilderness.LootReseeder;
import dev.bisz.worldgen.wilderness.OreReseeder;
import dev.bisz.worldgen.wilderness.RegenerationScheduler;
import dev.bisz.worldgen.wilderness.Regenerators;
import dev.bisz.worldgen.wilderness.SmallStructures;
import dev.bisz.worldgen.worldgen.BareboneGenerator;
import dev.bisz.worldgen.worldgen.WorldGenerators;
import dev.bisz.commands.CommandRegistery;
import dev.bisz.players.locales.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entry point for the world generation plugin. */
public final class WorldGenPlugin extends JavaPlugin {

  private static WorldGenPlugin instance;

  private WorldSettings settings;
  private LootTables lootTables;
  private PoiService pois;
  private ChunkRegenerator regenerator;
  private ChunkIndicators indicators;
  private RegenerationScheduler regenerationScheduler;
  private AreaRegenerator areaRegenerator;
  private SetupService setup;
  private SetupState setupState;
  private OreReseeder oreReseeder;
  private SmallStructures smallStructures;
  private LootReseeder lootReseeder;
  private FeatureGenerator features;
  private AdminDimensionService adminDimension;
  private SnapshotStore snapshots;
  private RegenerationSimulator simulator;
  private IndicatorHud indicatorHud;

  /** Returns the loaded plugin instance. */
  public static WorldGenPlugin instance() {
    return Objects.requireNonNull(instance, "WorldGen plugin is not loaded");
  }

  @Override
  public void onEnable() {
    instance = this;
    try {
      installLocaleDefaults();
      this.settings = WorldSettings.load(this);
      this.lootTables = LootTables.load(this);
      this.smallStructures = new SmallStructures(lootTables);
      this.pois = new PoiService(this, settings, lootTables, smallStructures);
      this.pois.load();
      this.areaRegenerator = new AreaRegenerator(this);
      this.setup = new SetupService(this);
      this.setupState = new SetupState(this);
      this.setupState.load();
      this.regenerator = Regenerators.create(this);
      this.indicators = new ChunkIndicators(this);
      this.indicators.load();
      this.oreReseeder = new OreReseeder(settings);
      this.lootReseeder = new LootReseeder(settings, lootTables);
      this.features = new FeatureGenerator(
        settings,
        oreReseeder,
        pois,
        lootReseeder
      );
      this.regenerationScheduler = new RegenerationScheduler(
        this,
        settings,
        indicators,
        regenerator,
        features
      );
      this.regenerationScheduler.start();
      this.adminDimension = new AdminDimensionService(this, settings);
      this.snapshots = new SnapshotStore(this);
      this.snapshots.load();
      this.simulator = new RegenerationSimulator(
        adminDimension,
        regenerator,
        oreReseeder,
        smallStructures,
        lootReseeder
      );
      this.indicatorHud = new IndicatorHud(this);
      this.indicatorHud.start();
      registerListeners();
      registerCommands();
      autoSetup();
      if (settings.debugVerifyRegen()) {
        new RegenSelfTest(this).schedule(4, 4);
      }
      getLogger()
        .info(
          "WorldGen enabled with regeneration backend " + regenerator.name() + "."
        );
    } catch (RuntimeException exception) {
      getLogger().severe("WorldGen could not start: " + exception.getMessage());
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  /**
   * Creates and generates the managed world on startup, with no administrator
   * action. Re-runs automatically if the world was removed.
   */
  private void autoSetup() {
    try {
      if (setupState.complete()) {
        // Paper does not auto-load the custom world on restart; adopt it.
        World world = setup.ensureWorld();
        // Re-apply gamerules/difficulty every start so config changes (for
        // example mobGriefing) reach an already-created world.
        setup.applyWorldRules(world);
        return;
      }
      SetupService.Report report = setup.run();
      for (SetupService.Step step : report.steps()) {
        getLogger().info(
          "setup " + step.name() + ": " + step.detail()
        );
      }
      if (report.success()) {
        setupState.markComplete(setup.managedWorldName());
      } else {
        getLogger()
          .warning(
            "World setup did not fully succeed; it will be retried on next start."
          );
      }
    } catch (RuntimeException exception) {
      getLogger()
        .warning("Automatic world setup failed: " + exception.getMessage());
    }
  }

  /**
   * Generator used when the server loads the managed world from disk (via the
   * {@code worlds: <name>: generator: worldgen} entry in bukkit.yml). This keeps
   * vanilla-terrain, feature-free generation applied across restarts.
   */
  @Override
  public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
    WorldSettings current = this.settings;
    if (current == null) {
      getLogger().info("Using BareboneGenerator for world " + worldName + ".");
      return new BareboneGenerator();
    }
    getLogger()
      .info(
        "Using " +
        (current.islandEnabled() ? "IslandWorldGenerator" : "BareboneGenerator") +
        " for world " +
        worldName +
        "."
      );
    return WorldGenerators.create(current);
  }

  @Override
  public void onDisable() {
    if (regenerationScheduler != null) regenerationScheduler.stop();
    if (areaRegenerator != null) areaRegenerator.stop();
    if (indicatorHud != null) indicatorHud.stop();
    if (indicators != null) indicators.save();
    if (snapshots != null) snapshots.save();
    CommandRegistery.unregisterAll(this);
    if (instance == this) instance = null;
  }

  private void registerListeners() {
    for (Listener listener : new Listener[] {
      new WildernessListener(this),
      new WildernessIntegrityListener(this),
      new WorldJoinListener(this),
      indicatorHud,
    }) {
      getServer().getPluginManager().registerEvents(listener, this);
    }
  }

  private void registerCommands() {
    CommandRegistery.register(this, new WorldAdminCommand(this));
    CommandRegistery.register(this, new WorldInfoCommand(this));
  }

  private void installLocaleDefaults() {
    for (String language : Locale.availableLanguages()) {
      String resource = "lang/" + language + ".json";
      try (InputStream input = getResource(resource)) {
        if (input == null) continue;
        JSON.mergeMissingDefaults(resource, input);
      } catch (IOException exception) {
        throw new IllegalStateException(
          "Cannot install bundled world locale file " + resource,
          exception
        );
      }
    }
    Locale.reload();
  }

  /** Reloads persisted chunk indicators. */
  public void reloadData() {
    indicators.load();
  }

  public WorldSettings settings() {
    return settings;
  }

  public LootTables lootTables() {
    return lootTables;
  }

  public PoiService pois() {
    return pois;
  }

  public ChunkRegenerator regenerator() {
    return regenerator;
  }

  public AreaRegenerator areaRegenerator() {
    return areaRegenerator;
  }

  public SetupService setup() {
    return setup;
  }

  public SetupState setupState() {
    return setupState;
  }

  public FeatureGenerator features() {
    return features;
  }

  public ChunkIndicators indicators() {
    return indicators;
  }

  public RegenerationScheduler regenerationScheduler() {
    return regenerationScheduler;
  }

  public AdminDimensionService adminDimension() {
    return adminDimension;
  }

  public SnapshotStore snapshots() {
    return snapshots;
  }

  public RegenerationSimulator simulator() {
    return simulator;
  }

  public IndicatorHud indicatorHud() {
    return indicatorHud;
  }
}
