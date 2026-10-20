package de.zirks.hangulquest;

import de.zirks.hangulquest.data.Concept;
import de.zirks.hangulquest.data.LuteCsv;
import de.zirks.hangulquest.data.LuteImport;
import de.zirks.hangulquest.data.Word;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuteImportTest {
	private static Concept stone() {
		Concept concept = new Concept();
		concept.id = "stone";
		concept.ko = "돌";
		concept.de = "Stein";
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:stone");
		concept.descKo = "땅에서 나와요.";
		concept.aliases = List.of("돌");
		return concept;
	}

	@Test
	void skipsStatusZeroAndMapsConcepts() {
		List<LuteCsv.Row> rows = List.of(
				new LuteCsv.Row("돌", "Stein", "", 1, "nomen"),
				new LuteCsv.Row("HIGH", "", "", 0, ""),
				new LuteCsv.Row("사과", "Apfel \\n Beispiel", "", 5, "nomen"),
				new LuteCsv.Row("전화를", "Telefon", "", 99, "nomen"),
				new LuteCsv.Row("물 좀 주세요", "Wasser bitte", "", 1, "ausdruck")
		);
		LuteImport.Result result = LuteImport.importRows(rows, List.of(stone()));
		assertTrue(result.skipped() >= 1);
		assertEquals(1, result.known().size());
		assertTrue(result.known().contains("lute:전화를"));
		assertEquals(1, result.phrases());
		for (Word word : result.words()) {
			if (word.ko.equals("돌")) {
				assertEquals("concept:stone", word.id);
				assertEquals("땅에서 나와요.", word.descKo);
			}
		}
		Map<String, Integer> levels = result.levels();
		assertEquals(Integer.valueOf(5), levels.get("lute:사과"));
		assertEquals(Integer.valueOf(0), levels.get("concept:stone"));
		assertFalse(levels.containsKey("lute:HIGH"));
	}

	@Test
	void deduplicatesKeepingHighestStatus() {
		List<LuteCsv.Row> rows = List.of(
				new LuteCsv.Row("사과", "Apfel", "", 1, ""),
				new LuteCsv.Row("사과", "Apfel", "", 4, "")
		);
		LuteImport.Result result = LuteImport.importRows(rows, List.of());
		assertEquals(1, result.words().size());
		assertEquals(Integer.valueOf(4), result.levels().get("lute:사과"));
	}
}
