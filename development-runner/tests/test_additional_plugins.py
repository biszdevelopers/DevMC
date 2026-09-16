from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from launcher.additional_plugins import (
    additional_mod_id,
    additional_plugin_id,
    collect_additional_mod_artifacts,
    collect_additional_plugin_artifacts,
)
from launcher.deployment import deploy_artifacts


class AdditionalPluginTests(unittest.TestCase):
    def test_collects_external_jars_with_a_stable_managed_id(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            plugin = Path(temporary) / "ProtocolLib.jar"
            plugin.write_bytes(b"jar")

            artifacts = collect_additional_plugin_artifacts([str(plugin)])

            self.assertEqual({additional_plugin_id(plugin)}, set(artifacts))
            self.assertEqual(plugin.resolve(), artifacts[additional_plugin_id(plugin)])

    def test_rejects_missing_or_non_jar_plugins(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(FileNotFoundError, "not found"):
                collect_additional_plugin_artifacts([str(root / "Citizens.jar")])
            text_file = root / "plugin.txt"
            text_file.write_text("not a jar", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "not a JAR"):
                collect_additional_plugin_artifacts([str(text_file)])

    def test_collects_mod_jars_separately_from_plugins(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            mod = Path(temporary) / "example-mod.jar"
            mod.write_bytes(b"mod")

            artifacts = collect_additional_mod_artifacts([str(mod)])

            self.assertEqual({additional_mod_id(mod)}, set(artifacts))

    def test_adopts_an_existing_manual_copy_when_deployed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            plugins_dir = root / "plugins"
            plugins_dir.mkdir()
            source = root / "ProtocolLib.jar"
            source.write_bytes(b"new plugin")
            (plugins_dir / source.name).write_bytes(b"manual plugin")
            artifacts = collect_additional_plugin_artifacts([str(source)])

            deploy_artifacts(
                plugins_dir,
                artifacts,
                desired_project_ids=set(artifacts),
                adopt_filenames={source.name},
            )

            self.assertEqual(b"new plugin", (plugins_dir / source.name).read_bytes())
