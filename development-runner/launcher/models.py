from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal


BuildSystem = Literal["maven", "gradle"]


@dataclass(frozen=True)
class PluginProject:
    project_id: str
    path: Path
    name: str
    build_system: BuildSystem
    group_id: str | None = None
    artifact_id: str | None = None
    dependencies: tuple[str, ...] = ()

    @property
    def coordinate(self) -> str | None:
        if self.group_id and self.artifact_id:
            return f"{self.group_id}:{self.artifact_id}"
        return None


@dataclass(frozen=True)
class ServerProfile:
    profile_id: str
    display_name: str
    kind: Literal["mohist", "spigot"]
    version: str
    runtime_dir: Path

    @property
    def server_jar(self) -> Path:
        return self.runtime_dir / "server.jar"

    @property
    def plugins_dir(self) -> Path:
        return self.runtime_dir / "plugins"

    @property
    def mods_dir(self) -> Path:
        return self.runtime_dir / "mods"


@dataclass
class LauncherSettings:
    plugin_root: str = "D:\\NewServer"
    selected_projects: list[str] = field(default_factory=list)
    plugin_states: dict[str, str] = field(default_factory=dict)
    additional_plugin_paths: list[str] = field(default_factory=list)
    additional_mod_paths: dict[str, list[str]] = field(default_factory=dict)
    incremental_builds: bool = True
    active_profile: str = "mohist-1.20.1"
    mohist_source: str = str(Path.home() / "Downloads" / "server.jar")
    spigot_source: str = str(Path.home() / "Downloads" / "spigot-1.20.1.jar")
    java_path: str = ""
    initial_memory_gb: str = "1"
    maximum_memory_gb: str = "4"
    extra_jvm_args: str = ""
    server_args: str = "nogui"
    debug_enabled: bool = False
    debug_port: str = "5005"
    debug_suspend: bool = False
    online_mode: dict[str, bool] = field(default_factory=dict)
    dimensions: dict[str, dict[str, bool]] = field(default_factory=dict)
    eula_accepted: dict[str, bool] = field(default_factory=dict)
