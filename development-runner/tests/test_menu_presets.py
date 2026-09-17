from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from launcher.menu_maker import MenuCell, MenuDesign, MenuItemDesign, MenuText
from launcher.menu_presets import PresetStore


ROOT = Path(__file__).resolve().parents[1]


class PresetStoreTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.personal = Path(self.temp.name) / "data"
        self.store = PresetStore(ROOT / "assets" / "menu-presets", self.personal)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_builtins_are_exact_and_read_only(self) -> None:
        items = {preset.name: preset for preset in self.store.item_presets()}
        glass = items["Black Glass Filler"]
        close = items["Localized Close Barrier"]
        self.assertEqual("hidden", glass.item.name.mode)
        self.assertIsNone(glass.item.click_action)
        self.assertEqual("locale.menu.close", close.item.name.value)
        self.assertEqual("context.session().close();", close.item.click_action)
        menu = self.store.menu_presets()[0]
        self.assertEqual(6, menu.design.rows)
        panes = [cell for cell in menu.design.base_cells.values() if cell.item.material == "BLACK_STAINED_GLASS_PANE"]
        self.assertEqual(25, len(panes))
        self.assertEqual(28, 54 - len(menu.design.base_cells))
        self.assertEqual("BARRIER", menu.design.base_cells[49].item.material)
        self.assertTrue(menu.java_source)
        with self.assertRaises(ValueError):
            self.store.delete(menu)

    def test_unicode_multiline_round_trip_and_exact_source(self) -> None:
        item = MenuItemDesign(
            name=MenuText("literal", "关闭"),
            click_action="one();\ntwo();",
            glimmer=True,
        )
        saved_item = self.store.save_item("项目 ✨", item)
        self.assertEqual(item, next(value for value in self.store.item_presets() if value.preset_id == saved_item.preset_id).item)
        design = MenuDesign()
        design.base_cells[0] = MenuCell("static", item=item)
        source = "// detached 中文\nclass Exact {}\n"
        saved = self.store.save_menu("菜单", design, source, "Detached")
        loaded = next(value for value in self.store.menu_presets() if value.preset_id == saved.preset_id)
        self.assertEqual(source, loaded.java_source)
        self.assertEqual("Detached", loaded.code_state)

    def test_duplicate_schema_and_malformed_files_are_nonfatal(self) -> None:
        self.store.save_item("Unique", MenuItemDesign(name=MenuText("literal", "X")))
        with self.assertRaises(ValueError):
            self.store.save_item("unique", MenuItemDesign(name=MenuText("literal", "Y")))
        bad = self.personal / "items" / "bad.json"
        bad.write_text('{"schemaVersion": 999, "kind": "item"}', encoding="utf-8")
        presets = self.store.item_presets()
        self.assertTrue(presets)
        self.assertTrue(any("schema" in error for error in self.store.errors))

    def test_version_one_paged_menu_migrates_to_paged_content(self) -> None:
        document = {
            "schemaVersion": 1,
            "kind": "menu",
            "id": "44444444-4444-4444-8444-444444444444",
            "name": "Legacy paged",
            "design": {
                "templateType": "paged",
                "rows": 1,
                "title": {"mode": "literal", "value": "Legacy"},
                "baseCells": {
                    "0": {"role": "content", "item": None, "storageIndex": None,
                          "readOnly": False, "contentOrder": 0}
                },
                "pageOverrides": {},
                "contentClickAction": "legacy();",
            },
            "javaSource": "// exact legacy source\n",
            "codeState": "Detached",
        }
        path = self.personal / "menus" / "legacy.json"
        path.write_text(json.dumps(document), encoding="utf-8")
        loaded = next(value for value in self.store.menu_presets() if value.name == "Legacy paged")
        self.assertEqual("paged_content", loaded.design.template_type)
        self.assertEqual("// exact legacy source\n", loaded.java_source)
        self.assertEqual("Detached", loaded.code_state)


if __name__ == "__main__":
    unittest.main()
