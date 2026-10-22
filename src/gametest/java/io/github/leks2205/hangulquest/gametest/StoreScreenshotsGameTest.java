package io.github.leks2205.hangulquest.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.culture.CultureCards;
import io.github.leks2205.hangulquest.culture.CultureHook;
import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueContent;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueHook;
import io.github.leks2205.hangulquest.dialogue.DialogueSession;
import io.github.leks2205.hangulquest.learn.hvpt.HvptPhase;
import io.github.leks2205.hangulquest.ui.Celebration;
import io.github.leks2205.hangulquest.ui.HudController;
import io.github.leks2205.hangulquest.ui.HudWindows;
import io.github.leks2205.hangulquest.ui.JournalScreen;
import io.github.leks2205.hangulquest.ui.WelcomeScreen;
import io.github.leks2205.hangulquest.ui.dialogue.DialogueScreen;
import io.github.leks2205.hangulquest.ui.hvpt.HvptModel;
import io.github.leks2205.hangulquest.ui.hvpt.HvptScreen;
import io.github.leks2205.hangulquest.ui.quiz.QuizScreen;
import io.github.leks2205.hangulquest.ui.quiz.QuizSession;
import io.github.leks2205.hangulquest.ui.quiz.cards.CardFactory;
import io.github.leks2205.hangulquest.ui.quiz.cards.CardInfo;
import io.github.leks2205.hangulquest.util.Lang;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Screenshots for the store listing (not a regression test): one scripted session at 1920x1080 and GUI scale 4,
 * English interface, one scene per feature. It only runs with HANGULQUEST_STORE_SHOTS=1 in the environment. They land in {@code build/gametest/store/}. It needs a display
 * of at least that size, for example {@code xvfb-run -s "-screen 0 1920x1080x24"}. The scenes that have no public
 * entry point (the dialogue and the story page) are reached by reflection, which is fine for a test.
 */
public final class StoreScreenshotsGameTest implements FabricClientGameTest {
	private static final int INFO_TAB = 0;
	private static final int WORDS_TAB = 1;
	private static final int STUDY_TAB = 2;
	private static final int QUEST_TAB = 4;
	private static final int GRAMMAR_TAB = 5;
	private static final int BOOKS_TAB = 6;

