package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SideQuest;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.SafetyGate;
import io.github.leks2205.hangulquest.ui.quiz.QuizOutcome;
import io.github.leks2205.hangulquest.ui.quiz.QuizScreen;
import io.github.leks2205.hangulquest.ui.quiz.QuizSession;
import io.github.leks2205.hangulquest.ui.quiz.QuizView;
import io.github.leks2205.hangulquest.util.I18n;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * All in-game cards: quest tracker, hint panel, sidequest question, quiz prompt, word card, banner.
 * Nothing here reads raw keys: quizzes, sidequest questions and the next-quest choice are answered in
 * {@link QuizScreen}, which is opened with the rebindable answer key.
 */
public final class HudController implements QuestEngine.Feedback {
	private final HangulQuestClient hq;
	private final Minecraft mc = Minecraft.getInstance();

	private final Celebration celebration = new Celebration();
	private int questReloadTicks;
	private List<Questline> nextChoices = List.of();
	private long nextChoicesUntil;
	private Word wordCard;
	private long wordCardUntil;
	private Component wordCardKo;
	private Component wordCardDe;
	private Component bannerKoText = Component.empty();
	private Component bannerDeText = Component.empty();
	private long bannerUntil;
	private QuestEngine.HintView hint;
	private HintLayout hintLayout;
	private long hintUntil;
	/** Counts client ticks; the quest log is rebuilt once per tick, not once per frame. */
	private long tickCount;
	private final QuestLogView questLog = new QuestLogView();
	private long lastHintAt;

	private final QuizSession session = new QuizSession();
	/** Set when a quiz was requested while another screen (the journal) was still open. */
	private boolean pendingOpen;
	private long lastQuizAt;
	private Word quizResultWord;
	private boolean quizResultCorrect;
	private long quizResultUntil;

	public HudController(HangulQuestClient hq) {
		this.hq = hq;
	}

	public void register() {
		HudElementRegistry.addLast(HangulQuestClient.id("hud"), this::extract);
	}

	/** The first automatic review should wait a full interval after joining. */
	public void onWorldJoin() {
		questLog.invalidate();
		lastQuizAt = System.currentTimeMillis();
		session.dismiss();
		pendingOpen = false;
	}

