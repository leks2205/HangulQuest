package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.quest.QuestEngine.TrackView;
import io.github.leks2205.hangulquest.util.I18n;

/** The strings of one quest-log row, as plain text so they can be built (and tested) without a font. */
final class QuestLogText {
	private QuestLogText() {
	}

	/** Step counter, e.g. "2/4". */
	static String counter(TrackView view) {
		return (view.stepIndex() + 1) + "/" + view.stepCount();
	}

	/** The count of a counting goal behind the clue (" 0/3"), or "" when the goal is not a count. */
	static String countSuffix(TrackView view) {
		int needed = view.needed();
		if (!view.countable() || needed <= 1) {
			return "";
		}
		return " " + view.progress() + "/" + needed;
	}

	/** Only condition messages ("밤 필요") are worth a second sentence; counts are already inline. */
	static String conditionStatus(TrackView view) {
		String status = view.status();
		if (status == null || status.isBlank() || status.contains("/")) {
			return "";
		}
		return status;
	}

	/** The status joins the clue on one line only when the whole line still fits. */
	static boolean statusFits(int objectiveWidth, int separatorWidth, int statusWidth, int width) {
		return objectiveWidth + separatorWidth + statusWidth <= width;
	}

	/** The Korean word of the marker on the row of the situational trail: "here". */
	static final String SITUATIONAL_KO = "상황";

	/** The marker's translation in the interface language, or "" for a row that is not the situational one. */
	static String situationalMarker(TrackView view) {
		return view.situational() ? I18n.t("hangulquest.ui.hud.situational") : "";
	}
}