	/** Words the profile "already knows", so the dictionary, stories and cards have something to show. */
	private static final List<String> KNOWN = List.of("stone", "cobblestone", "coal", "torch", "forest", "house", "build",
			"tree", "chop", "log", "make", "planks", "mine", "door", "place", "glass", "bed", "chest", "sleep", "apple",
			"water", "fire", "sword", "pickaxe", "bread", "wheat", "cow", "pig", "sheep", "diamond");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("HANGULQUEST_STORE_SHOTS") == null) {
			return; // opt-in: the regular game test run does not need these
		}
		Path shots = shotsDir();
		var world = context.worldBuilder().create();
		context.runOnClient(mc -> mc.getWindow().setWindowed(1920, 1080));
		context.runOnClient(mc -> mc.options.guiScale().set(4));
		context.waitTicks(10);

		// another test in the same run may already have used up the first-join screen
		context.waitTicks(20);
		context.runOnClient(mc -> WelcomeScreen.openOnJoin());
		context.waitForScreen(WelcomeScreen.class);
		context.waitTicks(5);
		context.takeScreenshot(shot("welcome", shots));
		closeScreen(context);

		// a profile with some history
		context.runOnClient(mc -> {
			var learn = HangulQuestClient.get().learn();
			long now = System.currentTimeMillis();
			for (String id : KNOWN) {
				learn.markKnown("concept:" + id, true, now);
			}
			HangulQuestClient.get().hud().windows().clear();
		});
		context.waitTicks(60);
		context.runOnClient(mc -> HangulQuestClient.get().hud().windows().clear());
		context.waitTicks(10);
		context.takeScreenshot(shot("hud-quest-log", shots));

		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitTicks(8);
		context.takeScreenshot(shot("hint", shots));
		context.getInput().pressKey(InputConstants.KEY_H);
		context.waitTicks(5);

		// a rule card, a culture note and a word card, each as the HUD window it is
		context.runOnClient(mc -> {
			var hq = HangulQuestClient.get();
			hq.hud().windows().clear();
			var note = CultureHook.content().notes().get(0);
			hq.hud().windows().show(HudWindows.Window.of(HudWindows.Kind.CULTURE, "store:" + note.id(),
					new CultureCards.Card(note, note.id(), System.currentTimeMillis() + 600_000L)).autoCloseMs(600_000L));
		});
		context.waitTicks(15);
		context.takeScreenshot(shot("culture-note", shots));
		context.runOnClient(mc -> {
			var hq = HangulQuestClient.get();
			hq.hud().windows().clear();
			hq.hud().wordCard(hq.catalog().byId("concept:torch").orElseThrow());
		});
		context.waitTicks(15);
		context.takeScreenshot(shot("word-card", shots));
		// journal tabs
		openJournal(context, QUEST_TAB);
		context.takeScreenshot(shot("journal-quests", shots));
		closeScreen(context);
		openJournal(context, WORDS_TAB);
		context.takeScreenshot(shot("journal-words", shots));
		closeScreen(context);
		openJournal(context, INFO_TAB);
		context.takeScreenshot(shot("journal-progress", shots));
		closeScreen(context);
		context.runOnClient(mc -> {
			var learn = HangulQuestClient.get().learn();
			for (String id : List.of("L1.eoyo", "L1.obj_eul", "L1.subj_i", "L1.e_place", "L2.seyo")) {
				learn.grammarCardShown(id, System.currentTimeMillis());
			}
		});
		openJournal(context, GRAMMAR_TAB);
		context.takeScreenshot(shot("journal-grammar", shots));
		closeScreen(context);

		// the library: open a story by calling the tab's own row click
		context.runOnClient(mc -> {
			var learn = HangulQuestClient.get().learn();
			learn.data().quests.completed.put("holzpfad", System.currentTimeMillis());
			for (var concept : HangulQuestClient.get().catalog().all()) {
				learn.markKnown(concept.id, true, System.currentTimeMillis());
			}
		});
		openJournal(context, BOOKS_TAB);
		context.takeScreenshot(shot("library-list", shots));
		context.runOnClient(mc -> clickFirstStory((JournalScreen) mc.gui.screen()));
		context.waitTicks(10);
		context.takeScreenshot(shot("library-story", shots));
		closeScreen(context);

		// cards
		context.runOnClient(mc -> HangulQuestClient.get().hud().selftestStartQuiz("concept:stone"));
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(8);
		context.takeScreenshot(shot("quiz-four-options", shots));
		closeScreen(context);
		showCard(context, CardFactory.Kind.TILES, "concept:chest");
		context.takeScreenshot(shot("quiz-syllable-tiles", shots));
		closeScreen(context);
		showCard(context, CardFactory.Kind.TYPED, "concept:stone");
		// 돌 on a 두벌식 keyboard: e (ㄷ), h (ㅗ), f (ㄹ)
		context.getInput().pressKey(InputConstants.KEY_E);
		context.getInput().pressKey(InputConstants.KEY_H);
		context.getInput().pressKey(InputConstants.KEY_F);
		context.waitTicks(5);
		context.takeScreenshot(shot("typed-answer", shots));
		closeScreen(context);

		// the sound forge, with a pretend voices pack
		HvptModel[] model = new HvptModel[1];
		context.runOnClient(mc -> {
			var progress = new io.github.leks2205.hangulquest.learn.ProgressData();
			var recorder = new io.github.leks2205.hangulquest.learn.hvpt.HvptRecorder(progress,
					io.github.leks2205.hangulquest.learn.ReviewLog.disabled(), () -> { });
			model[0] = new HvptModel(io.github.leks2205.hangulquest.learn.hvpt.HvptSet.shared(), new PretendPack(), recorder,
					() -> progress.hvpt, System::currentTimeMillis, new Random(3), java.time.ZoneId.systemDefault());
			mc.setScreenAndShow(new HvptScreen(null, model[0]));
		});
		context.waitForScreen(HvptScreen.class);
		context.waitTicks(3);
		context.takeScreenshot(shot("sound-forge-start", shots));
		context.runOnClient(mc -> model[0].start(HvptPhase.PRE));
		context.waitTicks(5);
		context.takeScreenshot(shot("sound-forge-trial", shots));
		context.getInput().pressKey(InputConstants.KEY_2);
		context.waitTicks(3);
		context.takeScreenshot(shot("sound-forge-feedback", shots));
		closeScreen(context);

		// a villager conversation
		context.runOnClient(mc -> openDialogue(mc));
		context.waitForScreen(DialogueScreen.class);
		context.waitTicks(8);
		context.takeScreenshot(shot("dialogue", shots));
		closeScreen(context);

		// the same journal in German
		context.runOnClient(mc -> Lang.set(Lang.DE.code()));
		openJournal(context, QUEST_TAB);
		context.takeScreenshot(shot("journal-quests-de", shots));
		closeScreen(context);
		context.runOnClient(mc -> Lang.set(Lang.AUTO));
		context.runOnClient(mc -> HangulQuestClient.get().hud().windows().clear());
		context.runOnClient(mc -> {
			HangulQuestClient.get().hud().windows().clear();
			HangulQuestClient.get().hud().selftestCelebration(Celebration.Kind.TRAIL);
		});
		context.waitTicks(40);
		context.takeScreenshot(shot("celebration", shots));
		context.runOnClient(mc -> HangulQuestClient.get().hud().windows().clear());
		context.waitTicks(5);

		world.close();
	}

	private static void showCard(ClientGameTestContext context, CardFactory.Kind kind, String conceptId) {
		context.runOnClient(mc -> {
			try {
				var hq = HangulQuestClient.get();
				HudController hud = hq.hud();
				var word = hq.catalog().byId(conceptId).orElseThrow();
				var card = CardFactory.create(kind, CardInfo.of(hq.catalog(), word), hq.catalog().all(),
						io.github.leks2205.hangulquest.data.LexiconTags.shared(), new Random(5));
				Field session = HudController.class.getDeclaredField("session");
				session.setAccessible(true);
				((QuizSession) session.get(hud)).begin(QuizSession.Quiz.of(word.id, card));
				mc.setScreenAndShow(new QuizScreen(hud));
			} catch (ReflectiveOperationException broken) {
				throw new AssertionError(broken);
			}
		});
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(8);
	}

	private static void openDialogue(net.minecraft.client.Minecraft mc) {
		try {
			var hq = HangulQuestClient.get();
			Field contentField = DialogueHook.class.getDeclaredField("content");
			contentField.setAccessible(true);
			DialogueContent content = (DialogueContent) contentField.get(null);
			Dialogue dialogue = content.set().all().iterator().next();
			var learn = hq.learn();
			DialogueSession session = new DialogueSession(dialogue, new io.github.leks2205.hangulquest.dialogue.GameDialogueSink(
					learn, hq.catalog(), id -> false, System::currentTimeMillis, dialogue.id()));
			var vocabulary = DialogueCoverage.vocabulary(hq.catalog(), content.lexicon(),
					id -> learn.isKnown(id) || learn.introduced(id), Lang.current());
			mc.setScreenAndShow(new DialogueScreen(session, "Minsu", vocabulary));
		} catch (ReflectiveOperationException broken) {
			throw new AssertionError(broken);
		}
	}

	/** Clicks the first story row of the Library tab, the way a click on it would. */
	private static void clickFirstStory(JournalScreen screen) {
		try {
			Field tabsField = JournalScreen.class.getDeclaredField("tabs");
			tabsField.setAccessible(true);
			Object library = ((Object[]) tabsField.get(screen))[BOOKS_TAB];
			Class<?> type = library.getClass();
			Field rowsField = type.getDeclaredField("rows");
			rowsField.setAccessible(true);
			List<?> rows = (List<?>) rowsField.get(library);
			int story = -1;
			for (int i = 0; i < rows.size() && story < 0; i++) {
				if (rows.get(i).toString().contains("STORY")) {
					story = i;
				}
			}
			check(story >= 0, "the library should list a story");
			Field listY = type.getDeclaredField("listY");
			Field listX = type.getDeclaredField("listX");
			Field first = type.getDeclaredField("listFirst");
			listY.setAccessible(true);
			listX.setAccessible(true);
			first.setAccessible(true);
			Field rowHeight = type.getDeclaredField("ROW_HEIGHT");
			rowHeight.setAccessible(true);
			int height = rowHeight.getInt(null);
			Method click = type.getDeclaredMethod("clickRow", double.class, double.class);
			click.setAccessible(true);
			double y = listY.getInt(library) + (story - first.getInt(library)) * height + 2;
			click.invoke(library, (double) listX.getInt(library) + 5, y);
		} catch (ReflectiveOperationException broken) {
			throw new AssertionError(broken);
		}
	}

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
		context.getInput().setCursorPos(4, 4); // no tooltip in the shot
		context.waitTicks(8);
	}

	private static void closeScreen(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null);
	}

	private static TestScreenshotOptions shot(String name, Path dir) {
		return TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(dir);
	}

	private static Path shotsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("../../gametest/store").normalize();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
