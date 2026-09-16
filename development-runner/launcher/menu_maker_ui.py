from __future__ import annotations

import json
import copy
import tkinter as tk
import tkinter.font as tkfont
from pathlib import Path
from tkinter import messagebox, simpledialog, ttk

from .java_editor import JavaCodeEditor

from .menu_maker import (
    MenuCell,
    MenuDesign,
    MenuItemDesign,
    MenuLore,
    MenuText,
    MinecraftAssets,
    generate_java,
    apply_callback_bodies,
    allowed_roles,
    callback_structure,
    extract_callback_bodies,
    render_menu_item,
    slot_traversal,
)
from .menu_presets import ItemPreset, MenuPreset, PresetStore


SCALE = 3
CHEST_LEFT = 7
CHEST_TOP = 17
MINECRAFT_COLORS = {
    "0": "#000000", "1": "#0000AA", "2": "#00AA00", "3": "#00AAAA",
    "4": "#AA0000", "5": "#AA00AA", "6": "#FFAA00", "7": "#AAAAAA",
    "8": "#555555", "9": "#5555FF", "a": "#55FF55", "b": "#55FFFF",
    "c": "#FF5555", "d": "#FF55FF", "e": "#FFFF55", "f": "#FFFFFF",
}
TEMPLATE_LABELS = {
    "single": "single",
    "paged_content": "paged (content)",
    "paged_storage": "paged (storage)",
}
TEMPLATE_TYPES = {label: value for value, label in TEMPLATE_LABELS.items()}


