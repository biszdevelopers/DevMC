package dev.bisz.city;

import dev.bisz.bundler.JSON;
import dev.bisz.city.city.CityManager;
import dev.bisz.city.commands.CityAdminCommand;
import dev.bisz.city.commands.CityCommand;
import dev.bisz.city.commands.CityTestCommand;
import dev.bisz.city.commands.StructureCommand;
import dev.bisz.city.commands.VendorCommand;
import dev.bisz.city.config.CitySettings;
import dev.bisz.city.economy.PriceTable;
import dev.bisz.city.economy.SystemVendor;
import dev.bisz.city.integration.CurrencyGateway;
import dev.bisz.city.listener.ProtectionListener;
import dev.bisz.city.listener.PvpListener;
import dev.bisz.city.listener.RentScheduler;
import dev.bisz.city.listener.StructureListener;
import dev.bisz.city.listener.WildernessListener;
import dev.bisz.city.listener.ZoneListener;
import dev.bisz.city.loot.LootTables;
import dev.bisz.city.plot.PlotManager;
import dev.bisz.city.plot.ProtectionService;
import dev.bisz.city.police.PoliceService;
import dev.bisz.city.police.SearchService;
import dev.bisz.city.police.WantedService;
import dev.bisz.city.setup.ChunkPregenerator;
import dev.bisz.city.setup.SetupService;
import dev.bisz.city.structure.StructureService;
import dev.bisz.city.wilderness.ChunkRegenerator;
import dev.bisz.city.wilderness.DirtyChunkTracker;
import dev.bisz.city.wilderness.LootReseeder;
import dev.bisz.city.wilderness.MonumentManager;
import dev.bisz.city.wilderness.OreReseeder;
import dev.bisz.city.wilderness.RegenerationScheduler;
import dev.bisz.city.wilderness.Regenerators;
import dev.bisz.city.wilderness.RegionIndicators;
import dev.bisz.city.wilderness.SmallStructures;
import dev.bisz.commands.CommandRegistery;
import dev.bisz.players.locales.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entry point for the city plugin. */
public final class CityPlugin extends JavaPlugin {

  private static CityPlugin instance;

  private CitySettings settings;
  private CurrencyGateway currency;
  private CityManager cities;
  private PlotManager plots;
  private ProtectionService protection;
  private PriceTable prices;
  private SystemVendor vendor;
  private LootTables lootTables;
  private MonumentManager monuments;
  private StructureService structures;
  private WantedService police;
  private SearchService search;
  private DirtyChunkTracker tracker;
  private ChunkRegenerator regenerator;
  private RegionIndicators indicators;
  private RegenerationScheduler regenerationScheduler;
  private RentScheduler rentScheduler;
  private ChunkPregenerator pregenerator;
  private SetupService setup;

  /** Returns the loaded plugin instance. */
  public static CityPlugin instance() {
    return Objects.requireNonNull(instance, "City plugin is not loaded");
  }

  @Override
  public void onEnable() {
    instance = this;
    try {
      installLocaleDefaults();
      this.settings = CitySettings.load(this);
      this.currency = new CurrencyGateway();
      this.cities = new CityManager(this, settings);
      this.cities.load();
      this.plots = new PlotManager(this, settings, cities, currency);
      this.plots.load();
      this.protection = new ProtectionService(cities, plots);
      this.prices = PriceTable.load(this);
      this.vendor = new SystemVendor(prices, settings, currency);
      this.lootTables = LootTables.load(this);
      this.monuments = new MonumentManager(this, lootTables);
      this.monuments.load();
      this.structures = new StructureService(this, settings, lootTables);
      this.structures.load();
      this.pregenerator = new ChunkPregenerator(this, settings);
      this.setup = new SetupService(this);
      this.police = new WantedService(settings);
      this.search = new SearchService();
      this.tracker = new DirtyChunkTracker();
      this.regenerator = Regenerators.create(this);
      this.indicators = new RegionIndicators(this);
      this.indicators.load();
      OreReseeder ores = new OreReseeder(settings);
      LootReseeder loot = new LootReseeder(settings, lootTables);
      SmallStructures smallStructures = new SmallStructures(settings, lootTables);
      this.regenerationScheduler = new RegenerationScheduler(
        this,
        settings,
        indicators,
        regenerator,
        ores,
        smallStructures,
        loot,
        cities,
        monuments,
        tracker
      );
      this.regenerationScheduler.start();
      this.rentScheduler = new RentScheduler(this);
      this.rentScheduler.start();
      registerListeners();
      registerCommands();
      getLogger()
        .info(
          "City enabled with " +
          cities.all().size() +
          " cities and " +
          monuments.all().size() +
          " monuments."
        );
    } catch (RuntimeException exception) {
      getLogger().severe("City could not start: " + exception.getMessage());
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    if (regenerationScheduler != null) regenerationScheduler.stop();
    if (rentScheduler != null) rentScheduler.stop();
    if (pregenerator != null) pregenerator.stop();
    if (cities != null) cities.save();
    if (plots != null) plots.save();
    if (monuments != null) monuments.save();
    if (structures != null) structures.save();
    if (indicators != null) indicators.save();
    CommandRegistery.unregisterAll(this);
    if (instance == this) instance = null;
  }

  private void registerListeners() {
    for (Listener listener : new Listener[] {
      new ProtectionListener(this),
      new WildernessListener(this),
      new PvpListener(this),
      new ZoneListener(this),
      new StructureListener(this),
    }) {
      getServer().getPluginManager().registerEvents(listener, this);
    }
  }

  private void registerCommands() {
    CommandRegistery.register(this, new CityCommand(this));
    CommandRegistery.register(this, new CityAdminCommand(this));
    CommandRegistery.register(this, new VendorCommand(this));
    CommandRegistery.register(this, new StructureCommand(this));
    CommandRegistery.register(this, new CityTestCommand(this));
  }

  private void installLocaleDefaults() {
    for (String language : Locale.availableLanguages()) {
      String resource = "lang/" + language + ".json";
      try (InputStream input = getResource(resource)) {
        if (input == null) continue;
        JSON.mergeMissingDefaults(resource, input);
      } catch (IOException exception) {
        throw new IllegalStateException(
          "Cannot install bundled city locale file " + resource,
          exception
        );
      }
    }
    Locale.reload();
  }

  /** Reloads persisted cities, plots, monuments, and region indicators. */
  public void reloadData() {
    cities.load();
    plots.load();
    monuments.load();
    indicators.load();
  }

  public CitySettings settings() {
    return settings;
  }

  public CurrencyGateway currency() {
    return currency;
  }

  public CityManager cities() {
    return cities;
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

  public StructureService structures() {
    return structures;
  }

  public ChunkRegenerator regenerator() {
    return regenerator;
  }

  public ChunkPregenerator pregenerator() {
    return pregenerator;
  }

  public SetupService setup() {
    return setup;
  }

  public PoliceService police() {
    return police;
  }

  public SearchService search() {
    return search;
  }

  public DirtyChunkTracker tracker() {
    return tracker;
  }

  public RegionIndicators indicators() {
    return indicators;
  }

  public RegenerationScheduler regenerationScheduler() {
    return regenerationScheduler;
  }
}
