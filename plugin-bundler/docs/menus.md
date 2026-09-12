# Bundler menus

Bundler owns one `MenuManager` and one active menu per player. Obtain it with
`BundlerPlugin.instance().menuManager()`. All opens, closes, refreshes, page changes,
and storage mutations must run on the Bukkit server thread.

The preferred API uses `SinglePageMenuTemplate` or `PagedMenuTemplate<T>`. These are
immutable definitions: build a template once and open it for as many players as needed.
`MenuDefinition` remains available only for callers of the earlier Bundler v2 API.

## A single-page menu

```java
SinglePageMenuTemplate template = SinglePageMenuTemplate.builder("Example", 3)
    .item(11, MenuItem.builder(Material.EMERALD)
        .name("§aAccept")
        .lore("§7This text is literal.")
        .onClick(context -> {
            context.player().sendMessage("Accepted");
            context.session().close();
        })
        .build())
    .item(15, MenuItem.builder(Material.BARRIER)
        .name("§cClose")
        .onClick(context -> context.session().close())
        .build())
    .onOpen(context -> context.player().sendMessage("Opened"))
    .onClose(context -> context.player().sendMessage("Closed"))
    .build();

MenuSession session = BundlerPlugin.instance().menuManager().open(player, template);
```

Bundler cancels the relevant inventory event before calling an item or template callback.
An exception from application code is logged with player, session, and slot context; it
does not invalidate the session. Callbacks use Java's standard `Consumer`, `Function`,
and `BiFunction` types rather than custom interfaces.

## Localized and viewer-specific items

```java
MenuItem item = MenuItem.builder(Material.BOOK)
    .localizedName("help.menu.name")
    .localizedLore("help.menu.short")
    .wrappedLocalizedLore("help.menu.long")
    .languageWrappedLore("A literal paragraph wrapped for the viewer's language.")
    .lore(viewer -> List.of("§7Level: " + viewer.getLevel()))
    .build();

SinglePageMenuTemplate localized = SinglePageMenuTemplate.builder(
    viewer -> Locale.get(viewer, "help.menu.title"), 3
).item(13, item).build();
```

`wrappedLore(text, width)` uses a fixed visible-character width. The language-aware
methods use `ChatUtils.wrapWithColor`, including its narrower CJK item-lore width.
`skullTexture(url)` accepts a valid `textures.minecraft.net` skin URL on a
`PLAYER_HEAD`. `MenuItem.dynamic(viewer -> stack)` is available when the whole stack is
viewer-specific.

## Paged content menus

Pages are one-based. An empty entry list still renders page 1. Content cells are filled
in the exact order supplied to `contentSlots`.

```java
PagedMenuTemplate<Auction> auctions = PagedMenuTemplate.<Auction>builder("Auctions", 6)
    .entries(viewer -> auctionService.visibleTo(viewer))
    .contentSlots(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25)
    .renderItem((viewer, auction) -> MenuItem.builder(auction.icon())
        .name(auction.displayName())
        .onClick(context -> auctionService.buy(context.player(), auction))
        .build())
    .item(4, MenuItem.builder(Material.CHEST).name("§6Auctions").build())
    .previousButton(45, MenuItem.builder(Material.ARROW).name("§ePrevious").build())
    .nextButton(53, MenuItem.builder(Material.ARROW).name("§eNext").build())
    .pageItem(2, 4, MenuItem.builder(Material.CHEST).name("§6Special page").build())
    .removePageItem(3, 4)
    .onPageChange(context -> context.player().sendMessage(
        "Page " + context.previousPage() + " -> " + context.page()))
    .build();
```

Every page starts with the base static layout. Page overrides can add, replace, or
remove only static cells. Content and navigation cells are reserved. A
`PagedMenuTemplate<T>` cannot configure storage cells.

## Storage-provider menus

A GUI cell maps to an exact logical index in a `StorageProvider`. The provider is not
a GUI, so it may expose thousands or millions of indices. The menu remains a small
viewport containing only the provider indices mapped by that template. Opening a
template with mappings but no provider fails immediately.

