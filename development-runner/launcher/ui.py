from __future__ import annotations

import queue
import subprocess
import threading
import time
import tkinter as tk
import webbrowser
from pathlib import Path
from tkinter import filedialog, messagebox, simpledialog, ttk

from .additional_plugins import collect_additional_mod_artifacts, collect_additional_plugin_artifacts
from .builds import build_project, run_maven_operation
from .deployment import deploy_artifacts
from .discovery import discover_projects, order_projects
from .incremental import (
    assess_projects,
    load_build_state,
    record_successful_build,
    remove_build_record,
    save_build_state,
)
from .models import LauncherSettings, PluginProject
from .locales import (
    DEFAULT_LOCALE_DIR,
    list_locale_files,
    load_locale_file,
    missing_locale_entries,
    most_complete_editable_locale,
    save_locale_file,
)
from .servers import (
    ServerProcess,
    build_server_command,
    display_command,
    find_java17,
    install_mohist,
    install_spigot,
    profiles,
    set_bukkit_setting,
    set_server_property,
    validate_java17,
)
from .settings import SettingsStore
from .menu_maker_ui import MenuMakerPane
from .economy_ui import EconomyPane


CONFIG_FILES = ("server.properties", "bukkit.yml", "spigot.yml")
MAVEN_OPERATIONS = (
    ("Clean", ("clean",)),
    ("Compile", ("compile",)),
    ("Run Tests", ("test",)),
    ("Package", ("package",)),
    ("Verify", ("verify",)),
    ("Install", ("install",)),
    ("Dependency Tree", ("dependency:tree",)),
    ("Clean & Install (skip tests)", ("clean", "install", "-DskipTests")),
)
PLUGIN_MODES = ("enabled", "inactive", "disabled")
PLUGIN_MODE_LABELS = {
    "enabled": "Enabled",
    "inactive": "Inactive",
    "disabled": "Disabled",
}


