package io.github.leks2205.hangulquest.ui.dialogue;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.audio.AudioService;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueSession;
import io.github.leks2205.hangulquest.dialogue.SpeechLevel;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.ui.widget.HangulTextField;
import io.github.leks2205.hangulquest.ui.widget.TypedAnswerPanel;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.villager.VillagerNames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * A conversation with an NPC, shown as a panel at the bottom of the screen. The line is set word by
 * word: a word the player does not know yet is underlined and explained when the mouse rests on it.
 * Space or a click continues, the number keys pick an answer, a typed answer has its own field.
 * Esc leaves the conversation; whatever it already did to the learning data stays done. The game
 * does not pause.
 */
public final class DialogueScreen extends Screen {
	private static final int MAX_WIDTH = 340;
	private static final int PAD = 8;
	private static final int LINE = 12;
	private static final int ROW = 26;
	private static final int[] NUMBER_KEYS = {
			InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4};

	/** The words of one line, analysed once. */
	private record Analysed(List<DialogueCoverage.Token> tokens, List<String> texts) {
	}

	/**
	 * Who speaks and how it sounds: the voice id of the recorded lines, the villager's name (for the self-introduction),
	 * whether it talks 반말 with the player now, and the speech level of its self-introduction. {@link #NONE} for an NPC
	 * without a voice.
	 */
	public record Voice(String voiceId, String name, BooleanSupplier banmal, SpeechLevel introLevel) {
		public static final Voice NONE = new Voice(null, null, () -> false, SpeechLevel.HAEYO);

		boolean talksBanmal() {
			return banmal != null && banmal.getAsBoolean();
		}
	}

	/**
	 * Whether the English/German gloss under a line is drawn: the same setting as the quest texts, off by default.
	 * Without it the conversation shows Korean only; the hover gloss of a single word stays.
	 */
	private boolean showGloss() {
		HangulQuestClient client = HangulQuestClient.get();
		return client != null && client.config().questTranslations;
	}

	private final DialogueSession session;
	private final String npcName;
	private final Voice voice;
	private final AudioService audio;
	/** Whether the line on screen has a recording; then R and the play mark speak it again. */
	private boolean spoken;
	private int[] replayArea;
	private final DialogueCoverage.Vocabulary vocabulary;
	private final Dialogue.Node first;
	private final Map<String, Analysed> analysed = new HashMap<>();
	private final HangulTextField freeField = new HangulTextField();
	private final List<int[]> rows = new ArrayList<>();
	private final Set<Integer> tried = new HashSet<>();
	private Dialogue.Node shown;
	private TypedAnswerPanel typedPanel;
	private String note = "";
	private boolean noteRight;

	/**
	 * @param npcName    the villager's given name, shown for the speaker the conversation starts with
	 * @param vocabulary what the player knows, for the underlined words
	 */
	public DialogueScreen(DialogueSession session, String npcName, DialogueCoverage.Vocabulary vocabulary) {
		this(session, npcName, vocabulary, Voice.NONE, null);
	}

