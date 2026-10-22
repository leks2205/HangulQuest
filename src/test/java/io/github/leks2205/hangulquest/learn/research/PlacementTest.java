package io.github.leks2205.hangulquest.learn.research;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static io.github.leks2205.hangulquest.learn.research.ResearchFixture.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementTest {
	private static final Catalog CATALOG = Catalog.ofConcepts(ConceptLoader.load());
	private static final LexiconTags TAGS = LexiconTags.shared();
	private static final List<String> NONWORDS = Pseudowords.load().stream().map(Pseudowords.Item::ko).toList();

	@TempDir
	Path dir;

	private static Placement build(long seed) {
		return Placement.build(CATALOG.all(), TAGS, NONWORDS, new Random(seed));
	}

	private static Placement toy(int real, int pseudo) {
		List<Placement.Item> items = new ArrayList<>();
		for (int i = 0; i < real; i++) {
			items.add(new Placement.Item("실" + i, "concept:r" + i, true));
		}
		for (int i = 0; i < pseudo; i++) {
			items.add(new Placement.Item("가" + i, null, false));
		}
		return new Placement(items);
	}

	// ------------------------------------------------------------------ the test itself

	@Test
	void itHasFortyEightRealWordsAndTwelvePseudowordsMixedTogether() {
		Placement placement = build(1);
		assertEquals(60, placement.size());
		long real = placement.items().stream().filter(Placement.Item::real).count();
		assertEquals(Placement.REAL_WORDS, real);
		assertEquals(Placement.PSEUDOWORDS, placement.size() - real);
		Set<String> ids = new HashSet<>();
		for (Placement.Item item : placement.items()) {
			if (item.real()) {
				assertTrue(ids.add(item.wordId()), "no word twice");
				assertTrue(CATALOG.byId(item.wordId()).isPresent());
				assertEquals(CATALOG.byId(item.wordId()).get().ko, item.ko());
			} else {
				assertTrue(NONWORDS.contains(item.ko()), item.ko());
				assertEquals(null, item.wordId());
			}
		}
		// the pseudowords are not all at the end: the order is shuffled
		int firstPseudo = 0;
		while (placement.items().get(firstPseudo).real()) {
			firstPseudo++;
		}
		assertTrue(firstPseudo < Placement.REAL_WORDS, "pseudowords are mixed in");
	}

	@Test
	void theRealWordsComeFromAcrossTheCurriculum() {
		int[] bands = new int[4];
		for (Placement.Item item : build(2).items()) {
			if (!item.real()) {
				continue;
			}
			LexiconTags.Tag tag = TAGS.tag(CATALOG.byId(item.wordId()).orElseThrow());
			assertFalse(tag.mcOnly(), "no Minecraft-only words");
			int grade = tag.curriculumGrade();
			bands[grade <= 0 ? 3 : grade <= 2 ? 0 : grade <= 4 ? 1 : 2]++;
		}
		assertEquals(16, bands[0], "beginner");
		assertEquals(14, bands[1], "intermediate");
		assertEquals(10, bands[2], "advanced");
		assertEquals(8, bands[3], "off the curriculum list");
	}

	@Test
	void theSameSeedMakesTheSameTestAndAnotherSeedADifferentOne() {
		assertEquals(build(7).items(), build(7).items());
		assertNotEquals(build(7).items(), build(8).items());
	}

	// ------------------------------------------------------------------ scoring

	@Test
	void theScoreIsHitsMinusFalseAlarms() {
		Placement placement = toy(48, 12);
		// 36 of 48 real words known, 3 of 12 pseudowords claimed
		for (int i = 0; i < 48; i++) {
			placement.answer(i < 36);
		}
		for (int i = 0; i < 12; i++) {
			placement.answer(i < 3);
		}
		Placement.Result result = placement.result();
		assertEquals(36, result.hits());
		assertEquals(3, result.falseAlarms());
		assertEquals(0.75, result.hitRate(), 1e-9);
		assertEquals(0.25, result.falseAlarmRate(), 1e-9);
		assertEquals(0.5, result.score(), 1e-9);
		assertTrue(result.reliable());
		assertTrue(placement.finished());
	}

	@Test
	void sayingYesToEverythingScoresNothing() {
		Placement placement = toy(48, 12);
		for (int i = 0; i < 60; i++) {
			placement.answer(true);
		}
		assertEquals(0.0, placement.result().score(), 1e-9);
		assertFalse(placement.result().reliable());
		assertTrue(placement.claimedWordIds().isEmpty(), "nobody is seeded from answers that mean nothing");
	}

	@Test
	void anHonestPlayerWhoKnowsEverythingScoresOne() {
		Placement placement = toy(48, 12);
		for (int i = 0; i < 48; i++) {
			placement.answer(true);
		}
		for (int i = 0; i < 12; i++) {
			placement.answer(false);
		}
		assertEquals(1.0, placement.result().score(), 1e-9);
		assertEquals(48, placement.claimedWordIds().size());
	}

	@Test
	void theScoreNeverGoesBelowZero() {
		Placement placement = toy(4, 4);
		for (int i = 0; i < 4; i++) {
			placement.answer(false);
		}
		for (int i = 0; i < 4; i++) {
			placement.answer(true);
		}
		assertEquals(0.0, placement.result().score(), 1e-9);
	}

	@Test
	void answersStopAtTheEndAndOnlyRealClaimsAreReturned() {
		Placement placement = toy(2, 1);
		assertEquals("실0", placement.current().ko());
		placement.answer(true);
		placement.answer(false);
		placement.answer(false);
		assertTrue(placement.finished());
		assertEquals(null, placement.current());
		placement.answer(true); // ignored
		assertEquals(3, placement.answered());
		assertEquals(List.of("concept:r0"), placement.claimedWordIds());
	}

	// ------------------------------------------------------------------ seeding the scheduler

	@Test
	void wordsThePlayerClaimsStartWithThreeDaysOfStabilityAndComeBackSoon() {
		ResearchFixture fixture = new ResearchFixture(dir, 10);
		List<String> claimed = fixture.ids.subList(0, 4);
		assertEquals(4, fixture.learn.seedFromPlacement(claimed, T0));

		for (String id : claimed) {
			WordProgress progress = fixture.learn.data().words.get(id);
			assertEquals(Placement.SEED_STABILITY_DAYS, progress.fsrs.stability, 1e-9);
			assertEquals(CardState.State.REVIEW, progress.fsrs.state);
			long days = (progress.fsrs.due - T0) / Srs.DAY;
			assertTrue(days >= 2 && days <= 4, "due in about three days, got " + days);
			assertEquals(progress.fsrs.due, progress.dueAt);
			assertFalse(progress.known, "not trusted: it still has to be verified");
		}
		assertFalse(fixture.learn.data().words.containsKey(fixture.ids.get(5)));
		assertTrue(fixture.learn.data().days.isEmpty(), "placement is not a study day");
		assertEquals(0, fixture.learn.totalReviews());
		assertEquals(0, fixture.learn.dueCount(T0 + 1 * Srs.DAY), "not due the next day");
		assertEquals(4, fixture.learn.dueCount(T0 + 5 * Srs.DAY), "but soon");

		fixture.learn.reviewLog().flush();
		List<ReviewLog.Event> events = ReviewLog.readAll(dir);
		assertEquals(4, events.size());
		assertEquals(ReviewMode.EXPOSURE, events.get(0).mode());
		assertEquals("placement", events.get(0).ctx());
	}

	@Test
	void earnedProgressAndKnownWordsAreNotOverwritten() {
		ResearchFixture fixture = new ResearchFixture(dir, 6);
		String studied = fixture.ids.get(0);
		fixture.reachThreeDays(studied);
		CardState card = fixture.learn.data().words.get(studied).fsrs.copy();
		String known = fixture.ids.get(1);
		fixture.learn.markKnown(known, true, T0);
		CardState knownCard = fixture.learn.data().words.get(known).fsrs.copy();

		int seeded = fixture.learn.seedFromPlacement(List.of(studied, known, "concept:nope", fixture.ids.get(2)), T0 + 9 * Srs.DAY);

		assertEquals(1, seeded, "only the untouched word is seeded");
		assertEquals(card, fixture.learn.data().words.get(studied).fsrs);
		assertEquals(knownCard, fixture.learn.data().words.get(known).fsrs);
		assertTrue(fixture.learn.data().words.get(known).known);
		assertFalse(fixture.learn.data().words.containsKey("concept:nope"));
	}

	@Test
	void aWordMetOnlyInAQuestIsSeededToo() {
		ResearchFixture fixture = new ResearchFixture(dir, 3);
		Word word = fixture.catalog.byId(fixture.ids.get(0)).orElseThrow();
		fixture.learn.introduce(word, T0);
		assertTrue(fixture.learn.data().words.get(word.id).fsrs.isNew());
		assertEquals(1, fixture.learn.seedFromPlacement(List.of(word.id), T0 + 1000));
		assertEquals(Placement.SEED_STABILITY_DAYS, fixture.learn.data().words.get(word.id).fsrs.stability, 1e-9);
	}
}
