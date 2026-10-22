package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/**
 * Raise young animals: "소를 키워요". The sensor sees two things: the player feeding (or at least using an
 * item on) an animal of the kind, and a baby of that kind (the synced "young" flag) that was not there
 * before. A baby that shows up within two minutes of a feeding is the result of it. Babies that were already
 * near when the step began, or that wander in later than the window, do not count. The baby also has to
 * stand within {@value #PARENT_RANGE} blocks of two of the animals the player fed: a calf next to the
 * player, born in the next pen, is no result of this feeding.
 */
final class BreedType implements GoalType<Goal.Breed> {
	/** How long after the last feeding a new baby is taken to come from it. */
	private static final long WINDOW_MS = 120_000L;
	/** A baby is born where its parents stand; they come together to within about three blocks first. */
	static final double PARENT_RANGE = 4.0;

	@Override
	public String id() {
		return "BREED";
	}

	@Override
	public Class<Goal.Breed> goalClass() {
		return Goal.Breed.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"), Field.count("count"));
	}

	@Override
	public Goal.Breed parse(JsonObject json) {
		return new Goal.Breed(JsonFields.string(json, "entity", ""), JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.Breed goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", goal.entity());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.Breed goal) {
		return goal.entity() + "/" + goal.count();
	}

	/** Notes the animals of the kind the player fed (or used an item on) this tick; true if there were any. */
	private static boolean fed(Goal.Breed goal, Observation obs, GoalEvaluator.Tracker tracker) {
		boolean fed = false;
		for (int i = 0; i < obs.interactedEntities.size(); i++) {
			if (goal.entity().equals(obs.interactedEntities.get(i))) {
				fed = true;
				if (i < obs.interactedUuids.size() && obs.interactedUuids.get(i) != null) {
					tracker.parents.putIfAbsent(obs.interactedUuids.get(i).toString(), null);
				}
			}
		}
		for (GiveFact gave : obs.gave) {
			if (gave.method() == GiveFact.Method.FEED && goal.entity().equals(gave.targetType())) {
				fed = true;
				if (gave.targetUuid() != null) {
					tracker.parents.putIfAbsent(gave.targetUuid().toString(), null);
				}
			}
		}
		return fed;
	}

	/** Two fed animals stand within {@value #PARENT_RANGE} blocks of the baby. */
	private static boolean bornToTheFed(EntityFact baby, GoalEvaluator.Tracker tracker) {
		int near = 0;
		for (double[] at : tracker.parents.values()) {
			if (at != null && Math.sqrt(Math.pow(at[0] - baby.x(), 2) + Math.pow(at[1] - baby.y(), 2)
					+ Math.pow(at[2] - baby.z(), 2)) <= PARENT_RANGE) {
				near++;
			}
		}
		return near >= 2;
	}

	@Override
	public boolean evaluate(Goal.Breed goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		if (fed(goal, obs, tracker)) {
			tracker.mark = obs.now;
		}
		for (EntityFact fact : obs.nearbyEntityPositions) { // where the fed animals stand now
			if (!fact.baby() && tracker.parents.containsKey(fact.uuid().toString())) {
				tracker.parents.put(fact.uuid().toString(), new double[] {fact.x(), fact.y(), fact.z()});
			}
		}
		for (EntityFact fact : obs.nearbyEntityPositions) {
			if (!fact.baby() || !goal.entity().equals(fact.type())) {
				continue;
			}
			// the first sight of a baby: counted when a feeding is fresh and the fed pair stands around it,
			// else it was simply there (or belongs to other animals)
			if (tracker.marks.add(fact.uuid().toString()) && tracker.mark != 0L
					&& obs.now - tracker.mark <= WINDOW_MS && bornToTheFed(fact, tracker)) {
				tracker.count++;
			}
		}
		return tracker.count >= Math.max(1, goal.count());
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.Breed goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.Breed goal, VerbTable table) {
		return Verb.RAISE;
	}

	@Override
	public String korean(Goal.Breed goal, SentenceContext c) {
		return SentenceBuilder.phrase(counted(goal, c, c.withCounts()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Breed goal, String obj, Verb verb) {
		return SentenceBuilder.counted(KoGrammar.eul(obj), goal.count(), true, goal.entity(), obj) + " "
				+ verb.past();
	}

	private static String counted(Goal.Breed goal, SentenceContext c, boolean withCounts) {
		return SentenceBuilder.counted(KoGrammar.eul(c.obj()), goal.count(), withCounts, goal.entity(), c.obj());
	}

	@Override
	public String status(Goal.Breed goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.Breed goal) {
		return goal.entity();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ENTITY;
	}

	@Override
	public void validate(Goal.Breed goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.count() < 1) {
			context.problem("count must be at least 1");
		}
	}
}
