package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verb variety ratchet. For every Korean level, the verb used most often may not take a larger share
 * of that level's steps than the baseline in {@code src/test/resources/content-baseline.json}
 * ("verbShareTop"). The numbers are printed on every run; when a content change improves a level,
 * lower the baseline so it cannot slide back. The long-term target is 25% (roadmap), the first
 * milestone 35%.
 */
class VerbVarietyTest {
	private static final double EPSILON = 1e-9;

	/** level -> {verb -> steps}. */
	static Map<Integer, Map<Verb, Integer>> counts() {
		Map<Integer, Map<Verb, Integer>> byLevel = new TreeMap<>();
		for (Questline trail : QuestLoader.loadAll()) {
			for (QuestStep step : trail.allSteps()) {
				Verb verb = SentenceBuilder.verbFor(step.goal());
				byLevel.computeIfAbsent(trail.koreanLevel(), level -> new EnumMap<>(Verb.class))
						.merge(verb, 1, Integer::sum);
			}
		}
		return byLevel;
	}

	record Top(Verb verb, double share, int steps) {
	}

	static Top top(Map<Verb, Integer> counts) {
		int total = counts.values().stream().mapToInt(Integer::intValue).sum();
		Verb best = null;
		int bestCount = -1;
		for (var entry : counts.entrySet()) {
			if (entry.getValue() > bestCount) {
				best = entry.getKey();
				bestCount = entry.getValue();
			}
		}
		return new Top(best, (double) bestCount / total, total);
	}

	/** Levels whose top verb got a bigger share than the baseline allows, one message each. */
	static List<String> regressions(Map<Integer, Map<Verb, Integer>> counts, JsonObject baseline) {
		List<String> worse = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			Top top = top(entry.getValue());
			double allowed = baseline.get(String.valueOf(entry.getKey())).getAsDouble();
			if (top.share() > allowed + EPSILON) {
				worse.add(String.format("level %d got more monotonous: %s %.2f%% > baseline %.2f%%",
						entry.getKey(), top.verb().dictionary(), top.share() * 100, allowed * 100));
			}
		}
		return worse;
	}

	static JsonObject baseline() {
		try (InputStream stream = VerbVarietyTest.class.getResourceAsStream("/content-baseline.json")) {
			assertTrue(stream != null, "src/test/resources/content-baseline.json is missing");
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("verbShareTop");
			}
		} catch (java.io.IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	@Test
	void noLevelIsMoreMonotonousThanItsBaseline() {
		JsonObject baseline = baseline();
		StringBuilder report = new StringBuilder("verb variety (share of the most used verb per level):\n");
		for (var entry : counts().entrySet()) {
			Top top = top(entry.getValue());
			report.append(String.format("  level %d: %s %.2f%% of %d steps (baseline %.2f%%, %d verbs in use)%n",
					entry.getKey(), top.verb().dictionary(), top.share() * 100, top.steps(),
					baseline.get(String.valueOf(entry.getKey())).getAsDouble() * 100, entry.getValue().size()));
		}
		System.out.print(report);
		List<String> worse = regressions(counts(), baseline);
		assertTrue(worse.isEmpty(), "verb variety got worse:\n" + String.join("\n", worse));
	}

	@Test
	void theBaselineCoversEveryLevelAndNoLevelExceedsTheFirstMilestone() {
		JsonObject baseline = baseline();
		for (int level : counts().keySet()) {
			assertTrue(baseline.has(String.valueOf(level)), "baseline is missing level " + level);
			assertTrue(baseline.get(String.valueOf(level)).getAsDouble() <= 0.35,
					"level " + level + " baseline is above the 35% milestone");
		}
	}

	@Test
	void theRatchetCanFail() {
		JsonObject baseline = JsonParser.parseString("{\"1\": 0.30}").getAsJsonObject();
		Map<Verb, Integer> monotone = new EnumMap<>(Verb.class);
		monotone.put(Verb.PLACE, 10);
		assertEquals(1, regressions(Map.of(1, monotone), baseline).size());

		Map<Verb, Integer> varied = new EnumMap<>(Verb.class);
		for (Verb verb : new Verb[]{Verb.PLACE, Verb.MINE, Verb.CHOP, Verb.COLLECT}) {
			varied.put(verb, 5);
		}
		assertEquals(0.25, top(varied).share(), 1e-9);
		assertEquals(0, regressions(Map.of(1, varied), baseline).size());

		varied.merge(Verb.PLACE, 5, Integer::sum);
		assertEquals(1, regressions(Map.of(1, varied), baseline).size(), "a 10-of-25 level is above 30%");
	}
}
