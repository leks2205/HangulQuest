package io.github.leks2205.hangulquest.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.ui.HudWindows;
import io.github.leks2205.hangulquest.ui.JournalScreen;
import io.github.leks2205.hangulquest.ui.WelcomeScreen;
import io.github.leks2205.hangulquest.ui.hvpt.HvptModel;
import io.github.leks2205.hangulquest.ui.hvpt.HvptScreen;
import io.github.leks2205.hangulquest.ui.journal.JournalHost;
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
	/** The journal's last tab. */
	private static final int SETTINGS_TAB = 7;
	private static final int STUDY_TAB = 2;

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
			HangulQuestClient.get().hud().banner("창문을 열고 신선한 공기를 마셔요. 밖에서 해가 떠요.",
					"Open the window and breathe the fresh air. The sun is rising outside, so the whole day is ahead of you.", true);
		});
		context.waitTicks(5);
		check(context.computeOnClient(mc -> HangulQuestClient.get().hud().windows().top() != null),
				"a banner should be on screen");
		context.waitTicks(120); // well past the old 3.5 s timeout
		check(context.computeOnClient(mc -> HangulQuestClient.get().hud().windows().isOpen()),
				"the banner should stay until the hint key is pressed");
		context.takeScreenshot(shot("02c-window", shots));
		// Other windows (the auto-pick notice, word cards) may queue behind the banner meanwhile: the press must close
		// exactly the banner, and the next one may then show.
		HudWindows.Window banner = context.computeOnClient(mc -> HangulQuestClient.get().hud().windows().top());
		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitTicks(5);
		check(context.computeOnClient(mc -> HangulQuestClient.get().hud().windows().top() != banner),
				"the hint key should close the window");
		check(context.computeOnClient(mc -> !HangulQuestClient.get().quests().hintUsed()),
				"closing a window must not count as a hint");
		// A wide card squeezed in beside the quest log wraps its text instead of covering the log.
		context.runOnClient(mc -> {
			HangulQuestClient.get().hud().windows().clear();
			HangulQuestClient.get().hud().selftestCelebration(io.github.leks2205.hangulquest.ui.Celebration.Kind.TRAIL);
		});
		context.waitTicks(30);
		context.takeScreenshot(shot("02c2-wide-window", shots));
		context.runOnClient(mc -> HangulQuestClient.get().hud().windows().clear());
		context.waitTicks(2);
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

		// The quest tab on a fresh profile: the tracked trails, the ones that can start, the greyed follow-ups.
		openJournal(context, JournalHost.QUEST_TAB);
		context.takeScreenshot(shot("02e-quests-fresh", shots));
		// A larger window at GUI scale 2 fits more cards per row; every title must stay readable there too.
		context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.getWindow().setWindowed(1280, 720);
		});
		context.waitTicks(10);
		context.takeScreenshot(shot("02e2-quests-fresh-1280x720", shots));
		context.runOnClient(mc -> {
			mc.options.guiScale().set(0);
			mc.getWindow().setWindowed(854, 480);
		});
		context.waitTicks(10);
		closeScreen(context);

		// One finished trail: it folds into "Completed (1)" and its follow-ups open up.
		context.runOnClient(mc -> {
			var quests = HangulQuestClient.get().quests();
			String id = quests.focusedId();
			HangulQuestClient.get().learn().data().quests.completed.put(id, System.currentTimeMillis());
			quests.untrack(id);
			HangulQuestClient.get().learn().markDirty();
		});
		openJournal(context, JournalHost.QUEST_TAB);
		context.takeScreenshot(shot("02f-quests-one-done", shots));
		closeScreen(context);

		openJournal(context, SETTINGS_TAB);
		context.takeScreenshot(shot("02g-settings", shots));
		closeScreen(context);

		forgeScreens(context, shots);

		// Night is a context the situational slot reacts to once it has held for a while.
		world.getServer().runCommand("time set midnight");
		for (int waited = 0; waited < 60 && context.computeOnClient(mc -> HangulQuestClient.get().quests().situationalId()) == null; waited++) {
			context.waitTicks(20);
		}
		context.waitTicks(10);
		check(context.computeOnClient(mc -> HangulQuestClient.get().quests().situationalId()) != null,
				"a night trail should take the situational slot after the context held");
		context.runOnClient(mc -> HangulQuestClient.get().hud().windows().clear());
		context.waitTicks(5);
		context.takeScreenshot(shot("02h-situational", shots));

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

	/**
	 * The sound forge: reached from the Study tab, it explains itself while there is no voices pack (the test
	 * client has none). With a pretend pack that has every sound it shows the start page, a trial and its feedback.
	 */
	private static void forgeScreens(ClientGameTestContext context, Path shots) {
		openJournal(context, STUDY_TAB);
		context.takeScreenshot(shot("02i-study", shots));
		closeScreen(context);

		context.runOnClient(mc -> mc.setScreenAndShow(new HvptScreen(null)));
		context.waitForScreen(HvptScreen.class);
		context.waitTicks(3);
		check(context.computeOnClient(mc -> ((HvptScreen) mc.gui.screen()).model().stage() == HvptModel.Stage.BLOCKED),
				"without the voices pack the sound forge should only explain");
		context.takeScreenshot(shot("02j-forge-no-pack", shots));
		context.runOnClient(mc -> mc.setScreenAndShow(null));
		context.waitFor(mc -> mc.gui.screen() == null);

		HvptModel[] model = new HvptModel[1];
		context.runOnClient(mc -> {
			var progress = new io.github.leks2205.hangulquest.learn.ProgressData();
			var recorder = new io.github.leks2205.hangulquest.learn.hvpt.HvptRecorder(progress,
					io.github.leks2205.hangulquest.learn.ReviewLog.disabled(), () -> { });
			model[0] = new HvptModel(io.github.leks2205.hangulquest.learn.hvpt.HvptSet.shared(), new PretendPack(), recorder,
					() -> progress.hvpt, System::currentTimeMillis, new java.util.Random(3), java.time.ZoneId.systemDefault());
			mc.setScreenAndShow(new HvptScreen(null, model[0]));
		});
		context.waitForScreen(HvptScreen.class);
		context.waitTicks(3);
		context.takeScreenshot(shot("02k-forge-start", shots));

		context.runOnClient(mc -> model[0].start(io.github.leks2205.hangulquest.learn.hvpt.HvptPhase.PRE));
		context.waitTicks(5);
		context.takeScreenshot(shot("02l-forge-trial", shots));
		context.getInput().pressKey(InputConstants.KEY_2);
		context.waitTicks(3);
		check(context.computeOnClient(mc -> model[0].session().answered() == 1),
				"the answer key should answer the trial");
		context.takeScreenshot(shot("02m-forge-feedback", shots));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(3);
		check(context.computeOnClient(mc -> model[0].session().index() == 1), "Enter should go on to the next trial");
		context.runOnClient(mc -> {
			while (model[0].stage() == HvptModel.Stage.RUN) {
				model[0].choose(0);
				model[0].advance();
			}
		});
		context.waitTicks(3);
		context.takeScreenshot(shot("02n-forge-result", shots));
		closeScreen(context);
	}

	/** Says yes to every sound and plays none, so the screens can be shown without the optional pack. */
	private static final class PretendPack implements HvptModel.Audio {
		@Override
		public boolean settingOn() {
			return true;
		}

		@Override
		public boolean packLoaded() {
			return true;
		}

		@Override
		public boolean forgeLoaded() {
			return true;
		}

		@Override
		public boolean has(String event) {
			return true;
		}

		@Override
		public void play(String event) {
		}
	}

	private static void openJournal(ClientGameTestContext context, int tab) {
		context.runOnClient(mc -> mc.setScreenAndShow(new JournalScreen(tab)));
		context.waitForScreen(JournalScreen.class);
		context.waitTicks(5);
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
