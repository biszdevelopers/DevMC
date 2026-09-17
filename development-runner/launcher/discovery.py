from __future__ import annotations

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

from .models import PluginProject


IGNORED_DIRECTORIES = {
    ".git", ".gradle", ".idea", ".mvn", ".vscode", "$recycle.bin",
    "build", "target", "node_modules", "out", "bin", "obj",
    "system volume information", "libraries", "logs", "world", "world_nether",
    "world_the_end",
}


def _plugin_descriptor(project: Path) -> Path | None:
    candidates = (
        project / "src" / "main" / "resources" / "plugin.yml",
        project / "src" / "main" / "resources" / "paper-plugin.yml",
        project / "plugin.yml",
        project / "paper-plugin.yml",
    )
    return next((candidate for candidate in candidates if candidate.is_file()), None)


def _plugin_name(descriptor: Path, fallback: str) -> str:
    try:
        for line in descriptor.read_text(encoding="utf-8-sig", errors="replace").splitlines():
            match = re.match(r"^\s*name\s*:\s*['\"]?([^'\"#]+)", line, re.IGNORECASE)
            if match:
                return match.group(1).strip()
    except OSError:
        pass
    return fallback


def _maven_metadata(pom: Path) -> tuple[str | None, str | None, tuple[str, ...]]:
    try:
        root = ET.parse(pom).getroot()
        namespace = root.tag[1:].partition("}")[0] if root.tag.startswith("{") else ""
        prefix = f"{{{namespace}}}" if namespace else ""

        def child_text(node: ET.Element, name: str) -> str | None:
            item = node.find(prefix + name)
            return item.text.strip() if item is not None and item.text else None

        group = child_text(root, "groupId")
        if not group:
            parent = root.find(prefix + "parent")
            group = child_text(parent, "groupId") if parent is not None else None
        artifact = child_text(root, "artifactId")
        dependencies: list[str] = []
        deps_node = root.find(prefix + "dependencies")
        if deps_node is not None:
            for dependency in deps_node.findall(prefix + "dependency"):
                dep_group = child_text(dependency, "groupId")
                dep_artifact = child_text(dependency, "artifactId")
                if dep_group and dep_artifact and "${" not in dep_group + dep_artifact:
                    dependencies.append(f"{dep_group}:{dep_artifact}")
        return group, artifact, tuple(dependencies)
    except (OSError, ET.ParseError):
        return None, None, ()


def discover_projects(root: Path) -> tuple[list[PluginProject], list[str]]:
    projects: list[PluginProject] = []
    warnings: list[str] = []
    if not root.is_dir():
        return [], [f"Plugin root does not exist: {root}"]

    def on_error(error: OSError) -> None:
        warnings.append(str(error))

    for current, directories, files in os.walk(root, topdown=True, onerror=on_error, followlinks=False):
        directories[:] = [
            name for name in directories
            if name.lower() not in IGNORED_DIRECTORIES
            and not name.startswith(".")
            and not (Path(current) / name).is_symlink()
        ]
        file_names = set(files)
        build_system = None
        # A build wrapper is a way to run the build, not part of what makes a
        # directory a plugin project. Wrapper-less projects can use Maven or
        # Gradle from PATH, or a shared wrapper beside the project.
        if "pom.xml" in file_names:
            build_system = "maven"
        elif {"build.gradle", "build.gradle.kts"} & file_names:
            build_system = "gradle"
        if not build_system:
            continue
        project_path = Path(current).resolve()
        descriptor = _plugin_descriptor(project_path)
        if not descriptor:
            continue
        group = artifact = None
        dependencies: tuple[str, ...] = ()
        if build_system == "maven":
            group, artifact, dependencies = _maven_metadata(project_path / "pom.xml")
        projects.append(PluginProject(
            project_id=os.path.normcase(str(project_path)),
            path=project_path,
            name=_plugin_name(descriptor, artifact or project_path.name),
            build_system=build_system,
            group_id=group,
            artifact_id=artifact,
            dependencies=dependencies,
        ))
        directories[:] = []
    projects.sort(key=lambda project: (project.name.lower(), str(project.path).lower()))
    return projects, warnings


def order_projects(
    projects: list[PluginProject], known_projects: list[PluginProject] | None = None
) -> list[PluginProject]:
    selected_ids = {project.project_id for project in projects}
    known_by_coordinate = {
        project.coordinate: project for project in (known_projects or projects) if project.coordinate
    }
    missing = []
    for project in projects:
        for coordinate in project.dependencies:
            dependency = known_by_coordinate.get(coordinate)
            if dependency and dependency.project_id not in selected_ids:
                missing.append(f"{project.name} requires {dependency.name}")
    if missing:
        raise ValueError("Select required plugin projects: " + "; ".join(sorted(set(missing))))
    by_coordinate = {project.coordinate: project for project in projects if project.coordinate}
    dependencies = {
        project.project_id: {
            by_coordinate[coordinate].project_id
            for coordinate in project.dependencies
            if coordinate in by_coordinate
        }
        for project in projects
    }
    by_id = {project.project_id: project for project in projects}
    ordered: list[PluginProject] = []
    ready = sorted(
        (project for project in projects if not dependencies[project.project_id]),
        key=lambda item: str(item.path).lower(),
    )
    while ready:
        project = ready.pop(0)
        ordered.append(project)
        for candidate in projects:
            remaining = dependencies[candidate.project_id]
            if project.project_id in remaining:
                remaining.remove(project.project_id)
                if not remaining and candidate not in ordered and candidate not in ready:
                    ready.append(candidate)
                    ready.sort(key=lambda item: str(item.path).lower())
    if len(ordered) != len(projects):
        unresolved = ", ".join(project.name for project in projects if project not in ordered)
        raise ValueError(f"Dependency cycle between selected projects: {unresolved}")
    return ordered
