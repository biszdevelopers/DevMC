from __future__ import annotations

import json
import os
from dataclasses import asdict
from pathlib import Path

from .models import LauncherSettings


class SettingsStore:
    def __init__(self, path: Path) -> None:
        self.path = path

    def load(self) -> LauncherSettings:
        if not self.path.exists():
            return LauncherSettings()
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            allowed = LauncherSettings.__dataclass_fields__.keys()
            return LauncherSettings(**{key: value for key, value in raw.items() if key in allowed})
        except (OSError, ValueError, TypeError):
            return LauncherSettings()

    def save(self, settings: LauncherSettings) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(self.path.suffix + ".tmp")
        temporary.write_text(json.dumps(asdict(settings), indent=2), encoding="utf-8")
        os.replace(temporary, self.path)
