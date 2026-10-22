package io.github.leks2205.hangulquest.learn.hvpt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stimulus set of the sound forge ({@code hvpt.json}): the contrasts, the minimal-pair items and the
 * voices. The jar holds only this description; the sounds are in the optional voices pack, one event per
 * item, form and voice ({@link #event}). The set is marked with its {@link #source()} ("tts" today) so natural
 * recordings can replace the audio later without a change here.
 *
 * <p>Training voices and held-out voices are disjoint by construction: the pre- and post-test use the held-out
 * ones, the sessions never do.
 */
public final class HvptSet {
	public static final String RESOURCE = "/assets/hangulquest/data/hvpt.json";
	public static final String EVENT_PREFIX = "hvpt.";
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest-hvpt");
	private static final HvptSet EMPTY = new HvptSet("none", List.of(), List.of(), new Params(60, 30, 10, 8, 3),
			List.of(), List.of());
	private static volatile HvptSet shared;

	/** One way to say the contrast: {@code key} (l, t, a, eo, o), the Korean text and its gloss (words only). */
	public record Form(String key, String ko, String en, String de) {
		public String gloss(Lang lang) {
			String text = lang == Lang.DE ? de : en;
			return text == null ? "" : text;
		}
	}

	/** A contrast such as ㄱ ㄲ ㅋ; {@code formKeys} are its sounds in display order. */
	public record Contrast(String id, String label, List<String> formKeys) {
		public Contrast {
			formKeys = List.copyOf(formKeys);
		}
	}

	/** A minimal pair or triplet: two or three forms of one contrast. {@code test} items make up the pre- and post-test. */
	public record Item(String id, String contrast, boolean word, boolean test, List<Form> forms) {
		public Item {
			forms = List.copyOf(forms);
		}

		public Form form(String key) {
			for (Form form : forms) {
				if (form.key().equals(key)) {
					return form;
				}
			}
			return null;
		}
	}

	/** Session sizes and the schedule the screen recommends. */
	public record Params(int trials, int testTrials, int recommendedSessions, int minSessions, int weeks) {
	}

	private final String source;
	private final List<String> trainingVoices;
	private final List<String> heldOutVoices;
	private final Params params;
	private final List<Contrast> contrasts;
	private final List<Item> items;

	public HvptSet(String source, List<String> trainingVoices, List<String> heldOutVoices, Params params,
				   List<Contrast> contrasts, List<Item> items) {
		this.source = source;
		this.trainingVoices = List.copyOf(trainingVoices);
		this.heldOutVoices = List.copyOf(heldOutVoices);
		this.params = params;
		this.contrasts = List.copyOf(contrasts);
		this.items = List.copyOf(items);
	}

	public static HvptSet none() {
		return EMPTY;
	}

	/** The file in the jar, loaded once. */
	public static HvptSet shared() {
		HvptSet loaded = shared;
		if (loaded == null) {
			loaded = load();
			shared = loaded;
		}
		return loaded;
	}

	/** A missing or broken file gives an empty set, so the screen says "no sounds" instead of crashing. */
	public static HvptSet load() {
		try (InputStream stream = HvptSet.class.getResourceAsStream(RESOURCE)) {
			return stream == null ? EMPTY : parse(stream);
		} catch (IOException | RuntimeException broken) {
			LOG.warn("could not read hvpt.json: {}", broken.toString());
			return EMPTY;
		}
	}

	public static HvptSet parse(InputStream stream) throws IOException {
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject session = root.getAsJsonObject("session");
			Params params = new Params(session.get("trials").getAsInt(), session.get("testTrials").getAsInt(),
					session.get("recommendedSessions").getAsInt(), session.get("minSessions").getAsInt(),
					session.get("weeks").getAsInt());
			List<Contrast> contrasts = new ArrayList<>();
			for (JsonElement element : root.getAsJsonArray("contrasts")) {
				JsonObject object = element.getAsJsonObject();
				List<String> keys = new ArrayList<>();
				for (JsonElement form : object.getAsJsonArray("forms")) {
					keys.add(form.getAsJsonObject().get("key").getAsString());
				}
				contrasts.add(new Contrast(object.get("id").getAsString(), object.get("label").getAsString(), keys));
			}
			List<Item> items = new ArrayList<>();
			for (JsonElement element : root.getAsJsonArray("items")) {
				JsonObject object = element.getAsJsonObject();
				List<Form> forms = new ArrayList<>();
				for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("forms").entrySet()) {
					JsonObject form = entry.getValue().getAsJsonObject();
					forms.add(new Form(entry.getKey(), form.get("ko").getAsString(), text(form, "en"), text(form, "de")));
				}
				items.add(new Item(object.get("id").getAsString(), object.get("contrast").getAsString(),
						"word".equals(object.get("kind").getAsString()),
						object.has("test") && object.get("test").getAsBoolean(), forms));
			}
			return new HvptSet(root.get("source").getAsString(), strings(root, "trainingVoices"),
					strings(root, "heldOutVoices"), params, contrasts, items);
		}
	}

	private static String text(JsonObject object, String name) {
		return object.has(name) ? object.get(name).getAsString() : null;
	}

	private static List<String> strings(JsonObject root, String name) {
		List<String> list = new ArrayList<>();
		for (JsonElement element : root.getAsJsonArray(name)) {
			list.add(element.getAsString());
		}
		return list;
	}

	/** "tts" while the sounds are synthetic. */
	public String source() {
		return source;
	}

	public List<String> trainingVoices() {
		return trainingVoices;
	}

	public List<String> heldOutVoices() {
		return heldOutVoices;
	}

	public Params params() {
		return params;
	}

	public List<Contrast> contrasts() {
		return contrasts;
	}

	public List<Item> items() {
		return items;
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	public Contrast contrast(String id) {
		for (Contrast contrast : contrasts) {
			if (contrast.id().equals(id)) {
				return contrast;
			}
		}
		return null;
	}

	public List<Item> itemsOf(String contrastId) {
		return items.stream().filter(item -> item.contrast().equals(contrastId)).toList();
	}

	public Item item(String id) {
		for (Item item : items) {
			if (item.id().equals(id)) {
				return item;
			}
		}
		return null;
	}

	/** The sound event of one form in one voice, as the voices pack defines it. */
	public static String event(String itemId, String formKey, String voice) {
		return EVENT_PREFIX + itemId + "." + formKey + "." + voice;
	}

	/** What the review log calls a stimulus: the item and form, without the voice. */
	public static String stimulusId(String itemId, String formKey) {
		return itemId + "." + formKey;
	}

	/** Every voice that is allowed in the given phase. */
	public List<String> voicesFor(HvptPhase phase) {
		return phase == HvptPhase.TRAIN ? trainingVoices : heldOutVoices;
	}

	/** The jar's own consistency: every item belongs to a contrast and uses only its forms; voices are disjoint. */
	public List<String> problems() {
		List<String> problems = new ArrayList<>();
		if (trainingVoices.stream().anyMatch(heldOutVoices::contains)) {
			problems.add("a voice is both training and held out");
		}
		Map<String, Set<String>> keys = new LinkedHashMap<>();
		contrasts.forEach(contrast -> keys.put(contrast.id(), Set.copyOf(contrast.formKeys())));
		for (Item item : items) {
			Set<String> allowed = keys.get(item.contrast());
			if (allowed == null) {
				problems.add(item.id() + ": unknown contrast " + item.contrast());
				continue;
			}
			if (item.forms().size() < 2 || item.forms().size() > 3) {
				problems.add(item.id() + ": needs two or three forms");
			}
			for (Form form : item.forms()) {
				if (!allowed.contains(form.key())) {
					problems.add(item.id() + ": form " + form.key() + " is not part of " + item.contrast());
				}
			}
		}
		return problems;
	}
}
