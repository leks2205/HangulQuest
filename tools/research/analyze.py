#!/usr/bin/env python3
"""Analyse Hangul Quest review logs (reviews.ndjson, plain or .gz, or an export zip's file).

    python3 tools/research/analyze.py reviews.ndjson [more files or folders] [--json] [--out FILE]
        [--tags src/main/resources/assets/hangulquest/data/lexicon-tags.json | --core-ids FILE]
        [--tz-offset-hours N] [--seed N]

What it reports (standard library only):

  retention     success rate of scheduled reviews by days since the previous review
  calibration   the FSRS-6 default model replayed over the log: predicted recall against what happened,
                with log loss and the log loss of always guessing the average
  probes        held-out probes at 7 and 30 days, with a Bayesian interval
  north_star    durably known core words per hour played
  experiments   per experiment and arm: success rate, probe rate, and the effect size of each arm against
                the first one, as a Beta-binomial posterior (mean difference, 95% interval, P(arm better))

A log line is {t, item, kind, mode, grade, latencyMs, elapsedDays, ctx, arm, v}, as written by the game.
Exports from the game add "core" (true/false) and hash the item ids; for raw logs give --tags or --core-ids,
otherwise every word counts as a core word and the report says so.

Notes. A review "succeeds" when the grade is Hard or better. Reviews of one word are not independent, so
the intervals of the experiment table are somewhat too narrow; they are for comparing arms, not for
claims. Probes are asked as four-option questions, so 25% is guessing.
"""
from __future__ import annotations

import argparse
import glob
import gzip
import json
import math
import random
import sys
from pathlib import Path

DAY_MS = 86_400_000
HOUR_MS = 3_600_000
SESSION_GAP_MS = 10 * 60_000
STUDY_DAY_SHIFT_MS = 4 * HOUR_MS  # the game's study day turns over at 04:00
DURABLE_STABILITY = 21.0
DURABLE_RECALL_DAYS = 3
PROBE_CHANCE = 0.25

SCHEDULING_MODES = {"MC4", "TILE", "TYPED", "SELF", "QUEST_UNAIDED", "QUEST_HINTED"}
RECALL_MODES = {"MC4", "TILE", "TYPED", "SELF", "QUEST_UNAIDED"}
ELAPSED_BINS = [(0, 0, "0 d"), (1, 1, "1 d"), (2, 3, "2-3 d"), (4, 7, "4-7 d"), (8, 14, "8-14 d"),
                (15, 30, "15-30 d"), (31, 60, "31-60 d"), (61, None, "61+ d")]

# FSRS-6 default weights (py-fsrs 6.3.2), the same as learn/fsrs/FsrsParams.java
W = [0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
     1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542]
STABILITY_MIN = 0.001
DECAY = -W[20]
FACTOR = 0.9 ** (1.0 / DECAY) - 1


# --------------------------------------------------------------------------------------- reading

