package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPlannerTest {
	private static final long T0 = 5_000_000L;

	// ------------------------------------------------------------------ the pure rules

	private static Word word(String id) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = "돌";
		concept.de = id;
		concept.en = id;
		concept.items = List.of("minecraft:stone");
		return concept.toWord();
	}

	private static SessionPlanner.Due fresh(String id, String set) {
		return new SessionPlanner.Due(word(id), set, true, 1, T0, 1, false);
	}

	private static SessionPlanner.Due review(String id, double retrievability) {
		return new SessionPlanner.Due(word(id), "set-" + id, false, retrievability, T0, 1, false);
	}

	private static List<String> ids(List<Word> words) {
		return words.stream().map(w -> w.id.substring("concept:".length())).toList();
	}

	private static List<Boolean> outcomes(int right, int wrong) {
		List<Boolean> out = new ArrayList<>();
		for (int i = 0; i < right; i++) {
			out.add(true);
		}
		for (int i = 0; i < wrong; i++) {
			out.add(false);
		}
		return out;
	}

	@Test
	void reviewsComeFirstMostForgottenFirstThenNewWords() {
		List<Word> ranked = SessionPlanner.rank(List.of(fresh("n1", "a"), review("r1", 0.9), review("r2", 0.4),
				review("r3", 0.7)), 5, Set.of(), 10);

		assertEquals(List.of("r2", "r3", "r1", "n1"), ids(ranked));
	}

	@Test
	void newWordsStopAtTheBudget() {
		List<SessionPlanner.Due> due = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			due.add(fresh("n" + i, "set" + i));
		}

		assertEquals(10, SessionPlanner.rank(due, 10, Set.of(), 50).size());
		assertEquals(3, SessionPlanner.rank(due, 3, Set.of(), 50).size());
		assertEquals(0, SessionPlanner.rank(due, 0, Set.of(), 50).size());
	}

	@Test
	void atMostOneNewWordPerSemanticSet() {
		List<Word> ranked = SessionPlanner.rank(List.of(fresh("a1", "animal"), fresh("a2", "animal"),
				fresh("a3", "animal"), fresh("b1", "block"), fresh("b2", "block"), fresh("c1", "food")), 10, Set.of(), 50);

		assertEquals(3, ranked.size());
		Set<String> sets = new HashSet<>();
		for (Word word : ranked) {
			assertTrue(sets.add(word.id.substring(8, 9)), "two words from one set: " + ids(ranked));
		}
	}

	@Test
	void aSetThatAlreadyGotANewWordThisSessionIsSkipped() {
		List<Word> ranked = SessionPlanner.rank(List.of(fresh("a1", "animal"), fresh("b1", "block")), 10,
				Set.of("animal"), 50);

		assertEquals(List.of("b1"), ids(ranked));
	}

	@Test
	void reviewsAreNotLimitedByTheNewWordRules() {
		List<SessionPlanner.Due> due = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			due.add(review("r" + i, 0.5 + i / 100.0));
		}
		assertEquals(30, SessionPlanner.rank(due, 0, Set.of(), 100).size());
		assertEquals(7, SessionPlanner.rank(due, 0, Set.of(), 7).size());
	}

	@Test
	void easierWordsAreIntroducedFirstAndMinecraftOnlyWordsLast() {
		SessionPlanner.Due hard = new SessionPlanner.Due(word("hard"), "s1", true, 1, T0, 5, false);
		SessionPlanner.Due easy = new SessionPlanner.Due(word("easy"), "s2", true, 1, T0, 1, false);
		SessionPlanner.Due mc = new SessionPlanner.Due(word("mc"), "s3", true, 1, T0, 1, true);
		SessionPlanner.Due untagged = new SessionPlanner.Due(word("untagged"), "s4", true, 1, T0, 0, false);

		assertEquals(List.of("easy", "hard", "untagged", "mc"),
				ids(SessionPlanner.rank(List.of(mc, untagged, hard, easy), 10, Set.of(), 10)));
	}

	// ------------------------------------------------------------------ the 85% controller

	@Test
	void theBudgetStaysWhenTheRateIsInTheMiddleOrThereAreFewSamples() {
		assertEquals(10, SessionPlanner.adjustedBudget(10, outcomes(17, 3)), "85%");
		assertEquals(10, SessionPlanner.adjustedBudget(10, outcomes(16, 4)), "exactly 80% is not below");
		assertEquals(10, SessionPlanner.adjustedBudget(10, outcomes(0, 9)), "nine answers say nothing yet");
		assertEquals(10, SessionPlanner.adjustedBudget(10, List.of()));
	}

	@Test
	void underEightyPercentGivesFewerNewWords() {
		assertEquals(5, SessionPlanner.adjustedBudget(10, outcomes(15, 5)));
		assertEquals(2, SessionPlanner.adjustedBudget(5, outcomes(14, 6)));
		assertEquals(0, SessionPlanner.adjustedBudget(10, outcomes(11, 9)), "55% is a review day");
	}

	@Test
	void overNinetyTwoPercentAllowsMoreNewWords() {
		assertEquals(15, SessionPlanner.adjustedBudget(10, outcomes(20, 0)));
		assertEquals(15, SessionPlanner.adjustedBudget(10, outcomes(19, 1)), "95%");
		assertEquals(10, SessionPlanner.adjustedBudget(10, outcomes(18, 2)), "90% is not above 92%");
		assertEquals(23, SessionPlanner.adjustedBudget(15, outcomes(20, 0)));
	}

	@Test
	void onlyTheNewestTwentyOutcomesCount() {
		List<Boolean> old = outcomes(0, 30);
		old.addAll(outcomes(20, 0));
		assertEquals(15, SessionPlanner.adjustedBudget(10, old));
		List<Boolean> lately = outcomes(30, 0);
		lately.addAll(outcomes(0, 20));
		assertEquals(0, SessionPlanner.adjustedBudget(10, lately));
	}

	@Test
	void newWordsLeftSubtractsTodaysNewWordsAndNeverGoesBelowZero() {
		assertEquals(7, SessionPlanner.newWordsLeft(10, List.of(), 3));
		assertEquals(0, SessionPlanner.newWordsLeft(10, List.of(), 12));
		assertEquals(5, SessionPlanner.newWordsLeft(12, List.of(), 5), "12 is not an offered choice and counts as 10");
	}

	// ------------------------------------------------------------------ with the learning service

	@TempDir
	Path dir;

	private Catalog catalog;
	private LearningService learn;
	private final Map<String, List<Word>> bySet = new HashMap<>();

	@BeforeEach
	void setUp() {
		List<Concept> concepts = ConceptLoader.load();
		catalog = Catalog.ofConcepts(concepts);
		ProgressStore store = new ProgressStore(dir, LoggerFactory.getLogger("planner-test"));
		learn = new LearningService(store.data(), store, catalog);
		LexiconTags tags = LexiconTags.shared();
		for (Word word : catalog.all()) {
			LexiconTags.Tag tag = tags.tag(word);
			if (tag != null && tag.semSet() != null && !tag.mcOnly()) {
				bySet.computeIfAbsent(tag.semSet(), key -> new ArrayList<>()).add(word);
			}
		}
	}

	/** One word from each of the first {@code count} sets that have at least {@code size} words. */
	private List<Word> wordsFromDifferentSets(int count) {
		List<Word> out = new ArrayList<>();
		for (List<Word> words : bySet.values()) {
			if (out.size() < count) {
				out.add(words.get(0));
			}
		}
		assertEquals(count, out.size(), "not enough semantic sets in the data");
		return out;
	}

	private Set<String> setsOf(List<Word> words) {
		Set<String> sets = new HashSet<>();
		for (Word word : words) {
			assertTrue(sets.add(LexiconTags.shared().semSet(word)), "two words from one set");
		}
		return sets;
	}

	@Test
	void theServiceOffersAtMostTheDailyBudgetOfNewWords() {
		for (Word word : wordsFromDifferentSets(14)) {
			learn.expose(word, T0);
		}

		assertEquals(10, learn.dueForPlay(T0, 100).size(), "the default is ten");
		learn.configure(0.88, 5);
		assertEquals(5, learn.dueForPlay(T0, 100).size());
		learn.configure(0.88, 15);
		assertEquals(14, learn.dueForPlay(T0, 100).size());
	}

	@Test
	void theServiceOffersAtMostOneNewWordPerSemanticSet() {
		List<Word> sameSet = null;
		for (List<Word> words : bySet.values()) {
			if (words.size() >= 3) {
				sameSet = words;
				break;
			}
		}
		for (Word word : sameSet.subList(0, 3)) {
			learn.expose(word, T0);
		}

		assertEquals(1, learn.dueForPlay(T0, 10).size());
	}

	@Test
	void gradingANewWordUsesUpTheBudgetAndItsSetForTheSession() {
		List<Word> words = wordsFromDifferentSets(12);
		List<Word> sameSet = null;
		for (List<Word> candidates : bySet.values()) {
			if (candidates.size() >= 2) {
				sameSet = candidates;
				break;
			}
		}
		for (Word word : words) {
			learn.expose(word, T0);
		}
		learn.expose(sameSet.get(1), T0);
		Word first = sameSet.get(0);
		learn.expose(first, T0);
		int before = learn.newWordsLeft(T0);

		learn.answerMc(first.id, 4, true, 1000, T0 + 1000);

		assertEquals(before - 1, learn.newWordsLeft(T0 + 1000));
		for (Word offered : learn.dueForPlay(T0 + 2000, 100)) {
			assertNotEquals(sameSet.get(1).id, offered.id, "a second new word from the same set was offered this session");
		}
	}

	@Test
	void aReviewedWordNeverCountsAsNewAgain() {
		Word word = wordsFromDifferentSets(1).get(0);
		learn.expose(word, T0);
		learn.answerMc(word.id, 4, true, 1000, T0);
		int left = learn.newWordsLeft(T0);

		learn.answerMc(word.id, 4, true, 1000, T0 + 1);
		learn.answerMc(word.id, 4, false, 1000, T0 + 2);

		assertEquals(left, learn.newWordsLeft(T0 + 2));
	}

	@Test
	void theControllerReadsTheLastTwentyGradedReviews() {
		Word word = wordsFromDifferentSets(1).get(0);
		learn.expose(word, T0);
		for (int i = 0; i < 20; i++) {
			learn.answerMc(word.id, 4, true, 1000, T0 + i);
		}
		assertEquals(15 - 1, learn.newWordsLeft(T0 + 20), "above 92%: half as many again, minus today's new word");

		for (int i = 0; i < 6; i++) {
			learn.answerMc(word.id, 4, false, 1000, T0 + 30 + i);
		}
		assertEquals(5 - 1, learn.newWordsLeft(T0 + 40), "14 of 20 is 70%: half the budget");
	}

	@Test
	void twoOptionAnswersDoNotFeedTheController() {
		Word word = wordsFromDifferentSets(1).get(0);
		learn.expose(word, T0);
		for (int i = 0; i < 20; i++) {
			learn.answerMc(word.id, 2, false, 1000, T0 + i);
		}
		assertEquals(10, learn.newWordsLeft(T0 + 20), "guesses among two options prove nothing either way");
	}

	@Test
	void dueForPlayRanksTheMostForgottenReviewFirst() {
		List<Word> words = wordsFromDifferentSets(2);
		Word old = words.get(0);
		Word recent = words.get(1);
		learn.expose(old, T0);
		learn.expose(recent, T0);
		long day = Srs.DAY;
		learn.answerMc(old.id, 4, true, 1000, T0);
		learn.answerMc(recent.id, 4, true, 1000, T0);
		// 'recent' is reviewed again later, so its card has more stability and is less forgotten when both are due.
		learn.answerMc(recent.id, 4, true, 1000, T0 + day);
		learn.answerMc(recent.id, 4, true, 1000, T0 + 3 * day);
		long later = T0 + 60 * day;

		List<Word> ranked = learn.dueForPlay(later, 10);

		assertEquals(old.id, ranked.get(0).id);
		assertEquals(recent.id, ranked.get(1).id);
		assertEquals(2, setsOf(ranked).size());
	}
}
