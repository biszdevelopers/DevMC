from __future__ import annotations

import hashlib
import json
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .models import PluginProject


BUILD_STATE_VERSION = 1
IGNORED_DIRECTORIES = {
    ".git",
    ".idea",
    ".gradle",
    ".settings",
    ".vscode",
    "__pycache__",
    "bin",
    "build",
    "logs",
    "node_modules",
    "obj",
    "out",
    "target",
}
BUILD_FILE_NAMES = {
    "build.gradle",
    "build.gradle.kts",
    "gradle.properties",
    "maven.config",
    "extensions.xml",
    "pom.xml",
    "settings.gradle",
    "settings.gradle.kts",
    "plugin.yml",
    "paper-plugin.yml",
}
WRAPPER_FILE_NAMES = {
    "gradle-wrapper.jar",
    "gradle-wrapper.properties",
    "maven-wrapper.jar",
    "maven-wrapper.properties",
    "mvnw",
    "mvnw.cmd",
}


@dataclass(frozen=True)
class ProjectAssessment:
    status: str
    reason: str
    source_fingerprint: str
    dependency_fingerprints: dict[str, str]
    cached_artifact: Path | None
    needs_build: bool
    needs_deploy: bool


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _is_relevant(relative_path: Path, include_tests: bool) -> bool:
    parts = relative_path.parts
    lowered = tuple(part.lower() for part in parts)
    if any(part in IGNORED_DIRECTORIES for part in lowered[:-1]):
        return False

    for index in range(len(lowered) - 1):
        if lowered[index : index + 2] == ("src", "main"):
            return True
        if include_tests and lowered[index : index + 2] == ("src", "test"):
            return True

    if any(part in {"buildsrc", "lib", "libs"} for part in lowered[:-1]):
        return True
    if any(part in {".mvn", "gradle"} for part in lowered[:-1]):
        return True
    name = lowered[-1]
    return name in BUILD_FILE_NAMES or name in WRAPPER_FILE_NAMES


def relevant_project_files(project_path: Path, include_tests: bool = False) -> list[Path]:
    files: list[Path] = []
    for root, directory_names, file_names in os.walk(project_path):
        directory_names[:] = sorted(
            name
            for name in directory_names
            if name.lower() not in IGNORED_DIRECTORIES
        )
        root_path = Path(root)
        for file_name in sorted(file_names):
            path = root_path / file_name
            if path.is_symlink():
                continue
            relative_path = path.relative_to(project_path)
            if _is_relevant(relative_path, include_tests):
                files.append(path)
    return sorted(files, key=lambda path: path.relative_to(project_path).as_posix().lower())


