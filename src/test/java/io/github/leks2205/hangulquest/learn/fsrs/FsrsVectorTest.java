package io.github.leks2205.hangulquest.learn.fsrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the port against the reference implementation. The vectors were produced by running py-fsrs
 * 6.3.2 (open-spaced-repetition/py-fsrs, {@code fsrs.Scheduler}) with its default FSRS-6 weights,
 * desired retention 0.88, maximum interval 365 days, the default learning steps (1 min, 10 min),
 * relearning step 10 min and fuzzing off. Every row is {@code grade,t(ms since 2026-01-01T12:00Z),
 * state,step,stability,difficulty,due(ms)} after that review; a review that has no step shows "-".
 * The formulas are the ones of the FSRS-6 algorithm page of the open-spaced-repetition wiki.
 */
class FsrsVectorTest {
	private static final double EPS = 1e-9;
	private static final long T0 = 1_767_268_800_000L; // 2026-01-01T12:00:00Z

	private static String[] rows(String name) {
		return switch (name) {
			case "goodChain" -> new String[] {
					"3,0,Learning,1,2.3065,2.118103970459016,600000",
					"3,600000,Review,-,2.3065,2.111214235785395,259800000",
					"3,259800000,Review,-,13.835840133660218,2.1043313908464483,1815000000",
					"3,1815000000,Review,-,65.19710158641902,2.0974554287524403,9245400000",
					"3,9245400000,Review,-,254.0186486063658,2.0905863426205262,38189400000",
					"3,38189400000,Review,-,840.9170220478804,2.083724125574744,69725400000",
			};
			case "mixed" -> new String[] {
					"2,0,Learning,0,1.2931,5.112170705601056,330000",
					"3,330000,Learning,1,1.3358997622047517,5.102286904192293,930000",
					"2,930000,Learning,1,1.3358997622047517,6.733898182869418,1530000",
					"1,1530000,Learning,0,0.4653455794121078,8.91168291149837,1590000",
					"3,1590000,Learning,1,0.5141893917269943,8.89799959788371,2190000",
					"4,2190000,Review,-,0.9710087017124305,8.51499078601202,88590000",
					"3,88590000,Review,-,2.3050806703328037,8.501704164522847,347790000",
			};
			case "againFirst" -> new String[] {
					"1,0,Learning,0,0.212,6.4133,60000",
					"1,60000,Learning,0,0.08335671711031604,8.806304468856837,120000",
					"3,120000,Learning,1,0.10314065007785231,8.792726533684817,720000",
					"3,720000,Review,-,0.12584423732556518,8.77916217644797,87120000",
					"4,87120000,Review,-,1.3490675019804483,8.356443436156297,259920000",
			};
			case "easyFirst" -> new String[] {
					"4,0,Review,-,8.2956,1.0,950400000",
					"3,950400000,Review,-,46.47643995833903,1.0,6220800000",
					"3,6220800000,Review,-,205.90975292394518,1.0,29635200000",
			};
			case "sameDay" -> new String[] {
					"3,0,Learning,1,2.3065,2.118103970459016,600000",
					"3,60000,Review,-,2.3065,2.111214235785395,259260000",
					"1,90000,Relearning,0,0.7750839828558984,7.392238132342694,690000",
					"3,120000,Review,-,0.8281648000472626,7.38007426350719,86520000",
					"3,150000,Review,-,0.8810323149596583,7.3679225585405215,86550000",
			};
			case "late" -> new String[] {
					"3,0,Learning,1,2.3065,2.118103970459016,600000",
					"3,600000,Review,-,2.3065,2.111214235785395,259800000",
					"3,259800000,Review,-,13.835840133660218,2.1043313908464483,1815000000",
					"3,2851800000,Review,-,84.54885391109818,2.0974554287524403,12442200000",
					"3,20131800000,Review,-,420.8106803465024,2.0905863426205262,51667800000",
					"3,51667800000,Review,-,1086.5078522936635,2.083724125574744,83203800000",
			};
			case "cap" -> new String[] {
					"4,0,Review,-,8.2956,1.0,950400000",
					"4,950400000,Review,-,79.80449515797316,1.0,10022400000",
					"4,10022400000,Review,-,549.1399270702651,1.0,41558400000",
					"4,41558400000,Review,-,1976.6509383304099,1.0,73094400000",
					"4,73094400000,Review,-,3361.37850796537,1.0,104630400000",
					"4,104630400000,Review,-,4673.265891842814,1.0,136166400000",
					"4,136166400000,Review,-,5933.006574805005,1.0,167702400000",
					"4,167702400000,Review,-,7153.28705789595,1.0,199238400000",
			};
			case "lapseAndRecover" -> new String[] {
					"3,0,Learning,1,2.3065,2.118103970459016,600000",
					"3,600000,Review,-,2.3065,2.111214235785395,259800000",
					"3,259800000,Review,-,13.835840133660218,2.1043313908464483,1815000000",
					"1,1815000000,Relearning,0,1.7798839285608272,7.389975788014609,1815600000",
					"3,1815600000,Review,-,1.800541197525931,7.377814181523433,1988400000",
					"3,1988400000,Review,-,5.231644686338515,7.365664736638748,2593200000",
					"2,2593200000,Review,-,10.948896301145458,8.236433861454366,3802800000",
					"3,3802800000,Review,-,23.926065648948878,8.22342579688975,6567600000",
			};
			default -> throw new IllegalArgumentException(name);
		};
	}

