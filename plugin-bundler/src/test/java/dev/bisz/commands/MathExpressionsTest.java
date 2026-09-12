package dev.bisz.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MathExpressionsTest {

  @Test
  void evaluatesLegacyArithmeticAndSuffixes() {
    assertEquals(2L, MathExpressions.evaluateInteger("1+1"));
    assertEquals(26L, MathExpressions.evaluateInteger("7 * 3 + 5"));
    assertEquals(10_000L, MathExpressions.evaluateInteger("2k * 5"));
    assertEquals(10_000_000L, MathExpressions.evaluateInteger("2m * 5"));
    assertEquals(100L, MathExpressions.evaluateInteger("50% * 200"));
  }

  @Test
  void keepsGeneralNumbersAndWholeNumbersDistinct() {
    assertEquals(1.5D, MathExpressions.evaluateNumber("3 / 2"));
    assertThrows(IllegalArgumentException.class, () ->
      MathExpressions.evaluateInteger("3 / 2")
    );
    assertThrows(IllegalArgumentException.class, () ->
      MathExpressions.evaluateNumber("unknown + 5")
    );
  }
}
