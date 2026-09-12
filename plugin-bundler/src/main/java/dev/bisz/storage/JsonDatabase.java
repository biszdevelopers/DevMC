/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class JsonDatabase implements AutoCloseable {

  private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_MAP =
    new TypeReference<LinkedHashMap<String, Object>>() {};
  private final Path root;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<Path, Object> fileLocks = new ConcurrentHashMap<
    Path,
    Object
  >();
  private final ExecutorService executor = Executors.newFixedThreadPool(
    2,
    runnable -> {
      Thread thread = new Thread(runnable, "Bundler-JSON");
      thread.setDaemon(true);
      return thread;
    }
  );

  public JsonDatabase(Path root) {
    this.root = Objects.requireNonNull(root, "root")
      .toAbsolutePath()
      .normalize();
    try {
      Files.createDirectories(this.root, new FileAttribute[0]);
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Cannot create JSON database directory " + String.valueOf(this.root),
        exception
      );
    }
  }

  public Path root() {
    return this.root;
  }

  public boolean installDefault(String name, InputStream defaultData) {
    Objects.requireNonNull(defaultData, "defaultData");
    Path path = this.resolve(name);
    Object object = this.lock(path);
    synchronized (object) {
      boolean bl;
      block10: {
        if (Files.exists(path, new LinkOption[0])) {
          return false;
        }
        Path temporary = null;
        try {
          Files.createDirectories(path.getParent(), new FileAttribute[0]);
          temporary = Files.createTempFile(
            path.getParent(),
            path.getFileName().toString(),
            ".tmp",
            new FileAttribute[0]
          );
          Files.copy(
            defaultData,
            temporary,
            StandardCopyOption.REPLACE_EXISTING
          );
          JsonDatabase.moveIntoPlace(temporary, path);
          bl = true;
          if (temporary == null) break block10;
        } catch (IOException exception) {
          try {
            throw new IllegalStateException(
              "Cannot install default JSON database file " +
              String.valueOf(path),
              exception
            );
          } catch (Throwable throwable) {
            if (temporary != null) {
              JsonDatabase.deleteTemporary(temporary);
            }
            throw throwable;
          }
        }
        JsonDatabase.deleteTemporary(temporary);
      }
      return bl;
    }
  }

  /*
   * WARNING - Removed try catching itself - possible behaviour change.
   */
  public int mergeMissingDefaults(String name, InputStream defaultData) {
    Objects.requireNonNull(defaultData, "defaultData");
    Path path = this.resolve(name);
    Object object = this.lock(path);
    synchronized (object) {
      Map defaults;
      try {
        defaults = this.mapper.readValue(defaultData, OBJECT_MAP);
      } catch (IOException exception) {
        throw new IllegalArgumentException(
          "Cannot read default JSON database data for " + String.valueOf(path),
          exception
        );
      }
      Map<String, Object> stored = this.readObject(path);
      int added = 0;
      for (Object rawEntry : defaults.entrySet()) {
        Map.Entry entry = (Map.Entry) rawEntry;
        if (stored.containsKey(entry.getKey())) continue;
        stored.put((String) entry.getKey(), entry.getValue());
        ++added;
      }
      if (added > 0) {
        this.writeObject(path, stored);
      }
      return added;
    }
  }

  /*
   * WARNING - Removed try catching itself - possible behaviour change.
   */
  public Map<String, Object> loadDataFromDataBase(String name) {
    Path path = this.resolve(name);
    Object object = this.lock(path);
    synchronized (object) {
      return this.readObject(path);
    }
  }

  /*
   * WARNING - Removed try catching itself - possible behaviour change.
   */
  public void saveDataFromDataBase(String name, Map<String, ?> data) {
    Path path = this.resolve(name);
    Object object = this.lock(path);
    synchronized (object) {
      this.writeObject(path, data);
    }
  }

  /*
   * WARNING - Removed try catching itself - possible behaviour change.
   */
  public <T> T updateObject(
    String name,
    Function<Map<String, Object>, T> update
  ) {
    Objects.requireNonNull(update, "update");
    Path path = this.resolve(name);
    Object object = this.lock(path);
    synchronized (object) {
      Map<String, Object> data = this.readObject(path);
      T result = update.apply(data);
      this.writeObject(path, data);
      return result;
    }
  }

  public <T> CompletionStage<T> supplyAsync(Supplier<T> operation) {
    return CompletableFuture.supplyAsync(operation, this.executor);
  }

  public Map<String, Object> objectValue(Object value) {
    if (!(value instanceof Map)) {
      return new LinkedHashMap<String, Object>();
    }
    return this.mapper.convertValue(value, OBJECT_MAP);
  }

  private Map<String, Object> readObject(Path path) {
    try {
      if (Files.notExists(path, new LinkOption[0])) {
        this.writeObject(path, Map.of());
        return new LinkedHashMap<String, Object>();
      }
      if (Files.size(path) == 0L) {
        return new LinkedHashMap<String, Object>();
      }
      return this.mapper.readValue(path.toFile(), OBJECT_MAP);
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Cannot read JSON database file " + String.valueOf(path),
        exception
      );
    }
  }

  /*
   * Enabled force condition propagation
   * Lifted jumps to return sites
   */
  private void writeObject(Path path, Map<String, ?> data) {
    Path temporary = null;
    try {
      Files.createDirectories(path.getParent(), new FileAttribute[0]);
      temporary = Files.createTempFile(
        path.getParent(),
        path.getFileName().toString(),
        ".tmp",
        new FileAttribute[0]
      );
      String json =
        this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data) +
        "\n";
      Files.writeString(
        temporary,
        (CharSequence) json,
        StandardCharsets.UTF_8,
        new OpenOption[0]
      );
      JsonDatabase.moveIntoPlace(temporary, path);
      if (temporary == null) return;
    } catch (IOException exception) {
      try {
        throw new IllegalStateException(
          "Cannot write JSON database file " + String.valueOf(path),
          exception
        );
      } catch (Throwable throwable) {
        if (temporary == null) throw throwable;
        JsonDatabase.deleteTemporary(temporary);
        throw throwable;
      }
    }
    JsonDatabase.deleteTemporary(temporary);
    return;
  }

  private Path resolve(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException(
        "JSON database filename cannot be blank"
      );
    }
    Path relative = Path.of(name.replace('\\', '/'), new String[0]);
    if (relative.isAbsolute()) {
      throw new IllegalArgumentException(
        "JSON database filename must be relative"
      );
    }
    Path resolved = this.root.resolve(relative).normalize();
    if (!resolved.startsWith(this.root) || resolved.equals(this.root)) {
      throw new IllegalArgumentException(
        "JSON database filename leaves the data directory"
      );
    }
    return resolved;
  }

  private Object lock(Path path) {
    return this.fileLocks.computeIfAbsent(path, ignored -> new Object());
  }

  private static void moveIntoPlace(Path temporary, Path path)
    throws IOException {
    try {
      Files.move(
        temporary,
        path,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      );
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteTemporary(Path temporary) {
    try {
      Files.deleteIfExists(temporary);
    } catch (IOException iOException) {
      // empty catch block
    }
  }

  @Override
  public void close() {
    this.executor.shutdown();
    try {
      if (!this.executor.awaitTermination(5L, TimeUnit.SECONDS)) {
        this.executor.shutdownNow();
      }
    } catch (InterruptedException exception) {
      this.executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
