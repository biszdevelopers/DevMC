package dev.bisz.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MenuTemplateTest {

  private static MenuItem item() {
    return MenuItem.dynamic(player -> {
      throw new AssertionError(
        "Template tests must not render Bukkit metadata"
      );
    });
  }

  @Test
  void validatesRowsAndLetsStorageOverrideStaticItems() {
    assertThrows(IllegalArgumentException.class, () ->
      SinglePageMenuTemplate.builder("Bad", 0)
    );
    var builder = SinglePageMenuTemplate.builder("Storage", 1).item(0, item());
    var template = builder.storageSlot(0, 0).item(0, item()).build();
    assertTrue(template.baseItems().isEmpty());
    assertTrue(template.storageSlots().containsKey(0));
  }

  @Test
  void rejectsDuplicateBackingStorageIndices() {
    var builder = SinglePageMenuTemplate.builder("Storage", 1)
      .storageSlot(0, 4)
      .readOnlyStorageSlot(1, 4);
    assertThrows(IllegalStateException.class, builder::build);
  }

  @Test
  void storageIndexAliasesSupportIndicesBeyondInventorySizes() {
    var template = SinglePageMenuTemplate.builder("Large provider", 1)
      .storageIndex(0, 10_000)
      .readOnlyStorageIndex(1, 25_000)
      .build();

    assertEquals(10_000, template.storageSlots().get(0).storageIndex());
    assertEquals(25_000, template.storageSlots().get(1).storageIndex());
  }

  @Test
  void emptyPagedDataStillProducesPageOne() {
    var template = PagedMenuTemplate.<String>builder("Empty", 1)
      .entries(List.of())
      .contentSlots(0)
      .renderItem((player, value) -> item())
      .build();
    RenderedMenuPage page = template.render(null, 99, null);
    assertEquals(1, page.page());
    assertEquals(1, page.pageCount());
  }

  @Test
  void pagingPreservesBaseLayoutAndAppliesStaticOverrides() {
    MenuItem base = item();
    MenuItem replacement = item();
    MenuItem renderedEntry = item();
    var template = PagedMenuTemplate.<String>builder("Paged", 1)
      .entries(List.of("one", "two"))
      .contentSlots(0)
      .renderItem((player, value) -> renderedEntry)
      .item(8, base)
      .pageItem(2, 8, replacement)
      .build();

    RenderedMenuPage first = template.render(null, 1, null);
    RenderedMenuPage second = template.render(null, 2, null);
    assertEquals(2, first.pageCount());
    assertSame(base, first.items().get(8));
    assertSame(replacement, second.items().get(8));
    assertSame(renderedEntry, second.items().get(0));
  }

  @Test
  void pagedContentAndNavigationOverrideStaticAndPageItems() {
    MenuItem previous = item();
    var authoritative = PagedMenuTemplate.<String>builder("Precedence", 1)
      .entries(List.of("first", "second"))
      .renderItem((player, value) -> item())
      .contentSlots(0)
      .item(0, item())
      .item(2, item())
      .previousButton(2, previous)
      .nextButton(3, item())
      .pageItem(1, 0, item())
      .pageItem(2, 2, item())
      .build();

    RenderedMenuPage first = authoritative.render(null, 1, null);
    RenderedMenuPage second = authoritative.render(null, 2, null);
    assertTrue(first.storageSlots().isEmpty());
    assertTrue(first.items().containsKey(0));
    assertFalse(first.items().containsKey(2));
    assertSame(previous, second.items().get(2));

    var valid = PagedMenuTemplate.<String>builder("Valid", 1)
      .entries(List.of("entry"))
      .renderItem((player, value) -> item())
      .contentSlots(0)
      .item(8, item())
      .removePageItem(1, 8)
      .build()
      .render(null, 1, null);
    assertFalse(valid.items().containsKey(8));
    assertTrue(valid.items().containsKey(0));
  }
}
