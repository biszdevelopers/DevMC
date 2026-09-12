package dev.bisz.bundler;

import java.io.InputStream;
import java.util.Map;

/** Legacy-style static access to Bundler's shared JSON files. */
public final class JSON {

  private JSON() {}

  public static Map<String, Object> loadDataFromDataBase(String name) {
    return BundlerPlugin.instance().jsonDatabase().loadDataFromDataBase(name);
  }

  public static void saveDataFromDataBase(String name, Map<String, ?> data) {
    BundlerPlugin.instance().jsonDatabase().saveDataFromDataBase(name, data);
  }

  public static int mergeMissingDefaults(String name, InputStream defaults) {
    return BundlerPlugin.instance()
      .jsonDatabase()
      .mergeMissingDefaults(name, defaults);
  }
}
