package io.github.leks2205.hangulquest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every release adds a progress fixture (see src/test/resources/progress-fixtures/README.md). The
 * release workflow refuses a tag without one; this is the same check at build time, so a version bump
 * without its fixture fails in CI long before anyone tags.
 */
class ReleaseFixtureTest {
	private static final Path FIXTURES = Path.of("src/test/resources/progress-fixtures");
	private static final Path COMPATIBILITY_TEST =
			Path.of("src/test/java/io/github/leks2205/hangulquest/learn/ProgressCompatibilityTest.java");

	private static String modVersion() throws IOException {
		Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) {
			properties.load(reader);
		}
		return properties.getProperty("mod_version");
	}

	@Test
	void theCurrentVersionHasAProgressFixture() throws IOException {
		String tag = "v" + modVersion();
		assertTrue(Files.isRegularFile(FIXTURES.resolve(tag + ".json")),
				"mod_version is " + modVersion() + " but src/test/resources/progress-fixtures/" + tag + ".json is missing."
						+ " Add the progress.json this version writes (synthetic data) and list it in"
						+ " ProgressCompatibilityTest.");
	}

	@Test
	void everyReleaseFixtureIsRunThroughTheCompatibilityTest() throws IOException {
		String source = Files.readString(COMPATIBILITY_TEST);
		List<String> unlisted = new ArrayList<>();
		try (Stream<Path> files = Files.list(FIXTURES)) {
			for (Path file : files.sorted().toList()) {
				String name = file.getFileName().toString();
				if (name.matches("v\\d+\\.\\d+\\.\\d+\\.json") && !source.contains("\"" + name.replace(".json", "") + "\"")) {
					unlisted.add(name);
				}
			}
		}
		assertTrue(unlisted.isEmpty(), "fixtures that ProgressCompatibilityTest never loads: " + unlisted);
	}
}
