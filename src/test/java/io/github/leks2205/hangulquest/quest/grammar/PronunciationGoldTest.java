package io.github.leks2205.hangulquest.quest.grammar;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * pronunciation-gold.tsv: word, bracket form, rule ids (comma separated, in {@link Pronunciation.Rule} order)
 * and an optional fourth column "sino" for Sino-Korean words. Built by hand from the 표준 발음법 and read by a
 * native speaker later; every row must come out of {@link Pronunciation} exactly.
 */
class PronunciationGoldTest {
	private static final String GOLD = "/pronunciation-gold.tsv";

	private record Row(int line, String word, String bracket, String rules, boolean sino) {
	}

	private static List<Row> rows() throws IOException {
		List<Row> rows = new ArrayList<>();
		try (InputStream stream = PronunciationGoldTest.class.getResourceAsStream(GOLD)) {
			assertTrue(stream != null, GOLD + " is missing");
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			String text;
			int line = 0;
			while ((text = reader.readLine()) != null) {
				line++;
				if (text.isBlank() || text.startsWith("#")) {
					continue;
				}
				String[] f = text.split("\t", -1);
				assertTrue(f.length == 3 || f.length == 4, GOLD + ":" + line + " needs 3 or 4 columns, has " + f.length);
				rows.add(new Row(line, f[0], f[1], f[2], f.length == 4 && f[3].equals("sino")));
			}
		}
		return rows;
	}

	@Test
	void theTableIsBigEnoughAndWellFormed() throws IOException {
		List<Row> rows = rows();
		assertTrue(rows.size() >= 150, "only " + rows.size() + " rows");
		Set<String> seen = new HashSet<>();
		for (Row row : rows) {
			String where = GOLD + ":" + row.line();
			assertTrue(row.bracket().startsWith("[") && row.bracket().endsWith("]"), where + " bracket form");
			assertTrue(seen.add(row.word() + (row.sino() ? "/sino" : "")), where + " duplicate " + row.word());
			if (!row.rules().isEmpty()) {
				for (String id : row.rules().split(",")) {
					assertTrue(Pronunciation.Rule.byId(id) != null, where + " unknown rule " + id);
				}
			}
		}
	}

	@Test
	void everyRowComesOutExactly() throws IOException {
		List<String> wrong = new ArrayList<>();
		for (Row row : rows()) {
			Pronunciation.Result result = Pronunciation.of(row.word(), row.sino());
			String got = "[" + result.pron() + "]";
			if (!got.equals(row.bracket()) || !result.ruleIds().equals(row.rules())) {
				wrong.add(row.word() + ": got " + got + " " + result.ruleIds() + ", gold " + row.bracket() + " "
						+ row.rules());
			}
		}
		assertTrue(wrong.isEmpty(), wrong.size() + " rows differ:\n" + String.join("\n", wrong));
	}

	@Test
	void everyRuleHasRowsInTheTable() throws IOException {
		Set<Pronunciation.Rule> covered = EnumSet.noneOf(Pronunciation.Rule.class);
		for (Row row : rows()) {
			if (!row.rules().isEmpty()) {
				for (String id : row.rules().split(",")) {
					covered.add(Pronunciation.Rule.byId(id));
				}
			}
		}
		assertEquals(EnumSet.allOf(Pronunciation.Rule.class), covered);
	}

	@Test
	void everyRuleHasEnoughRowsAndPlainWordsStayPlain() throws IOException {
		int[] perRule = new int[Pronunciation.Rule.values().length];
		int plain = 0;
		for (Row row : rows()) {
			if (row.rules().isEmpty()) {
				plain++;
				assertEquals("[" + row.word() + "]", row.bracket(), row.word() + " has no rule but changes");
			}
			for (String id : row.rules().isEmpty() ? new String[0] : row.rules().split(",")) {
				perRule[Pronunciation.Rule.byId(id).ordinal()]++;
			}
		}
		for (Pronunciation.Rule rule : Pronunciation.Rule.values()) {
			assertTrue(perRule[rule.ordinal()] >= 4, rule + " has " + perRule[rule.ordinal()] + " rows");
		}
		assertTrue(plain >= 10, "the table needs words that stay as written, has " + plain);
	}
}
