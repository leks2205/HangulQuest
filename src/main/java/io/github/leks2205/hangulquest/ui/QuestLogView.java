package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.quest.QuestEngine.TrackView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The quest log, built once per tick instead of once per frame: the rows with their texts, the
 * measured widths and the cut lines. A frame only draws what is stored here.
 */
final class QuestLogView {
	private static final int ROW_HEIGHT = 24;
	private static final int MARGIN = 6;

	private record Row(FormattedCharSequence title, FormattedCharSequence counter, int counterWidth,
					   FormattedCharSequence line, boolean focused, FormattedCharSequence marker, int markerWidth) {
	}

	private final RefreshGate gate = new RefreshGate();
	private Row[] rows = new Row[0];
	private FormattedCharSequence more;
	private int width;
	private int bottom = MARGIN;

	/** True when {@link #rebuild} has to run before the next draw. */
	boolean due(long tick, int rowLimit, int guiWidth) {
		return gate.due(tick, rowLimit, guiWidth);
	}

	/** Forces a rebuild, e.g. after the interface language changed. */
	void invalidate() {
		gate.reset();
	}

	boolean empty() {
		return rows.length == 0;
	}

	/** What the log covers at the top left, for windows that keep clear of it; empty when there is no log. */
	List<WindowPlacement.Rect> area() {
		return empty() ? List.of() : List.of(new WindowPlacement.Rect(0, 0, MARGIN + width + MARGIN, bottom));
	}

	/** Bottom edge of the log, so the hint card can sit below it. */
	int bottom() {
		return bottom;
	}

	void rebuild(Font font, List<TrackView> views, int rowLimit, int guiWidth) {
		if (views.isEmpty()) {
			rows = new Row[0];
			more = null;
			bottom = MARGIN;
			return;
		}
		int limit = Math.max(1, Math.min(views.size(), rowLimit));
		int maxWidth = Math.min(260, guiWidth - 16);
		Component separator = Component.literal(" · ");
		int separatorWidth = font.width(separator);

		Component[] titles = new Component[limit];
		Component[] counters = new Component[limit];
		Component[] objectives = new Component[limit];
		Component[] statuses = new Component[limit];
		Component[] markers = new Component[limit];
		int[] objectiveWidths = new int[limit];
		int[] statusWidths = new int[limit];
		int fullWidth = 120;
		for (int i = 0; i < limit; i++) {
			TrackView view = views.get(i);
			titles[i] = KoText.ko(view.titleKo());
			counters[i] = Component.literal(QuestLogText.counter(view));
			Component clue = KoText.ko(view.clueKo());
			String suffix = QuestLogText.countSuffix(view);
			objectives[i] = suffix.isEmpty() ? clue : Component.empty().append(clue).append(Component.literal(suffix));
			objectiveWidths[i] = font.width(objectives[i]);
			String status = QuestLogText.conditionStatus(view);
			if (!status.isEmpty()) {
				statuses[i] = KoText.ko(status);
				statusWidths[i] = font.width(statuses[i]);
			}
			String marker = QuestLogText.situationalMarker(view);
			if (view.situational()) {
				markers[i] = Component.empty().append(KoText.ko(QuestLogText.SITUATIONAL_KO))
						.append(Component.literal(marker.isEmpty() ? "" : " " + marker));
			}
			int markerWidth = markers[i] == null ? 0 : font.width(markers[i]) + 6;
			int titleWidth = font.width(titles[i]) + 8 + markerWidth + font.width(counters[i]);
			int objectiveWidth = objectiveWidths[i] + (statuses[i] == null ? 0 : separatorWidth + statusWidths[i]);
			fullWidth = Math.max(fullWidth, Math.min(maxWidth, titleWidth));
			fullWidth = Math.max(fullWidth, Math.min(maxWidth, objectiveWidth));
		}
		width = Math.min(maxWidth, fullWidth);

		Row[] built = new Row[limit];
		for (int i = 0; i < limit; i++) {
			Component line = objectives[i];
			if (statuses[i] != null
					&& QuestLogText.statusFits(objectiveWidths[i], 6, statusWidths[i], width)) {
				line = Component.empty().append(objectives[i]).append(separator).append(statuses[i]);
			}
			built[i] = new Row(titles[i].getVisualOrderText(), counters[i].getVisualOrderText(),
					font.width(counters[i]), Draw.fit(font, line, width), views.get(i).focused(),
					markers[i] == null ? null : markers[i].getVisualOrderText(),
					markers[i] == null ? 0 : font.width(markers[i]));
		}
		rows = built;
		more = views.size() > limit
				? Component.literal("+" + (views.size() - limit)).getVisualOrderText() : null;
		bottom = MARGIN + limit * ROW_HEIGHT + (more != null ? 9 : 0);
	}

	void draw(GuiGraphicsExtractor graphics, Font font) {
		int row = MARGIN;
		for (Row entry : rows) {
			Draw.text(graphics, font, entry.title(), MARGIN, row, entry.focused() ? Draw.GOLD : Draw.WHITE, true);
			Draw.text(graphics, font, entry.counter(), MARGIN + width - entry.counterWidth(), row, Draw.DIM, true);
			if (entry.marker() != null) { // "상황 here": this row follows where the player is
				Draw.text(graphics, font, entry.marker(), MARGIN + width - entry.counterWidth() - 6 - entry.markerWidth(),
						row, Draw.BLUE, true);
			}
			Draw.text(graphics, font, entry.line(), MARGIN, row + 11,
					entry.focused() ? Draw.WHITE : Draw.GRAY, true);
			row += ROW_HEIGHT;
		}
		if (more != null) {
			Draw.text(graphics, font, more, MARGIN, row, Draw.DIM, true);
		}
	}
}
