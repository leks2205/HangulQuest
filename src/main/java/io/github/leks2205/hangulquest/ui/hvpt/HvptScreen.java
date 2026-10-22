package io.github.leks2205.hangulquest.ui.hvpt;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.audio.AudioService;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.hvpt.HvptPhase;
import io.github.leks2205.hangulquest.learn.hvpt.HvptProgress;
import io.github.leks2205.hangulquest.learn.hvpt.HvptRecorder;
import io.github.leks2205.hangulquest.learn.hvpt.HvptResult;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSession;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSet;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSink;
import io.github.leks2205.hangulquest.learn.hvpt.HvptTrial;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 소리 대장간, the sound forge: listening practice for the Korean sounds an English or German ear tends to merge.
 * A voice says a syllable or a word, the player picks which one it was, and the answer is told at once with both
 * sounds played again. Keys 1, 2, 3 answer, R hears it again, Enter goes on, Esc leaves. Clicking works too.
 * Everything that is decided lives in {@link HvptModel}; this class paints it.
 */
public final class HvptScreen extends Screen {
	private static final String TITLE_KO = "소리 대장간";
	private static final int PANEL_W = 330;
	private static final int BUTTON_H = 20;
	private static final int OPTION_H = 28;

	/** A clickable area of the current frame. */
	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private final Screen parent;
	private final HvptModel model;
	private final List<Hit> hits = new ArrayList<>();

	/** Opens the forge for the running game. */
	public HvptScreen(Screen parent) {
		this(parent, liveModel());
	}

	/** For tests and previews: any model will do. */
	public HvptScreen(Screen parent, HvptModel model) {
		super(Component.literal(TITLE_KO));
		this.parent = parent;
		this.model = model;
	}

	private static HvptModel liveModel() {
		HangulQuestClient hq = HangulQuestClient.get();
		LearningService learn = hq == null ? null : hq.learn();
		AudioService audio = hq == null ? null : hq.audio();
		ProgressData.HvptData spare = new ProgressData.HvptData();
		HvptSink sink = learn == null ? new HvptSink() {
			@Override
			public void answered(HvptPhase phase, HvptTrial trial, String picked, boolean correct, long latencyMs, long now) {
			}

			@Override
			public void finished(HvptResult result, long now) {
			}
		} : new HvptRecorder(learn.data(), learn.reviewLog(), learn::markDirty);
		return new HvptModel(HvptSet.shared(), audioOf(audio), sink, () -> learn == null ? spare : learn.data().hvpt,
				System::currentTimeMillis, new Random(), ZoneId.systemDefault());
	}

	/** The audio service as the model sees it; a missing service behaves like a missing pack. */
	public static HvptModel.Audio audioOf(AudioService audio) {
		return new HvptModel.Audio() {
			@Override
			public boolean settingOn() {
				return audio == null || audio.settingOn();
			}

			@Override
			public boolean packLoaded() {
				return audio != null && audio.packLoaded();
			}

			@Override
			public boolean forgeLoaded() {
				return audio != null && audio.hvptLoaded();
			}

			@Override
			public boolean has(String event) {
				return audio != null && audio.hasSound(event);
			}

			@Override
			public void play(String event) {
				if (audio != null) {
					audio.playSound(event);
				}
			}
		};
	}

	public HvptModel model() {
		return model;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		model.tick();
	}

