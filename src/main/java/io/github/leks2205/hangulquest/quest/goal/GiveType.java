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
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Hand an item to a creature or a person: "소에게 밀을 줘요", "주민 앞에 빵을 떨어뜨려요". */
final class GiveType implements GoalType<Goal.Give> {
	private static final String[] METHODS = Arrays.stream(GiveFact.Method.values()).map(Enum::name)
			.toArray(String[]::new);
	/** The "someone" in a sentence whose recipient has no name. */
	private static final String SOMEONE = "누군가";

	@Override
	public String id() {
		return "GIVE";
	}

	@Override
	public Class<Goal.Give> goalClass() {
		return Goal.Give.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"), Field.resource("entity"),
				Field.choice("method", false, "Only this way of giving counts; DROP_NEAR is spoken as 앞에 … 떨어뜨려요.",
						METHODS),
				Field.integer("count", 1, 64, false));
	}

	@Override
	public Goal.Give parse(JsonObject json) {
		String method = JsonFields.string(json, "method", null);
		return new Goal.Give(JsonFields.string(json, "item", ""), JsonFields.string(json, "entity", ""),
				method == null || method.isBlank() ? null : method.toUpperCase(java.util.Locale.ROOT),
				JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.Give goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		json.addProperty("entity", goal.entity());
		if (goal.method() != null) {
			json.addProperty("method", goal.method());
		}
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.Give goal) {
		return goal.item() + ">" + goal.entity() + "/" + goal.count()
				+ (goal.method() == null ? "" : "@" + goal.method());
	}

	@Override
	public boolean evaluate(Goal.Give goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		for (GiveFact fact : obs.gave) {
			boolean way = goal.method() == null || goal.method().equals(fact.method().name());
			if (way && goal.entity().equals(fact.targetType()) && Matching.matchesBlock(goal.item(), fact.item())) {
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
	public int needed(Goal.Give goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.Give goal, VerbTable table) {
		return GiveFact.Method.DROP_NEAR.name().equals(goal.method()) ? Verb.DROP_AT : Verb.GIVE;
	}

	@Override
	public String korean(Goal.Give goal, SentenceContext c) {
		String item = SentenceBuilder.counted(KoGrammar.eul(item(goal, c)), goal.count(), c.withCounts());
		String who = recipient(goal, c);
		String head = c.verb() == Verb.DROP_AT ? who + " 앞에 " + item : KoGrammar.ege(who) + " " + item;
		return SentenceBuilder.phrase(head, c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Give goal, String obj, Verb verb) {
		return SentenceBuilder.counted(KoGrammar.eul(obj), goal.count(), true) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.Give goal, SentenceContext c) {
		String item = SentenceBuilder.counted(KoGrammar.eul(item(goal, c)), goal.count(), true);
		String who = recipient(goal, c);
		String head = c.verb() == Verb.DROP_AT ? who + " 앞에 " + item : KoGrammar.ege(who) + " " + item;
		return head + " " + c.verb().past();
	}

	@Override
	public List<String> grammarIds(Goal.Give goal, SentenceContext c) {
		List<String> ids = new ArrayList<>(GoalType.super.grammarIds(goal, c));
		ids.add("L1.eul_reul");
		if (c.verb() == Verb.DROP_AT) {
			ids.add("L1.loc.ap");
		}
		return ids;
	}

	@Override
	public String status(Goal.Give goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.Give goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}

	@Override
	public String taskSignature(Goal.Give goal) {
		return id() + ":" + goal.item() + ">" + goal.entity() + ":" + verb(goal, VerbTable.shared()).name();
	}

	/** The word may teach the item or the recipient. */
	@Override
	public boolean covers(Goal.Give goal, Concept concept) {
		return GoalType.super.covers(goal, concept) || goal.entity().equals(concept.entity);
	}

	@Override
	public void validate(Goal.Give goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (ValidationContext.badId(goal.entity())) {
			context.problem("bad entity " + goal.entity());
		}
		if (goal.method() != null && Arrays.stream(METHODS).noneMatch(goal.method()::equals)) {
			context.problem("unknown give method " + goal.method());
		}
	}

	/** The item: the sentence word unless that word is the recipient. */
	private static String item(Goal.Give goal, SentenceContext c) {
		boolean wordIsRecipient = c.wordNames(ObjectKind.ENTITY, goal.entity())
				&& !c.wordNames(ObjectKind.ITEM, goal.item());
		return wordIsRecipient ? c.name(ObjectKind.ITEM, goal.item()) : c.obj();
	}

	private static String recipient(Goal.Give goal, SentenceContext c) {
		String name = c.name(ObjectKind.ENTITY, goal.entity());
		return name.isEmpty() ? SOMEONE : name;
	}
}
