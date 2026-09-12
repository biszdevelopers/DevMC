from __future__ import annotations

import json
import os
import uuid
from dataclasses import dataclass
from pathlib import Path

from .menu_maker import MenuCell, MenuDesign, MenuItemDesign, MenuLore, MenuText, generate_java


SCHEMA_VERSION = 2


@dataclass(frozen=True)
class ItemPreset:
    preset_id: str
    name: str
    item: MenuItemDesign
    built_in: bool = False


@dataclass(frozen=True)
class MenuPreset:
    preset_id: str
    name: str
    design: MenuDesign
    java_source: str
    code_state: str = "Generated"
    built_in: bool = False


class PresetStore:
    """Loads read-only tracked presets and atomically persists personal presets."""

    def __init__(self, builtins_root: Path, personal_root: Path):
        self.builtins_root = builtins_root
        self.personal_root = personal_root
        self.errors: list[str] = []
        (personal_root / "items").mkdir(parents=True, exist_ok=True)
        (personal_root / "menus").mkdir(parents=True, exist_ok=True)

    def item_presets(self) -> list[ItemPreset]:
        return self._load_kind("item")

    def menu_presets(self) -> list[MenuPreset]:
        return self._load_kind("menu")

    def _load_kind(self, kind: str) -> list:
        self.errors.clear()
        loaded = []
        seen_ids: set[str] = set()
        seen_names: set[str] = set()
        for built_in, root in ((True, self.builtins_root), (False, self.personal_root)):
            directory = root / ("items" if kind == "item" else "menus")
            if not directory.is_dir():
                continue
            for path in sorted(directory.glob("*.json")):
                try:
                    document = json.loads(path.read_text(encoding="utf-8"))
                    preset = self._decode(document, kind, built_in)
                    folded = preset.name.casefold()
                    if preset.preset_id in seen_ids or folded in seen_names:
                        raise ValueError("duplicate preset id or name")
                    seen_ids.add(preset.preset_id)
                    seen_names.add(folded)
                    loaded.append(preset)
                except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as error:
                    self.errors.append(f"Skipped {path.name}: {error}")
        return loaded

    def save_item(self, name: str, item: MenuItemDesign, preset_id: str | None = None) -> ItemPreset:
        name = self._valid_name(name)
        self._ensure_unique("item", name, preset_id)
        preset = ItemPreset(preset_id or str(uuid.uuid4()), name, item)
        self._write("items", preset.preset_id, self._encode_item_preset(preset))
        return preset

    def save_menu(
        self, name: str, design: MenuDesign, java_source: str, code_state: str,
        preset_id: str | None = None,
    ) -> MenuPreset:
        name = self._valid_name(name)
        self._ensure_unique("menu", name, preset_id)
        preset = MenuPreset(preset_id or str(uuid.uuid4()), name, design, java_source, code_state)
        self._write("menus", preset.preset_id, self._encode_menu_preset(preset))
        return preset

    def delete(self, preset: ItemPreset | MenuPreset) -> None:
        if preset.built_in:
            raise ValueError("Built-in presets are read-only; use Save As to duplicate one.")
        folder = "items" if isinstance(preset, ItemPreset) else "menus"
        path = self.personal_root / folder / f"{preset.preset_id}.json"
        if path.is_file():
            path.unlink()

    @staticmethod
    def _valid_name(name: str) -> str:
        value = name.strip()
        if not value:
            raise ValueError("Preset name cannot be blank.")
        return value

    def _ensure_unique(self, kind: str, name: str, preset_id: str | None) -> None:
        presets = self.item_presets() if kind == "item" else self.menu_presets()
        if any(value.name.casefold() == name.casefold() and value.preset_id != preset_id for value in presets):
            raise ValueError(f"A preset named {name!r} already exists.")

    def _write(self, folder: str, preset_id: str, document: dict) -> None:
        destination = self.personal_root / folder / f"{preset_id}.json"
        temporary = destination.with_suffix(f".{uuid.uuid4().hex}.tmp")
        try:
            temporary.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            os.replace(temporary, destination)
        finally:
            if temporary.exists():
                temporary.unlink()

    def _decode(self, document: dict, kind: str, built_in: bool):
        version = document.get("schemaVersion")
        if version not in {1, SCHEMA_VERSION}:
            raise ValueError(f"unsupported schema version {document.get('schemaVersion')!r}")
        if document.get("kind") != kind:
            raise ValueError(f"expected {kind!r} preset")
        preset_id = str(document["id"])
        uuid.UUID(preset_id)
        name = self._valid_name(str(document["name"]))
        if kind == "item":
            item = _item_from_dict(document["item"])
            check = MenuDesign()
            check.base_cells[0] = MenuCell("static", item=item)
            errors = check.validate()
            if errors:
                raise ValueError(errors[0])
            return ItemPreset(preset_id, name, item, built_in)
        design_value = dict(document["design"])
        if version == 1 and design_value.get("templateType") == "paged":
            design_value["templateType"] = "paged_content"
        design = _design_from_dict(design_value)
        errors = design.validate()
        if errors:
            raise ValueError(errors[0])
        source = document.get("javaSource") or generate_java(design)
        state = str(document.get("codeState", "Generated"))
        return MenuPreset(preset_id, name, design, str(source), state, built_in)

    @staticmethod
    def _encode_item_preset(preset: ItemPreset) -> dict:
        return {"schemaVersion": SCHEMA_VERSION, "kind": "item", "id": preset.preset_id,
                "name": preset.name, "item": _item_to_dict(preset.item)}

    @staticmethod
    def _encode_menu_preset(preset: MenuPreset) -> dict:
        return {"schemaVersion": SCHEMA_VERSION, "kind": "menu", "id": preset.preset_id,
                "name": preset.name, "design": _design_to_dict(preset.design),
                "javaSource": preset.java_source, "codeState": preset.code_state}


