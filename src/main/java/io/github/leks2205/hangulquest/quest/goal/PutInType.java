package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.ContainerPutFact;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;

/** Put items into a chest, barrel or other container: "상자 안에 사과를 넣어요". */
final class PutInType implements GoalType<Goal.PutIn> {
	@Override
	public String id() {
		return "PUT_IN";
	}

	@Override
	public Class<Goal.PutIn> goalClass() {
		return Goal.PutIn.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"), Field.resource("container"), Field.count("count"));
	}

	@Override
	public Goal.PutIn parse(JsonObject json) {
		return new Goal.PutIn(JsonFields.string(json, "item", ""), JsonFields.string(json, "container", ""),
				JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.PutIn goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		json.addProperty("container", goal.container());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.PutIn goal) {
		return goal.item() + ">" + goal.container() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.PutIn goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		for (ContainerPutFact fact : obs.putIn) {
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
	public int needed(Goal.PutIn goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.PutIn goal, VerbTable table) {
		return Verb.PUT_IN;
	}

	@Override
	public String korean(Goal.PutIn goal, SentenceContext c) {
		String head = container(goal, c) + " 안에 "
				+ SentenceBuilder.counted(KoGrammar.eul(item(goal, c)), goal.count(), c.withCounts(), goal.item(),
					item(goal, c));
		return SentenceBuilder.phrase(head, c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.PutIn goal, String obj, Verb verb) {
		return SentenceBuilder.counted(KoGrammar.eul(obj), goal.count(), true, goal.item(), obj) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.PutIn goal, SentenceContext c) {
		return container(goal, c) + " 안에 "
				+ SentenceBuilder.counted(KoGrammar.eul(item(goal, c)), goal.count(), true, goal.item(), item(goal, c)) + " " + c.verb().past();
	}

	@Override
	public List<String> grammarIds(Goal.PutIn goal, SentenceContext c) {
		List<String> ids = new ArrayList<>(GoalType.super.grammarIds(goal, c));
		ids.add("L1.loc.an");
		return ids;
	}

	@Override
	public String status(Goal.PutIn goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.PutIn goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}

	@Override
	public String taskSignature(Goal.PutIn goal) {
		return id() + ":" + goal.item() + ">" + goal.container() + ":" + Verb.PUT_IN.name();
	}

	/** The word may teach the item or the container. */
	@Override
	public List<Mention> mentions(Goal.PutIn goal) {
		return List.of(new Mention(ObjectKind.ITEM, goal.item()), new Mention(ObjectKind.BLOCK, goal.container()));
	}

	@Override
	public void validate(Goal.PutIn goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (ValidationContext.badId(goal.container())) {
			context.problem("bad block " + goal.container());
		}
		if (goal.count() < 1) {
			context.problem("count must be at least 1");
		}
	}

	/** The item: the sentence word unless that word is the container. */
	private static String item(Goal.PutIn goal, SentenceContext c) {
		boolean wordIsContainer = c.wordNames(ObjectKind.BLOCK, goal.container())
				&& !c.wordNames(ObjectKind.ITEM, goal.item());
		return wordIsContainer ? c.name(ObjectKind.ITEM, goal.item()) : c.obj();
	}

	private static String container(Goal.PutIn goal, SentenceContext c) {
		String name = c.name(ObjectKind.BLOCK, goal.container());
		return name.isEmpty() ? "상자" : name;
	}
}
