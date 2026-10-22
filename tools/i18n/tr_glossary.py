"""Official Minecraft names in the target language, as a local glossary for translation packets.

The names come from the game's own language files, read from the asset cache of a local install (Loom
cache or ~/.minecraft/assets). They are Mojang's text: this module reads them at export time, puts the
few names a packet mentions into that packet, and never writes a vanilla file or a table of names into
the repository. Without the cache the packets simply carry no glossary.
"""

from __future__ import annotations

import json
import re
import sys
import zipfile
from pathlib import Path

KINDS = ("item", "block", "entity", "biome")  # the first kind wins when two share an English name
NAME_KEY = re.compile(r"^(item|block|entity|biome)\.minecraft\.[a-z0-9_]+$")
WORD = re.compile(r"[A-Za-z0-9][A-Za-z0-9'’\-]*")
MAX_WORDS = 6


def default_roots() -> list[Path]:
    return [Path("~/.gradle/caches/fabric-loom/assets").expanduser(), Path("~/.minecraft/assets").expanduser()]


def _index_file(root: Path, version: str) -> Path | None:
    indexes = root / "indexes"
    if not indexes.is_dir():
        return None
    files = sorted(indexes.glob("*.json"), key=lambda path: path.stat().st_mtime, reverse=True)
    preferred = [path for path in files if path.name.startswith(version + "-") or path.stem == version]
    return (preferred or files or [None])[0]


def _client_jars(roots: list[Path], version: str) -> list[Path]:
    """Where the client jar may be: next to a Loom asset cache, or in a launcher's versions folder."""
    jars = [Path(root).parent / version / "minecraft-client.jar" for root in roots]
    jars.append(Path("~/.minecraft/versions").expanduser() / version / f"{version}.jar")
    return jars


def read_vanilla(code: str, roots: list[Path] | None = None, version: str = "26.3") -> dict[str, str] | None:
    """The game's own `lang/<code>.json` as a dict, or None when no local copy has it.

    English is not an asset-index entry (it ships inside the client jar); the other languages are
    downloaded assets."""
    roots = [Path(root) for root in (roots or default_roots())]
    if code == "en_us":
        for jar in _client_jars(roots, version):
            if jar.is_file():
                try:
                    with zipfile.ZipFile(jar) as archive:
                        return json.loads(archive.read("assets/minecraft/lang/en_us.json").decode("utf-8"))
                except (OSError, KeyError, ValueError, zipfile.BadZipFile):
                    continue
        return None
    for root in roots:
        index = _index_file(Path(root), version)
        if index is None:
            continue
        try:
            entry = json.loads(index.read_text(encoding="utf-8"))["objects"].get(f"minecraft/lang/{code}.json")
        except (OSError, ValueError, KeyError):
            continue
        if not entry:
            continue
        path = Path(root) / "objects" / entry["hash"][:2] / entry["hash"]
        if path.is_file():
            return json.loads(path.read_text(encoding="utf-8"))
    return None


class Glossary:
    """English name to target name for items, blocks, mobs and biomes."""

    def __init__(self, english: dict[str, str], target: dict[str, str]):
        names: dict[str, tuple[str, str]] = {}
        for kind in KINDS:
            for key, en in english.items():
                match = NAME_KEY.match(key)
                if not match or match.group(1) != kind or not isinstance(en, str) or "%" in en:
                    continue
                local = target.get(key)
                if isinstance(local, str) and local.strip() and "%" not in local:
                    names.setdefault(en.casefold(), (en, local))
        self.names = names
        self.longest = max((len(name.split()) for name in names), default=1)

    def __len__(self) -> int:
        return len(self.names)

    def for_texts(self, texts: list[str]) -> dict[str, str]:
        """The names found in these texts (longest match first), sorted by English name."""
        found: dict[str, str] = {}
        for text in texts:
            words = [m.group(0) for m in WORD.finditer(text)]
            i = 0
            while i < len(words):
                hit = None
                for size in range(min(MAX_WORDS, self.longest, len(words) - i), 0, -1):
                    phrase = " ".join(words[i:i + size]).casefold()
                    hit = self.names.get(phrase) or self.names.get(self._singular(phrase))
                    if hit:
                        found[hit[0]] = hit[1]
                        i += size
                        break
                if not hit:
                    i += 1
        return dict(sorted(found.items(), key=lambda item: item[0].casefold()))

    @staticmethod
    def _singular(phrase: str) -> str:
        if phrase.endswith("ies"):
            return phrase[:-3] + "y"
        if phrase.endswith("s") and not phrase.endswith("ss"):
            return phrase[:-1]
        return phrase


def load(code: str, roots: list[Path] | None = None, version: str = "26.3") -> Glossary | None:
    """The glossary for a language, or None (with a message) when the local asset cache lacks it."""
    english = read_vanilla("en_us", roots, version)
    target = read_vanilla(code, roots, version)
    if english is None or target is None:
        print(f"note: no vanilla language file for {code} in the local asset cache; "
              "packets carry no glossary", file=sys.stderr)
        return None
    return Glossary(english, target)
