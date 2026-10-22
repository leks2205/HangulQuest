package io.github.leks2205.hangulquest.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.ui.HudWindows;
import io.github.leks2205.hangulquest.ui.JournalScreen;
import io.github.leks2205.hangulquest.ui.WelcomeScreen;
import io.github.leks2205.hangulquest.ui.quiz.QuizScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One scripted player session: create a world, see the welcome screen once, open the journal, learn a
 * word, save and quit, join again. Screenshots land in {@code build/gametest/}.
 */
public final class HangulQuestClientGameTest implements FabricClientGameTest {
	private static final String WORD = "concept:stone";

	@Override
	public void runTest(ClientGameTestContext context) {
		Path shots = shotsDir();

		TestSingleplayerContext world = context.worldBuilder().create();

		// The welcome screen opens on the first join of a fresh install.
		context.waitForScreen(WelcomeScreen.class);
		check(context.computeOnClient(mc -> HangulQuestClient.get().config().welcomeShown) == 1,
				"the welcome screen should count as shown once");
		context.takeScreenshot(shot("01-welcome", shots));
		closeScreen(context);

		// The journal opens on its key and closes again.
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitForScreen(JournalScreen.class);
		context.takeScreenshot(shot("02-journal", shots));
		closeScreen(context);

		// A review card opens in its own screen and closes without recording anything.
		context.runOnClient(mc -> HangulQuestClient.get().hud().selftestStartQuiz(WORD));
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(5);
		context.takeScreenshot(shot("02b-card", shots));
		closeScreen(context);

		// A HUD window stays until the hint key closes it, and that press is not a hint.
		context.runOnClient(mc -> {
			HangulQuestClient.get().hud().windows().clear();
			HangulQuestClient.get().hud().banner("창문", "window", true);
		});
		context.waitTicks(5);
		check(context.computeOnClient(mc -> HangulQuestClient.get().hud().windows().top() != null),
				"a banner should be on screen");
		context.waitTicks(120); // well past the old 3.5 s timeout
		check(context.computeOnClient(mc -> HangulQuestClient.get().hud().windows().isOpen()),
				"the banner should stay until the hint key is pressed");
		context.takeScreenshot(shot("02c-window", shots));
		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitTicks(5);
		check(context.computeOnClient(mc -> !HangulQuestClient.get().hud().windows().isOpen()),
				"the hint key should close the window");
		check(context.computeOnClient(mc -> !HangulQuestClient.get().quests().hintUsed()),
				"closing a window must not count as a hint");
		// with nothing open the same key is a hint, and its panel is a window too
		check(context.computeOnClient(mc -> HangulQuestClient.get().quests().focusedId() != null),
				"a quest should be in focus for the hint check");
		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitTicks(5);
		check(context.computeOnClient(mc -> HangulQuestClient.get().hud().windows().top() != null
						&& HangulQuestClient.get().hud().windows().top().kind() == HudWindows.Kind.HINT),
				"the hint key with no window open should show the hint panel");
		context.takeScreenshot(shot("02d-hint", shots));
		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitTicks(5);
		check(context.computeOnClient(mc -> HangulQuestClient.get().hud().windows().top() == null
						|| HangulQuestClient.get().hud().windows().top().kind() != HudWindows.Kind.HINT),
				"the hint key should close the hint panel without asking for another hint");
		context.runOnClient(mc -> HangulQuestClient.get().hud().windows().clear());

		// Progress made in this session ...
		context.runOnClient(mc -> HangulQuestClient.get().learn().markKnown(WORD, true, System.currentTimeMillis()));
		context.waitTicks(5);
		Path progress = context.computeOnClient(mc -> HangulQuestClient.get().learn().store().file());

		// ... is on disk after save and quit ...
		world.close();
		check(readString(progress).contains(WORD), "progress.json should contain " + WORD + " after quitting");
		context.takeScreenshot(shot("03-title", shots));

		// ... and the same world comes back with it and without a second welcome screen.
		try (TestSingleplayerContext again = world.getWorldSave().open()) {
			context.waitFor(mc -> mc.level != null && mc.player != null);
			context.waitTicks(40);
			check(context.computeOnClient(mc -> mc.gui.screen() == null),
					"no screen should open on the second join of the same game start");
			check(context.computeOnClient(mc -> HangulQuestClient.get().learn().isKnown(WORD)),
					WORD + " should still be known after re-joining");
			check(context.computeOnClient(mc -> HangulQuestClient.get().config().welcomeShown) == 1,
					"the welcome screen must not count a second showing");
			context.takeScreenshot(shot("04-rejoined", shots));
		}
	}

	private static void closeScreen(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null);
	}

	private static TestScreenshotOptions shot(String name, Path dir) {
		return TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(dir);
	}

	/** The run directory is build/run/clientGameTest, so two levels up is build/. */
	private static Path shotsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("../../gametest").normalize();
	}

	private static String readString(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException unreadable) {
			throw new AssertionError("could not read " + file, unreadable);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
