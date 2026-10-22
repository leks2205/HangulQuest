#!/usr/bin/env python3
"""Task-variety metrics and the content ratchet ("recycle words, not tasks").

Reads build/reports/content/steps.json, which the Java test StepsDumpTest writes from the mod's own
types (GoalType.taskSignature, SentenceBuilder), so Python and Java measure the same signatures.
TaskVarietyTest computes the same numbers in Java and fails when the two disagree.

  python3 tools/variety.py                    report with the gap to the roadmap targets
  python3 tools/variety.py --check            exit 1 if any metric is worse than content/metrics-baseline.json
                                              and also short of its roadmap target (inside the target it passes)
  python3 tools/variety.py --update-baseline  rewrite the baseline (variety AND coverage metrics): deliberate only
  python3 tools/variety.py --json             the metrics as JSON

Metrics (ratchet scope: every trail except *_demo, which is measured but exempt):
  duplicateSignatures   steps whose full task signature (goal type, object, verb, relation, conditions) came before
  objectOnlyRepeats     per trail: steps that differ from an earlier step of the trail only in the object word
  verbObjectMax         the most often repeated (verb, object) pair; roadmap: at most 3
  verbObjectPairsOver3  pairs that come back more than 3 times
  noveltyShare          trails that add a mechanic none of the previous 5 trails on the learner path had
  boredomIndex          consecutive learner-path steps that differ only in the object word
  bareShare.<L>         plain place/collect/break steps with no relation, condition, holding or sequence
  topTypeShare.<L>      share of the most used goal type; roadmap: at most 25%
  distinctTypes.<L>     goal types in use; roadmap: at least 6

The learner path is the trail graph in topological order, lower Korean level first, then `order`.
"""
from __future__ import annotations

import argparse
import heapq
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STEPS = ROOT / "build" / "reports" / "content" / "steps.json"
BASELINE = ROOT / "content" / "metrics-baseline.json"

NOVELTY_WINDOW = 5
PAIR_LIMIT = 3
# Values are stored with six decimals; half a unit of the last one is rounding, not a regression.
ROUNDING = 5e-7
BARE_TARGET = {1: 0.30, 2: 0.20}  # level 3 and up: 0.10
TYPE_CAP = 0.25
MIN_TYPES = 6
BOREDOM_TARGET = 0.20


# ---------------------------------------------------------------------------------------------- data

def load_steps(path: Path) -> list[dict]:
    if not path.exists():
        sys.exit(f"{path} is missing. Write it first: ./gradlew test --tests '*StepsDumpTest'")
    return json.loads(path.read_text(encoding="utf-8"))["trails"]


def learner_path(trails: list[dict], include_demo: bool) -> list[dict]:
    """Prerequisites first; among the trails that are ready, lower Korean level, then order, then id."""
    by_id: dict[str, dict] = {}
    for trail in trails:
        if include_demo or not trail["demo"]:
            by_id.setdefault(trail["id"], trail)

    def key(trail: dict):
        return (trail["level"], trail["order"], trail["id"])

    waiting: dict[str, int] = {}
    children: dict[str, list[dict]] = defaultdict(list)
    for trail in by_id.values():
        parents = {p for p in trail["requires"] if p in by_id and p != trail["id"]}
        waiting[trail["id"]] = len(parents)
        for parent in parents:
            children[parent].append(trail)
    ready = [(key(t), t["id"]) for t in by_id.values() if waiting[t["id"]] == 0]
    heapq.heapify(ready)
    path: list[dict] = []
    placed: set[str] = set()
    while ready:
        _, trail_id = heapq.heappop(ready)
        trail = by_id[trail_id]
        path.append(trail)
        placed.add(trail_id)
        for child in children.get(trail_id, []):
            waiting[child["id"]] -= 1
            if waiting[child["id"]] == 0:
                heapq.heappush(ready, (key(child), child["id"]))
    # trails on a prerequisite cycle cannot be ordered; they go last
    path.extend(sorted((t for t in by_id.values() if t["id"] not in placed), key=key))
    return path


# ------------------------------------------------------------------------------------------- metrics

def twins_in(trail: dict) -> int:
    seen: set[str] = set()
    twins = 0
    for step in trail["steps"]:
        if step["shape"] in seen:
            twins += 1
        seen.add(step["shape"])
    return twins


def novelty(path: list[dict]) -> tuple[float, list[str]]:
    """Share of trails that add a mechanic missing from the previous five, and the ids of those that do not."""
    if not path:
        return 1.0, []
    mechanics = [{m for s in t["steps"] for m in s["mechanics"]} for t in path]
    stale: list[str] = []
    for i, mine in enumerate(mechanics):
        before = set().union(*mechanics[max(0, i - NOVELTY_WINDOW):i]) if i else set()
        if i and not (mine - before):
            stale.append(path[i]["id"])
    return (len(path) - len(stale)) / len(path), stale


