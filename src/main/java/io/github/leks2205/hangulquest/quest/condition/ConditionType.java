package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/**
 * Everything the game knows about one kind of condition: JSON, the check against the world, the
 * Korean phrase and the status line. Like goals, a new type is one record in {@link Condition}, one
 * class implementing this interface and one line in {@link ConditionTypes}.
 */
public interface ConditionType<C extends Condition> {
	/** The JSON {@code type} string, e.g. IN_BIOME. */
	String id();

	Class<C> conditionClass();

	/** The JSON properties besides {@code type}, in the order the schema lists them. */
	List<Field> fields();

	C parse(JsonObject json);

	JsonObject toJson(C condition);

	/** Whether the condition holds right now. */
	boolean met(C condition, Observation obs);

	/** The phrase in front of the goal sentence ("밤에 "), with its trailing space; may be empty. */
	String prefix(C condition, SentenceBuilder.Namer namer);

	/** The status line while this condition is not met. */
	String unmetText(C condition, Observation obs, Catalog catalog);

	/** Grammar points the prefix teaches (ids such as {@code L2.eseo_activity}); most conditions list none. */
	default List<String> grammarIds(C condition) {
		return List.of();
	}

	/** Adds the problems of this condition (a bad id, an anchor that cannot work). */
	default void validate(C condition, ValidationContext context) {
	}

	/** True for the time-of-day condition, which a goal may excuse once the night has passed. */
	default boolean timeOfDay() {
		return false;
	}

	default boolean requiresNight(C condition) {
		return false;
	}
}
