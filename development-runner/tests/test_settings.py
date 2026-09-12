from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from launcher.models import LauncherSettings
from launcher.settings import SettingsStore


class SettingsTests(unittest.TestCase):
    def test_dimension_selections_persist_per_profile(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = SettingsStore(Path(temporary) / "settings.json")
            settings = LauncherSettings(dimensions={
                "mohist-1.20.1": {"nether": False, "end": True},
                "spigot-1.20.1": {"nether": True, "end": False},
            })
            store.save(settings)
            loaded = store.load()
            self.assertEqual(settings.dimensions, loaded.dimensions)

    def test_incremental_build_preference_persists(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = SettingsStore(Path(temporary) / "settings.json")
            store.save(LauncherSettings(incremental_builds=False))
            self.assertFalse(store.load().incremental_builds)

    def test_plugin_modes_persist(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = SettingsStore(Path(temporary) / "settings.json")
            expected = {
                "project-a": "enabled",
                "project-b": "inactive",
                "project-c": "disabled",
            }
            store.save(LauncherSettings(plugin_states=expected))
            self.assertEqual(expected, store.load().plugin_states)

    def test_additional_plugin_paths_persist(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = SettingsStore(Path(temporary) / "settings.json")
            expected = [r"D:\Downloads\ProtocolLib.jar", r"D:\Downloads\Citizens.jar"]
            store.save(LauncherSettings(additional_plugin_paths=expected))
            self.assertEqual(expected, store.load().additional_plugin_paths)

    def test_mohist_mod_paths_persist_per_profile(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = SettingsStore(Path(temporary) / "settings.json")
            expected = {"mohist-1.20.1": [r"D:\Downloads\example-mod.jar"]}
            store.save(LauncherSettings(additional_mod_paths=expected))
            self.assertEqual(expected, store.load().additional_mod_paths)


if __name__ == "__main__":
    unittest.main()
