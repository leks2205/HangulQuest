package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.culture.CultureNote;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.research.Pseudowords;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.story.ChapterSet;
import io.github.leks2205.hangulquest.story.Story;
import io.github.leks2205.hangulquest.story.StoryFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

	static final class Feedback implements QuestEngine.Feedback {

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
				// the verb the sentence really uses: a lead on a rideable animal says 묶다, not 타다
				Word taught = step.teach().isEmpty() ? null : catalog.byConceptId(step.teach().get(0)).orElse(null);
				Verb verb = SentenceBuilder.verbOf(step.goal(),
						taught != null && taught.category == Category.ACTIONS && taught.ko.endsWith("다") ? taught : null,
						step.conditions());
				String path = finale ? "finale.step" : "steps[" + trail.steps().indexOf(step) + "]";

				boolean authoredClue = step.hasClueKo();
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
		// The placement test's nonwords: native reviewers confirm that none of them is a real word.
		List<Pseudowords.Item> nonwords = Pseudowords.load();
		for (int i = 0; i < nonwords.size(); i++) {
			rows.add(new Row(nonwords.get(i).id(), 0, "nonword:pseudowords.json#words[" + i + "].ko",
					nonwords.get(i).ko(), ""));
		}
		rows.addAll(dialogueRows());
		rows.addAll(libraryRows());
		return rows;
	}

	/**
	 * The Korean text of the Library: every story paragraph and title, the summary of every culture note and the
	 * title of every chapter. The goal column says what the line is, so the reviewers see where it appears.
	 */
	static List<Row> libraryRows() {
		List<Row> rows = new ArrayList<>();
		for (Story story : StoryFixtures.shipped()) {
			String file = "stories/" + story.chapter() + "/" + story.id() + ".json";
			rows.add(new Row("story." + story.id() + ".title", story.level(), "authored:" + file + "#titleKo",
					story.titleKo(), "Story title, chapter " + story.chapter()));
			for (int i = 0; i < story.paragraphs().size(); i++) {
				rows.add(new Row("story." + story.id() + ".p" + (i + 1), story.level(),
						"authored:" + file + "#paragraphs[" + i + "]", story.paragraphs().get(i),
						"Story paragraph " + (i + 1) + " of " + story.paragraphs().size() + ", chapter " + story.chapter()
								+ ", graded text: 98% known words, grammar of levels 1 to 2 only"));
			}
		}
		List<CultureNote> notes = StoryFixtures.culture().notes();
		for (int i = 0; i < notes.size(); i++) {
			rows.add(new Row("culture." + notes.get(i).id() + ".ko", 2, "authored:culture.json#notes[" + i + "].ko",
					notes.get(i).ko(), "Culture note summary (the explanation is in the interface language)"));
		}
		List<ChapterSet.Chapter> chapters = StoryFixtures.chapters().all();
		for (int i = 0; i < chapters.size(); i++) {
			rows.add(new Row("chapter." + chapters.get(i).id() + ".title", chapters.get(i).minLevel(),
					"authored:chapters.json#chapters[" + i + "].titleKo", chapters.get(i).titleKo(),
					"Chapter title in the quest tab"));
		}
		return rows;
	}

	/**
	 * Every line of the villager conversations: what the NPC says and every reply the player can pick,
	 * with the speaker and speech level in the goal column (the registry shows them to the reviewers).
	 */
	static List<Row> dialogueRows() {
		List<Row> rows = new ArrayList<>();
		for (Dialogue dialogue : DialogueFixtures.shipped()) {
			String file = "dialogue/" + dialogue.id() + ".json";
			for (Dialogue.Node node : DialogueFixtures.sorted(dialogue).values()) {
				String id = "dialogue." + dialogue.id() + "." + node.id();
				rows.add(new Row(id, dialogue.level(), "authored:" + file + "#nodes." + node.id() + ".ko", node.ko(),
						"Dialogue " + node.speaker().id() + " " + node.speechLevel().id()));
				for (int i = 0; i < node.choices().size(); i++) {
					Dialogue.Choice choice = node.choices().get(i);
					String grade = choice.correct() == null ? "ungraded" : choice.correct() ? "right" : "wrong";
					rows.add(new Row(id + ".c" + (i + 1), dialogue.level(),
							"authored:" + file + "#nodes." + node.id() + ".choices[" + i + "].ko", choice.ko(),
							"Dialogue player " + choice.speechLevel().id() + " " + grade));
				}
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
	void theDumpHasEveryStoryParagraphCultureSummaryAndChapterTitle() {
		List<Row> rows = libraryRows();
		Set<String> ids = new HashSet<>();
		rows.forEach(row -> ids.add(row.id()));
		for (Story story : StoryFixtures.shipped()) {
			assertTrue(ids.contains("story." + story.id() + ".title"), story.id());
			for (int i = 1; i <= story.paragraphs().size(); i++) {
				assertTrue(ids.contains("story." + story.id() + ".p" + i), story.id() + " p" + i);
			}
		}
		StoryFixtures.culture().notes().forEach(note -> assertTrue(ids.contains("culture." + note.id() + ".ko"), note.id()));
		StoryFixtures.chapters().all().forEach(chapter -> assertTrue(ids.contains("chapter." + chapter.id() + ".title"), chapter.id()));
		assertEquals(rows.size(), ids.size(), "ids are unique");
		for (Row row : rows) {
			assertFalse(row.ko().isBlank(), row.id());
		}
	}

	/** The finale lines of the batches' dual review, {@code trail.<id>.finale} to the Korean text that was reviewed. */
	static Map<String, String> reviewedFinales() throws IOException {
		Map<String, String> reviewed = new HashMap<>();
		try (var batches = Files.newDirectoryStream(Path.of("content", "batches"))) {
			for (Path batch : batches) {
				Path lines = batch.resolve("korean-lines.tsv");
				if (!Files.isRegularFile(lines)) {
					continue;
				}
				for (String line : Files.readAllLines(lines, StandardCharsets.UTF_8)) {
					String[] cells = line.split("\t");
					if (cells.length >= 2 && cells[0].endsWith(".finale")) {
						reviewed.put(cells[0], cells[1]);
					}
				}
			}
		}
		return reviewed;
	}

	/** Trails whose finale was written by hand before the batch review existed; the list only shrinks. */
	static Set<String> legacyAuthoredFinales() throws IOException {
		Set<String> ids = new HashSet<>();
		try (var stream = SentenceDumpTest.class.getResourceAsStream("/legacy-authored-finales.txt")) {
			assertTrue(stream != null, "legacy-authored-finales.txt is missing");
			for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				if (!line.isBlank() && !line.startsWith("#")) {
					ids.add(line.trim());
				}
			}
		}
		return ids;
	}

	/**
	 * Finale text in the JSON is produced by SentenceBuilder.ceremony (tools/apply_ceremony_finales.py writes
	 * it): if the generator or the verb table changes, rerun the tool so they agree. A finale that differs from
	 * the generator is authored, and that is allowed when it went through the dual review of its batch (it is a
	 * line of that batch's korean-lines.tsv, as it stands) or when it is a "Title - sentence" finale. No finale
	 * may be the old broken template.
	 */
	@Test
	void anAuthoredFinaleWentThroughAReviewOrCarriesATitle() throws IOException {
		Map<String, String> reviewed = reviewedFinales();
		Set<String> legacy = legacyAuthoredFinales();
		List<String> broken = new ArrayList<>();
		List<String> unreviewed = new ArrayList<>();
		for (Questline trail : QuestLoader.loadAll()) {
			String finale = trail.finaleKo();
			if (finale.contains("라는 길의 의식")) {
				broken.add(trail.id() + ": " + finale + " != " + KoreanFixtures.ceremony(trail));
			} else if (!finale.equals(KoreanFixtures.ceremony(trail))
					&& !finale.equals(reviewed.get("trail." + trail.id() + ".finale"))
					&& !finale.contains(" \u2014 ") && !legacy.contains(trail.id())) {
				unreviewed.add(trail.id() + ": " + finale);
			}
		}
		assertTrue(broken.isEmpty(), "finale text still uses the old template instead of the ceremony sentence:\n"
				+ String.join("\n", broken));
		assertTrue(unreviewed.isEmpty(), "authored finales that neither the generator, a batch review "
				+ "(korean-lines.tsv) nor a \"Title \u2014 sentence\" form stands behind:\n" + String.join("\n", unreviewed));
	}

	@Test
	void theLegacyFinaleListNamesRealTrailsAndNothingNew() throws IOException {
		Set<String> ids = new HashSet<>();
		QuestLoader.loadAll().forEach(trail -> ids.add(trail.id()));
		Set<String> legacy = legacyAuthoredFinales();
		assertTrue(ids.containsAll(legacy), "legacy finales of trails that do not exist: " + legacy.stream()
				.filter(id -> !ids.contains(id)).toList());
		assertTrue(legacy.size() <= 41, "the legacy list may only shrink, it has " + legacy.size());
	}

	@Test
	void everyFinaleTheDumpCallsGeneratedEqualsTheGeneratorOutput(@TempDir Path scratch) {
		Map<String, String> ceremonies = new HashMap<>();
		for (Questline trail : QuestLoader.loadAll()) {
			ceremonies.put("trail." + trail.id() + ".finale", KoreanFixtures.ceremony(trail));
		}
		int generated = 0;
		for (Row row : collect(scratch)) {
			if (row.source().startsWith("generated:ceremony/")) {
				generated++;
				assertEquals(ceremonies.get(row.id()), row.ko(), row.id() + " is labelled generated");
			} else if (row.id().endsWith(".finale")) {
				assertTrue(row.source().startsWith("authored:"), row.id() + ": " + row.source());
				assertFalse(row.ko().equals(ceremonies.get(row.id())), row.id() + " equals the generator but is not labelled so");
			}
		}
		assertTrue(generated > 0, "no generated finale at all");
	}
}
