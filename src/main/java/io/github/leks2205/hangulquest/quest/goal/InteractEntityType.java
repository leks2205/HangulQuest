package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** Interact with an entity: feed it, talk to it, trade with it. */
final class InteractEntityType implements GoalType<Goal.InteractEntity> {
	@Override
	public String id() {
		return "INTERACT_ENTITY";
	}

	@Override
	public Class<Goal.InteractEntity> goalClass() {
		return Goal.InteractEntity.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"));
	}

	@Override
	public Goal.InteractEntity parse(JsonObject json) {
		return new Goal.InteractEntity(JsonFields.string(json, "entity", ""));
	}

	@Override
	public JsonObject toJson(Goal.InteractEntity goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", goal.entity());
		return json;
	}

	@Override
	public String keyPart(Goal.InteractEntity goal) {
		return goal.entity();
	}

	@Override
	public boolean evaluate(Goal.InteractEntity goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		for (String interacted : obs.interactedEntities) {
			if (Matching.sameEntity(tracker, goal.entity(), interacted)) {
				tracker.count++;
			}
		}
		return tracker.count >= 1;
	}

	@Override
	public Verb verb(Goal.InteractEntity goal, VerbTable table) {
		return table.choose(id(), goal.entity(), 1, Verb.FEED);
	}

	/** The tool in the hand decides the verb: shears shear a sheep, a lead leashes, a brush brushes (see {@link Tools}). */
	@Override
	public Verb verb(Goal.InteractEntity goal, VerbTable table, List<io.github.leks2205.hangulquest.quest.Condition> conditions) {
		Verb tool = Tools.interaction(conditions, goal.entity());
		return tool != null ? tool : verb(goal, table);
	}

	/** The sentence says the tool ("가위로 양털을 깎아요"), so a held-item condition for it is not said twice. */
	@Override
	public boolean says(Goal.InteractEntity goal, io.github.leks2205.hangulquest.quest.Condition condition) {
		return Tools.saysHolding(goal.entity(), condition);
	}

	@Override
	public String korean(Goal.InteractEntity goal, SentenceContext c) {
		return SentenceBuilder.phrase(withTool(c, c.obj()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.InteractEntity goal, String obj, Verb verb) {
		return SentenceBuilder.objectPhrase(obj, verb) + " " + verb.prefix() + verb.past();
	}

	@Override
	public String ceremony(Goal.InteractEntity goal, SentenceContext c) {
		return withTool(c, c.obj()) + " " + c.verb().prefix() + c.verb().past();
	}

	/**
	 * "가위로 양털을", "가위로 늑대의 갑옷을", "끈으로 소를", "솔로 아르마딜로를": the tool with 로, then what the
	 * click does to the creature. Every other verb keeps the plain object.
	 */
	private static String withTool(SentenceContext c, String creature) {
		String itemId = Tools.tool(c.verb());
		if (itemId == null) {
			return SentenceBuilder.objectPhrase(creature, c.verb());
		}
		String name = c.name(ObjectKind.ITEM, itemId);
		String tool = name.isEmpty() ? "" : KoGrammar.euro(name) + " ";
		String target = switch (c.verb()) {
			case SHEAR -> KoGrammar.eul(creature + "털");
			case UNARMOUR -> KoGrammar.eul(creature + "의 갑옷");
			default -> KoGrammar.eul(creature);
		};
		return tool + target;
	}

	@Override
	public List<String> grammarIds(Goal.InteractEntity goal, SentenceContext c) {
		List<String> ids = new java.util.ArrayList<>(GoalType.super.grammarIds(goal, c));
		if (Tools.tool(c.verb()) != null) {
			ids.add("L3.euro_instrument"); // 가위로
		}
		return ids;
	}

	@Override
	public String status(Goal.InteractEntity goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return "";
	}

	@Override
	public String object(Goal.InteractEntity goal) {
		return goal.entity();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ENTITY;
	}

	/**
	 * A golem touched with an empty hand does nothing (an iron golem takes an iron ingot, a snow golem loses its pumpkin
	 * to shears), and 만지다 fits no other creature. The goal would count a click that changes nothing, so it is refused.
	 */
	@Override
	public void validate(Goal.InteractEntity goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (verb(goal, VerbTable.shared(), context.step().conditions()) == Verb.TOUCH) {
			context.problem("a click on " + goal.entity() + " changes nothing; give it something (GIVE) instead of "
					+ "touching it");
		}
	}
}
