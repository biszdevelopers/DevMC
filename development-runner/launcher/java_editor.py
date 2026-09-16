from __future__ import annotations

import re
import tkinter as tk
from tkinter import ttk


class JavaCodeEditor(ttk.Frame):
    """Small dependency-free Java editor with IDE-style navigation helpers."""

    KEYWORDS = re.compile(r"\b(?:class|public|private|protected|static|final|void|var|new|return|if|else|for|while|try|catch|import|package|extends|implements|true|false|null)\b")
    TOKENS = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\b\d+\b')

    def __init__(self, parent: tk.Misc, on_change=None):
        super().__init__(parent)
        self.on_change = on_change
        self._setting = False
        self.gutter = tk.Canvas(self, width=42, background="#252526", highlightthickness=0)
        self.gutter.grid(row=0, column=0, sticky="ns")
        self.text = tk.Text(self, undo=True, wrap="none", font=("Consolas", 10), background="#1e1e1e",
                            foreground="#d4d4d4", insertbackground="white", selectbackground="#264f78")
        self.text.grid(row=0, column=1, sticky="nsew")
        vertical = ttk.Scrollbar(self, orient="vertical", command=self._yview)
        horizontal = ttk.Scrollbar(self, orient="horizontal", command=self.text.xview)
        vertical.grid(row=0, column=2, sticky="ns")
        horizontal.grid(row=1, column=1, sticky="ew")
        self.text.configure(yscrollcommand=lambda first, last: (vertical.set(first, last), self._draw_lines()))
        self.columnconfigure(1, weight=1)
        self.rowconfigure(0, weight=1)
        self.search = ttk.Entry(self)
        self.search.bind("<Return>", self._find_next)
        self.search.bind("<Escape>", lambda _e: self._hide_search())
        self.text.tag_configure("keyword", foreground="#569cd6")
        self.text.tag_configure("comment", foreground="#6a9955")
        self.text.tag_configure("string", foreground="#ce9178")
        self.text.tag_configure("number", foreground="#b5cea8")
        self.text.tag_configure("current", background="#2a2d2e")
        self.text.tag_configure("brace", background="#515c6a", foreground="#ffffff")
        self.text.bind("<<Modified>>", self._modified)
        self.text.bind("<KeyRelease>", lambda _e: self._decorate())
        self.text.bind("<ButtonRelease-1>", lambda _e: self._decorate())
        self.text.bind("<Tab>", self._indent)
        self.text.bind("<Shift-Tab>", self._outdent)
        self.text.bind("<Control-slash>", self._comment)
        self.text.bind("<Control-f>", self._show_search)
        self.text.bind("<Configure>", lambda _e: self._draw_lines())

    def get(self) -> str:
        return self.text.get("1.0", "end-1c")

    def set(self, source: str) -> None:
        self._setting = True
        self.text.delete("1.0", "end")
        self.text.insert("1.0", source)
        self.text.edit_modified(False)
        self._setting = False
        self._decorate()

    def focus_editor(self) -> None:
        self.text.focus_set()

    def _modified(self, _event=None) -> None:
        changed = self.text.edit_modified()
        self.text.edit_modified(False)
        if changed and not self._setting and self.on_change:
            self.on_change()
        self.after_idle(self._decorate)

    def _yview(self, *args) -> None:
        self.text.yview(*args)
        self._draw_lines()

    def _draw_lines(self) -> None:
        self.gutter.delete("all")
        index = self.text.index("@0,0")
        while True:
            data = self.text.dlineinfo(index)
            if data is None:
                break
            y = data[1]
            self.gutter.create_text(36, y, anchor="ne", text=index.split(".")[0], fill="#858585", font=("Consolas", 9))
            index = self.text.index(f"{index}+1line")

    def _decorate(self) -> None:
        source = self.get()
        for tag in ("keyword", "comment", "string", "number", "current", "brace"):
            self.text.tag_remove(tag, "1.0", "end")
        for match in self.KEYWORDS.finditer(source):
            self._tag_offsets("keyword", match.start(), match.end())
        for match in self.TOKENS.finditer(source):
            token = match.group()
            tag = "comment" if token.startswith(("//", "/*")) else "string" if token.startswith('"') else "number"
            self._tag_offsets(tag, match.start(), match.end())
        self.text.tag_add("current", "insert linestart", "insert lineend+1c")
        self.text.tag_lower("current")
        self._match_brace(source)
        self._draw_lines()

    def _tag_offsets(self, tag: str, start: int, end: int) -> None:
        self.text.tag_add(tag, f"1.0+{start}c", f"1.0+{end}c")

    def _match_brace(self, source: str) -> None:
        offset = len(self.text.get("1.0", "insert"))
        position = offset - 1 if offset and source[offset - 1:offset] in "{}()[]" else offset
        if position >= len(source) or source[position:position + 1] not in "{}()[]":
            return
        pairs = {"{": "}", "(": ")", "[": "]", "}": "{", ")": "(", "]": "["}
        opening = source[position] in "{(["
        depth = 0
        sequence = range(position, len(source)) if opening else range(position, -1, -1)
        for cursor in sequence:
            char = source[cursor]
            if char == source[position]: depth += 1
            elif char == pairs[source[position]]:
                depth -= 1
                if depth == 0:
                    self._tag_offsets("brace", position, position + 1)
                    self._tag_offsets("brace", cursor, cursor + 1)
                    return

    def _selection_lines(self) -> tuple[str, str]:
        try:
            return self.text.index("sel.first linestart"), self.text.index("sel.last lineend")
        except tk.TclError:
            return self.text.index("insert linestart"), self.text.index("insert lineend")

    def _indent(self, _event=None) -> str:
        try:
            start, end = self._selection_lines()
            if self.text.tag_ranges("sel"):
                self.text.insert(start, "    ")
                line = int(end.split('.')[0])
                for number in range(int(start.split('.')[0]) + 1, line + 1): self.text.insert(f"{number}.0", "    ")
            else:
                self.text.insert("insert", "    ")
        except tk.TclError:
            pass
        return "break"

    def _outdent(self, _event=None) -> str:
        start, end = self._selection_lines()
        for number in range(int(start.split('.')[0]), int(end.split('.')[0]) + 1):
            line = self.text.get(f"{number}.0", f"{number}.4")
            count = min(4, len(line) - len(line.lstrip(" ")))
            if count: self.text.delete(f"{number}.0", f"{number}.{count}")
        return "break"

    def _comment(self, _event=None) -> str:
        start, end = self._selection_lines()
        numbers = range(int(start.split('.')[0]), int(end.split('.')[0]) + 1)
        uncomment = all(self.text.get(f"{n}.0", f"{n}.0 lineend").lstrip().startswith("//") for n in numbers)
        for number in numbers:
            line = self.text.get(f"{number}.0", f"{number}.0 lineend")
            column = len(line) - len(line.lstrip())
            if uncomment: self.text.delete(f"{number}.{column}", f"{number}.{column + 2}")
            else: self.text.insert(f"{number}.{column}", "//")
        return "break"

    def _show_search(self, _event=None) -> str:
        self.search.grid(row=2, column=1, sticky="ew")
        self.search.focus_set()
        return "break"

    def _hide_search(self) -> str:
        self.search.grid_remove()
        self.text.focus_set()
        return "break"

    def _find_next(self, _event=None) -> str:
        found = self.text.search(self.search.get(), "insert+1c", stopindex="end", nocase=True)
        if not found: found = self.text.search(self.search.get(), "1.0", stopindex="end", nocase=True)
        if found:
            end = f"{found}+{len(self.search.get())}c"
            self.text.tag_remove("sel", "1.0", "end")
            self.text.tag_add("sel", found, end)
            self.text.mark_set("insert", end)
            self.text.see(found)
        return "break"