def project_fingerprint(project: PluginProject, include_tests: bool = False) -> str:
    digest = hashlib.sha256()
    for path in relevant_project_files(project.path, include_tests):
        relative_path = path.relative_to(project.path).as_posix()
        digest.update(relative_path.encode("utf-8", errors="surrogatepass"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def empty_build_state() -> dict[str, Any]:
    return {"version": BUILD_STATE_VERSION, "projects": {}}


def load_build_state(path: Path) -> dict[str, Any]:
    if not path.exists():
        return empty_build_state()
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return empty_build_state()
    if raw.get("version") != BUILD_STATE_VERSION or not isinstance(raw.get("projects"), dict):
        return empty_build_state()
    return raw


def save_build_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(state, indent=2, sort_keys=True) + "\n"
    fd, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(payload)
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def record_successful_build(
    state: dict[str, Any],
    project: PluginProject,
    source_fingerprint: str,
    dependency_fingerprints: dict[str, str],
    artifact: Path,
) -> None:
    projects = state.setdefault("projects", {})
    projects[project.project_id] = {
        "fingerprint": source_fingerprint,
        "dependency_fingerprints": dependency_fingerprints,
        "artifact_path": str(artifact.resolve()),
        "artifact_sha256": file_sha256(artifact),
    }


def remove_build_record(state: dict[str, Any], project_id: str) -> None:
    state.setdefault("projects", {}).pop(project_id, None)


def _load_deployment_manifest(plugins_dir: Path) -> dict[str, Any]:
    path = plugins_dir / ".development-launcher.json"
    if not path.exists():
        return {"artifacts": {}}
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"artifacts": {}}
    if not isinstance(raw.get("artifacts"), dict):
        return {"artifacts": {}}
    return raw


def assess_projects(
    projects: list[PluginProject],
    state: dict[str, Any],
    plugins_dir: Path,
    forced_project_ids: set[str] | None = None,
) -> dict[str, ProjectAssessment]:
    forced_project_ids = forced_project_ids or set()
    source_fingerprints = {
        project.project_id: project_fingerprint(project) for project in projects
    }
    by_coordinate = {
        project.coordinate: project for project in projects if project.coordinate
    }
    effective_fingerprints: dict[str, str] = {}

    def effective_fingerprint(project: PluginProject, visiting: set[str]) -> str:
        cached = effective_fingerprints.get(project.project_id)
        if cached is not None:
            return cached
        if project.project_id in visiting:
            return source_fingerprints[project.project_id]
        visiting = visiting | {project.project_id}
        digest = hashlib.sha256()
        digest.update(source_fingerprints[project.project_id].encode("ascii"))
        for coordinate in sorted(project.dependencies):
            dependency = by_coordinate.get(coordinate)
            if dependency is not None:
                digest.update(coordinate.encode("utf-8"))
                digest.update(effective_fingerprint(dependency, visiting).encode("ascii"))
        value = digest.hexdigest()
        effective_fingerprints[project.project_id] = value
        return value

    manifest = _load_deployment_manifest(plugins_dir)
    deployed = manifest.get("artifacts", {})
    records = state.get("projects", {})
    assessments: dict[str, ProjectAssessment] = {}

    for project in projects:
        dependency_fingerprints = {
            coordinate: effective_fingerprint(dependency, set())
            for coordinate in sorted(project.dependencies)
            if (dependency := by_coordinate.get(coordinate)) is not None
        }
        fingerprint = source_fingerprints[project.project_id]
        record = records.get(project.project_id)
        status = "Current"
        reason = "Source, build output, and installed JAR are current."
        needs_build = False
        needs_deploy = False
        cached_artifact: Path | None = None

        if project.project_id in forced_project_ids:
            status = "Force rebuild"
            reason = "This project was manually marked for rebuilding."
            needs_build = True
        elif not isinstance(record, dict):
            status = "Unknown"
            reason = "No successful build has been recorded yet."
            needs_build = True
        elif record.get("fingerprint") != fingerprint:
            status = "Modified"
            reason = "Relevant source or build configuration changed."
            needs_build = True
        elif record.get("dependency_fingerprints", {}) != dependency_fingerprints:
            status = "Dependency changed"
            reason = "A local plugin dependency changed since this project was built."
            needs_build = True
        else:
            artifact_value = record.get("artifact_path")
            cached_artifact = Path(artifact_value) if isinstance(artifact_value, str) else None
            if cached_artifact is None or not cached_artifact.is_file():
                status = "Build output missing"
                reason = "The previously built plugin JAR no longer exists."
                needs_build = True
                cached_artifact = None
            elif file_sha256(cached_artifact) != record.get("artifact_sha256"):
                status = "Build output changed"
                reason = "The cached plugin JAR changed outside the launcher."
                needs_build = True
                cached_artifact = None

        if not needs_build:
            deployment = deployed.get(project.project_id)
            if not isinstance(deployment, dict):
                status = "Not installed"
                reason = "A current build exists but is not installed in this server profile."
                needs_deploy = True
            else:
                filename = deployment.get("filename")
                installed = plugins_dir / filename if isinstance(filename, str) else None
                if installed is None or not installed.is_file():
                    status = "JAR missing"
                    reason = "The managed plugin JAR is missing from this server profile."
                    needs_deploy = True
                elif file_sha256(installed) != deployment.get("sha256"):
                    status = "Installed JAR changed"
                    reason = "The installed managed JAR differs from the deployment record."
                    needs_deploy = True
                elif record.get("artifact_sha256") != deployment.get("sha256"):
                    status = "Build ready"
                    reason = "A newer current build is ready to install."
                    needs_deploy = True

        assessments[project.project_id] = ProjectAssessment(
            status=status,
            reason=reason,
            source_fingerprint=fingerprint,
            dependency_fingerprints=dependency_fingerprints,
            cached_artifact=cached_artifact,
            needs_build=needs_build,
            needs_deploy=needs_deploy,
        )

    return assessments
