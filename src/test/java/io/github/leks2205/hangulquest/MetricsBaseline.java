package io.github.leks2205.hangulquest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The content ratchet: {@code content/metrics-baseline.json} holds the value of every content metric,
 * which way is better and the roadmap target. A metric may improve but never get worse. Values are
 * stored with six decimals, so "worse" means worse by more than the rounding of the stored value.
 * To lower the bar on purpose, run {@code tools/variety.py --update-baseline}.
 */
final class MetricsBaseline {
	static final Path FILE = Path.of("content", "metrics-baseline.json");
	/** Values are stored with six decimals; half a unit of the last one is rounding, not a regression. */
	static final double ROUNDING = 5e-7;

	private MetricsBaseline() {
	}

	static JsonObject load() {
		try {
			return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException broken) {
			throw new IllegalStateException(FILE + " is missing or unreadable", broken);
		}
	}

	static JsonObject metrics(JsonObject baseline) {
		return baseline.getAsJsonObject("metrics");
	}

	/** One message per metric that got worse, is missing from the baseline, or is no longer measured. */
	static List<String> regressions(Map<String, Double> actual, JsonObject baseline, String prefix) {
		JsonObject recorded = metrics(baseline);
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, Double> entry : actual.entrySet()) {
			if (!entry.getKey().startsWith(prefix)) {
				continue;
			}
			JsonElement element = recorded.get(entry.getKey());
			if (element == null) {
				problems.add(entry.getKey() + " is not in the baseline (run tools/variety.py --update-baseline)");
				continue;
			}
			JsonObject spec = element.getAsJsonObject();
			double allowed = spec.get("value").getAsDouble();
			boolean lower = "lower".equals(spec.get("better").getAsString());
			double value = entry.getValue();
			if (lower ? value > allowed + ROUNDING : value < allowed - ROUNDING) {
				problems.add(String.format("%s got worse: %s, baseline %s (%s is better)", entry.getKey(),
						format(value), format(allowed), spec.get("better").getAsString()));
			}
		}
		for (String key : recorded.keySet()) {
			if (key.startsWith(prefix) && !actual.containsKey(key)) {
				problems.add(key + " is in the baseline but no longer measured");
			}
		}
		return problems;
	}

	/** The distance to the roadmap target for every metric, one line each. */
	static String gapReport(Map<String, Double> actual, JsonObject baseline, String prefix) {
		JsonObject recorded = metrics(baseline);
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, Double> entry : actual.entrySet()) {
			if (!entry.getKey().startsWith(prefix)) {
				continue;
			}
			JsonElement element = recorded.get(entry.getKey());
			if (element == null) {
				continue;
			}
			JsonObject spec = element.getAsJsonObject();
			double target = spec.get("target").getAsDouble();
			boolean lower = "lower".equals(spec.get("better").getAsString());
			double gap = lower ? entry.getValue() - target : target - entry.getValue();
			out.append(String.format("  %-48s %10s  baseline %10s  target %s%s  %s%n", entry.getKey(),
					format(entry.getValue()), format(spec.get("value").getAsDouble()), lower ? "<= " : ">= ",
					format(target), gap <= ROUNDING ? "met" : "gap " + format(gap)));
		}
		return out.toString();
	}

	static String format(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.4f", value);
	}
}
