package dev.bisz.commands;

import java.util.List;
import java.util.Objects;

/**
 * Localized documentation and optional completions for one command argument.
 */
public record CommandArgumentHint(
  CommandArgumentType type,
  String descriptionKey,
  String englishDescription,
  boolean optional,
  List<String> suggestions
) {
  public CommandArgumentHint {
    type = Objects.requireNonNull(type, "type");
    descriptionKey = Objects.requireNonNull(descriptionKey, "descriptionKey");
    englishDescription = Objects.requireNonNull(
      englishDescription,
      "englishDescription"
    );
    suggestions = List.copyOf(suggestions);
  }

  public static CommandArgumentHint option(
    String descriptionKey,
    String englishDescription,
    List<String> suggestions
  ) {
    return new CommandArgumentHint(
      CommandArgumentType.OPTION,
      descriptionKey,
      englishDescription,
      false,
      suggestions
    );
  }

  public static CommandArgumentHint number(
    String descriptionKey,
    String englishDescription
  ) {
    return new CommandArgumentHint(
      CommandArgumentType.NUMBER,
      descriptionKey,
      englishDescription,
      false,
      List.of()
    );
  }

  public static CommandArgumentHint integer(
    String descriptionKey,
    String englishDescription
  ) {
    return new CommandArgumentHint(
      CommandArgumentType.INTEGER,
      descriptionKey,
      englishDescription,
      false,
      List.of()
    );
  }
}
