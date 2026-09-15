package dev.bisz.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class PagedStorageMenuTemplateTest {

  @Test
  void pagesConsecutiveProviderExcerptsAndKeepsPerSlotReadOnlyState() {
    MutableProvider provider = new MutableProvider(5);
    MenuItem base = item();
    MenuItem secondPage = item();
    var template = PagedStorageMenuTemplate.builder("Storage", 1)
      .storage(provider)
      .storageSlots(1, 2)
      .readOnlySlots(2)
      .previousButton(0, item())
      .nextButton(8, item())
      .item(7, base)
      .pageItem(2, 7, secondPage)
      .build();

    RenderedMenuPage second = template.render(null, 2, provider);
    assertEquals(3, second.pageCount());
    assertEquals(2, second.storageSlots().get(1).storageIndex());
    assertEquals(3, second.storageSlots().get(2).storageIndex());
    assertFalse(second.storageSlots().get(1).readOnly());
    assertTrue(second.storageSlots().get(2).readOnly());
    assertEquals(0, second.previousSlot());
    assertEquals(8, second.nextSlot());
    assertSame(secondPage, second.items().get(7));

    RenderedMenuPage last = template.render(null, 3, provider);
    assertEquals(Set.of(1), last.storageSlots().keySet());
    assertEquals(4, last.storageSlots().get(1).storageIndex());
    assertNull(last.nextSlot());
  }

  @Test
  void emptyAndShrinkingProvidersClampToPageOne() {
    MutableProvider provider = new MutableProvider(0);
    var template = PagedStorageMenuTemplate.builder("Storage", 1)
      .storage(provider)
      .storageSlots(1, 2, 3)
      .build();

    RenderedMenuPage empty = template.render(null, 99, provider);
    assertEquals(1, empty.page());
    assertEquals(1, empty.pageCount());
    assertTrue(empty.storageSlots().isEmpty());

    provider.size = 8;
    assertEquals(3, template.render(null, 3, provider).page());
    provider.size = 2;
    assertEquals(1, template.render(null, 3, provider).page());
  }

  @Test
  void navigationControlsOverrideStaticItemsAndLeaveThemAsFallback() {
    MutableProvider provider = new MutableProvider(2);
    MenuItem previous = item();
    MenuItem staticItem = item();
    var template = PagedStorageMenuTemplate.builder("Precedence", 1)
      .storage(provider)
      .storageSlots(1)
      .item(0, staticItem)
      .previousButton(0, previous)
      .build();

    assertSame(staticItem, template.render(null, 1, provider).items().get(0));
    assertSame(previous, template.render(null, 2, provider).items().get(0));
  }

  @Test
  void resolvesPlayerProviderAndRejectsInvalidLayouts() {
    MutableProvider provider = new MutableProvider(100_001);
    var template = PagedStorageMenuTemplate.builder("Storage", 1)
      .storage(player -> provider)
      .storageSlots(1)
      .build();
    assertSame(provider, template.boundStorage(null));
    assertEquals(
      100_000,
      template
        .render(null, 100_001, provider)
        .storageSlots()
        .get(1)
        .storageIndex()
    );

    assertThrows(IllegalStateException.class, () ->
      PagedStorageMenuTemplate.builder("Bad", 1)
        .storage(provider)
        .storageSlots(1)
        .readOnlySlots(2)
        .build()
    );
    var storageWins = PagedStorageMenuTemplate.builder("Storage wins", 1)
      .storage(provider)
      .storageSlots(1)
      .item(1, item())
      .build()
      .render(null, 1, provider);
    assertFalse(storageWins.items().containsKey(1));
    assertTrue(storageWins.storageSlots().containsKey(1));
    assertThrows(IllegalArgumentException.class, () ->
      PagedStorageMenuTemplate.builder("Bad", 1)
        .storage(provider)
        .storageSlots(1, 1)
    );
    MutableProvider invalid = new MutableProvider(-1);
    var invalidTemplate = PagedStorageMenuTemplate.builder("Bad", 1)
      .storage(invalid)
      .storageSlots(1)
      .build();
    assertThrows(IllegalStateException.class, () ->
      invalidTemplate.render(null, 1, invalid)
    );
  }

  @Test
  void contentAndStorageBuildersExposeOnlyTheirOwnDataRole() {
    Set<String> contentMethods = Arrays.stream(
      PagedMenuTemplate.Builder.class.getMethods()
    )
      .map(method -> method.getName())
      .collect(Collectors.toSet());
    Set<String> storageMethods = Arrays.stream(
      PagedStorageMenuTemplate.Builder.class.getMethods()
    )
      .map(method -> method.getName())
      .collect(Collectors.toSet());

    assertFalse(contentMethods.contains("storageSlots"));
    assertFalse(contentMethods.contains("storageIndex"));
    assertFalse(storageMethods.contains("entries"));
    assertFalse(storageMethods.contains("contentSlots"));
    assertFalse(storageMethods.contains("renderItem"));
  }

  private static MenuItem item() {
    return MenuItem.dynamic(player -> {
      throw new AssertionError("must not render");
    });
  }

  private static final class MutableProvider extends StorageProvider {

    private int size;

    private MutableProvider(int size) {
      this.size = size;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public ItemStack getItem(int index) {
      return null;
    }
  }
}
