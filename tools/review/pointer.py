"""Change exactly one string in a JSON file, addressed by a JSON pointer (RFC 6901), without touching
the rest of the file. The file is not re-serialised: the span of the string literal is found in the
raw text and only that span is replaced, so the diff is one line.
"""
from __future__ import annotations

import json
import re

_WS = " \t\r\n"


class PointerError(ValueError):
    pass


def split_pointer(pointer: str) -> list[str]:
    if pointer == "":
        return []
    if not pointer.startswith("/"):
        raise PointerError(f"not a JSON pointer: {pointer!r}")
    return [part.replace("~1", "/").replace("~0", "~") for part in pointer[1:].split("/")]


def resolve(document, pointer: str):
    node = document
    for part in split_pointer(pointer):
        if isinstance(node, list):
            if not re.fullmatch(r"0|[1-9][0-9]*", part) or int(part) >= len(node):
                raise PointerError(f"no element {part!r} at {pointer}")
            node = node[int(part)]
        elif isinstance(node, dict):
            if part not in node:
                raise PointerError(f"no key {part!r} at {pointer}")
            node = node[part]
        else:
            raise PointerError(f"cannot descend into a scalar at {pointer}")
    return node


class _Scanner:
    """Minimal JSON scanner that remembers where the value at a path starts and ends."""

    def __init__(self, text: str, path: list[str]):
        self.text = text
        self.path = path
        self.pos = 0
        self.found: tuple[int, int] | None = None

    def skip(self) -> None:
        while self.pos < len(self.text) and self.text[self.pos] in _WS:
            self.pos += 1

    def string(self) -> str:
        start = self.pos
        if self.text[self.pos] != '"':
            raise PointerError(f"expected a string at offset {self.pos}")
        self.pos += 1
        while self.text[self.pos] != '"':
            self.pos += 2 if self.text[self.pos] == "\\" else 1
        self.pos += 1
        return json.loads(self.text[start:self.pos])

    def value(self, depth: int) -> None:
        """Scan one value; when its path equals self.path, record its span."""
        self.skip()
        start = self.pos
        ch = self.text[self.pos]
        if ch == "{":
            self.pos += 1
            self.skip()
            if self.text[self.pos] == "}":
                self.pos += 1
            else:
                while True:
                    self.skip()
                    key = self.string()
                    self.skip()
                    self.pos += 1  # the colon
                    if depth < len(self.path) and key == self.path[depth]:
                        self.value_at(depth + 1)
                    else:
                        self.value(len(self.path) + 1)
                    self.skip()
                    if self.text[self.pos] == ",":
                        self.pos += 1
                        continue
                    self.pos += 1  # the closing brace
                    break
        elif ch == "[":
            self.pos += 1
            self.skip()
            index = 0
            if self.text[self.pos] == "]":
                self.pos += 1
            else:
                while True:
                    if depth < len(self.path) and str(index) == self.path[depth]:
                        self.value_at(depth + 1)
                    else:
                        self.value(len(self.path) + 1)
                    index += 1
                    self.skip()
                    if self.text[self.pos] == ",":
                        self.pos += 1
                        continue
                    self.pos += 1  # the closing bracket
                    break
        elif ch == '"':
            self.string()
        else:
            while self.pos < len(self.text) and self.text[self.pos] not in ",]} \t\r\n":
                self.pos += 1
        if depth == len(self.path) and self.found is None and start != self.pos:
            self.found = (start, self.pos)

    def value_at(self, depth: int) -> None:
        # the value just below a matching key: keep descending along the path
        self.value(depth)


def locate(text: str, pointer: str) -> tuple[int, int]:
    """(start, end) of the value at the pointer in the raw text."""
    path = split_pointer(pointer)
    scanner = _Scanner(text, path)
    scanner.value(0)
    if scanner.found is None:
        raise PointerError(f"cannot locate {pointer}")
    return scanner.found


def replace_string(text: str, pointer: str, old: str, new: str) -> str:
    """Return text with the string at pointer changed from old to new; refuses when old is not there."""
    document = json.loads(text)
    current = resolve(document, pointer)
    if current != old:
        raise PointerError(f"{pointer} holds {current!r}, not the expected text")
    start, end = locate(text, pointer)
    literal = text[start:end]
    if json.loads(literal) != old:
        raise PointerError(f"located the wrong span for {pointer}")
    ascii_only = "\\u" in literal
    result = text[:start] + json.dumps(new, ensure_ascii=ascii_only) + text[end:]
    if resolve(json.loads(result), pointer) != new:
        raise PointerError(f"the edit of {pointer} did not take")
    return result
