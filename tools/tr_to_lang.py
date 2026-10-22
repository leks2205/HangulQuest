#!/usr/bin/env python3
"""One-shot migration of the interface strings from `Tr` to lang keys (WP1.4a). Kept as documentation.

Before the migration, `Tr.t("German text")` was the lookup: the German literal was the key and an
English map in `Tr.java` translated it. This script

  1. reads the old `Tr.java` (`put(de, en)` pairs) for the English side,
  2. turns every `Tr.t("literal")` call listed in KEYS into `I18n.t("hangulquest.ui.<area>.<name>")`,
  3. writes both values into `en_us.json` and `de_de.json` (the keys that were composed by hand
     in the code, EXTRA, are added with their own texts), and
  4. fixes the imports.

Strings that were glued together from fragments (`Tr.t("Fällig ") + n + Tr.t(" Karten")`) were turned into
one format string with `%s` by hand before this ran; their texts are in EXTRA.

Usage (from the repository root):
    git show <commit-before-the-migration>:src/main/java/io/github/leks2205/hangulquest/util/Tr.java > /tmp/old_Tr.java
    python3 tools/tr_to_lang.py /tmp/old_Tr.java            rewrite sources and language files
    python3 tools/tr_to_lang.py /tmp/old_Tr.java --check    report only
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java"
LANG = ROOT / "src/main/resources/assets/hangulquest/lang"

U = "hangulquest.ui."

# German literal -> key, for calls that were a whole sentence already.
KEYS = {
    "Lernsystem lädt…": U + "info.loading",
    "Wiederholen": U + "info.review",
    "Quests öffnen": U + "info.open_quests",
    "Über das Lesen von Hangul": U + "info.about_hangul",
    "Wort des Tages": U + "info.word_of_day",
    "Wieder lernen": U + "words.relearn",
    "Als bekannt": U + "words.mark_known",
    "Wird wieder gelernt.": U + "words.status_relearn",
    "Als bekannt markiert.": U + "words.status_known",
    "Zurück": U + "words.back",
    "noch nicht entdeckt": U + "words.not_discovered",
    "Nichts fällig. Alles im grünen Bereich.": U + "study.nothing_due",
    "Jetzt wiederholen": U + "study.review_now",
    "Wiederholungen erscheinen auch automatisch im Spiel.": U + "study.auto_hint",
    "Quests laden…": U + "quest.loading",
    "✓ fertig": U + "quest.state.done",
    "▶ aktiv": U + "quest.state.active",
    "▸ im Log": U + "quest.state.in_log",
    "○ offen": U + "quest.state.open",
    "· gesperrt": U + "quest.state.locked",
    "Im Log": U + "quest.in_log",
    "Quest-Log ist voll (6)": U + "quest.log_full",
    "aus dem Log genommen · Fortschritt bleibt": U + "quest.removed_from_log",
    "Fokus": U + "quest.focus",
    "Starten": U + "quest.start",
    "Quests": U + "settings.quests",
    "Quest-Tracker": U + "settings.quest_tracker",
    "HUD-Karten": U + "settings.hud_cards",
    "Tooltips": U + "settings.tooltips",
    "Auto-Quiz": U + "settings.auto_quiz",
    "Ton": U + "settings.sound",
    "Übersetzungen in Quests": U + "settings.quest_translations",
    "Lute-Import": U + "settings.lute_import",
    "Fortschritt zurücksetzen": U + "settings.reset",
    "Fortschritt zurückgesetzt (Backup angelegt).": U + "settings.reset_done",
    "Noch einmal klicken, um wirklich zu löschen.": U + "settings.reset_confirm",
    "keine Datei": U + "settings.lute_none",
    "Richtig.": U + "hud.correct",
    "Die richtige Antwort steht oben.": U + "hud.right_answer",
    "Gerade ist nichts zu wiederholen.": U + "hud.nothing_to_review",
    "Willkommen bei Hangul Quest": U + "welcome.title",
    "Hangul Quest geht davon aus, dass du Hangul, das koreanische Alphabet, schon lesen kannst. "
    "Es lehrt Wörter und Grammatik, nicht das Alphabet. "
    "Die meisten lernen Hangul in ein paar Stunden.": U + "welcome.body",
    "Kannst du das lesen?": U + "welcome.caption",
    "Ich kann es lesen, los geht's": U + "welcome.yes",
    "Noch nicht, wo kann ich es lernen?": U + "welcome.not_yet",
    "Später erinnern": U + "welcome.later",
    "Nicht mehr anzeigen": U + "welcome.never",
    "Kostenlos Hangul lernen (öffnet deinen Browser)": U + "welcome.free_resources",
}

# Texts for keys the code uses without a 1:1 literal (format strings, tables, new texts): key -> (de, en).
EXTRA = {
    U + "journal.tab.info": ("Info", "Info"),
    U + "journal.tab.words": ("Wörter", "Words"),
    U + "journal.tab.study": ("Lernen", "Study"),
    U + "journal.tab.achievements": ("Erfolge", "Achievements"),
    U + "journal.tab.quests": ("Quests", "Quests"),
    U + "journal.tab.settings": ("Optionen", "Settings"),
    U + "info.discovered_mastered": ("Entdeckt %s   Beherrscht %s", "Discovered %s   Mastered %s"),
    U + "info.due_streak": ("Fällig %s   Serie %s Tage (Rekord %s)", "Due %s   Streak %s days (best %s)"),
    U + "info.today": ("Heute %s gesehen, %s richtig", "Today %s seen, %s correct"),
    U + "info.level": ("Stufe %s von 10", "Level %s of 10"),
    U + "words.count_hint": ("%s Wörter · Mausrad scrollt", "%s words · scroll to move"),
    U + "words.metrics": ("gesehen %s · richtig %s · falsch %s", "seen %s · correct %s · wrong %s"),
    U + "words.metrics_known": ("gesehen %s · richtig %s · falsch %s · bekannt",
                                 "seen %s · correct %s · wrong %s · known"),
    U + "study.cards_due": ("%s Karten fällig:", "%s cards due:"),
    U + "quest.tree_header": ("Questbaum · %s Linien", "Quest tree · %s trails"),
    U + "quest.tree_legend": ("Rad:Zoom  Ziehen  F:Fit  %s%%", "wheel:zoom  drag  F:fit  %s%%"),
    U + "quest.tip": ("%s · Stufe %s · TOPIK %s", "%s · tier %s · TOPIK %s"),
    U + "quest.title_line": ("%s · Stufe %s · TOPIK %s · %s", "%s · tier %s · TOPIK %s · %s"),
    U + "quest.nav": ("Vorher: %s   Danach: %s", "Before: %s   After: %s"),
    U + "quest.locked_reason": ("Gesperrt: %s", "Locked: %s"),
    U + "quest.added_to_log": ("ins Log gelegt: %s", "added to the log: %s"),
    U + "quest.focus_status": ("Fokus: %s", "Focus: %s"),
    U + "quest.requires_words": ("ab %s Wörtern", "from %s words"),
    U + "quest.requires_after": ("nach %s", "after %s"),
    U + "quest.goal_reached": ("Ziel erreicht · %s", "Goal reached · %s"),
    U + "quest.line_complete": ("Questline geschafft · %s", "Questline complete · %s"),
    U + "quest.sidequest_done": ("Nebenquest geschafft: %s", "Sidequest complete: %s"),
    U + "quest.reloaded": ("Questlinien neu geladen: %s.", "Questlines reloaded: %s."),
    U + "settings.language": ("Sprache: %s", "Language: %s"),
    U + "settings.language_auto": ("Auto (%s)", "Auto (%s)"),
    U + "settings.lute_info": ("Lute: %s", "Lute: %s"),
    U + "settings.lute_summary": ("%s Wörter importiert, %s übersprungen", "%s words imported, %s skipped"),
    U + "hud.quiz_ready": ("Quiz bereit: %s drücken", "Quiz ready: press %s"),
    U + "hud.open_with": ("Mit %s öffnen", "Open with %s"),
    U + "hud.load_failed": ("Inhalte konnten nicht geladen werden. Neuer Versuch folgt.",
                            "Content could not be loaded. Trying again shortly."),
    U + "quiz.keys": ("%s: antworten · Esc: schließen", "%s: answer · Esc: close"),
    U + "achievement.first_word": ("Erstes Wort", "First word"),
    U + "achievement.words_10": ("10 Wörter", "10 words"),
    U + "achievement.words_25": ("25 Wörter", "25 words"),
    U + "achievement.words_50": ("50 Wörter", "50 words"),
    U + "achievement.words_100": ("100 Wörter", "100 words"),
    U + "achievement.mastered_10": ("10 Wörter beherrscht", "10 words mastered"),
    U + "achievement.reviews_100": ("100 Wiederholungen", "100 reviews"),
    U + "achievement.streak_3": ("3 Tage in Folge", "3 days in a row"),
    U + "achievement.streak_7": ("7 Tage in Folge", "7 days in a row"),
    U + "achievement.streak_30": ("30 Tage in Folge", "30 days in a row"),
    U + "achievement.animals_10": ("10 Tiere", "10 animals"),
    U + "achievement.blocks_25": ("25 Blöcke", "25 blocks"),
    U + "achievement.first_trail": ("Erste Questlinie", "First questline"),
    U + "achievement.trails_3": ("Drei Questlinien", "Three questlines"),
    U + "achievement.sidequest_5": ("5 Nebenquests", "5 sidequests"),
    U + "category.blocks": ("Blöcke", "Blocks"),
    U + "category.tools": ("Werkzeuge", "Tools"),
    U + "category.food": ("Nahrung", "Food"),
    U + "category.animals": ("Tiere", "Animals"),
    U + "category.environment": ("Umgebung", "Surroundings"),
    U + "category.actions": ("Handlungen", "Actions"),
    U + "category.materials": ("Materialien", "Materials"),
    U + "category.combat": ("Kampf", "Combat"),
    U + "category.items": ("Gegenstände", "Items"),
    U + "category.building": ("Bauteile", "Building"),
    U + "category.misc": ("Sonstiges", "Other"),
    U + "category.phrase": ("Redewendung", "Phrase"),
}

CALL = re.compile(r'\bTr\.t\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)')
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
PUT = re.compile(r'put\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+),\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)')


def unescape(raw: str) -> str:
    return json.loads('"' + raw + '"')


def joined(group: str) -> str:
    return "".join(unescape(m.group(1)) for m in LITERAL.finditer(group))


def old_english(tr_source: Path) -> dict[str, str]:
    table = {}
    for m in PUT.finditer(tr_source.read_text(encoding="utf-8")):
        table[joined(m.group(1))] = joined(m.group(2))
    return table


def java_escape(text: str) -> str:
    return text.replace("\\", "\\\\").replace('"', '\\"')


def fix_imports(text: str) -> str:
    """Drops the `Tr` import once nothing uses it and adds `I18n` where it is used by its short name."""
    tr_import = re.search(r"^import ([\w.]*)\.Tr;\n", text, re.M)
    if tr_import is None:
        return text
    body = text.replace(tr_import.group(0), "")
    if re.search(r"(?<![.\w])Tr\b", body):
        return text
    uses_short_name = re.search(r"(?<![.\w])I18n\.", body) is not None
    has_import = re.search(r"^import [\w.]*\.I18n;", body, re.M) is not None
    if uses_short_name and not has_import:
        return text.replace(tr_import.group(0), f"import {tr_import.group(1)}.I18n;\n")
    return body


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    check = "--check" in sys.argv
    if len(args) != 1:
        print(__doc__)
        return 2
    english = old_english(Path(args[0]))
    en_values, de_values, problems = {}, {}, []
    for de, key in KEYS.items():
        if de not in english:
            problems.append(f"no English text in the old table for: {de!r}")
            continue
        de_values[key], en_values[key] = de, english[de]
    for key, (de, en) in EXTRA.items():
        de_values[key], en_values[key] = de, en

    rewritten = 0
    for path in sorted(JAVA.rglob("*.java")):
        if path.name in ("Tr.java", "I18n.java"):
            continue
        text = path.read_text(encoding="utf-8")

        def swap(m: re.Match) -> str:
            nonlocal rewritten
            literal = joined(m.group(1))
            key = KEYS.get(literal)
            if key is None:
                problems.append(f"{path.name}: Tr.t call without a key: {literal!r}")
                return m.group(0)
            rewritten += 1
            return f'I18n.t("{key}")'

        updated = CALL.sub(swap, text)
        if updated != text:
            updated = fix_imports(updated)
            if not check:
                path.write_text(updated, encoding="utf-8")

    if not check:
        for code, values in (("en_us", en_values), ("de_de", de_values)):
            file = LANG / f"{code}.json"
            data = json.loads(file.read_text(encoding="utf-8"))
            data.update(values)
            file.write_text(json.dumps(data, ensure_ascii=False, indent="\t") + "\n", encoding="utf-8")
    print(f"{rewritten} calls rewritten, {len(en_values)} keys")
    for problem in problems:
        print("PROBLEM:", problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
