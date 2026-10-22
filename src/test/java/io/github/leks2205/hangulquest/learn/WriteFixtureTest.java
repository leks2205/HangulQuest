package io.github.leks2205.hangulquest.learn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Writes the progress fixture of a release: the synthetic v4 sample, loaded and saved by the real
 * ProgressStore, so the file is what this build writes. Skipped unless HANGULQUEST_WRITE_FIXTURE=tag
 * is set; tools/new_fixture.sh does that.
 */
class WriteFixtureTest {
	private static final Logger LOG = LoggerFactory.getLogger("write-fixture-test");

	@TempDir
	Path tempDir;

	@Test
	void writesTheFixtureOfThisBuild() throws IOException {
		String tag = System.getenv("HANGULQUEST_WRITE_FIXTURE");
		assumeTrue(tag != null && !tag.isBlank(), "set HANGULQUEST_WRITE_FIXTURE=<tag> to write a fixture");
		try (InputStream in = WriteFixtureTest.class.getResourceAsStream("/progress-fixtures/v4-autotrack.json")) {
			assertNotNull(in);
			Files.write(tempDir.resolve("progress.json"), in.readAllBytes());
		}
		ProgressStore store = new ProgressStore(tempDir, LOG);
		store.markDirty();
		store.flush();
		Path target = Path.of("src/test/resources/progress-fixtures/" + tag + ".json");
		Files.copy(tempDir.resolve("progress.json"), target, StandardCopyOption.REPLACE_EXISTING);
	}
}
