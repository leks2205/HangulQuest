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
import io.github.leks2205.hangulquest.sense.TradeFact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Buy from or sell to a villager: "주민에게 빵을 사요" / "주민에게 빵을 팔아요". The client sees the trade as a
 * click on the result slot of the merchant screen, with the item that arrived and the item that went into
 * the payment slots (the same snapshots as crafting), so a trade is as reliable as a craft. A buy counts
 * what arrived, a sell what was paid; the merchant is the one right-clicked just before the screen opened.
 */
final class TradeType implements GoalType<Goal.Trade> {
	static final String VILLAGER = "minecraft:villager";

	@Override
	public String id() {
		return "TRADE";
	}

	@Override
	public Class<Goal.Trade> goalClass() {
		return Goal.Trade.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"), Field.optionalResource("entity", "The merchant; a villager when left out."),
				Field.choice("direction", true, "BUY gets the item from the merchant, SELL pays with it.", "BUY", "SELL"),
				Field.count("count"));
	}

	@Override
	public Goal.Trade parse(JsonObject json) {
		String direction = JsonFields.string(json, "direction", "BUY").toUpperCase(Locale.ROOT);
		return new Goal.Trade(JsonFields.string(json, "item", ""), JsonFields.string(json, "entity", VILLAGER),
				direction.equals("SELL") ? "SELL" : "BUY", JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.Trade goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		json.addProperty("entity", goal.entity());
		json.addProperty("direction", goal.direction());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.Trade goal) {
		return goal.direction() + ":" + goal.item() + "@" + goal.entity() + "/" + goal.count();
	}

	private static boolean sell(Goal.Trade goal) {
		return "SELL".equals(goal.direction());
	}

	@Override
	public boolean evaluate(Goal.Trade goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		for (TradeFact fact : obs.traded) {
			if (fact.merchant() != null && !fact.merchant().equals(goal.entity())) {
				continue; // a wandering trader is no villager
			}
			if (sell(goal)) {
				if (fact.paid() != null && Matching.accepts(tracker, goal.item(), fact.paid())) {
					tracker.count += Math.max(1, fact.paidCount());
				}
			} else if (Matching.accepts(tracker, goal.item(), fact.got())) {
				tracker.count += Math.max(1, fact.gotCount());
			}
		}
		return tracker.count >= Math.max(1, goal.count());
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.Trade goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.Trade goal, VerbTable table) {
		return sell(goal) ? Verb.SELL : Verb.BUY;
	}

	@Override
	public String korean(Goal.Trade goal, SentenceContext c) {
		return SentenceBuilder.phrase(head(goal, c, c.withCounts()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Trade goal, String obj, Verb verb) {
		return SentenceBuilder.counted(KoGrammar.eul(obj), goal.count(), true, goal.item(), obj) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.Trade goal, SentenceContext c) {
		return head(goal, c, true) + " " + c.verb().past();
	}

	/** "주민에게 밀을 (세 개) 팔아요", "주민에게서 빵을 (세 개) 사요": the merchant, then the thing. A buyer gets it from the seller. */
	private static String head(Goal.Trade goal, SentenceContext c, boolean withCounts) {
		String merchant = c.name(ObjectKind.ENTITY, goal.entity());
		String who = merchant.isEmpty() ? "주민" : merchant;
		return (sell(goal) ? KoGrammar.ege(who) : KoGrammar.egeseo(who)) + " "
				+ SentenceBuilder.counted(KoGrammar.eul(c.obj()), goal.count(), withCounts, goal.item(), c.obj());
	}

	@Override
	public List<String> grammarIds(Goal.Trade goal, SentenceContext c) {
		List<String> ids = new ArrayList<>(GoalType.super.grammarIds(goal, c));
		ids.add(sell(goal) ? "L2.ege" : "L2.ege_source");
		return ids;
	}

	@Override
	public String status(Goal.Trade goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.Trade goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}

	@Override
	public List<Mention> mentions(Goal.Trade goal) {
		return List.of(new Mention(ObjectKind.ITEM, goal.item()), new Mention(ObjectKind.ENTITY, goal.entity()));
	}

	@Override
	public void validate(Goal.Trade goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (ValidationContext.badId(goal.entity())) {
			context.problem("bad entity " + goal.entity());
		}
		if (goal.count() < 1) {
			context.problem("count must be at least 1");
		}
	}
}