def _text_to_dict(value: MenuText | MenuLore) -> dict:
    return {"mode": value.mode, "value": value.value}


def _item_to_dict(item: MenuItemDesign) -> dict:
    return {"material": item.material, "amount": item.amount, "name": _text_to_dict(item.name),
            "lore": [_text_to_dict(line) for line in item.lore], "wrap": item.wrap,
            "wrapWidth": item.wrap_width, "clickAction": item.click_action,
            "glimmer": item.glimmer}


def _item_from_dict(value: dict) -> MenuItemDesign:
    name = value["name"]
    name_mode = str(name["mode"])
    wrap = str(value.get("wrap", "none"))
    lore = tuple(MenuLore(str(line["mode"]), str(line.get("value", ""))) for line in value.get("lore", []))
    material = str(value["material"])
    amount = int(value.get("amount", 1))
    width = int(value.get("wrapWidth", 30))
    if name_mode not in {"literal", "localized", "hidden"}:
        raise ValueError(f"invalid item name mode {name_mode!r}")
    if any(line.mode not in {"literal", "localized"} for line in lore):
        raise ValueError("invalid lore text mode")
    if wrap not in {"none", "fixed", "language"}:
        raise ValueError(f"invalid wrapping mode {wrap!r}")
    if not material or amount not in range(1, 65) or width < 1:
        raise ValueError("invalid material, amount, or wrapping width")
    click_action = value.get("clickAction")
    if click_action is not None and not isinstance(click_action, str):
        raise ValueError("clickAction must be text or null")
    glimmer = value.get("glimmer", False)
    if not isinstance(glimmer, bool):
        raise ValueError("glimmer must be true or false")
    return MenuItemDesign(material, amount, MenuText(name_mode, str(name.get("value", ""))), lore,
                          wrap, width, click_action, glimmer)


def _cell_to_dict(cell: MenuCell | None) -> dict | None:
    if cell is None:
        return None
    return {"role": cell.role, "item": _item_to_dict(cell.item) if cell.item else None,
            "storageIndex": cell.storage_index, "readOnly": cell.read_only,
            "contentOrder": cell.content_order}


def _cell_from_dict(value: dict | None) -> MenuCell | None:
    if value is None:
        return None
    return MenuCell(str(value["role"]), _item_from_dict(value["item"]) if value.get("item") else None,
                    value.get("storageIndex"), bool(value.get("readOnly", False)), value.get("contentOrder"))


def _design_to_dict(design: MenuDesign) -> dict:
    return {"templateType": design.template_type, "rows": design.rows, "title": _text_to_dict(design.title),
            "baseCells": {str(slot): _cell_to_dict(cell) for slot, cell in design.base_cells.items()},
            "pageOverrides": {str(page): {str(slot): _cell_to_dict(cell) for slot, cell in cells.items()}
                              for page, cells in design.page_overrides.items()},
            "contentClickAction": design.content_click_action}


def _design_from_dict(value: dict) -> MenuDesign:
    title = value["title"]
    return MenuDesign(str(value.get("templateType", "single")), int(value.get("rows", 3)),
                      MenuText(str(title["mode"]), str(title.get("value", ""))),
                      {int(slot): _cell_from_dict(cell) for slot, cell in value.get("baseCells", {}).items()},
                      {int(page): {int(slot): _cell_from_dict(cell) for slot, cell in cells.items()}
                       for page, cells in value.get("pageOverrides", {}).items()},
                      str(value.get("contentClickAction", "// TODO handle the selected paged content item")))
