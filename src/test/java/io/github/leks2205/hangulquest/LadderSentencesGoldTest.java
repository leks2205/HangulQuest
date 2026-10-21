package io.github.leks2205.hangulquest;

import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thirty generated sentences, five goals at the six levels, compared to the byte with
 * {@code ladder-sentences-gold.tsv}: the ending each level uses (-아/어요, -(으)세요, -아/어야 해요) on
 * regular, contracting, ㄹ-dropping, ㄷ- and ㅂ-irregular verbs. The table is written by hand, not by the
 * generator, so a wrong ending cannot become the expectation by accident.
 */
class LadderSentencesGoldTest {
	record Row(String id, int level, Goal goal, String ko, String ending) {
	}

	static List<Row> rows() throws IOException {
		List<Row> rows = new ArrayList<>();
		try (InputStream stream = LadderSentencesGoldTest.class.getResourceAsStream("/ladder-sentences-gold.tsv")) {
			assertNotNull(stream, "ladder-sentences-gold.tsv is missing");
			String[] lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n");
			for (int i = 1; i < lines.length; i++) {
				if (lines[i].isBlank()) {
					continue;
				}
				String[] cells = lines[i].split("\t", -1);
				assertEquals(5, cells.length, "line " + (i + 1));
				Goal goal = QuestLoader.parseGoal(JsonParser.parseString(cells[2]).getAsJsonObject());
				assertNotNull(goal, cells[0] + ": unknown goal");
				rows.add(new Row(cells[0], Integer.parseInt(cells[1]), goal, cells[3], cells[4]));
			}
		}
		return rows;
	}

	@Test
	void thirtySentencesAtTheSixLevels() throws IOException {
		List<Row> rows = rows();
		assertEquals(30, rows.size());
		Set<String> keys = new HashSet<>();
		for (Row row : rows) {
			assertTrue(keys.add(row.id() + "@" + row.level()), "duplicate " + row.id() + " " + row.level());
		}
		assertEquals(Set.of(1, 2, 3, 4, 5, 6), rows.stream().map(Row::level).collect(Collectors.toSet()));
	}

	@Test
	void everySentenceIsTheGoldByte_forByte() throws IOException {
		List<String> wrong = new ArrayList<>();
		for (Row row : rows()) {
			SentenceBuilder.Spoken spoken = SentenceBuilder.spoken(row.goal(), null, List.of(), row.level(), false,
					KoreanFixtures.registryNamer());
			if (!Normalizer.isNormalized(row.ko(), Normalizer.Form.NFC)) {
				wrong.add(row.id() + "@" + row.level() + ": the gold line is not NFC");
			}
			try {
				assertArrayEquals(row.ko().getBytes(StandardCharsets.UTF_8), spoken.ko().getBytes(StandardCharsets.UTF_8));
			} catch (AssertionError different) {
				wrong.add(row.id() + "@" + row.level() + ": expected " + row.ko() + " but got " + spoken.ko());
			}
			if (!spoken.grammar().contains(row.ending())) {
				wrong.add(row.id() + "@" + row.level() + ": expected the ending " + row.ending() + " in " + spoken.grammar());
			}
		}
		assertTrue(wrong.isEmpty(), String.join("\n", wrong));
	}

	@Test
	void theHintStaysAtLevelOne() throws IOException {
		for (Row row : rows()) {
			String hint = SentenceBuilder.hint(row.goal(), null, List.of(), KoreanFixtures.registryNamer());
			String level1 = SentenceBuilder.normal(row.goal(), null, List.of(), 1, false, KoreanFixtures.registryNamer());
			assertEquals(level1.replaceAll(" (한|두|세|네|다섯) (개|마리)", ""), hint.replaceAll(" (한|두|세|네|다섯) (개|마리)", ""),
					row.id() + " hint");
			assertTrue(hint.endsWith("요."), row.id() + " hint is not -아/어요: " + hint);
			assertFalse(hint.contains("세요") || hint.contains("야 해요"), row.id() + " hint is above level 1: " + hint);
		}
	}
}
