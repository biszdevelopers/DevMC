package dev.bisz.city.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Defensive readers for the loosely typed JSON maps Bundler stores. */
public final class Json {

  private Json() {}

  /** Reads a string value, or the fallback when absent or of another type. */
  public static String string(
    Map<String, Object> map,
    String key,
    String fallback
  ) {
    Object value = map.get(key);
    return value instanceof String text ? text : fallback;
  }

  /** Reads an int value, or the fallback when absent or of another type. */
  public static int integer(
    Map<String, Object> map,
    String key,
    int fallback
  ) {
    Object value = map.get(key);
    return value instanceof Number number ? number.intValue() : fallback;
  }

  /** Reads a long value, or the fallback when absent or of another type. */
  public static long longValue(
    Map<String, Object> map,
    String key,
    long fallback
  ) {
    Object value = map.get(key);
    return value instanceof Number number ? number.longValue() : fallback;
  }

  /** Reads a double value, or the fallback when absent or of another type. */
  public static double decimal(
    Map<String, Object> map,
    String key,
    double fallback
  ) {
    Object value = map.get(key);
    return value instanceof Number number ? number.doubleValue() : fallback;
  }

  /** Reads a boolean value, or the fallback when absent or of another type. */
  public static boolean bool(
    Map<String, Object> map,
    String key,
    boolean fallback
  ) {
    Object value = map.get(key);
    return value instanceof Boolean flag ? flag : fallback;
  }

  /** Reads a list of strings, ignoring non-string entries. */
  public static List<String> stringList(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (!(value instanceof List<?> list)) return List.of();
    List<String> result = new ArrayList<>();
    for (Object element : list) {
      if (element instanceof String text) result.add(text);
    }
    return List.copyOf(result);
  }

  /** Reads a list of objects as maps, ignoring malformed entries. */
  public static List<Map<String, Object>> mapList(
    Map<String, Object> map,
    String key
  ) {
    Object value = map.get(key);
    if (!(value instanceof List<?> list)) return List.of();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object element : list) {
      Map<String, Object> converted = map(element);
      if (converted != null) result.add(converted);
    }
    return List.copyOf(result);
  }

  /** Converts an object to a string-keyed map when possible. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> map(Object value) {
    if (!(value instanceof Map<?, ?> raw)) return null;
    for (Object key : raw.keySet()) {
      if (!(key instanceof String)) return null;
    }
    return (Map<String, Object>) raw;
  }
}
