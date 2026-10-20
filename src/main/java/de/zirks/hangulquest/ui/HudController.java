package de.zirks.hangulquest.ui;

import com.mojang.blaze3d.platform.InputConstants;
import de.zirks.hangulquest.HangulQuestClient;
import de.zirks.hangulquest.learn.Achievements;
import de.zirks.hangulquest.learn.LearningService;
import de.zirks.hangulquest.quest.Goal;
import de.zirks.hangulquest.quest.QuestEngine;
import de.zirks.hangulquest.quest.QuestStep;
import de.zirks.hangulquest.quest.Questline;
import de.zirks.hangulquest.quest.SideQuest;
import de.zirks.hangulquest.data.Word;
import de.zirks.hangulquest.sense.Observation;
import de.zirks.hangulquest.sense.SafetyGate;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** All in-game cards: quest tracker, hint panel, sidequest quiz, review quiz, word card, banner. */
public final class HudController implements QuestEngine.Feedback {
	private final HangulQuestClient hq;
	private final Minecraft mc = Minecraft.getInstance();

	private final Celebration celebration = new Celebration();
	private int questReloadTicks;
	private List<Questline> nextChoices = List.of();
	private long nextChoicesUntil;
	private boolean key4;
	private boolean key5;
	private Word wordCard;
	private long wordCardUntil;
	private String bannerKo = "";
	private String bannerDe = "";
	private long bannerUntil;
	private QuestEngine.HintView hint;
	private int questLogBottom = 6;
	private long hintUntil;
	private long lastHintAt;

	private Word quizWord;
	private List<String> quizOptions = List.of();
	private int quizCorrect;
	private boolean quizActive;
	private long lastQuizAt;
	private Word quizResultWord;
	private boolean quizResultCorrect;
	private long quizResultUntil;

	private boolean key1;
	private boolean key2;
	private boolean key3;

	public HudController(HangulQuestClient hq) {
		this.hq = hq;
	}

	public void register() {
		HudElementRegistry.addLast(HangulQuestClient.id("hud"), this::extract);
	}

	/** The first automatic review should wait a full interval after joining. */
	public void onWorldJoin() {
		lastQuizAt = System.currentTimeMillis();
	}

	public void tick(Observation obs) {
		if (hq.quests() == null || hq.learn() == null) {
			return;
		}
		handleNumberKeys();
		if (++questReloadTicks >= 40) {
			questReloadTicks = 0;
			hq.reloadQuestlinesIfChanged(obs);
		}
		long now = System.currentTimeMillis();
		celebration.tickParticles(mc, now);
		if (!nextChoices.isEmpty()) {
			if (hq.quests().activeTrail() != null) {
				nextChoices = List.of();
			} else if (now > nextChoicesUntil) {
				Questline chosen = nextChoices.get(0);
				nextChoices = List.of();
				Observation fallbackObs = hq.lastObservation();
				if (fallbackObs == null) {
					fallbackObs = new Observation();
					fallbackObs.now = now;
				}
				hq.quests().startTrail(chosen.id(), fallbackObs, this);
			}
		}
		if (hint != null && now > hintUntil) {
			hint = null;
		}
		if (!quizActive && !hq.quests().sidequestActive() && hq.config().autoQuiz
				&& now - lastQuizAt > Math.max(1, hq.config().quizIntervalMinutes) * 60_000L
				&& SafetyGate.safeForQuiz(obs)) {
			startFromDue();
		}
	}

	private void handleNumberKeys() {
		boolean down1 = InputConstants.isKeyDown(InputConstants.KEY_1);
		boolean down2 = InputConstants.isKeyDown(InputConstants.KEY_2);
		boolean down3 = InputConstants.isKeyDown(InputConstants.KEY_3);
		boolean down4 = InputConstants.isKeyDown(InputConstants.KEY_4);
		boolean down5 = InputConstants.isKeyDown(InputConstants.KEY_5);
		if (down1 && !key1) {
			choose(0);
		} else if (down2 && !key2) {
			choose(1);
		} else if (down3 && !key3) {
			choose(2);
		} else if (down4 && !key4) {
			choose(3);
		} else if (down5 && !key5) {
			choose(4);
		}
		key1 = down1;
		key2 = down2;
		key3 = down3;
		key4 = down4;
		key5 = down5;
	}