	/**
	 * @param voice how the NPC sounds; each line is spoken when it is shown, if the voice pack has it
	 * @param audio the sound service; null for a silent conversation
	 */
	public DialogueScreen(DialogueSession session, String npcName, DialogueCoverage.Vocabulary vocabulary, Voice voice,
						  AudioService audio) {
		super(Component.literal("한글 탐험"));
		this.session = session;
		this.npcName = npcName;
		this.voice = voice == null ? Voice.NONE : voice;
		this.audio = audio;
		this.vocabulary = vocabulary;
		this.first = session.node();
		freeField.setPlaceholder(I18n.t("hangulquest.ui.dialogue.placeholder"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------- input

	@Override
	public boolean keyPressed(KeyEvent event) {
		Dialogue.Node node = session.node();
		if (event.key() == InputConstants.KEY_ESCAPE || node == null) {
			onClose();
			return true;
		}
		boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
		if (event.key() == InputConstants.KEY_R && node.kind() != Dialogue.Kind.TYPED) {
			speak(node);
			return true;
		}
		switch (node.kind()) {
			case LINE, END -> {
				if (enter || event.key() == InputConstants.KEY_SPACE) {
					advance();
					return true;
				}
			}
			case CHOICE -> {
				for (int i = 0; i < Math.min(NUMBER_KEYS.length, node.choices().size()); i++) {
					if (event.key() == NUMBER_KEYS[i]) {
						choose(i);
						return true;
					}
				}
			}
			case TYPED -> {
				if (typedPanel != null) {
					return typedPanel.keyPressed(event) || super.keyPressed(event);
				}
				if (enter) {
					// Nothing is graded and nothing is kept: an empty answer just skips the question.
					freeField.clear();
					session.typed(true);
					return true;
				}
				return freeField.keyPressed(event) || super.keyPressed(event);
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (typedPanel != null) {
			return typedPanel.keyReleased(event) || super.keyReleased(event);
		}
		return freeField.keyReleased(event) || super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		Dialogue.Node node = session.node();
		if (node == null || node.kind() != Dialogue.Kind.TYPED) {
			return super.charTyped(event);
		}
		return (typedPanel != null ? typedPanel.charTyped(event) : freeField.charTyped(event))
				|| super.charTyped(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Dialogue.Node node = session.node();
		if (node != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int[] area = replayArea;
			if (spoken && area != null && event.x() >= area[0] && event.x() < area[0] + area[2]
					&& event.y() >= area[1] && event.y() < area[1] + area[3]) {
				speak(node);
				return true;
			}
			if (node.kind() == Dialogue.Kind.LINE || node.kind() == Dialogue.Kind.END) {
				advance();
				return true;
			}
			if (node.kind() == Dialogue.Kind.CHOICE) {
				for (int i = 0; i < rows.size(); i++) {
					int[] row = rows.get(i);
					if (event.x() >= row[0] && event.x() < row[0] + row[2]
							&& event.y() >= row[1] && event.y() < row[1] + ROW) {
						choose(i);
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void advance() {
		session.advance();
		if (session.finished()) {
			onClose();
		}
	}

	@Override
	public void removed() {
		if (audio != null) {
			audio.stopLine();
		}
		super.removed();
	}

	/** The text of a node as this NPC says it: its own name in the self-introduction, 반말 once it talks 반말. */
	private String textOf(Dialogue.Node node) {
		if (node.selfIntro()) {
			return voice.name() == null ? "" : VillagerNames.selfIntro(voice.name(), voice.introLevel());
		}
		return node.text(voice.talksBanmal());
	}

	/** Whether the line on screen is the 반말 version. */
	private boolean banmalShown(Dialogue.Node node) {
		return !node.selfIntro() && voice.talksBanmal() && node.koBanmal() != null && !node.koBanmal().isBlank();
	}

	/** The sound of a line in this NPC's voice, or null when it has none. */
	private String eventOf(Dialogue.Node node) {
		if (voice.voiceId() == null) {
			return null;
		}
		if (node.selfIntro()) {
			VillagerNames.Name name = VillagerNames.byKo(voice.name());
			return name == null ? null : AudioService.selfIntroEvent(name.id(), voice.voiceId());
		}
		return AudioService.lineEvent(session.dialogue().id(), node.id(), banmalShown(node), voice.voiceId());
	}

	/** Speaks the line (again); silent without the voice pack. */
	private void speak(Dialogue.Node node) {
		String event = eventOf(node);
		spoken = audio != null && event != null && audio.playLine(event);
	}

	private void choose(int index) {
		Dialogue.Node node = session.node();
		Dialogue dialogue = session.dialogue();
		DialogueSession.Pick pick = session.choose(index);
		String key = dialogue.choiceNoteKey(node.id(), index);
		if (I18n.has(key)) {
			note = I18n.t(key);
			noteRight = !Boolean.FALSE.equals(pick.correct());
		}
		if (!pick.moved()) {
			tried.add(index);
		}
	}

	// ----------------------------------------------------------------- drawing

	/** Resets what belongs to one node when the conversation moved on. */
	private void sync(Dialogue.Node node) {
		if (node == shown) {
			return;
		}
		shown = node;
		tried.clear();
		typedPanel = null;
		speak(node);
		if (node.kind() == Dialogue.Kind.TYPED && !node.typed().free()) {
			Dialogue.Typed typed = node.typed();
			String icon = null;
			if (typed.word() != null) {
				icon = HangulQuestClient.get().catalog().byId(typed.word()).map(Word::iconId).orElse(null);
			}
			typedPanel = new TypedAnswerPanel(I18n.t(session.dialogue().promptKey(node.id())), icon,
					typed.expected().get(0), result -> session.typed(result.exact()));
		}
	}

	private Analysed analyse(Dialogue.Node node) {
		String text = textOf(node);
		return analysed.computeIfAbsent(node.id() + (banmalShown(node) ? "/banmal" : ""), id -> {
			List<DialogueCoverage.Token> tokens = DialogueCoverage.analyze(text, vocabulary);
			if (node.selfIntro() && voice.name() != null) {
				// The villager's own name is no word to learn.
				tokens = tokens.stream().map(token -> token.text().contains(voice.name())
						? new DialogueCoverage.Token(token.text(), DialogueCoverage.State.KNOWN, null, null) : token).toList();
			}
			return new Analysed(tokens, tokens.stream().map(DialogueCoverage.Token::text).toList());
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Dialogue.Node node = session.node();
		if (node == null) {
			return;
		}
		sync(node);
		Dialogue dialogue = session.dialogue();
		int width = Math.min(MAX_WIDTH, this.width - 12);
		int inner = width - 2 * PAD;

		Analysed line = analyse(node);
		DialogueLayout.Flow flow = DialogueLayout.flow(line.texts(), text -> this.font.width(KoText.ko(text)), inner,
				this.font.width(" "), LINE);
		boolean glossShown = showGloss();
		Component gloss = glossShown ? HangulTextField.styled(node.selfIntro() && voice.name() != null
				? I18n.t(dialogue.lineKey(node.id()), voice.name()) : I18n.t(dialogue.lineKey(node.id()))) : null;
		int glossHeight = gloss == null ? 0 : Draw.wrappedHeight(this.font, gloss, inner, 3);
		Component noteText = note.isEmpty() ? null : HangulTextField.styled(note);
		int noteHeight = noteText == null ? 0 : Draw.wrappedHeight(this.font, noteText, inner, 3) + 4;
		int bodyHeight = switch (node.kind()) {
			case CHOICE -> node.choices().size() * ROW;
			case TYPED -> typedPanel != null ? typedPanel.height(this.font, width)
					: LINE * 2 + freeField.height();
			default -> 0;
		};
		// A typed question draws its own key hint inside its panel.
		int footerHeight = node.kind() == Dialogue.Kind.TYPED && typedPanel != null ? 0 : 6 + LINE;
		int height = PAD + LINE + 4 + flow.height() + 4 + glossHeight + noteHeight + (bodyHeight > 0 ? 6 + bodyHeight : 0)
				+ footerHeight + PAD / 2;
		int x = (this.width - width) / 2;
		int y = Math.max(4, this.height - height - 28);
		Draw.panel(graphics, x, y, width, height, 0);

		int cursor = y + PAD;
		drawHeader(graphics, node, x + PAD, cursor);
		cursor += LINE + 4;
		drawLine(graphics, line, flow, x + PAD, cursor, mouseX, mouseY);
		cursor += flow.height() + 4;
		if (gloss != null) {
			Draw.wrapped(graphics, this.font, gloss, x + PAD, cursor, inner, Draw.GRAY, 3);
			cursor += glossHeight;
		}
		if (noteText != null) {
			Draw.wrapped(graphics, this.font, noteText, x + PAD, cursor + 4, inner, noteRight ? Draw.GREEN : Draw.GOLD, 3);
			cursor += noteHeight;
		}
		if (bodyHeight > 0) {
			cursor += 6;
			switch (node.kind()) {
				case CHOICE -> drawChoices(graphics, dialogue, node, x, cursor, width, mouseX, mouseY);
				case TYPED -> drawTyped(graphics, dialogue, node, x, cursor, width);
				default -> {
				}
			}
		}
		if (footerHeight > 0) {
			Draw.label(graphics, this.font, footer(node), x + PAD, y + height - LINE - PAD / 2, Draw.DIM);
		}
		drawTooltip(graphics, line, flow, x + PAD, y + PAD + LINE + 4, mouseX, mouseY);
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Dialogue.Node node, int x, int y) {
		String who = node.speaker() == first.speaker() && npcName != null && !npcName.isBlank()
				? npcName + " · " + node.speaker().ko() : node.speaker().ko();
		Draw.korean(graphics, this.font, who, x, y, Draw.GOLD);
		int after = x + this.font.width(KoText.ko(who)) + 6;
		String role = I18n.t("hangulquest.ui.dialogue.speaker." + node.speaker().id());
		Draw.label(graphics, this.font, role, after, y, Draw.DIM);
		if (spoken) {
			// The play mark: a click (or R) speaks the line again.
			int markX = after + this.font.width(role) + 8;
			Draw.label(graphics, this.font, "▶", markX, y, Draw.GOLD);
			replayArea = new int[] {markX - 2, y - 2, this.font.width("▶") + 4, LINE};
		} else {
			replayArea = null;
		}
	}

	private void drawLine(GuiGraphicsExtractor graphics, Analysed line, DialogueLayout.Flow flow, int x, int y,
						  int mouseX, int mouseY) {
		int hovered = flow.hit(mouseX - x, mouseY - y, LINE);
		for (DialogueLayout.Placed placed : flow.words()) {
			DialogueCoverage.Token token = line.tokens().get(placed.index());
			boolean unknown = token.state() != DialogueCoverage.State.KNOWN;
			int color = unknown ? Draw.GOLD : Draw.WHITE;
			Draw.korean(graphics, this.font, token.text(), x + placed.x(), y + placed.y(), color);
			if (unknown) {
				int alpha = hovered == placed.index() ? 0xFF : 0x88;
				graphics.fill(x + placed.x(), y + placed.y() + 10, x + placed.x() + placed.width(), y + placed.y() + 11,
						(alpha << 24) | 0x00FFC857);
			}
		}
	}

	/** The gloss of the underlined word under the mouse. */
	private void drawTooltip(GuiGraphicsExtractor graphics, Analysed line, DialogueLayout.Flow flow, int x, int y,
							 int mouseX, int mouseY) {
		int hovered = flow.hit(mouseX - x, mouseY - y, LINE);
		if (hovered < 0) {
			return;
		}
		DialogueCoverage.Token token = line.tokens().get(hovered);
		if (token.state() == DialogueCoverage.State.KNOWN) {
			return;
		}
		String text = token.gloss() == null || token.gloss().isBlank() ? "?" : token.gloss();
		int tipWidth = this.font.width(text) + 8;
		int tx = Math.max(4, Math.min(mouseX + 6, this.width - tipWidth - 4));
		int ty = Math.max(4, mouseY - 18);
		graphics.fill(tx - 1, ty - 1, tx + tipWidth + 1, ty + 13, Draw.BORDER);
		graphics.fill(tx, ty, tx + tipWidth, ty + 12, 0xFF1F1F26);
		Draw.label(graphics, this.font, text, tx + 4, ty + 2, Draw.WHITE);
	}

	private void drawChoices(GuiGraphicsExtractor graphics, Dialogue dialogue, Dialogue.Node node, int x, int y,
							 int width, int mouseX, int mouseY) {
		rows.clear();
		boolean glosses = showGloss();
		for (int i = 0; i < node.choices().size(); i++) {
			Dialogue.Choice choice = node.choices().get(i);
			int rowY = y + i * ROW;
			boolean spent = tried.contains(i);
			boolean hover = !spent && mouseX >= x + 4 && mouseX < x + width - 4 && mouseY >= rowY && mouseY < rowY + ROW;
			if (hover) {
				graphics.fill(x + 4, rowY - 2, x + width - 4, rowY + ROW - 4, 0x33FFFFFF);
			}
			rows.add(new int[] {x + 4, rowY - 2, width - 8});
			Draw.label(graphics, this.font, (i + 1) + ".", x + PAD, rowY, Draw.DIM);
			Draw.korean(graphics, this.font, choice.ko(), x + 26, rowY, spent ? Draw.RED : Draw.WHITE);
			if (glosses) {
				Draw.truncated(graphics, this.font, HangulTextField.styled(I18n.t(dialogue.choiceKey(node.id(), i))), x + 26,
						rowY + 11, width - 34, Draw.GRAY);
			}
		}
	}

	private void drawTyped(GuiGraphicsExtractor graphics, Dialogue dialogue, Dialogue.Node node, int x, int y, int width) {
		if (typedPanel != null) {
			typedPanel.extractRenderState(graphics, this.font, x, y, width);
			return;
		}
		Draw.truncated(graphics, this.font, HangulTextField.styled(I18n.t(dialogue.promptKey(node.id()))), x + PAD, y,
				width - 2 * PAD, Draw.WHITE);
		freeField.extractRenderState(graphics, this.font, x + PAD, y + LINE * 2 - 4, width - 2 * PAD);
	}

	private String footer(Dialogue.Node node) {
		String keys = switch (node.kind()) {
			case CHOICE -> I18n.t("hangulquest.ui.dialogue.keys_choice", "1–" + node.choices().size());
			case TYPED -> I18n.t("hangulquest.ui.dialogue.keys_free", HangulTextField.keyName(freeField.input().toggleKey()));
			default -> I18n.t("hangulquest.ui.dialogue.keys_line");
		};
		return spoken && node.kind() != Dialogue.Kind.TYPED ? keys + " · " + I18n.t("hangulquest.ui.dialogue.replay") : keys;
	}
}