def read_events(paths: list[str]) -> list[list[dict]]:
    """One list of events per input file, in file order; unreadable lines are skipped."""
    files: list[Path] = []
    for raw in paths:
        path = Path(raw)
        if path.is_dir():
            files.extend(sorted(path.glob("reviews*.ndjson*")))
        elif path.exists():
            files.append(path)
        else:
            files.extend(Path(p) for p in sorted(glob.glob(raw)))
    out = []
    for path in files:
        opener = gzip.open if path.suffix == ".gz" else open
        events = []
        with opener(path, "rt", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                try:
                    event = json.loads(line)
                    if isinstance(event, dict) and "t" in event and "item" in event:
                        events.append(event)
                except ValueError:
                    continue  # a half-written last line
        events.sort(key=lambda e: e["t"])
        out.append(events)
    return out


def load_core(tags_path: str | None, ids_path: str | None) -> set[str] | None:
    """Ids of core words (curriculum grade 1-2 or NIKL grade A), as the game names them: concept:<id>."""
    if tags_path:
        tags = json.loads(Path(tags_path).read_text(encoding="utf-8"))["tags"]
        return {"concept:" + cid for cid, tag in tags.items()
                if tag.get("curriculumGrade") in (1, 2) or tag.get("nikl") == "A"}
    if ids_path:
        return {line.strip() for line in Path(ids_path).read_text(encoding="utf-8").splitlines() if line.strip()}
    return None


# --------------------------------------------------------------------------------------- statistics

def betacf(a: float, b: float, x: float) -> float:
    tiny = 1e-30
    qab, qap, qam = a + b, a + 1.0, a - 1.0
    c, d = 1.0, 1.0 - qab * x / qap
    d = 1.0 / (d if abs(d) > tiny else tiny)
    h = d
    for m in range(1, 300):
        m2 = 2 * m
        aa = m * (b - m) * x / ((qam + m2) * (a + m2))
        d = 1.0 + aa * d
        d = 1.0 / (d if abs(d) > tiny else tiny)
        c = 1.0 + aa / c
        c = c if abs(c) > tiny else tiny
        h *= d * c
        aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
        d = 1.0 + aa * d
        d = 1.0 / (d if abs(d) > tiny else tiny)
        c = 1.0 + aa / c
        c = c if abs(c) > tiny else tiny
        delta = d * c
        h *= delta
        if abs(delta - 1.0) < 1e-12:
            break
    return h


def beta_cdf(a: float, b: float, x: float) -> float:
    """Regularised incomplete beta function I_x(a, b)."""
    if x <= 0:
        return 0.0
    if x >= 1:
        return 1.0
    front = math.exp(math.lgamma(a + b) - math.lgamma(a) - math.lgamma(b) + a * math.log(x) + b * math.log(1 - x))
    if x < (a + 1) / (a + b + 2):
        return front * betacf(a, b, x) / a
    return 1.0 - front * betacf(b, a, 1 - x) / b


def beta_quantile(a: float, b: float, q: float) -> float:
    low, high = 0.0, 1.0
    for _ in range(80):
        mid = (low + high) / 2
        if beta_cdf(a, b, mid) < q:
            low = mid
        else:
            high = mid
    return (low + high) / 2


def proportion(k: int, n: int) -> dict:
    """k of n with the mean and 95% interval of a Beta(1 + k, 1 + n - k) posterior (flat prior)."""
    if n == 0:
        return {"n": 0, "k": 0, "rate": None, "low": None, "high": None}
    a, b = 1 + k, 1 + n - k
    return {"n": n, "k": k, "rate": round(k / n, 4), "low": round(beta_quantile(a, b, 0.025), 4),
            "high": round(beta_quantile(a, b, 0.975), 4)}


def effect(base: tuple[int, int], other: tuple[int, int], rng: random.Random, draws: int = 20000) -> dict | None:
    """Posterior of rate(other) - rate(base) from (k, n) pairs, sampled; None when either has no data."""
    (k0, n0), (k1, n1) = base, other
    if n0 == 0 or n1 == 0:
        return None
    diffs = sorted(rng.betavariate(1 + k1, 1 + n1 - k1) - rng.betavariate(1 + k0, 1 + n0 - k0) for _ in range(draws))
    mean = sum(diffs) / draws
    return {"mean": round(mean, 4), "low": round(diffs[int(0.025 * draws)], 4),
            "high": round(diffs[int(0.975 * draws) - 1], 4), "p_other_better": round(sum(d > 0 for d in diffs) / draws, 4)}


# --------------------------------------------------------------------------------------- FSRS replay

class Card:
    __slots__ = ("stability", "difficulty", "last")

    def __init__(self, stability: float, difficulty: float, last: int):
        self.stability, self.difficulty, self.last = stability, difficulty, last


def retrievability(card: Card, now: int) -> float:
    elapsed = max(0, (now - card.last) // DAY_MS)
    return (1 + FACTOR * elapsed / card.stability) ** DECAY


def _init_difficulty(grade: int, clamp: bool) -> float:
    value = W[4] - math.exp(W[5] * (grade - 1)) + 1
    return min(max(value, 1.0), 10.0) if clamp else value


def _next_difficulty(difficulty: float, grade: int) -> float:
    delta = -(W[6] * (grade - 3))
    damped = difficulty + (10.0 - difficulty) * delta / 9.0
    reverted = W[7] * _init_difficulty(4, False) + (1 - W[7]) * damped
    return min(max(reverted, 1.0), 10.0)


def _short_term(stability: float, grade: int) -> float:
    increase = math.exp(W[17] * (grade - 3 + W[18])) * stability ** -W[19]
    if grade >= 2:
        increase = max(increase, 1.0)
    return max(stability * increase, STABILITY_MIN)


def _next_stability(difficulty: float, stability: float, r: float, grade: int) -> float:
    if grade == 1:
        long_term = (W[11] * difficulty ** -W[12] * ((stability + 1) ** W[13] - 1) * math.exp((1 - r) * W[14]))
        short_term = stability / math.exp(W[17] * W[18])
        value = min(long_term, short_term)
    else:
        hard = W[15] if grade == 2 else 1
        easy = W[16] if grade == 4 else 1
        value = stability * (1 + math.exp(W[8]) * (11 - difficulty) * stability ** -W[9]
                             * (math.exp((1 - r) * W[10]) - 1) * hard * easy)
    return max(value, STABILITY_MIN)


def review(card: Card | None, grade: int, now: int) -> Card:
    """One FSRS-6 review; the same arithmetic as Fsrs.java (fuzz and learning steps only change due dates)."""
    if card is None:
        return Card(max(W[grade - 1], STABILITY_MIN), _init_difficulty(grade, True), now)
    same_day = (now - card.last) // DAY_MS < 1
    if same_day:
        stability = _short_term(card.stability, grade)
    else:
        stability = _next_stability(card.difficulty, card.stability, retrievability(card, now), grade)
    return Card(stability, _next_difficulty(card.difficulty, grade), now)


def is_scheduled(event: dict) -> bool:
    return (event.get("kind") == "WORD" and event.get("mode") in SCHEDULING_MODES
            and isinstance(event.get("grade"), int) and 1 <= event["grade"] <= 4)


def replay(events: list[dict]):
    """Yields (event, card before it or None, card after it) for scheduled reviews, in time order.
    Placement exposures seed a card the way the game does (3 days of stability)."""
    cards: dict[str, Card] = {}
    for event in events:
        item = event["item"]
        if event.get("kind") == "WORD" and event.get("mode") == "EXPOSURE" and event.get("ctx") == "placement":
            if item not in cards:
                cards[item] = Card(3.0, 5.0, event["t"])
            continue
        if not is_scheduled(event):
            continue
        before = cards.get(item)
        after = review(before, event["grade"], event["t"])
        cards[item] = after
        yield event, before, after


# --------------------------------------------------------------------------------------- sections

def retention_curve(all_events: list[list[dict]]) -> list[dict]:
    counts = [[0, 0] for _ in ELAPSED_BINS]
    for events in all_events:
        for event in events:
            elapsed = event.get("elapsedDays", -1)
            if not is_scheduled(event) or not isinstance(elapsed, int) or elapsed < 0:
                continue
            for i, (low, high, _) in enumerate(ELAPSED_BINS):
                if elapsed >= low and (high is None or elapsed <= high):
                    counts[i][0] += 1
                    counts[i][1] += event["grade"] >= 2
                    break
    return [{"bin": label, **proportion(k, n)} for (_, _, label), (n, k) in zip(ELAPSED_BINS, counts)]


def calibration(all_events: list[list[dict]]) -> dict:
    predictions: list[tuple[float, int]] = []
    for events in all_events:
        for event, before, _after in replay(events):
            if before is None:
                continue  # the first review has nothing to predict
            r = min(max(retrievability(before, event["t"]), 1e-6), 1 - 1e-6)
            predictions.append((r, 1 if event["grade"] >= 2 else 0))
    n = len(predictions)
    if n == 0:
        return {"n": 0, "log_loss": None, "baseline_log_loss": None, "bins": []}
    log_loss = -sum(math.log(p if y else 1 - p) for p, y in predictions) / n
    base = min(max(sum(y for _, y in predictions) / n, 1e-6), 1 - 1e-6)
    baseline = -sum(math.log(base if y else 1 - base) for _, y in predictions) / n
    bins = []
    for i in range(10):
        chosen = [(p, y) for p, y in predictions if i / 10 <= p < (i + 1) / 10 or (i == 9 and p == 1)]
        if chosen:
            bins.append({"predicted_range": f"{i / 10:.1f}-{(i + 1) / 10:.1f}", "n": len(chosen),
                         "predicted": round(sum(p for p, _ in chosen) / len(chosen), 4),
                         "observed": round(sum(y for _, y in chosen) / len(chosen), 4)})
    calibration_error = math.sqrt(sum(b["n"] * (b["predicted"] - b["observed"]) ** 2 for b in bins) / n)
    return {"n": n, "mean_predicted": round(sum(p for p, _ in predictions) / n, 4),
            "observed": round(sum(y for _, y in predictions) / n, 4), "log_loss": round(log_loss, 4),
            "baseline_log_loss": round(baseline, 4), "rmse_bins": round(calibration_error, 4), "bins": bins}


def probe_results(all_events: list[list[dict]]) -> dict:
    result = {}
    for days in (7, 30):
        n = k = 0
        for events in all_events:
            for event in events:
                if (event.get("mode") == "PROBE" and event.get("kind") == "WORD" and event.get("ctx") == f"probe:{days}"
                        and isinstance(event.get("grade"), int)):
                    n += 1
                    k += event["grade"] >= 2
        stats = proportion(k, n)
        stats["target"] = 0.80 if days == 7 else 0.70
        stats["chance_corrected"] = None if n == 0 else round(max(0.0, (k / n - PROBE_CHANCE) / (1 - PROBE_CHANCE)), 4)
        result[f"{days}d"] = stats
    return result


def sessions_ms(events: list[dict]) -> int:
    """Sum of the spans of sessions; a new session starts after a gap of more than 10 minutes."""
    total = 0
    start = last = None
    for event in events:
        t = event["t"]
        if last is not None and t - last > SESSION_GAP_MS:
            total += last - start
            start = t
        elif start is None:
            start = t
        last = t
    if start is not None:
        total += last - start
    return total


def study_day(t: int, tz_hours: float) -> int:
    return int((t + tz_hours * HOUR_MS - STUDY_DAY_SHIFT_MS) // DAY_MS)


def durable_words(events: list[dict], core: set[str] | None, tz_hours: float) -> tuple[set[str], int, bool]:
    """(durably known core words, all durably known words, whether core membership was known)."""
    recall_days: dict[str, set[int]] = {}
    final: dict[str, Card] = {}
    for event, _before, after in replay(events):
        item = event["item"]
        final[item] = after
        if event["mode"] in RECALL_MODES and event["grade"] >= 2:
            recall_days.setdefault(item, set()).add(study_day(event["t"], tz_hours))
    core_flags = {e["item"]: bool(e["core"]) for e in events if "core" in e}
    known_core = core is not None or bool(core_flags)
    durable = {item for item, card in final.items()
               if card.stability >= DURABLE_STABILITY and len(recall_days.get(item, ())) >= DURABLE_RECALL_DAYS}

    def is_core(item: str) -> bool:
        if core is not None:
            return item in core
        return core_flags.get(item, True)

    return {item for item in durable if is_core(item)}, len(durable), known_core


def north_star(all_events: list[list[dict]], core: set[str] | None, tz_hours: float) -> dict:
    per_file = []
    total_ms = 0
    total_core = 0
    total_all = 0
    known = True
    for index, events in enumerate(all_events):
        ms = sessions_ms(events)
        words, every, known_core = durable_words(events, core, tz_hours)
        known = known and known_core
        total_ms += ms
        total_core += len(words)
        total_all += every
        hours = ms / HOUR_MS
        per_file.append({"file": index + 1, "hours": round(hours, 3), "durable_core_words": len(words),
                         "per_hour": round(len(words) / hours, 3) if hours > 0 else None})
    hours = total_ms / HOUR_MS
    return {"hours": round(hours, 3), "durable_core_words": total_core, "durable_words": total_all,
            "per_hour": round(total_core / hours, 3) if hours > 0 else None, "per_file": per_file,
            "core_known": known,
            "note": "hours are the spans of logged activity (sessions split at gaps over 10 minutes)"}


def arm_map(event: dict) -> dict[str, str]:
    arms = {}
    for part in (event.get("arm") or "").split(","):
        if ":" in part:
            name, _, arm = part.partition(":")
            arms[name.strip()] = arm.strip()
    return arms


def experiments(all_events: list[list[dict]], seed: int) -> dict:
    review_counts: dict[str, dict[str, list[int]]] = {}
    probe_counts: dict[str, dict[str, list[int]]] = {}
    words: dict[str, dict[str, set]] = {}
    for file_index, events in enumerate(all_events):
        for event in events:
            arms = arm_map(event)
            if not arms or event.get("kind") != "WORD":
                continue
            scheduled = is_scheduled(event) and event.get("elapsedDays", -1) >= 0
            probe = event.get("mode") == "PROBE" and isinstance(event.get("grade"), int)
            for name, arm in arms.items():
                words.setdefault(name, {}).setdefault(arm, set()).add((file_index, event["item"]))
                if scheduled:
                    cell = review_counts.setdefault(name, {}).setdefault(arm, [0, 0])
                    cell[0] += 1
                    cell[1] += event["grade"] >= 2
                if probe:
                    cell = probe_counts.setdefault(name, {}).setdefault(arm, [0, 0])
                    cell[0] += 1
                    cell[1] += event["grade"] >= 2
    rng = random.Random(seed)
    out = {}
    for name in sorted(set(review_counts) | set(probe_counts) | set(words)):
        arms = sorted(words.get(name, {}))
        table = {}
        for arm in arms:
            n, k = review_counts.get(name, {}).get(arm, [0, 0])
            pn, pk = probe_counts.get(name, {}).get(arm, [0, 0])
            n_words = len(words[name][arm])
            table[arm] = {"words": n_words, "reviews": n, "reviews_per_word": round(n / n_words, 2) if n_words else None,
                          "review_success": proportion(k, n), "probe_success": proportion(pk, pn)}
        effects = {}
        if len(arms) > 1:
            base = arms[0]
            for arm in arms[1:]:
                effects[f"{arm}_vs_{base}"] = {
                    "review_success": effect(tuple(reversed(review_counts.get(name, {}).get(base, [0, 0]))),
                                             tuple(reversed(review_counts.get(name, {}).get(arm, [0, 0]))), rng),
                    "probe_success": effect(tuple(reversed(probe_counts.get(name, {}).get(base, [0, 0]))),
                                            tuple(reversed(probe_counts.get(name, {}).get(arm, [0, 0]))), rng),
                }
        out[name] = {"arms": table, "effects": effects}
    return out


def analyse(paths: list[str], tags: str | None = None, core_ids: str | None = None, tz_hours: float = 0.0,
            seed: int = 1) -> dict:
    all_events = read_events(paths)
    core = load_core(tags, core_ids)
    return {
        "files": len(all_events),
        "events": sum(len(e) for e in all_events),
        "retention_curve": retention_curve(all_events),
        "calibration": calibration(all_events),
        "probes": probe_results(all_events),
        "north_star": north_star(all_events, core, tz_hours),
        "experiments": experiments(all_events, seed),
    }


# --------------------------------------------------------------------------------------- output

def pct(value) -> str:
    return "  -  " if value is None else f"{value * 100:5.1f}%"


def interval(stats: dict) -> str:
    return "" if stats["rate"] is None else f"[{stats['low'] * 100:.1f}, {stats['high'] * 100:.1f}]"


def render(report: dict) -> str:
    lines = [f"Hangul Quest research report: {report['files']} file(s), {report['events']} events", ""]
    lines.append("Retention by days since the previous review (success = Hard or better)")
    for row in report["retention_curve"]:
        lines.append(f"  {row['bin']:>8}  n={row['n']:<6} {pct(row['rate'])}  {interval(row)}")
    cal = report["calibration"]
    lines += ["", "FSRS-6 calibration (default weights replayed over the log)"]
    if cal["n"] == 0:
        lines.append("  no repeat reviews yet")
    else:
        lines.append(f"  n={cal['n']}  predicted {pct(cal['mean_predicted'])}  observed {pct(cal['observed'])}")
        lines.append(f"  log loss {cal['log_loss']:.4f}  (always guessing the average: {cal['baseline_log_loss']:.4f})"
                     f"  calibration error {cal['rmse_bins']:.4f}")
        for b in cal["bins"]:
            lines.append(f"  predicted {b['predicted_range']}  n={b['n']:<6} predicted {pct(b['predicted'])}  observed {pct(b['observed'])}")
    lines += ["", "Held-out probes (four-option questions; 25% is guessing)"]
    for key, stats in report["probes"].items():
        lines.append(f"  {key:>4}  n={stats['n']:<5} {pct(stats['rate'])}  {interval(stats)}  target {stats['target'] * 100:.0f}%"
                     f"  chance-corrected {pct(stats['chance_corrected'])}")
    ns = report["north_star"]
    lines += ["", "North star: durably known core words per hour played"]
    lines.append(f"  {ns['durable_core_words']} words in {ns['hours']:.2f} h  ->  "
                 + ("n/a" if ns["per_hour"] is None else f"{ns['per_hour']:.2f} per hour"))
    if not ns["core_known"]:
        lines.append("  (core membership unknown: every word counts; pass --tags or --core-ids)")
    lines.append(f"  {ns['note']}")
    lines += ["", "Experiments"]
    if not report["experiments"]:
        lines.append("  none in the log")
    for name, data in report["experiments"].items():
        lines.append(f"  {name}")
        for arm, row in data["arms"].items():
            lines.append(f"    arm {arm:<12} words={row['words']:<5} reviews={row['reviews']:<6} ({row['reviews_per_word']}/word)"
                         f"  review success {pct(row['review_success']['rate'])} {interval(row['review_success'])}"
                         f"  probes {pct(row['probe_success']['rate'])} (n={row['probe_success']['n']})")
        for label, effects in data["effects"].items():
            for outcome, stats in effects.items():
                if stats is None:
                    lines.append(f"    {label} {outcome}: no data")
                else:
                    lines.append(f"    {label} {outcome}: {stats['mean'] * 100:+.1f} points "
                                 f"[{stats['low'] * 100:+.1f}, {stats['high'] * 100:+.1f}], "
                                 f"P(better) {stats['p_other_better']:.2f}")
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", nargs="+", help="reviews.ndjson files (plain or .gz) or folders holding them")
    parser.add_argument("--json", action="store_true", help="print JSON instead of text")
    parser.add_argument("--out", help="write the report here instead of standard output")
    parser.add_argument("--tags", help="lexicon-tags.json, to tell core words in a raw log")
    parser.add_argument("--core-ids", help="a file with one core word id per line")
    parser.add_argument("--tz-offset-hours", type=float, default=0.0, help="local time zone for study days")
    parser.add_argument("--seed", type=int, default=1, help="seed of the posterior sampling")
    args = parser.parse_args(argv)
    report = analyse(args.files, args.tags, args.core_ids, args.tz_offset_hours, args.seed)
    text = json.dumps(report, indent=2, ensure_ascii=False) + "\n" if args.json else render(report)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
