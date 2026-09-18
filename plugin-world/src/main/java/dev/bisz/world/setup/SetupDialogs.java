package dev.bisz.world.setup;

import dev.bisz.world.WorldPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Dialog-driven first-time setup. Non-operators are told an operator is needed;
 * the first operator is asked to confirm generation and choose a seed and world
 * size.
 */
public final class SetupDialogs {

  private static final ClickCallback.Options OPTIONS = ClickCallback.Options
    .builder()
    .uses(1)
    .lifetime(Duration.ofMinutes(10))
    .build();

  private final WorldPlugin plugin;
  private final SetupState state;

  public SetupDialogs(WorldPlugin plugin, SetupState state) {
    this.plugin = plugin;
    this.state = state;
  }

  /** Prompts a joining player when setup is still incomplete. */
  public void promptOnJoin(Player player) {
    if (state.complete()) return;
    if (!player.isOp()) {
      player.showDialog(opRequiredDialog());
      return;
    }
    player.showDialog(setupDialog());
  }

  private Dialog opRequiredDialog() {
    ActionButton ok = ActionButton.builder(Component.text("OK"))
      .tooltip(Component.text("Dismiss"))
      .width(150)
      .action(noop())
      .build();
    return Dialog.create(builder ->
      builder
        .empty()
        .base(
          DialogBase.builder(Component.text("World setup"))
            .body(
              List.of(
                DialogBody.plainMessage(
                  Component.text(
                    "This world has not been generated yet. An operator must " +
                    "complete world setup."
                  )
                )
              )
            )
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .build()
        )
        .type(DialogType.notice(ok))
    );
  }

  private Dialog setupDialog() {
    ActionButton generate = ActionButton.builder(Component.text("Generate"))
      .tooltip(Component.text("Create the devMc world"))
      .width(150)
      .action(DialogAction.customClick(this::onGenerate, OPTIONS))
      .build();
    ActionButton cancel = ActionButton.builder(Component.text("Cancel"))
      .width(150)
      .action(noop())
      .build();
    return Dialog.create(builder ->
      builder
        .empty()
        .base(
          DialogBase.builder(Component.text("Generate a devMc world"))
            .body(
              List.of(
                DialogBody.plainMessage(
                  Component.text(
                    "Confirm that you want to generate a devMc world, then " +
                    "choose a seed and world size."
                  )
                )
              )
            )
            .inputs(
              List.of(
                DialogInput
                  .text("seed", Component.text("Seed (blank = random)"))
                  .build(),
                DialogInput
                  .numberRange(
                    "size",
                    Component.text("World size (blocks)"),
                    500f,
                    20000f
                  )
                  .step(500f)
                  .initial(4000f)
                  .build(),
                DialogInput
                  .text("name", Component.text("World name (blank = current)"))
                  .build()
              )
            )
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .build()
        )
        .type(DialogType.confirmation(generate, cancel))
    );
  }

  private void onGenerate(DialogResponseView response, Audience audience) {
    if (!(audience instanceof Player player)) return;
    String seed = response.getText("seed");
    String name = response.getText("name");
    Float size = response.getFloat("size");
    SetupService.Report report = plugin
      .setup()
      .run(
        player.getLocation(),
        new SetupService.SetupRequest(
          name == null ? "" : name,
          seed == null ? "" : seed,
          size == null ? 0.0 : size
        )
      );
    state.markComplete(name == null ? "" : name);
    player.sendMessage(
      Component.text(
        report.success()
          ? "World generation started."
          : "World generation started with warnings; check the console."
      )
    );
  }

  private static DialogAction noop() {
    return DialogAction.customClick((response, audience) -> {}, OPTIONS);
  }
}