	private static Fsrs reference() {
		return new Fsrs(FsrsParams.defaults().withFuzz(false));
	}

	@ParameterizedTest
	@ValueSource(strings = {"goodChain", "mixed", "againFirst", "easyFirst", "sameDay", "late", "cap", "lapseAndRecover"})
	void matchesThePythonReference(String name) {
		Fsrs fsrs = reference();
		CardState card = new CardState();
		int index = 0;
		for (String row : rows(name)) {
			String[] f = row.split(",");
			int grade = Integer.parseInt(f[0]);
			long now = T0 + Long.parseLong(f[1]);
			card = fsrs.review(card, grade, now);
			String where = name + " review " + index++ + " (" + row + ") -> " + card;
			assertEquals(CardState.State.valueOf(f[2].toUpperCase()), card.state, where);
			if (!f[3].equals("-")) {
				assertEquals(Integer.parseInt(f[3]), card.step, where);
			}
			assertEquals(Double.parseDouble(f[4]), card.stability, EPS, where);
			assertEquals(Double.parseDouble(f[5]), card.difficulty, EPS, where);
			assertEquals(T0 + Long.parseLong(f[6]), card.due, where);
			assertEquals(now, card.lastReview, where);
		}
	}

	@Test
	void theDefaultsAreTheFsrs6Weights() {
		FsrsParams p = FsrsParams.defaults();
		assertEquals(21, p.weights().length);
		assertEquals(0.212, p.w(0), 0);
		assertEquals(8.2956, p.w(3), 0);
		assertEquals(0.1542, p.w(20), 0);
		assertEquals(0.88, p.desiredRetention(), 0);
		assertEquals(365, p.maximumIntervalDays());
		assertArrayEquals(new long[] {60_000L, 600_000L}, p.learningStepsMs());
		assertArrayEquals(new long[] {600_000L}, p.relearningStepsMs());
		assertTrue(p.fuzz());
	}

	@Test
	void desiredRetentionStaysInsideItsRange() {
		assertEquals(0.80, FsrsParams.defaults().withDesiredRetention(0.5).desiredRetention(), 0);
		assertEquals(0.95, FsrsParams.defaults().withDesiredRetention(0.99).desiredRetention(), 0);
		assertEquals(0.9, FsrsParams.defaults().withDesiredRetention(0.9).desiredRetention(), 0);
		// a higher target retention must schedule earlier
		Fsrs lax = new Fsrs(FsrsParams.defaults().withDesiredRetention(0.80));
		Fsrs strict = new Fsrs(FsrsParams.defaults().withDesiredRetention(0.95));
		assertTrue(lax.intervalDays(10) > strict.intervalDays(10));
	}

