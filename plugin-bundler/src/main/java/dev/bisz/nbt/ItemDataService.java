/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.inventory.ItemStack
 */
package dev.bisz.nbt;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface ItemDataService {
  public Optional<String> getString(ItemStack var1, String var2);

  public ItemStack setString(ItemStack var1, String var2, String var3);

  public ItemStack remove(ItemStack var1, String var2);
}