class LauncherApp:
    def __init__(self, root: tk.Tk, base_dir: Path) -> None:
        self.root = root
        self.base_dir = base_dir
        self.data_dir = base_dir / "data"
        self.store = SettingsStore(self.data_dir / "settings.json")
        self.settings = self.store.load()
        legacy_root = self.settings.plugin_root.replace("/", "\\").rstrip("\\").casefold()
        if legacy_root == "d:" and base_dir.parent.name.casefold() == "newserver":
            self.settings.plugin_root = str(base_dir.parent)
        if not self.settings.java_path:
            self.settings.java_path = find_java17()
        self.profiles = profiles(self.data_dir)
        if self.settings.active_profile not in self.profiles:
            self.settings.active_profile = "mohist-1.20.1"
        self.projects: list[PluginProject] = []
        self.additional_plugin_rows: dict[str, str] = {}
        self.additional_mod_rows: dict[str, str] = {}
        self.force_rebuild_ids: set[str] = set()
        self.build_state_path = self.data_dir / "build-state.json"
        self.events: queue.Queue[tuple[str, object]] = queue.Queue()
        self.busy = False
        self.restart_pending = False
        self.config_dirty = {filename: False for filename in CONFIG_FILES}
        self.locale_dir = DEFAULT_LOCALE_DIR
        self.locale_files: dict[str, Path] = {}
        self.locale_documents: dict[str, dict[str, str]] = {}
        self.locale_dirty: set[str] = set()
        self.locale_row_keys: dict[str, str] = {}
        self.locale_edit_entry: ttk.Entry | None = None
        self.locale_edit_context: tuple[str, str, str] | None = None
        self.log_path: Path | None = None
        self.server = ServerProcess(self._queue_output, lambda code: self.events.put(("server_exit", code)))

        self.root.title("Minecraft Development Launcher")
        self.root.geometry("1120x760")
        self.root.minsize(920, 640)
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        self._create_variables()
        self._build_ui()
        self._bind_variables()
        self._update_profile_ui()
        self.load_all_configs(prompt=False)
        self.reload_locale_files(prompt=False)
        self.root.after(80, self._drain_events)
        self.root.after(150, self.refresh_projects)

    @property
    def profile(self):
        return self.profiles[self.profile_var.get()]

    def _create_variables(self) -> None:
        self.profile_var = tk.StringVar(value=self.settings.active_profile)
        self.mohist_source_var = tk.StringVar(value=self.settings.mohist_source)
        self.spigot_source_var = tk.StringVar(value=self.settings.spigot_source)
        self.java_var = tk.StringVar(value=self.settings.java_path)
        self.xms_var = tk.StringVar(value=self.settings.initial_memory_gb)
        self.xmx_var = tk.StringVar(value=self.settings.maximum_memory_gb)
        self.jvm_args_var = tk.StringVar(value=self.settings.extra_jvm_args)
        self.server_args_var = tk.StringVar(value=self.settings.server_args)
        self.debug_enabled_var = tk.BooleanVar(value=self.settings.debug_enabled)
        self.debug_port_var = tk.StringVar(value=self.settings.debug_port)
        self.debug_suspend_var = tk.BooleanVar(value=self.settings.debug_suspend)
        self.online_mode_var = tk.BooleanVar(value=True)
        self.overworld_var = tk.BooleanVar(value=True)
        self.nether_var = tk.BooleanVar(value=True)
        self.end_var = tk.BooleanVar(value=True)
        self.eula_var = tk.BooleanVar(value=False)
        self.command_var = tk.StringVar()
        self.plugin_root_var = tk.StringVar(value=self.settings.plugin_root)
        self.incremental_build_var = tk.BooleanVar(value=self.settings.incremental_builds)
        self.locale_filter_var = tk.StringVar()
        self.locale_detail_var = tk.StringVar(value="Select a language file to edit its translations.")
        self.status_var = tk.StringVar(value="Ready")

    def _build_ui(self) -> None:
        style = ttk.Style()
        style.configure("Title.TLabel", font=("Segoe UI", 16, "bold"))
        style.configure("Heading.TLabel", font=("Segoe UI", 10, "bold"))
        outer = ttk.Frame(self.root, padding=12)
        outer.pack(fill="both", expand=True)
        ttk.Label(outer, text="Minecraft Development Launcher", style="Title.TLabel").pack(anchor="w")
        ttk.Label(outer, text="Build, deploy, configure, and run 1.20.1 development servers.").pack(anchor="w", pady=(0, 10))
        self.notebook = ttk.Notebook(outer)
        self.notebook.pack(fill="both", expand=True)
        self.server_tab = ttk.Frame(self.notebook, padding=14)
        self.plugins_tab = ttk.Frame(self.notebook, padding=14)
        self.config_tab = ttk.Frame(self.notebook, padding=14)
        self.locale_tab = ttk.Frame(self.notebook, padding=14)
        self.menu_maker_tab = ttk.Frame(self.notebook)
        self.economy_tab = EconomyPane(self.notebook, self.status_var)
        self.console_tab = ttk.Frame(self.notebook, padding=10)
        self.notebook.add(self.server_tab, text="Server")
        self.notebook.add(self.plugins_tab, text="Plugins")
        self.notebook.add(self.config_tab, text="Configurations")
        self.notebook.add(self.locale_tab, text="Locale Editor")
        self.notebook.add(self.menu_maker_tab, text="Menu Maker")
        self.notebook.add(self.economy_tab, text="Economy")
        self.notebook.add(self.console_tab, text="Console")
        self._build_server_tab()
        self._build_plugins_tab()
        self._build_config_tab()
        self._build_locale_tab()
        self._build_menu_maker_tab()
        self._build_console_tab()
        self.notebook.bind("<<NotebookTabChanged>>", self._notebook_tab_changed, add="+")
        ttk.Separator(outer).pack(fill="x", pady=(8, 4))
        ttk.Label(outer, textvariable=self.status_var).pack(anchor="w")

    def _notebook_tab_changed(self, _event: tk.Event) -> None:
        if self.notebook.select() == str(self.economy_tab):
            self.economy_tab.refresh()

    def _build_server_tab(self) -> None:
        tab = self.server_tab
        ttk.Label(tab, text="Server profile", style="Heading.TLabel").grid(row=0, column=0, sticky="w")
        profiles_frame = ttk.Frame(tab)
        profiles_frame.grid(row=1, column=0, columnspan=4, sticky="ew", pady=(5, 12))
        self.profile_buttons = []
        for column, (profile_id, profile) in enumerate(self.profiles.items()):
            button = ttk.Radiobutton(
                profiles_frame, text=f"{profile.display_name}\n{profile.kind.title()} server",
                variable=self.profile_var, value=profile_id,
            )
            button.grid(row=0, column=column, sticky="w", padx=(0, 28))
            self.profile_buttons.append(button)

        ttk.Label(tab, text="Mohist source jar").grid(row=2, column=0, sticky="w", pady=4)
        self.mohist_entry = ttk.Entry(tab, textvariable=self.mohist_source_var)
        self.mohist_entry.grid(row=2, column=1, columnspan=2, sticky="ew", padx=8)
        self.mohist_browse = ttk.Button(tab, text="Browse...", command=self._browse_mohist)
        self.mohist_browse.grid(row=2, column=3)
        ttk.Label(tab, text="Spigot source jar").grid(row=3, column=0, sticky="w", pady=4)
        self.spigot_entry = ttk.Entry(tab, textvariable=self.spigot_source_var)
        self.spigot_entry.grid(row=3, column=1, columnspan=2, sticky="ew", padx=8)
        self.spigot_browse = ttk.Button(tab, text="Browse...", command=self._browse_spigot)
        self.spigot_browse.grid(row=3, column=3)
        ttk.Label(tab, text="Java 17 executable").grid(row=4, column=0, sticky="w", pady=4)
        ttk.Entry(tab, textvariable=self.java_var).grid(row=4, column=1, columnspan=2, sticky="ew", padx=8)
        ttk.Button(tab, text="Browse...", command=self._browse_java).grid(row=4, column=3)
        self.profile_status = ttk.Label(tab)
        self.profile_status.grid(row=5, column=0, columnspan=3, sticky="w", pady=6)
        self.spigot_button = ttk.Button(tab, text="Import/Reimport Spigot", command=self.import_spigot_now)
        self.spigot_button.grid(row=5, column=3)

        memory = ttk.LabelFrame(tab, text="Memory & JVM tweaks", padding=10)
        memory.grid(row=6, column=0, columnspan=4, sticky="ew", pady=10)
        ttk.Label(memory, text="Initial memory (GB)").grid(row=0, column=0, sticky="w")
        ttk.Combobox(memory, textvariable=self.xms_var, values=("0.5", "1", "2", "4", "8"), width=9).grid(row=0, column=1, padx=(8, 22))
        ttk.Label(memory, text="Maximum memory (GB)").grid(row=0, column=2, sticky="w")
        ttk.Combobox(memory, textvariable=self.xmx_var, values=("1", "2", "4", "6", "8", "12", "16"), width=9).grid(row=0, column=3, padx=8)
        ttk.Label(memory, text="Extra JVM flags").grid(row=1, column=0, sticky="w", pady=(10, 0))
        ttk.Entry(memory, textvariable=self.jvm_args_var).grid(row=1, column=1, columnspan=3, sticky="ew", padx=(8, 0), pady=(10, 0))
        ttk.Label(memory, text="Server arguments").grid(row=2, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(memory, textvariable=self.server_args_var).grid(row=2, column=1, columnspan=3, sticky="ew", padx=(8, 0), pady=(8, 0))
        memory.columnconfigure(3, weight=1)

        debug = ttk.LabelFrame(tab, text="Java debug mode (IntelliJ attach)", padding=10)
        debug.grid(row=7, column=0, columnspan=4, sticky="ew", pady=(0, 10))
        self.debug_check = ttk.Checkbutton(
            debug, text="Enable JDWP debug server", variable=self.debug_enabled_var,
            command=self._update_debug_controls,
        )
        self.debug_check.grid(row=0, column=0, sticky="w")
        ttk.Label(debug, text="Port").grid(row=0, column=1, sticky="e", padx=(24, 5))
        self.debug_port_entry = ttk.Entry(debug, textvariable=self.debug_port_var, width=8)
        self.debug_port_entry.grid(row=0, column=2, sticky="w")
        self.debug_suspend_check = ttk.Checkbutton(
            debug, text="Suspend server until debugger connects", variable=self.debug_suspend_var,
        )
        self.debug_suspend_check.grid(row=0, column=3, sticky="w", padx=(18, 0))
        ttk.Label(debug, text="Localhost only; attach IntelliJ to 127.0.0.1.").grid(
            row=1, column=0, columnspan=4, sticky="w", pady=(7, 0)
        )

        authentication = ttk.LabelFrame(tab, text="Player authentication", padding=10)
        authentication.grid(row=8, column=0, columnspan=4, sticky="ew", pady=(0, 10))
        self.online_mode_button = ttk.Radiobutton(
            authentication, text="Online mode — authenticate players with Mojang", variable=self.online_mode_var, value=True,
        )
        self.online_mode_button.grid(row=0, column=0, sticky="w")
        self.offline_mode_button = ttk.Radiobutton(
            authentication, text="Offline mode — allow local/unverified player names", variable=self.online_mode_var, value=False,
        )
        self.offline_mode_button.grid(row=1, column=0, sticky="w", pady=(5, 0))
        ttk.Label(authentication, text="Applies to this profile's server.properties before the next launch.").grid(
            row=2, column=0, sticky="w", pady=(7, 0)
        )

        dimensions = ttk.LabelFrame(tab, text="Dimensions", padding=10)
        dimensions.grid(row=9, column=0, columnspan=4, sticky="ew", pady=(0, 10))
        ttk.Checkbutton(
            dimensions, text="Overworld (0) — required by Minecraft", variable=self.overworld_var, state="disabled",
        ).grid(row=0, column=0, sticky="w")
        self.nether_check = ttk.Checkbutton(
            dimensions, text="Nether (-1)", variable=self.nether_var,
        )
        self.nether_check.grid(row=1, column=0, sticky="w", pady=(5, 0))
        self.end_check = ttk.Checkbutton(
            dimensions, text="The End (1)", variable=self.end_var,
        )
        self.end_check.grid(row=1, column=1, sticky="w", padx=(24, 0), pady=(5, 0))
        ttk.Label(dimensions, text="Changes apply on the next restart; disabled dimension data is preserved.").grid(
            row=2, column=0, columnspan=2, sticky="w", pady=(7, 0)
        )

        eula_frame = ttk.Frame(tab)
        eula_frame.grid(row=10, column=0, columnspan=4, sticky="ew", pady=6)
        ttk.Checkbutton(eula_frame, text="I accept the Minecraft EULA for this profile", variable=self.eula_var).pack(side="left")
        eula_link = ttk.Label(eula_frame, text="View EULA", foreground="#0563c1", cursor="hand2")
        eula_link.pack(side="left", padx=8)
        eula_link.bind("<Button-1>", lambda _event: webbrowser.open("https://www.minecraft.net/eula"))

        ttk.Label(tab, text="Java run command (read-only)", style="Heading.TLabel").grid(row=11, column=0, columnspan=4, sticky="w", pady=(12, 4))
        command_frame = ttk.Frame(tab)
        command_frame.grid(row=12, column=0, columnspan=4, sticky="ew")
        self.command_entry = ttk.Entry(command_frame, textvariable=self.command_var, state="readonly")
        self.command_entry.pack(side="left", fill="x", expand=True)
        ttk.Button(command_frame, text="Copy", command=self._copy_command).pack(side="left", padx=(8, 0))

        actions = ttk.Frame(tab)
        self.launch_summary = ttk.Label(tab)
        self.launch_summary.grid(row=13, column=0, columnspan=4, sticky="w", pady=(6, 0))
        actions.grid(row=14, column=0, columnspan=4, sticky="w", pady=(14, 0))
        self.run_button = ttk.Button(actions, text="Build, Deploy & Run", command=self.run_server)
        self.run_button.pack(side="left")
        self.test_button = ttk.Button(actions, text="Build & Test", command=self.build_and_test)
        self.test_button.pack(side="left", padx=8)
        self.stop_button = ttk.Button(actions, text="Stop Server", command=self.stop_server, state="disabled")
        self.stop_button.pack(side="left")
        tab.columnconfigure(1, weight=1)
        tab.columnconfigure(2, weight=1)

    def _build_plugins_tab(self) -> None:
        path_frame = ttk.Frame(self.plugins_tab)
        path_frame.pack(fill="x", pady=(0, 10))
        ttk.Label(path_frame, text="Project root").pack(side="left")
        ttk.Entry(path_frame, textvariable=self.plugin_root_var).pack(side="left", fill="x", expand=True, padx=8)
        ttk.Button(path_frame, text="Browse...", command=self._browse_plugin_root).pack(side="left")
        self.refresh_button = ttk.Button(path_frame, text="Refresh", command=self.refresh_projects)
        self.refresh_button.pack(side="left", padx=(8, 0))
        buttons = ttk.Frame(self.plugins_tab)
        buttons.pack(fill="x", pady=(0, 7))
        ttk.Button(
            buttons, text="Enable All", command=lambda: self._set_all_plugin_modes("enabled"),
        ).pack(side="left")
        ttk.Button(
            buttons, text="Set All Inactive", command=lambda: self._set_all_plugin_modes("inactive"),
        ).pack(side="left", padx=6)
        ttk.Button(
            buttons, text="Disable All", command=lambda: self._set_all_plugin_modes("disabled"),
        ).pack(side="left")
        ttk.Button(
            buttons, text="Force rebuild enabled", command=self._force_rebuild_enabled,
        ).pack(side="left", padx=(6, 0))
        ttk.Checkbutton(
            buttons,
            text="On launch, only build changed plugins",
            variable=self.incremental_build_var,
            command=self._incremental_setting_changed,
        ).pack(side="left", padx=(14, 0))
        self.project_count = ttk.Label(buttons, text="No projects scanned")
        self.project_count.pack(side="right")
        ttk.Label(
            self.plugins_tab,
            text="Click Mode to cycle: Enabled builds/deploys; Inactive keeps the installed JAR without rebuilding; Disabled removes it.",
        ).pack(anchor="w", pady=(0, 7))
        self.plugin_tree = ttk.Treeview(
            self.plugins_tab, columns=("mode", "name", "type", "path", "status"), show="headings", selectmode="browse"
        )
        for column, title, width in (
            ("mode", "Mode", 85), ("name", "Plugin", 150), ("type", "Build", 75),
            ("path", "Project path", 530), ("status", "Status", 150),
        ):
            self.plugin_tree.heading(column, text=title)
            self.plugin_tree.column(column, width=width, stretch=column in {"path", "status"})
        self.plugin_tree.pack(fill="both", expand=True)
        self.plugin_tree.tag_configure("enabled", foreground="#167647")
        self.plugin_tree.tag_configure("inactive", foreground="#9a6700")
        self.plugin_tree.tag_configure("disabled", foreground="#6e7781")
        self.plugin_tree.bind("<Button-1>", self._tree_click)
        self.plugin_tree.bind("<Button-3>", self._show_plugin_context_menu)

        additional_frame = ttk.Labelframe(self.plugins_tab, text="Additional server plugins", padding=8)
        additional_frame.pack(fill="x", pady=(10, 0))
        ttk.Label(
            additional_frame,
            text="External JARs such as ProtocolLib and Citizens are deployed with project plugins but are not built.",
        ).pack(anchor="w", pady=(0, 6))
        additional_actions = ttk.Frame(additional_frame)
        additional_actions.pack(fill="x")
        ttk.Button(additional_actions, text="Add JARs...", command=self._add_additional_plugins).pack(side="left")
        ttk.Button(
            additional_actions,
            text="Remove Selected",
            command=self._remove_selected_additional_plugin,
        ).pack(side="left", padx=(6, 0))
        self.additional_plugin_tree = ttk.Treeview(
            additional_frame, columns=("name", "path", "status"), show="headings", height=4, selectmode="browse",
        )
        for column, title, width in (
            ("name", "JAR", 190), ("path", "Source path", 700), ("status", "Status", 90),
        ):
            self.additional_plugin_tree.heading(column, text=title)
            self.additional_plugin_tree.column(column, width=width, stretch=column == "path")
        self.additional_plugin_tree.pack(fill="x", pady=(6, 0))
        self._render_additional_plugins()

        self.additional_mods_frame = ttk.Labelframe(
            self.plugins_tab, text="Additional Mohist test mods", padding=8,
        )
        self.additional_mods_frame.pack(fill="x", pady=(10, 0))
        ttk.Label(
            self.additional_mods_frame,
            text="Test mod JARs are deployed only to the selected Mohist profile's mods folder.",
        ).pack(anchor="w", pady=(0, 6))
        mod_actions = ttk.Frame(self.additional_mods_frame)
        mod_actions.pack(fill="x")
        ttk.Button(mod_actions, text="Add Mod JARs...", command=self._add_additional_mods).pack(side="left")
        ttk.Button(
            mod_actions,
            text="Remove Selected",
            command=self._remove_selected_additional_mod,
        ).pack(side="left", padx=(6, 0))
        self.additional_mod_tree = ttk.Treeview(
            self.additional_mods_frame, columns=("name", "path", "status"), show="headings", height=4, selectmode="browse",
        )
        for column, title, width in (
            ("name", "Mod JAR", 190), ("path", "Source path", 700), ("status", "Status", 90),
        ):
            self.additional_mod_tree.heading(column, text=title)
            self.additional_mod_tree.column(column, width=width, stretch=column == "path")
        self.additional_mod_tree.pack(fill="x", pady=(6, 0))
        self._render_additional_mods()

    def _build_config_tab(self) -> None:
        ttk.Label(
            self.config_tab,
            text="Each profile has its own files. Server Properties is the frequent editor; Bukkit and Spigot are advanced.",
        ).pack(anchor="w", pady=(0, 8))
        self.config_notebook = ttk.Notebook(self.config_tab)
        self.config_notebook.pack(fill="both", expand=True)
        self.config_editors: dict[str, tk.Text] = {}
        self.config_save_buttons: dict[str, ttk.Button] = {}
        self.config_locations: dict[str, ttk.Label] = {}
        titles = {
            "server.properties": "Server Properties",
            "bukkit.yml": "Bukkit (Advanced)",
            "spigot.yml": "Spigot (Advanced)",
        }
        for filename in CONFIG_FILES:
            page = ttk.Frame(self.config_notebook, padding=10)
            self.config_notebook.add(page, text=titles[filename])
            tools = ttk.Frame(page)
            tools.pack(fill="x", pady=(0, 8))
            ttk.Button(tools, text="Reload", command=lambda name=filename: self.load_config(name)).pack(side="left")
            save_button = ttk.Button(tools, text="Save", command=lambda name=filename: self.save_config(name))
            save_button.pack(side="left", padx=6)
            location = ttk.Label(tools)
            location.pack(side="right")
            editor_frame = ttk.Frame(page)
            editor_frame.pack(fill="both", expand=True)
            vertical = ttk.Scrollbar(editor_frame)
            vertical.pack(side="right", fill="y")
            horizontal = ttk.Scrollbar(editor_frame, orient="horizontal")
            horizontal.pack(side="bottom", fill="x")
            editor = tk.Text(
                editor_frame,
                wrap="none",
                undo=True,
                font=("Consolas", 10),
                yscrollcommand=vertical.set,
                xscrollcommand=horizontal.set,
            )
            editor.pack(fill="both", expand=True)
            vertical.configure(command=editor.yview)
            horizontal.configure(command=editor.xview)
            editor.bind("<<Modified>>", lambda event, name=filename: self._config_modified(name, event))
            self.config_editors[filename] = editor
            self.config_save_buttons[filename] = save_button
            self.config_locations[filename] = location

    def _build_locale_tab(self) -> None:
        ttk.Label(self.locale_tab, text="Locale Editor", style="Title.TLabel").pack(anchor="w")
        ttk.Label(
            self.locale_tab,
            text=f"Edit translation JSON files in {self.locale_dir}. Double-click a value to edit it.",
        ).pack(anchor="w", pady=(2, 10))

        actions = ttk.Frame(self.locale_tab)
        actions.pack(fill="x", pady=(0, 8))
        ttk.Button(actions, text="Reload Files", command=self.reload_locale_files).pack(side="left")
        ttk.Button(actions, text="Save File", command=self.save_selected_locale).pack(side="left", padx=(6, 0))
        ttk.Button(actions, text="Save All", command=self.save_all_locales).pack(side="left", padx=(6, 0))
        ttk.Label(actions, textvariable=self.locale_detail_var).pack(side="right")

        panes = ttk.Panedwindow(self.locale_tab, orient="horizontal")
        panes.pack(fill="both", expand=True)
        file_frame = ttk.Labelframe(panes, text="Language files", padding=8)
        table_frame = ttk.Labelframe(panes, text="Translations", padding=8)
        panes.add(file_frame, weight=1)
        panes.add(table_frame, weight=4)

        file_scroll = ttk.Scrollbar(file_frame)
        file_scroll.pack(side="right", fill="y")
        self.locale_file_tree = ttk.Treeview(
            file_frame, columns=("file", "kind", "size"), show="headings", selectmode="browse",
            yscrollcommand=file_scroll.set,
        )
        for column, title, width in (("file", "Language file", 160), ("kind", "Type", 105), ("size", "Size", 70)):
            self.locale_file_tree.heading(column, text=title)
            self.locale_file_tree.column(column, width=width, stretch=column == "file")
        self.locale_file_tree.pack(fill="both", expand=True)
        file_scroll.configure(command=self.locale_file_tree.yview)
        self.locale_file_tree.bind("<<TreeviewSelect>>", self._locale_file_selected)

        table_tools = ttk.Frame(table_frame)
        table_tools.pack(fill="x", pady=(0, 8))
        ttk.Label(table_tools, text="Filter").pack(side="left")
        filter_entry = ttk.Entry(table_tools, textvariable=self.locale_filter_var)
        filter_entry.pack(side="left", fill="x", expand=True, padx=(6, 12))
        self.locale_filter_var.trace_add("write", lambda *_args: self._show_locale_entries())
        ttk.Button(table_tools, text="Add Key", command=self.add_locale_key).pack(side="left")
        ttk.Button(table_tools, text="Delete Key", command=self.delete_locale_key).pack(side="left", padx=(6, 0))
        ttk.Button(
            table_tools,
            text="Add Missing Keys",
            command=self.add_missing_locale_entries,
        ).pack(side="left", padx=(6, 0))

        translation_frame = ttk.Frame(table_frame)
        translation_frame.pack(fill="both", expand=True)
        vertical = ttk.Scrollbar(translation_frame)
        vertical.pack(side="right", fill="y")
        horizontal = ttk.Scrollbar(translation_frame, orient="horizontal")
        horizontal.pack(side="bottom", fill="x")
        self.locale_translation_tree = ttk.Treeview(
            translation_frame, columns=("key", "value"), show="headings", selectmode="browse",
            yscrollcommand=vertical.set, xscrollcommand=horizontal.set,
        )
        self.locale_translation_tree.heading("key", text="Translation key")
        self.locale_translation_tree.heading("value", text="Translation")
        self.locale_translation_tree.column("key", width=300, stretch=False)
        self.locale_translation_tree.column("value", width=700, stretch=True)
        self.locale_translation_tree.pack(fill="both", expand=True)
        vertical.configure(command=self.locale_translation_tree.yview)
        horizontal.configure(command=self.locale_translation_tree.xview)
        self.locale_translation_tree.bind("<Double-1>", self._begin_locale_cell_edit)

    def _build_console_tab(self) -> None:
        actions = ttk.Frame(self.console_tab)
        actions.pack(fill="x", pady=(0, 8))
        self.console_restart_button = ttk.Button(
            actions, text="Rebuild & Restart", command=self.restart_server,
        )
        self.console_restart_button.pack(side="left")
        self.console_stop_button = ttk.Button(
            actions, text="Stop Server", command=self.stop_server, state="disabled",
        )
        self.console_stop_button.pack(side="left", padx=(8, 0))

        console_frame = ttk.Frame(self.console_tab)
        console_frame.pack(fill="both", expand=True)
        scrollbar = ttk.Scrollbar(console_frame)
        scrollbar.pack(side="right", fill="y")
        self.console = tk.Text(
            console_frame, state="disabled", bg="#111827", fg="#e5e7eb",
            insertbackground="white", font=("Consolas", 10), wrap="word", yscrollcommand=scrollbar.set,
        )
        self.console.pack(fill="both", expand=True)
        scrollbar.configure(command=self.console.yview)
        command_frame = ttk.Frame(self.console_tab)
        command_frame.pack(fill="x", pady=(8, 0))
        ttk.Label(command_frame, text="Server command").pack(side="left")
        self.console_command = ttk.Entry(command_frame)
        self.console_command.pack(side="left", fill="x", expand=True, padx=8)
        self.console_command.bind("<Return>", lambda _event: self.send_server_command())
        ttk.Button(command_frame, text="Send", command=self.send_server_command).pack(side="left")

    def _build_menu_maker_tab(self) -> None:
        self.menu_maker = MenuMakerPane(self.menu_maker_tab, self.base_dir, self.status_var, self.locale_dir)
        self.menu_maker.pack(fill="both", expand=True)

    def _bind_variables(self) -> None:
        for variable in (
            self.java_var, self.xms_var, self.xmx_var, self.jvm_args_var, self.server_args_var,
            self.debug_enabled_var, self.debug_port_var, self.debug_suspend_var,
        ):
            variable.trace_add("write", lambda *_args: self._update_command_preview())
        self.profile_var.trace_add("write", lambda *_args: self._profile_changed())
        self.eula_var.trace_add("write", lambda *_args: self._eula_changed())
        self.online_mode_var.trace_add("write", lambda *_args: self._online_mode_changed())
        self.nether_var.trace_add("write", lambda *_args: self._dimensions_changed())
        self.end_var.trace_add("write", lambda *_args: self._dimensions_changed())

    def _profile_changed(self) -> None:
        self.settings.active_profile = self.profile_var.get()
        self._update_profile_ui()
        self.load_all_configs(prompt=False)
        self._refresh_project_statuses()
        self._save_settings()

    def _eula_changed(self) -> None:
        self.settings.eula_accepted[self.profile.profile_id] = self.eula_var.get()
        self._save_settings()

    def _online_mode_changed(self) -> None:
        self.settings.online_mode[self.profile.profile_id] = self.online_mode_var.get()
        self._update_launch_summary()
        self._save_settings()

    def _dimensions_changed(self) -> None:
        self.settings.dimensions[self.profile.profile_id] = {
            "nether": self.nether_var.get(),
            "end": self.end_var.get(),
        }
        self._update_launch_summary()
        self._save_settings()

    def _update_profile_ui(self) -> None:
        profile = self.profile
        self.eula_var.set(bool(self.settings.eula_accepted.get(profile.profile_id, False)))
        self.online_mode_var.set(self.settings.online_mode.get(profile.profile_id, True))
        dimensions = self.settings.dimensions.get(profile.profile_id, {})
        self.nether_var.set(dimensions.get("nether", True))
        self.end_var.set(dimensions.get("end", True))
        source_enabled = not self.busy and not self.server.running
        mohist_state = "normal" if profile.kind == "mohist" and source_enabled else "disabled"
        spigot_state = "normal" if profile.kind == "spigot" and not self.busy and not self.server.running else "disabled"
        self.mohist_entry.configure(state=mohist_state)
        self.mohist_browse.configure(state=mohist_state)
        spigot_source_state = "normal" if profile.kind == "spigot" and source_enabled else "disabled"
        self.spigot_entry.configure(state=spigot_source_state)
        self.spigot_browse.configure(state=spigot_source_state)
        self.spigot_button.configure(state=spigot_state)
        self._update_debug_controls()
        authentication_state = "normal" if source_enabled else "disabled"
        self.online_mode_button.configure(state=authentication_state)
        self.offline_mode_button.configure(state=authentication_state)
        self.nether_check.configure(state=authentication_state)
        self.end_check.configure(state=authentication_state)
        if profile.kind == "mohist":
            self.additional_mods_frame.pack(fill="x", pady=(10, 0))
        else:
            self.additional_mods_frame.pack_forget()
        self._render_additional_mods()
        state = "ready" if profile.server_jar.is_file() else "setup required"
        self.profile_status.configure(text=f"Runtime: {profile.runtime_dir}  •  Server jar: {state}")
        self._update_command_preview()
        self._update_launch_summary()

    def _update_launch_summary(self) -> None:
        dimensions = ["Overworld (0)"]
        if self.nether_var.get():
            dimensions.append("Nether (-1)")
        if self.end_var.get():
            dimensions.append("The End (1)")
        auth = "Online mode" if self.online_mode_var.get() else "Offline mode"
        self.launch_summary.configure(text=f"Launch configuration: {auth}  •  Dimensions: {', '.join(dimensions)}")

    def _update_command_preview(self) -> None:
        try:
            command = build_server_command(
                self.java_var.get(), self.profile, self.xms_var.get(), self.xmx_var.get(),
                self.jvm_args_var.get(), self.server_args_var.get(), self.debug_enabled_var.get(),
                self.debug_port_var.get(), self.debug_suspend_var.get(),
            )
            self.command_var.set(display_command(command))
        except ValueError as error:
            self.command_var.set(f"Invalid settings: {error}")

    def _update_debug_controls(self) -> None:
        controls_available = not self.busy and not self.server.running
        self.debug_check.configure(state="normal" if controls_available else "disabled")
        enabled = self.debug_enabled_var.get() and controls_available
        state = "normal" if enabled else "disabled"
        self.debug_port_entry.configure(state=state)
        self.debug_suspend_check.configure(state=state)

    def _copy_command(self) -> None:
        self.root.clipboard_clear()
        self.root.clipboard_append(self.command_var.get())
        self.status_var.set("Java command copied to clipboard")

    def _browse_mohist(self) -> None:
        path = filedialog.askopenfilename(title="Select Mohist 1.20.1 server.jar", filetypes=[("Java archives", "*.jar")])
        if path:
            self.mohist_source_var.set(path)
            self._save_settings()

    def _browse_spigot(self) -> None:
        path = filedialog.askopenfilename(title="Select Spigot 1.20.1 server jar", filetypes=[("Java archives", "*.jar")])
        if path:
            self.spigot_source_var.set(path)
            self._save_settings()

    def _browse_java(self) -> None:
        path = filedialog.askopenfilename(title="Select Java 17 executable", filetypes=[("Java executable", "java.exe")])
        if path:
            self.java_var.set(path)
            self._save_settings()

    def _browse_plugin_root(self) -> None:
        path = filedialog.askdirectory(title="Select plugin project root", initialdir=self.plugin_root_var.get())
        if path:
            self.plugin_root_var.set(path)
            self.refresh_projects()

    def _add_additional_plugins(self) -> None:
        selected_paths = filedialog.askopenfilenames(
            title="Select additional plugin JARs",
            initialdir=str(Path.home() / "Downloads"),
            filetypes=(("Java archives", "*.jar"), ("All files", "*.*")),
        )
        if not selected_paths:
            return
        existing = {str(Path(path).expanduser().resolve()).casefold() for path in self.settings.additional_plugin_paths}
        added = 0
        for selected_path in selected_paths:
            path = Path(selected_path).expanduser().resolve()
            if path.suffix.casefold() != ".jar":
                messagebox.showerror("Additional plugin", f"{path.name} is not a JAR file.")
                continue
            normalized = str(path)
            if normalized.casefold() in existing:
                continue
            self.settings.additional_plugin_paths.append(normalized)
            existing.add(normalized.casefold())
            added += 1
        self._render_additional_plugins()
        self._save_settings()
        if added:
            self.status_var.set(f"Added {added} additional plugin JAR(s)")

    def _remove_selected_additional_plugin(self) -> None:
        selected = self.additional_plugin_tree.selection()
        if not selected:
            self.status_var.set("Select an additional plugin JAR first")
            return
        path = self.additional_plugin_rows.get(selected[0])
        if path is None:
            return
        self.settings.additional_plugin_paths.remove(path)
        self._render_additional_plugins()
        self._save_settings()
        self.status_var.set(f"Removed {Path(path).name} from additional plugins")

    def _render_additional_plugins(self) -> None:
        if not hasattr(self, "additional_plugin_tree"):
            return
        self.additional_plugin_tree.delete(*self.additional_plugin_tree.get_children())
        self.additional_plugin_rows.clear()
        for index, configured_path in enumerate(self.settings.additional_plugin_paths):
            path = Path(configured_path)
            status = "Ready" if path.is_file() and path.suffix.casefold() == ".jar" else "Missing"
            item = f"additional-{index}"
            self.additional_plugin_tree.insert("", "end", iid=item, values=(path.name, str(path), status))
            self.additional_plugin_rows[item] = configured_path

    def _additional_mod_paths(self) -> list[str]:
        return self.settings.additional_mod_paths.setdefault(self.profile.profile_id, [])

    def _add_additional_mods(self) -> None:
        if self.profile.kind != "mohist":
            return
        selected_paths = filedialog.askopenfilenames(
            title="Select additional Mohist mod JARs",
            initialdir=str(Path.home() / "Downloads"),
            filetypes=(("Java archives", "*.jar"), ("All files", "*.*")),
        )
        if not selected_paths:
            return
        paths = self._additional_mod_paths()
        existing = {str(Path(path).expanduser().resolve()).casefold() for path in paths}
        added = 0
        for selected_path in selected_paths:
            path = Path(selected_path).expanduser().resolve()
            if path.suffix.casefold() != ".jar":
                messagebox.showerror("Additional Mohist mod", f"{path.name} is not a JAR file.")
                continue
            normalized = str(path)
            if normalized.casefold() in existing:
                continue
            paths.append(normalized)
            existing.add(normalized.casefold())
            added += 1
        self._render_additional_mods()
        self._save_settings()
        if added:
            self.status_var.set(f"Added {added} Mohist test mod JAR(s)")

    def _remove_selected_additional_mod(self) -> None:
        selected = self.additional_mod_tree.selection()
        if not selected:
            self.status_var.set("Select an additional Mohist mod JAR first")
            return
        path = self.additional_mod_rows.get(selected[0])
        if path is None:
            return
        self._additional_mod_paths().remove(path)
        self._render_additional_mods()
        self._save_settings()
        self.status_var.set(f"Removed {Path(path).name} from Mohist test mods")

    def _render_additional_mods(self) -> None:
        if not hasattr(self, "additional_mod_tree"):
            return
        self.additional_mod_tree.delete(*self.additional_mod_tree.get_children())
        self.additional_mod_rows.clear()
        if self.profile.kind != "mohist":
            return
        for index, configured_path in enumerate(self._additional_mod_paths()):
            path = Path(configured_path)
            status = "Ready" if path.is_file() and path.suffix.casefold() == ".jar" else "Missing"
            item = f"additional-mod-{index}"
            self.additional_mod_tree.insert("", "end", iid=item, values=(path.name, str(path), status))
            self.additional_mod_rows[item] = configured_path

    def refresh_projects(self) -> None:
        if self.busy:
            return
        self._set_busy(True, "Scanning for Bukkit/Spigot plugin projects...")
        root = Path(self.plugin_root_var.get())

        def worker() -> None:
            projects, warnings = discover_projects(root)
            self.events.put(("projects", (projects, warnings)))

        threading.Thread(target=worker, daemon=True).start()

    def _show_projects(self, projects: list[PluginProject], warnings: list[str]) -> None:
        self.projects = projects
        self.plugin_tree.delete(*self.plugin_tree.get_children())
        migrated_states = False
        for project in projects:
            mode = self._plugin_mode(project.project_id)
            if self.settings.plugin_states.get(project.project_id) not in PLUGIN_MODES:
                self.settings.plugin_states[project.project_id] = mode
                migrated_states = True
            self.plugin_tree.insert("", "end", iid=project.project_id, values=(
                PLUGIN_MODE_LABELS[mode], project.name,
                project.build_system.title(), str(project.path), "Checking...",
            ), tags=(mode,))
        if migrated_states:
            self._persist_plugin_states()
        self.project_count.configure(text=f"{len(projects)} project(s)")
        for warning in warnings[:10]:
            self._append_console(f"[scan warning] {warning}")
        self._refresh_project_statuses()
        self._set_busy(False, f"Found {len(projects)} plugin project(s)")

    def _refresh_project_statuses(self) -> None:
        if not self.projects:
            return
        try:
            assessments = assess_projects(
                self.projects,
                load_build_state(self.build_state_path),
                self.profile.plugins_dir,
                self.force_rebuild_ids,
            )
        except OSError as error:
            self._append_console(f"[fingerprint warning] {error}")
            return
        for project_id, assessment in assessments.items():
            if self.plugin_tree.exists(project_id):
                values = list(self.plugin_tree.item(project_id, "values"))
                mode = self._plugin_mode(project_id)
                if mode == "inactive":
                    values[4] = "Kept; build skipped"
                elif mode == "disabled":
                    values[4] = "Will be removed"
                else:
                    values[4] = assessment.status
                self.plugin_tree.item(project_id, values=values)

    def _tree_click(self, event: tk.Event) -> None:
        if self.busy:
            return
        if self.plugin_tree.identify_region(event.x, event.y) != "cell" or self.plugin_tree.identify_column(event.x) != "#1":
            return
        item = self.plugin_tree.identify_row(event.y)
        if not item:
            return
        current = self._plugin_mode(item)
        next_mode = PLUGIN_MODES[(PLUGIN_MODES.index(current) + 1) % len(PLUGIN_MODES)]
        self._set_plugin_mode(item, next_mode)

    def _show_plugin_context_menu(self, event: tk.Event) -> str | None:
        item = self.plugin_tree.identify_row(event.y)
        if not item:
            return None
        project = next((candidate for candidate in self.projects if candidate.project_id == item), None)
        if not project:
            return None
        self.plugin_tree.selection_set(item)
        self.plugin_tree.focus(item)

        menu = tk.Menu(self.root, tearoff=False)
        menu.add_command(label=project.name, state="disabled")
        menu.add_separator()
        mode_menu = tk.Menu(menu, tearoff=False)
        for mode in PLUGIN_MODES:
            mode_menu.add_command(
                label=PLUGIN_MODE_LABELS[mode],
                command=lambda value=mode: self._set_plugin_mode(project.project_id, value),
            )
        menu.add_cascade(label="Plugin mode", menu=mode_menu)
        menu.add_separator()
        menu.add_command(
            label="Force rebuild on next launch",
            state="disabled" if self.busy else "normal",
            command=lambda: self._force_rebuild_projects({project.project_id}),
        )
        menu.add_command(
            label="Clear cached build record",
            state="disabled" if self.busy else "normal",
            command=lambda: self._clear_build_record(project),
        )
        menu.add_separator()
        if project.build_system == "maven":
            operation_state = "disabled" if self.busy else "normal"
            for label, arguments in MAVEN_OPERATIONS:
                menu.add_command(
                    label=label,
                    state=operation_state,
                    command=lambda name=label, args=arguments: self._run_project_maven(project, name, args),
                )
        else:
            menu.add_command(label="Maven operations are only available for Maven projects", state="disabled")
        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()
        return "break"

    def _incremental_setting_changed(self) -> None:
        self.settings.incremental_builds = self.incremental_build_var.get()
        self._save_settings()

    def _force_rebuild_enabled(self) -> None:
        project_ids = {project.project_id for project in self._enabled_projects()}
        self._force_rebuild_projects(project_ids)

    def _force_rebuild_projects(self, project_ids: set[str]) -> None:
        if self.busy:
            return
        self.force_rebuild_ids.update(project_ids)
        self._refresh_project_statuses()
        self.status_var.set(f"Marked {len(project_ids)} plugin project(s) for rebuilding")

    def _clear_build_record(self, project: PluginProject) -> None:
        state = load_build_state(self.build_state_path)
        remove_build_record(state, project.project_id)
        save_build_state(self.build_state_path, state)
        self.force_rebuild_ids.discard(project.project_id)
        self._refresh_project_statuses()
        self.status_var.set(f"Cleared cached build record for {project.name}")

    def _run_project_maven(self, project: PluginProject, label: str, arguments: tuple[str, ...]) -> None:
        if self.busy:
            return
        self._start_log()
        self.notebook.select(self.console_tab)
        self._set_busy(True, f"Running Maven {label} for {project.name}...")
        self.events.put(("project_status", (project.project_id, f"Maven: {label}")))

        def worker() -> None:
            try:
                self._queue_output(f"\n=== {project.name}: Maven {label} ===")
                run_maven_operation(project, arguments, self._queue_output)
                self.events.put(("project_status", (project.project_id, f"Maven {label}: done")))
                self.events.put(("operation_done", f"Maven {label} completed for {project.name}"))
            except Exception as error:
                self.events.put(("project_status", (project.project_id, f"Maven {label}: failed")))
                self.events.put(("operation_error", f"Maven {label} failed for {project.name}: {error}"))

        threading.Thread(target=worker, daemon=True).start()

    def _plugin_mode(self, project_id: str) -> str:
        mode = self.settings.plugin_states.get(project_id)
        if mode in PLUGIN_MODES:
            return mode
        return "enabled" if project_id in self.settings.selected_projects else "disabled"

    def _set_plugin_mode(self, project_id: str, mode: str) -> None:
        if self.busy or mode not in PLUGIN_MODES:
            return
        self.settings.plugin_states[project_id] = mode
        if self.plugin_tree.exists(project_id):
            values = list(self.plugin_tree.item(project_id, "values"))
            values[0] = PLUGIN_MODE_LABELS[mode]
            self.plugin_tree.item(project_id, values=values, tags=(mode,))
        self._persist_plugin_states()
        self._refresh_project_statuses()

    def _set_all_plugin_modes(self, mode: str) -> None:
        if self.busy or mode not in PLUGIN_MODES:
            return
        for item in self.plugin_tree.get_children():
            self.settings.plugin_states[item] = mode
            values = list(self.plugin_tree.item(item, "values"))
            values[0] = PLUGIN_MODE_LABELS[mode]
            self.plugin_tree.item(item, values=values, tags=(mode,))
        self._persist_plugin_states()
        self._refresh_project_statuses()

    def _persist_plugin_states(self) -> None:
        self.settings.selected_projects = [
            item for item in self.plugin_tree.get_children()
            if self._plugin_mode(item) != "disabled"
        ]
        self._save_settings()

    def _projects_in_mode(self, mode: str) -> list[PluginProject]:
        return [
            project for project in self.projects
            if self._plugin_mode(project.project_id) == mode
        ]

    def _enabled_projects(self) -> list[PluginProject]:
        return self._projects_in_mode("enabled")

    def _inactive_projects(self) -> list[PluginProject]:
        return self._projects_in_mode("inactive")

    def build_and_test(self) -> None:
        self._begin_build(run_tests=True, launch=False)

    def run_server(self) -> None:
        self._begin_build(run_tests=False, launch=True)

    def restart_server(self) -> None:
        if self.busy:
            return
        if not self.server.running:
            self.run_server()
            return
        self.restart_pending = True
        self._request_server_stop("Stopping server before rebuild...")

    def _begin_build(self, run_tests: bool, launch: bool) -> None:
        if self.busy or self.server.running:
            return
        if not self.eula_var.get() and launch:
            messagebox.showerror("EULA required", "Accept the Minecraft EULA for this server profile before launching.")
            return
        try:
            command = build_server_command(
                self.java_var.get(), self.profile, self.xms_var.get(), self.xmx_var.get(),
                self.jvm_args_var.get(), self.server_args_var.get(), self.debug_enabled_var.get(),
                self.debug_port_var.get(), self.debug_suspend_var.get(),
            )
        except ValueError as error:
            messagebox.showerror("Invalid launch settings", str(error))
            return
        enabled_projects = self._enabled_projects()
        inactive_projects = self._inactive_projects()
        active_projects = [
            project for project in self.projects
            if self._plugin_mode(project.project_id) != "disabled"
        ]
        additional_plugin_paths = list(self.settings.additional_plugin_paths)
        additional_mod_paths = list(self.settings.additional_mod_paths.get(self.profile.profile_id, []))
        self._save_settings()
        profile = self.profile
        incremental_enabled = self.incremental_build_var.get() and not run_tests
        forced_project_ids = set(self.force_rebuild_ids)
        online_mode = self.online_mode_var.get()
        nether_enabled = self.nether_var.get()
        end_enabled = self.end_var.get()
        self._start_log()
        self.notebook.select(self.console_tab)
        self._set_busy(True, "Preparing build...")

        def worker() -> None:
            try:
                if launch:
                    validate_java17(self.java_var.get())
                    if profile.kind == "mohist":
                        self._queue_output("Validating and importing Mohist 1.20.1...")
                        install_mohist(profile, Path(self.mohist_source_var.get()))
                    else:
                        self._queue_output("Validating and importing Spigot 1.20.1...")
                        install_spigot(profile, Path(self.spigot_source_var.get()))
                ordered = order_projects(active_projects, self.projects)
                state = load_build_state(self.build_state_path)
                assessments = assess_projects(
                    self.projects, state, profile.plugins_dir, forced_project_ids
                )
                artifacts: dict[str, Path] = {}
                skipped = 0
                for index, project in enumerate(ordered, 1):
                    if self._plugin_mode(project.project_id) == "inactive":
                        self._queue_output(
                            f"[launcher] Inactive {project.name}: keeping the installed JAR "
                            "and skipping its build."
                        )
                        self.events.put(
                            ("project_status", (project.project_id, "Kept; build skipped"))
                        )
                        continue
                    assessment = assessments[project.project_id]
                    should_build = (
                        not incremental_enabled
                        or assessment.needs_build
                        or project.project_id in forced_project_ids
                    )
                    if not should_build:
                        if assessment.cached_artifact is None:
                            raise RuntimeError(
                                f"No reusable build artifact was found for {project.name}"
                            )
                        artifacts[project.project_id] = assessment.cached_artifact
                        skipped += 1
                        self._queue_output(
                            f"[launcher] Skipping {project.name}: {assessment.reason}"
                        )
                        self.events.put(
                            ("project_status", (project.project_id, assessment.status))
                        )
                        continue
                    self.events.put(("project_status", (project.project_id, f"Building {index}/{len(ordered)}")))
                    self._queue_output(f"\n=== Building {project.name} ===")
                    artifact = build_project(project, run_tests, self._queue_output)
                    artifacts[project.project_id] = artifact
                    record_successful_build(
                        state,
                        project,
                        assessment.source_fingerprint,
                        assessment.dependency_fingerprints,
                        artifact,
                    )
                    save_build_state(self.build_state_path, state)
                    self.events.put(("project_status", (project.project_id, "Built")))
                if incremental_enabled:
                    self._queue_output(
                        f"[launcher] Incremental build: built {len(enabled_projects) - skipped}, "
                        f"reused {skipped}, kept {len(inactive_projects)} inactive plugin(s)."
                    )
                if launch:
                    self._queue_output("\n=== Applying configured plugin modes ===")
                    if profile.kind == "mohist":
                        additional_mod_artifacts = collect_additional_mod_artifacts(additional_mod_paths)
                        if additional_mod_artifacts:
                            self._queue_output(
                                "[launcher] Deploying additional Mohist mods: " + ", ".join(
                                    artifact.name for artifact in additional_mod_artifacts.values()
                                )
                            )
                        mod_deployment = deploy_artifacts(
                            profile.mods_dir,
                            additional_mod_artifacts,
                            desired_project_ids=set(additional_mod_artifacts),
                            adopt_filenames={artifact.name for artifact in additional_mod_artifacts.values()},
                        )
                        if mod_deployment.installed:
                            self._queue_output("[launcher] Installed mods: " + ", ".join(mod_deployment.installed))
                        if mod_deployment.removed:
                            self._queue_output("[launcher] Removed managed mods: " + ", ".join(mod_deployment.removed))
                    additional_artifacts = collect_additional_plugin_artifacts(additional_plugin_paths)
                    artifacts.update(additional_artifacts)
                    if additional_artifacts:
                        self._queue_output(
                            "[launcher] Deploying additional plugins: " + ", ".join(
                                artifact.name for artifact in additional_artifacts.values()
                            )
                        )
                    deployment = deploy_artifacts(
                        profile.plugins_dir,
                        artifacts,
                        desired_project_ids={
                            project.project_id for project in active_projects
                        } | set(additional_artifacts),
                        adopt_filenames={artifact.name for artifact in additional_artifacts.values()},
                    )
                    if deployment.installed:
                        self._queue_output(
                            "[launcher] Installed: " + ", ".join(deployment.installed)
                        )
                    if deployment.reused:
                        self._queue_output(
                            "[launcher] Already current: " + ", ".join(deployment.reused)
                        )
                    if deployment.removed:
                        self._queue_output(
                            "[launcher] Removed deselected managed JARs: "
                            + ", ".join(deployment.removed)
                        )
                    profile.runtime_dir.mkdir(parents=True, exist_ok=True)
                    (profile.runtime_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
                    set_server_property(profile.runtime_dir, "online-mode", str(online_mode).lower())
                    set_server_property(profile.runtime_dir, "allow-nether", str(nether_enabled).lower())
                    set_bukkit_setting(profile.runtime_dir, "settings", "allow-end", str(end_enabled).lower())
                    mode = "online" if online_mode else "offline"
                    self._queue_output(f"[launcher] Server authentication mode: {mode}")
                    loaded_dimensions = ["Overworld (0)"]
                    if nether_enabled:
                        loaded_dimensions.append("Nether (-1)")
                    if end_enabled:
                        loaded_dimensions.append("The End (1)")
                    self._queue_output(f"[launcher] Enabled dimensions: {', '.join(loaded_dimensions)}")
                    self._queue_output(f"> {display_command(command)}")
                    self.server.start(command, profile.runtime_dir)
                    self.events.put(
                        ("build_state_updated", {project.project_id for project in enabled_projects})
                    )
                    self.events.put(("launched", None))
                else:
                    self.events.put(
                        ("build_state_updated", {project.project_id for project in enabled_projects})
                    )
                    self.events.put(("operation_done", "Build and tests completed successfully"))
            except Exception as error:
                self.events.put(("operation_error", str(error)))

        threading.Thread(target=worker, daemon=True).start()

    def import_spigot_now(self) -> None:
        if self.busy or self.server.running:
            return
        profile = self.profiles["spigot-1.20.1"]
        self._start_log()
        self.notebook.select(self.console_tab)
        self._set_busy(True, "Importing Spigot 1.20.1...")

        def worker() -> None:
            try:
                install_spigot(profile, Path(self.spigot_source_var.get()))
                self.events.put(("operation_done", "Spigot 1.20.1 is ready"))
            except Exception as error:
                self.events.put(("operation_error", str(error)))

        threading.Thread(target=worker, daemon=True).start()

    def stop_server(self) -> None:
        self.restart_pending = False
        self._request_server_stop("Stopping server gracefully...")

    def _request_server_stop(self, status: str) -> None:
        if not self.server.running:
            return
        self._set_busy(True, status)
        try:
            self.server.stop()
        except RuntimeError as error:
            self.restart_pending = False
            self._set_busy(False, f"{self.profile.display_name} is running")
            messagebox.showerror("Stop failed", str(error))
            return

        def monitor() -> None:
            deadline = time.monotonic() + 30
            while self.server.running and time.monotonic() < deadline:
                time.sleep(0.25)
            if self.server.running:
                self.events.put(("stop_timeout", None))

        threading.Thread(target=monitor, daemon=True).start()

    def send_server_command(self) -> None:
        value = self.console_command.get().strip()
        if not value:
            return
        try:
            self.server.send(value)
            self._append_console(f"> {value}")
            self.console_command.delete(0, "end")
        except RuntimeError as error:
            messagebox.showerror("Command not sent", str(error))

    def reload_locale_files(self, prompt: bool = True) -> None:
        if prompt and self.locale_dirty and not messagebox.askyesno(
            "Discard locale changes?",
            "Reload locale files and discard unsaved changes in: " + ", ".join(sorted(self.locale_dirty)) + "?",
        ):
            return
        self._finish_locale_cell_edit(commit=True)
        selected = self._selected_locale_name()
        self.locale_files = {path.name: path for path in list_locale_files(self.locale_dir)}
        self.locale_documents.clear()
        self.locale_dirty.clear()
        self.locale_file_tree.delete(*self.locale_file_tree.get_children())
        self.locale_translation_tree.delete(*self.locale_translation_tree.get_children())
        self.locale_row_keys.clear()
        for filename, path in self.locale_files.items():
            kind = "Mojang reference" if path.stem.endswith("_mojang") else "Server locale"
            size = f"{path.stat().st_size / 1024:.0f} KiB"
            self.locale_file_tree.insert("", "end", iid=filename, values=(filename, kind, size))
        if not self.locale_files:
            self.locale_detail_var.set(f"No JSON locale files found in {self.locale_dir}")
            self.status_var.set("No locale files found")
            return
        target = selected if selected in self.locale_files else next(iter(self.locale_files))
        self.locale_file_tree.selection_set(target)
        self.locale_file_tree.focus(target)
        self.locale_file_tree.see(target)
        self._load_selected_locale()
        self.status_var.set(f"Loaded {len(self.locale_files)} locale file(s)")

    def _selected_locale_name(self) -> str | None:
        selected = self.locale_file_tree.selection()
        return selected[0] if selected else None

    def _locale_file_selected(self, _event: tk.Event) -> None:
        self._finish_locale_cell_edit(commit=True)
        self._load_selected_locale()

    def _load_selected_locale(self) -> None:
        filename = self._selected_locale_name()
        if not filename:
            return
        if filename not in self.locale_documents:
            try:
                self.locale_documents[filename] = load_locale_file(self.locale_files[filename])
            except (OSError, ValueError) as error:
                self.locale_detail_var.set(f"Could not load {filename}")
                self.status_var.set(str(error))
                messagebox.showerror("Locale file", str(error))
                return
        self._show_locale_entries()

    def _show_locale_entries(self) -> None:
        if not hasattr(self, "locale_translation_tree"):
            return
        filename = self._selected_locale_name()
        self.locale_translation_tree.delete(*self.locale_translation_tree.get_children())
        self.locale_row_keys.clear()
        if not filename or filename not in self.locale_documents:
            return
        values = self.locale_documents[filename]
        filter_text = self.locale_filter_var.get().casefold().strip()
        for index, key in enumerate(sorted(values, key=str.casefold)):
            value = values[key]
            if filter_text and filter_text not in key.casefold() and filter_text not in value.casefold():
                continue
            item = f"entry-{index}"
            self.locale_translation_tree.insert("", "end", iid=item, values=(key, value))
            self.locale_row_keys[item] = key
        marker = " • unsaved" if filename in self.locale_dirty else ""
        self.locale_detail_var.set(f"{filename}: {len(values)} translation key(s){marker}")

    def _begin_locale_cell_edit(self, event: tk.Event) -> str | None:
        if self.locale_translation_tree.identify_region(event.x, event.y) != "cell":
            return None
        if self.locale_translation_tree.identify_column(event.x) != "#2":
            return None
        item = self.locale_translation_tree.identify_row(event.y)
        filename = self._selected_locale_name()
        if not item or not filename or filename not in self.locale_documents:
            return None
        self._finish_locale_cell_edit(commit=True)
        key = self.locale_row_keys[item]
        bbox = self.locale_translation_tree.bbox(item, "#2")
        if not bbox:
            return None
        x, y, width, height = bbox
        entry = ttk.Entry(self.locale_translation_tree)
        entry.insert(0, self.locale_documents[filename][key])
        entry.selection_range(0, "end")
        entry.place(x=x, y=y, width=width, height=height)
        entry.focus_set()
        entry.bind("<Return>", lambda _event: self._finish_locale_cell_edit(commit=True) or "break")
        entry.bind("<Escape>", lambda _event: self._finish_locale_cell_edit(commit=False) or "break")
        entry.bind("<FocusOut>", lambda _event: self._finish_locale_cell_edit(commit=True))
        self.locale_edit_entry = entry
        self.locale_edit_context = (filename, item, key)
        return "break"

    def _finish_locale_cell_edit(self, commit: bool) -> None:
        entry = self.locale_edit_entry
        context = self.locale_edit_context
        if not entry or not context:
            return
        self.locale_edit_entry = None
        self.locale_edit_context = None
        filename, item, key = context
        value = entry.get()
        entry.destroy()
        if not commit or filename not in self.locale_documents:
            return
        if value != self.locale_documents[filename][key]:
            self.locale_documents[filename][key] = value
            self.locale_dirty.add(filename)
            if self.locale_translation_tree.exists(item):
                self.locale_translation_tree.item(item, values=(key, value))
            self._show_locale_entries()

    def save_selected_locale(self) -> None:
        self._finish_locale_cell_edit(commit=True)
        filename = self._selected_locale_name()
        if not filename:
            self.status_var.set("Select a locale file first")
            return
        if filename not in self.locale_documents:
            self._load_selected_locale()
        if filename not in self.locale_documents:
            return
        try:
            save_locale_file(self.locale_files[filename], self.locale_documents[filename])
        except (OSError, ValueError) as error:
            messagebox.showerror("Could not save locale", str(error))
            return
        self.locale_dirty.discard(filename)
        self._show_locale_entries()
        self.status_var.set(f"Saved {filename}")

    def save_all_locales(self) -> None:
        self._finish_locale_cell_edit(commit=True)
        for filename in sorted(self.locale_dirty):
            try:
                save_locale_file(self.locale_files[filename], self.locale_documents[filename])
            except (KeyError, OSError, ValueError) as error:
                messagebox.showerror("Could not save locale", str(error))
                return
        count = len(self.locale_dirty)
        self.locale_dirty.clear()
        self._show_locale_entries()
        self.status_var.set("No locale changes to save" if not count else f"Saved {count} locale file(s)")

    def add_locale_key(self) -> None:
        filename = self._selected_locale_name()
        if not filename:
            self.status_var.set("Select a locale file first")
            return
        if filename not in self.locale_documents:
            self._load_selected_locale()
        if filename not in self.locale_documents:
            return
        key = simpledialog.askstring("Add translation key", "Translation key:", parent=self.root)
        if key is None:
            return
        key = key.strip()
        if not key:
            messagebox.showerror("Invalid key", "A translation key cannot be blank.")
            return
        if key in self.locale_documents[filename]:
            messagebox.showerror("Duplicate key", f"{key} already exists in {filename}.")
            return
        self.locale_documents[filename][key] = ""
        self.locale_dirty.add(filename)
        self._show_locale_entries()
        self.status_var.set(f"Added {key} to {filename}")

    def add_missing_locale_entries(self) -> None:
        self._finish_locale_cell_edit(commit=True)
        filename = self._selected_locale_name()
        if not filename:
            self.status_var.set("Select a language file first")
            return
        if Path(filename).stem.casefold().endswith("_mojang"):
            self.status_var.set("Add missing keys is only available for editable server locales")
            return
        if filename not in self.locale_documents:
            self._load_selected_locale()
        if filename not in self.locale_documents:
            return

        for candidate, path in self.locale_files.items():
            if Path(candidate).stem.casefold().endswith("_mojang") or candidate in self.locale_documents:
                continue
            try:
                self.locale_documents[candidate] = load_locale_file(path)
            except (OSError, ValueError) as error:
                messagebox.showerror("Locale file", str(error))
                self.status_var.set(f"Could not load {candidate}")
                return

        template_name = most_complete_editable_locale(self.locale_documents)
        if template_name is None:
            self.status_var.set("No editable locale files are available as a template")
            return
        additions = missing_locale_entries(
            self.locale_documents[template_name], self.locale_documents[filename]
        )
        if not additions:
            self.status_var.set(f"{filename} already includes every key from {template_name}")
            return
        self.locale_documents[filename].update(additions)
        self.locale_dirty.add(filename)
        self._show_locale_entries()
        self.status_var.set(f"Added {len(additions)} missing key(s) to {filename} from {template_name}")

    def delete_locale_key(self) -> None:
        filename = self._selected_locale_name()
        selected = self.locale_translation_tree.selection()
        if not filename or not selected:
            self.status_var.set("Select a translation key first")
            return
        key = self.locale_row_keys.get(selected[0])
        if not key or not messagebox.askyesno("Delete translation key?", f"Delete {key} from {filename}?"):
            return
        del self.locale_documents[filename][key]
        self.locale_dirty.add(filename)
        self._show_locale_entries()
        self.status_var.set(f"Deleted {key} from {filename}")

    def _config_path(self, filename: str) -> Path:
        return self.profile.runtime_dir / filename

    def _config_modified(self, filename: str, _event: tk.Event) -> None:
        editor = self.config_editors[filename]
        if editor.edit_modified():
            self.config_dirty[filename] = True
            editor.edit_modified(False)

    def load_all_configs(self, prompt: bool = True) -> None:
        dirty = [filename for filename, changed in self.config_dirty.items() if changed]
        if prompt and dirty and not messagebox.askyesno(
            "Discard changes?", "Reload and discard unsaved changes in: " + ", ".join(dirty) + "?"
        ):
            return
        for filename in CONFIG_FILES:
            self.load_config(filename, prompt=False)

    def load_config(self, filename: str, prompt: bool = True) -> None:
        if prompt and self.config_dirty[filename] and not messagebox.askyesno(
            "Discard changes?", f"Reload {filename} and discard its unsaved changes?"
        ):
            return
        path = self._config_path(filename)
        editor = self.config_editors[filename]
        self.config_locations[filename].configure(text=str(path))
        editor.configure(state="normal")
        editor.delete("1.0", "end")
        if path.is_file():
            editor.insert("1.0", path.read_text(encoding="utf-8", errors="replace"))
            self.config_save_buttons[filename].configure(state="normal")
        else:
            editor.insert("1.0", "# This file will be generated after the server starts for the first time.\n")
            editor.configure(state="disabled")
            self.config_save_buttons[filename].configure(state="disabled")
        editor.edit_modified(False)
        self.config_dirty[filename] = False

    def save_config(self, filename: str) -> None:
        path = self._config_path(filename)
        path.parent.mkdir(parents=True, exist_ok=True)
        content = self.config_editors[filename].get("1.0", "end-1c")
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(content.rstrip("\n") + "\n", encoding="utf-8")
        temporary.replace(path)
        self.config_dirty[filename] = False
        self.status_var.set(f"Saved {path.name}")

    def _start_log(self) -> None:
        log_dir = self.data_dir / "launcher-logs"
        log_dir.mkdir(parents=True, exist_ok=True)
        self.log_path = log_dir / time.strftime("launcher-%Y%m%d-%H%M%S.log")

    def _queue_output(self, line: str) -> None:
        self.events.put(("output", line))

    def _append_console(self, line: str) -> None:
        self.console.configure(state="normal")
        self.console.insert("end", line + "\n")
        self.console.see("end")
        self.console.configure(state="disabled")
        if self.log_path:
            try:
                with self.log_path.open("a", encoding="utf-8") as log:
                    log.write(line + "\n")
            except OSError:
                pass

    def _drain_events(self) -> None:
        try:
            while True:
                event, payload = self.events.get_nowait()
                if event == "output":
                    self._append_console(str(payload))
                elif event == "projects":
                    projects, warnings = payload
                    self._show_projects(projects, warnings)
                elif event == "project_status":
                    project_id, status = payload
                    if self.plugin_tree.exists(project_id):
                        values = list(self.plugin_tree.item(project_id, "values"))
                        values[4] = status
                        self.plugin_tree.item(project_id, values=values)
                elif event == "build_state_updated":
                    self.force_rebuild_ids.difference_update(payload)
                    self._refresh_project_statuses()
                elif event == "launched":
                    self._set_busy(False, f"{self.profile.display_name} is running")
                elif event == "server_exit":
                    restart = self.restart_pending
                    self.restart_pending = False
                    self._append_console(f"[launcher] Server exited with code {payload}")
                    if restart:
                        self._set_busy(False, "Server stopped; rebuilding selected plugins...")
                        self.root.after_idle(self.run_server)
                    else:
                        self._set_busy(False, f"Server stopped (exit code {payload})")
                elif event == "operation_done":
                    self._append_console(f"[launcher] {payload}")
                    self._set_busy(False, str(payload))
                    self._update_profile_ui()
                elif event == "operation_error":
                    self._append_console(f"[error] {payload}")
                    self._set_busy(False, "Operation failed")
                    messagebox.showerror("Operation failed", str(payload))
                elif event == "stop_timeout":
                    if messagebox.askyesno("Server did not stop", "The server did not stop within 30 seconds. Force terminate it?"):
                        self.server.terminate()
                    else:
                        self.restart_pending = False
                        self._set_busy(False, f"{self.profile.display_name} is still running")
        except queue.Empty:
            pass
        self.root.after(80, self._drain_events)

    def _set_busy(self, busy: bool, status: str) -> None:
        self.busy = busy
        self.status_var.set(status)
        running = self.server.running
        idle = not busy
        stopped_state = "normal" if idle and not running else "disabled"
        running_state = "normal" if idle and running else "disabled"
        self.run_button.configure(state=stopped_state)
        self.test_button.configure(state=stopped_state)
        self.stop_button.configure(state=running_state)
        self.console_restart_button.configure(state="normal" if idle else "disabled")
        self.console_stop_button.configure(state=running_state)
        self.refresh_button.configure(state="disabled" if busy else "normal")
        profile_state = "disabled" if busy or self.server.running else "normal"
        for button in self.profile_buttons:
            button.configure(state=profile_state)
        self._update_profile_ui()

    def _save_settings(self) -> None:
        self.settings.active_profile = self.profile_var.get()
        self.settings.mohist_source = self.mohist_source_var.get()
        self.settings.spigot_source = self.spigot_source_var.get()
        self.settings.java_path = self.java_var.get()
        self.settings.initial_memory_gb = self.xms_var.get()
        self.settings.maximum_memory_gb = self.xmx_var.get()
        self.settings.extra_jvm_args = self.jvm_args_var.get()
        self.settings.server_args = self.server_args_var.get()
        self.settings.debug_enabled = self.debug_enabled_var.get()
        self.settings.debug_port = self.debug_port_var.get()
        self.settings.debug_suspend = self.debug_suspend_var.get()
        self.settings.plugin_root = self.plugin_root_var.get()
        self.settings.incremental_builds = self.incremental_build_var.get()
        try:
            self.store.save(self.settings)
        except OSError as error:
            self.status_var.set(f"Could not save settings: {error}")

    def _on_close(self) -> None:
        dirty = [filename for filename, changed in self.config_dirty.items() if changed]
        locale_dirty = sorted(self.locale_dirty)
        unsaved = []
        if dirty:
            unsaved.append("configuration changes in: " + ", ".join(dirty))
        if locale_dirty:
            unsaved.append("locale changes in: " + ", ".join(locale_dirty))
        if unsaved and not messagebox.askyesno(
            "Unsaved configuration changes",
            "Close and discard " + "; ".join(unsaved) + "?",
        ):
            return
        if self.server.running:
            if not messagebox.askyesno("Server is running", "Stop the server and close the launcher?"):
                return
            try:
                self.server.stop()
            except RuntimeError:
                pass
            if self.server.process:
                try:
                    self.server.process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    if not messagebox.askyesno("Force close?", "The server is still running. Force terminate it?"):
                        return
                    self.server.terminate()
        self._save_settings()
        self.root.destroy()


def run_app(base_dir: Path) -> None:
    root = tk.Tk()
    LauncherApp(root, base_dir)
    root.mainloop()
