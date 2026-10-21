#!/usr/bin/env python3
"""Tests for analyze.py on synthetic logs with known answers.

    python3 tools/research/test_analyze.py

AnalyzeScriptTest runs this from the Gradle test task, so CI covers it.
"""
from __future__ import annotations

import gzip
import json
import random
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import analyze  # noqa: E402

T0 = 1_700_000_000_000
MIN = 60_000
DAY = 86_400_000


def event(t, item, mode="MC4", grade=3, ctx="quiz", arm=None, elapsed=-1, core=None):
    e = {"t": t, "item": item, "kind": "WORD", "mode": mode, "grade": grade, "latencyMs": 900,
         "elapsedDays": elapsed, "ctx": ctx, "arm": arm, "v": 1}
    if core is not None:
        e["core"] = core
    return e


def write(path, events, gz=False):
    text = "".join(json.dumps(e) + "\n" for e in events)
    if gz:
        with gzip.open(path, "wt", encoding="utf-8") as handle:
            handle.write(text)
    else:
        Path(path).write_text(text, encoding="utf-8")


class FsrsPortTest(unittest.TestCase):
    def test_matches_the_java_vector(self):
        # FsrsVectorTest: Good at 0 and 10 min, then 3 days, ... ; stability after each review
        times = [0, 600_000, 259_800_000, 1_815_000_000, 9_245_400_000, 38_189_400_000]
        expected = [2.3065, 2.3065, 13.835840133660218, 65.19710158641902, 254.0186486063658, 840.9170220478804]
        card = None
        for t, want in zip(times, expected):
            card = analyze.review(card, 3, t)
            self.assertAlmostEqual(card.stability, want, places=6)

    def test_again_lowers_stability(self):
        card = analyze.review(None, 3, 0)
        card = analyze.review(card, 3, 10 * DAY)
        lapsed = analyze.review(card, 1, 30 * DAY)
        self.assertLess(lapsed.stability, card.stability)


class RetentionTest(unittest.TestCase):
    def test_bins_hold_the_right_counts(self):
        events = []
        # (elapsed days, grade): bin "0 d" 2x, "1 d" 3x (one fail), "4-7 d" 1x, "61+ d" 1x
        for i, (elapsed, grade) in enumerate([(0, 3), (0, 1), (1, 3), (1, 3), (1, 1), (5, 2), (90, 4), (-1, 3)]):
            events.append(event(T0 + i * MIN, f"w{i}", grade=grade, elapsed=elapsed))
        events.append(event(T0, "x", mode="MC2", grade=3, elapsed=1))  # a guess: not a scheduled review
        events.append(event(T0, "y", mode="PROBE", grade=3, elapsed=7))  # probes are reported separately
        report = analyze.retention_curve([events])
        by = {row["bin"]: row for row in report}
        self.assertEqual((by["0 d"]["n"], by["0 d"]["k"]), (2, 1))
        self.assertEqual((by["1 d"]["n"], by["1 d"]["k"]), (3, 2))
        self.assertEqual((by["4-7 d"]["n"], by["4-7 d"]["k"]), (1, 1))
        self.assertEqual((by["61+ d"]["n"], by["61+ d"]["k"]), (1, 1))
        self.assertEqual(sum(row["n"] for row in report), 7)


class CalibrationTest(unittest.TestCase):
    def test_a_learner_who_follows_the_model_is_calibrated(self):
        rng = random.Random(5)
        events = []
        for word in range(120):
            card = None
            t = T0 + word * 7 * MIN
            for step in range(8):
                grade = 3
                if card is not None:
                    r = analyze.retrievability(card, t)
                    grade = 3 if rng.random() < r else 1
                events.append(event(t, f"w{word}", grade=grade))
                card = analyze.review(card, grade, t)
                t += max(1, int(card.stability * rng.uniform(0.3, 4))) * DAY
        report = analyze.calibration([events])
        self.assertGreater(report["n"], 700)
        self.assertAlmostEqual(report["mean_predicted"], report["observed"], delta=0.03)
        self.assertLess(report["rmse_bins"], 0.05)
        self.assertLess(report["log_loss"], report["baseline_log_loss"])

    def test_a_learner_the_model_overestimates_is_not(self):
        rng = random.Random(6)
        events = []
        for word in range(120):
            card = None
            t = T0
            for step in range(6):
                grade = 3
                if card is not None:
                    grade = 3 if rng.random() < 0.4 * analyze.retrievability(card, t) else 1
                events.append(event(t, f"w{word}", grade=grade))
                card = analyze.review(card, grade, t)
                t += max(1, int(card.stability)) * DAY
        report = analyze.calibration([events])
        self.assertGreater(report["mean_predicted"] - report["observed"], 0.2)
        self.assertGreater(report["rmse_bins"], 0.2)


