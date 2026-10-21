package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExposureLedgerTest {
	private static final long T0 = 1_800_000_000_000L;

	@TempDir
	Path tempDir;

	private LearningService learn;
	private Word stone;

	@BeforeEach
	void setUp() {
		ProgressStore store = new ProgressStore(tempDir, LoggerFactory.getLogger("ledger-test"));
		Concept concept = new Concept();
		concept.id = "stone";
		concept.ko = "돌";
		concept.de = "Stein";
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:stone");
		concept.descKo = "땅에서 나와요.";
		Catalog catalog = Catalog.ofConcepts(List.of(concept));
		learn = new LearningService(store.data(), store, catalog);
		stone = catalog.byId("concept:stone").orElseThrow();
	}

	private WordProgress progress() {
		return learn.data().words.get("concept:stone");
	}

	@Test
	void everyEncounterIsCountedAndDistinctContextsAreKept() {
		ExposureLedger.Context mine = new ExposureLedger.Context("BREAK_BLOCK", "MINE", "primary", "steinpfad");
		ExposureLedger.Context place = new ExposureLedger.Context("PLACE_BLOCK", "PLACE", "primary", "steinpfad");
		ExposureLedger.Context otherTrail = new ExposureLedger.Context("BREAK_BLOCK", "MINE", "primary", "holzpfad");
		learn.expose(stone, T0, mine);
		learn.expose(stone, T0 + 1, mine);
		learn.expose(stone, T0 + 2, place);
		learn.expose(stone, T0 + 3, otherTrail);
		learn.expose(stone, T0 + 4, ExposureLedger.Context.NONE);

		assertEquals(5, ExposureLedger.encounters(progress()));
		assertEquals(3, ExposureLedger.distinctContexts(progress()), "a repeated context counts once, none counts not at all");
		assertEquals(List.of("BREAK_BLOCK|MINE|primary|steinpfad", "PLACE_BLOCK|PLACE|primary|steinpfad",
				"BREAK_BLOCK|MINE|primary|holzpfad"), progress().ledger.contexts);
		assertEquals(0, ExposureLedger.retrievals(progress()), "encounters are not retrievals");
	}

	@Test
	void aContextRoundTripsThroughItsKey() {
		ExposureLedger.Context context = new ExposureLedger.Context("USE_BLOCK", "OPEN", "secondary", "druckerei");
		assertEquals(context, ExposureLedger.Context.parse(context.key()));
		assertEquals(ExposureLedger.Context.NONE, ExposureLedger.Context.parse(ExposureLedger.Context.NONE.key()));
		assertEquals(ExposureLedger.Context.NONE, ExposureLedger.Context.parse(null));
		assertTrue(ExposureLedger.Context.NONE.isNone());
	}

	@Test
	void onlyTheLastTimestampsAreKept() {
		for (int i = 0; i < ExposureLedger.TIMES + 15; i++) {
			learn.expose(stone, T0 + i, ExposureLedger.Context.NONE);
		}
		List<Long> times = progress().ledger.times;
		assertEquals(ExposureLedger.TIMES, times.size());
		assertEquals(T0 + 15, times.get(0), "the oldest ones are dropped");
		assertEquals(T0 + ExposureLedger.TIMES + 14, times.getLast());
		assertEquals(ExposureLedger.TIMES + 15, ExposureLedger.encounters(progress()), "the count is not capped");
	}

	@Test
	void recentEncountersCountsOnlyTheWindow() {
		long day = Srs.DAY;
		learn.expose(stone, T0 - 20 * day, ExposureLedger.Context.NONE);
		learn.expose(stone, T0 - 10 * day, ExposureLedger.Context.NONE);
		learn.expose(stone, T0 - 1 * day, ExposureLedger.Context.NONE);
		assertEquals(2, ExposureLedger.recentEncounters(progress(), T0, 14));
		assertEquals(3, ExposureLedger.recentEncounters(progress(), T0, 30));
	}

	@Test
	void retrievalsAreCountedSeparatelyAndTheLedgerIsSaved() {
		ExposureLedger.Context context = new ExposureLedger.Context("BREAK_BLOCK", "MINE", "primary", "steinpfad");
		learn.expose(stone, T0, context);
		learn.answerMc("concept:stone", 4, true, 900, T0 + 1);
		learn.answerMc("concept:stone", 3, true, 900, T0 + 2);
		assertEquals(3, ExposureLedger.encounters(progress()));
		assertEquals(1, ExposureLedger.retrievals(progress()), "only the four-option answer is a retrieval");

		learn.store().flush();
		ProgressData reloaded = new ProgressStore(tempDir, LoggerFactory.getLogger("ledger-reload")).data();
		ExposureLedger.Entry entry = reloaded.words.get("concept:stone").ledger;
		assertEquals(3, entry.encounters);
		assertEquals(1, entry.retrievals);
		assertEquals(List.of(context.key()), entry.contexts);
		assertEquals(3, entry.times.size());
	}
}
