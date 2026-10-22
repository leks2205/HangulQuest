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
import io.github.leks2205.hangulquest.sense.CraftFact;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Take an item out of a furnace, blast furnace or smoker. Food and ceramics are baked (구워요), the
 * rest is made in the furnace ("화로에서 유리를 만들어요"); the -아/어서 chain with the input
 * ("모래를 녹여서 유리를 만들어요") starts at level 4.
 */
final class SmeltType implements GoalType<Goal.Smelt> {
	private static final Set<String> SCREENS = Set.of("furnace", "blast_furnace", "smoker");
	private static final String FURNACE = "minecraft:furnace";
	/** Where the -아/어서 form starts. */
	private static final int CHAIN_LEVEL = 4;

	@Override
	public String id() {
		return "SMELT";
	}

	@Override
	public Class<Goal.Smelt> goalClass() {
		return Goal.Smelt.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.optionalResource("input"), Field.resource("output"), Field.count("count"));
	}

	@Override
	public Goal.Smelt parse(JsonObject json) {
		String input = JsonFields.string(json, "input", null);
		return new Goal.Smelt(input == null || input.isBlank() ? null : input,
				JsonFields.string(json, "output", ""), JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.Smelt goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		if (goal.input() != null) {
			json.addProperty("input", goal.input());
		}
		json.addProperty("output", goal.output());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.Smelt goal) {
		return (goal.input() == null ? "" : goal.input() + ">") + goal.output() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.Smelt goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		for (CraftFact fact : obs.crafted) {
			if (SCREENS.contains(fact.station()) && Matching.accepts(tracker, goal.output(), fact.item())) {
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
	public int needed(Goal.Smelt goal) {
		return goal.count();
	}

	/**
	 * 구워요 for food, ceramics, glass and stone, 녹여요 for ingots, 만들어요 (in the furnace) for the rest.
	 * Glass, stone and ingots are only baked or melted when the goal says what goes in: "조약돌을 구워요" needs
	 * the cobblestone, without it the product is made.
	 */
	@Override
	public Verb verb(Goal.Smelt goal, VerbTable table) {
		Verb chosen = table.choose(id(), goal.output(), goal.count(), Verb.CRAFT);
		boolean needsInput = chosen == Verb.MELT
				|| chosen == Verb.BAKE && table.inFamily("kilned", goal.output()) && !table.inFamily("bakeables", goal.output());
		return needsInput && goal.input() == null ? Verb.CRAFT : chosen;
	}

	/**
	 * True when the sentence names the furnace and what goes into it: 화로에서 조약돌을 구워요, 철광석을 녹여요.
	 * A step that teaches the word of the product keeps saying the product (화로에서 돌을 만들어요), because the
	 * clue has to contain the word it teaches.
	 */
	private static boolean fromInput(Goal.Smelt goal, SentenceContext c, String input) {
		return !input.isEmpty() && !c.wordNames(ObjectKind.ITEM, goal.output()) && kilnVerb(goal, c);
	}

	/** Baking stone or glass, melting an ore: the verbs that name what goes into the furnace. */
	private static boolean kilnVerb(Goal.Smelt goal, SentenceContext c) {
		return c.verb() == Verb.MELT || c.verb() == Verb.BAKE
				&& !VerbTable.shared().inFamily("bakeables", goal.output());
	}

	/** The product is named and made in the furnace: the step teaches its word, or the goal has no input. */
	private static boolean makesProduct(Goal.Smelt goal, SentenceContext c) {
		return kilnVerb(goal, c) && !fromInput(goal, c, inputName(goal, c));
	}

	/** A food can also be cooked (요리해요) when the step teaches 요리하다; the baking stays the default. */
	@Override
	public List<Verb> alternativeVerbs(Goal.Smelt goal) {
		return food(goal.output()) ? List.of(Verb.COOK) : List.of();
	}

	/** Cooked meat and fish, baked potatoes and dried kelp: what a furnace makes to be eaten. */
	static boolean food(String output) {
		String path = output == null ? "" : output.substring(output.indexOf(':') + 1);
		return path.startsWith("cooked_") || path.equals("baked_potato") || path.equals("dried_kelp");
	}

	@Override
	public String korean(Goal.Smelt goal, SentenceContext c) {
		String input = inputName(goal, c);
		String made = counted(goal, c, outputName(goal, c), c.withCounts());
		if (c.verb() == Verb.COOK && (c.level() < CHAIN_LEVEL || input.isEmpty())) {
			return SentenceBuilder.phrase(made, c.verb(), c.level(), c.finale()); // 닭고기를 요리해요
		}
		if (c.level() >= CHAIN_LEVEL && !input.isEmpty()) {
			// sand is melted into glass, a cobblestone baked into stone (the plain sentence says 구워요 for both)
			boolean glass = "minecraft:glass".equals(goal.output());
			Verb how = (c.verb() == Verb.BAKE || c.verb() == Verb.COOK) && !glass ? c.verb() : Verb.MELT;
			return SentenceBuilder.phrase(KoGrammar.eul(input) + " " + how.eoyo() + "서 " + made, Verb.CRAFT,
					c.level(), c.finale());
		}
		if (fromInput(goal, c, input)) {
			return SentenceBuilder.phrase(KoGrammar.eseo(furnace(c)) + " " + counted(goal, c, input, c.withCounts()),
					c.verb(), c.level(), c.finale());
		}
		if (makesProduct(goal, c)) {
			return SentenceBuilder.phrase(KoGrammar.eseo(furnace(c)) + " " + made, Verb.CRAFT, c.level(), c.finale());
		}
		if (c.verb() == Verb.BAKE) {
			// Food is baked from what it was ("익히지 않은 소고기를 구워요"); without an input the product is named.
			String what = input.isEmpty() ? made : counted(goal, c, input, c.withCounts());
			return SentenceBuilder.phrase(what, c.verb(), c.level(), c.finale());
		}
		return SentenceBuilder.phrase(KoGrammar.eseo(furnace(c)) + " " + made, c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Smelt goal, String obj, Verb verb) {
		return SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), true, goal.output(), obj)
				+ " " + verb.past();
	}

	@Override
	public String ceremony(Goal.Smelt goal, SentenceContext c) {
		if (fromInput(goal, c, inputName(goal, c))) {
			return KoGrammar.eseo(furnace(c)) + " " + counted(goal, c, inputName(goal, c), true) + " "
					+ c.verb().past();
		}
		if (makesProduct(goal, c)) {
			return KoGrammar.eseo(furnace(c)) + " " + counted(goal, c, outputName(goal, c), true) + " "
					+ Verb.CRAFT.past();
		}
		if (c.verb() == Verb.COOK) {
			return counted(goal, c, outputName(goal, c), true) + " " + c.verb().past();
		}
		if (c.verb() == Verb.BAKE) {
			String input = inputName(goal, c);
			return counted(goal, c, input.isEmpty() ? outputName(goal, c) : input, true) + " " + c.verb().past();
		}
		return KoGrammar.eseo(furnace(c)) + " " + counted(goal, c, outputName(goal, c), true) + " "
				+ c.verb().past();
	}

	@Override
	public List<String> grammarIds(Goal.Smelt goal, SentenceContext c) {
		List<String> ids = new ArrayList<>(GoalType.super.grammarIds(goal, c));
		if (c.level() >= CHAIN_LEVEL && !inputName(goal, c).isEmpty()) {
			ids.add("L4.aseo");
		} else if (kilnVerb(goal, c) || c.verb() != Verb.BAKE && c.verb() != Verb.COOK) {
			ids.add("L2.eseo_activity");
		}
		return ids;
	}

	@Override
	public String status(Goal.Smelt goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.Smelt goal) {
		return goal.output();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}

	@Override
	public List<Mention> mentions(Goal.Smelt goal) {
		return goal.input() == null ? List.of(new Mention(ObjectKind.ITEM, goal.output()))
				: List.of(new Mention(ObjectKind.ITEM, goal.output()), new Mention(ObjectKind.ITEM, goal.input()));
	}

	@Override
	public void validate(Goal.Smelt goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.input() != null && ValidationContext.badId(goal.input())) {
			context.problem("bad item " + goal.input());
		}
		if (goal.count() < 1) {
			context.problem("count must be at least 1");
		}
	}

	/** "유리를", "스테이크를 세 개": the noun with its marker and the count. */
	private static String counted(Goal.Smelt goal, SentenceContext c, String name, boolean withCount) {
		String id = name.equals(inputName(goal, c)) ? goal.input() : goal.output();
		return SentenceBuilder.counted(KoGrammar.eul(name), goal.count(), withCount, id, name);
	}

	/** The product: the sentence word, unless that word names the input. */
	private static String outputName(Goal.Smelt goal, SentenceContext c) {
		boolean wordIsInput = c.wordNames(ObjectKind.ITEM, goal.input()) && !c.wordNames(ObjectKind.ITEM, goal.output());
		return wordIsInput ? c.name(ObjectKind.ITEM, goal.output()) : c.obj();
	}

	private static String inputName(Goal.Smelt goal, SentenceContext c) {
		return goal.input() == null ? "" : c.name(ObjectKind.ITEM, goal.input());
	}

	private static String furnace(SentenceContext c) {
		String name = c.name(ObjectKind.BLOCK, FURNACE);
		return name.isEmpty() ? "화로" : name;
	}
}
