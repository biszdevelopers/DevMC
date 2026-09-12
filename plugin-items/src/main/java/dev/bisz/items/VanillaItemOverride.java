/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.items;

import dev.bisz.items.DevItemStack;

/**
 * Legacy load callback for a generated vanilla definition.
 *
 * @deprecated Extend OverrideVanillaItem and register the definition instead.
 */
@FunctionalInterface
@Deprecated(forRemoval = false)
public interface VanillaItemOverride {
  public void onLoaded(DevItemStack var1);
}