	public void tick(Observation obs) {
		tickCount++;
		if (hq.quests() == null || hq.learn() == null) {
			return;
		}
		if (++questReloadTicks >= 40) {
			questReloadTicks = 0;
			hq.reloadQuestlinesIfChanged(obs);
		}
		long now = System.currentTimeMillis();
		celebration.tickParticles(mc, now);
		if (!nextChoices.isEmpty()) {
			if (hq.quests().pendingNextChoices().isEmpty()) {
				// picked, or the player went on with another trail; other active trails do not count
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
		session.tick(now); // an unanswered prompt just disappears; nothing is recorded
		if (pendingOpen && mc.gui.screen() == null) {
			pendingOpen = false;
			if (quizView() != null) {
				mc.setScreenAndShow(new QuizScreen(this));
			}
		}
		if (session.state() == QuizSession.State.IDLE && !hq.quests().sidequestActive()
				&& hq.config().autoQuiz
				&& now - lastQuizAt > Math.max(1, hq.config().quizIntervalMinutes) * 60_000L
				&& SafetyGate.safeForQuiz(obs)) {
			offerFromDue(now);
		}
	}

	// ------------------------------------------------------------- quiz screen

	/** The answer key: opens whatever is waiting for an answer, or starts a review of due words. */
	public void openAnswer() {
		if (mc.gui.screen() != null || hq.quests() == null || hq.learn() == null) {
			return;
		}
		if (quizView() != null || session.open(System.currentTimeMillis())) {
			mc.setScreenAndShow(new QuizScreen(this));
		} else if (!startFromDue()) {
			banner("지금은 복습할 단어가 없어요.", I18n.t("hangulquest.ui.hud.nothing_to_review"), false);
		}
	}

	/** What the quiz screen shows now, or null when nothing is waiting for an answer. */
	public QuizView quizView() {
		if (hq.quests() == null) {
			return null;
		}
		boolean translations = hq.config().questTranslations;
		if (!nextChoices.isEmpty()) {
			List<QuizView.Option> options = new ArrayList<>();
			for (Questline trail : nextChoices) {
				options.add(new QuizView.Option(trail.titleKo(), translations
						? trail.title(io.github.leks2205.hangulquest.util.Lang.current()) : ""));
			}
			return new QuizView(QuizView.Kind.NEXT_QUEST, "다음 퀘스트", "", true, null, options, true);
		}
		SideQuest side = hq.quests().sideQuest();
		if (hq.quests().sidequestActive() && side != null) {
			List<QuizView.Option> options = new ArrayList<>();
			for (String option : side.options()) {
				options.add(new QuizView.Option(option));
			}
			return new QuizView(QuizView.Kind.SIDEQUEST,
					"사이드 퀘스트 " + (side.stage() == 1 ? "1/2" : "2/2"),
					side.prompt(), !side.reversed(), null, options, side.reversed());
		}
		QuizSession.Quiz quiz = session.state() == QuizSession.State.ACTIVE ? session.quiz() : null;
		Optional<Word> word = quiz == null ? Optional.empty() : hq.catalog().byId(quiz.wordId());
		if (word.isEmpty()) {
			return null;
		}
		List<QuizView.Option> options = new ArrayList<>();
		for (String option : quiz.options()) {
			options.add(new QuizView.Option(option));
		}
		return new QuizView(QuizView.Kind.REVIEW, "복습", word.get().ko, true, null, options, false);
	}

	/** An answer given in the quiz screen (key 1-5 or a click). The only path that answers anything. */
	public QuizOutcome answerInScreen(int index) {
		QuizView view = quizView();
		if (view == null || index < 0 || index >= view.options().size()) {
			return QuizOutcome.NONE;
		}
		switch (view.kind()) {
			case NEXT_QUEST -> {
				Questline chosen = nextChoices.get(index);
				nextChoices = List.of();
				Observation obs = hq.lastObservation();
				if (obs == null) {
					obs = new Observation();
					obs.now = System.currentTimeMillis();
				}
				hq.quests().startTrail(chosen.id(), obs, this);
				return QuizOutcome.NONE;
			}
			case SIDEQUEST -> {
				return hq.quests().answerSidequest(index, this) ? QuizOutcome.CORRECT : QuizOutcome.WRONG;
			}
			default -> {
				return answerReview(index);
			}
		}
	}

	/** The quiz screen went away: an unanswered review is dropped, nothing is recorded. */
	public void dismissQuiz() {
		session.dismiss();
	}

	private QuizOutcome answerReview(int index) {
		long now = System.currentTimeMillis();
		Optional<QuizSession.Result> result = session.answer(index, (wordId, correct) -> {
			List<Achievements.Achievement> unlocked = hq.learn().answer(wordId, correct, now);
			lastQuizAt = now;
			quizResultWord = hq.catalog().byId(wordId).orElse(null);
			quizResultCorrect = correct;
			quizResultUntil = now + 2400L;
			sound(correct);
			for (Achievements.Achievement achievement : unlocked) {
				banner(achievement.ko(), achievement.label(), true);
			}
		});
		return result.map(r -> r.correct() ? QuizOutcome.CORRECT : QuizOutcome.WRONG).orElse(QuizOutcome.NONE);
	}

	// ---------------------------------------------------------------- feedback

	@Override
	public void banner(String ko, String de, boolean success) {
		bannerKoText = KoText.ko(ko == null ? "" : ko);
		bannerDeText = Component.literal(de != null && hq.config().questTranslations ? de : "");
		bannerUntil = System.currentTimeMillis() + 3500;
	}

	@Override
	public void wordCard(Word word) {
		wordCard = word;
		if (word == null) {
			return;
		}
		wordCardKo = KoText.ko(word.ko);
		wordCardDe = Component.literal(glossOf(word));
		wordCardUntil = System.currentTimeMillis() + Math.max(1000, hq.config().cardDurationMs);
	}

	@Override
	public void stepCompleted(String praiseKo, String nextClueKo, String nextClueDe) {
		celebration.trigger(Celebration.Kind.STEP, praiseKo, nextClueKo, "",
				hq.config().questTranslations ? nextClueDe : "", "", "");
		celebrationSound(Celebration.Kind.STEP);
		Celebration.particles(mc, Celebration.Kind.STEP);
	}

	@Override
	public void sidequestCompleted(Word word) {
		String learned = word == null ? "" : io.github.leks2205.hangulquest.quest.KoGrammar.eul(word.ko) + " 배웠어요.";
		String german = word == null || !hq.config().questTranslations ? ""
				: I18n.t("hangulquest.ui.quest.sidequest_done", glossOf(word));
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
				hq.config().questTranslations ? trail.finale(io.github.leks2205.hangulquest.util.Lang.current()) : "",
				hq.config().questTranslations ? "★ " + trail.title(io.github.leks2205.hangulquest.util.Lang.current()) : "", "");
		celebrationSound(Celebration.Kind.TRAIL);
		Celebration.particles(mc, Celebration.Kind.TRAIL);
	}

	private static String glossOf(Word word) {
		return word.gloss(io.github.leks2205.hangulquest.util.Lang.current());
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
		this.hintLayout = null;
		this.hintUntil = now + 20_000L;
	}

	public boolean hintVisible() {
		return hint != null;
	}

	public void dismissHint() {
		hint = null;
	}

	// ------------------------------------------------------------------ review

	/** A review the player asked for (review key, journal): opens in the quiz screen on the next tick. */
	public boolean startFromDue() {
		QuizSession.Quiz quiz = dueQuiz();
		if (quiz == null) {
			return false;
		}
		session.begin(quiz);
		pendingOpen = true;
		return true;
	}

	/** An automatic review: only a prompt, answerable once the player opens it. */
	private void offerFromDue(long now) {
		QuizSession.Quiz quiz = dueQuiz();
		if (quiz != null && session.offer(quiz, now)) {
			lastQuizAt = now;
		}
	}

	private QuizSession.Quiz dueQuiz() {
		LearningService learn = hq.learn();
		if (learn == null) {
			return null;
		}
		List<Word> due = learn.dueWords(System.currentTimeMillis(), 6);
		if (due.isEmpty()) {
			return null;
		}
		Collections.shuffle(due, new Random());
		return buildQuiz(due.get(0));
	}

	private QuizSession.Quiz buildQuiz(Word word) {
		Random random = new Random();
		List<String> options = new ArrayList<>();
		options.add(glossOf(word));
		for (Word distractor : hq.catalog().distractors(word, 2, random)) {
			if (!options.contains(glossOf(distractor))) {
				options.add(glossOf(distractor));
			}
		}
		Collections.shuffle(options, random);
		return new QuizSession.Quiz(word.id, options, options.indexOf(glossOf(word)));
	}

	// ----------------------------------------------------------------- drawing

	private void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (mc.player == null || mc.gui.screen() != null) {
			return;
		}
		if (!hq.config().enabled || !hq.config().hudCards) {
			return;
		}
		boolean hudHidden = mc.gui.hud.isHidden();
		if (!HudVisibility.cards(hudHidden)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < bannerUntil) {
			drawBanner(graphics);
		}
		if (hq.quests() != null && HudVisibility.questLog(hudHidden,
				mc.gui.hud.getDebugOverlay().showDebugScreen(), hq.config().quests, hq.config().questTracker)) {
			drawQuestLog(graphics);
		}
		boolean hintVisible = hint != null && now < hintUntil;
		if (hintVisible) {
			drawHint(graphics);
		} else if (hq.quests() != null && hq.quests().sidequestActive()) {
			drawSideQuest(graphics);
		} else if (session.promptVisible(now)) {
			drawQuizPrompt(graphics, now);
		} else if (quizResultWord != null && now < quizResultUntil) {
			drawQuizResult(graphics);
		}
		if (wordCard != null && now < wordCardUntil && !hintVisible
				&& !hq.quests().sidequestActive()) {
			drawWordCard(graphics, wordCard);
		}
		if (!nextChoices.isEmpty()) {
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
			if (hq.config().questTranslations) {
				Draw.truncated(graphics, font, Component.literal(trail.title(io.github.leks2205.hangulquest.util.Lang.current())),
						x + 150, rowY, width - 158, Draw.GRAY);
			}
			rowY += 14;
		}
		drawAnswerHint(graphics, x + 8, y + height - 12);
	}

