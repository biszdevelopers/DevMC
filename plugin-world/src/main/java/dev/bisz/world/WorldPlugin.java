package dev.bisz.world;

import dev.bisz.bundler.JSON;
import dev.bisz.world.settlement.SettlementManager;
import dev.bisz.world.commands.WorldAdminCommand;
import dev.bisz.world.commands.SettlementCommand;
import dev.bisz.world.commands.VendorCommand;
import dev.bisz.world.commands.WorldInfoCommand;
import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.economy.PriceTable;
import dev.bisz.world.economy.SystemVendor;
import dev.bisz.world.feature.FeatureGenerator;
import dev.bisz.world.integration.CurrencyGateway;
import dev.bisz.world.integration.SelectionBridge;
import dev.bisz.world.integration.SelectionBridges;
import dev.bisz.world.listener.IndicatorHud;
import dev.bisz.world.listener.ProtectionListener;
import dev.bisz.world.listener.PvpListener;
import dev.bisz.world.listener.RentScheduler;
import dev.bisz.world.listener.WildernessIntegrityListener;
import dev.bisz.world.listener.WildernessListener;
import dev.bisz.world.listener.WorldJoinListener;
import dev.bisz.world.listener.ZoneListener;
import dev.bisz.world.loot.LootTables;
import dev.bisz.world.plot.PlotManager;
import dev.bisz.world.plot.ProtectionService;
import dev.bisz.world.police.PoliceService;
import dev.bisz.world.police.SearchService;
import dev.bisz.world.police.WantedService;
import dev.bisz.world.setup.AreaRegenerator;
import dev.bisz.world.setup.SetupService;
import dev.bisz.world.setup.SetupState;
import dev.bisz.world.sim.AdminDimensionService;
import dev.bisz.world.sim.RegenSelfTest;
import dev.bisz.world.sim.RegenerationSimulator;
import dev.bisz.world.snapshot.SnapshotStore;
import dev.bisz.world.structure.PoiService;
import dev.bisz.world.wilderness.ChunkIndicators;
import dev.bisz.world.wilderness.ChunkRegenerator;
import dev.bisz.world.wilderness.LootReseeder;
import dev.bisz.world.wilderness.MonumentManager;
import dev.bisz.world.wilderness.OreReseeder;
import dev.bisz.world.wilderness.RegenerationScheduler;
import dev.bisz.world.wilderness.Regenerators;
import dev.bisz.world.wilderness.SmallStructures;
import dev.bisz.world.worldgen.BareboneGenerator;
import dev.bisz.world.worldgen.WorldGenerators;
import dev.bisz.commands.CommandRegistery;
import dev.bisz.players.locales.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entry point for the world plugin. */
public final class WorldPlugin extends JavaPlugin {

  private static WorldPlugin instance;

  private WorldSettings settings;
  private CurrencyGateway currency;
  private SelectionBridge selection;
  private SettlementManager settlements;
  private PlotManager plots;
  private ProtectionService protection;
  private PriceTable prices;
  private SystemVendor vendor;
  private LootTables lootTables;
  private MonumentManager monuments;
  private PoiService pois;
  private WantedService police;
  private SearchService search;
  private ChunkRegenerator regenerator;
  private ChunkIndicators indicators;
  private RegenerationScheduler regenerationScheduler;
  private RentScheduler rentScheduler;
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
  public static WorldPlugin instance() {
    return Objects.requireNonNull(instance, "World plugin is not loaded");
  }

  @Override
  public void onEnable() {
    instance = this;
    try {
      installLocaleDefaults();
      this.settings = WorldSettings.load(this);
      this.currency = new CurrencyGateway();
      this.selection = SelectionBridges.create(this);
      this.settlements = new SettlementManager(this, settings);
      this.settlements.load();
      this.plots = new PlotManager(this, settings, settlements, currency);
      this.plots.load();
      this.protection = new ProtectionService(settlements, plots);
      this.prices = PriceTable.load(this);
      this.vendor = new SystemVendor(prices, settings, currency);
      this.lootTables = LootTables.load(this);
      this.monuments = new MonumentManager(this, lootTables);
      this.monuments.load();
      this.smallStructures = new SmallStructures(lootTables);
      this.pois = new PoiService(this, settings, lootTables, smallStructures);
      this.pois.load();
      this.areaRegenerator = new AreaRegenerator(this);
      this.setup = new SetupService(this);
      this.setupState = new SetupState(this);
      this.setupState.load();
      this.police = new WantedService(settings);
      this.search = new SearchService();
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
        features,
        settlements,
        monuments
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
      this.rentScheduler = new RentScheduler(this);
      this.rentScheduler.start();
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
          "World enabled with " +
          settlements.all().size() +
          " settlements and " +
          monuments.all().size() +
          " monuments."
        );
    } catch (RuntimeException exception) {
      getLogger().severe("World could not start: " + exception.getMessage());
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
   * {@code worlds: <name>: generator: world} entry in bukkit.yml). This keeps
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
    if (rentScheduler != null) rentScheduler.stop();
    if (areaRegenerator != null) areaRegenerator.stop();
    if (indicatorHud != null) indicatorHud.stop();
    if (settlements != null) settlements.save();
    if (plots != null) plots.save();
    if (monuments != null) monuments.save();
    if (indicators != null) indicators.save();
    if (snapshots != null) snapshots.save();
    CommandRegistery.unregisterAll(this);
    if (instance == this) instance = null;
  }

  private void registerListeners() {
    for (Listener listener : new Listener[] {
      new ProtectionListener(this),
      new WildernessListener(this),
      new WildernessIntegrityListener(this),
      new PvpListener(this),
      new ZoneListener(this),
      new WorldJoinListener(this),
      indicatorHud,
    }) {
      getServer().getPluginManager().registerEvents(listener, this);
    }
  }

  private void registerCommands() {
    CommandRegistery.register(this, new SettlementCommand(this));
    CommandRegistery.register(this, new WorldAdminCommand(this));
    CommandRegistery.register(this, new VendorCommand(this));
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

  /** Reloads persisted settlements, plots, monuments, and chunk indicators. */
  public void reloadData() {
    settlements.load();
    plots.load();
    monuments.load();
    indicators.load();
  }

  public WorldSettings settings() {
    return settings;
  }

  public CurrencyGateway currency() {
    return currency;
  }

  public SelectionBridge selection() {
    return selection;
  }

  public SettlementManager settlements() {
    return settlements;
  }

  public PlotManager plots() {
    return plots;
  }

  public ProtectionService protection() {
    return protection;
  }

  public PriceTable prices() {
    return prices;
  }

  public SystemVendor vendor() {
    return vendor;
  }

  public LootTables lootTables() {
    return lootTables;
  }

  public MonumentManager monuments() {
    return monuments;
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

  public PoliceService police() {
    return police;
  }

  public SearchService search() {
    return search;
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
