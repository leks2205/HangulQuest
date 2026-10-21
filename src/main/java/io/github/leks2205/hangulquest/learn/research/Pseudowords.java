package io.github.leks2205.hangulquest.learn.research;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The nonwords of the placement test, and the rules that keep them honest. A pseudoword must be spelled
 * like a Korean word (complete syllables, simple codas, no sound changes at the joints) and must not be a
 * real word: not in the dictionary, not a dictionary word with a particle or {@code 다} or {@code 하다}
 * on it, and not two dictionary words stuck together. The shipped list is fixed and waits for native review.
 */
public final class Pseudowords {
	public static final String RESOURCE = "/assets/hangulquest/data/pseudowords.json";
	public static final String ID_PREFIX = "pseudo.";
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest-research");

	/** Endings a real word often carries; a candidate that is a dictionary word plus one of these is real. */
	private static final List<String> SUFFIXES = List.of("이", "가", "은", "는", "을", "를", "에", "도", "의", "와",
			"과", "로", "다", "하다", "하게", "해요", "이다", "이에요", "예요", "들");
	private static final int[] CODAS = {0, 1, 4, 8, 16, 17, 21}; // none, ㄱ ㄴ ㄹ ㅁ ㅂ ㅇ
	private static final int[] VOWELS = {0, 1, 2, 4, 5, 6, 8, 12, 13, 17, 18, 20}; // ㅏ ㅐ ㅑ ㅓ ㅔ ㅕ ㅗ ㅛ ㅜ ㅠ ㅡ ㅣ
	private static final int ONSET_NIEUN = 2;
	private static final int ONSET_MIEUM = 6;
	private static final int RIEUL_ONSET = 5;
	private static final int ONSET_IEUNG = 11;
	private static final int ONSET_HIEUH = 18;
	private static final int[] PLAIN_STOP_ONSETS = {0, 3, 7, 9, 12}; // ㄱ ㄷ ㅂ ㅅ ㅈ
	private static final int CODA_G = 1;
	private static final int CODA_R = 8;
	private static final int CODA_B = 17;

	private Pseudowords() {
	}

	/** One nonword with its id in the review registry. */
	public record Item(String id, String ko) {
	}

	/** The fixed list from the jar; empty when the file is missing or broken. */
	public static List<Item> load() {
		try (InputStream stream = Pseudowords.class.getResourceAsStream(RESOURCE)) {
			return stream == null ? List.of() : parse(stream);
		} catch (IOException | RuntimeException broken) {
			LOG.warn("could not read pseudowords.json: {}", broken.toString());
			return List.of();
		}
	}

	static List<Item> parse(InputStream stream) throws IOException {
		List<Item> items = new ArrayList<>();
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (JsonElement element : root.getAsJsonArray("words")) {
				JsonObject word = element.getAsJsonObject();
				items.add(new Item(word.get("id").getAsString(), word.get("ko").getAsString()));
			}
		}
		return items;
	}

	/**
	 * True when the text is spelled like a Korean word of two or three syllables: only complete syllables from
	 * the plain set, no word-initial ㄹ, and no joint where the sound would differ from the spelling
	 * (liaison, nasalisation, lateralisation, tensing after a closed syllable).
	 */
	public static boolean wellFormed(String text) {
		if (text == null || text.length() < 2 || text.length() > 3) {
			return false;
		}
		int previousCoda = 0;
		for (int i = 0; i < text.length(); i++) {
			int code = text.charAt(i) - 0xAC00;
			if (code < 0 || code >= 19 * 21 * 28) {
				return false;
			}
			int coda = code % 28;
			int vowel = code / 28 % 21;
			int onset = code / 28 / 21;
			if (!contains(CODAS, coda) || !contains(VOWELS, vowel)) {
				return false;
			}
			if (onset == RIEUL_ONSET && (i == 0 || previousCoda != 0)) {
				return false;
			}
			if (i > 0 && previousCoda != 0 && changesSound(previousCoda, onset)) {
				return false;
			}
			previousCoda = coda;
		}
		return true;
	}

	/** A closed syllable (final index) before an onset (initial index) whose sound is not the spelling. */
	private static boolean changesSound(int coda, int onset) {
		boolean stopCoda = coda == CODA_G || coda == CODA_B;
		if (onset == ONSET_IEUNG || onset == ONSET_HIEUH) {
			return true; // 한국어 [한구거], 학회 [하쾨]
		}
		if (stopCoda && (onset == ONSET_NIEUN || onset == ONSET_MIEUM)) {
			return true; // 학문 [항문]
		}
		if (coda == CODA_R && onset == ONSET_NIEUN) {
			return true; // 칼날 [칼랄]
		}
		return (stopCoda || coda == CODA_R) && contains(PLAIN_STOP_ONSETS, onset); // 학교 [학꾜]
	}

	private static boolean contains(int[] values, int wanted) {
		for (int value : values) {
			if (value == wanted) {
				return true;
			}
		}
		return false;
	}

	/** A dictionary from lines of text: every whitespace- or punctuation-separated Korean token. */
	public static Set<String> dictionary(Collection<String> lines) {
		Set<String> words = new HashSet<>();
		for (String line : lines) {
			for (String token : line.split("[^\\p{IsHangul}]+")) {
				if (!token.isEmpty()) {
					words.add(token);
				}
			}
		}
		return words;
	}

	/** True when the candidate is, or is built from, a real word of the dictionary. */
	public static boolean isReal(String candidate, Set<String> dictionary) {
		if (candidate == null || candidate.isEmpty()) {
			return true;
		}
		if (dictionary.contains(candidate)) {
			return true;
		}
		for (String suffix : SUFFIXES) {
			if (candidate.length() > suffix.length() && candidate.endsWith(suffix)
					&& dictionary.contains(candidate.substring(0, candidate.length() - suffix.length()))) {
				return true;
			}
			if (dictionary.contains(candidate + suffix)) {
				return true;
			}
		}
		for (int cut = 1; cut < candidate.length(); cut++) {
			if (dictionary.contains(candidate.substring(0, cut)) && dictionary.contains(candidate.substring(cut))) {
				return true; // two real words stuck together
			}
		}
		return false;
	}

	/** The candidates that are well formed and not real, in order, without duplicates. */
	public static List<String> vet(Collection<String> candidates, Set<String> dictionary) {
		Map<String, Boolean> kept = new LinkedHashMap<>();
		for (String candidate : candidates) {
			if (wellFormed(candidate) && !isReal(candidate, dictionary)) {
				kept.put(candidate, Boolean.TRUE);
			}
		}
		return new ArrayList<>(kept.keySet());
	}
}
