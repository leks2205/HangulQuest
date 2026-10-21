package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.LuteCsv;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.AdaptiveTrailGenerator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Loading must survive odd files, retry politely and keep the daily trail across a restart. */
class RobustLoadingTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("robust-loading-test");
	private static final String CSV = "term;translation;pronunciation;status;tags\n"
			+ "돌;Stein;;1;nomen\n물;Wasser;;2;nomen\n";

	@TempDir
	Path tempDir;

	// ------------------------------------------------------------------ LuteCsv decoding

	private static void assertRows(List<LuteCsv.Row> rows) {
		assertEquals(2, rows.size());
		assertEquals("돌", rows.get(0).term());
		assertEquals("Stein", rows.get(0).translation());
		assertEquals("물", rows.get(1).term());
		assertEquals(2, rows.get(1).status());
	}

	@Test
	void decodesPlainUtf8() throws Exception {
		Path file = tempDir.resolve("utf8.csv");
		Files.write(file, CSV.getBytes(StandardCharsets.UTF_8));
		assertRows(LuteCsv.parseFile(file));
		assertEquals("UTF-8", LuteCsv.decode(CSV.getBytes(StandardCharsets.UTF_8)).charset());
	}

	@Test
	void stripsTheUtf8Bom() throws Exception {
		byte[] body = CSV.getBytes(StandardCharsets.UTF_8);
		byte[] bytes = new byte[body.length + 3];
		bytes[0] = (byte) 0xEF;
		bytes[1] = (byte) 0xBB;
		bytes[2] = (byte) 0xBF;
		System.arraycopy(body, 0, bytes, 3, body.length);
		LuteCsv.Decoded decoded = LuteCsv.decode(bytes);
		assertEquals("UTF-8", decoded.charset());
		assertEquals(CSV, decoded.text(), "no BOM may be left in front of the header");
		Path file = tempDir.resolve("bom.csv");
		Files.write(file, bytes);
		assertRows(LuteCsv.parseFile(file));
	}

	@Test
	void fallsBackToCp949WhenTheBytesAreNotUtf8() throws Exception {
		assumeTrue(Charset.isSupported("x-windows-949"), "this JRE has no CP949");
		byte[] bytes = CSV.getBytes(Charset.forName("x-windows-949"));
		// The strict UTF-8 reader would have thrown on this file.
		assertThrowsMalformed(bytes);
		LuteCsv.Decoded decoded = LuteCsv.decode(bytes);
		assertEquals("x-windows-949", decoded.charset());
		assertEquals(CSV, decoded.text());
		Path file = tempDir.resolve("cp949.csv");
		Files.write(file, bytes);
		assertRows(LuteCsv.parseFile(file));
	}

	@Test
	void fallsBackToWindows1252ForLatinExports() throws Exception {
		String latin = "term;translation;pronunciation;status;tags\nBrot;Brötchen – süß;;1;\n";
		byte[] bytes = latin.getBytes(Charset.forName("windows-1252"));
		assertThrowsMalformed(bytes);
		LuteCsv.Decoded decoded = LuteCsv.decode(bytes);
		// Either legacy decoder may accept these bytes, but the file must not fail to load.
		assertTrue(decoded.charset().equals("windows-1252") || decoded.charset().equals("x-windows-949"));
		if (decoded.charset().equals("windows-1252")) {
			assertEquals(latin, decoded.text());
		}
		Path file = tempDir.resolve("latin.csv");
		Files.write(file, bytes);
		assertEquals(1, LuteCsv.parseFile(file).size());
	}

	@Test
	void bytesNoDecoderAcceptsNeverThrow() throws Exception {
		// 0x81 before ';' is malformed in UTF-8 and CP949 and undefined in windows-1252.
		byte[] head = "term;translation;pronunciation;status;tags\nab".getBytes(StandardCharsets.ISO_8859_1);
		byte[] tail = ";x;;1;\n".getBytes(StandardCharsets.ISO_8859_1);
		byte[] bytes = new byte[head.length + 1 + tail.length];
		System.arraycopy(head, 0, bytes, 0, head.length);
		bytes[head.length] = (byte) 0x81;
		System.arraycopy(tail, 0, bytes, head.length + 1, tail.length);
		assertThrowsMalformed(bytes);
		Path file = tempDir.resolve("broken.csv");
		Files.write(file, bytes);
		assertEquals(1, LuteCsv.parseFile(file).size());
		assertEquals("windows-1252", LuteCsv.decode(bytes).charset());
	}

	@Test
	void decodesUtf16WithBom() throws Exception {
		byte[] body = CSV.getBytes(StandardCharsets.UTF_16LE);
		byte[] bytes = new byte[body.length + 2];
		bytes[0] = (byte) 0xFF;
		bytes[1] = (byte) 0xFE;
		System.arraycopy(body, 0, bytes, 2, body.length);
		LuteCsv.Decoded decoded = LuteCsv.decode(bytes);
		assertEquals("UTF-16LE", decoded.charset());
		assertEquals(CSV, decoded.text());
	}

	private static void assertThrowsMalformed(byte[] bytes) {
		try {
			StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
					.decode(java.nio.ByteBuffer.wrap(bytes));
		} catch (java.nio.charset.CharacterCodingException expected) {
			return;
		}
		throw new AssertionError("the test bytes were valid UTF-8, the fallback is not exercised");
	}

	// ------------------------------------------------------------------ retry schedule

	@Test
	void backoffGrowsAndTellsOnlyTheFirstFailure() {
		LoadBackoff backoff = new LoadBackoff();
		assertTrue(backoff.mayAttempt(0));

		assertTrue(backoff.failed(1_000), "the first failure is the one to log and show");
		assertFalse(backoff.mayAttempt(1_000));
		assertFalse(backoff.mayAttempt(5_999));
		assertTrue(backoff.mayAttempt(6_000), "5 s later");
		assertEquals(5_000, backoff.waitMs(1_000));

		assertFalse(backoff.failed(6_000), "later failures stay quiet");
		assertFalse(backoff.mayAttempt(35_999));
		assertTrue(backoff.mayAttempt(36_000), "30 s later");

		assertFalse(backoff.failed(36_000));
		assertFalse(backoff.mayAttempt(155_999));
		assertTrue(backoff.mayAttempt(156_000), "2 min later");

		for (int i = 0; i < 10; i++) {
			backoff.failed(1_000_000);
		}
		assertEquals(600_000, backoff.waitMs(1_000_000), "the delay stops growing at 10 min");
		assertEquals(13, backoff.failures());
	}

	@Test
	void aSuccessResetsTheBackoff() {
		LoadBackoff backoff = new LoadBackoff();
		backoff.failed(0);
		backoff.failed(5_000);
		backoff.succeeded();
		assertTrue(backoff.mayAttempt(5_001));
		assertEquals(0, backoff.failures());
		assertTrue(backoff.failed(10_000), "a new failure after a success is a first failure again");
		assertEquals(5_000, backoff.waitMs(10_000));
	}

	// ------------------------------------------------------------------ the daily trail

	private static Concept concept(String id, String ko) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = id;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:" + id);
		concept.descKo = "땅에서 나와요.";
		return concept;
	}

	private static Catalog catalog() {
		return Catalog.ofConcepts(List.of(concept("stone", "돌"), concept("dirt", "흙"),
				concept("sand", "모래"), concept("clay", "점토")));
	}

	@Test
	void restoreRebuildsTheSameTrailOnTheSameDay() {
		Path dir = tempDir.resolve("same-day");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = catalog();
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline generated = AdaptiveTrailGenerator.generate(catalog, learn, "overworld@today", 1_000L);
		assertNotNull(generated);

		ProgressData.Adaptive saved = new ProgressData.Adaptive();
		saved.generatedFor = "2026-10-21";
		saved.wordIds = generated.concepts();

		Questline restored = AdaptiveTrailGenerator.restore(catalog, saved, "2026-10-21");
		assertNotNull(restored);
		assertEquals(generated.id(), restored.id());
		assertEquals(generated.concepts(), restored.concepts());
		assertEquals(generated.allSteps().size(), restored.allSteps().size());
		for (int i = 0; i < generated.allSteps().size(); i++) {
			assertEquals(generated.allSteps().get(i), restored.allSteps().get(i),
					"step " + i + " must keep its id and goal");
		}
		assertTrue(restored.adaptive());
	}

	@Test
	void restoreIgnoresAnotherDayAndEmptyData() {
		Catalog catalog = catalog();
		ProgressData.Adaptive saved = new ProgressData.Adaptive();
		saved.generatedFor = "2026-10-20";
		saved.wordIds = List.of("stone", "dirt");
		assertNull(AdaptiveTrailGenerator.restore(catalog, saved, "2026-10-21"));
		assertNull(AdaptiveTrailGenerator.restore(catalog, null, "2026-10-21"));
		saved.generatedFor = "2026-10-21";
		saved.wordIds = new ArrayList<>();
		assertNull(AdaptiveTrailGenerator.restore(catalog, saved, "2026-10-21"));
		saved.wordIds = List.of("no-such-concept");
		assertNull(AdaptiveTrailGenerator.restore(catalog, saved, "2026-10-21"));
	}

	private static Observation breaking(long now, String block) {
		Observation obs = new Observation();
		obs.now = now;
		obs.dimension = "minecraft:overworld";
		obs.brokenBlocks.add(block);
		return obs;
	}

	@Test
	void theDailyTrailKeepsItsProgressAcrossARestart() {
		Path dir = tempDir.resolve("restart");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = catalog();
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline generated = AdaptiveTrailGenerator.generate(catalog, learn, "overworld@today", 1_000L);
		assertNotNull(generated);
		assertTrue(generated.steps().size() >= 2);

		QuestEngine first = new QuestEngine(List.of(generated), catalog, learn, LOG);
		QuestEngine.Feedback quiet = new QuestEngine.Feedback() {
			@Override
			public void banner(String ko, String de, boolean success) {
			}

			@Override
			public void wordCard(io.github.leks2205.hangulquest.data.Word word) {
			}

			@Override
			public void sound(boolean success) {
			}
		};
		Observation start = new Observation();
		start.now = 2_000L;
		start.dimension = "minecraft:overworld";
		first.startTrail("adaptive", start, quiet);
		String firstBlock = ((io.github.leks2205.hangulquest.quest.Goal.BreakBlock)
				generated.steps().get(0).goal()).block();
		// Break enough of the first block to finish step one.
		for (int i = 0; i < 4 && "a1".equals(first.currentStep().id()); i++) {
			first.tick(breaking(2_100L + i, firstBlock), quiet);
		}
		assertEquals("a2", first.currentStep().id(), "the first step should be done");
		store.flush();

		// Restart: the same day, new objects, the saved concepts only.
		ProgressStore reloaded = new ProgressStore(dir, LOG);
		Catalog catalogAgain = catalog();
		LearningService learnAgain = new LearningService(reloaded.data(), reloaded, catalogAgain);
		ProgressData.Adaptive saved = new ProgressData.Adaptive();
		saved.generatedFor = "2026-10-21";
		saved.wordIds = generated.concepts();
		Questline rebuilt = AdaptiveTrailGenerator.restore(catalogAgain, saved, "2026-10-21");
		QuestEngine second = new QuestEngine(List.of(rebuilt), catalogAgain, learnAgain, LOG);
		assertNotNull(second.activeTrail(), "the daily trail was dropped on restart");
		assertEquals("adaptive", second.activeTrail().id());
		assertEquals("a2", second.currentStep().id());

		// Without the rebuilt trail the engine has nothing to restore: the old behaviour.
		ProgressStore bare = new ProgressStore(dir, LOG);
		QuestEngine without = new QuestEngine(List.of(), catalogAgain,
				new LearningService(bare.data(), bare, catalogAgain), LOG);
		assertNull(without.activeTrail());
	}
}
