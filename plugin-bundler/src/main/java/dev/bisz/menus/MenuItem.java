package dev.bisz.menus;

import dev.bisz.chat.ChatUtils;
import dev.bisz.players.locales.Locale;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** A lazily rendered item and its optional menu click callback. */
public final class MenuItem {

  private final Function<Player, ItemStack> renderer;
  private final Consumer<MenuClickContext> clickAction;

  private MenuItem(
    Function<Player, ItemStack> renderer,
    Consumer<MenuClickContext> clickAction
  ) {
    this.renderer = renderer;
    this.clickAction = clickAction;
  }

  /** Starts an item builder using a Bukkit material. */
  public static Builder builder(Material material) {
    return new Builder(
      new ItemStack(Objects.requireNonNull(material, "material"))
    );
  }

  /** Starts an item builder using a defensive clone of an existing stack. */
  public static Builder builder(ItemStack item) {
    return new Builder(Objects.requireNonNull(item, "item").clone());
  }

  /** Creates an item whose complete stack is produced for each viewer. */
  public static MenuItem dynamic(Function<Player, ItemStack> renderer) {
    Objects.requireNonNull(renderer, "renderer");
    return new MenuItem(player -> requireStack(renderer.apply(player)), null);
  }

  /** Returns a copy of this item with a click callback. */
  public MenuItem onClick(Consumer<MenuClickContext> action) {
    return new MenuItem(renderer, Objects.requireNonNull(action, "action"));
  }

  ItemStack render(Player player) {
    return requireStack(renderer.apply(player)).clone();
  }

  Consumer<MenuClickContext> clickAction() {
    return clickAction;
  }

  private static ItemStack requireStack(ItemStack stack) {
    if (stack == null || stack.getType().isAir()) {
      throw new IllegalArgumentException(
        "A menu item renderer must return a non-air ItemStack"
      );
    }
    return stack;
  }

  /** Fluent builder for literal, localized, wrapped, and textured menu items. */
  public static final class Builder {

    private final ItemStack base;
    private int amount;
    private Function<Player, String> name;
    private final List<Function<Player, List<String>>> lore = new ArrayList<>();
    private String skullTexture;
    private Consumer<MenuClickContext> clickAction;
    private boolean glint;

    private Builder(ItemStack base) {
      this.base = base;
      this.amount = Math.max(1, base.getAmount());
    }

    /** Sets the stack amount. */
    public Builder amount(int amount) {
      if (amount < 1 || amount > base.getMaxStackSize()) {
        throw new IllegalArgumentException(
          "amount must be between 1 and " + base.getMaxStackSize()
        );
      }
      this.amount = amount;
      return this;
    }

    /** Sets a literal display name. */
    public Builder name(String value) {
      this.name = ignored -> Objects.requireNonNull(value, "value");
      return this;
    }

    /** Sets a display name calculated for the viewer. */
    public Builder name(Function<Player, String> value) {
      this.name = Objects.requireNonNull(value, "value");
      return this;
    }

    /** Sets a display name using Bundler's locale service. */
    public Builder localizedName(String key, Object... arguments) {
      Objects.requireNonNull(key, "key");
      Object[] copied = arguments.clone();
      this.name = player -> Locale.get(player, key, copied);
      return this;
    }

    /** Appends literal lore lines without wrapping. */
    public Builder lore(String... lines) {
      List<String> copied = List.of(lines.clone());
      lore.add(ignored -> copied);
      return this;
    }

    /** Appends viewer-specific lore lines. */
    public Builder lore(Function<Player, List<String>> lines) {
      lore.add(Objects.requireNonNull(lines, "lines"));
      return this;
    }

    /** Appends a translated lore entry without wrapping. */
    public Builder localizedLore(String key, Object... arguments) {
      Objects.requireNonNull(key, "key");
      Object[] copied = arguments.clone();
      lore.add(player ->
        List.of(Locale.get(player, key, copied).split("\\R", -1))
      );
      return this;
    }

    /** Appends literal lore wrapped to a fixed visible-character width. */
    public Builder wrappedLore(String text, int width) {
      if (width < 1) throw new IllegalArgumentException(
        "width must be positive"
      );
      lore.add(ignored ->
        List.of(ChatUtils.wrapTextColor(text, width).split("\\R", -1))
      );
      return this;
    }

    /** Appends literal lore using the viewing player's language-aware item width. */
    public Builder languageWrappedLore(String text) {
      Objects.requireNonNull(text, "text");
      lore.add(player ->
        List.of(
          ChatUtils.wrapWithColor(text, Locale.getLanguage(player)).split(
            "\\R",
            -1
          )
        )
      );
      return this;
    }

    /** Appends translated lore using Bundler's language-aware item width. */
    public Builder wrappedLocalizedLore(String key, Object... arguments) {
      Objects.requireNonNull(key, "key");
      Object[] copied = arguments.clone();
      lore.add(player ->
        List.of(
          ChatUtils.wrapWithColor(
            Locale.get(player, key, copied),
            Locale.getLanguage(player)
          ).split("\\R", -1)
        )
      );
      return this;
    }

    /** Applies a textures.minecraft.net skin URL to a PLAYER_HEAD item. */
    public Builder skullTexture(String url) {
      if (base.getType() != Material.PLAYER_HEAD) {
        throw new IllegalStateException(
          "skullTexture requires Material.PLAYER_HEAD"
        );
      }
      this.skullTexture = Objects.requireNonNull(url, "url");
      return this;
    }

    /** Adds the enchanted glint without displaying a synthetic enchantment. */
    public Builder glint() {
      this.glint = true;
      return this;
    }

    /** Enables or disables the synthetic enchanted glint. */
    public Builder glint(boolean value) {
      this.glint = value;
      return this;
    }

    /** Runs the supplied callback after Bundler cancels the click. */
    public Builder onClick(Consumer<MenuClickContext> action) {
      this.clickAction = Objects.requireNonNull(action, "action");
      return this;
    }

    /** Builds the immutable menu item. */
    public MenuItem build() {
      ItemStack source = base.clone();
      return new MenuItem(
        player -> {
          ItemStack rendered = source.clone();
          rendered.setAmount(amount);
          ItemMeta meta = rendered.getItemMeta();
          if (meta == null) throw new IllegalArgumentException(
            "Item has no mutable metadata: " + rendered.getType()
          );
          if (name != null) meta.setDisplayName(
            Objects.requireNonNull(name.apply(player), "rendered name")
          );
          if (!lore.isEmpty()) {
            ArrayList<String> lines = new ArrayList<>();
            for (Function<Player, List<String>> provider : lore) {
              List<String> supplied = Objects.requireNonNull(
                provider.apply(player),
                "rendered lore"
              );
              supplied.forEach(line ->
                lines.add(Objects.requireNonNull(line, "lore line"))
              );
            }
            meta.setLore(lines);
          }
          if (glint) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
          }
          if (skullTexture != null) {
            if (!(meta instanceof SkullMeta skull)) {
              throw new IllegalStateException(
                "PLAYER_HEAD did not provide SkullMeta"
              );
            }
            var profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            try {
              profile.getTextures().setSkin(URI.create(skullTexture).toURL());
            } catch (
              IllegalArgumentException
              | MalformedURLException exception
            ) {
              throw new IllegalArgumentException(
                "Invalid skull texture URL: " + skullTexture,
                exception
              );
            }
            skull.setOwnerProfile(profile);
          }
          rendered.setItemMeta(meta);
          return rendered;
        },
        clickAction
      );
    }
  }
}
