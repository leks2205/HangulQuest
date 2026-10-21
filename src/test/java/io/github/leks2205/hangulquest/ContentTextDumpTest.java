package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes the rendered English and German text of every trail, step, finale and word to
 * {@code build/reports/content-text/<code>.tsv}, as the game shows it. The files are the evidence for
 * a content move: dump before and after, and {@code cmp} must say they are the same
 * (see {@code docs/decisions/ADR-0001-content-text-to-lang-keys.md}).
 *
 * Columns: kind, id, field, text. Tabs and line breaks in the text are escaped.
 */
class ContentTextDumpTest {
	static final Path REPORT_DIR = Path.of("build", "reports", "content-text");
	private static final List<String> LANGUAGES = List.of("en_us", "de_de", "fr_fr");

	@AfterEach
	void resetLang() {
		Lang.set("EN");
	}

	static String esc(String text) {
		if (text == null) {
			return "<null>";
		}
		return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
	}

	private static void row(List<String> rows, String kind, String id, String field, String text) {
		rows.add(kind + "\t" + id + "\t" + field + "\t" + esc(text));
	}

	private static void step(List<String> rows, String id, QuestStep step, Lang lang) {
		row(rows, "step", id, "clue", step.clue(lang));
		row(rows, "step", id, "hint", step.hint(lang));
		row(rows, "step", id, "clueEn", step.clueEn());
		row(rows, "step", id, "clueDe", step.clueDe());
		row(rows, "step", id, "hintEn", step.hintEn());
		row(rows, "step", id, "hintDe", step.hintDe());
	}

	/** One language's lines, sorted by trail id and in file order inside a trail. */
	static List<String> render(String code) {
		Lang lang = Lang.of(code);
		Lang.set(code);
		List<String> rows = new ArrayList<>();
		List<Questline> trails = new ArrayList<>(QuestLoader.loadAll());
		trails.sort(Comparator.comparing(Questline::id));
		for (Questline trail : trails) {
			String id = trail.id();
			row(rows, "trail", id, "title", trail.title(lang));
			row(rows, "trail", id, "titleEn", trail.titleEn());
			row(rows, "trail", id, "titleDe", trail.titleDe());
			row(rows, "trail", id, "titleEnRaw", trail.titleEnRaw());
			row(rows, "trail", id, "finale", trail.finale(lang));
			row(rows, "trail", id, "finaleEn", trail.finaleEn());
			row(rows, "trail", id, "finaleDe", trail.finaleDe());
			row(rows, "trail", id, "finaleEnRaw", trail.finaleEnRaw());
			row(rows, "trail", id, "topic", trail.topicKey());
			for (QuestStep step : trail.steps()) {
				step(rows, id + "/" + step.id(), step, lang);
			}
			step(rows, id + "/finale", trail.finale(), lang);
		}
		for (Concept concept : ConceptLoader.load()) {
			Word word = concept.toWord();
			String id = concept.id;
			row(rows, "word", id, "gloss", word.gloss(lang));
			row(rows, "word", id, "glossFull", word.glossFull(lang));
			row(rows, "word", id, "de", word.de);
			row(rows, "word", id, "en", word.en);
			row(rows, "word", id, "deShort", word.deShort);
			row(rows, "word", id, "untranslated", String.valueOf(word.untranslated));
			row(rows, "word", id, "sentenceDe", concept.sentenceDe);
			row(rows, "word", id, "sentenceEn", concept.sentenceEn);
		}
		return rows;
	}

	@Test
	void writesTheRenderedTextOfEveryLanguage() throws IOException {
		Files.createDirectories(REPORT_DIR);
		for (String code : LANGUAGES) {
			List<String> rows = render(code);
			assertTrue(rows.size() > 20_000, code + ": only " + rows.size() + " lines");
			Files.write(REPORT_DIR.resolve(code + ".tsv"), rows, StandardCharsets.UTF_8);
		}
	}
}
