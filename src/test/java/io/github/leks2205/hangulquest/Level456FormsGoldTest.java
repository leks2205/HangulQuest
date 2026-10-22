package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.grammar.Conjugator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forms of levels 4 to 6 (합쇼체, 반말, -자, -아/어라 and the connectives of the upper levels) on regular verbs and
 * on every irregular class, compared with {@code level456-forms-gold.tsv}. The table is written by hand, not by the
 * generator, so a wrong form cannot become the expectation by accident. A "-" cell means the form does not exist
 * for that word (an adjective has no -자, -아/어라 or -(으)러).
 */
class Level456FormsGoldTest {
	private static final List<Function<Conjugator.Word, String>> FORMS = List.of(
			Conjugator.Word::hapnida, Conjugator.Word::hapnikka, Conjugator.Word::hapnidaPast,
			Conjugator.Word::banmal, Conjugator.Word::banmalPast, Conjugator.Word::ja, Conjugator.Word::eora,
			Conjugator.Word::nikka, Conjugator.Word::jiman, Conjugator.Word::eureo, Conjugator.Word::ado,
			Conjugator.Word::neunDongan, Conjugator.Word::eulsurok, Conjugator.Word::deusi,
			Conjugator.Word::giMaryeon, Conjugator.Word::suBakke, Conjugator.Word::eulgeyo);

	private static List<String[]> rows() throws IOException {
		try (InputStream stream = Level456FormsGoldTest.class.getResourceAsStream("/level456-forms-gold.tsv")) {
			assertNotNull(stream, "level456-forms-gold.tsv is missing");
			List<String[]> rows = new ArrayList<>();
			String[] lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n");
			for (int i = 1; i < lines.length; i++) {
				if (!lines[i].isBlank()) {
					String[] cells = lines[i].split("\t", -1);
					assertEquals(FORMS.size() + 2, cells.length, "line " + (i + 1));
					rows.add(cells);
				}
			}
			return rows;
		}
	}

	@Test
	void theTableCoversEveryIrregularClassAndAdjectives() throws IOException {
		List<String[]> rows = rows();
		assertTrue(rows.size() >= 24, "only " + rows.size() + " rows");
		String notes = String.join(" ", rows.stream().map(row -> row[row.length - 1]).toList());
		for (String kind : List.of("regular", "hada", "rieul_drop", "d_irregular", "b_irregular", "s_irregular",
				"eu_drop", "reu_irregular", "adjective", "existential")) {
			assertTrue(notes.contains(kind.replace('_', ' ')), kind + " is missing from the table");
		}
	}

	@Test
	void everyFormIsTheGoldByte_forByte() throws IOException {
		List<String> wrong = new ArrayList<>();
		for (String[] row : rows()) {
			Conjugator.Word word = Conjugator.of(row[0]);
			for (int i = 0; i < FORMS.size(); i++) {
				String expected = row[i + 1];
				if (expected.equals("-")) {
					continue;
				}
				if (!Normalizer.isNormalized(expected, Normalizer.Form.NFC)) {
					wrong.add(row[0] + ": the gold cell " + expected + " is not NFC");
				}
				String made = FORMS.get(i).apply(word);
				if (!expected.equals(made)) {
					wrong.add(row[0] + " column " + (i + 1) + ": expected " + expected + " but got " + made);
				}
			}
		}
		assertTrue(wrong.isEmpty(), String.join("\n", wrong));
	}

	@Test
	void anAdjectiveSpeaksItsWhileClauseAsAState() {
		assertEquals("큰 동안", Conjugator.of("크다").neunDongan());
		assertEquals("먹는 동안", Conjugator.of("먹다").neunDongan());
		assertEquals("있는 동안", Conjugator.of("있다").neunDongan());
	}
}
