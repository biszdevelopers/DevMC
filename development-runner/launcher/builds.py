from __future__ import annotations

import shutil
import subprocess
import zipfile
from pathlib import Path
from threading import Event
from typing import Callable

from .models import PluginProject


OutputCallback = Callable[[str], None]


def _complete_wrapper(directory: Path, build_system: str) -> Path | None:
    if build_system == "maven":
        names = ("mvnw.cmd", "mvnw")
        support_files = (directory / ".mvn" / "wrapper" / "maven-wrapper.properties",)
    else:
        names = ("gradlew.bat", "gradlew")
        support_files = (
            directory / "gradle" / "wrapper" / "gradle-wrapper.properties",
            directory / "gradle" / "wrapper" / "gradle-wrapper.jar",
        )
    wrapper = next((directory / name for name in names if (directory / name).is_file()), None)
    if wrapper and all(path.is_file() for path in support_files):
        return wrapper
    return None


def _build_launcher(project: PluginProject) -> tuple[str, bool]:
    local_wrapper = _complete_wrapper(project.path, project.build_system)
    if local_wrapper:
        return str(local_wrapper), False

    executable_names = ("mvn.cmd", "mvn") if project.build_system == "maven" else ("gradle.bat", "gradle")
    executable = next((path for name in executable_names if (path := shutil.which(name))), None)
    if executable:
        return executable, False

    # Projects under the same plugin root commonly share one wrapper. It is
    # safe to use that wrapper while explicitly targeting this project's build.
    try:
        siblings = sorted(
            (path for path in project.path.parent.iterdir() if path.is_dir() and path != project.path),
            key=lambda path: path.name.lower(),
        )
    except OSError:
        siblings = []
    for sibling in siblings:
        wrapper = _complete_wrapper(sibling, project.build_system)
        if wrapper:
            return str(wrapper), True

    tool = "Maven (mvn/mvnw)" if project.build_system == "maven" else "Gradle (gradle/gradlew)"
    raise FileNotFoundError(
        f"No {tool} launcher is available for {project.name}. Add a project wrapper, install the build tool, "
        f"or place the project beside another project with the same type of wrapper."
    )


def build_command(project: PluginProject, run_tests: bool) -> list[str]:
    if project.build_system == "maven":
        arguments = ["clean", "install"]
        if not run_tests:
            arguments.append("-DskipTests")
        return maven_operation_command(project, arguments)
    launcher, shared_wrapper = _build_launcher(project)
    command = [launcher]
    if shared_wrapper:
        command.extend(["-p", str(project.path)])
    command.extend(["clean", "build"])
    if not run_tests:
        command.extend(["-x", "test"])
    return command


def maven_operation_command(project: PluginProject, arguments: list[str] | tuple[str, ...]) -> list[str]:
    if project.build_system != "maven":
        raise ValueError(f"{project.name} is not a Maven project")
    if not arguments:
        raise ValueError("Select at least one Maven operation")
    launcher, shared_wrapper = _build_launcher(project)
    command = [launcher]
    if shared_wrapper:
        command.extend(["-f", str(project.path / "pom.xml")])
    command.extend(arguments)
    return command


def run_streaming(
    command: list[str],
    cwd: Path,
    output: OutputCallback,
    cancel: Event | None = None,
) -> None:
    startup = subprocess.STARTUPINFO()
    startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    process = subprocess.Popen(
        command,
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        errors="replace",
        startupinfo=startup,
        creationflags=subprocess.CREATE_NO_WINDOW,
    )
    assert process.stdout is not None
    for line in process.stdout:
        output(line.rstrip("\r\n"))
        if cancel and cancel.is_set():
            process.terminate()
            raise RuntimeError("Operation cancelled")
    code = process.wait()
    if code:
        raise RuntimeError(f"Command failed with exit code {code}: {subprocess.list2cmdline(command)}")


def build_project(project: PluginProject, run_tests: bool, output: OutputCallback) -> Path:
    command = build_command(project, run_tests)
    output(f"> {subprocess.list2cmdline(command)}")
    run_streaming(command, project.path, output)
    return find_plugin_artifact(project)


def run_maven_operation(
    project: PluginProject,
    arguments: list[str] | tuple[str, ...],
    output: OutputCallback,
) -> None:
    command = maven_operation_command(project, arguments)
    output(f"> {subprocess.list2cmdline(command)}")
    run_streaming(command, project.path, output)


def _contains_plugin_descriptor(jar_path: Path) -> bool:
    try:
        with zipfile.ZipFile(jar_path) as archive:
            names = {name.lower() for name in archive.namelist()}
            return "plugin.yml" in names or "paper-plugin.yml" in names
    except (OSError, zipfile.BadZipFile):
        return False


def find_plugin_artifact(project: PluginProject) -> Path:
    output_dir = project.path / ("target" if project.build_system == "maven" else "build/libs")
    if not output_dir.is_dir():
        raise FileNotFoundError(f"No build output directory was created for {project.name}")
    ignored = ("original-", "-sources.jar", "-javadoc.jar", "-tests.jar", "-plain.jar")
    candidates = [
        jar for jar in output_dir.glob("*.jar")
        if not jar.name.startswith(ignored[0])
        and not any(jar.name.endswith(suffix) for suffix in ignored[1:])
        and _contains_plugin_descriptor(jar)
    ]
    if len(candidates) != 1:
        names = ", ".join(jar.name for jar in candidates) or "none"
        raise RuntimeError(f"Expected one deployable plugin jar for {project.name}; found: {names}")
    return candidates[0]
