package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.I18n;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every language the mod lists is complete enough to load and cannot crash a screen: both of its files
 * exist, it has no key English lacks, and its placeholders fit the English text. How much of a language
 * is translated is reported (and written to {@code build/reports/i18n/coverage.tsv}), not required:
 * a missing key falls back to English. Quality rules live in {@code tools/i18n/translate.py check}.
 */
class LangCompletenessTest {
	private static final Path LANG = Path.of("src/main/resources/assets/hangulquest/lang");
	private static final Path CONTENT = LANG.resolve("content");
	private static final String ENGLISH = "en_us";
	/** The German file also holds translations of the example sentences; the English ones are not written yet. */
	private static final Pattern SENTENCE_KEY = Pattern.compile("hangulquest\\.word\\..+\\.sentence");
	private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([a-zA-Z%])");

	private static Map<String, String> read(Path file) throws IOException {
		Map<String, String> out = new LinkedHashMap<>();
		if (Files.exists(file)) {
			JsonObject object = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String key : object.keySet()) {
				out.put(key, object.get(key).getAsString());
			}
		}
		return out;
	}

	private static Path file(Path dir, String code) {
		return dir.resolve(code + ".json");
	}

	/** Codes that have a file in either folder, English included. */
	private static List<String> codesOnDisk() throws IOException {
		TreeSet<String> codes = new TreeSet<>();
		for (Path dir : List.of(LANG, CONTENT)) {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path path : files.toList()) {
					String name = path.getFileName().toString();
					if (name.endsWith(".json")) {
						codes.add(name.substring(0, name.length() - ".json".length()));
					}
				}
			}
		}
		return new ArrayList<>(codes);
	}

	/**
	 * What a text takes as arguments, one entry per conversion in argument order: {@code s1}, {@code s2}
	 * for plain ones (numbered as they come) and for {@code %2$s} the number it names, plus {@code %%}.
	 * Positional placeholders may be reordered, so the entries are sorted. Null when plain and positional
	 * ones are mixed, which Java reads differently from what a translator means.
	 */
	static List<String> arguments(String text) {
		List<String> out = new ArrayList<>();
		int sequential = 0;
		boolean plain = false;
		boolean positional = false;
		Matcher matcher = FORMAT.matcher(text);
		while (matcher.find()) {
			String conversion = matcher.group(2);
			if (conversion.equals("%")) {
				out.add("%%");
			} else if (matcher.group(1) == null) {
				plain = true;
				out.add(conversion + (++sequential));
			} else {
				positional = true;
				out.add(conversion + matcher.group(1));
			}
		}
		if (plain && positional) {
			return null;
		}
		Collections.sort(out);
		return out;
	}

	/** Why a translation does not fit its English text, or null. */
	static String placeholderProblem(String english, String text) {
		List<String> want = arguments(english);
		List<String> have = arguments(text);
		if (have == null) {
			return "mixes %s and %1$s";
		}
		return want.equals(have) ? null : "takes " + have + " instead of " + want;
	}

	@Test
	void everyListedLanguageHasBothFiles() {
		for (I18n.Language language : I18n.languages()) {
			assertTrue(Files.exists(file(LANG, language.code())), language.code() + " has no interface file");
			assertTrue(Files.exists(file(CONTENT, language.code())), language.code() + " has no content file");
		}
	}

	@Test
	void noLanguageHasAKeyEnglishLacks() throws IOException {
		List<String> extra = new ArrayList<>();
		for (Path dir : List.of(LANG, CONTENT)) {
			Map<String, String> english = read(file(dir, ENGLISH));
			assertFalse(english.isEmpty(), "the English file is missing or empty in " + dir);
			for (String code : codesOnDisk()) {
				if (code.equals(ENGLISH)) {
					continue;
				}
				for (String key : read(file(dir, code)).keySet()) {
					if (!english.containsKey(key) && !SENTENCE_KEY.matcher(key).matches()) {
						extra.add(code + ": " + key);
					}
				}
			}
		}
		assertTrue(extra.isEmpty(), "keys that English does not have:\n" + String.join("\n", extra));
	}

	@Test
	void placeholdersFitTheEnglishText() throws IOException {
		List<String> broken = new ArrayList<>();
		for (Path dir : List.of(LANG, CONTENT)) {
			Map<String, String> english = read(file(dir, ENGLISH));
			for (String code : codesOnDisk()) {
				if (code.equals(ENGLISH)) {
					continue;
				}
				for (Map.Entry<String, String> entry : read(file(dir, code)).entrySet()) {
					String reference = english.get(entry.getKey());
					String problem = reference == null ? null : placeholderProblem(reference, entry.getValue());
					if (problem != null) {
						broken.add(code + " " + entry.getKey() + ": " + problem);
					}
				}
			}
		}
		assertTrue(broken.isEmpty(), String.join("\n", broken));
	}

	@Test
	void theCheckSeesBrokenPlaceholders() {
		assertEquals(null, placeholderProblem("%s of %s", "%2$s de %1$s"));
		assertEquals(null, placeholderProblem("%s%% done", "%s%% fertig"));
		assertTrue(placeholderProblem("%s of %s", "%s de") != null);
		assertTrue(placeholderProblem("%s of %s", "%s de %s %s") != null);
		assertTrue(placeholderProblem("%s of %s", "%1$s de %1$s") != null);
		assertTrue(placeholderProblem("%s of %s", "%1$s de %s") != null);
		assertTrue(placeholderProblem("%s%% done", "%s fertig") != null);
		assertTrue(placeholderProblem("done", "%s fertig") != null);
	}

	/**
	 * The game's default font is the vanilla one with its Unifont fallback, which draws any character of
	 * the Basic Multilingual Plane (Vietnamese, Thai, Japanese and Chinese included). Anything outside it,
	 * such as an emoji, or a replacement character from a broken encoding, shows as a box.
	 */
	@Test
	void textStaysInTheBasicMultilingualPlaneWithoutBrokenCharacters() throws IOException {
		List<String> bad = new ArrayList<>();
		for (Path dir : List.of(LANG, CONTENT)) {
			for (String code : codesOnDisk()) {
				for (Map.Entry<String, String> entry : read(file(dir, code)).entrySet()) {
					String text = entry.getValue();
					if (text.codePoints().anyMatch(c -> c > 0xFFFF || c == 0xFFFD)) {
						bad.add(code + " " + entry.getKey());
					}
				}
			}
		}
		assertTrue(bad.isEmpty(), "characters the game font cannot draw:\n" + String.join("\n", bad));
	}

	@Test
	void languageNamesAreDrawableToo() {
		for (I18n.Language language : I18n.languages()) {
			assertTrue(language.name().codePoints().noneMatch(c -> c > 0xFFFF || c == 0xFFFD), language.name());
		}
	}

	/** Prints one line per language and writes the table the CI keeps with its other reports. */
	@Test
	void coverageIsReportedPerLanguage() throws IOException {
		Map<String, String> englishUi = read(file(LANG, ENGLISH));
		Map<String, String> englishContent = read(file(CONTENT, ENGLISH));
		List<String> listed = new ArrayList<>();
		for (I18n.Language language : I18n.languages()) {
			listed.add(language.code());
		}
		StringBuilder table = new StringBuilder("language\tlisted\tinterface\tcontent\n");
		for (String code : codesOnDisk()) {
			int ui = countIn(read(file(LANG, code)), englishUi);
			int content = countIn(read(file(CONTENT, code)), englishContent);
			boolean isListed = listed.contains(code);
			String line = String.format("%s%s: interface %d/%d, content %d/%d", code, isListed ? "" : " (not listed)", ui,
					englishUi.size(), content, englishContent.size());
			System.err.println("i18n coverage " + line);
			table.append(code).append('\t').append(isListed).append('\t').append(ui).append('/').append(englishUi.size())
					.append('\t').append(content).append('/').append(englishContent.size()).append('\n');
		}
		Path report = Path.of("build/reports/i18n/coverage.tsv");
		Files.createDirectories(report.getParent());
		Files.writeString(report, table.toString(), StandardCharsets.UTF_8);
		assertTrue(listed.contains(ENGLISH));
	}

	private static int countIn(Map<String, String> language, Map<String, String> english) {
		int count = 0;
		for (Map.Entry<String, String> entry : language.entrySet()) {
			if (english.containsKey(entry.getKey()) && !entry.getValue().isBlank()) {
				count++;
			}
		}
		return count;
	}
}
