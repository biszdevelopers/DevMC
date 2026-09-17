from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from launcher.builds import build_command, find_plugin_artifact, maven_operation_command
from launcher.models import PluginProject


class BuildTests(unittest.TestCase):
    @staticmethod
    def make_maven_wrapper(root: Path) -> Path:
        wrapper = root / "mvnw.cmd"
        wrapper.write_text("", encoding="utf-8")
        support = root / ".mvn" / "wrapper"
        support.mkdir(parents=True)
        (support / "maven-wrapper.properties").write_text(
            "distributionUrl=https://example.invalid/apache-maven.zip\n", encoding="utf-8"
        )
        return wrapper

    def test_maven_fast_and_test_commands(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_maven_wrapper(root)
            project = PluginProject(str(root), root, "Demo", "maven")
            self.assertEqual("-DskipTests", build_command(project, False)[-1])
            self.assertNotIn("-DskipTests", build_command(project, True))

    def test_wrapperless_maven_project_uses_sibling_wrapper(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            project_path = root / "currency"
            project_path.mkdir()
            (project_path / "pom.xml").write_text("<project/>", encoding="utf-8")
            shared = root / "bundler"
            shared.mkdir()
            wrapper = self.make_maven_wrapper(shared)
            project = PluginProject(str(project_path), project_path, "Currency", "maven")

            with patch("launcher.builds.shutil.which", return_value=None):
                command = build_command(project, False)

            self.assertEqual(str(wrapper), command[0])
            self.assertEqual(["-f", str(project_path / "pom.xml")], command[1:3])
            self.assertEqual("-DskipTests", command[-1])

    def test_specific_maven_operation_uses_project_wrapper(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            wrapper = self.make_maven_wrapper(root)
            project = PluginProject(str(root), root, "Demo", "maven")

            command = maven_operation_command(project, ("dependency:tree",))

            self.assertEqual([str(wrapper), "dependency:tree"], command)

    def test_incomplete_local_wrapper_falls_back_to_complete_sibling(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            project_path = root / "smp"
            project_path.mkdir()
            (project_path / "pom.xml").write_text("<project/>", encoding="utf-8")
            (project_path / "mvnw.cmd").write_text("broken wrapper", encoding="utf-8")
            shared = root / "bundler"
            shared.mkdir()
            wrapper = self.make_maven_wrapper(shared)
            project = PluginProject(str(project_path), project_path, "SMP", "maven")

            with patch("launcher.builds.shutil.which", return_value=None):
                command = maven_operation_command(project, ("package",))

            self.assertEqual(str(wrapper), command[0])
            self.assertEqual(["-f", str(project_path / "pom.xml")], command[1:3])

    def test_maven_operation_rejects_gradle_project(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            project = PluginProject(str(root), root, "Demo", "gradle")
            with self.assertRaisesRegex(ValueError, "not a Maven project"):
                maven_operation_command(project, ("test",))

    def test_selects_descriptor_jar_and_ignores_original(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "target"
            output.mkdir()
            for name in ("demo.jar", "original-demo.jar"):
                with zipfile.ZipFile(output / name, "w") as archive:
                    archive.writestr("plugin.yml", "name: Demo")
            project = PluginProject(str(root), root, "Demo", "maven")
            self.assertEqual("demo.jar", find_plugin_artifact(project).name)


if __name__ == "__main__":
    unittest.main()
