package dev.bisz.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandArgumentHintTest {

  @Test
  void optionHintsKeepTheirNormalCompletionCandidates() {
    CommandArgumentHint hint = CommandArgumentHint.option(
      "description",
      "description",
      List.of("give", "remove")
    );

    assertEquals(CommandArgumentType.OPTION, hint.type());
    assertEquals(List.of("give", "remove"), hint.suggestions());
  }

  @Test
  void numericHintsDoNotInventNumericCompletions() {
    CommandArgumentHint hint = CommandArgumentHint.number(
      "description",
      "amount"
    );

    assertEquals(CommandArgumentType.NUMBER, hint.type());
    assertTrue(hint.suggestions().isEmpty());
  }

  @Test
  void integerHintsAreDistinctFromGeneralNumberHints() {
    CommandArgumentHint hint = CommandArgumentHint.integer(
      "description",
      "whole-number amount"
    );

    assertEquals(CommandArgumentType.INTEGER, hint.type());
    assertTrue(hint.suggestions().isEmpty());
  }
}
