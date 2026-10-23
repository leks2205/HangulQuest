package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.culture.CultureCards;
import io.github.leks2205.hangulquest.culture.CultureHook;
import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SideQuest;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.quest.grammar.Pronunciation;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.SafetyGate;
import io.github.leks2205.hangulquest.ui.quiz.QuizOutcome;
import io.github.leks2205.hangulquest.ui.quiz.QuizScreen;
import io.github.leks2205.hangulquest.ui.quiz.QuizSession;
import io.github.leks2205.hangulquest.ui.quiz.QuizView;
import io.github.leks2205.hangulquest.ui.quiz.cards.Card;
import io.github.leks2205.hangulquest.ui.quiz.cards.CardFactory;
import io.github.leks2205.hangulquest.ui.quiz.cards.CardInfo;
import io.github.leks2205.hangulquest.ui.quiz.cards.CardRecording;
import io.github.leks2205.hangulquest.ui.quiz.cards.ClozeGrammarCard;
import io.github.leks2205.hangulquest.ui.quiz.cards.RecognitionMc4Card;
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
 *
 * <p>Every card except the quest log and the sidequest question is a window in {@link HudWindows}: it stays
 * on screen until the hint key closes it (see {@link #pressHintKey}), one at a time, with a "+N" badge for
 * the ones behind it. Other code shows one through {@link #windows()}, or through
 * {@code QuestEngine.Feedback#notice} from the quest side. Setting {@code autoCloseWindows} brings back the
 * old timeouts.
 */
public final class HudController implements QuestEngine.Feedback {
	/** The old timeouts, used only while "close windows automatically" is on. */
	private static final long HINT_MS = 20_000L;
	private static final long RULE_CARD_MS = 25_000L;
	private static final long BANNER_MS = 3_500L;
	private static final long RESULT_MS = 2_400L;
	private static final String NEXT_KEY = "nextquest";
	private static final String REVIEW_KEY = "review";

	private final HangulQuestClient hq;
	private final Minecraft mc = Minecraft.getInstance();

	private final HudWindows windows = new HudWindows();
	private List<Questline> nextChoices = List.of();
	/** When the first choice is started for the player (only with automatic closing), or the engine's own timer ended. */
	private long nextChoicesUntil;
	private HintLayout hintLayout;
	/** Counts client ticks; the quest log is rebuilt once per tick, not once per frame. */
	private long tickCount;
	private final QuestLogView questLog = new QuestLogView();
	private boolean questLogDrawn;
	private long lastHintAt;
	/** The word card in the bottom-right corner. It is not a window: only the word key shows it, and the hint key never closes it. */
	private WordCard wordCard;
	private long wordCardUntil;

	// What the windows carry. The texts are built once when the window is made.
	private record Notice(Component title, Component titleText, Component body, Component bodyText) {
	}

	private record WordCard(Word word, Component ko, Component gloss, Component pron) {
	}

	private record RuleCard(QuestEngine.RuleCardView view, Component examples, List<Component> lines) {
	}

	private record QuizResult(Word word, boolean correct) {
	}

	private final QuizSession session = new QuizSession();
	/** Set when a quiz was requested while another screen (the journal) was still open. */
	private boolean pendingOpen;
	private long lastQuizAt;

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
		windows.clear();
		nextChoices = List.of();
		wordCard = null;
	}

	/** The windows on the HUD. Show one with {@code windows().show(HudWindows.Window.of(...))}; see {@link HudWindows}. */
	public HudWindows windows() {
		return windows;
	}

	/** Whether the HUD draws windows right now; the hint key only closes a window the player can see. */
	private boolean windowsDisplayable() {
		return mc.player != null && mc.gui.screen() == null && hq.config().enabled && hq.config().hudCards
				&& HudVisibility.cards(mc.gui.hud.isHidden());
	}

	/**
	 * The hint key. With a window open it closes the top one and nothing else happens: {@code showHint} is not
	 * run, so the press is never a hint (no hint panel, no sidequest, no {@code QUEST_HINTED} grade, no count).
	 * With no window open it runs {@code showHint}. Returns true when it closed a window.
	 */
	public boolean pressHintKey(Runnable showHint) {
		return windows.pressHintKey(windowsDisplayable(), showHint);
	}

	public void tick(Observation obs) {
		tickCount++;
		if (hq.quests() == null || hq.learn() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean autoClose = hq.config().autoCloseWindows;
		windows.setAccepting(hq.config().enabled && hq.config().hudCards);
		tickNextChoices(now, autoClose);
		tickRuleCard();
		session.tick(now); // a prompt that timed out just disappears; nothing is recorded
		if (session.state() != QuizSession.State.PROMPT) {
			windows.remove(REVIEW_KEY);
		} else if (!windows.contains(REVIEW_KEY)) {
			session.dismiss(); // its window is gone (the HUD cards were switched off): nobody can see the prompt
		}
		if (nextChoices.isEmpty()) {
			windows.remove(NEXT_KEY);
		}
		windows.tick(now, windowsDisplayable(), autoClose);
		HudWindows.Window top = windows.top();
		if (top != null && top.shown() && top.content() instanceof Celebration celebration) {
			celebration.tickParticles(mc, now);
		}
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

	/**
	 * The open next-quest choice ends when it is picked or the player goes on with another trail. With
	 * automatic closing it also starts the first option after a minute; otherwise it waits for the player
	 * even after the engine's own timer has run out.
	 */
	private void tickNextChoices(long now, boolean autoClose) {
		if (nextChoices.isEmpty()) {
			return;
		}
		boolean engineDone = hq.quests().pendingNextChoices().isEmpty();
		if (autoClose) {
			if (engineDone) {
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
			return;
		}
		boolean started = nextChoices.stream().anyMatch(trail -> hq.quests().isTracked(trail.id()));
		if (started || (engineDone && now <= nextChoicesUntil)) {
			nextChoices = List.of(); // picked, or the player went on with another trail
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
		return new QuizView(QuizView.Kind.REVIEW, "복습", word.get().ko, true, null, options, false, quiz.card());
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

	/**
	 * The first attempt on the open card, from the quiz screen. The one place a card's answer is recorded:
	 * a retry or the feedback afterwards never comes here.
	 */
	public QuizOutcome recordCardAnswer(Card.Answer answer) {
		long now = System.currentTimeMillis();
		Optional<QuizSession.Result> result = session.answer(answer, now, (wordId, given, latencyMs) -> {
			List<Achievements.Achievement> unlocked = CardRecording.record(hq.learn(), wordId, given, latencyMs, now);
			lastQuizAt = now;
			sound(given.correct());
			for (Achievements.Achievement achievement : unlocked) {
				banner(achievement.ko(), achievement.label(), true);
			}
		});
		return result.map(r -> r.correct() ? QuizOutcome.CORRECT : QuizOutcome.WRONG).orElse(QuizOutcome.NONE);
	}

	private QuizOutcome answerReview(int index) {
		QuizSession.Quiz open = session.quiz();
		if (open != null && open.card() != null) {
			Optional<Card.Answer> given = open.card().choose(index);
			return given.isPresent() ? recordCardAnswer(given.get()) : QuizOutcome.NONE;
		}
		long now = System.currentTimeMillis();
		int optionCount = session.quiz() == null ? 4 : session.quiz().options().size();
		long latencyMs = now - session.activeSince();
		Optional<QuizSession.Result> result = session.answer(index, (wordId, correct) -> {
			List<Achievements.Achievement> unlocked = hq.learn().answerMc(wordId, optionCount, correct, latencyMs, now);
			lastQuizAt = now;
			Word asked = hq.catalog().byId(wordId).orElse(null);
			if (asked != null) {
				windows.show(HudWindows.Window.of(HudWindows.Kind.QUIZ_RESULT, "result:" + wordId + "@" + now,
						new QuizResult(asked, correct)).priority(1).autoCloseMs(RESULT_MS));
			}
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
		banner(ko, de, success, null);
	}

	/** A banner whose newer copies replace older ones of the same group that still wait (focus changes). */
	public void banner(String ko, String de, boolean success, String group) {
		String korean = ko == null ? "" : ko;
		String text = de != null && hq.config().questTranslations ? de : "";
		windows.show(HudWindows.Window.of(HudWindows.Kind.NOTICE, "banner:" + korean + "|" + text,
				new Notice(KoText.ko(korean), text.isBlank() ? null : Component.literal(text), null, null))
				.priority(1).group(group).autoCloseMs(BANNER_MS));
	}

	@Override
	public void notice(String key, String titleKo, String titleText, String bodyKo, String bodyText) {
		windows.show(HudWindows.Window.of(HudWindows.Kind.NOTICE, "notice:" + key,
				new Notice(KoText.ko(titleKo == null ? "" : titleKo),
						titleText == null || titleText.isBlank() ? null : Component.literal(titleText),
						bodyKo == null || bodyKo.isBlank() ? null : KoText.ko(bodyKo),
						bodyText == null || bodyText.isBlank() ? null : Component.literal(bodyText)))
				.priority(2).autoCloseMs(2 * BANNER_MS));
	}

	/** The word key: shows the word card in the bottom-right corner for a while and says the word. */
	public void wordCard(Word word) {
		if (word == null) {
			return;
		}
		String bracket = Pronunciation.of(word.ko, word.conceptId, LexiconTags.shared()).bracket();
		WordCard card = new WordCard(word, KoText.ko(word.ko), Component.literal(glossOf(word)),
				bracket == null ? null : KoText.ko(bracket));
		if (hq.audio() != null) {
			hq.audio().playWord(word.audioId()); // the player asked for it; silent without the voice pack
		}
		wordCard = card;
		wordCardUntil = System.currentTimeMillis() + Math.max(1000, hq.config().cardDurationMs);
	}

	@Override
	public void stepCompleted(String praiseKo, String nextClueKo, String nextClueDe) {
		celebrate(Celebration.Kind.STEP, praiseKo, nextClueKo, "",
				hq.config().questTranslations ? nextClueDe : "", "", "");
		celebrationSound(Celebration.Kind.STEP);
		Celebration.particles(mc, Celebration.Kind.STEP);
	}

	@Override
	public void sidequestCompleted(Word word) {
		String learned = word == null ? "" : io.github.leks2205.hangulquest.quest.KoGrammar.eul(word.ko) + " 배웠어요.";
		String german = word == null || !hq.config().questTranslations ? ""
				: I18n.t("hangulquest.ui.quest.sidequest_done", glossOf(word));
		celebrate(Celebration.Kind.SIDE, "사이드 퀘스트 완료!", learned, "", german, "",
				word == null ? "" : word.iconItem);
		celebrationSound(Celebration.Kind.SIDE);
		Celebration.particles(mc, Celebration.Kind.SIDE);
	}

	@Override
	public void chooseNext(List<Questline> options) {
		nextChoices = new ArrayList<>(options.subList(0, Math.min(5, options.size())));
		nextChoicesUntil = System.currentTimeMillis() + 60_000L;
		// Closing the window leaves the choice open: the answer key still opens it.
		windows.show(HudWindows.Window.of(HudWindows.Kind.NEXT_QUEST, NEXT_KEY, null).priority(2));
	}

	@Override
	public void trailCompleted(Questline trail) {
		trailCompleted(trail, trail.finaleKo());
	}

	@Override
	public void trailCompleted(Questline trail, String finaleKo) {
		celebrate(Celebration.Kind.TRAIL, "퀘스트 완료!",
				trail.titleKo(), finaleKo,
				hq.config().questTranslations ? trail.finale(io.github.leks2205.hangulquest.util.Lang.current()) : "",
				hq.config().questTranslations ? "★ " + trail.title(io.github.leks2205.hangulquest.util.Lang.current()) : "", "");
		celebrationSound(Celebration.Kind.TRAIL);
		Celebration.particles(mc, Celebration.Kind.TRAIL);
	}

	/** A completion card as a window: a trail's finale first, then a sidequest, then a step; steps replace waiting steps. */
	private void celebrate(Celebration.Kind kind, String titleKo, String lineKo, String line2Ko, String lineDe,
						   String badge, String iconItem) {
		Celebration card = new Celebration();
		card.trigger(kind, titleKo, lineKo, line2Ko, lineDe, badge, iconItem);
		int priority = switch (kind) {
			case STEP -> 1;
			case SIDE -> 2;
			case TRAIL -> 3;
		};
		windows.show(HudWindows.Window.of(HudWindows.Kind.CELEBRATION,
						"celebration:" + kind + ":" + titleKo + "|" + lineKo, card)
				.priority(priority).group(kind == Celebration.Kind.STEP ? "step" : null)
				.autoCloseMs(kind.duration).onShown(() -> card.begin(System.currentTimeMillis())));
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

	/** Shows the hint panel as a window. It stays until the hint key closes it. */
	public void showHint(QuestEngine.HintView view, long now) {
		if (now - lastHintAt < 1500) {
			return;
		}
		lastHintAt = now;
		hintLayout = null;
		String key = "hint:" + view.clueKo() + "|" + view.simplifiedKo();
		windows.show(HudWindows.Window.of(HudWindows.Kind.HINT, key, view).priority(2).autoCloseMs(HINT_MS));
	}

	/** Shows the rule card of a construction the focused step uses for the first time, once. */
	private void tickRuleCard() {
		if (mc.gui.screen() != null || !hq.config().enabled || !hq.config().hudCards) {
			return;
		}
		QuestEngine.RuleCardView pending = hq.quests().pendingRuleCard();
		if (pending == null || windows.contains("rule:" + pending.id())) {
			return;
		}
		RuleCard card = new RuleCard(pending, KoText.ko(String.join("   ", pending.examplesKo())),
				pending.rule().stream().map(KoText::mixed).toList());
		// it counts as met once it is on screen, not while it waits behind other windows
		windows.show(HudWindows.Window.of(HudWindows.Kind.RULE_CARD, "rule:" + pending.id(), card)
				.priority(1).autoCloseMs(RULE_CARD_MS).onShown(() -> hq.quests().ruleCardShown(pending.id())));
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
		boolean timed = hq.config().autoCloseWindows;
		if (quiz != null && session.offer(quiz, now, timed ? QuizSession.PROMPT_MS : QuizSession.NO_TIMEOUT)) {
			lastQuizAt = now;
			// The prompt is a window like the others; closing it with the hint key drops the quiz, records nothing.
			boolean shown = windows.show(HudWindows.Window.of(HudWindows.Kind.REVIEW_PROMPT, REVIEW_KEY, null)
					.priority(1).onClose(reason -> {
						if (reason != HudWindows.Closed.REMOVED) {
							session.dismiss();
						}
					}));
			if (!shown) {
				session.dismiss(); // nobody can see it, so it must not wait for ever
			}
		}
	}

	private QuizSession.Quiz dueQuiz() {
		LearningService learn = hq.learn();
		if (learn == null) {
			return null;
		}
		var probe = learn.dueProbe(System.currentTimeMillis());
		if (probe.isPresent()) {
			// A held-out word is asked as a plain four-option question; see LearningService.answerMc.
			Word probed = hq.catalog().byId(probe.get().wordId()).orElse(null);
			if (probed != null) {
				return buildMcQuiz(probed);
			}
		}
		Random random = new Random();
		if (CardFactory.grammarTurn(random)) {
			ClozeGrammarCard cloze = CardFactory.grammar(learn.grammar(), GrammarBook.shared(), random);
			if (cloze != null) {
				return QuizSession.Quiz.of(cloze.construction().id(), cloze);
			}
		}
		List<Word> due = learn.dueForPlay(System.currentTimeMillis(), 6);
		if (due.isEmpty()) {
			return null;
		}
		Collections.shuffle(due, random);
		return buildQuiz(due.get(0));
	}

	/** The card for a due word: recognition, or a productive card once the word is stable enough. */
	private QuizSession.Quiz buildQuiz(Word word) {
		WordProgress progress = hq.learn().data().words.get(word.id);
		Card card = CardFactory.create(word, progress, hq.catalog(), LexiconTags.shared(), new Random(),
				hq.learn().productiveUnlockDays(word.id), hq.config().typedCards);
		return QuizSession.Quiz.of(word.id, card);
	}

	/** Always recognition (four options): the dev self-test, and the held-out probes. */
	private QuizSession.Quiz buildMcQuiz(Word word) {
		return QuizSession.Quiz.of(word.id, RecognitionMc4Card.create(CardInfo.of(hq.catalog(), word),
				hq.catalog().all(), LexiconTags.shared(), new Random()));
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
		questLogDrawn = hq.quests() != null && HudVisibility.questLog(hudHidden,
				mc.gui.hud.getDebugOverlay().showDebugScreen(), hq.config().quests, hq.config().questTracker);
		if (questLogDrawn) {
			drawQuestLog(graphics);
		}
		// One window at a time, the top of the queue. The sidequest question is a panel, not a window: it
		// shows when no window is open.
		HudWindows.Window top = windows.top();
		if (top != null && top.shown()) {
			drawWindow(graphics, top, now);
		} else if (hq.quests() != null && hq.quests().sidequestActive()) {
			drawSideQuest(graphics);
		}
		if (wordCard != null && now < wordCardUntil) {
			drawWordCard(graphics, wordCard);
		}
	}

	/** Dev self-test helper: start a quiz for a known word. */
	public void selftestStartQuiz(String wordId) {
		hq.catalog().byId(wordId).ifPresent(word -> {
			session.begin(buildMcQuiz(word));
			pendingOpen = true;
		});
	}

	/** Dev self-test helper: answer the visible quiz correctly, revealing the icon. */
	public void selftestRevealQuiz() {
		QuizSession.Quiz quiz = session.quiz();
		if (session.state() == QuizSession.State.ACTIVE && quiz != null) {
			answerReview(quiz.card() instanceof RecognitionMc4Card mc ? mc.correctIndex() : quiz.correctIndex());
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

	/** Lines of each hint text; the card gets narrower beside the quest log, so the text may need more. */
	private static final int HINT_LINES = 4;

	/** A hint card's texts, wrapped lines and heights; rebuilt only for a new hint or a new window width. */
	private record HintLayout(QuestEngine.HintView source, Font font, int guiWidth, int width,
							  List<FormattedCharSequence> clue, List<FormattedCharSequence> simplified,
							  List<FormattedCharSequence> description, Component side,
							  int clueHeight, int simpleHeight, int descriptionHeight, int height) {
	}

	private HintLayout hintLayout(Font font, QuestEngine.HintView hint) {
		int guiWidth = mc.getWindow().getGuiScaledWidth();
		HintLayout cached = hintLayout;
		int width = widthFor(Math.min(320, guiWidth - 16));
		if (cached != null && cached.source() == hint && cached.font() == font && cached.guiWidth() == guiWidth
				&& cached.width() == width) {
			return cached;
		}
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
		int clueHeight = Draw.linesHeight(font, clueLines, HINT_LINES);
		int simpleHeight = Draw.linesHeight(font, simpleLines, HINT_LINES);
		int descriptionHeight = descriptionLines == null ? 0 : Draw.linesHeight(font, descriptionLines, HINT_LINES);
		int sideHeight = side == null ? 0 : font.lineHeight + 2;
		int height = 18 + clueHeight + 4 + simpleHeight + 4 + descriptionHeight + 2 + sideHeight + 12;
		hintLayout = new HintLayout(hint, font, guiWidth, width, clueLines, simpleLines, descriptionLines, side,
				clueHeight, simpleHeight, descriptionHeight, height);
		return hintLayout;
	}

	/** Draws the window on top of the queue. */
	private void drawWindow(GuiGraphicsExtractor graphics, HudWindows.Window window, long now) {
		WindowChrome.Info chrome = new WindowChrome.Info(keyLabel(hq.hintKey()), windows.waiting());
		Object content = window.content();
		switch (window.kind()) {
			case HINT -> drawHint(graphics, (QuestEngine.HintView) content, chrome);
			case RULE_CARD -> drawRuleCard(graphics, (RuleCard) content, chrome);
			case CULTURE -> CultureHook.draw(graphics, mc, (CultureCards.Card) content, chrome, logArea());
			case NOTICE -> drawNotice(graphics, (Notice) content, chrome);
			case REVIEW_PROMPT -> drawQuizPrompt(graphics, now, chrome);
			case NEXT_QUEST -> drawNextChoices(graphics, chrome);
			case CELEBRATION -> drawCelebration(graphics, (Celebration) content, now, chrome);
			case QUIZ_RESULT -> drawQuizResult(graphics, (QuizResult) content, chrome);
		}
	}

	/** The width a window gets: what it wants, or the free space beside the quest log when that is less. */
	private int widthFor(int wanted) {
		return WindowChrome.widthFor(mc.getWindow().getGuiScaledWidth(), wanted, logArea());
	}

	private List<WindowPlacement.Rect> logArea() {
		return questLogDrawn ? questLog.area() : List.of();
	}

	/** Where a window that would like to sit at ({@code x}, {@code y}) goes: off the crosshair and the hotbar. */
	private WindowPlacement.Point place(int x, int y, int width, int height) {
		return WindowChrome.place(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
				x, y, width, height, logArea());
	}

	private void drawHint(GuiGraphicsExtractor graphics, QuestEngine.HintView hint, WindowChrome.Info chrome) {
		var font = mc.font;
		HintLayout layout = hintLayout(font, hint);
		int width = layout.width();
		int height = layout.height() + WindowChrome.footerHeight(font);
		int textWidth = width - 14;
		int guiHeight = mc.getWindow().getGuiScaledHeight();
		var at = place((mc.getWindow().getGuiScaledWidth() - width) / 2,
				Math.max(questLog.bottom() + 6, guiHeight / 4), width, height);
		int x = at.x();
		int y = at.y();
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, font, "힌트", x + 7, y + 5, Draw.GOLD);
		int cursor = y + 17;
		Draw.drawLines(graphics, font, layout.clue(), x + 7, cursor, Draw.DIM, HINT_LINES);
		cursor += layout.clueHeight() + 4;
		Draw.drawLines(graphics, font, layout.simplified(), x + 7, cursor, Draw.WHITE, HINT_LINES);
		cursor += layout.simpleHeight() + 4;
		if (layout.description() != null) {
			Draw.drawLines(graphics, font, layout.description(), x + 7, cursor, Draw.BLUE, HINT_LINES);
			cursor += layout.descriptionHeight() + 2;
		}
		if (layout.side() != null) {
			Draw.truncated(graphics, font, layout.side(), x + 7, cursor, textWidth, Draw.GOLD);
		}
		WindowChrome.footer(graphics, font, x, y, width, height, chrome);
	}

	private void drawRuleCard(GuiGraphicsExtractor graphics, RuleCard card, WindowChrome.Info chrome) {
		var font = mc.font;
		int width = widthFor(Math.min(320, mc.getWindow().getGuiScaledWidth() - 16));
		int textWidth = width - 14;
		int examplesHeight = Draw.wrappedHeight(font, card.examples(), textWidth, 3);
		int height = 17 + examplesHeight + 4;
		for (Component line : card.lines()) {
			height += Draw.wrappedHeight(font, line, textWidth, 4) + 2;
		}
		height += 4 + WindowChrome.footerHeight(font);
		var at = place((mc.getWindow().getGuiScaledWidth() - width) / 2,
				Math.max(questLog.bottom() + 6, mc.getWindow().getGuiScaledHeight() / 4), width, height);
		int x = at.x();
		int y = at.y();
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, font, "문법 · " + card.view().form(), x + 7, y + 5, Draw.GOLD);
		int cursor = y + 17;
		Draw.wrapped(graphics, font, card.examples(), x + 7, cursor, textWidth, Draw.WHITE, 3);
		cursor += examplesHeight + 4;
		for (Component line : card.lines()) {
			Draw.wrapped(graphics, font, line, x + 7, cursor, textWidth, Draw.GRAY, 4);
			cursor += Draw.wrappedHeight(font, line, textWidth, 4) + 2;
		}
		WindowChrome.footer(graphics, font, x, y, width, height, chrome);
	}

	private void drawSideQuest(GuiGraphicsExtractor graphics) {
		SideQuest side = hq.quests().sideQuest();
		if (side == null) {
			return;
		}
		var font = mc.font;
		int width = widthFor(320);
		int height = 128;
		var at = place((mc.getWindow().getGuiScaledWidth() - width) / 2,
				(mc.getWindow().getGuiScaledHeight() - height) / 2, width, height);
		int x = at.x();
		int y = at.y();
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

	private void drawNextChoices(GuiGraphicsExtractor graphics, WindowChrome.Info chrome) {
		if (nextChoices.isEmpty()) {
			return;
		}
		var font = mc.font;
		int width = widthFor(300);
		int rows = nextChoices.size();
		int footer = WindowChrome.footerHeight(font);
		int height = 24 + rows * 14 + 14 + footer;
		var at = place((mc.getWindow().getGuiScaledWidth() - width) / 2,
				mc.getWindow().getGuiScaledHeight() / 2 - height / 2 - 20, width, height);
		int x = at.x();
		int y = at.y();
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
		drawAnswerHint(graphics, x + 8, y + height - footer - 12);
		WindowChrome.footer(graphics, font, x, y, width, height, chrome);
	}

	private void drawCelebration(GuiGraphicsExtractor graphics, Celebration card, long now, WindowChrome.Info chrome) {
		var font = mc.font;
		int guiWidth = mc.getWindow().getGuiScaledWidth();
		Celebration.Box box = card.measure(font, widthFor(card.wantedWidth(guiWidth)), WindowChrome.footerHeight(font));
		var at = place((guiWidth - box.width()) / 2,
				(mc.getWindow().getGuiScaledHeight() - box.height()) / 2 - (card.kind() == Celebration.Kind.TRAIL ? 0 : 12),
				box.width(), box.height());
		card.draw(graphics, font, at.x(), at.y(), box, now, hq.config().autoCloseWindows);
		WindowChrome.footer(graphics, font, at.x(), at.y(), box.width(), box.height(), chrome);
	}

	private void drawQuizResult(GuiGraphicsExtractor graphics, QuizResult result, WindowChrome.Info chrome) {
		var font = mc.font;
		Word word = result.word();
		int width = widthFor(300);
		int footer = WindowChrome.footerHeight(font);
		int height = 74 + footer;
		var at = place((mc.getWindow().getGuiScaledWidth() - width) / 2,
				(mc.getWindow().getGuiScaledHeight() - height) / 2, width, height);
		int x = at.x();
		int y = at.y();
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, font, result.correct() ? "정답!" : "다시!", x + 8, y + 6,
				result.correct() ? Draw.GREEN : Draw.RED);
		Draw.itemIcon(graphics, word.iconItem, x + 8, y + 24);
		Draw.korean(graphics, font, word.ko, x + 30, y + 24, Draw.WHITE);
		Draw.truncated(graphics, font, Component.literal(glossOf(word)), x + 30, y + 39,
				width - 40, Draw.GRAY);
		Draw.label(graphics, font, result.correct() ? I18n.t("hangulquest.ui.hud.correct")
				: I18n.t("hangulquest.ui.hud.right_answer"),
				x + 8, y + height - footer - 12, Draw.DIM);
		WindowChrome.footer(graphics, font, x, y, width, height, chrome);
	}

	/** "Quiz ready: press [key]". Stays until the player opens it or closes it with the hint key. */
	private void drawQuizPrompt(GuiGraphicsExtractor graphics, long now, WindowChrome.Info chrome) {
		if (session.state() != QuizSession.State.PROMPT) {
			return;
		}
		var font = mc.font;
		Component title = KoText.ko("복습");
		Component text = Component.literal(I18n.t("hangulquest.ui.hud.quiz_ready", keyLabel(hq.answerKey())));
		int width = widthFor(Math.max(WindowChrome.MIN_WIDTH, 10 + font.width(title) + 8 + font.width(text) + 10));
		int height = 5 + font.lineHeight + 6 + WindowChrome.footerHeight(font);
		var at = place((mc.getWindow().getGuiScaledWidth() - width) / 2, 10, width, height);
		int x = at.x();
		int y = at.y();
		Draw.panel(graphics, x, y, width, height, 0);
		graphics.text(font, title, x + 10, y + 5, Draw.GOLD, false);
		graphics.text(font, text, x + 10 + font.width(title) + 8, y + 5, Draw.WHITE, false);
		long remaining = session.promptRemainingMs(now);
		if (remaining != QuizSession.NO_TIMEOUT) { // a countdown only when windows close by themselves
			int left = (int) ((width - 2) * Math.min(remaining, QuizSession.PROMPT_MS) / QuizSession.PROMPT_MS);
			graphics.fill(x + 1, y + 5 + font.lineHeight + 2, x + 1 + left, y + 5 + font.lineHeight + 4, Draw.GOLD);
		}
		WindowChrome.footer(graphics, font, x, y, width, height, chrome);
	}

	/** The footer of the sidequest panel: it is answered in the quiz screen. */
	private void drawAnswerHint(GuiGraphicsExtractor graphics, int x, int y) {
		var font = mc.font;
		Component ko = KoText.ko("답을 골라요");
		graphics.text(font, ko, x, y, Draw.DIM, false);
		Draw.label(graphics, font, " · " + I18n.t("hangulquest.ui.hud.open_with", keyLabel(hq.answerKey())),
				x + font.width(ko), y, Draw.DIM);
	}

	/** A key as the player sees it, such as "[R]"; empty for a key that is not there. */
	private static String keyLabel(net.minecraft.client.KeyMapping key) {
		return key == null ? "" : "[" + key.getTranslatedKeyMessage().getString() + "]";
	}

	private void drawWordCard(GuiGraphicsExtractor graphics, WordCard card) {
		var font = mc.font;
		Word word = card.word();
		Component korean = card.ko();
		Component german = card.gloss();
		boolean pron = card.pron() != null;
		int textWidth = Math.max(font.width(korean), font.width(german));
		if (pron) {
			textWidth = Math.max(textWidth, font.width(card.pron()));
		}
		textWidth = Math.min(textWidth, 220);
		int width = Math.max(WindowChrome.MIN_WIDTH, 28 + textWidth + 7);
		int body = 6 + font.lineHeight * (pron ? 3 : 2) + 5;
		int height = body;
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int screenHeight = mc.getWindow().getGuiScaledHeight();
		width = widthFor(Math.min(width, screenWidth - 12));
		var at = place(screenWidth - width - 6, screenHeight - height - 6, width, height);
		int x = at.x();
		int y = at.y();
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.itemIcon(graphics, word.iconItem, x + 6, y + (body - 16) / 2);
		Draw.truncated(graphics, font, korean, x + 27, y + 5, width - 33, Draw.WHITE);
		int line = y + 6 + font.lineHeight;
		if (pron) {
			Draw.truncated(graphics, font, card.pron(), x + 27, line, width - 33, Draw.DIM);
			line += font.lineHeight + 1;
		}
		Draw.truncated(graphics, font, german, x + 27, line, width - 33, Draw.GRAY);
	}

	/** A banner, achievement or streak toast, or a quest announcement: a Korean title with optional lines. */
	private void drawNotice(GuiGraphicsExtractor graphics, Notice notice, WindowChrome.Info chrome) {
		var font = mc.font;
		int guiWidth = mc.getWindow().getGuiScaledWidth();
		int width = widthFor(Math.min(300, guiWidth - 12));
		int textWidth = width - 16;
		int titleHeight = Draw.wrappedHeight(font, notice.title(), textWidth, 3);
		int titleTextHeight = notice.titleText() == null ? 0 : Draw.wrappedHeight(font, notice.titleText(), textWidth, 3);
		int bodyHeight = notice.body() == null ? 0 : Draw.wrappedHeight(font, notice.body(), textWidth, 4);
		int bodyTextHeight = notice.bodyText() == null ? 0 : Draw.wrappedHeight(font, notice.bodyText(), textWidth, 4);
		int height = 6 + titleHeight + (titleTextHeight == 0 ? 0 : 2 + titleTextHeight)
				+ (bodyHeight == 0 ? 0 : 5 + bodyHeight) + (bodyTextHeight == 0 ? 0 : 2 + bodyTextHeight)
				+ 5 + WindowChrome.footerHeight(font);
		var at = place((guiWidth - width) / 2, mc.getWindow().getGuiScaledHeight() - height - 70, width, height);
		int x = at.x();
		int y = at.y();
		Draw.panel(graphics, x, y, width, height, 0);
		int cursor = y + 5;
		Draw.wrapped(graphics, font, notice.title(), x + 8, cursor, textWidth, Draw.GOLD, 3);
		cursor += titleHeight + 2;
		if (notice.titleText() != null) {
			Draw.wrapped(graphics, font, notice.titleText(), x + 8, cursor, textWidth, Draw.GRAY, 3);
			cursor += titleTextHeight + 2;
		}
		if (notice.body() != null) {
			Draw.wrapped(graphics, font, notice.body(), x + 8, cursor + 2, textWidth, Draw.WHITE, 4);
			cursor += bodyHeight + 5;
		}
		if (notice.bodyText() != null) {
			Draw.wrapped(graphics, font, notice.bodyText(), x + 8, cursor, textWidth, Draw.GRAY, 4);
		}
		WindowChrome.footer(graphics, font, x, y, width, height, chrome);
	}
}
