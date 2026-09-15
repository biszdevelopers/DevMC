package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AbilityDisplayTest {
  @Test void activeAbilityRendersNameDescriptionCooldownAndUsage() {
    Ability ability = Ability.builder("ability.test.double_jump")
      .name("Double Jump").description("Allows you to jump in the air.")
      .quality(Quality.COMMON).usage(AbilityUsageMethod.DOUBLE_JUMP).cooldownSeconds(3D).build();
    assertEquals(List.of(
      Quality.COMMON.colorCode() + "Double Jump",
      "§7Allows you to jump in the air.",
      "  §8Cooldown: §a3.0s",
      "  §8Use by §eDouble Jumping"
    ), AbilityDisplay.render(List.of(ability), null));
  }

  @Test void passiveAbilityUsesOnlyActivationIntervalLine() {
    Ability ability = Ability.builder("ability.test.aura")
      .name("Aura").description("Pulses around its holder.")
      .usage(AbilityUsageMethod.PASSIVE_HAND).activationIntervalSeconds(1.5D).build();
    assertEquals(List.of(
      Quality.COMMON.colorCode() + "Aura",
      "§7Pulses around its holder.",
      "  §8Activates every §e1.5 seconds §8(on hand)"
    ), AbilityDisplay.render(List.of(ability), null));
  }

  @Test void abilitiesAreSeparatedAndTimingModesAreValidated() {
    Ability active = Ability.builder("ability.test.active").name("Active").description("Active ability.")
      .usage(AbilityUsageMethod.RIGHT_CLICK).cooldownSeconds(0D).build();
    List<String> rendered = AbilityDisplay.render(List.of(active, active), null);
    assertEquals("  §8Cooldown: §aInstant!", rendered.get(2));
    assertEquals("", rendered.get(4));
    assertThrows(IllegalArgumentException.class, () -> Ability.builder("ability.test.invalid")
      .name("Invalid").description("Invalid ability.").usage(AbilityUsageMethod.PASSIVE_HAND)
      .cooldownSeconds(1D).activationIntervalSeconds(1D).build());
  }

  @Test void cooldownActionBarsUseFifteenSegmentsAndJoinWithADarkGrayDivider() {
    Ability doubleJump = Ability.builder("ability.test.double_jump").name("Double Jump")
      .description("Jump.").usage(AbilityUsageMethod.DOUBLE_JUMP).cooldownSeconds(3D).build();
    Ability shortbow = Ability.builder("ability.test.shortbow").name("Shortbow")
      .description("Shoot.").usage(AbilityUsageMethod.LEFT_CLICK).cooldownSeconds(.2D).build();
    AbilityCooldown jumpCooldown = AbilityCooldown.start(doubleJump, 1_000L);
    AbilityCooldown shortbowCooldown = AbilityCooldown.start(shortbow, 1_000L);

    String jump = jumpCooldown.actionBar(null, 2_500L);
    assertEquals(15, jump.chars().filter(character -> character == '|').count());
    assertTrue(jump.contains("§e1.5s"));
    assertEquals("§fDouble Jump §8- §b§lREADY", jumpCooldown.actionBar(null, 4_000L));
    assertEquals(jump + "§8|" + shortbowCooldown.actionBar(null, 1_000L),
      Ability.joinActionBars(List.of(jump, shortbowCooldown.actionBar(null, 1_000L))));
  }
}
