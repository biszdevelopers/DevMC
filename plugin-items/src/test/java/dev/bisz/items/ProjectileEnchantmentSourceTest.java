package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectileEnchantmentSourceTest {
  @Test void entityHitsKeepWeaponSnapshotsUntilDamageAndPiercingComplete() {
    assertFalse(EnchantmentRuntimeListener.shouldClearProjectileSource(true));
    assertTrue(EnchantmentRuntimeListener.shouldClearProjectileSource(false));
  }
}
