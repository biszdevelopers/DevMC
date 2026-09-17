from __future__ import annotations

import struct
import unittest
from pathlib import Path

from launcher.menu_maker import (
    MenuCell,
    MenuDesign,
    MenuItemDesign,
    MenuLore,
    MenuText,
    MinecraftAssets,
    generate_java,
    apply_callback_bodies,
    allowed_roles,
    callback_structure,
    extract_callback_bodies,
    render_menu_item,
    slot_traversal,
)


ROOT = Path(__file__).resolve().parents[1]


class MinecraftAssetsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.assets = MinecraftAssets(ROOT / "assets" / "minecraft")

    def test_copied_chest_texture_is_the_vanilla_atlas(self) -> None:
        payload = self.assets.chest_texture.read_bytes()
        self.assertEqual(b"\x89PNG\r\n\x1a\n", payload[:8])
        width, height = struct.unpack(">II", payload[16:24])
        self.assertEqual((256, 256), (width, height))

    def test_resolves_direct_item_and_block_parent_textures(self) -> None:
        sword = self.assets.texture_for("DIAMOND_SWORD")
        planks = self.assets.texture_for("OAK_PLANKS")
        self.assertIsNotNone(sword)
        self.assertIsNotNone(planks)
        self.assertEqual("diamond_sword.png", sword.name)
        self.assertEqual("oak_planks.png", planks.name)

    def test_unsupported_builtin_model_uses_explicit_fallback(self) -> None:
        self.assertIsNone(self.assets.texture_for("PLAYER_HEAD"))
        self.assertIn("DIAMOND_SWORD", self.assets.materials())


class MenuDesignTests(unittest.TestCase):
    def test_bulk_fill_traversal_supports_horizontal_and_vertical_order(self) -> None:
        self.assertEqual((0, 1, 2, 3), slot_traversal(1, "horizontal")[:4])
        self.assertEqual((0, 9, 1, 10), slot_traversal(2, "vertical")[:4])
        with self.assertRaises(ValueError):
            slot_traversal(2, "diagonal")

    def test_template_types_strictly_limit_workstation_roles(self) -> None:
        self.assertEqual(("empty", "static", "storage"), allowed_roles("single"))
        self.assertNotIn("storage", allowed_roles("paged_content"))
        self.assertNotIn("content", allowed_roles("paged_storage"))

    def test_validates_rows_roles_storage_and_content_order(self) -> None:
        design = MenuDesign(template_type="paged_content", rows=7, title=MenuText("literal", " "))
        design.base_cells = {
            0: MenuCell("content", content_order=1),
            1: MenuCell("content", content_order=3),
            2: MenuCell("storage", storage_index=4),
            3: MenuCell("storage", storage_index=4),
        }
        text = "\n".join(design.validate())
        self.assertIn("Rows must be between 1 and 6", text)
        self.assertIn("title cannot be blank", text)
        self.assertIn("consecutive from 0", text)
        self.assertIn("may only be mapped once", text)

    def test_page_overrides_only_allow_static_cells(self) -> None:
        design = MenuDesign(template_type="paged_content")
        design.base_cells[0] = MenuCell("content", content_order=0)
        design.page_overrides[2] = {1: MenuCell("storage", storage_index=0)}
        self.assertTrue(any("only static overrides" in error for error in design.validate()))

    def test_page_overrides_cannot_replace_reserved_base_cells(self) -> None:
        design = MenuDesign(template_type="paged_content")
        design.base_cells[0] = MenuCell("content", content_order=0)
        design.page_overrides[2] = {
            0: MenuCell("static", item=MenuItemDesign(name=MenuText("literal", "Bad")))
        }
        self.assertTrue(any("cannot be overridden" in error for error in design.validate()))

    def test_tooltip_rendering_resolves_locale_and_matches_language_width(self) -> None:
        item = MenuItemDesign(
            material="BOOK",
            name=MenuText("localized", "menu.name"),
            lore=(MenuLore("localized", "menu.lore"),),
            wrap="language",
        )
        translations = {
            "menu.name": "§aLocalized book",
            "menu.lore": "§7one two three four five six seven eight",
        }
        name, english = render_menu_item(item, translations, "en_us")
        _name, chinese = render_menu_item(item, translations, "zh_cn")
        self.assertEqual("§aLocalized book", name)
        self.assertLess(len(english), len(chinese))
        self.assertTrue(chinese[1].startswith("§7"))

    def test_tooltip_fixed_wrapping_preserves_literal_lore_order(self) -> None:
        item = MenuItemDesign(
            name=MenuText("literal", "Title"),
            lore=(MenuLore("literal", "first entry"), MenuLore("literal", "second entry")),
            wrap="fixed",
            wrap_width=20,
        )
        name, lore = render_menu_item(item, {}, "en_us")
        self.assertEqual("Title", name)
        self.assertEqual(("first entry", "second entry"), lore)


