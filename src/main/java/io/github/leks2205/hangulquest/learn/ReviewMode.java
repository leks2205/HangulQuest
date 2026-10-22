package io.github.leks2205.hangulquest.learn;

/**
 * How an item was met. {@code schedules} says whether a graded event moves the FSRS card;
 * {@code recall} says whether a correct event counts as unaided recall for {@link Mastery}.
 * A guess among two or three options is never evidence of recall, so MC2 and MC3 do neither.
 */
public enum ReviewMode {
	MC4(true, true),
	MC3(false, false),
	MC2(false, false),
	TILE(true, true),
	TYPED(true, true),
	SELF(true, true),
	QUEST_UNAIDED(true, true),
	/** Done after a hint: the schedule moves (Hard) but it does not count as recall. */
	QUEST_HINTED(true, false),
	/** Held-out measurement: logged, never changes the schedule. */
	PROBE(false, false),
	/** A listening trial of the sound forge: logged, never changes a schedule, never counts as recall. */
	HVPT(false, false),
	EXPOSURE(false, false);

	private final boolean schedules;
	private final boolean recall;

	ReviewMode(boolean schedules, boolean recall) {
		this.schedules = schedules;
		this.recall = recall;
	}

	public boolean schedules() {
		return schedules;
	}

	public boolean recall() {
		return recall;
	}

	/** The multiple-choice mode for a question with this many options. */
	public static ReviewMode multipleChoice(int options) {
		return options >= 4 ? MC4 : options == 3 ? MC3 : MC2;
	}
}
