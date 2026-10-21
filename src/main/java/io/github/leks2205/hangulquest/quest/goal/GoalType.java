package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/**
 * Everything the game knows about one kind of goal: how it is read from JSON, checked against the
 * world, spoken in Korean, shown in the status line and validated. The sealed {@link Goal} records
 * only carry the data; the callers dispatch through {@link GoalTypes}.
 *
 * <p>Adding a goal type takes three small edits:
 * <ol>
 * <li>a record in {@link Goal} (this keeps the compiler's exhaustiveness checks);</li>
 * <li>one class implementing this interface, holding all the logic;</li>
 * <li>one line in {@link GoalTypes}.</li>
 * </ol>
 * The JSON schema in docs/questline.schema.json is generated from {@link #fields()}.
 */
public interface GoalType<G extends Goal> {
	/** The JSON {@code type} string, e.g. BREAK_BLOCK. */
	String id();

	Class<G> goalClass();

	/** The JSON properties besides {@code type}, in the order the schema lists them. */
	List<Field> fields();

	/** Reads the goal; a missing property takes its default. */
	G parse(JsonObject json);

	/** The JSON form that {@link #parse} reads back. */
	JsonObject toJson(G goal);

	/** The part of the progress key that tells two goals of this type apart. */
	String keyPart(G goal);

	// ------------------------------------------------------------- evaluation

	/**
	 * Advances the tracker with this tick's observation; true when the goal is met. Conditions have
	 * been checked already. {@code nightLatch} is set when a "night" condition is excused because the
	 * event happened at night (only for types that are {@link #nightLatchable()}).
	 */
	boolean evaluate(G goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch);

	/** Runs before every tick, even while the conditions fail. */
	default void beforeTick(G goal, Observation obs, GoalEvaluator.Tracker tracker) {
	}

	/** Whether an event at night still counts after the night is over (a bed skipped the time). */
	default boolean nightLatchable() {
		return false;
	}

	/** Whether being asleep excuses a time-of-day condition for this goal. */
	default boolean excusesTimeCondition(G goal, Observation obs) {
		return false;
	}

	/** Whether the sensor must pick entities along the camera ray far beyond vanilla's reach. */
	default boolean needsFarEntityLook() {
		return false;
	}

	/** True when the status line and the HUD show a counter for this goal. */
	default boolean countable() {
		return false;
	}

	/** How much the counter must reach. */
	default int needed(G goal) {
		return 1;
	}

	/** How long the player has to look, in milliseconds; 0 for goals without a dwell time. */
	default long dwellNeededMs(G goal) {
		return 0L;
	}

	// ---------------------------------------------------------------- Korean

	/** The verb of the sentence; an explicit verb on the goal wins over the table. */
	Verb verb(G goal, VerbTable table);

	/** The instruction sentence, without the condition prefix. */
	String korean(G goal, SentenceContext context);

	/** The past-tense finale body, without the condition prefix and the full stop. */
	String ceremony(G goal, String obj, Verb verb);

	/** The Korean name of the object, or null when there is none. */
	default String objectName(G goal, SentenceBuilder.Namer namer) {
		String object = object(goal);
		return switch (objectKind()) {
			case BLOCK -> namer.block(object);
			case ITEM -> namer.item(object);
			case ENTITY -> namer.entity(object);
			case BIOME -> SentenceBuilder.biomeName(object, namer);
			case DIMENSION -> SentenceBuilder.dimension(object);
			case NONE -> null;
		};
	}

	// ----------------------------------------------------------------- status

	/** A fixed status that beats everything else (sleeping in a bed), or null. */
	default String statusOverride(G goal, Observation obs) {
		return null;
	}

	/** The status line: progress toward the goal. {@code word} is the Korean noun of the first taught word. */
	String status(G goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog, String word);

	// ------------------------------------------------------------- validation

	/** The id of the thing the goal points at, null for goals without one. */
	String object(G goal);

	ObjectKind objectKind();

	/** Type, object and verb as one string: two steps with the same signature ask for the same task. */
	default String taskSignature(G goal) {
		String object = object(goal);
		return id() + ":" + (object == null ? "" : object) + ":" + verb(goal, VerbTable.shared()).name();
	}

	/** Whether the taught word names what the goal points at. */
	default boolean covers(G goal, Concept concept) {
		String object = object(goal);
		return switch (objectKind()) {
			case BLOCK, ITEM -> object != null
					&& (concept.items != null && concept.items.contains(object) || object.equals(concept.icon));
			case ENTITY -> object != null && object.equals(concept.entity);
			case BIOME -> object == null || object.startsWith("#") || object.equals(concept.biome);
			default -> true;
		};
	}

	/** Adds the problems of this goal: a taught word that does not fit, and bad ids. */
	default void validate(G goal, ValidationContext context) {
		if (!context.step().teach().isEmpty()) {
			var first = context.catalog().concept(context.step().teach().get(0));
			if (first.isPresent() && !covers(goal, first.get())) {
				context.problem("the sentence word '" + first.get().ko + "' (" + first.get().id
						+ ") does not describe the goal " + describe(goal));
			}
		}
		String noun = objectKind().noun();
		if (noun != null && ValidationContext.badId(object(goal))) {
			context.problem("bad " + noun + " " + object(goal));
		}
	}

	private String describe(G goal) {
		ObjectKind kind = objectKind();
		boolean named = kind == ObjectKind.BLOCK || kind == ObjectKind.ITEM || kind == ObjectKind.ENTITY;
		return named && object(goal) != null ? object(goal) : goal.getClass().getSimpleName();
	}
}