class JavaGeneratorTests(unittest.TestCase):
    def test_generates_localized_wrapped_single_page_and_escapes_java(self) -> None:
        item = MenuItemDesign(
            material="PAPER",
            name=MenuText("localized", "menu.name"),
            lore=(MenuLore("literal", 'Line "one"\\next'), MenuLore("localized", "menu.help")),
            wrap="language",
            click_action='context.player().sendMessage("hello");',
            glimmer=True,
        )
        design = MenuDesign(title=MenuText("localized", "menu.title"))
        design.base_cells[4] = MenuCell("static", item=item)
        code = generate_java(design)
        self.assertIn("SinglePageMenuTemplate.builder(viewer -> Locale.get(viewer, \"menu.title\"), 3)", code)
        self.assertIn(".localizedName(\"menu.name\")", code)
        self.assertIn('.languageWrappedLore("Line \\"one\\"\\\\next")', code)
        self.assertIn('.wrappedLocalizedLore("menu.help")', code)
        self.assertIn(".glint()", code)
        self.assertIn('context.player().sendMessage("hello");', code)
        self.assertIn('key="base:4"', code)
        self.assertIn("import java.util.List;", code)

    def test_generates_paged_content_layout_and_overrides(self) -> None:
        nav = MenuItemDesign(material="ARROW", name=MenuText("literal", "Next"))
        override = MenuItemDesign(material="BARRIER", name=MenuText("literal", "Page two"))
        design = MenuDesign(template_type="paged_content", rows=2)
        design.base_cells = {
            0: MenuCell("content", content_order=0),
            1: MenuCell("content", content_order=1),
            17: MenuCell("next", item=nav),
        }
        design.page_overrides[2] = {8: MenuCell("static", item=override), 7: None}
        code = generate_java(design)
        self.assertIn("List<ItemStack> contentItems", code)
        self.assertIn(".contentSlots(0, 1)", code)
        self.assertIn("builder.pageItem(2, 8", code)
        self.assertIn("builder.removePageItem(2, 7);", code)
        self.assertIn("menuManager().open(player, builder.build())", code)

    def test_invalid_design_does_not_generate_code(self) -> None:
        with self.assertRaises(ValueError):
            generate_java(MenuDesign(template_type="paged_content"))

    def test_generates_paged_storage_provider_viewport(self) -> None:
        design = MenuDesign(template_type="paged_storage", rows=2)
        design.base_cells = {
            0: MenuCell("storage", storage_index=1, read_only=True),
            1: MenuCell("storage", storage_index=0),
            9: MenuCell("previous", item=MenuItemDesign(material="ARROW", name=MenuText("literal", "Back"))),
            17: MenuCell("next", item=MenuItemDesign(material="ARROW", name=MenuText("literal", "Next"))),
        }
        code = generate_java(design)
        self.assertIn("PagedStorageMenuTemplate.builder", code)
        self.assertIn("StorageProvider storage", code)
        self.assertIn(".storage(storage)", code)
        self.assertIn(".storageSlots(1, 0)", code)
        self.assertIn("builder.readOnlySlots(0);", code)
        self.assertNotIn("contentItems", code)
        self.assertIn("menuManager().open(player, builder.build())", code)

    def test_rejects_hybrid_paged_roles(self) -> None:
        content = MenuDesign(template_type="paged_content")
        content.base_cells = {
            0: MenuCell("content", content_order=0),
            1: MenuCell("storage", storage_index=0),
        }
        self.assertTrue(any("cannot contain storage" in error for error in content.validate()))
        storage = MenuDesign(template_type="paged_storage")
        storage.base_cells = {
            0: MenuCell("storage", storage_index=0),
            1: MenuCell("content", content_order=0),
        }
        self.assertTrue(any("cannot contain content" in error for error in storage.validate()))

    def test_hidden_name_and_callback_marker_round_trip(self) -> None:
        design = MenuDesign()
        design.base_cells[4] = MenuCell("static", item=MenuItemDesign(
            material="BARRIER", name=MenuText("hidden", ""), click_action="first();\nsecond();"
        ))
        source = generate_java(design)
        self.assertIn('.name(" ")', source)
        bodies, errors = extract_callback_bodies(source)
        self.assertEqual([], errors)
        self.assertEqual("first();\nsecond();", bodies["base:4"])
        edited = source.replace("first();\n            second();", "changed();")
        self.assertEqual(callback_structure(source)[0], callback_structure(edited)[0])
        apply_callback_bodies(design, {"base:4": "changed();"})
        self.assertEqual("changed();", design.base_cells[4].item.click_action)

    def test_broken_and_duplicate_markers_are_reported(self) -> None:
        source = '// <menu-maker:callback key="base:1">\nwork();\n'
        self.assertIn("no end marker", extract_callback_bodies(source)[1][0])
        duplicate = source + '// </menu-maker:callback>\n' + source + '// </menu-maker:callback>\n'
        self.assertTrue(any("duplicate" in value for value in extract_callback_bodies(duplicate)[1]))


if __name__ == "__main__":
    unittest.main()
