package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.util.I18n;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interface text lives in {@code lang/<code>.json}. English is the reference: every key the code uses
 * must be in it, German must have the same keys, and no language may disagree about how many
 * arguments a text takes. Other languages are allowed to be incomplete (they fall back to English).
 */
class I18nCoverageTest {
	private static final Path SOURCES = Path.of("src/main/java");
	private static final Path LANG_DIR = Path.of("src/main/resources/assets/hangulquest/lang");
	private static final String PREFIX = "hangulquest.ui.";
	private static final Pattern KEY_LITERAL = Pattern.compile("\"(hangulquest\\.ui\\.[a-z0-9_]+(?:\\.[a-z0-9_]+)*)\"");
	private static final Pattern OLD_CALL = Pattern.compile("\\bTr\\.t\\(\\s*\"");

	private static Map<String, String> read(String code) throws IOException {
		Map<String, String> out = new LinkedHashMap<>();
		JsonObject object = JsonParser.parseString(
				Files.readString(LANG_DIR.resolve(code + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
		for (String key : object.keySet()) {
			out.put(key, object.get(key).getAsString());
		}
		return out;
	}

	private static Set<String> uiKeys(Map<String, String> table) {
		Set<String> keys = new LinkedHashSet<>();
		for (String key : table.keySet()) {
			if (key.startsWith(PREFIX)) {
				keys.add(key);
			}
		}
		return keys;
	}

	/** Every language file next to en_us.json, by code. */
	private static List<String> otherLanguages() throws IOException {
		List<String> codes = new ArrayList<>();
		try (Stream<Path> files = Files.list(LANG_DIR)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				String code = name.substring(0, name.length() - ".json".length());
				if (name.endsWith(".json") && !code.equals("en_us") && !code.equals("de_de")) {
					codes.add(code);
				}
			}
		}
		return codes;
	}

	/** Keys the sources mention, plus the ones built from enums. */
	private static Map<String, String> usedKeys() throws IOException {
		Map<String, String> keys = new LinkedHashMap<>();
		try (Stream<Path> files = Files.walk(SOURCES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				Matcher matcher = KEY_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
				while (matcher.find()) {
					keys.putIfAbsent(matcher.group(1), file.getFileName().toString());
				}
			}
		}
		for (Category category : Category.values()) {
			keys.putIfAbsent(category.key(), "Category");
		}
		return keys;
	}

	@Test
	void everyKeyUsedInCodeIsInEnglish() throws IOException {
		Map<String, String> used = usedKeys();
		assertTrue(used.size() > 80, "scanner found only " + used.size() + " keys, the pattern is broken");
		Map<String, String> english = read("en_us");
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, String> key : used.entrySet()) {
			if (!english.containsKey(key.getKey())) {
				missing.add(key.getValue() + ": " + key.getKey());
			}
		}
		assertTrue(missing.isEmpty(), "keys used in code but not in en_us.json:\n" + String.join("\n", missing));
	}

	@Test
	void germanHasExactlyTheEnglishKeys() throws IOException {
		Set<String> english = uiKeys(read("en_us"));
		Set<String> german = uiKeys(read("de_de"));
		Set<String> missing = new LinkedHashSet<>(english);
		missing.removeAll(german);
		Set<String> extra = new LinkedHashSet<>(german);
		extra.removeAll(english);
		assertTrue(missing.isEmpty(), "missing in de_de.json: " + missing);
		assertTrue(extra.isEmpty(), "in de_de.json but not in en_us.json: " + extra);
	}

	@Test
	void vanillaKeysAreInBothFiles() throws IOException {
		Set<String> english = new LinkedHashSet<>(read("en_us").keySet());
		Set<String> german = new LinkedHashSet<>(read("de_de").keySet());
		assertEquals(english, german);
	}

	@Test
	void formatArgumentCountsMatchAcrossLocales() throws IOException {
		Map<String, String> english = read("en_us");
		List<String> mismatched = new ArrayList<>();
		List<String> codes = new ArrayList<>(List.of("de_de"));
		codes.addAll(otherLanguages());
		for (String code : codes) {
			for (Map.Entry<String, String> entry : read(code).entrySet()) {
				String reference = english.get(entry.getKey());
				if (reference != null && I18n.argCount(reference) != I18n.argCount(entry.getValue())) {
					mismatched.add(code + " " + entry.getKey() + ": " + I18n.argCount(entry.getValue())
							+ " argument(s) instead of " + I18n.argCount(reference));
				}
			}
		}
		assertTrue(mismatched.isEmpty(), String.join("\n", mismatched));
	}

	@Test
	void otherLocalesMayBeIncompleteButTheyAreReported() throws IOException {
		Set<String> english = uiKeys(read("en_us"));
		for (String code : otherLanguages()) {
			Set<String> missing = new LinkedHashSet<>(english);
			missing.removeAll(uiKeys(read(code)));
			if (!missing.isEmpty()) {
				System.err.println("WARN " + code + ".json lacks " + missing.size() + " of " + english.size()
						+ " keys, English is shown for them");
			}
		}
	}

	@Test
	void keysThatNothingUsesAreReported() throws IOException {
		Set<String> unused = uiKeys(read("en_us"));
		unused.removeAll(usedKeys().keySet());
		if (!unused.isEmpty()) {
			System.err.println("WARN keys in en_us.json that no code uses: " + unused);
		}
	}

	@Test
	void everyListedLanguageHasAFile() {
		for (I18n.Language language : I18n.languages()) {
			assertTrue(Files.exists(LANG_DIR.resolve(language.code() + ".json")),
					"languages.json lists " + language.code() + " but there is no lang file");
		}
	}

	@Test
	void noTrLiteralCallsRemainInTheSources() throws IOException {
		List<String> left = new ArrayList<>();
		try (Stream<Path> files = Files.walk(SOURCES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				if (file.getFileName().toString().equals("Tr.java")) {
					continue;
				}
				if (OLD_CALL.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
					left.add(file.getFileName().toString());
				}
			}
		}
		assertTrue(left.isEmpty(), "Tr.t(\"literal\") calls left, use a lang key: " + left);
	}

	@Test
	void theLiteralCallPatternSeesACall() {
		assertTrue(OLD_CALL.matcher("x(io.github.Tr.t(\"Eins\"));").find());
		assertTrue(OLD_CALL.matcher("x(Tr.t(\n\t\t\"Eins\"));").find());
		assertTrue(!OLD_CALL.matcher("x(I18n.t(\"hangulquest.ui.a.b\"));").find());
	}

	@Test
	void achievementAndCategoryKeysExist() throws IOException {
		Map<String, String> english = read("en_us");
		for (Achievements.Achievement achievement : Achievements.ALL) {
			assertTrue(english.containsKey(achievement.key()), "no label for " + achievement.key());
		}
		for (Category category : Category.values()) {
			assertTrue(english.containsKey(category.key()), "no label for " + category);
		}
	}

	@Test
	void argCountReadsPlainAndPositionalPlaceholders() {
		assertEquals(0, I18n.argCount("nothing here"));
		assertEquals(2, I18n.argCount("%s and %s"));
		assertEquals(2, I18n.argCount("%2$s before %1$s"));
		assertEquals(1, I18n.argCount("%s%% done"));
	}
}
