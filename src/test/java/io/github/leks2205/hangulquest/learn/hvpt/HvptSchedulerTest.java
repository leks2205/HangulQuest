package io.github.leks2205.hangulquest.learn.hvpt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HvptSchedulerTest {
	private final HvptSet set = HvptSet.shared();
	/** A pack that has every sound of every voice. */
	private final Predicate<String> everything = event -> true;

	private static Map<String, Integer> count(List<HvptTrial> trials, java.util.function.Function<HvptTrial, String> key) {
		Map<String, Integer> counts = new HashMap<>();
		trials.forEach(trial -> counts.merge(key.apply(trial), 1, Integer::sum));
		return counts;
	}

	private static int spread(Map<String, Integer> counts) {
		return counts.values().stream().mapToInt(Integer::intValue).max().orElse(0)
				- counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
	}

	@Test
	void aSessionHasSixtyTrialsAndEveryContrastTheSameShare() {
		for (long seed = 0; seed < 30; seed++) {
			List<HvptTrial> trials = HvptScheduler.build(set, HvptPhase.TRAIN, everything, new Random(seed));
			assertEquals(60, trials.size());
			Map<String, Integer> byContrast = count(trials, HvptTrial::contrast);
			assertEquals(set.contrasts().size(), byContrast.size());
			for (int share : byContrast.values()) {
				assertEquals(10, share, "seed " + seed);
			}
		}
	}

	@Test
	void contrastsDoNotRunInARow() {
		List<HvptTrial> trials = HvptScheduler.build(set, HvptPhase.TRAIN, everything, new Random(5));
		for (int i = 1; i < trials.size(); i++) {
			assertNotEquals(trials.get(i - 1).contrast(), trials.get(i).contrast(), "trial " + i);
		}
	}

	@Test
	void formsAreAskedForInTurnWithinAContrast() {
		for (long seed = 0; seed < 20; seed++) {
			List<HvptTrial> trials = HvptScheduler.build(set, HvptPhase.TRAIN, everything, new Random(seed));
			for (HvptSet.Contrast contrast : set.contrasts()) {
				Map<String, Integer> targets = count(trials.stream().filter(t -> t.contrast().equals(contrast.id())).toList(),
						HvptTrial::target);
				assertTrue(targets.size() >= 2, contrast.id());
				assertTrue(spread(targets) <= 2, contrast.id() + " " + targets + " seed " + seed);
			}
		}
	}

	@Test
	void voicesRotateAndShareTheTrialsEvenly() {
		List<HvptTrial> trials = HvptScheduler.build(set, HvptPhase.TRAIN, everything, new Random(11));
		for (int i = 1; i < trials.size(); i++) {
			assertNotEquals(trials.get(i - 1).voice(), trials.get(i).voice(), "same voice twice at " + i);
		}
		Map<String, Integer> byVoice = count(trials, HvptTrial::voice);
		assertEquals(Set.copyOf(set.trainingVoices()), byVoice.keySet());
		assertTrue(spread(byVoice) <= 1, byVoice.toString());
	}

	@Test
	void heldOutVoicesNeverAppearInTraining() {
		Set<String> held = Set.copyOf(set.heldOutVoices());
		for (long seed = 0; seed < 100; seed++) {
			for (HvptTrial trial : HvptScheduler.build(set, HvptPhase.TRAIN, everything, new Random(seed))) {
				assertFalse(held.contains(trial.voice()), trial.voice() + " seed " + seed);
			}
		}
	}

	@Test
	void theTestsUseOnlyHeldOutVoicesAndOnlyTestItems() {
		Set<String> training = Set.copyOf(set.trainingVoices());
		for (HvptPhase phase : List.of(HvptPhase.PRE, HvptPhase.POST)) {
			for (long seed = 0; seed < 30; seed++) {
				List<HvptTrial> trials = HvptScheduler.build(set, phase, everything, new Random(seed));
				assertEquals(30, trials.size());
				for (HvptTrial trial : trials) {
					assertFalse(training.contains(trial.voice()), trial.voice());
					assertTrue(set.heldOutVoices().contains(trial.voice()));
					assertTrue(set.item(trial.itemId()).test(), trial.itemId());
				}
				count(trials, HvptTrial::contrast).values().forEach(n -> assertEquals(5, n));
			}
		}
	}

	@Test
	void thePostTestIsThePreTestInANewOrder() {
		List<HvptTrial> pre = HvptScheduler.build(set, HvptPhase.PRE, everything, new Random(1));
		List<HvptTrial> post = HvptScheduler.build(set, HvptPhase.POST, everything, new Random(2));
		assertEquals(Set.copyOf(pre), Set.copyOf(post), "same items, targets and voices");
		assertNotEquals(pre, post, "but not in the same order");
		assertEquals(30, Set.copyOf(pre).size(), "every trial of a test is different");
	}

	@Test
	void trialsOnlyUseSoundsThePackHas() {
		Set<String> present = new HashSet<>();
		for (HvptSet.Item item : set.items()) {
			for (HvptSet.Form form : item.forms()) {
				for (String voice : set.trainingVoices()) {
					// the pack lacks every sound of one voice and the aspirated forms of one other
					if (!voice.equals("sohee") && !(voice.equals("male1") && form.key().equals("a"))) {
						present.add(HvptSet.event(item.id(), form.key(), voice));
					}
				}
			}
		}
		List<HvptTrial> trials = HvptScheduler.build(set, HvptPhase.TRAIN, present::contains, new Random(3));
		assertEquals(60, trials.size());
		for (HvptTrial trial : trials) {
			assertNotEquals("sohee", trial.voice());
			for (String form : trial.options()) {
				assertTrue(present.contains(trial.event(form)), trial.event(form) + " is not in the pack");
			}
		}
	}

	@Test
	void anEmptyPackGivesNoTrials() {
		assertEquals(List.of(), HvptScheduler.build(set, HvptPhase.TRAIN, event -> false, new Random(1)));
		assertEquals(List.of(), HvptScheduler.build(set, HvptPhase.PRE, event -> false, new Random(1)));
	}

	@Test
	void aPackWithOnlyTrainingVoicesCannotRunTheTests() {
		Predicate<String> trainingOnly = event -> set.trainingVoices().stream().anyMatch(voice -> event.endsWith("." + voice));
		assertEquals(List.of(), HvptScheduler.build(set, HvptPhase.PRE, trainingOnly, new Random(1)));
		assertEquals(60, HvptScheduler.build(set, HvptPhase.TRAIN, trainingOnly, new Random(1)).size());
	}

	@Test
	void aContrastWithoutSoundsIsLeftOutAndTheRestShareTheSession() {
		Predicate<String> noVowels = event -> !event.startsWith("hvpt.vo_");
		List<HvptTrial> trials = HvptScheduler.build(set, HvptPhase.TRAIN, noVowels, new Random(4));
		assertEquals(60, trials.size());
		Map<String, Integer> byContrast = count(trials, HvptTrial::contrast);
		assertFalse(byContrast.containsKey("vo"));
		byContrast.values().forEach(n -> assertEquals(12, n));
	}

	@Test
	void everyTrialHasTwoOrThreeOptionsAndItsTargetIsOneOfThem() {
		List<HvptTrial> trials = new ArrayList<>(HvptScheduler.build(set, HvptPhase.TRAIN, everything, new Random(8)));
		for (HvptTrial trial : trials) {
			assertTrue(trial.options().size() >= 2 && trial.options().size() <= 3);
			assertTrue(trial.options().contains(trial.target()));
			assertEquals(set.item(trial.itemId()).forms().size(), trial.options().size());
		}
	}

	@Test
	void everyItemComesUpOverSeveralSessions() {
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 12; seed++) {
			HvptScheduler.build(set, HvptPhase.TRAIN, everything, new Random(seed)).forEach(t -> seen.add(t.itemId()));
		}
		assertEquals(set.items().size(), seen.size());
	}

	@Test
	void aThinPackLetsTheTestBorrowOtherItemsOfAContrast() {
		// only one test item of the bilabial contrast has sounds, but other items do
		Predicate<String> thin = event -> !event.startsWith("hvpt.bp_") || event.startsWith("hvpt.bp_s3.")
				|| event.startsWith("hvpt.bp_s4.") || event.startsWith("hvpt.bp_w1.");
		List<HvptTrial> trials = HvptScheduler.build(set, HvptPhase.PRE, thin, new Random(1));
		Set<String> items = new HashSet<>();
		trials.stream().filter(t -> t.contrast().equals("bp")).forEach(t -> items.add(t.itemId()));
		assertTrue(items.size() >= 3, items.toString());
		assertEquals(30, trials.size());
		trials.forEach(t -> assertTrue(set.heldOutVoices().contains(t.voice())));
	}
}
