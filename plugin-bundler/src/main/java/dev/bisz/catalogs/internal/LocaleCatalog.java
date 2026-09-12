/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.catalogs.internal;

import java.util.Map;

public record LocaleCatalog(
  int schemaVersion,
  String language,
  Map<String, String> translations
) {}
