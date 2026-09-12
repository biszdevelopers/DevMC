package dev.bisz.combat.items;

import dev.bisz.items.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class ExperienceBottleItem extends CustomItem {

  public static final ItemId ID = ItemId.of("combat", "experience_bottle");

  public ExperienceBottleItem() {
    super(
      ID,
      ItemProperties.builder(Material.POTION)
        .maximumStackSize(1)
        .quality(Quality.RARE)
        .placeable(false)
        .metadata("xp_points", ItemDataType.INTEGER, 0)
        .build()
    );
  }

  @Override
  protected ItemStack createBaseStack(int amount) {
    ItemStack item = super.createBaseStack(amount);
    PotionMeta meta = (PotionMeta) item.getItemMeta();
    meta.setColor(Color.LIME);
    item.setItemMeta(meta);
    return item;
  }

  @Override
  protected String renderName(DevItemStack s, Player v) {
    return translate(v, "combat.item.xp.name", "Bottled Experience");
  }

  @Override
  protected List<String> renderLore(DevItemStack s, Player v) {
    return List.of(
      translate(
        v,
        "combat.item.xp.lore",
        "Contains %s experience points.",
        String.format("%,d", s.metadata("xp_points").map(Integer.class::cast).orElse(0))
      ),
            translate(
                    v,
                    "combat.item.xp.lore1",
                    "drink"
            )
    );
  }

  @Override
  protected void onConsumed(DevItemStack s, Player consumer) {
    int points = (Integer) s.metadata("xp_points").orElse(0);
    if (points > 0) consumer.giveExp(points);
  }
}
