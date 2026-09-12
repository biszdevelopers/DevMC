package dev.bisz.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class DevCommandTest {

  private static final CommandSender SENDER =
    (CommandSender) Proxy.newProxyInstance(
      CommandSender.class.getClassLoader(),
      new Class<?>[] { CommandSender.class },
      (proxy, method, arguments) -> defaultValue(method.getReturnType())
    );

  @Test
  void emptyIntegerInputShowsMathSupportHint() {
    assertEquals(
      " Mathematical operations are supported.",
      new IntegerCommand()
        .tabComplete(SENDER, "test", new String[] { "" })
        .get(1)
    );
  }

  @Test
  void enteredIntegerExpressionShowsItsResult() {
    assertEquals(
      " Result: 2k+5 = 2005",
      new IntegerCommand()
        .tabComplete(SENDER, "test", new String[] { "2k+5" })
        .get(1)
    );
  }

  @Test
  void enteredNumberExpressionUsesNumberEvaluation() {
    assertEquals(
      " Result: 3/2 = 1.5",
      new NumberCommand()
        .tabComplete(SENDER, "test", new String[] { "3/2" })
        .get(1)
    );
  }

  @Test
  void fractionalIntegerExpressionShowsTheRoundedResult() {
    assertEquals(
      " Result: 3/2 = 1.5 ≈ 2",
      new IntegerCommand()
        .tabComplete(SENDER, "test", new String[] { "3/2" })
        .get(1)
    );
  }

  @Test
  void invalidExpressionShowsMathError() {
    assertEquals(
      " Invalid Expression",
      new NumberCommand()
        .tabComplete(SENDER, "test", new String[] { "nope" })
        .get(1)
    );
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    return 0;
  }

  private static final class IntegerCommand extends TestCommand {

    @Override
    protected CommandArgumentHint argumentHint(
      CommandSender sender,
      String alias,
      String[] arguments
    ) {
      return CommandArgumentHint.integer("description", "amount");
    }
  }

  private static final class NumberCommand extends TestCommand {

    @Override
    protected CommandArgumentHint argumentHint(
      CommandSender sender,
      String alias,
      String[] arguments
    ) {
      return CommandArgumentHint.number("description", "amount");
    }
  }

  private abstract static class TestCommand extends DevCommand {

    private TestCommand() {
      super("test", "");
    }

    @Override
    protected boolean executeCommand(
      CommandSender sender,
      String label,
      String[] arguments
    ) {
      return true;
    }

    @Override
    protected List<String> complete(
      CommandSender sender,
      String alias,
      String[] arguments
    ) {
      return List.of();
    }
  }
}
