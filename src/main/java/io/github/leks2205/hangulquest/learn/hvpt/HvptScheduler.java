package io.github.leks2205.hangulquest.learn.hvpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Builds the trials of one run.
 *
 * <ul>
 *   <li><b>Contrasts are balanced:</b> every contrast gets the same number of trials, give or take one, and
 *       the run never plays two trials of one contrast in a row while another is left.</li>
 *   <li><b>Targets are balanced:</b> inside a contrast the form that is asked for is the least asked so far, so
 *       tense, plain and aspirated come up equally often.</li>
 *   <li><b>Voices rotate:</b> each trial takes the least used voice that has the item, never the voice of the trial
 *       before when another one will do.</li>
 *   <li><b>Held-out voices stay out of training:</b> {@link HvptPhase#TRAIN} uses the training voices only, the
 *       tests the held-out ones only.</li>
 *   <li><b>Pre- and post-test are the same test:</b> the same items, targets and voices, drawn from a fixed seed,
 *       only in a new order each time.</li>
 * </ul>
 *
 * Only what the voices pack really has is used: an item in a voice needs the sound of every one of its forms,
 * so the options always come from one speaker.
 */
public final class HvptScheduler {
	/** Fixes the draw of the tests, so that pre and post compare like with like. */
	static final long TEST_SEED = 0x50F0D6EL;

	private HvptScheduler() {
	}

	/** The trials of a run, or an empty list when the pack has nothing to play for this phase. */
	public static List<HvptTrial> build(HvptSet set, HvptPhase phase, Predicate<String> has, Random random) {
		Random draw = phase.test() ? new Random(TEST_SEED) : random;
		Map<String, Map<HvptSet.Item, List<String>>> usable = usable(set, phase, has);
		List<String> contrasts = new ArrayList<>(usable.keySet());
		if (contrasts.isEmpty()) {
			return List.of();
		}
		int total = phase.test() ? set.params().testTrials() : set.params().trials();
		Map<String, Integer> quota = quotas(contrasts, total, draw);

		Map<String, List<Pick>> picks = new LinkedHashMap<>();
		for (String contrast : contrasts) {
			picks.put(contrast, picksFor(usable.get(contrast), quota.get(contrast), draw));
		}
		List<Pick> ordered = interleave(picks, draw);
		List<HvptTrial> trials = assignVoices(ordered, usable, draw);
		if (phase.test()) {
			Collections.shuffle(trials, random);
		}
		return trials;
	}

	private record Pick(String contrast, HvptSet.Item item, String target) {
	}

	/** A test contrast with fewer playable test items than this borrows the other items of the contrast. */
	static final int MIN_TEST_ITEMS = 3;

	/** Contrast to the items that can be played, each with the voices that have every form of it. */
	private static Map<String, Map<HvptSet.Item, List<String>>> usable(HvptSet set, HvptPhase phase, Predicate<String> has) {
		Map<String, Map<HvptSet.Item, List<String>>> usable = new LinkedHashMap<>();
		for (HvptSet.Contrast contrast : set.contrasts()) {
			Map<HvptSet.Item, List<String>> items = playable(set, phase, has, contrast, phase.test());
			if (phase.test() && items.size() < MIN_TEST_ITEMS) {
				// a thin pack: the test borrows the contrast's other items rather than ask one item over and over
				items = playable(set, phase, has, contrast, false);
			}
			if (!items.isEmpty()) {
				usable.put(contrast.id(), items);
			}
		}
		return usable;
	}

	private static Map<HvptSet.Item, List<String>> playable(HvptSet set, HvptPhase phase, Predicate<String> has,
															HvptSet.Contrast contrast, boolean testItemsOnly) {
		Map<HvptSet.Item, List<String>> items = new LinkedHashMap<>();
		for (HvptSet.Item item : set.itemsOf(contrast.id())) {
			if (testItemsOnly && !item.test()) {
				continue;
			}
			List<String> voices = new ArrayList<>();
			for (String voice : set.voicesFor(phase)) {
				if (item.forms().stream().allMatch(form -> has.test(HvptSet.event(item.id(), form.key(), voice)))) {
					voices.add(voice);
				}
			}
			if (!voices.isEmpty()) {
				items.put(item, voices);
			}
		}
		return items;
	}

	private static Map<String, Integer> quotas(List<String> contrasts, int total, Random draw) {
		int base = total / contrasts.size();
		int extra = total % contrasts.size();
		List<String> order = new ArrayList<>(contrasts);
		Collections.shuffle(order, draw);
		Map<String, Integer> quota = new HashMap<>();
		for (int i = 0; i < order.size(); i++) {
			quota.put(order.get(i), base + (i < extra ? 1 : 0));
		}
		return quota;
	}

	/** Items in a shuffled cycle, so each comes up as evenly as the count allows; the target is the least asked form. */
	private static List<Pick> picksFor(Map<HvptSet.Item, List<String>> items, int count, Random draw) {
		List<HvptSet.Item> pool = new ArrayList<>(items.keySet());
		Map<String, Integer> asked = new HashMap<>();
		List<Pick> picks = new ArrayList<>();
		List<HvptSet.Item> queue = new ArrayList<>();
		HvptSet.Item last = null;
		while (picks.size() < count) {
			if (queue.isEmpty()) {
				queue.addAll(pool);
				Collections.shuffle(queue, draw);
				if (pool.size() > 1 && queue.get(0) == last) {
					Collections.swap(queue, 0, queue.size() - 1);
				}
			}
			HvptSet.Item item = queue.remove(0);
			last = item;
			picks.add(new Pick(item.contrast(), item, leastAsked(item, asked, draw)));
		}
		return picks;
	}

	private static String leastAsked(HvptSet.Item item, Map<String, Integer> asked, Random draw) {
		List<String> best = new ArrayList<>();
		int fewest = Integer.MAX_VALUE;
		for (HvptSet.Form form : item.forms()) {
			int count = asked.getOrDefault(form.key(), 0);
			if (count < fewest) {
				fewest = count;
				best.clear();
			}
			if (count == fewest) {
				best.add(form.key());
			}
		}
		String target = best.get(draw.nextInt(best.size()));
		asked.merge(target, 1, Integer::sum);
		return target;
	}

	/** Round robin over the contrasts, longest queue first, never the same contrast twice while another is left. */
	private static List<Pick> interleave(Map<String, List<Pick>> picks, Random draw) {
		Map<String, List<Pick>> left = new LinkedHashMap<>();
		picks.forEach((contrast, list) -> left.put(contrast, new ArrayList<>(list)));
		List<Pick> ordered = new ArrayList<>();
		String previous = null;
		while (!left.isEmpty()) {
			int most = 0;
			for (Map.Entry<String, List<Pick>> entry : left.entrySet()) {
				if (!entry.getKey().equals(previous) || left.size() == 1) {
					most = Math.max(most, entry.getValue().size());
				}
			}
			List<String> candidates = new ArrayList<>();
			for (Map.Entry<String, List<Pick>> entry : left.entrySet()) {
				if (entry.getValue().size() == most && (!entry.getKey().equals(previous) || left.size() == 1)) {
					candidates.add(entry.getKey());
				}
			}
			String next = candidates.get(draw.nextInt(candidates.size()));
			List<Pick> queue = left.get(next);
			ordered.add(queue.remove(0));
			if (queue.isEmpty()) {
				left.remove(next);
			}
			previous = next;
		}
		return ordered;
	}

	private static List<HvptTrial> assignVoices(List<Pick> ordered, Map<String, Map<HvptSet.Item, List<String>>> usable,
												Random draw) {
		Map<String, Integer> used = new HashMap<>();
		List<HvptTrial> trials = new ArrayList<>();
		String previous = null;
		for (Pick pick : ordered) {
			List<String> voices = usable.get(pick.contrast()).get(pick.item());
			List<String> allowed = new ArrayList<>(voices);
			if (allowed.size() > 1) {
				allowed.remove(previous);
			}
			int fewest = allowed.stream().mapToInt(voice -> used.getOrDefault(voice, 0)).min().orElse(0);
			List<String> best = allowed.stream().filter(voice -> used.getOrDefault(voice, 0) == fewest).toList();
			String voice = best.get(draw.nextInt(best.size()));
			used.merge(voice, 1, Integer::sum);
			previous = voice;
			trials.add(new HvptTrial(pick.contrast(), pick.item().id(), pick.target(), voice,
					pick.item().forms().stream().map(HvptSet.Form::key).toList()));
		}
		return trials;
	}
}
