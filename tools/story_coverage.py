#!/usr/bin/env python3
"""Can each story open at its unlock point? Coverage of the story text for the simulated learner.

A story opens when its unlock trails are done, enough words are met and 98% of its running words are known
(StoryCoverage). This joins build/reports/content/story-tokens.json (StoryCoverageDumpTest: the catalog words each
story needs, with their counts) with the learner of tools/coverage.py, and reports per story:

  taught      the share of running words known when a word counts as known once a finished step taught it
              (what the Library does: LibraryTab asks learn.isKnown or learn.introduced)
  met3        the share when a word has to be met KNOWN_AFTER times in the clues, titles and finales (the stricter
              learner of tools/coverage.py)
  minimal     the same two shares for a player who did only the unlock trails and their prerequisites
  end         the same two shares after the whole trail graph
  words       distinct words taught at that point against the story's minWords

Words the lexicon file marks as known always count. Lists the words that hold a story back.

  python3 tools/story_coverage.py            the table
  python3 tools/story_coverage.py --words    also the missing words of every story below 98%
  python3 tools/story_coverage.py --check    exit 1 when a story stays below 98% for the taught learner even at the end
  python3 tools/story_coverage.py --check-open   exit 1 when a story is below 98% or below its minWords at its unlock
                                             point on the canonical path (the gate: every story can open)

A story's glossed words (at most three, explained in the reader) count as known everywhere above.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import coverage  # noqa: E402
import variety  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "build" / "reports" / "content" / "story-tokens.json"
REQUIRED = 98


def closure(trails: list[dict], wanted: list[str]) -> list[dict]:
    """The wanted trails and everything they require."""
    by_id = {t["id"]: t for t in trails}
    seen: set[str] = set()
    todo = [w for w in wanted if w in by_id]
    while todo:
        current = todo.pop()
        if current in seen:
            continue
        seen.add(current)
        todo.extend(p for p in by_id[current]["requires"] if p in by_id)
    return [by_id[i] for i in seen]


def taught_ids(path: list[dict]) -> set[str]:
    return {concept for trail in path for step in trail["steps"] for concept in step["teach"]}


def concept_key(word_id: str) -> str:
    """The id lexicon.by_id knows: concept:x -> x, mc:item:minecraft:y -> minecraft:y."""
    if word_id.startswith("concept:"):
        return word_id[len("concept:"):]
    if word_id.startswith("mc:"):
        return word_id.split(":", 2)[2]
    return word_id


def share(needs: dict[str, int], total: int, known) -> tuple[float, list[str]]:
    missing = [w for w in needs if not known(w)]
    unknown_tokens = sum(needs[w] for w in missing)
    return 100.0 * (total - unknown_tokens) / total, missing


def evaluate(lexicon: coverage.Lexicon, story: dict, trails: list[dict]) -> dict:
    """Coverage for the learner who finished exactly `trails`.

    taught: the word is in the teach list of a finished step (the game then calls it introduced).
    met:    the word was met KNOWN_AFTER times in the texts of the finished trails (the learner of coverage.py;
            this is also how verbs get known, since quests rarely teach a verb).
    both:   either. The game accepts a word the player knows from anywhere, so this is the honest estimate.
    """
    sim = coverage.simulate(trails, lexicon, include_demo=True)
    taught = {lexicon.by_id.get(t) for t in taught_ids(sim["path"])} - {None}
    enc = sim["encounters"]

    def canonical(word_id: str):
        return lexicon.by_id.get(concept_key(word_id))

    def is_taught(word_id: str) -> bool:
        return canonical(word_id) in taught

    def is_met(word_id: str) -> bool:
        word = canonical(word_id)
        need = coverage.BOUND_KNOWN_AFTER if word in lexicon.bound else coverage.KNOWN_AFTER
        return word is not None and enc.get(word, 0) >= need

    needs, total = story["needs"], story["total"]
    # a glossed word is explained in the reader and counts as known for the unlock rule
    glossed = set(story.get("glossed", []))
    taught_share, _ = share(needs, total, lambda w: w in glossed or is_taught(w))
    met_share, _ = share(needs, total, lambda w: w in glossed or is_met(w))
    both_share, missing = share(needs, total, lambda w: w in glossed or is_taught(w) or is_met(w))
    return {"taught": taught_share, "met": met_share, "both": both_share, "words": len(taught), "missing": missing}


def teaching_trails(lexicon: coverage.Lexicon, path: list[dict], stories: list[dict]) -> dict[str, dict[str, str | None]]:
    """For each story and each word it needs: the first trail of the path after which the learner knows it."""
    first: dict[str, dict[str, str | None]] = {s["id"]: {w: None for w in s["needs"]} for s in stories}
    taught: set[str] = set()

    def after(index: int, trail: dict, encounters) -> None:
        taught.update(c for step in trail["steps"] for c in [lexicon.by_id.get(t) for t in step["teach"]] if c)
        for story in stories:
            for word, found in first[story["id"]].items():
                if found is not None:
                    continue
                canonical = lexicon.by_id.get(concept_key(word))
                need = coverage.BOUND_KNOWN_AFTER if canonical in lexicon.bound else coverage.KNOWN_AFTER
                if canonical in taught or (canonical is not None and encounters.get(canonical, 0) >= need):
                    first[story["id"]][word] = trail["id"]

    coverage.simulate(path, lexicon, include_demo=True, on_trail=after)
    return first


def run(show_words: bool, check: bool, propose: bool = False, check_open: bool = False) -> int:
    if not TOKENS.exists():
        sys.exit(f"{TOKENS} is missing. Write it first: ./gradlew test --tests '*StoryCoverageDumpTest'")
    stories = json.loads(TOKENS.read_text(encoding="utf-8"))
    trails = variety.load_steps(variety.STEPS)
    lexicon = coverage.Lexicon.from_repo()
    path = variety.learner_path(trails, True)
    index = {t["id"]: i for i, t in enumerate(path)}
    print(f"{'story':16} {'lv':>2} {'unlock':>7} | {'path':>6} {'minimal':>7} {'end':>6} | at the unlock point: "
          f"{'taught':>6} {'met3':>6} | words/min")
    never: list[str] = []
    closed: list[str] = []
    details: list[str] = []
    for story in stories:
        wanted = story["trails"]
        absent = [t for t in wanted if t not in index]
        if absent:
            print(f"{story['id']:16} unlock trails missing: {absent}")
            never.append(story["id"])
            closed.append(story["id"])
            continue
        point = max(index[t] for t in wanted)
        canon = evaluate(lexicon, story, path[:point + 1])
        minimal = evaluate(lexicon, story, closure(trails, wanted))
        end = evaluate(lexicon, story, path)

        def mark(value: float) -> str:
            return f"{value:5.1f}{'*' if value < REQUIRED else ' '}"

        print(f"{story['id']:16} {story['level']:>2} {point + 1:>3}/{len(path):<3} | {mark(canon['both']):>6} "
              f"{mark(minimal['both']):>7} {mark(end['both']):>6} | {'':20}{canon['taught']:6.1f} {canon['met']:6.1f} | "
              f"{canon['words']}/{story['minWords']}{' LOW' if canon['words'] < story['minWords'] else ''}")
        if end["both"] < REQUIRED:
            never.append(story["id"])
        if canon["both"] < REQUIRED or canon["words"] < story["minWords"]:
            closed.append(story["id"])
        if canon["both"] < REQUIRED or minimal["both"] < REQUIRED:
            details.append(f"{story['id']}: unknown at the unlock point on the canonical path "
                           f"{[w.split(':', 1)[1] for w in canon['missing']]}; for the minimal player "
                           f"{[w.split(':', 1)[1] for w in minimal['missing']]}; after everything "
                           f"{[w.split(':', 1)[1] for w in end['missing']]}")
    if propose:
        print()
        all_first = teaching_trails(lexicon, path, stories)
        for story in stories:
            canon_point = max(index[t] for t in story["trails"] if t in index)
            if evaluate(lexicon, story, path[:canon_point + 1])["both"] >= REQUIRED:
                continue
            first = all_first[story['id']]
            never_taught = [w for w, t in first.items() if t is None]
            later = {t for t in first.values() if t is not None and index[t] > canon_point}
            levels = {t: next(x["level"] for x in path if x["id"] == t) for t in later}
            print(f"{story['id']} (level {story['level']}): add trails {sorted(later, key=lambda t: index[t])} "
                  f"levels {levels}; words no trail teaches: {[w.split(':', 1)[1] for w in never_taught]}")
    print("\n* below the 98% rule. path: the canonical learner at the last unlock trail; minimal: only the unlock "
          "trails and their prerequisites; end: the whole graph.")
    if show_words:
        print("\n".join(details))
    if never:
        print(f"\nnever reach {REQUIRED}% even after the whole graph: {never}")
    if closed:
        print(f"\nclosed at the unlock point on the canonical path: {closed}")
    return 1 if ((check and never) or (check_open and closed)) else 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--words", action="store_true", help="list the missing words of stories below 98%%")
    parser.add_argument("--propose", action="store_true", help="for stories below 98%%, the trails that teach the words")
    parser.add_argument("--check", action="store_true", help="exit 1 when a story never reaches 98%% for the taught learner")
    parser.add_argument("--check-open", action="store_true",
                        help="exit 1 when a story is closed at its unlock point on the canonical path")
    args = parser.parse_args(argv)
    return run(args.words, args.check, args.propose, args.check_open)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
