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
import io.github.leks2205.hangulquest.sense.ContainerTakeFact;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;

/**
 * Take items out of a chest, barrel or other container: "상자에서 사과를 꺼내요". The counterpart of
 * {@link PutInType}, seen the same way: the slot tally of the open screen, where the container lost
 * what the player's side gained.
 */
final class TakeOutType implements GoalType<Goal.TakeOut> {
	@Override
	public String id() {
		return "TAKE_OUT";
	}

	@Override
	public Class<Goal.TakeOut> goalClass() {
		return Goal.TakeOut.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"), Field.resource("container"), Field.count("count"));
	}

	@Override
	public Goal.TakeOut parse(JsonObject json) {
		return new Goal.TakeOut(JsonFields.string(json, "item", ""), JsonFields.string(json, "container", ""),
				JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.TakeOut goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		json.addProperty("container", goal.container());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.TakeOut goal) {
		return goal.item() + "<" + goal.container() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.TakeOut goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		for (ContainerTakeFact fact : obs.takenOut) {
			if (fact.block() != null && Matching.accepts(tracker, goal.container(), fact.block())
					&& Matching.accepts(tracker, goal.item(), fact.item())) {
				tracker.count += Math.max(1, fact.count());
			}
		}
		return tracker.count >= Math.max(1, goal.count());
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.TakeOut goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.TakeOut goal, VerbTable table) {
		return Verb.TAKE_OUT;
	}

	@Override
	public String korean(Goal.TakeOut goal, SentenceContext c) {
		return SentenceBuilder.phrase(head(goal, c, c.withCounts()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.TakeOut goal, String obj, Verb verb) {
		return SentenceBuilder.counted(KoGrammar.eul(obj), goal.count(), true, goal.item(), obj) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.TakeOut goal, SentenceContext c) {
		return head(goal, c, true) + " " + c.verb().past();
	}

	/** "상자에서 사과를 (두 개)": where it comes out of, then the thing. */
	private static String head(Goal.TakeOut goal, SentenceContext c, boolean withCounts) {
		String item = item(goal, c);
		return KoGrammar.eseo(container(goal, c)) + " "
				+ SentenceBuilder.counted(KoGrammar.eul(item), goal.count(), withCounts, goal.item(), item);
	}

	@Override
	public List<String> grammarIds(Goal.TakeOut goal, SentenceContext c) {
		List<String> ids = new ArrayList<>(GoalType.super.grammarIds(goal, c));
		ids.add("L2.eseo_activity");
		return ids;
	}

	@Override
	public String status(Goal.TakeOut goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.TakeOut goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}

	@Override
	public String taskSignature(Goal.TakeOut goal) {
		return id() + ":" + goal.item() + "<" + goal.container() + ":" + Verb.TAKE_OUT.name();
	}

	/** The word may teach the item or the container. */
	@Override
	public List<Mention> mentions(Goal.TakeOut goal) {
		return List.of(new Mention(ObjectKind.ITEM, goal.item()), new Mention(ObjectKind.BLOCK, goal.container()));
	}

	@Override
	public void validate(Goal.TakeOut goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (ValidationContext.badId(goal.container())) {
			context.problem("bad block " + goal.container());
		}
		if (goal.count() < 1) {
			context.problem("count must be at least 1");
		}
	}

	/** The item: the sentence word unless that word is the container. */
	private static String item(Goal.TakeOut goal, SentenceContext c) {
		boolean wordIsContainer = c.wordNames(ObjectKind.BLOCK, goal.container())
				&& !c.wordNames(ObjectKind.ITEM, goal.item());
		return wordIsContainer ? c.name(ObjectKind.ITEM, goal.item()) : c.obj();
	}

	private static String container(Goal.TakeOut goal, SentenceContext c) {
		String name = c.name(ObjectKind.BLOCK, goal.container());
		return name.isEmpty() ? "상자" : name;
	}
}
