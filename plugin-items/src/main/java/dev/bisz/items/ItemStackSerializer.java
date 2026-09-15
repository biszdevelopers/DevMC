/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.util.io.BukkitObjectInputStream
 *  org.bukkit.util.io.BukkitObjectOutputStream
 */
package dev.bisz.items;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ItemStackSerializer {

  private static final String FORMAT = "bukkit-bytes-v1";

  private ItemStackSerializer() {}

  /*
   * Enabled aggressive exception aggregation
   */
  public static Map<String, String> serialize(ItemStack stack) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      Map<String, String> map;
      try (
        BukkitObjectOutputStream output = new BukkitObjectOutputStream(
          (OutputStream) bytes
        );
      ) {
        output.writeObject((Object) Objects.requireNonNull(stack, "stack"));
        map = Map.of(
          "format",
          FORMAT,
          "data",
          Base64.getEncoder().encodeToString(bytes.toByteArray())
        );
      }
      return map;
    } catch (IOException exception) {
      throw new IllegalArgumentException(
        "Could not serialize item stack",
        exception
      );
    }
  }

  /** Serializes a stack into the version implied by this ItemLib build. */
  public static String serializePayload(ItemStack stack) {
    return serialize(stack).get("data");
  }

  public static ItemStack deserialize(Map<String, String> encoded) {
    Objects.requireNonNull(encoded, "encoded");
    if (!FORMAT.equals(encoded.get("format"))) {
      throw new IllegalArgumentException(
        "Unsupported item serialization format: " + encoded.get("format")
      );
    }
    byte[] data = Base64.getDecoder().decode(
      Objects.requireNonNull(encoded.get("data"), "data")
    );
    try (
      BukkitObjectInputStream input = new BukkitObjectInputStream(
        new ByteArrayInputStream(data)
      );
    ) {
      Object decoded = input.readObject();
      if (!(decoded instanceof ItemStack stack)) {
        throw new IllegalArgumentException(
          "Serialized data is not an ItemStack"
        );
      }
      return stack;
    } catch (IOException | ClassNotFoundException exception) {
      throw new IllegalArgumentException(
        "Invalid item serialization data",
        exception
      );
    }
  }

  /** Deserializes a payload previously returned by {@link #serializePayload}. */
  public static ItemStack deserializePayload(String payload) {
    return deserialize(Map.of("format", FORMAT, "data", Objects.requireNonNull(payload, "payload")));
  }
}
