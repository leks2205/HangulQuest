package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonArray;
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
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.sense.CraftFact;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Make an item at a crafting screen: take it out of the result slot. */
final class CraftType implements GoalType<Goal.Craft> {
	/** The screens where taking the result is crafting (Station ids), and the block that names each. */
	private static final Map<String, String> STATIONS = Map.of(
			"inventory2x2", "",
			"crafting_table", "minecraft:crafting_table",
			"stonecutter", "minecraft:stonecutter",
			"smithing", "minecraft:smithing_table",
			"loom", "minecraft:loom",
			"cartography", "minecraft:cartography_table",
			"grindstone", "minecraft:grindstone");

	@Override
	public String id() {
		return "CRAFT";
	}

	@Override
	public Class<Goal.Craft> goalClass() {
		return Goal.Craft.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"), Field.count("count"),
				Field.choice("station", false, "Where it is made; any crafting screen when left out.",
						STATIONS.keySet().stream().sorted().toArray(String[]::new)),
				Field.resourceList("with", "Ingredients named in the sentence (와/과 and (으)로). Only the wording, not checked."));
	}

	@Override
	public Goal.Craft parse(JsonObject json) {
		String station = JsonFields.string(json, "station", null);
		return new Goal.Craft(JsonFields.string(json, "item", ""), JsonFields.integer(json, "count", 1),
				station == null || station.isBlank() ? null : station, JsonFields.strings(json, "with"));
	}

	@Override
	public JsonObject toJson(Goal.Craft goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		json.addProperty("count", goal.count());
		if (goal.station() != null) {
			json.addProperty("station", goal.station());
		}
		if (!goal.with().isEmpty()) {
			JsonArray with = new JsonArray();
			goal.with().forEach(with::add);
			json.add("with", with);
		}
		return json;
	}

	@Override
	public String keyPart(Goal.Craft goal) {
		return goal.item() + "/" + goal.count() + (goal.station() == null ? "" : "@" + goal.station());
	}

	@Override
	public boolean evaluate(Goal.Craft goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		for (CraftFact fact : obs.crafted) {
			if (!Matching.matchesBlock(goal.item(), fact.item())) {
				continue;
			}
			boolean rightScreen = goal.station() == null
					? STATIONS.containsKey(fact.station()) : goal.station().equals(fact.station());
			if (rightScreen) {
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
	public int needed(Goal.Craft goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.Craft goal, VerbTable table) {
		return table.choose(id(), goal.item(), goal.count(), Verb.CRAFT);
	}

	@Override
	public String korean(Goal.Craft goal, SentenceContext c) {
		String made = SentenceBuilder.counted(SentenceBuilder.objectPhrase(c.obj(), c.verb()), goal.count(), c.withCounts(), goal.item(), c.obj());
		String gathered = gathered(goal, c);
		String head = gathered.isEmpty() ? stationPhrase(goal, c) + materials(goal.with(), c) + made
				: gathered + stationPhrase(goal, c) + made;
		return SentenceBuilder.phrase(head, c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Craft goal, String obj, Verb verb) {
		return SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), true, goal.item(), obj)
				+ " " + verb.past();
	}

	@Override
	public List<String> grammarIds(Goal.Craft goal, SentenceContext c) {
		List<String> ids = new ArrayList<>(GoalType.super.grammarIds(goal, c));
		if (!stationPhrase(goal, c).isEmpty()) {
			ids.add("L2.eseo_activity");
		}
		if (!gathered(goal, c).isEmpty()) {
			if (goal.with().stream().distinct().count() > 1) {
				ids.add("L2.wa_gwa");
			}
			ids.add(Clauses.Link.AND.grammar());
		} else if (!materials(goal.with(), c).isEmpty()) {
			if (goal.with().stream().distinct().count() > 1) {
				ids.add("L2.wa_gwa");
			}
			ids.add("L2.euro_material");
		}
		return ids;
	}

	@Override
	public String status(Goal.Craft goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.Craft goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}

	/** The word may teach the product, an ingredient or the station. */
	@Override
	public List<Mention> mentions(Goal.Craft goal) {
		List<Mention> all = new ArrayList<>(List.of(new Mention(ObjectKind.ITEM, goal.item())));
		for (String ingredient : goal.with()) {
			all.add(new Mention(ObjectKind.ITEM, ingredient));
		}
		String station = goal.station() == null ? null : STATIONS.get(goal.station());
		if (station != null) {
			all.add(new Mention(ObjectKind.BLOCK, station));
		}
		return all;
	}

	@Override
	public void validate(Goal.Craft goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.station() != null && !STATIONS.containsKey(goal.station())) {
			context.problem("unknown crafting station " + goal.station());
		}
		for (String ingredient : goal.with()) {
			if (ValidationContext.badId(ingredient)) {
				context.problem("bad ingredient " + ingredient);
			}
		}
		if (goal.count() < 1) {
			context.problem("count must be at least 1");
		}
	}

	/** "작업대에서 " for the screen that makes it, or "". */
	private static String stationPhrase(Goal.Craft goal, SentenceContext c) {
		String block = goal.station() == null ? "" : STATIONS.getOrDefault(goal.station(), "");
		if (block.isEmpty()) {
			return "";
		}
		String name = c.name(ObjectKind.BLOCK, block);
		return name.isEmpty() ? "" : KoGrammar.eseo(name) + " ";
	}

	/**
	 * At a level that joins clauses (ladder.json {@code connectives}) the ingredients are gathered first:
	 * "막대기와 석탄을 모으고 ". "" at any other level, or when there is nothing to name.
	 */
	private static String gathered(Goal.Craft goal, SentenceContext c) {
		if (!Ladder.shared().rung(c.level()).connectives() || goal.with().isEmpty()) {
			return "";
		}
		List<String> names = ingredientNames(goal.with(), c);
		if (names.isEmpty()) {
			return "";
		}
		String last = names.get(names.size() - 1);
		String front = names.size() == 1 ? KoGrammar.eul(last) : joinWithAnd(names, KoGrammar.eul(last));
		return front + " " + Verb.COLLECT.stem() + "고 ";
	}

	/** "막대기와 석탄으로 ", "나무 판자, 막대기와 철로 ", or "" when there are none or one has no name. */
	static String materials(List<String> ids, SentenceContext c) {
		List<String> names = ingredientNames(ids, c);
		if (names.isEmpty()) {
			return "";
		}
		if (names.size() == 1) {
			return KoGrammar.euro(names.get(0)) + " ";
		}
		return joinWithAnd(names, KoGrammar.euro(names.get(names.size() - 1))) + " ";
	}

	/** "A, B와 C를": the names in front of the last one, then 와/과 on the one before it and the last one marked. */
	private static String joinWithAnd(List<String> names, String markedLast) {
		String beforeLast = names.get(names.size() - 2);
		String front = String.join(", ", names.subList(0, names.size() - 2));
		return (front.isEmpty() ? "" : front + ", ") + KoGrammar.waGwa(beforeLast) + " " + markedLast;
	}

	/** The distinct names of the ingredients; empty when there are none or one has no name. */
	private static List<String> ingredientNames(List<String> ids, SentenceContext c) {
		List<String> names = new ArrayList<>();
		for (String id : ids) {
			String name = c.name(ObjectKind.ITEM, id);
			if (name.isEmpty()) {
				return List.of();
			}
			if (!names.contains(name)) {
				names.add(name);
			}
		}
		return names;
	}
}
