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
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** Keep the crosshair on a block for a moment. */
final class LookAtBlockType implements GoalType<Goal.LookAtBlock> {
	@Override
	public String id() {
		return "LOOK_AT_BLOCK";
	}

	@Override
	public Class<Goal.LookAtBlock> goalClass() {
		return Goal.LookAtBlock.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"), Field.integer("dwellMs", 100, 10000, true));
	}

	@Override
	public Goal.LookAtBlock parse(JsonObject json) {
		return new Goal.LookAtBlock(JsonFields.string(json, "block", ""), JsonFields.integer(json, "dwellMs", 600));
	}

	@Override
	public JsonObject toJson(Goal.LookAtBlock goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		json.addProperty("dwellMs", goal.dwellMs());
		return json;
	}

	@Override
	public String keyPart(Goal.LookAtBlock goal) {
		return goal.block() + "/" + goal.dwellMs();
	}

	@Override
	public boolean evaluate(Goal.LookAtBlock goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		String looked = Matching.accepts(tracker, goal.block(), obs.lookedBlock) ? goal.block() : null;
		if (looked == null && nightLatch && Matching.accepts(tracker, goal.block(), obs.lookedBlockAtNight)) {
			looked = goal.block();
		}
		return Matching.dwell(tracker, goal.block(), looked, goal.dwellMs());
	}

	@Override
	public boolean nightLatchable() {
		return true;
	}

	@Override
	public long dwellNeededMs(Goal.LookAtBlock goal) {
		return goal.dwellMs();
	}

	/** Water and lava are looked at through their own ray: the crosshair pick passes through them. */
	@Override
	public boolean needsFluidLook(Goal.LookAtBlock goal) {
		return "minecraft:water".equals(goal.block()) || "minecraft:lava".equals(goal.block());
	}

	@Override
	public Verb verb(Goal.LookAtBlock goal, VerbTable table) {
		return Verb.WATCH;
	}

	@Override
	public String korean(Goal.LookAtBlock goal, SentenceContext c) {
		return SentenceBuilder.phrase(KoGrammar.eul(c.obj()) + " " + SentenceBuilder.seconds(goal.dwellMs())
				+ "초 동안", c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.LookAtBlock goal, String obj, Verb verb) {
		return KoGrammar.eul(obj) + " " + verb.past();
	}

	@Override
	public String status(Goal.LookAtBlock goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return QuestStatus.look(tracker.dwellMs, goal.dwellMs());
	}

	@Override
	public String object(Goal.LookAtBlock goal) {
		return goal.block();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}
}
