package dev.bisz.commands;

import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * Evaluates the arithmetic syntax historically accepted by Bundler command numbers.
 */
public final class MathExpressions {

  private MathExpressions() {}

  public static double evaluateNumber(String expression) {
    if (expression == null || expression.trim().isEmpty()) {
      throw new IllegalArgumentException("A math expression cannot be blank");
    }
    try {
      double value = new ExpressionBuilder(preprocess(expression))
        .build()
        .evaluate();
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException(
          "Math expression must evaluate to a finite number"
        );
      }
      return value;
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid math expression", exception);
    }
  }

  public static long evaluateInteger(String expression) {
    double value = evaluateNumber(expression);
    if (
      value != Math.rint(value) ||
      value < Long.MIN_VALUE ||
      value > Long.MAX_VALUE
    ) {
      throw new IllegalArgumentException(
        "Math expression must evaluate to a whole number"
      );
    }
    return (long) value;
  }

  private static String preprocess(String expression) {
    String compact = expression
      .toLowerCase(java.util.Locale.ROOT)
      .replaceAll("[ \\t\\r\\n\\f]+", "");
    compact = compact.replaceAll("([0-9]+)k", "$1*1000");
    compact = compact.replaceAll("([0-9]+)m", "$1*1000000");
    return compact.replaceAll("([0-9]+)%", "($1/100.0)");
  }
}
