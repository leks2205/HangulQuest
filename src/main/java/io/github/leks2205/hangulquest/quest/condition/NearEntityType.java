package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Counters;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/**
 * An entity of a kind within some blocks of the player. With {@code count} that many of them: 주민 두 명 근처에서,
 * 소 세 마리 근처에서 (명 for people, 마리 for animals, from counters.json).
 */
final class NearEntityType implements ConditionType<Condition.NearEntity> {
	/** Native numerals go up to twenty. */
	private static final int MAX_COUNT = 20;

	@Override
	public String id() {
		return "NEAR_ENTITY";
	}

	@Override
	public Class<Condition.NearEntity> conditionClass() {
		return Condition.NearEntity.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"), Field.integer("radius", 1, 64, false),
				Field.integer("count", 1, MAX_COUNT, false));
	}

	@Override
	public Condition.NearEntity parse(JsonObject json) {
		return new Condition.NearEntity(JsonFields.string(json, "entity", ""), JsonFields.number(json, "radius", 16),
				Math.max(1, JsonFields.integer(json, "count", 1)));
	}

	@Override
	public JsonObject toJson(Condition.NearEntity condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", condition.entity());
		Radius.write(json, condition.radius());
		if (condition.count() > 1) {
			json.addProperty("count", condition.count());
		}
		return json;
	}

	/**
	 * The nearest entity of that type must be within {@code radius} of the player. When the sensor
	 * did not record a distance (hand-built observations), presence in the scan is enough.
	 */
	@Override
	public boolean met(Condition.NearEntity condition, Observation obs) {
		if (condition.radius() < 0 || obs.nearbyEntities.getOrDefault(condition.entity(), 0) <= 0) {
			return false;
		}
		if (condition.count() > 1) {
			return nearCount(condition, obs) >= condition.count();
		}
		Double distance = obs.nearestEntityDistance.get(condition.entity());
		return distance == null || distance <= condition.radius();
	}

	/**
	 * How many of the kind stand within the radius: counted from where each one is. An observation that only
	 * has the counts (hand-built) is taken as it is, within the distance of the nearest.
	 */
	private static int nearCount(Condition.NearEntity condition, Observation obs) {
		if (obs.nearbyEntityPositions.isEmpty() || obs.position == null) {
			Double distance = obs.nearestEntityDistance.get(condition.entity());
			return distance == null || distance <= condition.radius()
					? obs.nearbyEntities.getOrDefault(condition.entity(), 0) : 0;
		}
		int near = 0;
		for (EntityFact fact : obs.nearbyEntityPositions) {
			if (fact.type().equals(condition.entity()) && fact.position().distanceTo(obs.position) <= condition.radius()) {
				near++;
			}
		}
		return near;
	}

	/** The name with its count: "주민 두 명", "소 세 마리"; the bare name for one. */
	private static String counted(Condition.NearEntity condition, String name) {
		if (name == null || name.isBlank() || condition.count() < 2) {
			return name;
		}
		return name + " " + KoGrammar.counter(condition.count(), Counters.shared().counter(condition.entity(), name));
	}

	@Override
	public List<String> grammarIds(Condition.NearEntity condition) {
		if (condition.count() < 2) {
			return List.of();
		}
		String counter = Counters.shared().counter(condition.entity(), null);
		String id = Counters.shared().grammar(counter);
		return List.of("L1.num_native", id == null ? "L1.counter_gae" : id);
	}

	@Override
	public void validate(Condition.NearEntity condition, ValidationContext context) {
		if (condition.count() < 1 || condition.count() > MAX_COUNT) {
			context.problem("count must be 1.." + MAX_COUNT + ", not " + condition.count());
		}
	}

	@Override
	public List<io.github.leks2205.hangulquest.quest.goal.GoalType.Mention> mentions(Condition.NearEntity condition) {
		return List.of(new io.github.leks2205.hangulquest.quest.goal.GoalType.Mention(io.github.leks2205.hangulquest.quest.goal.ObjectKind.ENTITY, condition.entity()));
	}

	@Override
	public String prefix(Condition.NearEntity condition, SentenceBuilder.Namer namer) {
		return SentenceBuilder.nearName(counted(condition, namer.entity(condition.entity())), "가까이에서 ");
	}

	@Override
	public String placeNoun(Condition.NearEntity condition, SentenceBuilder.Namer namer) {
		String name = counted(condition, namer.entity(condition.entity()));
		return name == null || name.isBlank() ? null : name + " 근처";
	}

	@Override
	public String unmetText(Condition.NearEntity condition, Observation obs, Catalog catalog) {
		return "가까이에 " + counted(condition, QuestStatus.entity(condition.entity(), catalog)) + " 필요";
	}
}
