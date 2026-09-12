from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from launcher.locales import (
    list_locale_files,
    load_locale_file,
    missing_locale_entries,
    most_complete_editable_locale,
    save_locale_file,
)


class LocaleFileTests(unittest.TestCase):
    def test_lists_and_round_trips_locale_json_with_unicode(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            locale_dir = Path(temporary)
            document = locale_dir / "zh_cn.json"
            save_locale_file(document, {"welcome": "欢迎", "item.name": "§a物品"})
            (locale_dir / "notes.txt").write_text("not a locale", encoding="utf-8")

            self.assertEqual([document], list_locale_files(locale_dir))
            self.assertEqual({"welcome": "欢迎", "item.name": "§a物品"}, load_locale_file(document))

    def test_rejects_non_string_locale_values(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            document = Path(temporary) / "broken.json"
            document.write_text('{"key": 1}', encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "string translation"):
                load_locale_file(document)

    def test_uses_the_largest_non_mojang_locale_as_the_template(self) -> None:
        documents = {
            "en_us.json": {"shared": "Shared", "only.en": "English"},
            "zh_cn.json": {"shared": "共有", "only.cn": "中文", "third": "第三"},
            "zh_cn_mojang.json": {"ignored": "reference", "one": "1", "two": "2", "three": "3"},
        }

        template = most_complete_editable_locale(documents)

        self.assertEqual("zh_cn.json", template)
        self.assertEqual(
            {"only.cn": "中文", "third": "第三"},
            missing_locale_entries(documents[template], documents["en_us.json"]),
        )


if __name__ == "__main__":
    unittest.main()