	@Override
	public void onClose() {
		model.abandon();
		this.minecraft.setScreenAndShow(parent);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (model.stage() == HvptModel.Stage.RUN) {
			int option = optionOf(key);
			if (option >= 0) {
				model.choose(option);
				return true;
			}
			if (key == InputConstants.KEY_R) {
				model.replay();
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
				model.advance();
				return true;
			}
		} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
			if (model.stage() == HvptModel.Stage.RESULT) {
				model.refresh();
			} else if (model.stage() == HvptModel.Stage.INTRO) {
				model.start(model.progress().next());
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private static int optionOf(int key) {
		return switch (key) {
			case InputConstants.KEY_1, InputConstants.KEY_NUMPAD1 -> 0;
			case InputConstants.KEY_2, InputConstants.KEY_NUMPAD2 -> 1;
			case InputConstants.KEY_3, InputConstants.KEY_NUMPAD3 -> 2;
			default -> -1;
		};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (Hit hit : hits) {
				if (hit.contains(event.x(), event.y())) {
					hit.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		hits.clear();
		int width = Math.min(PANEL_W, this.width - 12);
		int x = (this.width - width) / 2;
		switch (model.stage()) {
			case BLOCKED -> blocked(g, x, width, mouseX, mouseY);
			case INTRO -> intro(g, x, width, mouseX, mouseY);
			case RUN -> run(g, x, width, mouseX, mouseY);
			case RESULT -> result(g, x, width, mouseX, mouseY);
		}
	}

	private int top(int height) {
		return Math.max(2, (this.height - height) / 2);
	}

	private void title(GuiGraphicsExtractor g, int x, int y, int width) {
		Draw.korean(g, this.font, TITLE_KO, x + 8, y + 6, Draw.GOLD);
		String name = I18n.t("hangulquest.ui.hvpt.title");
		Draw.label(g, this.font, name, x + 8 + this.font.width(KoText.ko(TITLE_KO)) + 6, y + 6, Draw.DIM);
	}

	private void blocked(GuiGraphicsExtractor g, int x, int width, int mx, int my) {
		String text = switch (model.block()) {
			case SOUND_OFF -> I18n.t("hangulquest.ui.hvpt.block.sound_off");
			case NO_PACK -> I18n.t("hangulquest.ui.hvpt.block.no_pack");
			default -> I18n.t("hangulquest.ui.hvpt.block.no_forge");
		};
		Component body = KoText.mixed(text);
		int textHeight = Draw.wrappedHeight(this.font, body, width - 16, 8);
		int height = 22 + textHeight + 12 + BUTTON_H + 10;
		int y = top(height);
		Draw.panel(g, x, y, width, height, 0);
		title(g, x, y, width);
		Draw.wrapped(g, this.font, body, x + 8, y + 22, width - 16, Draw.WHITE, 8);
		button(g, I18n.t("hangulquest.ui.hvpt.back"), x + width / 2 - 50, y + 22 + textHeight + 12, 100, mx, my, true,
				this::onClose);
	}

	private void intro(GuiGraphicsExtractor g, int x, int width, int mx, int my) {
		HvptProgress progress = model.progress();
		HvptSet set = model.set();
		Component intro = KoText.mixed(I18n.t("hangulquest.ui.hvpt.intro"));
		int introHeight = Draw.wrappedHeight(this.font, intro, width - 16, 4);
		int rows = progress.lines().size();
		int height = 20 + introHeight + 6 + 11 + 22 + 3 + 11 + rows * 10 + 8 + BUTTON_H + 4 + 10 + 8;
		int y = top(height);
		Draw.panel(g, x, y, width, height, 0);
		title(g, x, y, width);
		int cursor = y + 20;
		Draw.wrapped(g, this.font, intro, x + 8, cursor, width - 16, Draw.GRAY, 4);
		cursor += introHeight + 6;

		HvptSet.Params params = progress.params();
		Draw.label(g, this.font, I18n.t("hangulquest.ui.hvpt.progress", progress.trainingRuns(), params.recommendedSessions(),
				progress.preDone() ? progress.daysSinceStart() + 1 : 0, params.weeks() * 7), x + 8, cursor, Draw.WHITE);
		cursor += 11;
		String schedule = !progress.preDone() ? I18n.t("hangulquest.ui.hvpt.next_pre", set.params().testTrials())
				: progress.postReady() ? I18n.t("hangulquest.ui.hvpt.next_post")
				: progress.trainedToday() ? I18n.t("hangulquest.ui.hvpt.next_rest")
				: I18n.t("hangulquest.ui.hvpt.next_train", set.params().trials());
		Draw.wrapped(g, this.font, Component.literal(schedule), x + 8, cursor, width - 16,
				progress.trainedToday() ? Draw.DIM : Draw.GREEN, 2);
		cursor += 25;

		int cLabel = x + 8;
		int cPre = x + width - 8 - 3 * 52;
		Draw.label(g, this.font, I18n.t("hangulquest.ui.hvpt.col.pre"), cPre, cursor, Draw.DIM);
		Draw.label(g, this.font, I18n.t("hangulquest.ui.hvpt.col.recent"), cPre + 52, cursor, Draw.DIM);
		Draw.label(g, this.font, I18n.t("hangulquest.ui.hvpt.col.post"), cPre + 104, cursor, Draw.DIM);
		cursor += 11;
		for (HvptProgress.Line line : progress.lines()) {
			HvptSet.Contrast contrast = set.contrast(line.contrast());
			Draw.korean(g, this.font, contrast == null ? line.contrast() : contrast.label(), cLabel, cursor, Draw.WHITE);
			percent(g, line.pre(), cPre, cursor, Draw.GRAY);
			percent(g, line.recent(), cPre + 52, cursor, Draw.WHITE);
			percent(g, line.post(), cPre + 104, cursor, Draw.GREEN);
			cursor += 10;
		}
		cursor += 8;

		HvptPhase phase = progress.next();
		int half = (width - 24) / 2;
		boolean trainReady = progress.preDone();
		String main = phase == HvptPhase.PRE ? I18n.t("hangulquest.ui.hvpt.start_pre", set.params().testTrials())
				: I18n.t("hangulquest.ui.hvpt.start_train", set.params().trials());
		button(g, main, x + 8, cursor, half, mx, my, true, () -> model.start(phase));
		String post = I18n.t("hangulquest.ui.hvpt.start_post", set.params().testTrials());
		button(g, post, x + 16 + half, cursor, half, mx, my, progress.postReady(), () -> model.start(HvptPhase.POST));
		cursor += BUTTON_H + 4;
		String note = model.tooFewSounds() ? I18n.t("hangulquest.ui.hvpt.too_few")
				: trainReady && !progress.postReady() && !progress.postDone()
				? I18n.t("hangulquest.ui.hvpt.post_wait", progress.runsUntilPost(), progress.daysUntilPost())
				: I18n.t("hangulquest.ui.hvpt.leave");
		Draw.label(g, this.font, note, x + 8, cursor, model.tooFewSounds() ? Draw.RED : Draw.DIM);
	}

	private static String percent(double share) {
		return Math.round(share * 100) + "%";
	}

	private void percent(GuiGraphicsExtractor g, Double value, int x, int y, int color) {
		String text = value == null ? "-" : percent(value.doubleValue());
		Draw.label(g, this.font, text, x, y, value == null ? Draw.DIM : color);
	}

	private void run(GuiGraphicsExtractor g, int x, int width, int mx, int my) {
		HvptSession session = model.session();
		HvptTrial trial = session.current();
		if (trial == null) {
			return;
		}
		HvptSet set = model.set();
		HvptSet.Item item = set.item(trial.itemId());
		boolean feedback = session.state() == HvptSession.State.FEEDBACK;
		HvptSession.Feedback answer = session.feedback();
		int height = 150;
		int y = top(height);
		Draw.panel(g, x, y, width, height, 0);
		title(g, x, y, width);
		String count = I18n.t("hangulquest.ui.hvpt.trial", session.index() + 1, session.size());
		Draw.label(g, this.font, count, x + width - 8 - this.font.width(count), y + 6, Draw.GRAY);
		int barY = y + 17;
		g.fill(x + 8, barY, x + width - 8, barY + 2, 0x44FFFFFF);
		g.fill(x + 8, barY, x + 8 + (width - 16) * session.answered() / Math.max(1, session.size()), barY + 2, Draw.GOLD);

		HvptSet.Contrast contrast = set.contrast(trial.contrast());
		Draw.korean(g, this.font, contrast == null ? "" : contrast.label(), x + 8, y + 24, Draw.DIM);
		String phaseName = switch (session.phase()) {
			case PRE -> I18n.t("hangulquest.ui.hvpt.phase.pre");
			case POST -> I18n.t("hangulquest.ui.hvpt.phase.post");
			case TRAIN -> I18n.t("hangulquest.ui.hvpt.phase.train");
		};
		Draw.label(g, this.font, phaseName, x + width - 8 - this.font.width(phaseName), y + 24, Draw.DIM);

		String prompt = I18n.t("hangulquest.ui.hvpt.prompt");
		Draw.label(g, this.font, prompt, x + (width - this.font.width(prompt)) / 2, y + 40, Draw.WHITE);

		int count3 = trial.options().size();
		int gap = 6;
		int bw = (width - 16 - gap * (count3 - 1)) / count3;
		int by = y + 56;
		for (int i = 0; i < count3; i++) {
			String key = trial.options().get(i);
			HvptSet.Form form = item == null ? null : item.form(key);
			int bx = x + 8 + i * (bw + gap);
			int index = i;
			int border = Draw.BORDER;
			int fill = 0x33FFFFFF;
			if (feedback) {
				if (key.equals(answer.heard())) {
					border = Draw.GREEN;
					fill = 0x338CE99A;
				} else if (key.equals(answer.picked())) {
					border = Draw.RED;
					fill = 0x33FF6B6B;
				}
			} else if (inside(mx, my, bx, by, bw, OPTION_H)) {
				fill = 0x55FFFFFF;
			}
			box(g, bx, by, bw, OPTION_H, fill, border);
			Draw.label(g, this.font, Integer.toString(i + 1), bx + 4, by + 3, Draw.DIM);
			String ko = form == null ? key : form.ko();
			Draw.korean(g, this.font, ko, bx + (bw - this.font.width(KoText.ko(ko))) / 2, by + (OPTION_H - 8) / 2, Draw.WHITE);
			hits.add(new Hit(bx, by, bw, OPTION_H, () -> model.choose(index)));
		}

		int fy = by + OPTION_H + 8;
		if (feedback) {
			String verdict = answer.correct() ? I18n.t("hangulquest.ui.hvpt.right") : I18n.t("hangulquest.ui.hvpt.wrong");
			Draw.label(g, this.font, verdict, x + 8, fy, answer.correct() ? Draw.GREEN : Draw.RED);
			fy += 11;
			String gloss = glossLine(item, answer);
			if (!gloss.isEmpty()) {
				Draw.wrapped(g, this.font, KoText.mixed(gloss), x + 8, fy, width - 16, Draw.GRAY, 2);
			}
			String keys = I18n.t("hangulquest.ui.hvpt.keys_feedback");
			Draw.label(g, this.font, keys, x + 8, y + height - 12, Draw.DIM);
			button(g, I18n.t("hangulquest.ui.hvpt.next"), x + width - 8 - 70, y + height - 12 - 22, 70, mx, my, true,
					model::advance);
		} else {
			button(g, I18n.t("hangulquest.ui.hvpt.replay"), x + 8, fy, 90, mx, my, true, model::replay);
			String keys = I18n.t("hangulquest.ui.hvpt.keys_listen", count3);
			Draw.label(g, this.font, keys, x + 8, y + height - 12, Draw.DIM);
		}
	}

	private String glossLine(HvptSet.Item item, HvptSession.Feedback answer) {
		if (item == null || !item.word()) {
			return "";
		}
		HvptSet.Form heard = item.form(answer.heard());
		HvptSet.Form picked = item.form(answer.picked());
		Lang lang = Lang.current();
		String text = heard.ko() + " = " + heard.gloss(lang);
		if (!answer.correct() && picked != null) {
			text += "     " + picked.ko() + " = " + picked.gloss(lang);
		}
		return text;
	}

	private void result(GuiGraphicsExtractor g, int x, int width, int mx, int my) {
		HvptResult result = model.session().result();
		HvptProgress progress = model.progress();
		HvptSet set = model.set();
		int rows = result.byContrast().size();
		int height = 20 + 14 + 11 + rows * 10 + 8 + BUTTON_H + 12;
		int y = top(height);
		Draw.panel(g, x, y, width, height, 0);
		title(g, x, y, width);
		int cursor = y + 20;
		Draw.label(g, this.font, I18n.t("hangulquest.ui.hvpt.result", result.correct(), result.trials(),
				percent(result.accuracy())), x + 8, cursor, Draw.WHITE);
		cursor += 14;
		String compare = null;
		if (result.phase() == HvptPhase.POST && progress.gain() != null) {
			compare = I18n.t("hangulquest.ui.hvpt.gain", percent(progress.preAccuracy()),
					percent(progress.postAccuracy()));
		} else if (result.phase() == HvptPhase.TRAIN && progress.preAccuracy() != null) {
			compare = I18n.t("hangulquest.ui.hvpt.vs_pre", percent(progress.preAccuracy()));
		} else if (result.phase() == HvptPhase.PRE) {
			compare = I18n.t("hangulquest.ui.hvpt.pre_saved");
		}
		Draw.label(g, this.font, compare == null ? "" : compare, x + 8, cursor, Draw.GRAY);
		cursor += 11;
		for (var entry : result.byContrast().entrySet()) {
			HvptSet.Contrast contrast = set.contrast(entry.getKey());
			Draw.korean(g, this.font, contrast == null ? entry.getKey() : contrast.label(), x + 8, cursor, Draw.WHITE);
			HvptResult.Tally tally = entry.getValue();
			String text = tally.correct() + "/" + tally.trials() + "  " + percent(tally.accuracy());
			Draw.label(g, this.font, text, x + width - 8 - this.font.width(text), cursor,
					tally.accuracy() >= 0.8 ? Draw.GREEN : Draw.WHITE);
			cursor += 10;
		}
		cursor += 8;
		button(g, I18n.t("hangulquest.ui.hvpt.done"), x + width / 2 - 50, cursor, 100, mx, my, true, model::refresh);
	}

	// ------------------------------------------------------------------ widgets

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w, y + 1, border);
		g.fill(x, y + h - 1, x + w, y + h, border);
		g.fill(x, y, x + 1, y + h, border);
		g.fill(x + w - 1, y, x + w, y + h, border);
	}

	private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int mx, int my, boolean enabled,
						Runnable action) {
		boolean hover = enabled && inside(mx, my, x, y, w, BUTTON_H);
		box(g, x, y, w, BUTTON_H, hover ? 0x55FFFFFF : enabled ? 0x33FFFFFF : 0x15FFFFFF, enabled ? Draw.BORDER : 0xFF2A2A31);
		String shown = this.font.width(label) > w - 6 ? this.font.plainSubstrByWidth(label, w - 12) + "…" : label;
		Draw.label(g, this.font, shown, x + (w - this.font.width(shown)) / 2, y + (BUTTON_H - 8) / 2,
				enabled ? Draw.WHITE : Draw.DIM);
		if (enabled) {
			hits.add(new Hit(x, y, w, BUTTON_H, action));
		}
	}
}
