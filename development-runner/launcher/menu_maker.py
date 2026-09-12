from __future__ import annotations

import json
import re
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Literal


Role = Literal["static", "storage", "content", "previous", "next"]
TemplateType = Literal["single", "paged_content", "paged_storage"]
TextMode = Literal["literal", "localized", "hidden"]
WrapMode = Literal["none", "fixed", "language"]
LEGACY_COLOR = re.compile(r"§[0-9A-FK-ORa-fk-or]")


def allowed_roles(template_type: TemplateType) -> tuple[str, ...]:
    """Return selectable workstation roles for a concrete template type."""
    roles = {
        "single": ("empty", "static", "storage"),
        "paged_content": ("empty", "static", "content", "previous", "next"),
        "paged_storage": ("empty", "static", "storage", "previous", "next"),
    }
    try:
        return roles[template_type]
    except KeyError as error:
        raise ValueError(f"unknown template type {template_type!r}") from error


@dataclass(frozen=True)
class MenuText:
    mode: TextMode = "literal"
    value: str = ""


@dataclass(frozen=True)
class MenuLore:
    mode: TextMode
    value: str


@dataclass(frozen=True)
class MenuItemDesign:
    material: str = "STONE"
    amount: int = 1
    name: MenuText = field(default_factory=MenuText)
    lore: tuple[MenuLore, ...] = ()
    wrap: WrapMode = "none"
    wrap_width: int = 30
    click_action: str | None = None
    glimmer: bool = False

    @property
    def clickable(self) -> bool:
        return self.click_action is not None


@dataclass(frozen=True)
class MenuCell:
    role: Role
    item: MenuItemDesign | None = None
    storage_index: int | None = None
    read_only: bool = False
    content_order: int | None = None


@dataclass
class MenuDesign:
    template_type: TemplateType = "single"
    rows: int = 3
    title: MenuText = field(default_factory=lambda: MenuText("literal", "My Menu"))
    base_cells: dict[int, MenuCell] = field(default_factory=dict)
    page_overrides: dict[int, dict[int, MenuCell | None]] = field(default_factory=dict)
    content_click_action: str = "// TODO handle the selected paged content item"

    @property
    def size(self) -> int:
        return self.rows * 9

    def validate(self) -> list[str]:
        errors: list[str] = []
        if self.template_type not in {"single", "paged_content", "paged_storage"}:
            errors.append("Template type must be single, paged content, or paged storage.")
        if self.title.mode not in {"literal", "localized"}:
            errors.append("Menu title mode must be literal or localized.")
        if self.rows not in range(1, 7):
            errors.append("Rows must be between 1 and 6.")
        if not self.title.value.strip():
            errors.append("The menu title cannot be blank.")
        for slot, cell in self.base_cells.items():
            errors.extend(_validate_cell(slot, cell, self.size, self.template_type))
        content_cells = [cell for cell in self.base_cells.values() if cell.role == "content"]
        storage_cells = [cell for cell in self.base_cells.values() if cell.role == "storage"]
        if self.template_type == "single" and content_cells:
            errors.append("Single-page menus cannot contain content slots.")
        if self.template_type == "paged_content" and storage_cells:
            errors.append("Paged content menus cannot contain storage slots.")
        if self.template_type == "paged_storage" and content_cells:
            errors.append("Paged storage menus cannot contain content slots.")
        if self.template_type == "paged_content":
            content = [cell.content_order for cell in self.base_cells.values() if cell.role == "content"]
            if not content:
                errors.append("A paged menu needs at least one content slot.")
            elif sorted(content) != list(range(len(content))):
                errors.append("Paged content orders must be consecutive from 0.")
        if self.template_type == "paged_storage":
            orders = [cell.storage_index for cell in storage_cells]
            if not orders:
                errors.append("A paged storage menu needs at least one storage viewport slot.")
            elif sorted(orders) != list(range(len(orders))):
                errors.append("Paged storage orders must be consecutive from 0.")
        if self.template_type != "single":
            if sum(cell.role == "previous" for cell in self.base_cells.values()) > 1:
                errors.append("Only one previous-page control is allowed.")
            if sum(cell.role == "next" for cell in self.base_cells.values()) > 1:
                errors.append("Only one next-page control is allowed.")
        for page, overrides in self.page_overrides.items():
            if page < 1:
                errors.append("Override pages must be numbered from 1.")
            for slot, cell in overrides.items():
                base = self.base_cells.get(slot)
                if base is not None and base.role != "static":
                    errors.append(
                        f"Page {page}, slot {slot}: storage, content, and navigation cells cannot be overridden."
                    )
                if cell is not None and cell.role != "static":
                    errors.append(f"Page {page}, slot {slot}: only static overrides are allowed.")
                if cell is not None:
                    errors.extend(_validate_cell(slot, cell, self.size, self.template_type))
        storage_indices = [cell.storage_index for cell in storage_cells]
        if len(storage_indices) != len(set(storage_indices)):
            errors.append("Each storage-provider index may only be mapped once.")
        return errors


