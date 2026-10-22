package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.WornItems;

import java.util.List;
import java.util.Locale;

/**
 * Wear a piece of armour (투구를 써요, 갑옷을 입어요, 바지를 입어요, 신발을 신어요) or take it off (벗어요). The client
 * reads the four armour slots of the player. Putting on counts when the slot was seen without the piece and then
 * has it; taking off counts when the slot was seen with it and then does not. A piece that is already worn when
 * the step begins therefore has to come off and go on again, the same way an item already in the inventory does
 * not count for an acquire step.
 */
final class EquipType implements GoalType<Goal.Equip> {
	static final String ON = "ON";
	static final String OFF = "OFF";

	@Override
	public String id() {
		return "EQUIP";
	}

	@Override
	public Class<Goal.Equip> goalClass() {
		return Goal.Equip.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"),
				Field.choice("change", false, "ON (the default) puts the armour piece on, OFF takes it off.", ON, OFF));
	}

	@Override
	public Goal.Equip parse(JsonObject json) {
		String change = JsonFields.string(json, "change", null);
		return new Goal.Equip(JsonFields.string(json, "item", ""),
				change == null || change.isBlank() ? null : change.toUpperCase(Locale.ROOT));
	}

	@Override
	public JsonObject toJson(Goal.Equip goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		if (goal.change() != null) {
			json.addProperty("change", goal.change());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.Equip goal) {
		return goal.item() + "/" + (off(goal) ? OFF : ON);
	}

	static boolean off(Goal.Equip goal) {
		return OFF.equals(goal.change());
	}

	/** The armour slot an item goes in ({@code head}, {@code chest}, {@code legs}, {@code feet}), or null. */
	static String slot(String item) {
		String id = BlockSpec.representative(item); // #minecraft:chest_armor stands for a chestplate
		if (id == null) {
			return null;
		}
		String path = id.substring(id.indexOf(':') + 1);
		if (path.endsWith("_helmet")) {
			return WornItems.HEAD;
		}
		if (path.endsWith("_chestplate")) {
			return WornItems.CHEST;
		}
		if (path.endsWith("_leggings")) {
			return WornItems.LEGS;
		}
		return path.endsWith("_boots") ? WornItems.FEET : null;
	}

	@Override
	public boolean evaluate(Goal.Equip goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		String slot = slot(goal.item());
		if (slot == null) {
			return false;
		}
		String worn = obs.worn.get(slot);
		boolean wearing = worn != null && Matching.accepts(tracker, goal.item(), worn);
		boolean wantOn = !off(goal);
		if (wearing != wantOn) {
			tracker.phase = 1; // seen the other way round: the change that follows is the player's
			return false;
		}
		return tracker.phase == 1;
	}

	/** 투구는 쓰고, 갑옷과 바지는 입고, 신발은 신는다; 벗는 건 모두 벗다. */
	@Override
	public Verb verb(Goal.Equip goal, VerbTable table) {
		if (off(goal)) {
			return Verb.TAKE_OFF;
		}
		return table.choose(id(), BlockSpec.representative(goal.item()), 1, Verb.PUT_ON);
	}

	@Override
	public String korean(Goal.Equip goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.objectPhrase(c.obj(), c.verb()), c.verb(), c.level(),
				c.finale());
	}

	@Override
	public String ceremony(Goal.Equip goal, String obj, Verb verb) {
		return SentenceBuilder.objectPhrase(obj, verb) + " " + verb.past();
	}

	@Override
	public String status(Goal.Equip goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return "";
	}

	@Override
	public String object(Goal.Equip goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}

	/** Any helmet, any chestplate: {@code #minecraft:chest_armor} says 갑옷 and takes every material. */
	@Override
	public boolean tagObject() {
		return true;
	}

	@Override
	public void validate(Goal.Equip goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (slot(goal.item()) == null) {
			context.problem("EQUIP needs a helmet, chestplate, leggings or boots, not " + goal.item());
		}
		if (goal.change() != null && !ON.equals(goal.change()) && !OFF.equals(goal.change())) {
			context.problem("change must be ON or OFF, not " + goal.change());
		}
	}
}
