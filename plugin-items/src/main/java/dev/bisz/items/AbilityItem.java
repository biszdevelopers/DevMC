package dev.bisz.items;

import java.util.List;

/** Opt-in, display-only contract for items that expose stack-specific abilities. */
public interface AbilityItem {
  List<Ability> abilities(DevItemStack stack);
}