def render_menu_item(
    item: MenuItemDesign,
    translations: dict[str, str],
    language: str = "en_us",
) -> tuple[str, tuple[str, ...]]:
    """Resolve and wrap one design exactly as the generated MenuItem builder does."""
    name = _resolve_menu_text(item.name, translations)
    lines: list[str] = []
    width = item.wrap_width if item.wrap == "fixed" else (15 if language in {"zh_cn", "zh_tw", "ja_jp", "ko_kr"} else 30)
    for entry in item.lore:
        value = translations.get(entry.value, entry.value) if entry.mode == "localized" else entry.value
        if item.wrap == "none":
            lines.extend(value.splitlines() or [""])
        else:
            lines.extend(_wrap_with_minecraft_colors(value, width))
    return name, tuple(lines)


def slot_traversal(rows: int, orientation: Literal["horizontal", "vertical"]) -> tuple[int, ...]:
    """Return deterministic chest-slot order for workstation bulk fills."""
    if rows not in range(1, 7):
        raise ValueError("rows must be between 1 and 6")
    if orientation == "horizontal":
        return tuple(range(rows * 9))
    if orientation == "vertical":
        return tuple(row * 9 + column for column in range(9) for row in range(rows))
    raise ValueError("orientation must be horizontal or vertical")


def _resolve_menu_text(text: MenuText, translations: dict[str, str]) -> str:
    if text.mode == "hidden":
        return ""
    return translations.get(text.value, text.value) if text.mode == "localized" else text.value


def _wrap_with_minecraft_colors(text: str, width: int) -> list[str]:
    if width < 1:
        return []
    rendered: list[str] = []
    active = ""
    for paragraph in re.split(r"\r\n?|\n", text):
        words = paragraph.strip().split()
        paragraph_lines: list[str] = []
        line = ""
        for word in words:
            candidate = word if not line else f"{line} {word}"
            if line and _visible_length(candidate) > width:
                paragraph_lines.append(line)
                line = word
            else:
                line = candidate
        paragraph_lines.append(line)
        for index, value in enumerate(paragraph_lines):
            shown = (active if index > 0 else "") + value
            rendered.append(shown)
            active = _last_colors(active + value)
    return rendered


def _visible_length(text: str) -> int:
    return len(LEGACY_COLOR.sub("", text))


def _last_colors(text: str) -> str:
    color = ""
    formats: list[str] = []
    for match in LEGACY_COLOR.finditer(text):
        code = match.group()[1].lower()
        if code in "0123456789abcdef" or code == "r":
            color = "" if code == "r" else "§" + code
            formats.clear()
        elif code in "klmno":
            token = "§" + code
            if token not in formats:
                formats.append(token)
    return color + "".join(formats)


def _validate_cell(slot: int, cell: MenuCell, size: int, template_type: str) -> list[str]:
    errors: list[str] = []
    if cell.role not in {"static", "storage", "content", "previous", "next"}:
        errors.append(f"Slot {slot} has an unknown role {cell.role!r}.")
        return errors
    if slot < 0 or slot >= size:
        errors.append(f"Slot {slot} is outside the menu.")
    if cell.role in {"static", "previous", "next"}:
        if cell.item is None:
            errors.append(f"Slot {slot} needs an item.")
        else:
            if not re.fullmatch(r"[A-Z][A-Z0-9_]*", cell.item.material):
                errors.append(f"Slot {slot} has an invalid Bukkit material.")
            if cell.item.amount < 1 or cell.item.amount > 64:
                errors.append(f"Slot {slot} amount must be between 1 and 64.")
            if cell.item.name.mode != "hidden" and not cell.item.name.value.strip():
                errors.append(f"Slot {slot} item name cannot be blank.")
            if cell.item.wrap == "fixed" and cell.item.wrap_width < 1:
                errors.append(f"Slot {slot} wrap width must be positive.")
    if cell.role == "storage" and (cell.storage_index is None or cell.storage_index < 0):
        errors.append(f"Slot {slot} needs a non-negative storage-provider index.")
    if cell.role == "content":
        if template_type != "paged_content":
            errors.append(f"Slot {slot}: content slots require a paged content template.")
        if cell.content_order is None or cell.content_order < 0:
            errors.append(f"Slot {slot} needs a non-negative content order.")
    if cell.role in {"previous", "next"} and template_type == "single":
        errors.append(f"Slot {slot}: page controls require a paged template.")
    return errors