class MenuMakerPane(ttk.Frame):
    """Interactive chest-layout editor and Java generator."""

    def __init__(self, parent: tk.Misc, base_dir: Path, status_var: tk.StringVar, locale_dir: Path):
        super().__init__(parent, padding=10)
        self.base_dir = base_dir
        self.status_var = status_var
        self.locale_dir = locale_dir
        self.assets = MinecraftAssets(base_dir / "assets" / "minecraft")
        self.presets = PresetStore(base_dir / "assets" / "menu-presets", base_dir / "data" / "menu-presets")
        self.design = MenuDesign()
        self.selected_slot = 0
        self.current_page = 0
        self._row_before_change = self.design.rows
        self._background: tk.PhotoImage | None = None
        self._source_background: tk.PhotoImage | None = None
        self._icon_cache: dict[str, tk.PhotoImage | None] = {}
        self._canvas_images: list[tk.PhotoImage] = []
        self._item_clipboard: MenuItemDesign | None = None
        self._hover_slot: int | None = None
        self._translations: dict[str, dict[str, str]] = {}
        self._active_menu: MenuPreset | None = None
        self._last_generated_source = ""
        self._code_state = "Generated"
        self._dirty = False
        self._load_preview_translations()
        self._create_variables()
        self._tooltip_font = tkfont.Font(family="Consolas", size=10)
        self._tooltip_title_font = tkfont.Font(family="Consolas", size=10, weight="bold")
        self._build()
        self._configure_role_values()
        self._bind_events()
        self._load_cell()
        self._redraw()
        self._update_code()
        self._dirty = False
        self._update_preset_status()

    def _create_variables(self) -> None:
        self.template_var = tk.StringVar(value=TEMPLATE_LABELS["single"])
        self.title_mode_var = tk.StringVar(value="literal")
        self.title_var = tk.StringVar(value="My Menu")
        self.rows_var = tk.IntVar(value=3)
        self.page_var = tk.StringVar(value="Base layout")
        self.slot_label_var = tk.StringVar(value="Slot 0 (row 1, column 1)")
        self.role_var = tk.StringVar(value="empty")
        self.material_var = tk.StringVar(value="STONE")
        self.amount_var = tk.IntVar(value=1)
        self.name_mode_var = tk.StringVar(value="literal")
        self.name_var = tk.StringVar(value="Item")
        self.wrap_var = tk.StringVar(value="none")
        self.wrap_width_var = tk.IntVar(value=30)
        self.clickable_var = tk.BooleanVar(value=False)
        self.glimmer_var = tk.BooleanVar(value=False)
        self.storage_index_var = tk.IntVar(value=0)
        self.read_only_var = tk.BooleanVar(value=False)
        self.content_order_var = tk.IntVar(value=0)
        self.validation_var = tk.StringVar()
        self.preview_language_var = tk.StringVar(value="en_us")
        self.menu_preset_var = tk.StringVar()
        self.item_preset_var = tk.StringVar()
        self.code_state_var = tk.StringVar(value="Generated")
        self.preset_status_var = tk.StringVar()

    def _build(self) -> None:
        toolbar = ttk.Frame(self)
        toolbar.pack(fill="x", pady=(0, 8))
        ttk.Label(toolbar, text="Template").pack(side="left")
        ttk.Combobox(toolbar, textvariable=self.template_var, values=tuple(TEMPLATE_TYPES), width=17, state="readonly").pack(side="left", padx=(5, 12))
        ttk.Label(toolbar, text="Rows").pack(side="left")
        ttk.Spinbox(toolbar, from_=1, to=6, textvariable=self.rows_var, width=4, command=self._rows_changed).pack(side="left", padx=(5, 12))
        ttk.Label(toolbar, text="Title").pack(side="left")
        ttk.Combobox(toolbar, textvariable=self.title_mode_var, values=("literal", "localized"), width=10, state="readonly").pack(side="left", padx=(5, 5))
        self.title_entry = ttk.Combobox(toolbar, textvariable=self.title_var, values=self._locale_keys(), width=32)
        self.title_entry.pack(side="left", fill="x", expand=True)
        ttk.Label(toolbar, text="View").pack(side="left", padx=(12, 5))
        self.page_combo = ttk.Combobox(toolbar, textvariable=self.page_var, values=("Base layout",), width=14, state="readonly")
        self.page_combo.pack(side="left")
        ttk.Button(toolbar, text="Add Page", command=self._add_page).pack(side="left", padx=(5, 0))
        ttk.Label(toolbar, text="Tooltip").pack(side="left", padx=(12, 5))
        ttk.Combobox(
            toolbar, textvariable=self.preview_language_var,
            values=tuple(self._translations) or ("en_us",), width=8, state="readonly",
        ).pack(side="left")
        ttk.Button(toolbar, text="Reset", command=self._reset).pack(side="right", padx=(5, 0))

        preset_bar = ttk.Frame(self)
        preset_bar.pack(fill="x", pady=(0, 8))
        ttk.Label(preset_bar, text="Menu preset").pack(side="left")
        self.menu_preset_combo = ttk.Combobox(preset_bar, textvariable=self.menu_preset_var, state="readonly", width=30)
        self.menu_preset_combo.pack(side="left", padx=5)
        ttk.Button(preset_bar, text="Load", command=self._load_menu_preset).pack(side="left")
        ttk.Button(preset_bar, text="Save", command=self._save_menu_preset).pack(side="left", padx=(4, 0))
        ttk.Button(preset_bar, text="Save As", command=lambda: self._save_menu_preset(True)).pack(side="left", padx=(4, 0))
        ttk.Button(preset_bar, text="Delete", command=self._delete_menu_preset).pack(side="left", padx=(4, 10))
        ttk.Button(preset_bar, text="Empty → Storage", command=lambda: self._bulk_role_dialog("storage")).pack(side="right", padx=(4, 0))
        ttk.Button(preset_bar, text="Empty → Content", command=lambda: self._bulk_role_dialog("content")).pack(side="right", padx=(4, 0))
        ttk.Button(preset_bar, text="Fill All with Item", command=self._fill_item_dialog).pack(side="right", padx=(4, 8))
        ttk.Label(preset_bar, textvariable=self.preset_status_var).pack(side="left")

        panes = ttk.Panedwindow(self, orient="horizontal")
        panes.pack(fill="both", expand=True)
        left = ttk.Frame(panes)
        right = ttk.Labelframe(panes, text="Generated Java", padding=8)
        panes.add(left, weight=3)
        panes.add(right, weight=2)

        editor_panes = ttk.Panedwindow(left, orient="vertical")
        editor_panes.pack(fill="both", expand=True)
        preview = ttk.Labelframe(
            editor_panes,
            text="Minecraft chest preview — arrows navigate · Alt+S storage · Alt+C content",
            padding=6,
        )
        fields = ttk.Labelframe(editor_panes, text="Selected slot", padding=8)
        editor_panes.add(preview, weight=3)
        editor_panes.add(fields, weight=2)

        self.canvas = tk.Canvas(preview, background="#202020", highlightthickness=0, cursor="hand2")
        self.canvas.pack(fill="both", expand=True)
        self.canvas.bind("<Button-1>", self._canvas_click)
        self.canvas.bind("<Motion>", self._canvas_motion)
        self.canvas.bind("<Leave>", self._canvas_leave)
        self.canvas.bind("<Control-c>", self._copy_item_hotkey)
        self.canvas.bind("<Control-v>", self._paste_item_hotkey)
        self.canvas.bind("<Delete>", self._clear_item_hotkey)
        self.canvas.bind("<Alt-s>", lambda event: self._quick_role(event, "storage"))
        self.canvas.bind("<Alt-c>", lambda event: self._quick_role(event, "content"))
        self.canvas.bind("<Left>", lambda event: self._move_selection(event, 0, -1))
        self.canvas.bind("<Right>", lambda event: self._move_selection(event, 0, 1))
        self.canvas.bind("<Up>", lambda event: self._move_selection(event, -1, 0))
        self.canvas.bind("<Down>", lambda event: self._move_selection(event, 1, 0))
        self.canvas.bind("<Configure>", lambda _event: self._redraw())

        ttk.Label(fields, textvariable=self.slot_label_var, style="Heading.TLabel").grid(row=0, column=0, columnspan=6, sticky="w", pady=(0, 5))
        ttk.Label(fields, text="Role").grid(row=1, column=0, sticky="w")
        self.role_combo = ttk.Combobox(
            fields, textvariable=self.role_var,
            values=("empty", "static", "storage", "content", "previous", "next"),
            width=11, state="readonly",
        )
        self.role_combo.grid(row=1, column=1, sticky="w", padx=(5, 14))
        ttk.Label(fields, text="Material").grid(row=1, column=2, sticky="w")
        self.material_combo = ttk.Combobox(fields, textvariable=self.material_var, values=self.assets.materials(), width=23)
        self.material_combo.grid(row=1, column=3, sticky="ew", padx=5)
        ttk.Label(fields, text="Amount").grid(row=1, column=4, sticky="w")
        self.amount_spin = ttk.Spinbox(fields, from_=1, to=64, textvariable=self.amount_var, width=5)
        self.amount_spin.grid(row=1, column=5, sticky="w", padx=5)

        ttk.Label(fields, text="Name").grid(row=2, column=0, sticky="w", pady=(6, 0))
        self.name_mode_combo = ttk.Combobox(fields, textvariable=self.name_mode_var, values=("literal", "localized", "hidden"), width=10, state="readonly")
        self.name_mode_combo.grid(row=2, column=1, sticky="w", padx=(5, 14), pady=(6, 0))
        self.name_entry = ttk.Combobox(fields, textvariable=self.name_var, values=self._locale_keys())
        self.name_entry.grid(row=2, column=2, columnspan=4, sticky="ew", padx=5, pady=(6, 0))

        ttk.Label(fields, text="Lore").grid(row=3, column=0, sticky="nw", pady=(6, 0))
        lore_frame = ttk.Frame(fields)
        lore_frame.grid(row=3, column=1, columnspan=5, sticky="nsew", padx=5, pady=(6, 0))
        self.lore_text = tk.Text(lore_frame, height=4, undo=True, font=("Consolas", 9), wrap="none")
        self.lore_text.pack(fill="both", expand=True)
        ttk.Label(lore_frame, text="One entry per line. Prefix a locale key with @; plain lines are literal.").pack(anchor="w")

        ttk.Label(fields, text="Wrap").grid(row=4, column=0, sticky="w", pady=(6, 0))
        self.wrap_combo = ttk.Combobox(fields, textvariable=self.wrap_var, values=("none", "fixed", "language"), width=10, state="readonly")
        self.wrap_combo.grid(row=4, column=1, sticky="w", padx=(5, 14), pady=(6, 0))
        ttk.Label(fields, text="Width").grid(row=4, column=2, sticky="e", pady=(6, 0))
        self.wrap_width_spin = ttk.Spinbox(fields, from_=1, to=120, textvariable=self.wrap_width_var, width=6)
        self.wrap_width_spin.grid(row=4, column=3, sticky="w", padx=5, pady=(6, 0))
        self.click_check = ttk.Checkbutton(fields, text="Generate click TODO", variable=self.clickable_var)
        self.click_check.grid(row=4, column=4, sticky="w", pady=(6, 0))
        self.glimmer_check = ttk.Checkbutton(fields, text="Enchantment glimmer", variable=self.glimmer_var)
        self.glimmer_check.grid(row=4, column=5, sticky="w", pady=(6, 0))

        self.storage_label = ttk.Label(fields, text="Provider index")
        self.storage_label.grid(row=5, column=0, sticky="w", pady=(6, 0))
        self.storage_spin = ttk.Spinbox(fields, from_=0, to=2147483646, textvariable=self.storage_index_var, width=10)
        self.storage_spin.grid(row=5, column=1, sticky="w", padx=(5, 14), pady=(6, 0))
        self.read_only_check = ttk.Checkbutton(fields, text="Read-only storage cell", variable=self.read_only_var)
        self.read_only_check.grid(row=5, column=2, columnspan=2, sticky="w", pady=(6, 0))
        ttk.Label(fields, text="Content order").grid(row=5, column=4, sticky="e", pady=(6, 0))
        self.content_spin = ttk.Spinbox(fields, from_=0, to=53, textvariable=self.content_order_var, width=6)
        self.content_spin.grid(row=5, column=5, sticky="w", padx=5, pady=(6, 0))

        actions = ttk.Frame(fields)
        actions.grid(row=6, column=0, columnspan=6, sticky="ew", pady=(8, 0))
        ttk.Button(actions, text="Apply Slot", command=self._apply_cell).pack(side="left")
        ttk.Button(actions, text="Clear Slot", command=self._clear_cell).pack(side="left", padx=6)
        ttk.Button(actions, text="Copy Item (Ctrl+C)", command=self._copy_item).pack(side="left", padx=(8, 3))
        ttk.Button(actions, text="Paste Item (Ctrl+V)", command=self._paste_item).pack(side="left", padx=3)
        ttk.Label(actions, textvariable=self.validation_var, foreground="#b42318").pack(side="left", padx=8)
        preset_actions = ttk.Frame(fields)
        preset_actions.grid(row=7, column=0, columnspan=6, sticky="ew", pady=(6, 0))
        ttk.Label(preset_actions, text="Item preset").pack(side="left")
        self.item_preset_combo = ttk.Combobox(preset_actions, textvariable=self.item_preset_var, state="readonly", width=28)
        self.item_preset_combo.pack(side="left", padx=5)
        ttk.Button(preset_actions, text="Load", command=self._load_item_preset).pack(side="left")
        ttk.Button(preset_actions, text="Save", command=self._save_item_preset).pack(side="left", padx=(4, 0))
        ttk.Button(preset_actions, text="Save As", command=lambda: self._save_item_preset(True)).pack(side="left", padx=(4, 0))
        ttk.Button(preset_actions, text="Delete", command=self._delete_item_preset).pack(side="left", padx=(4, 0))
        fields.columnconfigure(3, weight=1)
        fields.rowconfigure(3, weight=1)

        self.code = JavaCodeEditor(right, self._code_changed)
        self.code.pack(fill="both", expand=True)
        code_actions = ttk.Frame(right)
        code_actions.pack(fill="x", pady=(7, 0))
        ttk.Label(code_actions, textvariable=self.code_state_var).pack(side="left")
        ttk.Button(code_actions, text="Regenerate", command=self._regenerate_code).pack(side="right", padx=(5, 0))
        ttk.Button(code_actions, text="Copy Java", command=self._copy_java).pack(side="right")
        self._refresh_presets()

    def _bind_events(self) -> None:
        self.template_var.trace_add("write", lambda *_: self._template_changed())
        self.title_mode_var.trace_add("write", lambda *_: self._design_changed())
        self.title_var.trace_add("write", lambda *_: self._design_changed())
        self.rows_var.trace_add("write", lambda *_: self._rows_changed())
        self.page_combo.bind("<<ComboboxSelected>>", self._page_changed)
        self.role_var.trace_add("write", lambda *_: self._role_changed())
        self.preview_language_var.trace_add("write", lambda *_: self._redraw())
        self.bind_all("<Control-s>", self._save_hotkey, add="+")

    def _load_preview_translations(self) -> None:
        bundled = self.base_dir.parent / "plugin-bundler" / "src" / "main" / "resources" / "lang"
        for directory in (bundled, self.locale_dir):
            if not directory.is_dir():
                continue
            for path in sorted(directory.glob("*.json")):
                if path.stem.casefold().endswith("_mojang"):
                    continue
                try:
                    document = json.loads(path.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError):
                    continue
                if isinstance(document, dict):
                    self._translations[path.stem.casefold()] = {
                        str(key): value for key, value in document.items() if isinstance(value, str)
                    }

    def _locale_keys(self) -> tuple[str, ...]:
        keys = {key for document in self._translations.values() for key in document}
        return tuple(sorted(keys, key=str.casefold))

    def _template_changed(self) -> None:
        selected = TEMPLATE_TYPES.get(self.template_var.get(), "single")
        old = self.design.template_type
        if selected != old:
            incompatible = {
                "single": {"content", "previous", "next"},
                "paged_content": {"storage"},
                "paged_storage": {"content"},
            }[selected]
            occupied = [slot for slot, cell in self.design.base_cells.items() if cell.role in incompatible]
            if occupied:
                self.template_var.set(TEMPLATE_LABELS[old])
                messagebox.showerror(
                    "Incompatible layout",
                    f"Clear incompatible {', '.join(sorted(incompatible))} cells first: {', '.join(map(str, occupied))}.",
                )
                return
        self.design.template_type = selected
        if selected == "single":
            self.current_page = 0
            self.page_var.set("Base layout")
        self._configure_role_values()
        self._update_page_values()
        self._load_cell()
        self._redraw()
        self._update_code()

    def _rows_changed(self) -> None:
        try:
            rows = int(self.rows_var.get())
        except (ValueError, tk.TclError):
            return
        if rows not in range(1, 7):
            return
        new_size = rows * 9
        occupied = [slot for slot in self.design.base_cells if slot >= new_size]
        occupied.extend(slot for overrides in self.design.page_overrides.values() for slot in overrides if slot >= new_size)
        if occupied:
            messagebox.showerror("Cannot reduce rows", f"Clear configured slot(s) {', '.join(map(str, sorted(set(occupied))))} first.")
            self.rows_var.set(self._row_before_change)
            return
        self.design.rows = rows
        self._row_before_change = rows
        self.selected_slot = min(self.selected_slot, new_size - 1)
        self._redraw()
        self._update_code()

    def _design_changed(self) -> None:
        self.design.title = MenuText(self.title_mode_var.get(), self.title_var.get())
        self._update_code()

    def _add_page(self) -> None:
        if self.design.template_type == "single":
            self.status_var.set("Switch the template to paged before adding page overrides")
            return
        page = max(self.design.page_overrides, default=0) + 1
        self.design.page_overrides[page] = {}
        self._update_page_values()
        self.page_var.set(f"Page {page}")
        self.current_page = page
        self._load_cell()
        self._redraw()
        self._update_code()

    def _update_page_values(self) -> None:
        values = ["Base layout"]
        if self.design.template_type != "single":
            values.extend(f"Page {page}" for page in sorted(self.design.page_overrides))
        self.page_combo.configure(values=values)

    def _page_changed(self, _event: tk.Event) -> None:
        value = self.page_var.get()
        self.current_page = 0 if value == "Base layout" else int(value.split()[-1])
        self._load_cell()
        self._redraw()

    def _canvas_click(self, event: tk.Event) -> None:
        slot = self._slot_at(event.x, event.y)
        if slot is None:
            return
        self.canvas.focus_set()
        self.selected_slot = slot
        self._load_cell()
        self._redraw()

    def _move_selection(self, _event: tk.Event, row_delta: int, column_delta: int) -> str:
        row, column = divmod(self.selected_slot, 9)
        row = max(0, min(self.design.rows - 1, row + row_delta))
        column = max(0, min(8, column + column_delta))
        self.selected_slot = row * 9 + column
        self._load_cell()
        self._redraw()
        return "break"

    def _quick_role(self, _event: tk.Event, role: str) -> str:
        allowed = {
            "single": {"storage"},
            "paged_content": {"content"},
            "paged_storage": {"storage"},
        }[self.design.template_type]
        if role not in allowed:
            self.status_var.set(f"{role.title()} slots are not available in {TEMPLATE_LABELS[self.design.template_type]}")
            return "break"
        if self.current_page:
            self.status_var.set("Storage and content hotkeys only edit the base layout")
            return "break"
        current = self.design.base_cells.get(self.selected_slot)
        if current is not None and current.role == role:
            self.status_var.set(f"Slot {self.selected_slot} is already {role} order " + str(
                current.storage_index if role == "storage" else current.content_order
            ))
            return "break"
        cells = [cell for cell in self.design.base_cells.values() if cell.role == role]
        order = max(
            ((cell.storage_index if role == "storage" else cell.content_order) for cell in cells),
            default=-1,
        ) + 1
        self.role_var.set(role)
        if role == "storage":
            self.storage_index_var.set(order)
        else:
            self.content_order_var.set(order)
        self._apply_cell()
        self.canvas.focus_set()
        return "break"

    def _bulk_role_dialog(self, role: str) -> None:
        allowed = (role == "content" and self.design.template_type == "paged_content") or (
            role == "storage" and self.design.template_type in {"single", "paged_storage"}
        )
        if not allowed:
            messagebox.showerror("Role unavailable", f"{role.title()} slots are incompatible with this template type.")
            return
        dialog = tk.Toplevel(self)
        dialog.title(f"Fill empty cells as {role}")
        dialog.transient(self.winfo_toplevel())
        dialog.resizable(False, False)
        frame = ttk.Frame(dialog, padding=14)
        frame.pack(fill="both", expand=True)
        ttk.Label(frame, text="Choose how provider/content order moves through the chest:").pack(anchor="w")
        orientation = tk.StringVar(value="horizontal")
        ttk.Radiobutton(frame, text="Horizontal — left to right, then next row", variable=orientation, value="horizontal").pack(anchor="w", pady=(8, 0))
        ttk.Radiobutton(frame, text="Vertical — top to bottom, then next column", variable=orientation, value="vertical").pack(anchor="w")
        buttons = ttk.Frame(frame)
        buttons.pack(fill="x", pady=(12, 0))
        ttk.Button(buttons, text="Cancel", command=dialog.destroy).pack(side="right")
        ttk.Button(buttons, text="Fill", command=lambda: self._bulk_fill_role(role, orientation.get(), dialog)).pack(side="right", padx=6)
        dialog.grab_set()

    def _bulk_fill_role(self, role: str, orientation: str, dialog: tk.Toplevel) -> None:
        traversal = slot_traversal(self.design.rows, orientation)
        existing = [cell for cell in self.design.base_cells.values() if cell.role == role]
        order = max(
            ((cell.storage_index if role == "storage" else cell.content_order) for cell in existing),
            default=-1,
        ) + 1
        changed = 0
        for slot in traversal:
            if slot in self.design.base_cells:
                continue
            if role == "storage":
                self.design.base_cells[slot] = MenuCell("storage", storage_index=order)
            else:
                self.design.base_cells[slot] = MenuCell("content", content_order=order)
            order += 1
            changed += 1
        dialog.destroy()
        self.current_page = 0
        self.page_var.set("Base layout")
        self._load_cell()
        self._redraw()
        self._update_code()
        self.status_var.set(f"Filled {changed} empty cells as {role} in {orientation} order")

    def _fill_item_dialog(self) -> None:
        dialog = tk.Toplevel(self)
        dialog.title("Fill empty cells with item")
        dialog.transient(self.winfo_toplevel())
        frame = ttk.Frame(dialog, padding=12)
        frame.pack(fill="both", expand=True)
        material = tk.StringVar(value=self.material_var.get())
        amount = tk.IntVar(value=self.amount_var.get())
        name_mode = tk.StringVar(value=self.name_mode_var.get())
        name = tk.StringVar(value=self.name_var.get())
        wrap = tk.StringVar(value=self.wrap_var.get())
        width = tk.IntVar(value=self.wrap_width_var.get())
        clickable = tk.BooleanVar(value=self.clickable_var.get())
        glimmer = tk.BooleanVar(value=self.glimmer_var.get())
        ttk.Label(frame, text="Material").grid(row=0, column=0, sticky="w")
        ttk.Combobox(frame, textvariable=material, values=self.assets.materials(), width=28).grid(row=0, column=1, sticky="ew", padx=6)
        ttk.Label(frame, text="Amount").grid(row=0, column=2, sticky="w")
        ttk.Spinbox(frame, from_=1, to=64, textvariable=amount, width=5).grid(row=0, column=3)
        ttk.Label(frame, text="Name").grid(row=1, column=0, sticky="w", pady=(6, 0))
        ttk.Combobox(frame, textvariable=name_mode, values=("literal", "localized", "hidden"), state="readonly", width=10).grid(row=1, column=1, sticky="w", padx=6, pady=(6, 0))
        ttk.Combobox(frame, textvariable=name, values=self._locale_keys(), width=30).grid(row=1, column=2, columnspan=2, sticky="ew", pady=(6, 0))
        ttk.Label(frame, text="Lore").grid(row=2, column=0, sticky="nw", pady=(6, 0))
        lore = tk.Text(frame, height=6, width=48, font=("Consolas", 9), undo=True)
        lore.grid(row=2, column=1, columnspan=3, sticky="nsew", padx=6, pady=(6, 0))
        lore.insert("1.0", self.lore_text.get("1.0", "end-1c"))
        ttk.Label(frame, text="One lore entry per line; prefix localized keys with @.").grid(
            row=3, column=1, columnspan=3, sticky="w", padx=6
        )
        ttk.Label(frame, text="Wrap").grid(row=4, column=0, sticky="w", pady=(6, 0))
        ttk.Combobox(frame, textvariable=wrap, values=("none", "fixed", "language"), state="readonly", width=10).grid(row=4, column=1, sticky="w", padx=6, pady=(6, 0))
        ttk.Label(frame, text="Width").grid(row=4, column=2, sticky="e", pady=(6, 0))
        ttk.Spinbox(frame, from_=1, to=120, textvariable=width, width=6).grid(row=4, column=3, pady=(6, 0))
        ttk.Checkbutton(frame, text="Generate click TODO callbacks", variable=clickable).grid(row=5, column=1, sticky="w", pady=(6, 0))
        ttk.Checkbutton(frame, text="Enchantment glimmer", variable=glimmer).grid(row=5, column=2, columnspan=2, sticky="w", pady=(6, 0))
        ttk.Label(frame, text="This replaces every slot role in the base layout.", foreground="#b42318").grid(
            row=6, column=0, columnspan=4, sticky="w", pady=(8, 0)
        )
        buttons = ttk.Frame(frame)
        buttons.grid(row=7, column=0, columnspan=4, sticky="ew", pady=(10, 0))
        ttk.Button(buttons, text="Cancel", command=dialog.destroy).pack(side="right")
        ttk.Button(buttons, text="Fill", command=lambda: self._bulk_fill_item(
            material.get(), amount.get(), name_mode.get(), name.get(), lore.get("1.0", "end-1c"),
            wrap.get(), width.get(), clickable.get(), glimmer.get(), dialog,
        )).pack(side="right", padx=6)
        frame.columnconfigure(2, weight=1)
        dialog.grab_set()

    def _bulk_fill_item(self, material: str, amount: int, name_mode: str, name: str, lore_text: str,
                        wrap: str, width: int, clickable: bool, glimmer: bool, dialog: tk.Toplevel) -> None:
        lore = tuple(
            MenuLore("localized", line[1:]) if line.startswith("@") else MenuLore("literal", line)
            for line in lore_text.splitlines()
        )
        item = MenuItemDesign(material.strip().upper(), int(amount), MenuText(name_mode, name), lore,
                              wrap, int(width), "// TODO handle this menu item" if clickable else None,
                              glimmer)
        probe = MenuDesign(rows=self.design.rows)
        probe.base_cells[0] = MenuCell("static", item=item)
        errors = probe.validate()
        if errors:
            messagebox.showerror("Invalid item", errors[0], parent=dialog)
            return
        for slot in range(self.design.size):
            self.design.base_cells[slot] = MenuCell("static", item=item)
        dialog.destroy()
        self.current_page = 0
        self.page_var.set("Base layout")
        self._load_cell()
        self._redraw()
        self._update_code()
        self.status_var.set(f"Filled all {self.design.size} cells with {item.material}")

    def _slot_at(self, canvas_x: int, canvas_y: int) -> int | None:
        origin_x = max(0, (self.canvas.winfo_width() - 176 * SCALE) // 2)
        x = (canvas_x - origin_x) // SCALE - CHEST_LEFT
        y = (canvas_y - 8) // SCALE - CHEST_TOP
        column, row = x // 18, y // 18
        if column not in range(9) or row not in range(self.design.rows):
            return None
        if x % 18 >= 16 or y % 18 >= 16:
            return None
        return row * 9 + column

    def _canvas_motion(self, event: tk.Event) -> None:
        slot = self._slot_at(event.x, event.y)
        self._hover_slot = slot
        self.canvas.delete("tooltip")
        if slot is not None:
            self._draw_hover_tooltip(event.x, event.y, self._effective_cell(slot))

    def _canvas_leave(self, _event: tk.Event) -> None:
        self._hover_slot = None
        self.canvas.delete("tooltip")

    def _effective_cell(self, slot: int) -> MenuCell | None:
        if self.current_page:
            overrides = self.design.page_overrides.get(self.current_page, {})
            if slot in overrides:
                return overrides[slot]
        return self.design.base_cells.get(slot)

    def _load_cell(self) -> None:
        row, column = divmod(self.selected_slot, 9)
        self.slot_label_var.set(f"Slot {self.selected_slot} (row {row + 1}, column {column + 1})")
        cell = self._effective_cell(self.selected_slot)
        self.role_var.set(cell.role if cell else "empty")
        if cell and cell.item:
            item = cell.item
            self.material_var.set(item.material)
            self.amount_var.set(item.amount)
            self.name_mode_var.set(item.name.mode)
            self.name_var.set(item.name.value)
            self.wrap_var.set(item.wrap)
            self.wrap_width_var.set(item.wrap_width)
            self.clickable_var.set(item.clickable)
            self.glimmer_var.set(item.glimmer)
            self.lore_text.delete("1.0", "end")
            self.lore_text.insert("1.0", "\n".join(("@" if lore.mode == "localized" else "") + lore.value for lore in item.lore))
        else:
            self.lore_text.delete("1.0", "end")
        if cell and cell.role == "storage":
            self.storage_index_var.set(cell.storage_index or 0)
            self.read_only_var.set(cell.read_only)
        if cell and cell.role == "content":
            self.content_order_var.set(cell.content_order or 0)
        self._role_changed()

    def _role_changed(self) -> None:
        role = self.role_var.get()
        if self.current_page and role not in {"empty", "static"}:
            self.validation_var.set("Page overrides may only be static")
        else:
            self.validation_var.set("")
        is_item = role in {"static", "previous", "next"}
        is_storage = role == "storage"
        is_content = role == "content"
        self.material_combo.configure(state="normal" if is_item else "disabled")
        self.amount_spin.configure(state="normal" if is_item else "disabled")
        self.name_mode_combo.configure(state="readonly" if is_item else "disabled")
        self.name_entry.configure(state="normal" if is_item else "disabled")
        self.lore_text.configure(state="normal" if is_item else "disabled")
        self.wrap_combo.configure(state="readonly" if is_item else "disabled")
        self.wrap_width_spin.configure(state="normal" if is_item else "disabled")
        self.click_check.configure(state="normal" if is_item else "disabled")
        self.glimmer_check.configure(state="normal" if is_item else "disabled")
        self.storage_spin.configure(state="normal" if is_storage else "disabled")
        self.read_only_check.configure(state="normal" if is_storage else "disabled")
        self.content_spin.configure(state="normal" if is_content else "disabled")

    def _configure_role_values(self) -> None:
        values = allowed_roles(self.design.template_type)
        self.role_combo.configure(values=values)
        if self.role_var.get() not in values:
            self.role_var.set("empty")
        self.storage_label.configure(
            text="Viewport order" if self.design.template_type == "paged_storage" else "Provider index"
        )

    def _apply_cell(self) -> None:
        role = self.role_var.get()
        if self.current_page and role not in {"empty", "static"}:
            messagebox.showerror("Invalid page override", "Only static items may differ from the base layout.")
            return
        if role == "empty":
            self._clear_cell()
            return
        item = None
        if role in {"static", "previous", "next"}:
            lore: list[MenuLore] = []
            for raw in self.lore_text.get("1.0", "end-1c").splitlines():
                if not raw:
                    lore.append(MenuLore("literal", ""))
                elif raw.startswith("@"):
                    lore.append(MenuLore("localized", raw[1:]))
                else:
                    lore.append(MenuLore("literal", raw))
            try:
                amount = int(self.amount_var.get())
                width = int(self.wrap_width_var.get())
            except (ValueError, tk.TclError):
                messagebox.showerror("Invalid item", "Amount and wrap width must be integers.")
                return
            item = MenuItemDesign(
                material=self.material_var.get().strip().upper(),
                amount=amount,
                name=MenuText(self.name_mode_var.get(), self.name_var.get()),
                lore=tuple(lore),
                wrap=self.wrap_var.get(),
                wrap_width=width,
                click_action=(
                    (self._effective_cell(self.selected_slot).item.click_action
                     if self._effective_cell(self.selected_slot) and self._effective_cell(self.selected_slot).item
                     and self._effective_cell(self.selected_slot).item.click_action is not None
                     else "// TODO handle this menu item")
                    if self.clickable_var.get() else None
                ),
                glimmer=self.glimmer_var.get(),
            )
        cell = MenuCell(
            role=role,
            item=item,
            storage_index=int(self.storage_index_var.get()) if role == "storage" else None,
            read_only=self.read_only_var.get() if role == "storage" else False,
            content_order=int(self.content_order_var.get()) if role == "content" else None,
        )
        previous_errors = set(self.design.validate())
        previous = None
        if self.current_page:
            overrides = self.design.page_overrides.setdefault(self.current_page, {})
            previous = overrides.get(self.selected_slot, ...)
            overrides[self.selected_slot] = cell
        else:
            previous = self.design.base_cells.get(self.selected_slot, ...)
            self.design.base_cells[self.selected_slot] = cell
        errors = [error for error in self.design.validate() if error not in previous_errors]
        if errors:
            if self.current_page:
                if previous is ...:
                    self.design.page_overrides[self.current_page].pop(self.selected_slot, None)
                else:
                    self.design.page_overrides[self.current_page][self.selected_slot] = previous
            else:
                if previous is ...:
                    self.design.base_cells.pop(self.selected_slot, None)
                else:
                    self.design.base_cells[self.selected_slot] = previous
            messagebox.showerror("Invalid menu cell", errors[0])
            return
        self._redraw()
        self._update_code()
        self.status_var.set(f"Updated menu slot {self.selected_slot}")

    def _clear_cell(self) -> None:
        if self.current_page:
            if self.selected_slot in self.design.base_cells and self.design.base_cells[self.selected_slot].role == "static":
                self.design.page_overrides.setdefault(self.current_page, {})[self.selected_slot] = None
            else:
                self.design.page_overrides.setdefault(self.current_page, {}).pop(self.selected_slot, None)
        else:
            self.design.base_cells.pop(self.selected_slot, None)
        self.role_var.set("empty")
        self._redraw()
        self._update_code()

    def _copy_item(self) -> None:
        cell = self._effective_cell(self.selected_slot)
        if cell is None or cell.item is None:
            self.status_var.set("Selected slot does not contain a copyable item")
            return
        self._item_clipboard = cell.item
        self.status_var.set(f"Copied item from menu slot {self.selected_slot}")

    def _copy_item_hotkey(self, _event: tk.Event) -> str:
        self._copy_item()
        return "break"

    def _paste_item(self) -> None:
        if self._item_clipboard is None:
            self.status_var.set("Copy an item before pasting")
            return
        previous_errors = set(self.design.validate())
        current = self._effective_cell(self.selected_slot)
        role = current.role if current is not None and current.role in {"static", "previous", "next"} else "static"
        if self.current_page:
            role = "static"
        pasted = MenuCell(role, item=self._item_clipboard)
        if self.current_page:
            target = self.design.page_overrides.setdefault(self.current_page, {})
        else:
            target = self.design.base_cells
        previous = target.get(self.selected_slot, ...)
        target[self.selected_slot] = pasted
        errors = [error for error in self.design.validate() if error not in previous_errors]
        if errors:
            if previous is ...:
                target.pop(self.selected_slot, None)
            else:
                target[self.selected_slot] = previous
            messagebox.showerror("Cannot paste item", errors[0])
            return
        self._load_cell()
        self._redraw()
        self._update_code()
        self.status_var.set(f"Pasted item into menu slot {self.selected_slot}")

    def _paste_item_hotkey(self, _event: tk.Event) -> str:
        self._paste_item()
        return "break"

    def _clear_item_hotkey(self, _event: tk.Event) -> str:
        self._clear_cell()
        self.status_var.set(f"Cleared menu slot {self.selected_slot}")
        return "break"

    def _redraw(self) -> None:
        self.canvas.delete("all")
        self._canvas_images.clear()
        height = (CHEST_TOP + self.design.rows * 18 + 7) * SCALE
        width = 176 * SCALE
        origin_x = max(0, (self.canvas.winfo_width() - width) // 2)
        try:
            self._source_background = tk.PhotoImage(file=str(self.assets.chest_texture))
            cropped = tk.PhotoImage(width=176, height=height // SCALE)
            cropped.tk.call(str(cropped), "copy", str(self._source_background), "-from", 0, 0, 176, height // SCALE)
            self._background = cropped.zoom(SCALE, SCALE)
            self.canvas.create_image(origin_x, 8, image=self._background, anchor="nw")
        except tk.TclError:
            self.canvas.create_rectangle(origin_x, 8, origin_x + width, 8 + height, fill="#c6c6c6")
        colors = {"static": "#48a868", "storage": "#3d7cc9", "content": "#b07bdb", "previous": "#e39a39", "next": "#e39a39"}
        labels = {"storage": "S", "content": "C", "previous": "←", "next": "→"}
        for slot in range(self.design.size):
            row, column = divmod(slot, 9)
            x = origin_x + (CHEST_LEFT + column * 18) * SCALE
            y = 8 + (CHEST_TOP + row * 18) * SCALE
            cell = self._effective_cell(slot)
            if cell:
                self.canvas.create_rectangle(x, y, x + 48, y + 48, outline=colors[cell.role], width=3)
                if cell.item:
                    icon = self._icon(cell.item.material)
                    if icon:
                        self.canvas.create_image(x + 24, y + 24, image=icon)
                        self._canvas_images.append(icon)
                    if cell.item.glimmer:
                        self.canvas.create_rectangle(
                            x + 5,
                            y + 5,
                            x + 43,
                            y + 43,
                            outline="#b36cff",
                            width=2,
                        )
                        self.canvas.create_text(
                            x + 8,
                            y + 5,
                            text="✦",
                            anchor="nw",
                            fill="#72e4ff",
                            font=("Segoe UI Symbol", 10, "bold"),
                        )
                    if cell.item.amount > 1:
                        amount = str(cell.item.amount)
                        amount_font = ("Consolas", 10, "bold")
                        self.canvas.create_text(x + 45, y + 43, text=amount, anchor="se", fill="#202020", font=amount_font)
                        self.canvas.create_text(x + 43, y + 41, text=amount, anchor="se", fill="white", font=amount_font)
                if cell.role == "storage":
                    text = f"S{cell.storage_index}{'R' if cell.read_only else ''}"
                    self.canvas.create_text(x + 24, y + 40, text=text, fill="white", font=("Segoe UI", 8, "bold"))
                elif cell.role == "content":
                    self.canvas.create_text(x + 24, y + 24, text=f"C{cell.content_order}", fill="white", font=("Segoe UI", 10, "bold"))
                elif cell.role in labels:
                    self.canvas.create_text(x + 24, y + 24, text=labels[cell.role], fill="white", font=("Segoe UI", 16, "bold"))
            if slot == self.selected_slot:
                self.canvas.create_rectangle(x - 2, y - 2, x + 50, y + 50, outline="#fff200", width=3)
        self.canvas.configure(scrollregion=(0, 0, max(width, self.canvas.winfo_width()), height + 16))

    def _draw_hover_tooltip(self, pointer_x: int, pointer_y: int, cell: MenuCell | None) -> None:
        if cell is None or cell.item is None:
            return
        language = self.preview_language_var.get().casefold()
        name, lore = render_menu_item(cell.item, self._translations.get(language, {}), language)
        lines = [] if cell.item.name.mode == "hidden" else [(name, "#FFFFFF", self._tooltip_title_font)]
        lines.extend((line, "#AAAAAA", self._tooltip_font) for line in lore)
        if not lines:
            return
        widths = [self._colored_width(text, font) for text, _color, font in lines]
        line_height = max(self._tooltip_font.metrics("linespace"), 16) + 2
        box_width = max(widths, default=0) + 14
        box_height = line_height * len(lines) + 10
        x = min(pointer_x + 16, max(4, self.canvas.winfo_width() - box_width - 4))
        y = min(pointer_y + 18, max(4, self.canvas.winfo_height() - box_height - 4))
        x = max(4, x)
        y = max(4, y)
        self.canvas.create_rectangle(x, y, x + box_width, y + box_height, fill="#100010", outline="#100010", tags=("tooltip",))
        self.canvas.create_rectangle(x + 2, y + 2, x + box_width - 2, y + box_height - 2, outline="#5000FF", width=2, tags=("tooltip",))
        self.canvas.create_rectangle(x + 4, y + 4, x + box_width - 4, y + box_height - 4, outline="#28007F", tags=("tooltip",))
        text_y = y + 6
        for text, default_color, font in lines:
            self._draw_colored_text(x + 7, text_y, text, default_color, font)
            text_y += line_height
        self.canvas.tag_raise("tooltip")

    def _colored_width(self, text: str, font: tkfont.Font) -> int:
        return sum(font.measure(segment) for segment, _color in self._minecraft_segments(text, "#FFFFFF"))

    def _draw_colored_text(self, x: int, y: int, text: str, default_color: str, font: tkfont.Font) -> None:
        cursor = x
        for segment, color in self._minecraft_segments(text, default_color):
            if not segment:
                continue
            self.canvas.create_text(cursor + 2, y + 2, text=segment, anchor="nw", fill="#000000", font=font, tags=("tooltip",))
            self.canvas.create_text(cursor, y, text=segment, anchor="nw", fill=color, font=font, tags=("tooltip",))
            cursor += font.measure(segment)

    @staticmethod
    def _minecraft_segments(text: str, default_color: str) -> list[tuple[str, str]]:
        segments: list[tuple[str, str]] = []
        buffer: list[str] = []
        color = default_color
        index = 0
        while index < len(text):
            if text[index] == "§" and index + 1 < len(text):
                if buffer:
                    segments.append(("".join(buffer), color))
                    buffer.clear()
                code = text[index + 1].lower()
                if code in MINECRAFT_COLORS:
                    color = MINECRAFT_COLORS[code]
                elif code == "r":
                    color = default_color
                index += 2
                continue
            buffer.append(text[index])
            index += 1
        if buffer or not segments:
            segments.append(("".join(buffer), color))
        return segments

    def _icon(self, material: str) -> tk.PhotoImage | None:
        if material in self._icon_cache:
            return self._icon_cache[material]
        path = self.assets.texture_for(material)
        if path is None:
            self._icon_cache[material] = None
            return None
        try:
            icon = tk.PhotoImage(file=str(path)).zoom(2, 2)
        except tk.TclError:
            icon = None
        self._icon_cache[material] = icon
        return icon

    def _update_code(self) -> None:
        self.design.template_type = TEMPLATE_TYPES.get(self.template_var.get(), "single")
        self.design.title = MenuText(self.title_mode_var.get(), self.title_var.get())
        self._dirty = True
        self._update_preset_status()
        try:
            generated = generate_java(self.design)
            self.validation_var.set("")
        except ValueError as error:
            generated = "// Complete the layout to generate Java.\n// " + str(error).replace("\n", "\n// ") + "\n"
            self.validation_var.set(str(error).splitlines()[0])
        if self._code_state in {"Detached", "Invalid callback markers"} and self._last_generated_source:
            return
        self.code.set(generated)
        self._last_generated_source = generated
        self._set_code_state("Generated")

    def _copy_java(self) -> None:
        content = self.code.get()
        if content.startswith("// Complete"):
            self.status_var.set("Fix the highlighted menu validation issue before copying")
            return
        self.clipboard_clear()
        self.clipboard_append(content)
        self.status_var.set("Generated menu Java copied to clipboard")

    def _set_code_state(self, state: str, detail: str = "") -> None:
        self._code_state = state
        self.code_state_var.set(state + (f" — {detail}" if detail else ""))

    def _code_changed(self) -> None:
        source = self.code.get()
        bodies, errors = extract_callback_bodies(source)
        if errors:
            self._set_code_state("Invalid callback markers", errors[0])
        else:
            current_structure, _ = callback_structure(source)
            generated_structure, _ = callback_structure(self._last_generated_source)
            if current_structure != generated_structure:
                self._set_code_state("Detached")
            else:
                unknown = apply_callback_bodies(self.design, bodies)
                if unknown:
                    self._set_code_state("Invalid callback markers", f"Unknown callback key {unknown[0]}")
                else:
                    self._set_code_state("Callbacks modified" if source != self._last_generated_source else "Generated")
        self._dirty = True
        self._update_preset_status()

    def _regenerate_code(self) -> None:
        source = self.code.get()
        bodies, errors = extract_callback_bodies(source)
        if errors:
            messagebox.showerror("Cannot regenerate", errors[0])
            return
        apply_callback_bodies(self.design, bodies)
        try:
            generated = generate_java(self.design)
        except ValueError as error:
            messagebox.showerror("Cannot regenerate", str(error))
            return
        self.code.set(generated)
        self._last_generated_source = generated
        self._set_code_state("Generated")
        self.status_var.set("Java regenerated; valid callback bodies were retained")

    @staticmethod
    def _preset_label(preset: ItemPreset | MenuPreset) -> str:
        return f"{'Built-in' if preset.built_in else 'Personal'} · {preset.name}"

    def _refresh_presets(self) -> None:
        self._item_presets = {self._preset_label(value): value for value in self.presets.item_presets()}
        item_errors = list(self.presets.errors)
        self._menu_presets = {self._preset_label(value): value for value in self.presets.menu_presets()}
        errors = item_errors + self.presets.errors
        self.item_preset_combo.configure(values=tuple(self._item_presets))
        self.menu_preset_combo.configure(values=tuple(self._menu_presets))
        if errors:
            self.preset_status_var.set(errors[0])
        else:
            self._update_preset_status()

    def _update_preset_status(self) -> None:
        owner = "Unsaved" if self._active_menu is None else ("Built-in" if self._active_menu.built_in else "Personal")
        self.preset_status_var.set(f"{owner}{' · modified' if self._dirty else ''}")

    def _chosen_item(self) -> ItemPreset | None:
        return self._item_presets.get(self.item_preset_var.get())

    def _chosen_menu(self) -> MenuPreset | None:
        return self._menu_presets.get(self.menu_preset_var.get())

    def _load_item_preset(self) -> None:
        preset = self._chosen_item()
        if preset is None:
            self.status_var.set("Choose an item preset first")
            return
        self._item_clipboard = copy.deepcopy(preset.item)
        self._paste_item()
        self.status_var.set(f"Loaded item preset {preset.name}")

    def _save_item_preset(self, save_as: bool = False) -> None:
        cell = self._effective_cell(self.selected_slot)
        if cell is None or cell.item is None:
            messagebox.showerror("Cannot save item", "Select a static or navigation item first.")
            return
        chosen = self._chosen_item()
        overwrite = chosen is not None and not chosen.built_in and not save_as
        if overwrite and not messagebox.askyesno("Overwrite item preset?", f"Replace {chosen.name}?"):
            return
        name = chosen.name if overwrite else simpledialog.askstring("Save item preset", "Preset name:", parent=self)
        if name is None:
            return
        try:
            saved = self.presets.save_item(name, copy.deepcopy(cell.item), chosen.preset_id if overwrite else None)
        except ValueError as error:
            messagebox.showerror("Cannot save preset", str(error)); return
        self._refresh_presets()
        self.item_preset_var.set(self._preset_label(saved))
        self.status_var.set(f"Saved item preset {saved.name}")

    def _delete_item_preset(self) -> None:
        preset = self._chosen_item()
        if preset is None: return
        if preset.built_in:
            messagebox.showerror("Read-only preset", "Built-in presets cannot be deleted."); return
        if messagebox.askyesno("Delete item preset?", f"Delete {preset.name}?"):
            self.presets.delete(preset); self.item_preset_var.set(""); self._refresh_presets()

    def _load_menu_preset(self) -> None:
        preset = self._chosen_menu()
        if preset is None:
            self.status_var.set("Choose a menu preset first"); return
        if self._dirty and not messagebox.askyesno("Load menu preset?", "Discard unsaved menu changes?"):
            return
        self.design = copy.deepcopy(preset.design)
        self._active_menu = preset
        self.template_var.set(TEMPLATE_LABELS[self.design.template_type])
        self.rows_var.set(self.design.rows); self._row_before_change = self.design.rows
        self.title_mode_var.set(self.design.title.mode); self.title_var.set(self.design.title.value)
        self.current_page = 0; self.page_var.set("Base layout"); self.selected_slot = 0
        self._update_page_values(); self._load_cell(); self._redraw()
        self.code.set(preset.java_source)
        self._last_generated_source = generate_java(self.design)
        self._set_code_state(preset.code_state)
        self._dirty = False; self._update_preset_status()
        self.status_var.set(f"Loaded menu preset {preset.name}")

    def _save_menu_preset(self, save_as: bool = False) -> None:
        chosen = self._active_menu
        overwrite = chosen is not None and not chosen.built_in and not save_as
        if overwrite and not messagebox.askyesno("Overwrite menu preset?", f"Replace {chosen.name}?"):
            return
        name = chosen.name if overwrite else simpledialog.askstring("Save menu preset", "Preset name:", parent=self)
        if name is None: return
        try:
            saved = self.presets.save_menu(name, copy.deepcopy(self.design), self.code.get(), self._code_state,
                                           chosen.preset_id if overwrite else None)
        except ValueError as error:
            messagebox.showerror("Cannot save preset", str(error)); return
        self._active_menu = saved; self._dirty = False
        self._refresh_presets(); self.menu_preset_var.set(self._preset_label(saved)); self._update_preset_status()
        self.status_var.set(f"Saved menu preset {saved.name}")

    def _delete_menu_preset(self) -> None:
        preset = self._chosen_menu()
        if preset is None: return
        if preset.built_in:
            messagebox.showerror("Read-only preset", "Built-in presets cannot be deleted."); return
        if messagebox.askyesno("Delete menu preset?", f"Delete {preset.name}?"):
            self.presets.delete(preset)
            if self._active_menu and self._active_menu.preset_id == preset.preset_id: self._active_menu = None
            self.menu_preset_var.set(""); self._refresh_presets()

    def _save_hotkey(self, event: tk.Event) -> str | None:
        if not self.winfo_ismapped(): return None
        self._save_menu_preset()
        return "break"

    def _reset(self) -> None:
        if not messagebox.askyesno("Reset menu maker?", "Discard the current menu layout?"):
            return
        self.design = MenuDesign()
        self._active_menu = None
        self._code_state = "Generated"
        self.template_var.set(TEMPLATE_LABELS["single"])
        self.rows_var.set(3)
        self.title_mode_var.set("literal")
        self.title_var.set("My Menu")
        self.current_page = 0
        self.page_var.set("Base layout")
        self.selected_slot = 0
        self._update_page_values()
        self._load_cell()
        self._redraw()
        self._update_code()
        self._dirty = False
        self._update_preset_status()
        self.status_var.set("Menu maker reset")