def compute(trails: list[dict], include_demo: bool = False) -> dict[str, float]:
    path = learner_path(trails, include_demo)
    steps = [(t["level"], s) for t in path for s in t["steps"]]
    out: dict[str, float] = {}

    seen: set[str] = set()
    duplicates = 0
    for _, step in steps:
        if step["signature"] in seen:
            duplicates += 1
        seen.add(step["signature"])
    out["variety.duplicateSignatures"] = duplicates
    out["variety.objectOnlyRepeats"] = sum(twins_in(t) for t in path)

    pairs = Counter((s["verb"], s["object"]) for _, s in steps if s["object"])
    out["variety.verbObjectMax"] = max(pairs.values(), default=0)
    out["variety.verbObjectPairsOver3"] = sum(1 for c in pairs.values() if c > PAIR_LIMIT)

    out["variety.noveltyShare"] = novelty(path)[0]
    same = sum(1 for a, b in zip(steps, steps[1:]) if a[1]["shape"] == b[1]["shape"])
    out["variety.boredomIndex"] = same / (len(steps) - 1) if len(steps) > 1 else 0.0

    total: Counter = Counter()
    bare: Counter = Counter()
    types: dict[int, Counter] = defaultdict(Counter)
    for level, step in steps:
        total[level] += 1
        bare[level] += 1 if step["bare"] else 0
        types[level][step["goalType"]] += 1
    for level in sorted(total):
        out[f"variety.bareShare.{level}"] = bare[level] / total[level]
        out[f"variety.topTypeShare.{level}"] = max(types[level].values()) / total[level]
        out[f"variety.distinctTypes.{level}"] = len(types[level])
    return out


def spec(key: str) -> tuple[str, float]:
    """Which way is better and the roadmap target for a metric key (shared with coverage.py)."""
    name = key.split(".", 1)[1]
    base, _, level = name.partition(".")
    table = {
        "variety.duplicateSignatures": ("lower", 0),
        "variety.objectOnlyRepeats": ("lower", 0),
        "variety.verbObjectMax": ("lower", PAIR_LIMIT),
        "variety.verbObjectPairsOver3": ("lower", 0),
        "variety.noveltyShare": ("higher", 1.0),
        "variety.boredomIndex": ("lower", BOREDOM_TARGET),
    }
    if key in table:
        return table[key]
    if base == "bareShare":
        return "lower", BARE_TARGET.get(int(level), 0.10)
    if base == "topTypeShare":
        return "lower", TYPE_CAP
    if base == "distinctTypes":
        return "higher", MIN_TYPES
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import coverage  # noqa: E402  (the coverage metrics know their own targets)
    return coverage.spec(key)


# ----------------------------------------------------------------------------------------- baseline

def r6(value: float) -> float:
    return round(float(value), 6)


def load_baseline(path: Path) -> dict:
    if not path.exists():
        sys.exit(f"{path} is missing. Create it with: python3 tools/variety.py --update-baseline")
    return json.loads(path.read_text(encoding="utf-8"))


def meets_target(value: float, entry: dict) -> bool:
    """True when the value reaches the roadmap target stored next to the baseline value."""
    target = entry.get("target")
    if target is None:
        return False
    return value <= target + ROUNDING if entry["better"] == "lower" else value >= target - ROUNDING


def regressions(values: dict[str, float], baseline: dict, prefix: str) -> list[str]:
    recorded = baseline["metrics"]
    problems = []
    for key, value in values.items():
        if not key.startswith(prefix):
            continue
        if key not in recorded:
            problems.append(f"{key} is not in the baseline (run tools/variety.py --update-baseline)")
            continue
        allowed = recorded[key]["value"]
        lower = recorded[key]["better"] == "lower"
        if meets_target(value, recorded[key]):
            continue  # a metric inside its roadmap target is free, whatever the baseline says
        if (value > allowed + ROUNDING) if lower else (value < allowed - ROUNDING):
            problems.append(f"{key} got worse: {fmt(value)}, baseline {fmt(allowed)} ({recorded[key]['better']} is better)")
    for key in recorded:
        if key.startswith(prefix) and key not in values:
            problems.append(f"{key} is in the baseline but no longer measured")
    return problems


