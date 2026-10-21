package io.github.leks2205.hangulquest.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * The words dialogue uses that are not in the catalog: greetings, pronouns, a few verbs, the
 * professions. {@code forms} are the stems an eojeol can start with (the rest must be a particle or an
 * ending, see {@link DialogueCoverage}). {@code known} marks everyday words a beginner is expected to
 * have met; every other entry counts as a new word and is glossed on hover.
 */
public final class DialogueLexicon {
	public static final DialogueLexicon EMPTY = new DialogueLexicon(List.of());

	public record Entry(String id, List<String> forms, boolean known, String en, String de) {
		public Entry {
			forms = List.copyOf(forms);
		}

		/** The gloss in the interface language: German for German, English for everything else. */
		public String gloss(Lang lang) {
			String text = lang == Lang.DE && !de.isBlank() ? de : en;
			return text.isBlank() ? de : text;
		}
	}

	private final List<Entry> entries;

	private DialogueLexicon(List<Entry> entries) {
		this.entries = entries;
	}

	public static DialogueLexicon parse(String json) {
		List<Entry> entries = new ArrayList<>();
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		for (JsonElement element : root.getAsJsonArray("words")) {
			JsonObject word = element.getAsJsonObject();
			entries.add(new Entry(JsonFields.string(word, "id", ""), JsonFields.strings(word, "forms"),
					word.has("known") && word.get("known").getAsBoolean(), JsonFields.string(word, "en", ""),
					JsonFields.string(word, "de", "")));
		}
		return new DialogueLexicon(List.copyOf(entries));
	}

	public List<Entry> entries() {
		return entries;
	}
}
