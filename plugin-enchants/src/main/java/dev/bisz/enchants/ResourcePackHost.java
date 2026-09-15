package dev.bisz.enchants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Builds the mono7 font resource pack from the bundled jar assets, serves it
 * over HTTP, and sends it to each player on join so it loads automatically.
 */
public final class ResourcePackHost implements Listener {
  private static final String[] ENTRIES = {
    "pack.mcmeta",
    "README.md",
    "assets/minecraft/font/mono.json",
    "assets/minecraft/textures/font/ascii_mono.png"
  };

  private final EnchantsPlugin plugin;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private byte[] packBytes;
  private byte[] packHash;
  private String url;
  private boolean required;

  public ResourcePackHost(EnchantsPlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    if (!plugin.config().config().getBoolean("server.resource-pack.enabled", true)) return;
    try {
      packBytes = buildZip();
      if (packBytes.length == 0) {
        plugin.getLogger().warning("Resource pack is empty - not hosting.");
        return;
      }
      packHash = sha1(packBytes);
      int port = plugin.config().config().getInt("server.resource-pack.port", 9090);
      url = plugin.config().config().getString("server.resource-pack.url", "");
      if (url == null || url.isEmpty()) url = "http://localhost:" + port + "/resource-pack.zip";
      required = plugin.config().config().getBoolean("server.resource-pack.required", true);
      startServer(port);
      plugin.getLogger().info("Serving trueMC resource pack at " + url + (required ? " (required)" : ""));
    } catch (Throwable throwable) {
      plugin.getLogger().warning("Failed to start resource pack host: " + throwable);
    }
  }

  public void stop() {
    running.set(false);
  }

  private void startServer(int port) {
    running.set(true);
    Thread thread = new Thread(() -> {
      try (ServerSocket server = new ServerSocket(port)) {
        while (running.get()) {
          try (Socket client = server.accept()) {
            handle(client);
          } catch (IOException ignored) {
          }
        }
      } catch (IOException exception) {
        plugin.getLogger().warning("Resource pack HTTP server failed: " + exception);
      }
    }, "trueMC-resource-pack");
    thread.setDaemon(true);
    thread.start();
  }

  private void handle(Socket socket) {
    try {
      InputStream in = socket.getInputStream();
      int read = 0;
      int last = -1;
      while (read < 8192) {
        int b = in.read();
        if (b == -1) break;
        if (last == '\r' && b == '\n') {
          int c = in.read();
          if (c == '\r') {
            in.read();
            break;
          }
        }
        last = b;
        read++;
      }
      byte[] body = packBytes;
      OutputStream out = socket.getOutputStream();
      out.write(("HTTP/1.1 200 OK\r\n"
        + "Content-Type: application/zip\r\n"
        + "Content-Length: " + body.length + "\r\n"
        + "Connection: close\r\n"
        + "\r\n").getBytes(StandardCharsets.US_ASCII));
      out.write(body);
      out.flush();
    } catch (IOException ignored) {
    }
  }

  private void sendTo(Player player) {
    if (packBytes == null || player == null || !player.isOnline()) return;
    try {
      player.setResourcePack(url, packHash);
    } catch (Throwable throwable) {
      plugin.getLogger().warning("Failed to send resource pack to " + player.getName() + ": " + throwable);
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    sendTo(event.getPlayer());
  }

  private byte[] buildZip() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (String entry : ENTRIES) {
        InputStream in = plugin.getResource("resource-pack/" + entry);
        if (in == null) continue;
        zip.putNextEntry(new ZipEntry(entry));
        in.transferTo(zip);
        zip.closeEntry();
        in.close();
      }
    }
    return bytes.toByteArray();
  }

  private static byte[] sha1(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-1").digest(data);
    } catch (Exception exception) {
      return new byte[0];
    }
  }
}
