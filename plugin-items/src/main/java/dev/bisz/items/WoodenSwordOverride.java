package dev.bisz.items;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Built-in example that adds a persistent attack counter to vanilla wooden swords. */
public final class WoodenSwordOverride extends OverrideDamageableVanillaItem {

  public static final String ATTACK_TIMES = "attack_times";

  public WoodenSwordOverride() {
    super(
      Material.WOODEN_SWORD,
      ItemProperties.builder(Material.WOODEN_SWORD)
        .metadata(ATTACK_TIMES, ItemDataType.INTEGER, 0)
        .attackTriggering(true)
        .build()
    );
  }

  @Override
  protected List<String> renderLore(DevItemStack stack, Player viewer) {
    int attacks = stack
      .metadata(ATTACK_TIMES)
      .map(Integer.class::cast)
      .orElse(0);
    return List.of(
      "§7" + translate(
        viewer,
        "item.minecraft.wooden_sword.attack_times",
        "Attack Times: %s",
        Integer.toString(attacks)
      )
    );
  }

  @Override
  protected void onAttack(
    DevItemStack stack,
    Player attacker,
    Entity target,
    EntityDamageByEntityEvent event
  ) {
    int attacks = stack
      .metadata(ATTACK_TIMES)
      .map(Integer.class::cast)
      .orElse(0);
    stack.metadata(ATTACK_TIMES, nextAttackCount(attacks));
  }

  static int nextAttackCount(int current) {
    if (current >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return Math.max(0, current) + 1;
  }
}
