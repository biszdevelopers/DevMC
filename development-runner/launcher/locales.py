from __future__ import annotations

import json
import os
from pathlib import Path


DEFAULT_LOCALE_DIR = Path(r"D:\ServerData\lang")


def list_locale_files(locale_dir: Path = DEFAULT_LOCALE_DIR) -> list[Path]:
    if not locale_dir.is_dir():
        return []
    return sorted(
        (path for path in locale_dir.iterdir() if path.is_file() and path.suffix.lower() == ".json"),
        key=lambda path: path.name.lower(),
    )


def load_locale_file(path: Path) -> dict[str, str]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except OSError as error:
        raise OSError(f"Could not read {path.name}: {error}") from error
    except json.JSONDecodeError as error:
        raise ValueError(f"{path.name} is not valid JSON: {error.msg} (line {error.lineno})") from error
    if not isinstance(raw, dict):
        raise ValueError(f"{path.name} must contain one JSON object of translation keys and values")
    if not all(isinstance(key, str) and isinstance(value, str) for key, value in raw.items()):
        raise ValueError(f"{path.name} must contain only string translation keys and values")
    return raw


def save_locale_file(path: Path, values: dict[str, str]) -> None:
    if not all(isinstance(key, str) and key.strip() and isinstance(value, str) for key, value in values.items()):
        raise ValueError("Locale entries must have non-empty string keys and string values")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(values, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def most_complete_editable_locale(documents: dict[str, dict[str, str]]) -> str | None:
    """Return the editable locale with the most entries, ignoring Mojang references."""
    candidates = (
        filename
        for filename in sorted(documents, key=str.casefold)
        if not Path(filename).stem.casefold().endswith("_mojang")
    )
    return max(candidates, key=lambda filename: len(documents[filename]), default=None)


def missing_locale_entries(reference: dict[str, str], current: dict[str, str]) -> dict[str, str]:
    """Copy only translation keys absent from the selected locale."""
    return {key: value for key, value in reference.items() if key not in current}
