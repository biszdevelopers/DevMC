package dev.bisz.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** Lossless Base64 codec for Bukkit item stacks, including plugin metadata. */
public final class ItemStackCodec {

  private ItemStackCodec() {}

  public static String encode(ItemStack item) {
    if (item == null || item.getType().isAir()) return "";
    try (
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes);
    ) {
      output.writeObject(item.clone());
      return Base64.getEncoder().encodeToString(bytes.toByteArray());
    } catch (IOException exception) {
      throw new IllegalArgumentException(
        "Cannot serialize item stack",
        exception
      );
    }
  }

  public static ItemStack decode(String encoded) {
    if (encoded == null || encoded.isBlank()) return null;
    try (
      BukkitObjectInputStream input = new BukkitObjectInputStream(
        new ByteArrayInputStream(Base64.getDecoder().decode(encoded))
      );
    ) {
      Object value = input.readObject();
      if (
        !(value instanceof ItemStack item)
      ) throw new IllegalArgumentException("Encoded value is not an ItemStack");
      return item.clone();
    } catch (
      IOException
      | ClassNotFoundException
      | IllegalArgumentException exception
    ) {
      throw new IllegalArgumentException(
        "Cannot deserialize item stack",
        exception
      );
    }
  }
}
