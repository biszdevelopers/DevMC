from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from launcher.deployment import deploy_artifacts
from launcher.incremental import (
    assess_projects,
    empty_build_state,
    project_fingerprint,
    record_successful_build,
)
from launcher.models import PluginProject


class IncrementalBuildTests(unittest.TestCase):
    def _project(
        self,
        root: Path,
        name: str,
        group_id: str,
        dependencies: tuple[str, ...] = (),
    ) -> PluginProject:
        path = root / name
        source = path / "src" / "main" / "java"
        source.mkdir(parents=True)
        (source / f"{name}.java").write_text(f"class {name} {{}}\n", encoding="utf-8")
        (path / "pom.xml").write_text("<project />\n", encoding="utf-8")
        return PluginProject(
            project_id=name,
            path=path,
            name=name,
            build_system="maven",
            group_id=group_id,
            artifact_id=name.lower(),
            dependencies=dependencies,
        )

    def test_generated_build_outputs_do_not_change_fingerprint(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            project = self._project(Path(temporary), "Demo", "dev.test")
            before = project_fingerprint(project)
            target = project.path / "target"
            target.mkdir()
            (target / "Demo.jar").write_bytes(b"generated")
            self.assertEqual(before, project_fingerprint(project))

    def test_source_change_marks_local_dependents_dirty(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            core = self._project(root, "Core", "dev.test")
            addon = self._project(root, "Addon", "dev.test", ("dev.test:core",))
            projects = [core, addon]
            outputs = root / "outputs"
            outputs.mkdir()
            core_jar = outputs / "Core.jar"
            addon_jar = outputs / "Addon.jar"
            core_jar.write_bytes(b"core")
            addon_jar.write_bytes(b"addon")
            state = empty_build_state()
            initial = assess_projects(projects, state, root / "plugins")
            record_successful_build(
                state, core, initial["Core"].source_fingerprint, {}, core_jar
            )
            record_successful_build(
                state,
                addon,
                initial["Addon"].source_fingerprint,
                initial["Addon"].dependency_fingerprints,
                addon_jar,
            )

            (core.path / "src" / "main" / "java" / "Core.java").write_text(
                "class Core { int changed; }\n", encoding="utf-8"
            )
            changed = assess_projects(projects, state, root / "plugins")
            self.assertEqual("Modified", changed["Core"].status)
            self.assertEqual("Dependency changed", changed["Addon"].status)
            self.assertTrue(changed["Core"].needs_build)
            self.assertTrue(changed["Addon"].needs_build)

    def test_current_build_and_deployment_are_reused(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            project = self._project(root, "Demo", "dev.test")
            artifact = root / "Demo.jar"
            artifact.write_bytes(b"plugin")
            state = empty_build_state()
            first = assess_projects([project], state, root / "plugins")["Demo"]
            record_successful_build(
                state, project, first.source_fingerprint, {}, artifact
            )
            deploy_artifacts(root / "plugins", {"Demo": artifact})

            current = assess_projects([project], state, root / "plugins")["Demo"]
            self.assertEqual("Current", current.status)
            self.assertFalse(current.needs_build)
            self.assertFalse(current.needs_deploy)
            self.assertEqual(artifact.resolve(), current.cached_artifact.resolve())

    def test_dependency_changes_propagate_through_multiple_projects(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            core = self._project(root, "Core", "dev.test")
            api = self._project(root, "Api", "dev.test", ("dev.test:core",))
            feature = self._project(root, "Feature", "dev.test", ("dev.test:api",))
            projects = [core, api, feature]
            state = empty_build_state()
            initial = assess_projects(projects, state, root / "plugins")
            for project in projects:
                artifact = root / f"{project.name}.jar"
                artifact.write_bytes(project.name.encode("utf-8"))
                record_successful_build(
                    state,
                    project,
                    initial[project.project_id].source_fingerprint,
                    initial[project.project_id].dependency_fingerprints,
                    artifact,
                )

            (core.path / "pom.xml").write_text(
                "<project><version>2</version></project>\n", encoding="utf-8"
            )
            changed = assess_projects(projects, state, root / "plugins")
            self.assertEqual("Modified", changed["Core"].status)
            self.assertEqual("Dependency changed", changed["Api"].status)
            self.assertEqual("Dependency changed", changed["Feature"].status)


if __name__ == "__main__":
    unittest.main()
