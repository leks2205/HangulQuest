package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Several goals done one after the other: "침대에서 자기 전에 빵을 먹어요", "빵을 먹은 후에 자요",
 * "나무를 베고 나서 판자를 만들어요". An event that comes before its turn does not count. With
 * {@code ordered} false the parts may come in any order ("문을 닫고 횃불을 놓아요"). Level 3 and up, except the
 * plain -고 (level 2): parts in any order, or in order with the connector {@code AND} ("문을 닫고 횃불을 놓으세요").
 */
final class SequenceType implements GoalType<Goal.Sequence> {
	static final int MIN_LEVEL = 3;
	/** The level of the plain -고, which is a level-2 construction (L2.go_sequence). */
	static final int MIN_LEVEL_AND = 2;
	/** The level of -아/어서 and -(으)러 가다. */
	static final int MIN_LEVEL_EOSEO = 4;
	private static final String[] CONNECTORS = {"BEFORE", "AFTER", "THEN", "AND", "EOSEO", "EUREO"};
	/** What an ordered sequence without a connector picks from (by its parts); -고 is only ever written. */
	private static final String[] DEFAULT_CONNECTORS = {"BEFORE", "AFTER", "THEN"};

	@Override
	public String id() {
		return "SEQUENCE";
	}

	@Override
	public Class<Goal.Sequence> goalClass() {
		return Goal.Sequence.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.goalList("parts", 2, 3, "The goals, in the order they have to happen."),
				Field.flag("ordered", "False lets the parts happen in any order (default true)."),
				Field.choice("connector", false, "Which Korean connective ties the two parts of an ordered "
						+ "sequence; chosen from the parts when left out. AND is the plain -고 (level 2); EOSEO is -아/어서 "
						+ "and EUREO -(으)러 가다 (level 4, never chosen by itself); the order is checked all the same.",
						CONNECTORS));
	}

	@Override
	public Goal.Sequence parse(JsonObject json) {
		List<Goal> parts = new ArrayList<>();
		if (json.has("parts") && json.get("parts").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("parts")) {
				Goal part = element.isJsonObject() ? GoalTypes.parse(element.getAsJsonObject()) : null;
				if (part != null) {
					parts.add(part);
				}
			}
		}
		boolean ordered = !json.has("ordered") || !json.get("ordered").isJsonPrimitive()
				|| json.get("ordered").getAsBoolean();
		String connector = JsonFields.string(json, "connector", null);
		return new Goal.Sequence(parts, ordered,
				connector == null || connector.isBlank() ? null : connector.toUpperCase(Locale.ROOT));
	}

	@Override
	public JsonObject toJson(Goal.Sequence goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		JsonArray parts = new JsonArray();
		goal.parts().forEach(part -> parts.add(GoalTypes.toJson(part)));
		json.add("parts", parts);
		json.addProperty("ordered", goal.ordered());
		if (goal.connector() != null) {
			json.addProperty("connector", goal.connector());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.Sequence goal) {
		return (goal.ordered() ? "ordered:" : "any:")
				+ goal.parts().stream().map(GoalEvaluator::key).collect(Collectors.joining("|"));
	}

	@Override
	public boolean evaluate(Goal.Sequence goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		List<Goal> parts = goal.parts();
		if (parts.isEmpty()) {
			return false;
		}
		if (goal.ordered()) {
			// tracker.count is the part waiting for its turn; the later parts see nothing yet.
			if (tracker.count < parts.size()) {
				GoalEvaluator.Tracker sub = Clauses.sub(tracker, tracker.count, parts.size());
				boolean done = GoalEvaluator.tick(parts.get(tracker.count), List.of(), obs, sub);
				tracker.assisted |= sub.assisted;
				if (!done) {
					tracker.formErrors += outOfOrder(parts, tracker.count, obs, tracker);
				}
				if (done) {
					tracker.count++;
				}
			}
			return tracker.count >= parts.size();
		}
		if (tracker.doneMask == 0 && tracker.count > 0) {
			tracker.doneMask = (1L << Math.min(tracker.count, 62)) - 1; // progress restored from a save
		}
		for (int i = 0; i < parts.size(); i++) {
			if ((tracker.doneMask & (1L << i)) != 0) {
				continue;
			}
			GoalEvaluator.Tracker sub = Clauses.sub(tracker, i, parts.size());
			if (GoalEvaluator.tick(parts.get(i), List.of(), obs, sub)) {
				tracker.doneMask |= 1L << i;
			}
			tracker.assisted |= sub.assisted;
		}
		tracker.count = Long.bitCount(tracker.doneMask);
		return tracker.count >= parts.size();
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.Sequence goal) {
		return goal.parts().size();
	}

	/** The verb of the part that ends the sentence. */
	@Override
	public Verb verb(Goal.Sequence goal, VerbTable table) {
		if (goal.parts().isEmpty()) {
			return Verb.USE;
		}
		Goal last = goal.parts().get(finiteIndex(goal));
		return GoalTypes.of(last).verb(last, table);
	}

	@Override
	public String korean(Goal.Sequence goal, SentenceContext c) {
		if (goal.parts().size() < 2) {
			return "";
		}
		return joined(goal, c, false);
	}

	@Override
	public String ceremony(Goal.Sequence goal, String obj, Verb verb) {
		return "";
	}

	@Override
	public String ceremony(Goal.Sequence goal, SentenceContext c) {
		return goal.parts().size() < 2 ? "" : joined(goal, c, true);
	}

	@Override
	public List<String> grammarIds(Goal.Sequence goal, SentenceContext c) {
		List<String> ids = new ArrayList<>();
		if (goal.parts().size() < 2) {
			return ids;
		}
		ids.add(link(goal).grammar());
		for (int i = 0; i < goal.parts().size(); i++) {
			Goal part = goal.parts().get(i);
			List<String> partIds = GoalTypes.of(part).grammarIds(part, Clauses.contextFor(part, c, c.level()));
			// Only the part that carries the verb has the ending of the level.
			ids.addAll(i == finiteIndex(goal) ? partIds : partIds.subList(1, partIds.size()));
		}
		return ids;
	}

	/**
	 * The connective decides the order; parts in any order have no deciding form, and neither has a plain -고 that
	 * does not check the order.
	 */
	@Override
	public List<String> decidingForms(Goal.Sequence goal) {
		return goal.ordered() && goal.parts().size() > 1 ? List.of(link(goal).grammar()) : List.of();
	}

	@Override
	public String status(Goal.Sequence goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.parts().size());
	}

	@Override
	public String object(Goal.Sequence goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}

	/** Type and parts in order (or sorted, when any order is fine): the same words in another order are another task. */
	@Override
	public String taskSignature(Goal.Sequence goal) {
		List<String> signatures = new ArrayList<>();
		for (Goal part : goal.parts()) {
			signatures.add(GoalTypes.of(part).taskSignature(part));
		}
		if (!goal.ordered()) {
			signatures.sort(null);
		}
		return id() + ":" + String.join(goal.ordered() ? ">" : "&", signatures);
	}

	@Override
	public List<Mention> mentions(Goal.Sequence goal) {
		List<Mention> all = new ArrayList<>();
		for (Goal part : goal.parts()) {
			all.addAll(GoalTypes.of(part).mentions(part));
		}
		return all;
	}

	@Override
	public boolean covers(Goal.Sequence goal, Concept concept) {
		return goal.parts().stream().anyMatch(part -> GoalTypes.of(part).covers(part, concept));
	}

	@Override
	public void validate(Goal.Sequence goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		boolean plainGo = !goal.ordered() || "AND".equals(goal.connector());
		boolean level4 = goal.ordered() && ("EOSEO".equals(goal.connector()) || "EUREO".equals(goal.connector()));
		int minLevel = plainGo ? MIN_LEVEL_AND : level4 ? MIN_LEVEL_EOSEO : MIN_LEVEL;
		if (context.koreanLevel() > 0 && context.koreanLevel() < minLevel) {
			context.problem("SEQUENCE needs koreanLevel " + minLevel + " or higher ("
					+ (plainGo ? "-고" : level4 ? "-아/어서, -(으)러 가다"
					: "-기 전에, -(으)ㄴ 후에, -고 나서; at level 2 only the connector AND, -고") + ")");
		}
		if (!goal.ordered() && ("EOSEO".equals(goal.connector()) || "EUREO".equals(goal.connector()))) {
			context.problem("the connector " + goal.connector() + " needs an ordered sequence");
		}
		int size = goal.parts().size();
		if (size < 2 || size > (goal.ordered() ? 2 : 3)) {
			context.problem("SEQUENCE needs " + (goal.ordered() ? "exactly 2 parts" : "2 or 3 parts") + ", has " + size);
		}
		if (goal.connector() != null && !List.of(CONNECTORS).contains(goal.connector())) {
			context.problem("unknown SEQUENCE connector " + goal.connector());
		}
		for (int i = 0; i < size; i++) {
			Goal part = goal.parts().get(i);
			if (Clauses.usable(part)) {
				Clauses.validatePart(part, context, "parts[" + i + "]");
			} else {
				context.problem("parts[" + i + "] cannot be spoken as a clause (use a simple goal)");
			}
		}
		if (goal.ordered() && size == 2) {
			checkLevelFour(goal, context);
		}
		Clauses.checkClueDoesNotTeach(context, false);
	}

	/**
	 * -아/어서 ties two acts of the same player, one after the other: both must be single events, so the order can be
	 * checked. -(으)러 가다 is a trip and what is done there: the trip comes first, goes to a place the verb 가다 or
	 * 들어가다 can name, and the step must hold IN_BIOME for that place, since the act is done there.
	 */
	private static void checkLevelFour(Goal.Sequence goal, ValidationContext context) {
		List<Goal> parts = goal.parts();
		if ("EOSEO".equals(goal.connector())) {
			for (Goal part : parts) {
				if (!isEvent(part)) {
					context.problem("-아/어서 needs two acts that happen at one moment each, not " + part.getClass().getSimpleName());
				}
			}
		}
		if (!"EUREO".equals(goal.connector())) {
			return;
		}
		if (!(parts.get(0) instanceof Goal.ReachBiome trip)) {
			context.problem("-(으)러 가다 starts with the trip: the first part must be REACH_BIOME");
			return;
		}
		Verb way = GoalTypes.of(trip).verb(trip, VerbTable.shared());
		if (way != Verb.GO && way != Verb.ENTER) {
			context.problem("-(으)러 가다 needs a trip that 가다 or 들어가다 names, not " + way.dictionary());
		}
		if (!isEvent(parts.get(1))) {
			context.problem("-(으)러 가다 needs one act to do there, not " + parts.get(1).getClass().getSimpleName());
		}
		boolean there = context.step() != null && context.step().conditions().stream()
				.anyMatch(condition -> condition instanceof Condition.InBiome in && in.biome().equals(trip.biome()));
		if (!there) {
			context.problem("-(으)러 가다 says the act is done there: add the condition IN_BIOME " + trip.biome());
		}
	}

	/** The sentence of a trip "to do it" already says the place: IN_BIOME for the biome of the trip is not spoken twice. */
	@Override
	public boolean says(Goal.Sequence goal, Condition condition) {
		return goal.ordered() && "EUREO".equals(goal.connector()) && !goal.parts().isEmpty()
				&& goal.parts().get(0) instanceof Goal.ReachBiome trip && condition instanceof Condition.InBiome in
				&& in.biome().equals(trip.biome());
	}

	/** Goals that one event completes, so a later part can be checked on a fresh tracker each tick. */
	private static boolean isEvent(Goal part) {
		return Clauses.isEvent(part);
	}

	/**
	 * Whether a part whose turn has not come happened this tick: the player did the later thing first. It
	 * never counts toward the goal; the engine tells the learning side the order was not followed. A part that
	 * is the same goal as an earlier one is left out, since one event cannot be told apart.
	 */
	private static int outOfOrder(List<Goal> parts, int turn, Observation obs, GoalEvaluator.Tracker tracker) {
		int early = 0;
		for (int later = turn + 1; later < parts.size(); later++) {
			Goal part = parts.get(later);
			if (!isEvent(part) || parts.subList(0, later).contains(part)) {
				continue;
			}
			GoalEvaluator.Tracker probe = new GoalEvaluator.Tracker();
			probe.accepted = tracker.accepted;
			if (GoalEvaluator.tick(part, List.of(), obs, probe)) {
				early++;
			}
		}
		return early;
	}

	/** Which of the two parts is spoken last, with the finite verb. */
	private static int finiteIndex(Goal.Sequence goal) {
		int last = goal.parts().size() - 1;
		return goal.ordered() && ("BEFORE".equals(effectiveConnector(goal)) || "EUREO".equals(goal.connector())) ? 0 : last;
	}

	private static String effectiveConnector(Goal.Sequence goal) {
		if (goal.connector() != null) {
			return goal.connector();
		}
		return DEFAULT_CONNECTORS[Math.floorMod(goal.parts().stream().map(GoalEvaluator::key)
				.collect(Collectors.joining("|")).hashCode(), DEFAULT_CONNECTORS.length)];
	}

	private static Clauses.Link link(Goal.Sequence goal) {
		if (!goal.ordered()) {
			return Clauses.Link.AND;
		}
		return switch (effectiveConnector(goal)) {
			case "AND" -> Clauses.Link.AND;
			case "BEFORE" -> Clauses.Link.BEFORE;
			case "AFTER" -> Clauses.Link.AFTER;
			case "EOSEO" -> Clauses.Link.EOSEO;
			case "EUREO" -> Clauses.Link.EUREO;
			default -> Clauses.Link.THEN;
		};
	}

	/**
	 * "A 기 전에 B" says B first and A second, so BEFORE puts the later part in the clause. The other links
	 * put the earlier part in the clause. The last part of an unordered sequence carries the verb.
	 */
	private static String joined(Goal.Sequence goal, SentenceContext c, boolean past) {
		List<Goal> parts = goal.parts();
		c = withoutRenamingPicture(parts, c);
		Clauses.Link link = link(goal);
		int finite = finiteIndex(goal);
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i == finite) {
				continue;
			}
			String clause = Clauses.clause(parts.get(i), c, link);
			if (clause == null) {
				return "";
			}
			text.append(clause).append(' ');
		}
		Goal main = parts.get(finite);
		if (parts.size() == 2) {
			dropRepeatedDwell(parts.get(1 - finite), main, text);
		}
		String last = past ? tripCeremony(link, main, c) : Clauses.sentence(main, c, c.level());
		if (parts.size() == 2) {
			last = withoutRepeatedObject(parts.get(1 - finite), main, text.toString(), last);
			last = withoutRepeatedContainer(parts.get(1 - finite), main, text.toString(), last);
		}
		return text + last;
	}

	/** The past of the main part; the trip of a -(으)러 clause is a movement ("숲으로 갔어요"), never "숲에 도착했어요". */
	private static String tripCeremony(Clauses.Link link, Goal main, SentenceContext c) {
		if (link != Clauses.Link.EUREO) {
			return Clauses.ceremony(main, c);
		}
		SentenceContext part = Clauses.contextFor(main, c, 1);
		return KoGrammar.euro(part.obj()) + " " + part.verb().past();
	}

	/**
	 * Two parts on the same thing say it once: "가죽 장화를 만들고 신으세요", not "가죽 장화를 만들고 가죽 장화를 신으세요".
	 * Only when both parts point at the same id, both are the bare "object + verb" (no count, no second noun: "상자를
	 * 만들고 넣으세요" would not say what goes in), and the second sentence opens with the very phrase the first one does.
	 */
	private static String withoutRepeatedObject(Goal clausePart, Goal main, String clause, String last) {
		String object = GoalTypes.of(main).object(main);
		if (object == null || !object.equals(GoalTypes.of(clausePart).object(clausePart))) {
			return last;
		}
		String objectPhrase = openingObject(clause);
		if (objectPhrase == null || !last.startsWith(objectPhrase)) {
			return last;
		}
		String clauseRest = clause.substring(objectPhrase.length()).strip();
		String lastRest = last.substring(objectPhrase.length());
		// what is left of each sentence is the verb alone (with its connective: 만든 후에, 쓰기 전에, 놓고 나서); a
		// count or a second noun stays in the sentence
		return clauseRest.isEmpty() || !verbAlone(clauseRest) || lastRest.isBlank() || lastRest.contains(" ") ? last
				: lastRest;
	}

	/**
	 * Putting something in a container and taking something out of the same one says the container once: "훈연기 안에
	 * 감자를 넣어서 구운 감자를 꺼내야 해요", not "… 훈연기에서 구운 감자를 꺼내야 해요". The container is the one the
	 * clause opens with ("훈연기 안에 …").
	 */
	private static String withoutRepeatedContainer(Goal clausePart, Goal main, String clause, String last) {
		if (!(clausePart instanceof Goal.PutIn in) || !(main instanceof Goal.TakeOut out)
				|| !in.container().equals(out.container())) {
			return last;
		}
		int inside = clause.indexOf(" 안에 ");
		if (inside <= 0) {
			return last;
		}
		String from = KoGrammar.eseo(clause.substring(0, inside)) + " ";
		return last.startsWith(from) ? last.substring(from.length()) : last;
	}

	/** One verb, with the 전에 / 후에 / 나서 of its connective. */
	private static boolean verbAlone(String rest) {
		String[] words = rest.split(" ");
		return words.length == 1 || words.length == 2 && List.of("전에", "후에", "나서").contains(words[1]);
	}

	/**
	 * Two looks of the same length say the seconds once: "금 블록을 바라보기 전에 피글린을 일 초 동안 바라보세요", not
	 * "금 블록을 일 초 동안 바라보기 전에 피글린을 일 초 동안 …".
	 */
	private static void dropRepeatedDwell(Goal clausePart, Goal main, StringBuilder clause) {
		long dwell = dwellOf(main);
		if (dwell > 0 && dwell == dwellOf(clausePart)) {
			String phrase = " " + SentenceBuilder.secondsPhrase(dwell);
			int at = clause.indexOf(phrase);
			if (at >= 0) {
				clause.delete(at, at + phrase.length());
			}
		}
	}

	private static long dwellOf(Goal part) {
		return switch (part) {
			case Goal.LookAtBlock look -> look.dwellMs();
			case Goal.LookAtEntity look -> look.dwellMs();
			default -> 0L;
		};
	}

	/**
	 * A word drawn with an item (the pearl on the enderman) names the item only in a step of its own. Where another
	 * part of the sequence is the very thing the word names, the picture renames nothing: the pearl keeps its name.
	 */
	private static SentenceContext withoutRenamingPicture(List<Goal> parts, SentenceContext c) {
		Word word = c.word();
		if (word == null || word.iconItem == null) {
			return c;
		}
		boolean named = parts.stream().anyMatch(part -> {
			GoalType<Goal> type = GoalTypes.of(part);
			return type.object(part) != null && SentenceContext.wordNames(word, type.objectKind(part), type.object(part),
					c.namer());
		});
		return named ? c.withWord(SentenceBuilder.withoutPicture(word)) : c;
	}

	/** The words up to the first object marker 을/를 ("가죽 장화를 "), or null when the text does not open with one. */
	private static String openingObject(String text) {
		java.util.regex.Matcher marker = java.util.regex.Pattern.compile("^([^ ]+ )*?[^ ]*[을를] ").matcher(text);
		return marker.find() ? marker.group() : null;
	}
}
