from __future__ import annotations

import hashlib
import json
import os
import shutil
import tempfile
from dataclasses import dataclass
from pathlib import Path


MANIFEST_NAME = ".development-launcher.json"


@dataclass(frozen=True)
class DeploymentResult:
    installed: tuple[str, ...]
    reused: tuple[str, ...]
    removed: tuple[str, ...]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _load_manifest(plugins_dir: Path) -> dict:
    try:
        return json.loads((plugins_dir / MANIFEST_NAME).read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError):
        return {"artifacts": {}}


def deploy_artifacts(
    plugins_dir: Path,
    artifacts: dict[str, Path],
    desired_project_ids: set[str] | None = None,
    adopt_filenames: set[str] | None = None,
) -> DeploymentResult:
    plugins_dir.mkdir(parents=True, exist_ok=True)
    old_manifest = _load_manifest(plugins_dir)
    old_artifacts = old_manifest.get("artifacts", {})
    old_filenames = {
        item.get("filename", "").lower() for item in old_artifacts.values() if item.get("filename")
    }
    adopt_filenames = {filename.lower() for filename in (adopt_filenames or set())}
    desired_project_ids = (
        set(artifacts) if desired_project_ids is None else set(desired_project_ids)
    )
    unexpected = set(artifacts) - desired_project_ids
    if unexpected:
        raise RuntimeError(
            "Artifacts were supplied for unselected projects: "
            + ", ".join(sorted(unexpected))
        )

    retained: dict[str, dict[str, str]] = {}
    for project_id in desired_project_ids - set(artifacts):
        item = old_artifacts.get(project_id)
        if not isinstance(item, dict):
            raise RuntimeError(
                f"Inactive project {project_id} has no launcher-managed installed JAR. "
                "Enable it for one launch before setting it inactive."
            )
        filename = item.get("filename")
        installed = plugins_dir / filename if isinstance(filename, str) else None
        if installed is None or not installed.is_file():
            raise RuntimeError(
                f"The installed JAR for inactive project {project_id} is missing. "
                "Enable it to rebuild and reinstall it."
            )
        retained[project_id] = {
            "filename": filename,
            "sha256": _sha256(installed),
        }

    filenames = [
        item["filename"] for item in retained.values()
    ] + [artifact.name for artifact in artifacts.values()]
    if len(set(name.lower() for name in filenames)) != len(filenames):
        raise RuntimeError("Two selected projects produced the same plugin jar filename")
    collisions = [
        name for name in filenames
        if (
            (plugins_dir / name).is_file()
            and name.lower() not in old_filenames
            and name.lower() not in adopt_filenames
        )
    ]
    if collisions:
        raise RuntimeError(
            "Refusing to overwrite plugin jars not managed by this launcher: " + ", ".join(collisions)
        )

    stage = Path(tempfile.mkdtemp(prefix=".launcher-stage-", dir=plugins_dir))
    backup = Path(tempfile.mkdtemp(prefix=".launcher-backup-", dir=plugins_dir))
    touched = {item.get("filename") for item in old_artifacts.values() if item.get("filename")}
    touched.update(filenames)
    installed_names: list[str] = []
    reused_names: list[str] = [item["filename"] for item in retained.values()]
    removed_names: list[str] = []
    try:
        incoming: dict[str, dict[str, str]] = {}
        for project_id, artifact in artifacts.items():
            if not artifact.is_file():
                raise RuntimeError(f"Artifact does not exist: {artifact}")
            artifact_hash = _sha256(artifact)
            incoming[project_id] = {
                "filename": artifact.name,
                "sha256": artifact_hash,
            }
            installed = plugins_dir / artifact.name
            old_item = old_artifacts.get(project_id)
            if (
                isinstance(old_item, dict)
                and old_item == incoming[project_id]
                and installed.is_file()
                and _sha256(installed) == artifact_hash
            ):
                reused_names.append(artifact.name)
                continue
            shutil.copy2(artifact, stage / artifact.name)
            installed_names.append(artifact.name)
        for filename in touched:
            current = plugins_dir / filename
            if current.is_file():
                shutil.copy2(current, backup / filename)
        try:
            for item in old_artifacts.values():
                filename = item.get("filename")
                if filename and filename not in filenames:
                    installed = plugins_dir / filename
                    if installed.exists():
                        installed.unlink()
                        removed_names.append(filename)
            new_manifest = {"artifacts": dict(retained)}
            for project_id, artifact in artifacts.items():
                staged = stage / artifact.name
                if staged.exists():
                    os.replace(staged, plugins_dir / artifact.name)
                new_manifest["artifacts"][project_id] = incoming[project_id]
            temporary = plugins_dir / (MANIFEST_NAME + ".tmp")
            temporary.write_text(json.dumps(new_manifest, indent=2), encoding="utf-8")
            os.replace(temporary, plugins_dir / MANIFEST_NAME)
            return DeploymentResult(
                installed=tuple(sorted(installed_names)),
                reused=tuple(sorted(reused_names)),
                removed=tuple(sorted(removed_names)),
            )
        except Exception:
            for filename in touched:
                current = plugins_dir / filename
                saved = backup / filename
                if saved.is_file():
                    shutil.copy2(saved, current)
                else:
                    current.unlink(missing_ok=True)
            raise
    finally:
        shutil.rmtree(stage, ignore_errors=True)
        shutil.rmtree(backup, ignore_errors=True)