class ProbeTest(unittest.TestCase):
    def test_rates_and_intervals(self):
        events = []
        for i in range(40):
            events.append(event(T0 + i, f"a{i}", mode="PROBE", grade=3 if i < 32 else 1, ctx="probe:7", elapsed=7))
            events.append(event(T0 + i, f"b{i}", mode="PROBE", grade=3 if i < 24 else 1, ctx="probe:30", elapsed=30))
        events.append(event(T0, "c", mode="MC4", grade=1, ctx="probe:7"))  # not a PROBE line
        probes = analyze.probe_results([events])
        self.assertEqual((probes["7d"]["n"], probes["7d"]["k"], probes["7d"]["rate"]), (40, 32, 0.8))
        self.assertEqual((probes["30d"]["n"], probes["30d"]["k"], probes["30d"]["rate"]), (40, 24, 0.6))
        self.assertLess(probes["7d"]["low"], 0.8)
        self.assertGreater(probes["7d"]["high"], 0.8)
        self.assertLess(probes["30d"]["high"] - probes["30d"]["low"], 0.35)
        self.assertEqual(probes["7d"]["chance_corrected"], round((0.8 - 0.25) / 0.75, 4))


class BetaTest(unittest.TestCase):
    def test_quantiles_of_a_known_beta(self):
        # Beta(1, 1) is uniform; Beta(2, 1) has cdf x^2
        self.assertAlmostEqual(analyze.beta_quantile(1, 1, 0.25), 0.25, places=6)
        self.assertAlmostEqual(analyze.beta_quantile(2, 1, 0.25), 0.5, places=6)
        self.assertAlmostEqual(analyze.beta_cdf(3, 5, 0.4), 0.5801, places=3)


class NorthStarTest(unittest.TestCase):
    def build(self):
        events = []
        days = [0, 3, 10, 25, 60]
        for d, day in enumerate(days):
            base = T0 + day * DAY
            minute = 0

            def add(item, **kw):
                nonlocal minute
                events.append(event(base + minute * MIN, item, **kw))
                minute += 1

            for item in ("c1", "c2", "c3"):
                add(item, core=True, elapsed=-1 if d == 0 else days[d] - days[d - 1])
            add("n1", core=False)  # not a core word
            add("g1", mode="MC2", core=True)  # a two-option guess, right every time
            add("h1", mode="QUEST_HINTED", grade=2, core=True)  # hinted: not unaided recall
            if d == 0:
                for _ in range(3):
                    add("s1", core=True)  # three reviews on one day
        return events

    def test_counts_only_what_earned_it(self):
        events = self.build()
        words, every, known = analyze.durable_words(events, None, 0)
        self.assertEqual(words, {"c1", "c2", "c3"})
        self.assertTrue(known)
        self.assertEqual(every, 4)  # n1 is durable too, but not core
        # recall on three days matters: with only the first two review days nothing is durable yet
        early = [e for e in events if e["t"] < T0 + 5 * DAY]
        self.assertEqual(analyze.durable_words(early, None, 0)[0], set())

    def test_hours_come_from_sessions_split_at_ten_minutes(self):
        events = self.build()
        # day 0 has 9 events in 8 minutes, the four later days 6 events in 5 minutes each
        self.assertEqual(analyze.sessions_ms(events), (8 + 4 * 5) * MIN)
        gaps = [event(T0 + m * MIN, "w") for m in (0, 9, 19, 30, 35)]
        # 0-9-19 stays one session (gaps of 9 and 10 minutes), 19-30 splits (11 minutes), then 30-35
        self.assertEqual(analyze.sessions_ms(gaps), (19 + 5) * MIN)

    def test_the_report_combines_both(self):
        report = analyze.north_star([self.build()], None, 0)
        self.assertEqual(report["durable_core_words"], 3)
        self.assertAlmostEqual(report["hours"], 28 / 60, places=3)
        self.assertAlmostEqual(report["per_hour"], 3 / (28 / 60), places=2)

    def test_core_ids_override_the_flag(self):
        events = self.build()
        words, _, _ = analyze.durable_words(events, {"c1", "n1"}, 0)
        self.assertEqual(words, {"c1", "n1"})


