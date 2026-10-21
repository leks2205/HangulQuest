package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes every Korean line a player can see to {@code build/reports/korean/sentences.tsv}, exactly
 * as the quest engine produces it (generated clue and hint text, authored text, finale text), plus
 * the Korean fields of the concept table. {@code tools/lint_korean.py} lints that file, so generated
 * sentences get the same scrutiny as hand-written ones.
 *
 * Columns: id, level, source, ko, goal. The goal column is a compact description of the step's
 * goal so rules can tell "모으다 with a count of 1" from "모으다 for a stack".
 */
class SentenceDumpTest {
	static final Path REPORT = Path.of("build", "reports", "korean", "sentences.tsv");
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("sentence-dump");

	@TempDir
	Path tempDir;

	private static final class Feedback implements QuestEngine.Feedback {
		@Override
		public void wordCard(Word word) {
		}

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void banner(String korean, String german, boolean side) {
		}

		@Override
		public void chooseNext(List<Questline> options) {
		}
	}

	record Row(String id, int level, String source, String ko, String goal) {
	}

	/** Every line, built through the engine's own text paths. */
	static List<Row> collect(Path scratch) {
		Catalog catalog = KoreanFixtures.catalog();
		SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);
		List<Questline> trails = QuestLoader.loadAll();
		ProgressStore store = new ProgressStore(scratch.resolve("progress"), LOG);
		LearningService learn = new LearningService(store.data(), store, catalog);
		// a real player meets the ladder step by step; the dump shows every sentence the content can make
		learn.grammar().unlockAll();
		QuestEngine engine = new QuestEngine(trails, catalog, learn, LOG);
		Feedback feedback = new Feedback();
		Observation start = new Observation();
		start.now = 1000L;
		start.dimension = "minecraft:overworld";

		List<Row> rows = new ArrayList<>();
		for (Questline trail : trails) {
			engine.startTrail(trail.id(), start, feedback);
			String file = "questlines/" + trail.id() + ".json";
			int level = trail.koreanLevel();
			rows.add(new Row("trail." + trail.id() + ".title", level,
					"authored:" + file + "#titleKo", trail.titleKo(), ""));
			for (QuestStep step : trail.allSteps()) {
				boolean finale = "finale".equals(step.id());
				String base = "trail." + trail.id() + "." + step.id();
				String goal = KoreanFixtures.describe(step.goal());
				Verb verb = SentenceBuilder.verbFor(step.goal());
				String path = finale ? "finale.step" : "steps[" + trail.steps().indexOf(step) + "]";

				boolean authoredClue = step.conditions().isEmpty() && step.hasClueKo();
				rows.add(new Row(base + ".clue", level,
						authoredClue ? "authored:" + file + "#" + path + ".clue.ko"
								: "generated:clue/" + step.goal().getClass().getSimpleName() + "/" + verb,
						engine.clueKo(step), goal));

				boolean authoredHint = step.hintKo() != null && !step.hintKo().isBlank();
				rows.add(new Row(base + ".hint", level,
						authoredHint ? "authored:" + file + "#" + path + ".hint.ko"
								: "generated:hint/" + step.goal().getClass().getSimpleName() + "/" + verb,
						engine.hintKo(step), goal));

				if (finale) {
					String ceremony = KoreanFixtures.ceremony(trail);
					boolean generated = ceremony.equals(trail.finaleKo());
					rows.add(new Row("trail." + trail.id() + ".finale", level,
							generated ? "generated:ceremony/" + step.goal().getClass().getSimpleName()
									+ "/" + verb + "@" + file + "#finale.textKo"
									: "authored:" + file + "#finale.textKo",
							trail.finaleKo(), goal));
				}
			}
		}
		for (Concept concept : ConceptLoader.load()) {
			String src = "authored:concepts.json#" + concept.id + ".";
			rows.add(new Row("concept." + concept.id + ".ko", 0, src + "ko", concept.ko, ""));
			if (concept.descKo != null && !concept.descKo.isBlank()) {
				rows.add(new Row("concept." + concept.id + ".descKo", 0, src + "descKo", concept.descKo, ""));
			}
			if (concept.sentenceKo != null && !concept.sentenceKo.isBlank()) {
				rows.add(new Row("concept." + concept.id + ".sentenceKo", 0, src + "sentenceKo",
						concept.sentenceKo, ""));
			}
		}
		return rows;
	}

	private static String cell(String value) {
		return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}

	static void write(List<Row> rows, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		StringBuilder out = new StringBuilder("id\tlevel\tsource\tko\tgoal\n");
		for (Row row : rows) {
			out.append(cell(row.id())).append('\t').append(row.level()).append('\t')
					.append(cell(row.source())).append('\t').append(cell(row.ko())).append('\t')
					.append(cell(row.goal())).append('\n');
		}
		Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
	}

	/**
	 * What the ceremony generator says for every trail, as {@code trail-id<TAB>text}. Input of
	 * tools/apply_ceremony_finales.py, which writes these lines into the questline JSON.
	 */
	static void writeCeremonies(Path target) throws IOException {
		StringBuilder out = new StringBuilder();
		for (Questline trail : QuestLoader.loadAll()) {
			out.append(trail.id()).append('\t').append(KoreanFixtures.ceremony(trail)).append('\n');
		}
		Files.createDirectories(target.getParent());
		Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
	}

	@Test
	void dumpsEveryClueHintAndFinaleLine() throws IOException {
		List<Row> rows = collect(tempDir);
		write(rows, REPORT);
		writeCeremonies(REPORT.resolveSibling("ceremony.tsv"));

		List<Questline> trails = QuestLoader.loadAll();
		int steps = trails.stream().mapToInt(trail -> trail.allSteps().size()).sum();
		long clues = rows.stream().filter(row -> row.id().endsWith(".clue")).count();
		long hints = rows.stream().filter(row -> row.id().endsWith(".hint")).count();
		long finales = rows.stream().filter(row -> row.id().endsWith(".finale")).count();
		assertEquals(steps, clues, "one clue row per step");
		assertEquals(steps, hints, "one hint row per step");
		assertEquals(trails.size(), finales, "one finale row per trail");

		Set<String> ids = new HashSet<>();
		for (Row row : rows) {
			assertTrue(ids.add(row.id()), "duplicate row id " + row.id());
			assertFalse(row.ko() == null || row.ko().isBlank(), row.id() + " has no Korean text");
		}
		assertTrue(Files.size(REPORT) > 100_000, "the report looks too small: " + Files.size(REPORT));
	}

	@Test
	void everyGeneratedFinaleMatchesTheCeremonyGenerator() {
		// Finale text in the JSON is produced by SentenceBuilder.ceremony (tools/apply_ceremony_finales.py
		// writes it): if the generator or the verb table changes, rerun the tool so they agree.
		int generated = 0;
		List<String> stale = new ArrayList<>();
		for (Questline trail : QuestLoader.loadAll()) {
			String ceremony = KoreanFixtures.ceremony(trail);
			if (ceremony.equals(trail.finaleKo())) {
				generated++;
			} else if (trail.finaleKo().contains("라는 길의 의식")) {
				stale.add(trail.id() + ": " + trail.finaleKo() + " != " + ceremony);
			}
		}
		assertTrue(stale.isEmpty(), "finale text still uses the old template instead of the ceremony sentence:\n"
				+ String.join("\n", stale));
		assertTrue(generated > 300, "expected the ~330 generated ceremony finales, got " + generated);
	}
}
