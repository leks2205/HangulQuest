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
import io.github.leks2205.hangulquest.sense.GiveTracker;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hand an item to a creature or a person: "소에게 밀을 줘요", "주민 앞에 빵을 떨어뜨려요". A villager takes no item
 * by right-click: a gift to one counts when the villager picks the dropped item up (DROP_NEAR) or when it is paid
 * in a trade (TRADE); a wandering trader only takes a trade.
 */
final class GiveType implements GoalType<Goal.Give> {
	private static final String[] METHODS = Arrays.stream(GiveFact.Method.values()).map(Enum::name)
			.toArray(String[]::new);
	/** The way a hint tells how to give to a villager: the item is put down in front of it. Never written in a file. */
	static final String PLACE_NEAR = "PLACE_NEAR";
	private static final String TRADER = "minecraft:wandering_trader";
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
			boolean way = counts(goal, fact);
			boolean item = specific(goal) ? goal.item().equals(fact.item())
					: Matching.accepts(tracker, goal.item(), fact.item());
			if (way && goal.entity().equals(fact.targetType()) && item) {
				tracker.count += Math.max(1, fact.count());
			}
		}
		return tracker.count >= Math.max(1, goal.count());
	}

	/** Whether the way the item was given is one that counts for this goal and its recipient. */
	static boolean counts(Goal.Give goal, GiveFact fact) {
		String way = fact.method().name();
		if (goal.method() != null) {
			return goal.method().equals(way);
		}
		if (GiveTracker.PICKUP_TYPES.contains(goal.entity())) {
			return way.equals("DROP_NEAR") || way.equals("TRADE");
		}
		return !TRADER.equals(goal.entity()) || way.equals("TRADE");
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
		if (PLACE_NEAR.equals(goal.method())) {
			return Verb.PLACE;
		}
		Verb used = Tools.gift(goal.item(), goal.entity(), goal.method());
		if (used != null) {
			return used; // armour is put on (입히다), dye colours a sheep (염색하다)
		}
		return GiveFact.Method.DROP_NEAR.name().equals(goal.method()) ? Verb.DROP_AT : Verb.GIVE;
	}

	@Override
	public String korean(Goal.Give goal, SentenceContext c) {
		if (c.verb() == Verb.DRESS || c.verb() == Verb.DYE) {
			return SentenceBuilder.phrase(usedUp(goal, c), c.verb(), c.level(), c.finale());
		}
		String item = SentenceBuilder.counted(KoGrammar.eul(item(goal, c)), goal.count(), c.withCounts(), goal.item(),
				item(goal, c));
		String who = recipient(goal, c);
		String head = inFront(goal, c) ? who + " 앞에 " + item : KoGrammar.ege(who) + " " + item;
		return SentenceBuilder.phrase(head, c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Give goal, String obj, Verb verb) {
		return SentenceBuilder.counted(KoGrammar.eul(obj), goal.count(), true, goal.item(), obj) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.Give goal, SentenceContext c) {
		if (c.verb() == Verb.DRESS || c.verb() == Verb.DYE) {
			return usedUp(goal, c) + " " + c.verb().past();
		}
		String item = SentenceBuilder.counted(KoGrammar.eul(item(goal, c)), goal.count(), true, goal.item(), item(goal, c));
		String who = recipient(goal, c);
		String head = inFront(goal, c) ? who + " 앞에 " + item : KoGrammar.ege(who) + " " + item;
		return head + " " + c.verb().past();
	}

	/**
	 * Armour put on a creature, dye put on a sheep: "늑대에게 갑옷을 입히세요", "파란색 염료로 양을 염색하세요". The
	 * armour is the word the step teaches, else 갑옷 (the creature says which armour); the dye is the instrument.
	 */
	private static String usedUp(Goal.Give goal, SentenceContext c) {
		String who = recipient(goal, c);
		if (c.verb() == Verb.DYE) {
			return KoGrammar.euro(item(goal, c)) + " " + KoGrammar.eul(who);
		}
		String armour = c.wordNames(ObjectKind.ITEM, goal.item()) ? item(goal, c) : "갑옷";
		return KoGrammar.ege(who) + " " + KoGrammar.eul(armour);
	}

	@Override
	public List<String> grammarIds(Goal.Give goal, SentenceContext c) {
		List<String> ids = new ArrayList<>(GoalType.super.grammarIds(goal, c));
		if (c.verb() == Verb.DYE) {
			ids.add("L3.euro_instrument"); // 염료로
			return ids;
		}
		ids.add("L1.eul_reul");
		if (inFront(goal, c)) {
			ids.add("L1.loc.ap");
		}
		return ids;
	}

	/** The recipient marker decides who gets it: 소에게. A drop in front of someone has no marker. */
	@Override
	public List<String> decidingForms(Goal.Give goal) {
		Verb verb = verb(goal, VerbTable.shared());
		return verb == Verb.GIVE || verb == Verb.DRESS ? List.of("L2.ege") : List.of();
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
	public List<Mention> mentions(Goal.Give goal) {
		return List.of(new Mention(ObjectKind.ITEM, goal.item()), new Mention(ObjectKind.ENTITY, goal.entity()));
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
		boolean picks = GiveTracker.PICKUP_TYPES.contains(goal.entity());
		if ((picks || TRADER.equals(goal.entity())) && ("FEED".equals(goal.method()) || "USE_WITH".equals(goal.method()))) {
			context.problem("a " + goal.entity() + " takes no item by right-click: leave the method out, or use "
					+ (picks ? "DROP_NEAR or " : "") + "TRADE");
		}
		if (TRADER.equals(goal.entity()) && "DROP_NEAR".equals(goal.method())) {
			context.problem("a wandering trader does not pick up dropped items: use TRADE");
		}
	}

	/**
	 * The one flower an iron golem is about: a poppy. Any flower would be "꽃", but the golem's gift is that very one,
	 * so the sentence names it and the check wants it (the taught word does not widen it).
	 */
	static boolean specific(Goal.Give goal) {
		return "minecraft:iron_golem".equals(goal.entity()) && "minecraft:poppy".equals(goal.item());
	}

	/** The item is put down in front of the recipient (떨어뜨려요, 놓아요) instead of handed over (줘요). */
	private static boolean inFront(Goal.Give goal, SentenceContext c) {
		return c.verb() == Verb.DROP_AT || PLACE_NEAR.equals(goal.method());
	}

	/**
	 * The gift: the sentence word when it names the item, else the item's own name (its vanilla name or
	 * the word that has it as a referent). A word that is only drawn with the item never names it: a cat
	 * shown by a cod must not hand the cat a cat.
	 */
	private static String item(Goal.Give goal, SentenceContext c) {
		if (specific(goal)) {
			String own = c.namer().untaught().item(goal.item());
			if (own != null && !own.isBlank()) {
				return own;
			}
		}
		String name = c.name(ObjectKind.ITEM, goal.item());
		return name.isEmpty() ? c.obj() : name;
	}

	private static String recipient(Goal.Give goal, SentenceContext c) {
		String name = c.name(ObjectKind.ENTITY, goal.entity());
		return name.isEmpty() ? SOMEONE : name;
	}
}
