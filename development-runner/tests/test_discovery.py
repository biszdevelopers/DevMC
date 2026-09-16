from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from launcher.discovery import discover_projects, order_projects


class DiscoveryTests(unittest.TestCase):
    def make_maven_project(
        self, root: Path, folder: str, group: str, artifact: str, dependency: str | None = None
    ) -> Path:
        project = root / folder
        resources = project / "src" / "main" / "resources"
        resources.mkdir(parents=True)
        (resources / "plugin.yml").write_text(f"name: {folder.title()}\n", encoding="utf-8")
        (project / "mvnw.cmd").write_text("", encoding="utf-8")
        dependency_xml = ""
        if dependency:
            dep_group, dep_artifact = dependency.split(":")
            dependency_xml = (
                f"<dependencies><dependency><groupId>{dep_group}</groupId>"
                f"<artifactId>{dep_artifact}</artifactId></dependency></dependencies>"
            )
        (project / "pom.xml").write_text(
            f"<project><modelVersion>4.0.0</modelVersion><groupId>{group}</groupId>"
            f"<artifactId>{artifact}</artifactId>{dependency_xml}</project>",
            encoding="utf-8",
        )
        return project

    def test_discovers_projects_and_orders_internal_dependencies(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_maven_project(root, "base", "dev.test", "base")
            self.make_maven_project(root, "feature", "dev.test", "feature", "dev.test:base")
            projects, warnings = discover_projects(root)
            self.assertEqual([], warnings)
            self.assertEqual({"Base", "Feature"}, {project.name for project in projects})
            self.assertEqual(["Base", "Feature"], [project.name for project in order_projects(projects)])

    def test_requires_discovered_dependency_to_be_selected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_maven_project(root, "base", "dev.test", "base")
            self.make_maven_project(root, "feature", "dev.test", "feature", "dev.test:base")
            projects, _ = discover_projects(root)
            feature = next(project for project in projects if project.name == "Feature")
            with self.assertRaisesRegex(ValueError, "Feature requires Base"):
                order_projects([feature], projects)

    def test_ignores_build_directories_and_non_plugins(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_maven_project(root / "build", "ignored", "dev.test", "ignored")
            non_plugin = root / "library"
            non_plugin.mkdir()
            (non_plugin / "pom.xml").write_text("<project/>", encoding="utf-8")
            (non_plugin / "mvnw.cmd").write_text("", encoding="utf-8")
            projects, _ = discover_projects(root)
            self.assertEqual([], projects)

    def test_discovers_plugin_without_a_project_build_wrapper(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            project = self.make_maven_project(root, "currency", "dev.test", "currency")
            (project / "mvnw.cmd").unlink()

            projects, warnings = discover_projects(root)

            self.assertEqual([], warnings)
            self.assertEqual(["Currency"], [item.name for item in projects])


if __name__ == "__main__":
    unittest.main()
