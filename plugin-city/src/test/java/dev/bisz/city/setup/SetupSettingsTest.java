package dev.bisz.city.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bisz.city.config.CitySettings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SetupSettingsTest {

  @Test
  void gamerulesAreParsedFromPrefixedKeys() {
    Map<String, Object> values = new HashMap<>();
    values.put("setup.gamerule.keepInventory", true);
    values.put("setup.gamerule.doFireTick", false);
    values.put("setup.gamerule.randomTickSpeed", 3);
    values.put("setup.border_size", 2500.0);
    values.put("pregen.radius_chunks", 6);

    CitySettings settings = CitySettings.from(values);
    assertEquals("true", settings.setupGamerules().get("keepInventory"));
    assertEquals("false", settings.setupGamerules().get("doFireTick"));
    assertEquals("3", settings.setupGamerules().get("randomTickSpeed"));
    assertEquals(2500.0, settings.setupBorderSize(), 1.0E-9);
    assertEquals(6, settings.pregenRadiusChunks());
  }

  @Test
  void setupDefaultsAreSane() {
    CitySettings settings = CitySettings.from(new HashMap<>());
    assertEquals("", settings.setupWorldName());
    assertEquals("NORMAL", settings.setupEnvironment());
    assertTrue(settings.setupSpawnCity());
    assertTrue(settings.setupGamerules().isEmpty());
    assertTrue(settings.pregenChunksPerTick() > 0);
  }

  @Test
  void reportSucceedsOnlyWhenEveryStepPasses() {
    SetupService.Report good = new SetupService.Report(
      List.of(
        new SetupService.Step("world", true, "ok"),
        new SetupService.Step("border", true, "ok")
      )
    );
    SetupService.Report bad = new SetupService.Report(
      List.of(
        new SetupService.Step("world", true, "ok"),
        new SetupService.Step("border", false, "no")
      )
    );
    assertTrue(good.success());
    assertFalse(bad.success());
  }
}
