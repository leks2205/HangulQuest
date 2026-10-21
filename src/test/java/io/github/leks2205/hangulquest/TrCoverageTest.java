package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.util.Tr;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The German text is the key of {@link Tr}, so a key without an English entry silently shows German
 * in the English interface. This scans the sources for every literal key.
 */
class TrCoverageTest {
	private static final Pattern CALL = Pattern.compile(
			"\\bTr\\.t\\(\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)\\)");
	private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

	@Test
	void everyLiteralKeyHasAnEnglishEntry() throws IOException {
		Map<String, String> keys = literalKeys(Path.of("src/main/java"));
		assertTrue(keys.size() > 60, "scanner found only " + keys.size() + " keys, the pattern is broken");
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, String> key : keys.entrySet()) {
			if (!Tr.hasEnglish(key.getKey())) {
				missing.add(key.getValue() + ": \"" + key.getKey() + "\"");
			}
		}
		assertTrue(missing.isEmpty(), "Tr keys without an English entry:\n" + String.join("\n", missing));
	}

	@Test
	void achievementLabelsHaveEnglishEntries() {
		for (Achievements.Achievement achievement : Achievements.ALL) {
			assertTrue(Tr.hasEnglish(achievement.key()), "no English label for " + achievement.key());
		}
	}

	@Test
	void achievementLabelsFollowTheLanguageAtRuntime() {
		try {
			Lang.set("EN");
			Achievements.Achievement first = Achievements.ALL.get(0);
			assertEquals("First word", first.de());
			Lang.set("DE");
			assertEquals("Erstes Wort", first.de());
		} finally {
			Lang.set("EN");
		}
	}

	@Test
	void theKnownTypoKeyIsGone() {
		assertFalse(Tr.hasEnglish(" gesen, "));
	}

	@Test
	void theScannerFindsLiteralsAndJoinedLiterals() {
		String source = "x(Tr.t(\"Eins\")); y(io.github.Tr.t(\"Zwei \"\n + \"drei\")); z(Tr.t(name)); "
				+ "w(Tr.t(\"Vier \" + name)); v(Tr.t(\"Quote \\\" ok\"));";
		Map<String, String> found = new LinkedHashMap<>();
		collect(source, "fixture", found);
		assertEquals(List.of("Eins", "Zwei drei", "Quote \" ok"), new ArrayList<>(found.keySet()));
	}

	@Test
	void aMissingKeyIsReportedAsMissing() {
		assertFalse(Tr.hasEnglish("Dieser Schlüssel existiert garantiert nicht"));
	}

	private static Map<String, String> literalKeys(Path root) throws IOException {
		Map<String, String> keys = new LinkedHashMap<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				if (file.getFileName().toString().equals("Tr.java")) {
					continue;
				}
				collect(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString(), keys);
			}
		}
		return keys;
	}

	private static void collect(String source, String where, Map<String, String> keys) {
		Matcher call = CALL.matcher(source);
		while (call.find()) {
			StringBuilder key = new StringBuilder();
			Matcher literal = LITERAL.matcher(call.group(1));
			while (literal.find()) {
				key.append(unescape(literal.group(1)));
			}
			keys.putIfAbsent(key.toString(), where);
		}
	}

	private static String unescape(String raw) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c != '\\' || i + 1 >= raw.length()) {
				out.append(c);
				continue;
			}
			char next = raw.charAt(++i);
			switch (next) {
				case 'n' -> out.append('\n');
				case 't' -> out.append('\t');
				case 'u' -> {
					out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
					i += 4;
				}
				default -> out.append(next);
			}
		}
		return out.toString();
	}
}