	private void choose(int index) {
		if (mc.gui.screen() != null) {
			return;
		}
		if (!nextChoices.isEmpty()) {
			if (index < nextChoices.size()) {
				Questline chosen = nextChoices.get(index);
				nextChoices = List.of();
				Observation obs = hq.lastObservation();
				if (obs == null) {
					obs = new Observation();
					obs.now = System.currentTimeMillis();
				}
				hq.quests().startTrail(chosen.id(), obs, this);
			}
			return;
		}
		if (hq.quests().sidequestActive()) {
			hq.quests().answerSidequest(index, this);
		} else if (quizActive) {
			answerQuiz(index);
		}
	}

	// ---------------------------------------------------------------- feedback

	@Override
	public void banner(String ko, String de, boolean success) {
		bannerKo = ko == null ? "" : ko;
		bannerDe = de != null && hq.config().questGerman ? de : "";
		bannerUntil = System.currentTimeMillis() + 3500;
	}

	@Override
	public void wordCard(Word word) {
		wordCard = word;
		wordCardUntil = System.currentTimeMillis() + Math.max(1000, hq.config().cardDurationMs);
	}

	@Override
	public void stepCompleted(String praiseKo, String nextClueKo, String nextClueDe) {
		celebration.trigger(Celebration.Kind.STEP, praiseKo, nextClueKo, "",
				hq.config().questGerman ? nextClueDe : "", "", "");
		celebrationSound(Celebration.Kind.STEP);
		Celebration.particles(mc, Celebration.Kind.STEP);
	}

	@Override
	public void sidequestCompleted(Word word) {
		String learned = word == null ? "" : de.zirks.hangulquest.quest.KoGrammar.eul(word.ko) + " 배웠어요.";
		String german = word == null || !hq.config().questGerman ? ""
				: "Nebenquest geschafft: " + word.deShort;
		celebration.trigger(Celebration.Kind.SIDE, "사이드 퀘스트 완료!", learned, "", german, "",
				word == null ? "" : word.iconItem);
		celebrationSound(Celebration.Kind.SIDE);
		Celebration.particles(mc, Celebration.Kind.SIDE);
	}

	@Override
	public void chooseNext(List<Questline> options) {
		nextChoices = new ArrayList<>(options.subList(0, Math.min(5, options.size())));
		nextChoicesUntil = System.currentTimeMillis() + 60_000L;
	}

	@Override
	public void trailCompleted(Questline trail) {
		celebration.trigger(Celebration.Kind.TRAIL, "퀘스트 완료!",
				trail.titleKo(), trail.finaleKo(),
				hq.config().questGerman ? trail.finaleDe() : "",
				hq.config().questGerman ? "★ " + trail.titleDe() : "", "");
		celebrationSound(Celebration.Kind.TRAIL);
		Celebration.particles(mc, Celebration.Kind.TRAIL);
	}