	/** Dev self-test helper: start a quiz for a known word. */
	public void selftestStartQuiz(String wordId) {
		hq.catalog().byId(wordId).ifPresent(word -> {
			session.begin(buildQuiz(word));
			pendingOpen = true;
		});
	}

	/** Dev self-test helper: answer the visible quiz correctly, revealing the icon. */
	public void selftestRevealQuiz() {
		QuizSession.Quiz quiz = session.quiz();
		if (session.state() == QuizSession.State.ACTIVE && quiz != null) {
			answerReview(quiz.correctIndex());
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
	 * The rows are built once per tick; a frame only draws them.
	 */
	private void drawQuestLog(GuiGraphicsExtractor graphics) {
		int rowLimit = hq.config().questTrackerRows;
		int guiWidth = mc.getWindow().getGuiScaledWidth();
		if (questLog.due(tickCount, rowLimit, guiWidth)) {
			questLog.rebuild(mc.font, hq.quests().trackedViews(), rowLimit, guiWidth);
		}
		questLog.draw(graphics, mc.font);
	}

	/** A hint card's texts, wrapped lines and heights; rebuilt only for a new hint or a new window width. */
	private record HintLayout(QuestEngine.HintView source, Font font, int guiWidth, int width,
							  List<FormattedCharSequence> clue, List<FormattedCharSequence> simplified,
							  List<FormattedCharSequence> description, Component side,
							  int clueHeight, int simpleHeight, int descriptionHeight, int height) {
	}

	private HintLayout hintLayout(Font font) {
		int guiWidth = mc.getWindow().getGuiScaledWidth();
		HintLayout cached = hintLayout;
		if (cached != null && cached.source() == hint && cached.font() == font && cached.guiWidth() == guiWidth) {
			return cached;
		}
		int width = Math.min(320, guiWidth - 16);
		int textWidth = width - 14;
		Component clue = KoText.ko(hint.clueKo() == null ? "" : hint.clueKo());
		Component simplified = KoText.ko(hint.simplifiedKo() == null ? "" : hint.simplifiedKo());
		Component description = hint.descriptionKo() == null || hint.descriptionKo().isBlank()
				? null : KoText.ko(hint.descriptionKo());
		Component side = hint.word() == null || !hint.sidequestStarted() ? null
				: KoText.ko("사이드 퀘스트: " + hint.word().ko + " 알아보기");
		List<FormattedCharSequence> clueLines = Draw.lines(font, clue, textWidth);
		List<FormattedCharSequence> simpleLines = Draw.lines(font, simplified, textWidth);
		List<FormattedCharSequence> descriptionLines = description == null ? null
				: Draw.lines(font, description, textWidth);
		int clueHeight = Draw.linesHeight(font, clueLines, 2);
		int simpleHeight = Draw.linesHeight(font, simpleLines, 2);
		int descriptionHeight = descriptionLines == null ? 0 : Draw.linesHeight(font, descriptionLines, 2);
		int sideHeight = side == null ? 0 : font.lineHeight + 2;
		int height = 18 + clueHeight + 4 + simpleHeight + 4 + descriptionHeight + 2 + sideHeight + 12;
		hintLayout = new HintLayout(hint, font, guiWidth, width, clueLines, simpleLines, descriptionLines, side,
				clueHeight, simpleHeight, descriptionHeight, height);
		return hintLayout;
	}

	private void drawHint(GuiGraphicsExtractor graphics) {
		var font = mc.font;
		HintLayout layout = hintLayout(font);
		int width = layout.width();
		int textWidth = width - 14;
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = Math.max(questLog.bottom() + 6, mc.getWindow().getGuiScaledHeight() / 4);
		Draw.panel(graphics, x, y, width, layout.height(), 0);
		Draw.korean(graphics, font, "힌트", x + 7, y + 5, Draw.GOLD);
		int cursor = y + 17;
		Draw.drawLines(graphics, font, layout.clue(), x + 7, cursor, Draw.DIM, 2);
		cursor += layout.clueHeight() + 4;
		Draw.drawLines(graphics, font, layout.simplified(), x + 7, cursor, Draw.WHITE, 2);
		cursor += layout.simpleHeight() + 4;
		if (layout.description() != null) {
			Draw.drawLines(graphics, font, layout.description(), x + 7, cursor, Draw.BLUE, 2);
			cursor += layout.descriptionHeight() + 2;
		}
		if (layout.side() != null) {
			Draw.truncated(graphics, font, layout.side(), x + 7, cursor, textWidth, Draw.GOLD);
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
		drawAnswerHint(graphics, x + 8, y + height - 12);
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
		Draw.truncated(graphics, font, Component.literal(glossOf(quizResultWord)), x + 30, y + 39,
				width - 40, Draw.GRAY);
		Draw.label(graphics, font, quizResultCorrect ? I18n.t("hangulquest.ui.hud.correct")
				: I18n.t("hangulquest.ui.hud.right_answer"),
				x + 8, y + height - 12, Draw.DIM);
	}

	/** "Quiz ready: press [key]". Shown for a short while, then it simply goes away. */
	private void drawQuizPrompt(GuiGraphicsExtractor graphics, long now) {
		var font = mc.font;
		Component title = KoText.ko("복습");
		Component text = Component.literal(I18n.t("hangulquest.ui.hud.quiz_ready", answerKeyLabel()));
		int width = 10 + font.width(title) + 8 + font.width(text) + 10;
		int height = 22;
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = 10;
		Draw.panel(graphics, x, y, width, height, 0);
		graphics.text(font, title, x + 10, y + 5, Draw.GOLD, false);
		graphics.text(font, text, x + 10 + font.width(title) + 8, y + 5, Draw.WHITE, false);
		int left = (int) ((width - 2) * session.promptRemainingMs(now) / QuizSession.PROMPT_MS);
		graphics.fill(x + 1, y + height - 3, x + 1 + left, y + height - 1, Draw.GOLD);
	}

	/** The footer of the sidequest and next-quest panels: they are answered in the quiz screen. */
	private void drawAnswerHint(GuiGraphicsExtractor graphics, int x, int y) {
		var font = mc.font;
		Component ko = KoText.ko("답을 골라요");
		graphics.text(font, ko, x, y, Draw.DIM, false);
		Draw.label(graphics, font, " · " + I18n.t("hangulquest.ui.hud.open_with", answerKeyLabel()),
				x + font.width(ko), y, Draw.DIM);
	}

	private String answerKeyLabel() {
		var key = hq.answerKey();
		return key == null ? "" : "[" + key.getTranslatedKeyMessage().getString() + "]";
	}

	private void drawWordCard(GuiGraphicsExtractor graphics, Word word) {
		var font = mc.font;
		Component korean = wordCardKo;
		Component german = wordCardDe;
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

	private void drawBanner(GuiGraphicsExtractor graphics) {
		var font = mc.font;
		int width = Math.min(300, mc.getWindow().getGuiScaledWidth() - 12);
		int height = 34;
		int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
		int y = mc.getWindow().getGuiScaledHeight() - height - 70;
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.truncated(graphics, font, bannerKoText, x + 8, y + 5, width - 16, Draw.GOLD);
		Draw.truncated(graphics, font, bannerDeText, x + 8, y + 17, width - 16, Draw.GRAY);
	}
}
