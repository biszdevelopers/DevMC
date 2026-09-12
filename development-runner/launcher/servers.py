from __future__ import annotations

import os
import re
import shlex
import shutil
import subprocess
import threading
import zipfile
from pathlib import Path
from typing import Callable

from .models import ServerProfile


def find_java17() -> str:
    candidates = [
        Path(r"C:\Program Files\Java\jdk-17\bin\java.exe"),
        Path(r"C:\Program Files\Eclipse Adoptium\jdk-17.0.0.0-hotspot\bin\java.exe"),
    ]
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidates.append(Path(java_home) / "bin" / "java.exe")
    candidates.extend(Path(path) for path in [shutil.which("java") or ""] if path)
    return str(next((path for path in candidates if path.is_file()), candidates[0]))


def profiles(data_dir: Path) -> dict[str, ServerProfile]:
    server_root = data_dir / "servers"
    return {
        "mohist-1.20.1": ServerProfile(
            "mohist-1.20.1", "Mohist 1.20.1", "mohist", "1.20.1", server_root / "mohist-1.20.1"
        ),
        "spigot-1.20.1": ServerProfile(
            "spigot-1.20.1", "Spigot 1.20.1", "spigot", "1.20.1", server_root / "spigot-1.20.1"
        ),
    }


def validate_mohist_jar(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Mohist server jar was not found: {path}")
    try:
        with zipfile.ZipFile(path) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
    except (OSError, KeyError, zipfile.BadZipFile) as error:
        raise RuntimeError(f"Invalid server jar: {path}") from error
    lowered = manifest.lower()
    if "mohist" not in lowered or "1.20.1" not in lowered:
        raise RuntimeError("The selected jar is not a Mohist 1.20.1 server jar")


def validate_spigot_jar(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Spigot server jar was not found: {path}")
    try:
        with zipfile.ZipFile(path) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
            names = archive.namelist()
    except (OSError, KeyError, zipfile.BadZipFile) as error:
        raise RuntimeError(f"Invalid server jar: {path}") from error
    is_spigot = "Main-Class: org.bukkit.craftbukkit.bootstrap.Main" in manifest
    has_legacy_1201_classes = any(name.startswith("org/bukkit/craftbukkit/v1_20_R1/") for name in names)
    has_1201_version_bundle = any(
        name.lower().startswith("meta-inf/versions/spigot-1.20.1") and name.lower().endswith(".jar")
        for name in names
    )
    if not is_spigot or not (has_legacy_1201_classes or has_1201_version_bundle):
        raise RuntimeError("The selected jar is not a Spigot 1.20.1 server jar")


def _install_server_jar(profile: ServerProfile, source: Path) -> None:
    profile.runtime_dir.mkdir(parents=True, exist_ok=True)
    if not profile.server_jar.exists() or source.stat().st_mtime_ns != profile.server_jar.stat().st_mtime_ns:
        temporary = profile.server_jar.with_suffix(".jar.tmp")
        shutil.copy2(source, temporary)
        os.replace(temporary, profile.server_jar)


def install_mohist(profile: ServerProfile, source: Path) -> None:
    validate_mohist_jar(source)
    _install_server_jar(profile, source)


def install_spigot(profile: ServerProfile, source: Path) -> None:
    validate_spigot_jar(source)
    _install_server_jar(profile, source)


def set_server_property(runtime_dir: Path, key: str, value: str) -> None:
    """Set one Java properties entry while preserving unrelated server settings and comments."""
    path = runtime_dir / "server.properties"
    runtime_dir.mkdir(parents=True, exist_ok=True)
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines() if path.is_file() else []
    expression = re.compile(rf"^\s*{re.escape(key)}\s*=", re.IGNORECASE)
    retained = [line for line in lines if not expression.match(line)]
    retained.append(f"{key}={value}")
    temporary = path.with_suffix(".properties.tmp")
    temporary.write_text("\n".join(retained) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def set_bukkit_setting(runtime_dir: Path, section: str, key: str, value: str) -> None:
    """Update one simple Bukkit YAML scalar without reformatting unrelated config."""
    path = runtime_dir / "bukkit.yml"
    runtime_dir.mkdir(parents=True, exist_ok=True)
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines() if path.is_file() else []
    section_pattern = re.compile(rf"^(?P<indent>\s*){re.escape(section)}\s*:\s*(?:#.*)?$", re.IGNORECASE)
    key_pattern = re.compile(
        rf"^(?P<indent>\s*){re.escape(key)}\s*:\s*(?P<current>[^#]*)(?P<comment>\s*(?:#.*)?)$",
        re.IGNORECASE,
    )
    section_index: int | None = None
    section_indent = ""
    for index, line in enumerate(lines):
        match = section_pattern.match(line)
        if match:
            section_index = index
            section_indent = match.group("indent")
            break

    rendered = str(value).lower()
    if section_index is None:
        if lines and lines[-1].strip():
            lines.append("")
        lines.extend([f"{section}:", f"  {key}: {rendered}"])
    else:
        block_end = len(lines)
        key_index: int | None = None
        for index in range(section_index + 1, len(lines)):
            line = lines[index]
            if line.strip() and not line.lstrip().startswith("#"):
                indentation = line[: len(line) - len(line.lstrip())]
                if len(indentation) <= len(section_indent):
                    block_end = index
                    break
            match = key_pattern.match(line)
            if match and len(match.group("indent")) > len(section_indent):
                key_index = index
                break
        if key_index is not None:
            match = key_pattern.match(lines[key_index])
            assert match is not None
            comment = match.group("comment").strip()
            suffix = f" {comment}" if comment else ""
            lines[key_index] = f"{match.group('indent')}{key}: {rendered}{suffix}"
        else:
            lines.insert(block_end, f"{section_indent}  {key}: {rendered}")

    temporary = path.with_suffix(".yml.tmp")
    temporary.write_text("\n".join(lines) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def split_arguments(value: str) -> list[str]:
    if not value.strip():
        return []
    parts = shlex.split(value, posix=False)
    return [part[1:-1] if len(part) >= 2 and part[0] == part[-1] and part[0] in "\"'" else part for part in parts]


def validate_java17(java_path: str) -> None:
    executable = Path(java_path)
    if not executable.is_file():
        raise FileNotFoundError(f"Java executable was not found: {java_path}")
    result = subprocess.run(
        [str(executable), "-version"], capture_output=True, text=True, encoding="utf-8",
        errors="replace", creationflags=subprocess.CREATE_NO_WINDOW, timeout=15,
    )
    version_output = result.stderr + result.stdout
    first_line = version_output.splitlines()[0] if version_output.splitlines() else ""
    if result.returncode or not any(marker in first_line for marker in ('version "17.', 'openjdk version "17.')):
        raise RuntimeError(f"Minecraft 1.20.1 requires the selected Java 17 executable; found: {first_line or 'unknown'}")


def build_server_command(
    java_path: str,
    profile: ServerProfile,
    initial_memory_gb: str,
    maximum_memory_gb: str,
    extra_jvm_args: str,
    server_args: str,
    debug_enabled: bool = False,
    debug_port: str = "5005",
    debug_suspend: bool = False,
) -> list[str]:
    try:
        initial = float(initial_memory_gb)
        maximum = float(maximum_memory_gb)
    except ValueError as error:
        raise ValueError("Memory values must be numbers in gigabytes") from error
    if initial <= 0 or maximum <= 0 or initial > maximum:
        raise ValueError("Memory must be positive and initial memory cannot exceed maximum memory")

    def memory_arg(prefix: str, value: float) -> str:
        megabytes = int(value * 1024)
        return f"{prefix}{megabytes}M"

    extra_arguments = split_arguments(extra_jvm_args)
    if any(argument.lower().startswith(("-xms", "-xmx")) for argument in extra_arguments):
        raise ValueError("Use the memory fields instead of adding -Xms or -Xmx to extra JVM flags")
    if any("jdwp" in argument.lower() for argument in extra_arguments):
        raise ValueError("Use the Debug mode controls instead of adding JDWP flags to extra JVM flags")
    debug_arguments: list[str] = []
    if debug_enabled:
        try:
            port = int(debug_port)
        except ValueError as error:
            raise ValueError("Debug port must be a number from 1 through 65535") from error
        if not 1 <= port <= 65535:
            raise ValueError("Debug port must be a number from 1 through 65535")
        suspend = "y" if debug_suspend else "n"
        debug_arguments.append(
            f"-agentlib:jdwp=transport=dt_socket,server=y,suspend={suspend},address=127.0.0.1:{port}"
        )
    return [
        java_path,
        memory_arg("-Xms", initial),
        memory_arg("-Xmx", maximum),
        *extra_arguments,
        *debug_arguments,
        "-jar",
        str(profile.server_jar),
        *split_arguments(server_args),
    ]


def display_command(command: list[str]) -> str:
    return subprocess.list2cmdline(command)


class ServerProcess:
    def __init__(self, output: Callable[[str], None], exited: Callable[[int], None]) -> None:
        self.output = output
        self.exited = exited
        self.process: subprocess.Popen[str] | None = None
        self._lock = threading.Lock()

    @property
    def running(self) -> bool:
        return self.process is not None and self.process.poll() is None

    def start(self, command: list[str], cwd: Path) -> None:
        with self._lock:
            if self.running:
                raise RuntimeError("A server is already running")
            cwd.mkdir(parents=True, exist_ok=True)
            startup = subprocess.STARTUPINFO()
            startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            self.process = subprocess.Popen(
                command,
                cwd=cwd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1,
                startupinfo=startup,
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
        threading.Thread(target=self._read_output, daemon=True).start()

    def _read_output(self) -> None:
        process = self.process
        assert process is not None and process.stdout is not None
        for line in process.stdout:
            self.output(line.rstrip("\r\n"))
        code = process.wait()
        self.exited(code)

    def send(self, command: str) -> None:
        if not self.running or not self.process or not self.process.stdin:
            raise RuntimeError("The server is not running")
        self.process.stdin.write(command.rstrip("\r\n") + "\n")
        self.process.stdin.flush()

    def stop(self) -> None:
        self.send("stop")

    def terminate(self) -> None:
        if self.running and self.process:
            self.process.terminate()
