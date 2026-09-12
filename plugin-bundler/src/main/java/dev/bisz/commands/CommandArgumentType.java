package dev.bisz.commands;

/**
 * A human-readable type for a command argument hint.
 */
public enum CommandArgumentType {
  NUMBER("commands.general.tabcomplete.type.number", "number"),
  INTEGER("commands.general.tabcomplete.type.integer", "integer"),
  TEXT("commands.general.tabcomplete.type.text", "text"),
  PLAYER("commands.general.tabcomplete.type.player", "player"),
  OPTION("commands.general.tabcomplete.type.option", "option");

  private final String localeKey;
  private final String englishName;

  CommandArgumentType(String localeKey, String englishName) {
    this.localeKey = localeKey;
    this.englishName = englishName;
  }

  public String localeKey() {
    return localeKey;
  }

  public String englishName() {
    return englishName;
  }

  public boolean supportsMathExpressions() {
    return this == NUMBER || this == INTEGER;
  }
}