class MinecraftAssets:
    """Resolves Bukkit-style material names through copied Minecraft item models."""

    def __init__(self, root: Path):
        self.root = root
        self.models = root / "models"
        self.textures = root / "textures"
        self._materials: tuple[str, ...] | None = None

    @property
    def chest_texture(self) -> Path:
        return self.textures / "gui" / "container" / "generic_54.png"

    def materials(self) -> tuple[str, ...]:
        if self._materials is None:
            language = self.root / "lang" / "en_us.json"
            values = json.loads(language.read_text(encoding="utf-8")) if language.is_file() else {}
            identifiers = {
                key.rsplit(".", 1)[-1]
                for key in values
                if key.startswith(("item.minecraft.", "block.minecraft."))
            }
            available = {
                path.stem for path in (self.models / "item").glob("*.json")
            }
            self._materials = tuple(sorted(identifier.upper() for identifier in identifiers & available))
        return self._materials

    def texture_for(self, material: str) -> Path | None:
        identifier = material.lower()
        return self._model_texture("item", identifier, set())

    def _model_texture(self, kind: str, identifier: str, seen: set[tuple[str, str]]) -> Path | None:
        key = (kind, identifier)
        if key in seen:
            return None
        seen.add(key)
        model_path = self.models / kind / f"{identifier}.json"
        if not model_path.is_file():
            direct = self.textures / kind / f"{identifier}.png"
            return direct if direct.is_file() else None
        try:
            model = json.loads(model_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return None
        parent = model.get("parent", "")
        if parent == "builtin/entity":
            # Chests, skulls, banners, and similar items need entity rendering rather
            # than a flat model texture. The UI deliberately shows its fallback tile.
            return None
        textures = model.get("textures", {})
        candidates = [textures.get(name) for name in ("layer0", "all", "side", "top", "particle")]
        candidates.extend(value for value in textures.values() if isinstance(value, str))
        for reference in candidates:
            resolved = self._resolve_reference(reference, textures)
            if resolved:
                path = self.root / "textures" / f"{resolved}.png"
                if path.is_file():
                    return path
        if isinstance(parent, str) and parent.startswith("minecraft:"):
            parent_kind, _, parent_name = parent.removeprefix("minecraft:").partition("/")
            if parent_kind in {"item", "block"} and parent_name:
                return self._model_texture(parent_kind, parent_name, seen)
        for fallback_kind in ("item", "block"):
            path = self.textures / fallback_kind / f"{identifier}.png"
            if path.is_file():
                return path
        return None

    @staticmethod
    def _resolve_reference(reference: object, textures: dict[str, object]) -> str | None:
        if not isinstance(reference, str):
            return None
        visited: set[str] = set()
        while reference.startswith("#"):
            name = reference[1:]
            if name in visited:
                return None
            visited.add(name)
            next_value = textures.get(name)
            if not isinstance(next_value, str):
                return None
            reference = next_value
        return reference.removeprefix("minecraft:")


def generate_java(design: MenuDesign) -> str:
    errors = design.validate()
    if errors:
        raise ValueError("\n".join(errors))
    has_storage = any(cell.role == "storage" for cell in design.base_cells.values())
    paged_content = design.template_type == "paged_content"
    paged_storage = design.template_type == "paged_storage"
    paged = paged_content or paged_storage
    parameters = ["Player player"]
    if has_storage:
        parameters.append("StorageProvider storage")
    if paged_content:
        parameters.append("List<ItemStack> contentItems")
    lines = [
        "import dev.bisz.bundler.BundlerPlugin;",
        "import dev.bisz.menus.MenuItem;",
        "import dev.bisz.menus.MenuSession;",
        "import dev.bisz.menus.StorageProvider;" if has_storage else "",
        ("import dev.bisz.menus.PagedMenuTemplate;" if paged_content else
         "import dev.bisz.menus.PagedStorageMenuTemplate;" if paged_storage else
         "import dev.bisz.menus.SinglePageMenuTemplate;"),
        "import dev.bisz.players.locales.Locale;",
        "import java.util.List;",
        "import org.bukkit.Material;",
        "import org.bukkit.entity.Player;",
        "import org.bukkit.inventory.ItemStack;" if paged_content else "",
        "",
        f"public MenuSession openGeneratedMenu({', '.join(parameters)}) {{",
    ]
    title = _title_expression(design.title)
    if paged_content:
        content = sorted(
            ((cell.content_order, slot) for slot, cell in design.base_cells.items() if cell.role == "content")
        )
        slots = ", ".join(str(slot) for _, slot in content)
        lines.extend([
            f"    var builder = PagedMenuTemplate.<ItemStack>builder({title}, {design.rows})",
            "        .entries(contentItems)",
            f"        .contentSlots({slots})",
            "        .renderItem((viewer, contentItem) -> MenuItem.builder(contentItem)",
            "            .onClick(context -> {",
            '                // <menu-maker:callback key="content">',
            *_emit_callback_body(design.content_click_action, 16),
            "                // </menu-maker:callback>",
            "            })",
            "            .build());",
        ])
    elif paged_storage:
        storage = sorted(
            ((cell.storage_index, slot, cell.read_only) for slot, cell in design.base_cells.items() if cell.role == "storage")
        )
        slots = ", ".join(str(slot) for _, slot, _ in storage)
        read_only = ", ".join(str(slot) for _, slot, value in storage if value)
        lines.extend([
            f"    var builder = PagedStorageMenuTemplate.builder({title}, {design.rows})",
            "        .storage(storage)",
            f"        .storageSlots({slots});",
        ])
        if read_only:
            lines.append(f"    builder.readOnlySlots({read_only});")
    else:
        lines.append(f"    var builder = SinglePageMenuTemplate.builder({title}, {design.rows});")

    for slot, cell in sorted(design.base_cells.items()):
        if cell.role == "static":
            lines.extend(_emit_item_call("item", slot, cell.item, 4, callback_key=f"base:{slot}"))
        elif cell.role == "storage" and not paged_storage:
            method = "readOnlyStorageIndex" if cell.read_only else "storageIndex"
            lines.append(f"    builder.{method}({slot}, {cell.storage_index});")
        elif cell.role == "previous":
            lines.extend(_emit_item_call("previousButton", slot, cell.item, 4, callback_key=f"base:{slot}"))
        elif cell.role == "next":
            lines.extend(_emit_item_call("nextButton", slot, cell.item, 4, callback_key=f"base:{slot}"))

    if paged:
        for page, overrides in sorted(design.page_overrides.items()):
            for slot, cell in sorted(overrides.items()):
                if cell is None:
                    lines.append(f"    builder.removePageItem({page}, {slot});")
                else:
                    lines.extend(_emit_item_call("pageItem", slot, cell.item, 4, page=page, callback_key=f"page:{page}:{slot}"))

    open_args = "player, builder.build(), storage" if has_storage and not paged_storage else "player, builder.build()"
    lines.extend([
        f"    return BundlerPlugin.instance().menuManager().open({open_args});",
        "}",
    ])
    return "\n".join(line for line in lines if line != "") + "\n"


def _title_expression(title: MenuText) -> str:
    escaped = _java_string(title.value)
    return f'viewer -> Locale.get(viewer, "{escaped}")' if title.mode == "localized" else f'"{escaped}"'


def _emit_item_call(
    method: str, slot: int, item: MenuItemDesign | None, indent: int,
    page: int | None = None, callback_key: str | None = None,
) -> list[str]:
    if item is None:
        raise ValueError(f"{method} slot {slot} has no item")
    prefix = " " * indent
    first_args = f"{page}, {slot}, " if page is not None else f"{slot}, "
    result = [f"{prefix}builder.{method}({first_args}MenuItem.builder(Material.{item.material})"]
    if item.amount != 1:
        result.append(f"{prefix}    .amount({item.amount})")
    if item.name.mode == "hidden":
        result.append(f'{prefix}    .name(" ")')
    elif item.name.mode == "localized":
        result.append(f'{prefix}    .localizedName("{_java_string(item.name.value)}")')
    else:
        result.append(f'{prefix}    .name("{_java_string(item.name.value)}")')
    for lore in item.lore:
        escaped = _java_string(lore.value)
        if lore.mode == "localized" and item.wrap == "language":
            result.append(f'{prefix}    .wrappedLocalizedLore("{escaped}")')
        elif lore.mode == "localized" and item.wrap == "fixed":
            result.append(
                f'{prefix}    .lore(viewer -> List.of(dev.bisz.chat.ChatUtils.wrapTextColor(Locale.get(viewer, "{escaped}"), {item.wrap_width}).split("\\\\R", -1)))'
            )
        elif lore.mode == "localized":
            result.append(f'{prefix}    .localizedLore("{escaped}")')
        elif item.wrap == "fixed":
            result.append(f'{prefix}    .wrappedLore("{escaped}", {item.wrap_width})')
        elif item.wrap == "language":
            result.append(f'{prefix}    .languageWrappedLore("{escaped}")')
        else:
            result.append(f'{prefix}    .lore("{escaped}")')
    if item.glimmer:
        result.append(f"{prefix}    .glint()")
    if item.click_action is not None:
        result.extend([
            f"{prefix}    .onClick(context -> {{",
            f'{prefix}        // <menu-maker:callback key="{callback_key or f"base:{slot}"}">',
            *_emit_callback_body(item.click_action, indent + 8),
            f"{prefix}        // </menu-maker:callback>",
            f"{prefix}    }})",
        ])
    result.append(f"{prefix}    .build());")
    return result


CALLBACK_START = re.compile(r'^\s*// <menu-maker:callback key="([^"]+)">\s*$')
CALLBACK_END = re.compile(r"^\s*// </menu-maker:callback>\s*$")


def _emit_callback_body(body: str, indent: int) -> list[str]:
    prefix = " " * indent
    return [prefix + line if line else "" for line in body.splitlines()] or [prefix]


def extract_callback_bodies(source: str) -> tuple[dict[str, str], list[str]]:
    """Return marker-bounded callback bodies and precise, non-destructive errors."""
    bodies: dict[str, str] = {}
    errors: list[str] = []
    active_key: str | None = None
    active_line = 0
    body_lines: list[str] = []
    for number, line in enumerate(source.splitlines(), 1):
        start = CALLBACK_START.match(line)
        if start:
            if active_key is not None:
                errors.append(f"Line {number}: callback marker is nested inside {active_key!r}.")
                continue
            active_key = start.group(1)
            active_line = number
            body_lines = []
            continue
        if CALLBACK_END.match(line):
            if active_key is None:
                errors.append(f"Line {number}: callback end marker has no start marker.")
                continue
            if active_key in bodies:
                errors.append(f"Line {active_line}: duplicate callback key {active_key!r}.")
            else:
                nonblank = [len(value) - len(value.lstrip()) for value in body_lines if value.strip()]
                margin = min(nonblank, default=0)
                bodies[active_key] = "\n".join(value[margin:] if value.strip() else "" for value in body_lines)
            active_key = None
            body_lines = []
            continue
        if active_key is not None:
            body_lines.append(line)
    if active_key is not None:
        errors.append(f"Line {active_line}: callback {active_key!r} has no end marker.")
    return bodies, errors


def callback_structure(source: str) -> tuple[str, list[str]]:
    """Normalize callback bodies so editor changes outside them can be detected."""
    _bodies, errors = extract_callback_bodies(source)
    if errors:
        return source, errors
    output: list[str] = []
    inside = False
    for line in source.splitlines():
        if CALLBACK_START.match(line):
            inside = True
            output.append(line)
            output.append("<menu-maker:callback-body>")
        elif CALLBACK_END.match(line):
            inside = False
            output.append(line)
        elif not inside:
            output.append(line)
    return "\n".join(output), []


def apply_callback_bodies(design: MenuDesign, bodies: dict[str, str]) -> list[str]:
    """Apply known callback bodies to a design; unknown keys are reported."""
    unknown: list[str] = []
    for key, body in bodies.items():
        if key == "content":
            design.content_click_action = body
            continue
        parts = key.split(":")
        cell: MenuCell | None = None
        target: dict[int, MenuCell | None] | None = None
        slot = -1
        if len(parts) == 2 and parts[0] == "base" and parts[1].isdigit():
            slot = int(parts[1])
            target = design.base_cells
            cell = target.get(slot)
        elif len(parts) == 3 and parts[0] == "page" and parts[1].isdigit() and parts[2].isdigit():
            slot = int(parts[2])
            target = design.page_overrides.get(int(parts[1]))
            cell = target.get(slot) if target is not None else None
        if cell is None or cell.item is None:
            unknown.append(key)
            continue
        target[slot] = replace(cell, item=replace(cell.item, click_action=body))
    return unknown


def _java_string(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
    )
