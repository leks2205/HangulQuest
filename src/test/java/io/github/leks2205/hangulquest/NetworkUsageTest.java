package io.github.leks2205.hangulquest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod promises zero network requests. The only way out of the game is a link opened in the system
 * browser through Minecraft's own confirmation screen, which asks the player first.
 */
class NetworkUsageTest {
	private static final Path SOURCES = Path.of("src/main/java");

	/** Anything that can talk to the network on its own. */
	private static final List<Pattern> FORBIDDEN = List.of(
			Pattern.compile("java\\.net\\.http"),
			Pattern.compile("\\bHttpClient\\b"),
			Pattern.compile("URLConnection"),
			Pattern.compile("Socket"),
			Pattern.compile("\\.openStream\\s*\\("),
			Pattern.compile("\\.openConnection\\s*\\("),
			Pattern.compile("\\.toURL\\s*\\("),
			Pattern.compile("\\bURL\\b"),
			Pattern.compile("openUri|openUrl"),
			// java.net.URI is only a value type; the allowed link goes through ConfirmLinkScreen.
			Pattern.compile("java\\.net\\.(?!URI\\b)"),
			Pattern.compile("javax\\.net"));

	/** The one allowed way to leave the game. */
	private static final Pattern ALLOWED_LINK = Pattern.compile("ConfirmLinkScreen\\.confirmLinkNow\\(");

	/** Comments only talk about URLs and sockets; the code is what counts. */
	private static String withoutComments(String line) {
		String text = line.replaceAll("/\\*.*?\\*/", "").replaceAll("(^|\\s)//.*$", "");
		String trimmed = text.strip();
		return trimmed.startsWith("*") || trimmed.startsWith("/*") ? "" : text;
	}

	/** Lines in {@code source} that use the network, as {@code line: text}. */
	static List<String> violations(String source) {
		List<String> found = new ArrayList<>();
		String[] lines = source.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = withoutComments(lines[i]);
			if (ALLOWED_LINK.matcher(line).find()) {
				line = ALLOWED_LINK.matcher(line).replaceAll("");
			}
			for (Pattern pattern : FORBIDDEN) {
				if (pattern.matcher(line).find()) {
					found.add((i + 1) + ": " + lines[i].strip());
					break;
				}
			}
		}
		return found;
	}

	@Test
	void theModNeverUsesTheNetwork() throws IOException {
		List<String> problems = new ArrayList<>();
		try (Stream<Path> files = Files.walk(SOURCES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
				for (String hit : violations(Files.readString(file))) {
					problems.add(SOURCES.relativize(file) + ":" + hit);
				}
			}
		}
		assertTrue(problems.isEmpty(), "network use in src/main/java:\n" + String.join("\n", problems));
	}

	@Test
	void theScannerCatchesWhatItShould() {
		for (String bad : List.of(
				"import java.net.http.HttpClient;",
				"var client = HttpClient.newHttpClient();",
				"var c = (HttpURLConnection) uri.toURL().openConnection();",
				"try (var s = new Socket(host, 80)) {",
				"try (var in = new java.net.URL(\"x\").openStream()) {",
				"import java.net.URL;",
				"import java.net.URLEncoder;",
				"Util.getPlatform().openUri(uri);")) {
			assertEquals(1, violations(bad).size(), "should be flagged: " + bad);
		}
	}

	@Test
	void commentsAreNotCode() {
		assertTrue(violations("/** Entries without an https URL are dropped. */").isEmpty());
		assertTrue(violations("\t * a Socket would be wrong here").isEmpty());
		assertTrue(violations("int a = 1; // not a URL").isEmpty());
		// A scheme inside a string is not a comment.
		assertEquals(1, violations("var s = \"https://x\"; new Socket();").size());
	}

	@Test
	void theConfirmationScreenLinkIsAllowed() {
		assertTrue(violations("import java.net.URI;").isEmpty());
		assertTrue(violations("ConfirmLinkScreen.confirmLinkNow(this, URI.create(entry.url()));").isEmpty());
		// The allowance covers the call, not a line that also does something else.
		assertEquals(1, violations("ConfirmLinkScreen.confirmLinkNow(this, uri); new Socket();").size());
	}
}