	private void celebrationSound(Celebration.Kind kind) {
		if (!hq.config().sound) {
			return;
		}
		try {
			var manager = mc.getSoundManager();
			switch (kind) {
				case STEP -> manager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F));
				case SIDE -> manager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.6F));
				case TRAIL -> {
					manager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.8F));
					manager.playDelayed(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.8F), 6);
					manager.playDelayed(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 0.4F), 2);
				}
			}
		} catch (RuntimeException ignored) {
			// celebration audio is cosmetic
		}
	}

	@Override
	public void sound(boolean success) {
		if (!hq.config().sound) {
			return;
		}
		try {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(
					success ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F));
		} catch (RuntimeException ignored) {
			// sound is cosmetic
		}
	}

	public void showHint(QuestEngine.HintView view, long now) {
		if (now - lastHintAt < 1500) {
			return;
		}
		lastHintAt = now;
		this.hint = view;
		this.hintUntil = now + 20_000L;
	}

	public boolean hintVisible() {
		return hint != null;
	}

	public void dismissHint() {
		hint = null;
	}

	// ------------------------------------------------------------------ review

	public void startFromDue() {
		LearningService learn = hq.learn();
		if (learn == null) {
			return;
		}
		List<Word> due = learn.dueWords(System.currentTimeMillis(), 6);
		if (due.isEmpty()) {
			return;
		}
		Collections.shuffle(due, new Random());
		startQuiz(due.get(0));
	}

	public void startQuiz(Word word) {
		if (word == null) {
			return;
		}
		Random random = new Random();
		List<String> options = new ArrayList<>();
		options.add(word.deShort);
		for (Word distractor : hq.catalog().distractors(word, 2, random)) {
			if (!options.contains(distractor.deShort)) {
				options.add(distractor.deShort);
			}
		}
		Collections.shuffle(options, random);
		quizWord = word;
		quizOptions = options;
		quizCorrect = options.indexOf(word.deShort);
		quizActive = true;
	}

	private void answerQuiz(int index) {
		if (quizWord == null) {
			quizActive = false;
			return;
		}
		boolean correct = index == quizCorrect;
		long now = System.currentTimeMillis();
		List<Achievements.Achievement> unlocked = hq.learn().answer(quizWord.id, correct, now);
		quizActive = false;
		lastQuizAt = now;
		quizResultWord = quizWord;
		quizResultCorrect = correct;
		quizResultUntil = now + 2400L;
		sound(correct);
		for (Achievements.Achievement achievement : unlocked) {
			banner(achievement.ko(), achievement.de(), true);
		}
	}

	// ----------------------------------------------------------------- drawing

	private void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (mc.player == null || mc.gui.screen() != null) {
			return;
		}
		if (!hq.config().enabled || !hq.config().hudCards) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < bannerUntil) {
			drawBanner(graphics, bannerKo, bannerDe);
		}
		if (hq.quests() != null && hq.config().quests && hq.config().questTracker) {
			drawQuestLog(graphics);
		}
		boolean hintVisible = hint != null && now < hintUntil;
		if (hintVisible) {
			drawHint(graphics);
		} else if (hq.quests() != null && hq.quests().sidequestActive()) {
			drawSideQuest(graphics);
		} else if (quizActive) {
			drawQuiz(graphics);
		} else if (quizResultWord != null && now < quizResultUntil) {
			drawQuizResult(graphics);
		}
		if (wordCard != null && now < wordCardUntil && !quizActive && !hintVisible
				&& !hq.quests().sidequestActive()) {
			drawWordCard(graphics, wordCard);
		}
		if (!nextChoices.isEmpty() && mc.gui.screen() == null) {
			drawNextChoices(graphics);
		}
		celebration.draw(graphics, mc.font, mc.getWindow().getGuiScaledWidth(),
				mc.getWindow().getGuiScaledHeight(), now);
	}

	/** Dev self-test helper: play one celebration tier for screenshots. */
	private void drawNextChoices(GuiGraphicsExtractor graphics) {
		var font = mc.font;
		int width = 300;
		int rows = nextChoices.size();
		int height = 24 + rows * 14 + 14;
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = mc.getWindow().getGuiScaledHeight() / 2 - height / 2 - 20;
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, font, "다음 퀘스트", x + 8, y + 6, Draw.GOLD);
		int rowY = y + 22;
		for (int i = 0; i < rows; i++) {
			Questline trail = nextChoices.get(i);
			String label = (i + 1) + ". " + trail.titleKo();
			Draw.korean(graphics, font, label, x + 8, rowY, Draw.WHITE);
			if (hq.config().questGerman) {
				Draw.truncated(graphics, font, Component.literal(trail.titleDe()),
						x + 150, rowY, width - 158, Draw.GRAY);
			}
			rowY += 14;
		}
		Draw.label(graphics, font, "1–" + rows + ": 선택", x + 8, y + height - 12, Draw.DIM);
	}

	/** Dev self-test helper: start a quiz for a known word. */
	public void selftestStartQuiz(String wordId) {
		hq.catalog().byId(wordId).ifPresent(this::startQuiz);
	}

	/** Dev self-test helper: answer the visible quiz correctly, revealing the icon. */
	public void selftestRevealQuiz() {
		if (quizActive) {
			answerQuiz(quizCorrect);
		}
	}

	public void selftestCelebration(Celebration.Kind kind) {
		switch (kind) {
			case STEP -> stepCompleted("좋아요!", "다음 단서: 조약돌을 세 개 놓아요.", "");
			case SIDE -> sidequestCompleted(hq.catalog().byId("concept:stone").orElse(null));
			case TRAIL -> {
				Questline trail = hq.quests().trails().isEmpty() ? null : hq.quests().trails().get(0);
				if (trail != null) {
					trailCompleted(trail);
				}
			}
		}
	}

	/**
	 * Questie-style quest log: one compact, background-free list of the tracked quests. Text carries a
	 * drop shadow instead of a panel, every quest is two tight lines, and counting goals show their
	 * number inline ("0/3") instead of a bar, so the log stays out of the way of the world.
	 */
	private void drawQuestLog(GuiGraphicsExtractor graphics) {
		QuestEngine quests = hq.quests();
		List<QuestEngine.TrackView> views = quests.trackedViews();
		if (views.isEmpty()) {
			return;
		}
		var font = mc.font;
		int limit = Math.max(1, Math.min(views.size(), hq.config().questTrackerRows));
		List<QuestEngine.TrackView> shown = views.subList(0, limit);
		boolean more = views.size() > limit;

		int x = 6;
		int y = 6;
		int maxWidth = Math.min(260, mc.getWindow().getGuiScaledWidth() - 16);
		int width = 120;
		for (QuestEngine.TrackView view : shown) {
			width = Math.max(width, Math.min(maxWidth, titleWidth(font, view)));
			width = Math.max(width, Math.min(maxWidth, objectiveWidth(font, view)));
		}
		width = Math.min(maxWidth, width);

		int rowHeight = 24;
		int height = shown.size() * rowHeight + (more ? 9 : 0);

		int row = y;
		for (QuestEngine.TrackView view : shown) {
			drawQuestRow(graphics, view, x, row, width);
			row += rowHeight;
		}
		if (more) {
			drawShadowed(graphics, font, Component.literal("+" + (views.size() - limit)), x, row,
					Draw.DIM);
		}
		this.questLogBottom = y + height;
	}

	private int titleWidth(net.minecraft.client.gui.Font font, QuestEngine.TrackView view) {
		return font.width(KoText.ko(view.titleKo())) + 8
				+ font.width(Component.literal(counter(view)));
	}

	private int objectiveWidth(net.minecraft.client.gui.Font font, QuestEngine.TrackView view) {
		int width = font.width(objective(view));
		String status = conditionStatus(view);
		if (!status.isEmpty()) {
			width += font.width(Component.literal(" · ")) + font.width(KoText.ko(status));
		}
		return width;
	}

	/** The clue plus the count of a counting goal, e.g. "회색 돌을 세 개 캐요. 0/3". */
	private Component objective(QuestEngine.TrackView view) {
		Component clue = KoText.ko(view.clueKo());
		int needed = view.needed();
		if (!view.countable() || needed <= 1) {
			return clue;
		}
		return Component.empty().append(clue)
				.append(Component.literal(" " + view.progress() + "/" + needed));
	}

	/** Only condition messages ("밤 필요") are worth a second sentence; counts are already inline. */
	private static String conditionStatus(QuestEngine.TrackView view) {
		String status = view.status();
		if (status == null || status.isBlank() || status.contains("/")) {
			return "";
		}
		return status;
	}

	private int drawQuestRow(GuiGraphicsExtractor graphics, QuestEngine.TrackView view, int x, int y,
							 int width) {
		var font = mc.font;
		int color = view.focused() ? Draw.GOLD : Draw.WHITE;
		drawShadowed(graphics, font, KoText.ko(view.titleKo()), x, y, color);
		String counter = counter(view);
		int counterX = x + width - font.width(Component.literal(counter));
		drawShadowed(graphics, font, Component.literal(counter), counterX, y, Draw.DIM);

		Component objective = objective(view);
		int objectiveWidth = font.width(objective);
		String status = conditionStatus(view);
		Component line = objective;
		if (!status.isEmpty() && objectiveWidth + 6 + font.width(KoText.ko(status)) <= width) {
			line = Component.empty().append(objective)
					.append(Component.literal(" · "))
					.append(KoText.ko(status));
		}
		Draw.truncated(graphics, font, line, x, y + 11, width,
				view.focused() ? Draw.WHITE : Draw.GRAY, true);
		return y;
	}

	/** Flat HUD text with the vanilla drop shadow, which is what makes text readable without a panel. */
	private static void drawShadowed(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
									 Component text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, true);
	}

	private static String counter(QuestEngine.TrackView view) {
		return (view.stepIndex() + 1) + "/" + view.stepCount();
	}

	private void drawHint(GuiGraphicsExtractor graphics) {
		var font = mc.font;
		int width = Math.min(320, mc.getWindow().getGuiScaledWidth() - 16);
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = Math.max(questLogBottom + 6, mc.getWindow().getGuiScaledHeight() / 4);
		int textWidth = width - 14;
		Component clue = KoText.ko(hint.clueKo() == null ? "" : hint.clueKo());
		Component simplified = KoText.ko(hint.simplifiedKo() == null ? "" : hint.simplifiedKo());
		Component description = hint.descriptionKo() == null || hint.descriptionKo().isBlank()
				? null : KoText.ko(hint.descriptionKo());
		Component side = hint.word() == null || !hint.sidequestStarted() ? null
				: KoText.ko("사이드 퀘스트: " + hint.word().ko + " 알아보기");
		int clueHeight = Draw.wrappedHeight(font, clue, textWidth, 2);
		int simpleHeight = Draw.wrappedHeight(font, simplified, textWidth, 2);
		int descriptionHeight = description == null ? 0 : Draw.wrappedHeight(font, description, textWidth, 2);
		int sideHeight = side == null ? 0 : font.lineHeight + 2;
		int height = 18 + clueHeight + 4 + simpleHeight + 4 + descriptionHeight + 2 + sideHeight + 12;
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, font, "힌트", x + 7, y + 5, Draw.GOLD);
		int cursor = y + 17;
		Draw.wrapped(graphics, font, clue, x + 7, cursor, textWidth, Draw.DIM, 2);
		cursor += clueHeight + 4;
		Draw.wrapped(graphics, font, simplified, x + 7, cursor, textWidth, Draw.WHITE, 2);
		cursor += simpleHeight + 4;
		if (description != null) {
			Draw.wrapped(graphics, font, description, x + 7, cursor, textWidth, Draw.BLUE, 2);
			cursor += descriptionHeight + 2;
		}
		if (side != null) {
			Draw.truncated(graphics, font, side, x + 7, cursor, textWidth, Draw.GOLD);
		}
	}

	private void drawSideQuest(GuiGraphicsExtractor graphics) {
		SideQuest side = hq.quests().sideQuest();
		if (side == null) {
			return;
		}
		var font = mc.font;
		int width = 320;
		int height = 128;
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = (mc.getWindow().getGuiScaledHeight() - height) / 2;
		Draw.panel(graphics, x, y, width, height, 0);
		Component title = Component.empty().append(KoText.ko("사이드 퀘스트 " + (side.stage() == 1 ? "1/2" : "2/2")));
		Draw.truncated(graphics, font, title, x + 8, y + 6, width - 16, Draw.GOLD);
		Draw.question(graphics, font, x + 8, y + 20, 16);
		if (side.reversed()) {
			Draw.label(graphics, font, side.prompt(), x + 30, y + 22, Draw.WHITE);
		} else {
			Draw.korean(graphics, font, side.prompt(), x + 30, y + 22, Draw.WHITE);
		}
		int rowY = y + 46;
		List<String> options = side.options();
		for (int i = 0; i < Math.min(3, options.size()); i++) {
			String option = options.get(i);
			Draw.label(graphics, font, (i + 1) + ".", x + 10, rowY + i * 18, Draw.DIM);
			if (side.reversed()) {
				Draw.korean(graphics, font, option, x + 28, rowY + i * 18, Draw.WHITE);
			} else {
				Draw.truncated(graphics, font, Component.literal(option), x + 28, rowY + i * 18,
						width - 44, Draw.WHITE);
			}
		}
		Draw.korean(graphics, font, "1 2 3   ·   답을 골라요", x + 8, y + height - 12, Draw.DIM);
	}

	private void drawQuizResult(GuiGraphicsExtractor graphics) {
		if (quizResultWord == null) {
			return;
		}
		var font = mc.font;
		int width = 300;
		int height = 74;
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = (mc.getWindow().getGuiScaledHeight() - height) / 2;
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, font, quizResultCorrect ? "정답!" : "다시!", x + 8, y + 6,
				quizResultCorrect ? Draw.GREEN : Draw.RED);
		Draw.itemIcon(graphics, quizResultWord.iconItem, x + 8, y + 24);
		Draw.korean(graphics, font, quizResultWord.ko, x + 30, y + 24, Draw.WHITE);
		Draw.truncated(graphics, font, Component.literal(quizResultWord.deShort), x + 30, y + 39,
				width - 40, Draw.GRAY);
		Draw.label(graphics, font, quizResultCorrect ? "Richtig." : "Die richtige Antwort steht oben.",
				x + 8, y + height - 12, Draw.DIM);
	}

	private void drawQuiz(GuiGraphicsExtractor graphics) {
		if (quizWord == null) {
			return;
		}
		var font = mc.font;
		int width = 300;
		int height = 112;
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = (mc.getWindow().getGuiScaledHeight() - height) / 2;
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, font, "복습", x + 8, y + 6, Draw.GOLD);
		Draw.question(graphics, font, x + 8, y + 20, 16);
		Draw.korean(graphics, font, quizWord.ko, x + 30, y + 22, Draw.WHITE);
		for (int i = 0; i < Math.min(3, quizOptions.size()); i++) {
			Draw.label(graphics, font, (i + 1) + ".", x + 10, y + 46 + i * 18, Draw.DIM);
			Draw.truncated(graphics, font, Component.literal(quizOptions.get(i)), x + 28,
					y + 46 + i * 18, width - 44, Draw.WHITE);
		}
	}

	private void drawWordCard(GuiGraphicsExtractor graphics, Word word) {
		var font = mc.font;
		Component korean = KoText.ko(word.ko);
		Component german = Component.literal(word.deShort);
		int textWidth = Math.max(font.width(korean), font.width(german));
		textWidth = Math.min(textWidth, 220);
		int width = 28 + textWidth + 7;
		int height = 6 + font.lineHeight * 2 + 5;
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int screenHeight = mc.getWindow().getGuiScaledHeight();
		width = Math.min(width, screenWidth - 12);
		int x = screenWidth - width - 6;
		int y = screenHeight - height - 6;
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.itemIcon(graphics, word.iconItem, x + 6, y + (height - 16) / 2);
		Draw.truncated(graphics, font, korean, x + 27, y + 5, width - 33, Draw.WHITE);
		Draw.truncated(graphics, font, german, x + 27, y + 6 + font.lineHeight, width - 33, Draw.GRAY);
	}

	private void drawBanner(GuiGraphicsExtractor graphics, String ko, String de) {
		var font = mc.font;
		int width = Math.min(300, mc.getWindow().getGuiScaledWidth() - 12);
		int height = 34;
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = mc.getWindow().getGuiScaledHeight() - height - 70;
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.truncated(graphics, font, KoText.ko(ko), x + 8, y + 5, width - 16, Draw.GOLD);
		Draw.truncated(graphics, font, Component.literal(de), x + 8, y + 17, width - 16, Draw.GRAY);
	}
}
