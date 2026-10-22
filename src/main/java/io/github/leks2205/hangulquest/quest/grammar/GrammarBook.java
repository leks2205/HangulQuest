package io.github.leks2205.hangulquest.quest.grammar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.Lang;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The constructions of grammar.json as the game reads them: the rule card, the examples and the cloze
 * sentences of every construction, in ladder order. Pure data; the ladder and the learner's progress
 * live elsewhere.
 */
public final class GrammarBook {
	public static final String RESOURCE = "/assets/hangulquest/data/grammar.json";

	/** The two-line rule card; the Korean lines are examples, the others the rule in a language. */
	public record RuleCard(List<String> ko, List<String> en, List<String> de) {
		/** The rule lines in the interface language: German for German, English for every other. */
		public List<String> lines(Lang lang) {
			return lang == Lang.DE && !de.isEmpty() ? de : en;
		}
	}

	public record Example(String ko, String en) {
	}

	/** A sentence with a gap. {@code accepted} holds the answer and every other right fill. */
	public record Cloze(String sentence, String gloss, String answer, List<String> accepted,
						List<String> distractors) {
		/** The sentence with the gap filled by the answer. */
		public String filled() {
			return sentence.replace("___", answer);
		}
	}

	public record Construction(String id, String form, int level, List<String> prereq, RuleCard ruleCard,
							   List<Example> examples, List<Cloze> clozes, String generator) {
	}

	private static volatile GrammarBook shared;

	private final Map<String, Construction> byId;

	GrammarBook(Map<String, Construction> byId) {
		this.byId = byId;
	}

	public static GrammarBook shared() {
		GrammarBook book = shared;
		if (book == null) {
			book = load();
			shared = book;
		}
		return book;
	}

	private static GrammarBook load() {
		try (InputStream stream = GrammarBook.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException(RESOURCE + " is missing");
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(reader).getAsJsonObject());
			}
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	public static GrammarBook parse(String json) {
		return parse(JsonParser.parseString(json).getAsJsonObject());
	}

	static GrammarBook parse(JsonObject root) {
		Map<String, Construction> map = new LinkedHashMap<>();
		for (JsonElement element : root.getAsJsonArray("constructions")) {
			JsonObject c = element.getAsJsonObject();
			JsonObject card = c.getAsJsonObject("ruleCard");
			RuleCard ruleCard = new RuleCard(strings(card, "ko"), strings(card, "en"), strings(card, "de"));
			List<Example> examples = new ArrayList<>();
			for (JsonElement example : c.getAsJsonArray("examples")) {
				JsonObject e = example.getAsJsonObject();
				examples.add(new Example(e.get("ko").getAsString(), e.get("en").getAsString()));
			}
			List<Cloze> clozes = new ArrayList<>();
			for (JsonElement cloze : c.getAsJsonArray("clozeTemplates")) {
				JsonObject t = cloze.getAsJsonObject();
				String answer = t.get("answer").getAsString();
				List<String> accepted = new ArrayList<>(List.of(answer));
				accepted.addAll(strings(t, "alsoAccept"));
				clozes.add(new Cloze(t.get("sentence").getAsString(), t.get("gloss").getAsString(), answer,
						List.copyOf(accepted), strings(t, "distractors")));
			}
			Construction construction = new Construction(c.get("id").getAsString(), c.get("form").getAsString(),
					c.get("level").getAsInt(), strings(c, "prereq"), ruleCard, List.copyOf(examples),
					List.copyOf(clozes), c.get("generator").getAsString());
			map.put(construction.id(), construction);
		}
		return new GrammarBook(map);
	}

	private static List<String> strings(JsonObject object, String key) {
		List<String> out = new ArrayList<>();
		if (object.has(key) && object.get(key).isJsonArray()) {
			JsonArray array = object.getAsJsonArray(key);
			for (JsonElement element : array) {
				out.add(element.getAsString());
			}
		}
		return List.copyOf(out);
	}

	public boolean has(String id) {
		return byId.containsKey(id);
	}

	/** The construction, or null for an id the book does not know. */
	public Construction get(String id) {
		return byId.get(id);
	}

	/** Every construction in ladder (file) order. */
	public List<Construction> all() {
		return List.copyOf(byId.values());
	}

	/** The ids a goal type emitted as the constructions they stand for; unknown ids are dropped, duplicates kept once. */
	public List<String> resolve(List<String> emitted, Ladder ladder) {
		List<String> out = new ArrayList<>();
		for (String id : emitted) {
			String resolved = ladder.resolve(id);
			if (byId.containsKey(resolved) && !out.contains(resolved)) {
				out.add(resolved);
			}
		}
		return out;
	}
}
