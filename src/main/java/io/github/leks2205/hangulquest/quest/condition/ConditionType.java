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

	/** The same at a Korean level, for a condition whose wording follows the ladder (SKY: 밖에서 before 하늘이 보이는 곳에서). */
	default String prefix(C condition, SentenceBuilder.Namer namer, int level) {
		return prefix(condition, namer);
	}

	/** The phrase when the goal takes the condition over and checks it on its target ({@code GoalType.takesOver}). */
	default String targetPrefix(C condition, SentenceBuilder.Namer namer, int level) {
		return prefix(condition, namer, level);
	}

	/** Grammar points of {@link #targetPrefix}. */
	default List<String> targetGrammarIds(C condition, int level) {
		return grammarIds(condition, level);
	}

	/**
	 * For a prefix that says a place with 에서 ("해변에서 "): the place noun alone ("해변"), so that a sentence whose
	 * body starts with a place of its own can use it as a modifier instead of doubling the 에서; null for every
	 * phrase that is no place noun (밖에서, 땅속에서, 가까이에서).
	 */
	default String placeNoun(C condition, SentenceBuilder.Namer namer) {
		return null;
	}

	/**
	 * Ways an authored clue says the condition in its own words, without the phrase ("밤에 " as "한밤에" or
	 * "밤이 되면"): when the clue holds one of them, the phrase is not put in front. Kept to forms that cannot be
	 * another word (밤 is also a chestnut, 낮다 is "low"), so a plain "밤을 먹어요" still gets "밤에".
	 */
	default List<String> words(C condition, SentenceBuilder.Namer namer) {
		return List.of();
	}

	/** The status line while this condition is not met. */
	String unmetText(C condition, Observation obs, Catalog catalog);

	/** Grammar points the prefix teaches (ids such as {@code L2.eseo_activity}); most conditions list none. */
	default List<String> grammarIds(C condition) {
		return List.of();
	}

	/** Same at a Korean level. */
	default List<String> grammarIds(C condition, int level) {
		return grammarIds(condition);
	}

	/** The block, item or entity the condition points at: a step may teach the word for it. */
	default List<io.github.leks2205.hangulquest.quest.goal.GoalType.Mention> mentions(C condition) {
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