def write_baseline(path: Path, sections: dict[str, dict[str, float]], params: dict | None) -> None:
    """Replace the metrics of the given prefixes ({"variety.": {...}}); the others stay as they were."""
    old = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    metrics = {k: v for k, v in old.get("metrics", {}).items()
               if not any(k.startswith(prefix) for prefix in sections)}
    for values in sections.values():
        for key, value in values.items():
            better, target = spec(key)
            metrics[key] = {"value": r6(value), "better": better, "target": target}
    comment = ("Content ratchet (roadmap Part B): a metric may improve but never get worse. The tests "
               "(TaskVarietyTest, CoverageTest, RecyclingTest) and CI fail on a regression. Update it only on "
               "purpose, with python3 tools/variety.py --update-baseline, and say why in the commit. "
               "target = the roadmap goal: a metric that meets it passes even when it is worse than the "
               "baseline value. The 2.x classic tree is far from most targets.")
    lines = ["{", f' "_comment": {json.dumps(comment, ensure_ascii=False)},', ' "schema": 1,',
             f' "params": {json.dumps(params if params is not None else old.get("params", {}))},',
             ' "metrics": {']
    items = sorted(metrics.items())
    for n, (key, item) in enumerate(items):
        lines.append(f"  {json.dumps(key)}: {json.dumps(item)}" + ("," if n + 1 < len(items) else ""))
    lines += [" }", "}"]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def fmt(value: float) -> str:
    return str(int(value)) if float(value).is_integer() else f"{value:.4f}"


def gap_lines(values: dict[str, float], baseline: dict | None, prefix: str) -> list[str]:
    lines = []
    for key, value in values.items():
        if not key.startswith(prefix):
            continue
        better, target = spec(key)
        gap = (value - target) if better == "lower" else (target - value)
        base = ""
        if baseline and key in baseline["metrics"]:
            base = f"  baseline {fmt(baseline['metrics'][key]['value']):>9}"
        status = "met" if gap <= ROUNDING else f"gap {fmt(gap)}"
        lines.append(f"  {key:<42} {fmt(value):>9}{base}  target {'<=' if better == 'lower' else '>='} {fmt(target):<6} {status}")
    return lines


# ------------------------------------------------------------------------------------------- report

def report(trails: list[dict], baseline: dict | None) -> str:
    out: list[str] = []
    path = learner_path(trails, False)
    n_steps = sum(len(t["steps"]) for t in path)
    out.append(f"task variety: {len(path)} trails, {n_steps} steps (without {sum(1 for t in trails if t['demo'])} demo trails)")
    values = compute(trails, False)
    out.extend(gap_lines(values, baseline, "variety."))

    with_demo = compute(trails, True)
    changed = [k for k in values if abs(values[k] - with_demo[k]) > ROUNDING]
    out.append("")
    out.append("with the demo trails measured too (not ratcheted): "
               + (", ".join(f"{k.split('.', 1)[1]} {fmt(with_demo[k])}" for k in changed) or "no difference"))

    twins = sorted(((twins_in(t), t["id"]) for t in path), reverse=True)
    out.append("")
    out.append("trails with the most steps that only swap the object word: "
               + ", ".join(f"{i} ({n})" for n, i in twins[:8] if n))
    pairs = Counter((s["verb"], s["object"]) for t in path for s in t["steps"] if s["object"])
    out.append("most repeated (verb, object) pairs: "
               + ", ".join(f"{v} {o} x{c}" for (v, o), c in pairs.most_common(6)))
    stale = novelty(path)[1]
    out.append(f"trails that add no new mechanic over the previous {NOVELTY_WINDOW}: {len(stale)} of {len(path)}")
    return "\n".join(out)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--steps", type=Path, default=STEPS)
    parser.add_argument("--baseline", type=Path, default=BASELINE)
    parser.add_argument("--check", action="store_true", help="fail if a metric is worse than the baseline")
    parser.add_argument("--update-baseline", action="store_true",
                        help="rewrite the baseline with the current variety and coverage metrics")
    parser.add_argument("--json", action="store_true", help="print the metrics as JSON")
    args = parser.parse_args(argv)

    trails = load_steps(args.steps)
    values = compute(trails, False)

    if args.json:
        print(json.dumps(values, indent=1))
        return 0

    if args.update_baseline:
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        import coverage
        covered = coverage.compute(trails, False)["metrics"]
        write_baseline(args.baseline, {"variety.": values, "coverage.": covered}, coverage.PARAMS)
        print(f"baseline written: {args.baseline.relative_to(ROOT) if args.baseline.is_relative_to(ROOT) else args.baseline}")
        return 0

    baseline = load_baseline(args.baseline) if args.baseline.exists() else None
    print(report(trails, baseline))
    if args.check:
        if baseline is None:
            print(f"{args.baseline} is missing. Create it with: python3 tools/variety.py --update-baseline")
            return 1
        problems = regressions(values, baseline, "variety.")
        if problems:
            print("\ntask variety got worse:\n  " + "\n  ".join(problems))
            print("Fix the content, or, if the change is deliberate: python3 tools/variety.py --update-baseline")
            return 1
        print("\ntask variety: no metric is worse than the baseline.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
