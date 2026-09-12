from __future__ import annotations

import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

from launcher.servers import (
    build_server_command,
    display_command,
    profiles,
    set_bukkit_setting,
    set_server_property,
    split_arguments,
    validate_spigot_jar,
)


class ServerCommandTests(unittest.TestCase):
    def test_memory_and_tweaks_appear_in_command(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            profile = profiles(Path(temporary))["spigot-1.20.1"]
            command = build_server_command(
                r"C:\Program Files\Java\jdk-17\bin\java.exe",
                profile,
                "1.5",
                "6",
                "-XX:+UseG1GC -Ddemo=true",
                "nogui --port 25566",
            )
            self.assertIn("-Xms1536M", command)
            self.assertIn("-Xmx6144M", command)
            self.assertIn("-XX:+UseG1GC", command)
            self.assertIn("--port", command)
            preview = display_command(command)
            self.assertEqual(preview, subprocess.list2cmdline(command))
            self.assertIn('"C:\\Program Files\\Java', preview)

    def test_rejects_invalid_memory_range(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            profile = profiles(Path(temporary))["mohist-1.20.1"]
            with self.assertRaisesRegex(ValueError, "cannot exceed"):
                build_server_command("java", profile, "8", "4", "", "nogui")

    def test_rejects_duplicate_memory_flags(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            profile = profiles(Path(temporary))["mohist-1.20.1"]
            with self.assertRaisesRegex(ValueError, "memory fields"):
                build_server_command("java", profile, "1", "4", "-Xmx8G", "nogui")

    def test_adds_local_jdwp_debugging_flags(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            profile = profiles(Path(temporary))["mohist-1.20.1"]
            command = build_server_command("java", profile, "1", "4", "", "nogui", True, "5005", False)
            self.assertIn(
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005",
                command,
            )

    def test_rejects_invalid_debug_port(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            profile = profiles(Path(temporary))["mohist-1.20.1"]
            with self.assertRaisesRegex(ValueError, "Debug port"):
                build_server_command("java", profile, "1", "4", "", "nogui", True, "nope", False)

    def test_rejects_manual_jdwp_flag(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            profile = profiles(Path(temporary))["mohist-1.20.1"]
            with self.assertRaisesRegex(ValueError, "Debug mode controls"):
                build_server_command("java", profile, "1", "4", "-agentlib:jdwp=anything", "nogui")

    def test_splits_quoted_arguments(self) -> None:
        self.assertEqual(["-Dlabel=hello world", "nogui"], split_arguments('"-Dlabel=hello world" nogui'))

    def test_validates_spigot_1201_jar(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "spigot-1.20.1.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("META-INF/MANIFEST.MF", "Main-Class: org.bukkit.craftbukkit.bootstrap.Main\n")
                archive.writestr("org/bukkit/craftbukkit/v1_20_R1/CraftServer.class", b"")
            validate_spigot_jar(jar)

    def test_validates_bundled_spigot_1201_jar(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "spigot-1.20.1.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("META-INF/MANIFEST.MF", "Main-Class: org.bukkit.craftbukkit.bootstrap.Main\n")
                archive.writestr("META-INF/versions/spigot-1.20.1-R0.1-SNAPSHOT.jar", b"jar")
            validate_spigot_jar(jar)

    def test_sets_online_mode_without_losing_other_server_properties(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary)
            properties = runtime / "server.properties"
            properties.write_text("motd=Development Server\nonline-mode=true\n# Keep this comment\n", encoding="utf-8")
            set_server_property(runtime, "online-mode", "false")
            content = properties.read_text(encoding="utf-8")
            self.assertIn("motd=Development Server", content)
            self.assertIn("# Keep this comment", content)
            self.assertEqual(1, content.count("online-mode="))
            self.assertIn("online-mode=false", content)

    def test_sets_dimension_settings_without_removing_world_data(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary)
            nether = runtime / "world_nether"
            end = runtime / "world_the_end"
            nether.mkdir()
            end.mkdir()
            (nether / "level.dat").write_bytes(b"nether")
            (end / "level.dat").write_bytes(b"end")
            (runtime / "server.properties").write_text("motd=Development\nallow-nether=true\n", encoding="utf-8")
            (runtime / "bukkit.yml").write_text(
                "settings:\n  allow-end: true # preserve this\n  warn-on-overload: false\nspawn-limits:\n  monsters: 70\n",
                encoding="utf-8",
            )
            set_server_property(runtime, "allow-nether", "false")
            set_bukkit_setting(runtime, "settings", "allow-end", "false")
            self.assertIn("allow-nether=false", (runtime / "server.properties").read_text(encoding="utf-8"))
            bukkit = (runtime / "bukkit.yml").read_text(encoding="utf-8")
            self.assertIn("  allow-end: false # preserve this", bukkit)
            self.assertIn("  warn-on-overload: false", bukkit)
            self.assertIn("spawn-limits:", bukkit)
            self.assertEqual(b"nether", (nether / "level.dat").read_bytes())
            self.assertEqual(b"end", (end / "level.dat").read_bytes())


if __name__ == "__main__":
    unittest.main()