```java
SinglePageMenuTemplate storageTemplate = SinglePageMenuTemplate.builder("Deposit", 3)
    .storageIndex(11, 0)          // writable
    .storageIndex(12, 7)
    .readOnlyStorageIndex(15, 12)
    .onStorageChange(context -> audit(
        context.player(), context.storageIndex(), context.previousItem(), context.item()))
    .build();

Inventory storage = Bukkit.createInventory(null, 18, "Backing data");
StorageProvider provider = StorageProvider.fromInventory(storage);
MenuSession session = BundlerPlugin.instance().menuManager()
    .open(player, storageTemplate, provider);
```

Passing the Bukkit `Inventory` directly remains supported and automatically creates
the same adapter. `storageSlot` and `readOnlyStorageSlot` also remain as compatibility
aliases for `storageIndex` and `readOnlyStorageIndex`.

A specialized provider can obtain its displayed stacks from another data source:

```java
public final class AuctionHouseStorage extends StorageProvider {
    private final List<Auction> cachedAuctions;

    public AuctionHouseStorage(List<Auction> cachedAuctions) {
        this.cachedAuctions = List.copyOf(cachedAuctions);
    }

    @Override
    public int size() {
        return cachedAuctions.size();
    }

    @Override
    public ItemStack getItem(int index) {
        return cachedAuctions.get(index).displayItem();
    }
}
```

This provider is read-only because it does not override `setItem`. A paged storage
template treats its ordered storage cells as a viewport and calculates the provider
excerpt automatically:

```java
PagedStorageMenuTemplate auctions = PagedStorageMenuTemplate.builder("Auctions", 6)
    .storage(viewer -> new AuctionHouseStorage(auctionCache.visibleTo(viewer)))
    .storageSlots(10, 11, 12, 13, 14, 15, 16,
                  19, 20, 21, 22, 23, 24, 25)
    .readOnlySlots(10, 11, 12, 13, 14, 15, 16,
                   19, 20, 21, 22, 23, 24, 25)
    .previousButton(45, MenuItem.builder(Material.ARROW).name("§ePrevious").build())
    .nextButton(53, MenuItem.builder(Material.ARROW).name("§eNext").build())
    .onStorageChange(context -> audit(
        context.player(), context.storageProvider(), context.storageIndex()))
    .build();

MenuSession session = BundlerPlugin.instance().menuManager().open(player, auctions);
```

With fourteen viewport cells, page 1 maps provider indices 0–13, page 2 maps 14–27,
and so on. The final page maps only indices below `provider.size()`. Provider size is
rechecked on refresh and page transition, and a page is clamped when the provider
shrinks. The player-specific provider function is evaluated once per opened session.

Paged content and paged storage are deliberately separate types. A
`PagedStorageMenuTemplate` has no `entries`, `contentSlots`, or `renderItem` methods;
its counterpart has no storage viewport methods.

Provider methods execute on the Bukkit server thread. Do not run blocking database
queries inside `getItem`; asynchronously load a snapshot or cache first, then open or
refresh the menu on the server thread.

Writable mappings implement pickup, placement, merging, swapping, shift transfer,
number-key/offhand swapping, drop keys, double-click collection, and drag distribution.
Read-only mappings reject every mutation. Provider values are never tagged;
the displayed stack is always a tagged clone. Tags are stripped before an item is put
on the cursor, in a player inventory/provider, or into the world.

## Live mutation and lifecycle

```java
session.setItem(22, MenuItem.builder(Material.CLOCK).name("Working...").build());
session.removeItem(10);
session.refresh();       // rebuilds from the template
session.nextPage();
session.previousPage();
session.close();
```

`setItem` and `removeItem` update the displayed view immediately and last until a
refresh or page transition rebuilds it. A session becomes invalid after close or when
another menu replaces it; subsequent methods throw `IllegalStateException`.

Bundler tags every rendered view item with `bundler:menu_item = "MENU ITEM"` and the
session UUID in `bundler:menu_session`. Its centralized listener protects all inventory
transfer paths. It purges tagged copies from player equipment, inventory, cursor, and
non-menu containers on inventory lifecycle events and player join/quit, plus a periodic
online-player failsafe. Untagged provider items are never purged. All sessions,
listeners, and cleanup tasks are disposed when Bundler disables.

## Language selector

`/locale` and `/lang` with no argument open Bundler's three-row language selector.
`/locale <code>`, tab completion, console behavior, and `/locale reload` continue to use
the command API. The selector itself is also an example of a localized
`SinglePageMenuTemplate`; see `LanguageSelectionMenu` in the source tree.
