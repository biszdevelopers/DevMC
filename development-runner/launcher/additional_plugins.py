from __future__ import annotations

import hashlib
from pathlib import Path


def additional_plugin_id(path: Path) -> str:
    normalized = str(path.expanduser().resolve()).casefold().encode("utf-8")
    return "additional:" + hashlib.sha256(normalized).hexdigest()


def collect_additional_plugin_artifacts(paths: list[str]) -> dict[str, Path]:
    return _collect_artifacts(paths, "additional", "plugin")


def additional_mod_id(path: Path) -> str:
    normalized = str(path.expanduser().resolve()).casefold().encode("utf-8")
    return "mod:" + hashlib.sha256(normalized).hexdigest()


def collect_additional_mod_artifacts(paths: list[str]) -> dict[str, Path]:
    return _collect_artifacts(paths, "mod", "mod")


def _collect_artifacts(paths: list[str], prefix: str, label: str) -> dict[str, Path]:
    artifacts: dict[str, Path] = {}
    for configured_path in paths:
        path = Path(configured_path).expanduser().resolve()
        if path.suffix.casefold() != ".jar":
            raise ValueError(f"Additional {label} is not a JAR: {path}")
        if not path.is_file():
            raise FileNotFoundError(f"Additional {label} JAR was not found: {path}")
        normalized = str(path).casefold().encode("utf-8")
        artifacts[prefix + ":" + hashlib.sha256(normalized).hexdigest()] = path
    return artifacts
