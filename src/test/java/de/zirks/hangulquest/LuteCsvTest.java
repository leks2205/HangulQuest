package de.zirks.hangulquest;

import de.zirks.hangulquest.data.LuteCsv;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuteCsvTest {
	@Test
	void parsesQuotedFieldsAndMultiLineValues() {
		String csv = """
				term;translation;pronunciation;status;tags
				돌;Stein;;1;nomen
				"<a>물 좀 주세요</a>";"Kann ich Wasser haben?";;1;ausdruck
				HIGH;;;0;
				""";
		List<LuteCsv.Row> rows = LuteCsv.parse(csv);
		assertEquals(3, rows.size());
		assertEquals("돌", rows.get(0).term());
		assertEquals(1, rows.get(0).status());
		assertEquals("nomen", rows.get(0).tags());
		assertTrue(rows.get(1).term().contains("물 좀 주세요"));
		assertEquals("Kann ich Wasser haben?", rows.get(1).translation());
		assertEquals(0, rows.get(2).status());
	}

	@Test
	void handlesBomAndSemicolonsInsideQuotes() {
		String csv = "\uFEFFterm;translation;pronunciation;status;tags\n"
				+ "\"a;b\";\"x;y\";;;\"nomen;;verb\"\n";
		List<LuteCsv.Row> rows = LuteCsv.parse(csv);
		assertEquals(1, rows.size());
		assertEquals("a;b", rows.get(0).term());
		assertEquals("x;y", rows.get(0).translation());
		assertEquals("nomen;;verb", rows.get(0).tags());
	}
}
