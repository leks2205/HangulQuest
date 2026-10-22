package io.github.leks2205.hangulquest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * tools/content_guard.py on real (temporary) git repositories: a PR that changes the Korean text of
 * more than 15 trail files or 300 Korean strings fails unless it carries the label or references an
 * ADR; byte-identical Korean never counts.
 */
class ContentGuardTest {
	private static final String TRAILS = "src/main/resources/assets/hangulquest/data/questlines/";
	private static final String DATA = "src/main/resources/assets/hangulquest/data/";

	@TempDir
	Path repo;

	record Run(int exit, String output) {
	}

	private static Run run(Path dir, String... command) {
		try {
			Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!process.waitFor(60, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return new Run(-1, "timed out\n" + output);
			}
			return new Run(process.exitValue(), output);
		} catch (IOException | InterruptedException unavailable) {
			return new Run(-1, unavailable.toString());
		}
	}

	private void git(String... args) {
		List<String> command = new ArrayList<>(List.of("git", "-c", "user.name=Test", "-c", "user.email=test@example.invalid",
				"-c", "commit.gpgsign=false", "-c", "init.defaultBranch=main"));
		command.addAll(List.of(args));
		Run run = run(repo, command.toArray(new String[0]));
		assertEquals(0, run.exit(), String.join(" ", command) + "\n" + run.output());
	}

	@BeforeEach
	void baseCommit() throws IOException {
		assumeTrue(run(repo, "git", "--version").exit() == 0, "git is not available");
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
		git("init", "-q");
		for (int i = 0; i < 20; i++) {
			write(TRAILS + "t" + i + ".json", trail(i, "돌을 캐요", "Mine stone"));
		}
		write(DATA + "concepts.json", concepts(0, 0));
		git("add", "-A");
		git("commit", "-q", "-m", "base");
		git("checkout", "-q", "-b", "feature");
	}

	private void write(String path, String content) throws IOException {
		Path file = repo.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	private static String trail(int n, String ko, String en) {
		return "{\"id\":\"t" + n + "\",\"steps\":[{\"clue\":{\"ko\":\"" + ko + " " + n + "\",\"en\":\"" + en + "\"}}]}";
	}

	/** {@code count} Korean strings, the first {@code from} of them skipped, so two files can share some. */
	private static String concepts(int from, int count) {
		StringBuilder out = new StringBuilder("[");
		for (int i = from; i < from + count; i++) {
			out.append(i == from ? "" : ",").append("{\"id\":\"c").append(i).append("\",\"ko\":\"말").append(i).append("\"}");
		}
		return out.append("]").toString();
	}

	private void commit(String message) {
		git("add", "-A");
		git("commit", "-q", "-m", message);
	}

	private Run guard(String... more) {
		List<String> command = new ArrayList<>(List.of("python3", Path.of("tools", "content_guard.py").toAbsolutePath().toString(),
				"--repo", repo.toString(), "--base", "main", "--head", "feature"));
		command.addAll(List.of(more));
		return run(Path.of("").toAbsolutePath(), command.toArray(new String[0]));
	}

	private void rewriteKorean(int trails) throws IOException {
		for (int i = 0; i < trails; i++) {
			write(TRAILS + "t" + i + ".json", trail(i, "돌을 파요", "Mine stone"));
		}
	}

	@Test
	void fifteenRewrittenTrailsPassAndSixteenFail() throws IOException {
		rewriteKorean(15);
		commit("content: 15 trails");
		Run fifteen = guard();
		assertEquals(0, fifteen.exit(), fifteen.output());

		rewriteKorean(16);
		commit("content: 16 trails");
		Run sixteen = guard();
		assertEquals(1, sixteen.exit(), sixteen.output());
		assertTrue(sixteen.output().contains("16 trail files"), sixteen.output());
		assertTrue(sixteen.output().contains("mass-change-approved"), "the message must say how to get approval");
	}

	@Test
	void theLabelApprovesAMassChange() throws IOException {
		rewriteKorean(20);
		commit("content: everything");
		assertEquals(1, guard().exit());
		Run labelled = guard("--labels", "[\"content\",\"mass-change-approved\"]");
		assertEquals(0, labelled.exit(), labelled.output());
		Run other = guard("--labels", "content");
		assertEquals(1, other.exit(), "another label approves nothing");
	}

	@Test
	void anAdrReferencedInTheBodyOrACommitApprovesIt() throws IOException {
		rewriteKorean(20);
		write("docs/decisions/0003-rebuild-level-1.md", "# Rebuild level 1\n");
		commit("content: level 1 rebuild, see docs/decisions/0003-rebuild-level-1.md");
		Run inCommit = guard();
		assertEquals(0, inCommit.exit(), inCommit.output());
		assertTrue(inCommit.output().contains("0003-rebuild-level-1.md"), inCommit.output());

		Run inBody = guard("--body", "Follows ADR-0003.");
		assertEquals(0, inBody.exit(), inBody.output());
	}

	@Test
	void anAdrThatDoesNotExistApprovesNothing() throws IOException {
		rewriteKorean(20);
		commit("content: see docs/decisions/0042-made-up.md and ADR-0042");
		Run run = guard();
		assertEquals(1, run.exit(), run.output());
	}

	@Test
	void changesToEnglishAndGermanTextOnlyAreExempt() throws IOException {
		for (int i = 0; i < 20; i++) {
			write(TRAILS + "t" + i + ".json", trail(i, "돌을 캐요", "Dig stone"));
		}
		commit("content: new English glosses");
		Run run = guard();
		assertEquals(0, run.exit(), run.output());
		assertTrue(run.output().contains("0 trail files"), run.output());
	}

	@Test
	void movedStructureWithTheSameKoreanIsExempt() throws IOException {
		for (int i = 0; i < 20; i++) {
			Files.move(repo.resolve(TRAILS + "t" + i + ".json"), repo.resolve(TRAILS + "renamed" + i + ".json"));
		}
		commit("refactor: rename the trails");
		Run run = guard();
		assertEquals(0, run.exit(), run.output());
		assertTrue(run.output().contains("0 trail files"), run.output());
	}

	@Test
	void moreThanThreeHundredKoreanStringsFailEvenInOneFile() throws IOException {
		write(DATA + "concepts.json", concepts(0, 300));
		commit("content: 300 words");
		assertEquals(0, guard().exit());

		write(DATA + "concepts.json", concepts(0, 301));
		commit("content: 301 words");
		Run run = guard();
		assertEquals(1, run.exit(), run.output());
		assertTrue(run.output().contains("301 Korean strings"), run.output());
	}

	@Test
	void anEditedStringCountsOnce() throws IOException {
		write(DATA + "concepts.json", concepts(0, 300));
		commit("content: 300 words");
		write(DATA + "concepts.json", concepts(0, 300).replace("말", "글"));
		commit("content: all of them edited");
		Run run = guard();
		assertEquals(0, run.exit(), "300 edits are 300 strings, not 600: " + run.output());
	}

	@Test
	void theSelfTestPasses() {
		Run run = run(Path.of("").toAbsolutePath(), "python3", "tools/content_guard.py", "--self-test");
		assertEquals(0, run.exit(), run.output());
	}
}
