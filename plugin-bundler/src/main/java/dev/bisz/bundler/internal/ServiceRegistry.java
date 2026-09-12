/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.bundler.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ServiceRegistry {

  private final Map<Class<?>, Object> services = new LinkedHashMap();

  public <T> void register(Class<T> type, T implementation) {
    this.services.put(type, type.cast(implementation));
  }

  public <T> Optional<T> service(Class<T> serviceType) {
    return Optional.ofNullable(this.services.get(serviceType)).map(
      serviceType::cast
    );
  }

  public <T> T require(Class<T> serviceType) {
    return this.service(serviceType).orElseThrow(() ->
      new IllegalStateException(
        "Bundler service is unavailable: " + serviceType.getName()
      )
    );
  }
}