class ExperimentTest(unittest.TestCase):
    def make(self, p_a, p_b, seed=3):
        rng = random.Random(seed)
        events = []
        for word in range(300):
            arm = "retention:0.85" if word % 2 == 0 else "retention:0.90"
            p = p_a if word % 2 == 0 else p_b
            for step in range(4):
                events.append(event(T0 + word * MIN + step * DAY, f"w{word}", grade=3 if rng.random() < p else 1,
                                    arm=arm, elapsed=1 + step))
            events.append(event(T0 + 40 * DAY + word, f"w{word}", mode="PROBE", ctx="probe:30",
                                grade=3 if rng.random() < p else 1, arm=arm, elapsed=30))
        return events

    def test_a_real_difference_is_found(self):
        report = analyze.experiments([self.make(0.75, 0.90)], seed=1)["retention"]
        self.assertEqual(set(report["arms"]), {"0.85", "0.90"})
        self.assertEqual(report["arms"]["0.85"]["words"], 150)
        stats = report["effects"]["0.90_vs_0.85"]["review_success"]
        self.assertGreater(stats["mean"], 0.08)
        self.assertLess(stats["mean"], 0.22)
        self.assertGreater(stats["low"], 0.0)
        self.assertGreater(stats["p_other_better"], 0.99)
        self.assertIsNotNone(report["effects"]["0.90_vs_0.85"]["probe_success"])

    def test_no_difference_is_not_found(self):
        stats = analyze.experiments([self.make(0.8, 0.8, seed=4)], seed=1)["retention"]["effects"]["0.90_vs_0.85"]["review_success"]
        self.assertLess(stats["low"], 0.0)
        self.assertGreater(stats["high"], 0.0)

    def test_the_direction_follows_the_arms(self):
        stats = analyze.experiments([self.make(0.9, 0.7)], seed=1)["retention"]["effects"]["0.90_vs_0.85"]["review_success"]
        self.assertLess(stats["high"], 0.0)

    def test_two_experiments_are_split_apart(self):
        events = [event(T0, "a", arm="retention:0.85,productive:3d", elapsed=1),
                  event(T0, "b", arm="retention:0.90,productive:7d", elapsed=1),
                  event(T0, "c", arm=None, elapsed=1)]
        report = analyze.experiments([events], seed=1)
        self.assertEqual(set(report), {"retention", "productive"})
        self.assertEqual(set(report["productive"]["arms"]), {"3d", "7d"})
        self.assertEqual(report["retention"]["arms"]["0.85"]["words"], 1)


class CommandLineTest(unittest.TestCase):
    def run_cli(self, *args):
        return subprocess.run([sys.executable, "-I", str(HERE / "analyze.py"), *args], capture_output=True,
                              text=True, check=True).stdout

    def test_json_and_text_from_plain_and_gzipped_files(self):
        with tempfile.TemporaryDirectory() as folder:
            plain = Path(folder) / "reviews.ndjson"
            packed = Path(folder) / "reviews-2026-01.ndjson.gz"
            write(plain, ExperimentTest().make(0.75, 0.9)[:200])
            write(packed, NorthStarTest().build(), gz=True)
            data = json.loads(self.run_cli("--json", str(plain), str(packed)))
            self.assertEqual(data["files"], 2)
            self.assertEqual(data["north_star"]["durable_core_words"], 3)
            for key in ("retention_curve", "calibration", "probes", "north_star", "experiments"):
                self.assertIn(key, data)
            text = self.run_cli(str(plain), str(packed))
            self.assertIn("North star", text)
            self.assertIn("Retention by days", text)
            # a folder is read the same way
            both = json.loads(self.run_cli("--json", folder))
            self.assertEqual(both["files"], 2)

    def test_damaged_lines_are_skipped(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "reviews.ndjson"
            path.write_text(json.dumps(event(T0, "a")) + "\n{broken\n\n" + json.dumps(event(T0 + 1, "b")) + "\n{\"t\":", encoding="utf-8")
            self.assertEqual(json.loads(self.run_cli("--json", str(path)))["events"], 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
