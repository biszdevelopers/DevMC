from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from launcher.deployment import MANIFEST_NAME, deploy_artifacts


class DeploymentTests(unittest.TestCase):
    def test_replaces_and_removes_only_managed_jars(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            plugins = root / "plugins"
            outputs = root / "outputs"
            plugins.mkdir()
            outputs.mkdir()
            (plugins / "third-party.jar").write_bytes(b"third party")
            first = outputs / "first.jar"
            first.write_bytes(b"first")
            deploy_artifacts(plugins, {"first-project": first})
            self.assertTrue((plugins / "first.jar").is_file())
            second = outputs / "second.jar"
            second.write_bytes(b"second")
            deploy_artifacts(plugins, {"second-project": second})
            self.assertFalse((plugins / "first.jar").exists())
            self.assertTrue((plugins / "second.jar").is_file())
            self.assertTrue((plugins / "third-party.jar").is_file())
            manifest = json.loads((plugins / MANIFEST_NAME).read_text(encoding="utf-8"))
            self.assertEqual({"second-project"}, set(manifest["artifacts"]))

    def test_refuses_unmanaged_filename_collision(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            plugins = root / "plugins"
            plugins.mkdir()
            (plugins / "demo.jar").write_bytes(b"third party")
            artifact = root / "demo.jar"
            artifact.write_bytes(b"ours")
            with self.assertRaisesRegex(RuntimeError, "not managed"):
                deploy_artifacts(plugins, {"demo": artifact})

    def test_reuses_an_identical_managed_jar_without_replacing_it(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            plugins = root / "plugins"
            artifact = root / "demo.jar"
            artifact.write_bytes(b"same")
            deploy_artifacts(plugins, {"demo": artifact})
            result = deploy_artifacts(plugins, {"demo": artifact})
            self.assertEqual((), result.installed)
            self.assertEqual(("demo.jar",), result.reused)

    def test_can_retain_selected_artifact_omitted_from_incremental_batch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            plugins = root / "plugins"
            artifact = root / "demo.jar"
            artifact.write_bytes(b"same")
            deploy_artifacts(plugins, {"demo": artifact})
            result = deploy_artifacts(
                plugins, {}, desired_project_ids={"demo"}
            )
            self.assertTrue((plugins / "demo.jar").is_file())
            self.assertEqual(("demo.jar",), result.reused)

    def test_inactive_artifact_is_kept_even_if_it_was_manually_replaced(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            plugins = root / "plugins"
            artifact = root / "demo.jar"
            artifact.write_bytes(b"built")
            deploy_artifacts(plugins, {"demo": artifact})
            (plugins / "demo.jar").write_bytes(b"manually frozen version")

            result = deploy_artifacts(
                plugins, {}, desired_project_ids={"demo"}
            )

            self.assertEqual(b"manually frozen version", (plugins / "demo.jar").read_bytes())
            self.assertEqual(("demo.jar",), result.reused)
            manifest = json.loads((plugins / MANIFEST_NAME).read_text(encoding="utf-8"))
            self.assertEqual("demo.jar", manifest["artifacts"]["demo"]["filename"])

    def test_inactive_artifact_must_already_be_installed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            plugins = Path(temporary) / "plugins"
            with self.assertRaisesRegex(RuntimeError, "Enable it for one launch"):
                deploy_artifacts(
                    plugins, {}, desired_project_ids={"never-installed"}
                )


if __name__ == "__main__":
    unittest.main()
