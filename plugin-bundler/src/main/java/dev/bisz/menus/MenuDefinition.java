package dev.bisz.menus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;

/**
 * Compatibility description for the first Bundler v2 menu API.
 * Prefer {@link SinglePageMenuTemplate#builder(String, int)} for new menus.
 */
@Deprecated
public record MenuDefinition(
  String title,
  int rows,
  Map<Integer, ItemStack> items,
  Map<Integer, Consumer<MenuClickContext>> clickHandlers
) {
  public MenuDefinition {
    Objects.requireNonNull(title, "title");
    if (rows < 1 || rows > 6) {
      throw new IllegalArgumentException("Menu rows must be 1 through 6");
    }
    LinkedHashMap<Integer, ItemStack> copies = new LinkedHashMap<>();
    Objects.requireNonNull(items, "items").forEach((slot, item) ->
      copies.put(slot, item.clone())
    );
    items = Map.copyOf(copies);
    clickHandlers = Map.copyOf(clickHandlers);
  }
}