	@Test
	void badWeightsAreRejected() {
		double[] wrongLength = new double[20];
		assertThrows(IllegalArgumentException.class, () -> FsrsParams.defaults().withWeights(wrongLength));
		double[] outOfBounds = FsrsParams.defaults().weights();
		outOfBounds[7] = 3.0;
		assertThrows(IllegalArgumentException.class, () -> FsrsParams.defaults().withWeights(outOfBounds));
	}

	@Test
	void theIntervalIsCappedAtTheMaximum() {
		Fsrs fsrs = reference();
		assertEquals(365, fsrs.nextIntervalDays(100_000));
		assertEquals(1, fsrs.nextIntervalDays(0.01));
	}

	@Test
	void aReviewDoesNotChangeTheCardItWasGiven() {
		CardState card = new CardState();
		reference().review(card, 3, T0);
		assertTrue(card.isNew());
		assertEquals(0, card.reps);
	}

	@Test
	void anInvalidGradeIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> reference().review(new CardState(), 0, T0));
		assertThrows(IllegalArgumentException.class, () -> reference().review(new CardState(), 5, T0));
	}

	@Test
	void retrievabilityFallsWithTimeAndIsNinetyPercentAtOneStability() {
		Fsrs fsrs = reference();
		CardState card = fsrs.review(new CardState(), 4, T0); // review state, stability 8.2956
		assertEquals(0.9, fsrs.retrievability(card, T0 + 8 * Fsrs.DAY_MS), 0.01);
		assertTrue(fsrs.retrievability(card, T0 + 30 * Fsrs.DAY_MS) < fsrs.retrievability(card, T0 + 3 * Fsrs.DAY_MS));
		assertEquals(0, fsrs.retrievability(new CardState(), T0), 0);
	}

	private static CardState established() {
		CardState card = new CardState();
		card.state = CardState.State.REVIEW;
		card.stability = 30;
		card.difficulty = 5;
		card.reps = 3;
		card.lastReview = T0;
		card.due = T0 + 30 * Fsrs.DAY_MS;
		return card;
	}

	@Test
	void fuzzIsDeterministicSpreadsIntervalsAndStaysInsideTheReferenceRange() {
		Fsrs fuzzed = Fsrs.defaults();
		Fsrs exact = reference();
		CardState seed = established();
		int changes = 0;
		long previous = -1;
		for (int i = 0; i < 40; i++) {
			long now = T0 + 25L * Fsrs.DAY_MS + i * 3_600_000L;
			CardState a = fuzzed.review(seed, 3, now);
			CardState b = fuzzed.review(seed, 3, now);
			assertEquals(a, b, "the same review must give the same card");
			long plain = (exact.review(seed, 3, now).due - now) / Fsrs.DAY_MS;
			long days = (a.due - now) / Fsrs.DAY_MS;
			double delta = 1 + 0.15 * 4.5 + 0.1 * 13 + 0.05 * Math.max(0, plain - 20);
			assertTrue(Math.abs(days - plain) <= Math.ceil(delta), "fuzz of " + (days - plain) + " days around " + plain);
			assertTrue(days <= 365 && days >= 2);
			if (days != previous) {
				changes++;
			}
			previous = days;
		}
		assertTrue(changes > 3, "fuzz must actually spread the intervals");
	}

	@Test
	void shortIntervalsAreNeverFuzzed() {
		CardState young = established();
		young.stability = 0.1;
		long now = T0 + Fsrs.DAY_MS;
		assertEquals(reference().review(young, 3, now), Fsrs.defaults().review(young, 3, now));
	}
}
