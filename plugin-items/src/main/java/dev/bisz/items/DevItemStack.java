package dev.bisz.items;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Bukkit stack together with its ItemLib definition and PDC-backed state. */
public final class DevItemStack {
  static final String ID_KEY = "item-id";
  static final String UNIQUE_KEY = "unique-id";
  static final String CUSTOM_NAME_KEY = "custom-name";
  static final String LANGUAGE_KEY = "lang";
  static final String RENDER_SIGNATURE_KEY = "render-signature";
  static final String LEGACY_ENCHANTMENT_SIGNATURE_KEY = "enchantment-signature";
  static final String CUSTOM_ENCHANTMENTS_KEY = "custom-enchantments";
  static final String ENCHANTMENT_METADATA_KEY = "enchantment-metadata";
  static final String META_PREFIX = "meta/";
  private final DevItem definition;
  private final ItemStack bukkit;
  private final List<String> runtimeLore = new ArrayList<>();

  DevItemStack(DevItem definition, ItemStack bukkit) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.bukkit = Objects.requireNonNull(bukkit, "bukkit");
  }

  public DevItem definition() { return definition; }
  public ItemStack bukkitStack() { return bukkit; }

  void initialize() {
    edit(container -> {
      if (!definition.vanilla()) container.set(key(ID_KEY), PersistentDataType.STRING, definition.id().toString());
      for (ItemMetadata entry : definition.properties().metadata().values()) {
        if (!has(container, META_PREFIX + entry.key(), entry.type())) set(container, META_PREFIX + entry.key(), entry.type(), entry.defaultValue());
      }
      if (definition.properties().maximumStackSize() == 1 && !container.has(key(UNIQUE_KEY), PersistentDataType.STRING)) {
        container.set(key(UNIQUE_KEY), PersistentDataType.STRING, UUID.randomUUID().toString());
      }
    });
  }

  public Optional<String> customName() { return read(CUSTOM_NAME_KEY, ItemDataType.STRING).map(String.class::cast); }
  public void customName(String value) {
    edit(container -> {
      if (value == null || value.isBlank()) container.remove(key(CUSTOM_NAME_KEY));
      else container.set(key(CUSTOM_NAME_KEY), PersistentDataType.STRING, value);
      clearRenderSignature(container);
    });
  }
  public Optional<Object> metadata(String name) {
    ItemMetadata declared = definition.properties().metadata().get(name);
    return declared == null ? Optional.empty() : read(META_PREFIX + name, declared.type()).or(() -> Optional.of(declared.defaultValue()));
  }
  public void metadata(String name, Object value) {
    ItemMetadata declared = definition.properties().metadata().get(name);
    if (declared == null) throw new IllegalArgumentException("Undeclared metadata: " + name);
    if (!ItemMetadata.matches(declared.type(), value)) throw new IllegalArgumentException("Metadata type mismatch: " + name);
    edit(container -> {
      set(container, META_PREFIX + name, declared.type(), value);
      clearRenderSignature(container);
    });
  }
  public Optional<UUID> uniqueId() { return read(UNIQUE_KEY, ItemDataType.STRING).map(value -> UUID.fromString((String) value)); }
  public boolean sameItem(DevItemStack other) { return uniqueId().map(id -> other.uniqueId().map(id::equals).orElse(false)).orElseGet(() -> bukkit.isSimilar(other.bukkit)); }

  /** All known native and custom enchantments on this stack. */
  public Map<DevEnchantment, Integer> enchantments() {
    Map<DevEnchantment, Integer> result = new LinkedHashMap<>();
    EnchantmentRegistry registry = ItemsPlugin.instance().enchantments();
    for (Map.Entry<EnchantmentId, Integer> entry : enchantmentLevels().entrySet()) {
      registry.get(entry.getKey()).ifPresent(enchantment -> result.put(enchantment, entry.getValue()));
    }
    return Map.copyOf(result);
  }
  public Optional<Integer> enchantmentLevel(DevEnchantment enchantment) { return Optional.ofNullable(enchantmentLevels().get(Objects.requireNonNull(enchantment, "enchantment").id())); }

  /** All known enchantments together with their per-item metadata. */
  public Map<DevEnchantment, EnchantmentData> enchantmentData() {
    Map<DevEnchantment, EnchantmentData> result = new LinkedHashMap<>();
    EnchantmentRegistry registry = ItemsPlugin.instance().enchantments();
    Map<EnchantmentId, Map<NamespacedKey, Object>> metadata = enchantmentMetadataValues();
    enchantmentLevels().forEach((id, level) -> registry.get(id).ifPresent(enchantment ->
      result.put(enchantment, new EnchantmentData(level, metadata.getOrDefault(id, Map.of())))
    ));
    return Map.copyOf(result);
  }

  /** Returns immutable metadata for one applied enchantment. */
  public Map<NamespacedKey, Object> enchantmentMetadata(DevEnchantment enchantment) {
    EnchantmentId id = Objects.requireNonNull(enchantment, "enchantment").id();
    return enchantmentMetadataValues().getOrDefault(id, Map.of());
  }

  /** Sets one Boolean or Integer metadata value on an applied enchantment. */
  public void setEnchantmentMetadata(DevEnchantment enchantment, NamespacedKey key, Object value) {
    EnchantmentId id = requireApplied(enchantment);
    validateMetadataValue(key, value);
    Map<EnchantmentId, Map<NamespacedKey, Object>> all = mutableMetadata();
    LinkedHashMap<NamespacedKey, Object> values = new LinkedHashMap<>(all.getOrDefault(id, Map.of()));
    values.put(key, value);
    all.put(id, values);
    writeEnchantmentMetadata(all);
    invalidateRender();
  }

  /** Removes one metadata value and returns whether it existed. */
  public boolean removeEnchantmentMetadata(DevEnchantment enchantment, NamespacedKey key) {
    EnchantmentId id = Objects.requireNonNull(enchantment, "enchantment").id();
    Map<EnchantmentId, Map<NamespacedKey, Object>> all = mutableMetadata();
    LinkedHashMap<NamespacedKey, Object> values = new LinkedHashMap<>(all.getOrDefault(id, Map.of()));
    if (values.remove(Objects.requireNonNull(key, "key")) == null) return false;
    if (values.isEmpty()) all.remove(id); else all.put(id, values);
    writeEnchantmentMetadata(all);
    invalidateRender();
    return true;
  }

  /** Adds or replaces a level. Vanilla levels remain in native Minecraft tags. */
  public void enchant(DevEnchantment enchantment, int level) {
    Objects.requireNonNull(enchantment, "enchantment"); validateLevel(level);
    if (enchantment.vanilla()) {
      VanillaEnchantment vanilla = (VanillaEnchantment) enchantment;
      ItemMeta meta = requiredMeta();
      if (meta instanceof EnchantmentStorageMeta book) book.addStoredEnchant(vanilla.bukkit(), level, true); else meta.addEnchant(vanilla.bukkit(), level, true);
      bukkit.setItemMeta(meta); invalidateRender();
      return;
    }
    Map<EnchantmentId, Integer> entries = customEnchantmentLevels(); entries.put(enchantment.id(), level); writeCustomEnchantmentLevels(entries); invalidateRender();
  }

  /** Adds or replaces an enchantment and atomically replaces its metadata. */
  public void enchant(
    DevEnchantment enchantment,
    int level,
    Map<NamespacedKey, Object> metadata
  ) {
    Objects.requireNonNull(metadata, "metadata").forEach(DevItemStack::validateMetadataValue);
    enchant(enchantment, level);
    Map<EnchantmentId, Map<NamespacedKey, Object>> all = mutableMetadata();
    if (metadata.isEmpty()) all.remove(enchantment.id());
    else all.put(enchantment.id(), new LinkedHashMap<>(metadata));
    writeEnchantmentMetadata(all);
    invalidateRender();
  }
  public boolean removeEnchantment(DevEnchantment enchantment) {
    Objects.requireNonNull(enchantment, "enchantment");
    boolean removed;
    if (enchantment.vanilla()) {
      VanillaEnchantment vanilla = (VanillaEnchantment) enchantment; ItemMeta meta = requiredMeta();
      boolean present = meta instanceof EnchantmentStorageMeta book ? book.hasStoredEnchant(vanilla.bukkit()) : meta.hasEnchant(vanilla.bukkit());
      if (meta instanceof EnchantmentStorageMeta book) book.removeStoredEnchant(vanilla.bukkit()); else meta.removeEnchant(vanilla.bukkit());
      bukkit.setItemMeta(meta); removed = present;
    } else {
      Map<EnchantmentId, Integer> entries = customEnchantmentLevels(); removed = entries.remove(enchantment.id()) != null;
      if (removed) writeCustomEnchantmentLevels(entries);
    }
    if (removed) {
      Map<EnchantmentId, Map<NamespacedKey, Object>> metadata = mutableMetadata();
      metadata.remove(enchantment.id());
      writeEnchantmentMetadata(metadata);
      invalidateRender();
    }
    return removed;
  }
  /** Known custom entries only; unknown serialized IDs remain preserved. */
  public Map<DevEnchantment, Integer> customEnchantments() {
    Map<DevEnchantment, Integer> result = new LinkedHashMap<>(); EnchantmentRegistry registry = ItemsPlugin.instance().enchantments();
    for (Map.Entry<EnchantmentId, Integer> entry : customEnchantmentLevels().entrySet()) registry.get(entry.getKey()).filter(enchantment -> !enchantment.vanilla()).ifPresent(enchantment -> result.put(enchantment, entry.getValue()));
    return Map.copyOf(result);
  }
  /** Copies the entire custom payload, including unknown IDs, to this stack. */
  public void copyCustomEnchantmentsFrom(DevItemStack source) {
    Objects.requireNonNull(source, "source");
    var previousCustom = customEnchantmentLevels().keySet();
    var sourceCustom = source.customEnchantmentLevels().keySet();
    Map<EnchantmentId, Map<NamespacedKey, Object>> metadata = mutableMetadata();
    previousCustom.forEach(metadata::remove);
    Map<EnchantmentId, Map<NamespacedKey, Object>> sourceMetadata = source.enchantmentMetadataValues();
    sourceCustom.forEach(id -> {
      Map<NamespacedKey, Object> values = sourceMetadata.get(id);
      if (values != null && !values.isEmpty()) metadata.put(id, new LinkedHashMap<>(values));
    });
    String encoded = source.readCustomPayload();
    edit(container -> { if (encoded == null) container.remove(key(CUSTOM_ENCHANTMENTS_KEY)); else container.set(key(CUSTOM_ENCHANTMENTS_KEY), PersistentDataType.STRING, encoded); });
    writeEnchantmentMetadata(metadata);
    invalidateRender();
  }

  /** Applies ItemLib's intersection-based metadata rules to an anvil result. */
  void mergeAnvilEnchantmentMetadata(DevItemStack left, DevItemStack right) {
    Objects.requireNonNull(left, "left"); Objects.requireNonNull(right, "right");
    Map<EnchantmentId, Map<NamespacedKey, Object>> leftAll = left.enchantmentMetadataValues();
    Map<EnchantmentId, Map<NamespacedKey, Object>> rightAll = right.enchantmentMetadataValues();
    Map<EnchantmentId, Map<NamespacedKey, Object>> merged = new TreeMap<>(Comparator.comparing(EnchantmentId::toString));
    for (EnchantmentId id : enchantmentLevels().keySet()) {
      Map<NamespacedKey, Object> leftValues = leftAll.getOrDefault(id, Map.of());
      Map<NamespacedKey, Object> rightValues = rightAll.getOrDefault(id, Map.of());
      Map<NamespacedKey, Object> values = mergeMetadataValues(leftValues, rightValues);
      if (!values.isEmpty()) merged.put(id, values);
    }
    writeEnchantmentMetadata(merged);
    invalidateRender();
  }

  static Map<NamespacedKey, Object> mergeMetadataValues(
    Map<NamespacedKey, Object> left,
    Map<NamespacedKey, Object> right
  ) {
    LinkedHashMap<NamespacedKey, Object> values = new LinkedHashMap<>();
    left.forEach((metadataKey, leftValue) -> {
      Object rightValue = right.get(metadataKey);
      if (leftValue instanceof Boolean leftBoolean && rightValue instanceof Boolean rightBoolean) {
        values.put(metadataKey, leftBoolean && rightBoolean);
      } else if (leftValue instanceof Integer leftInteger && rightValue instanceof Integer rightInteger) {
        values.put(metadataKey, Math.min(leftInteger, rightInteger));
      }
    });
    return Map.copyOf(values);
  }

  void clearEnchantmentMetadata() {
    writeEnchantmentMetadata(Map.of());
    invalidateRender();
  }
  /** Preserves custom-item identity through Bukkit operations that rebuild ItemMeta. */
  public void copyItemIdentityFrom(DevItemStack source) {
    Objects.requireNonNull(source, "source");
    String identity = source.read(ID_KEY, ItemDataType.STRING).map(String.class::cast).orElse(null);
    edit(container -> {
      if (identity == null) container.remove(key(ID_KEY));
      else container.set(key(ID_KEY), PersistentDataType.STRING, identity);
    });
  }

  public void render(Player viewer) {
    if (viewer == null) { edit(container -> { container.remove(key(LANGUAGE_KEY)); clearRenderSignature(container); }); return; }
    definition.render(this, viewer);
  }
  public Optional<String> renderedLanguage() { return read(LANGUAGE_KEY, ItemDataType.STRING).map(String.class::cast); }
  public boolean isRenderedFor(Player viewer) {
    if (viewer == null) return false;
    String language = ItemTranslations.language(viewer); String signature = renderSignature();
    return renderedLanguage().map(ItemTranslations::normalize).filter(language::equals).isPresent()
      && read(RENDER_SIGNATURE_KEY, ItemDataType.STRING).map(String.class::cast).filter(signature::equals).isPresent();
  }
  public void appendRuntimeLore(String line) { runtimeLore.add(Objects.requireNonNull(line, "line")); }

  List<String> renderEnchantmentLore(Player viewer) {
    List<String> lore = new ArrayList<>(); EnchantmentRegistry registry = ItemsPlugin.instance().enchantments();
    Map<EnchantmentId, Map<NamespacedKey, Object>> metadata = enchantmentMetadataValues();
    List<Map.Entry<EnchantmentId, Integer>> entries = new ArrayList<>(enchantmentLevels().entrySet());
    entries.sort((left, right) -> {
      DevEnchantment leftEnchantment = registry.get(left.getKey()).orElse(null);
      DevEnchantment rightEnchantment = registry.get(right.getKey()).orElse(null);
      int byQuality = Integer.compare(qualityOf(rightEnchantment).ordinal(), qualityOf(leftEnchantment).ordinal());
      if (byQuality != 0) return byQuality;
      int byLevel = Integer.compare(right.getValue(), left.getValue());
      if (byLevel != 0) return byLevel;
      return nameOf(left.getKey(), leftEnchantment, viewer).toLowerCase(Locale.ROOT)
        .compareTo(nameOf(right.getKey(), rightEnchantment, viewer).toLowerCase(Locale.ROOT));
    });
    for (Map.Entry<EnchantmentId, Integer> entry : entries) {
      DevEnchantment enchantment = registry.get(entry.getKey()).orElse(null);
      if (enchantment == null) {
        String plain = ItemTranslations.humanize(entry.getKey().path()) + " " + RomanNumerals.format(entry.getValue());
        lore.add(EnchantmentDisplay.extraRoll(metadata.getOrDefault(entry.getKey(), Map.of())) ? "§e✎ " + plain : "§7" + plain);
      } else {
        lore.add(EnchantmentDisplay.renderLine(
          enchantment,
          new EnchantmentData(entry.getValue(), metadata.getOrDefault(entry.getKey(), Map.of())),
          viewer
        ));
      }
    }
    return lore;
  }
  void applyDisplay(String name, List<String> enchantmentLore, List<String> extraLore, String language) {
    ItemMeta meta = bukkit.getItemMeta(); if (meta == null) return;
    meta.addItemFlags(ItemFlag.values()); meta.setDisplayName(name); List<String> lore = new ArrayList<>();
    String quality = ItemTranslations.translate(language, "itemmeta.quality." + definition.properties().quality().name(), ItemTranslations.humanize(definition.properties().quality().name()));
    lore.add(ItemTranslations.translate(language, "itemmeta.general.quality", "§7Quality: %s", definition.properties().quality().colorCode() + quality));
    if (!definition.properties().tradeable()) lore.add("§6🚷 " + ItemTranslations.translate(language, "itemmeta.untradable", "Not tradeable"));
    if (!definition.properties().category().isEmpty()) lore.add("§8" + ItemTranslations.translate(language, "itemmeta.category." + definition.properties().category(), definition.properties().category()));
    if (!enchantmentLore.isEmpty()) {
      lore.add(" ");
      lore.addAll(enchantmentLore);
    }
    if (!extraLore.isEmpty() || !runtimeLore.isEmpty()) { lore.add(" "); lore.addAll(extraLore); lore.addAll(runtimeLore); }
    meta.setLore(lore);
    meta.getPersistentDataContainer().set(key(LANGUAGE_KEY), PersistentDataType.STRING, ItemTranslations.normalize(language));
    meta.getPersistentDataContainer().remove(key(LEGACY_ENCHANTMENT_SIGNATURE_KEY));
    meta.getPersistentDataContainer().set(key(RENDER_SIGNATURE_KEY), PersistentDataType.STRING, renderSignature());
    bukkit.setItemMeta(meta);
  }

  private Map<EnchantmentId, Integer> enchantmentLevels() {
    Map<EnchantmentId, Integer> result = new TreeMap<>(Comparator.comparing(EnchantmentId::toString));
    bukkit.getEnchantments().forEach((enchantment, level) -> result.put(fromBukkit(enchantment), level));
    ItemMeta meta = bukkit.getItemMeta();
    if (meta instanceof EnchantmentStorageMeta book) book.getStoredEnchants().forEach((enchantment, level) -> result.put(fromBukkit(enchantment), level));
    result.putAll(customEnchantmentLevels()); return result;
  }
  private static EnchantmentId fromBukkit(Enchantment enchantment) { return EnchantmentId.of(enchantment.getKey().getNamespace(), enchantment.getKey().getKey()); }
  private static Quality qualityOf(DevEnchantment enchantment) { return enchantment == null ? Quality.COMMON : enchantment.properties().quality(); }
  private static String nameOf(EnchantmentId id, DevEnchantment enchantment, Player viewer) { return enchantment == null ? ItemTranslations.humanize(id.path()) : enchantment.name(viewer); }
  private String enchantmentSignature() {
    StringBuilder result = new StringBuilder("v2");
    Map<EnchantmentId, Map<NamespacedKey, Object>> metadata = enchantmentMetadataValues();
    enchantmentLevels().forEach((id, level) -> {
      result.append('|').append(id).append('=').append(level);
      metadata.getOrDefault(id, Map.of()).entrySet().stream()
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(NamespacedKey::toString)))
        .forEach(entry -> result.append(';').append(entry.getKey()).append('=').append(entry.getValue().getClass().getSimpleName()).append(':').append(entry.getValue()));
    });
    return result.toString();
  }

  private String renderSignature() {
    StringBuilder result = new StringBuilder("v3|definition=")
      .append(definition.getClass().getName());
    customName().ifPresent(value -> result
      .append("|name=")
      .append(value.length())
      .append(':')
      .append(value));
    definition.properties().metadata().entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .forEach(entry -> metadata(entry.getKey()).ifPresent(value -> result
        .append("|metadata=")
        .append(entry.getKey())
        .append(':')
        .append(value.getClass().getSimpleName())
        .append(':')
        .append(value)));
    return result.append("|enchantments=").append(enchantmentSignature()).toString();
  }
  private Map<EnchantmentId, Integer> customEnchantmentLevels() { return decodeCustomEnchantments(readCustomPayload()); }
  private String readCustomPayload() { ItemMeta meta = bukkit.getItemMeta(); return meta == null ? null : meta.getPersistentDataContainer().get(key(CUSTOM_ENCHANTMENTS_KEY), PersistentDataType.STRING); }
  private void writeCustomEnchantmentLevels(Map<EnchantmentId, Integer> entries) {
    String encoded = encodeCustomEnchantments(entries);
    edit(container -> { if (encoded == null) container.remove(key(CUSTOM_ENCHANTMENTS_KEY)); else container.set(key(CUSTOM_ENCHANTMENTS_KEY), PersistentDataType.STRING, encoded); });
  }
  private void invalidateRender() {
    edit(DevItemStack::clearRenderSignature);
  }

  private static void clearRenderSignature(PersistentDataContainer container) {
    container.remove(key(RENDER_SIGNATURE_KEY));
    container.remove(key(LEGACY_ENCHANTMENT_SIGNATURE_KEY));
  }

  private EnchantmentId requireApplied(DevEnchantment enchantment) {
    EnchantmentId id = Objects.requireNonNull(enchantment, "enchantment").id();
    if (!enchantmentLevels().containsKey(id)) throw new IllegalStateException(
      "Cannot attach metadata to an enchantment that is not on the item: " + id
    );
    return id;
  }

  private Map<EnchantmentId, Map<NamespacedKey, Object>> mutableMetadata() {
    Map<EnchantmentId, Map<NamespacedKey, Object>> result = new TreeMap<>(Comparator.comparing(EnchantmentId::toString));
    enchantmentMetadataValues().forEach((id, values) -> result.put(id, new LinkedHashMap<>(values)));
    return result;
  }

  private Map<EnchantmentId, Map<NamespacedKey, Object>> enchantmentMetadataValues() {
    ItemMeta meta = bukkit.getItemMeta();
    if (meta == null) return Map.of();
    String encoded = meta.getPersistentDataContainer().get(key(ENCHANTMENT_METADATA_KEY), PersistentDataType.STRING);
    return decodeEnchantmentMetadata(encoded);
  }

  private void writeEnchantmentMetadata(Map<EnchantmentId, Map<NamespacedKey, Object>> values) {
    String encoded = encodeEnchantmentMetadata(values);
    edit(container -> {
      if (encoded == null) container.remove(key(ENCHANTMENT_METADATA_KEY));
      else container.set(key(ENCHANTMENT_METADATA_KEY), PersistentDataType.STRING, encoded);
    });
  }

  static Map<EnchantmentId, Map<NamespacedKey, Object>> decodeEnchantmentMetadata(String encoded) {
    Map<EnchantmentId, Map<NamespacedKey, Object>> result = new TreeMap<>(Comparator.comparing(EnchantmentId::toString));
    if (encoded == null || encoded.isBlank()) return result;
    String[] pieces = encoded.split("\\|", -1);
    if (pieces.length == 0 || !"v1".equals(pieces[0])) return result;
    for (int index = 1; index < pieces.length; index++) {
      String[] fields = pieces[index].split(",", -1);
      if (fields.length != 4) continue;
      try {
        EnchantmentId id = EnchantmentId.parse(decodeToken(fields[0]));
        NamespacedKey metadataKey = NamespacedKey.fromString(decodeToken(fields[1]));
        if (metadataKey == null) continue;
        Object value = switch (fields[2]) {
          case "b" -> switch (fields[3]) { case "1" -> true; case "0" -> false; default -> throw new IllegalArgumentException(); };
          case "i" -> Integer.valueOf(fields[3]);
          default -> throw new IllegalArgumentException();
        };
        result.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(metadataKey, value);
      } catch (IllegalArgumentException ignored) {}
    }
    result.replaceAll((id, values) -> Map.copyOf(values));
    return result;
  }

  static String encodeEnchantmentMetadata(Map<EnchantmentId, Map<NamespacedKey, Object>> values) {
    StringBuilder result = new StringBuilder("v1");
    values.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(EnchantmentId::toString))).forEach(enchantment ->
      enchantment.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(NamespacedKey::toString))).forEach(metadata -> {
        validateMetadataValue(metadata.getKey(), metadata.getValue());
        String type = metadata.getValue() instanceof Boolean ? "b" : "i";
        String value = metadata.getValue() instanceof Boolean flag ? (flag ? "1" : "0") : metadata.getValue().toString();
        result.append('|').append(encodeToken(enchantment.getKey().toString())).append(',')
          .append(encodeToken(metadata.getKey().toString())).append(',').append(type).append(',').append(value);
      })
    );
    return result.length() == 2 ? null : result.toString();
  }

  private static void validateMetadataValue(NamespacedKey key, Object value) {
    Objects.requireNonNull(key, "metadata key");
    if (!(value instanceof Boolean) && !(value instanceof Integer)) throw new IllegalArgumentException(
      "Enchantment metadata values must be Boolean or Integer: " + key
    );
  }

  private static String encodeToken(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decodeToken(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }
  private static Map<EnchantmentId, Integer> decodeCustomEnchantments(String encoded) {
    Map<EnchantmentId, Integer> result = new TreeMap<>(Comparator.comparing(EnchantmentId::toString)); if (encoded == null || encoded.isBlank()) return result;
    String[] pieces = encoded.split("\\|", -1); if (pieces.length == 0 || !"v1".equals(pieces[0])) return result;
    for (int index = 1; index < pieces.length; index++) {
      int separator = pieces[index].lastIndexOf('='); if (separator < 1) continue;
      try { int level = Integer.parseInt(pieces[index].substring(separator + 1)); if (level >= 1 && level <= 3999) result.put(EnchantmentId.parse(pieces[index].substring(0, separator)), level); } catch (IllegalArgumentException ignored) {}
    }
    return result;
  }
  private static String encodeCustomEnchantments(Map<EnchantmentId, Integer> entries) {
    if (entries.isEmpty()) return null; StringBuilder result = new StringBuilder("v1");
    entries.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(EnchantmentId::toString))).forEach(entry -> { validateLevel(entry.getValue()); result.append('|').append(entry.getKey()).append('=').append(entry.getValue()); });
    return result.toString();
  }
  private ItemMeta requiredMeta() { ItemMeta meta = bukkit.getItemMeta(); if (meta == null) throw new IllegalStateException("Item has no mutable metadata"); return meta; }
  private Optional<Object> read(String name, ItemDataType type) { ItemMeta meta = bukkit.getItemMeta(); return meta == null ? Optional.empty() : Optional.ofNullable(get(meta.getPersistentDataContainer(), name, type)); }
  private void edit(Consumer<PersistentDataContainer> action) { ItemMeta meta = requiredMeta(); action.accept(meta.getPersistentDataContainer()); bukkit.setItemMeta(meta); }
  private static NamespacedKey key(String path) { return new NamespacedKey((Plugin) ItemsPlugin.instance(), path); }
  private static void validateLevel(int level) { if (level < 1 || level > 3999) throw new IllegalArgumentException("Enchantment levels must be 1..3999"); }
  private static boolean has(PersistentDataContainer container, String path, ItemDataType type) { return switch (type) { case STRING -> container.has(key(path), PersistentDataType.STRING); case INTEGER -> container.has(key(path), PersistentDataType.INTEGER); case LONG -> container.has(key(path), PersistentDataType.LONG); case DOUBLE -> container.has(key(path), PersistentDataType.DOUBLE); case BOOLEAN -> container.has(key(path), PersistentDataType.BYTE); }; }
  private static Object get(PersistentDataContainer container, String path, ItemDataType type) { return switch (type) { case STRING -> container.get(key(path), PersistentDataType.STRING); case INTEGER -> container.get(key(path), PersistentDataType.INTEGER); case LONG -> container.get(key(path), PersistentDataType.LONG); case DOUBLE -> container.get(key(path), PersistentDataType.DOUBLE); case BOOLEAN -> { Byte value = container.get(key(path), PersistentDataType.BYTE); yield value == null ? null : value != 0; } }; }
  private static void set(PersistentDataContainer container, String path, ItemDataType type, Object value) { switch (type) { case STRING -> container.set(key(path), PersistentDataType.STRING, (String) value); case INTEGER -> container.set(key(path), PersistentDataType.INTEGER, (Integer) value); case LONG -> container.set(key(path), PersistentDataType.LONG, (Long) value); case DOUBLE -> container.set(key(path), PersistentDataType.DOUBLE, (Double) value); case BOOLEAN -> container.set(key(path), PersistentDataType.BYTE, (byte) ((Boolean) value ? 1 : 0)); } }
}
