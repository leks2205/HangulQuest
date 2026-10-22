package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
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
	private static final String[] CONNECTORS = {"BEFORE", "AFTER", "THEN", "AND"};
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
						+ "sequence; chosen from the parts when left out. AND is the plain -고 (level 2); the order "
						+ "is checked all the same.", CONNECTORS));
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
		int minLevel = plainGo ? MIN_LEVEL_AND : MIN_LEVEL;
		if (context.koreanLevel() > 0 && context.koreanLevel() < minLevel) {
			context.problem("SEQUENCE needs koreanLevel " + minLevel + " or higher ("
					+ (plainGo ? "-고" : "-기 전에, -(으)ㄴ 후에, -고 나서; at level 2 only the connector AND, -고") + ")");
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
		Clauses.checkClueDoesNotTeach(context, false);
	}

	/** Goals that one event completes, so a later part can be checked on a fresh tracker each tick. */
	private static boolean isEvent(Goal part) {
		return part instanceof Goal.PlaceBlock || part instanceof Goal.PlaceRel || part instanceof Goal.PlaceOnPattern
				|| part instanceof Goal.BuildPattern || part instanceof Goal.BreakBlock || part instanceof Goal.Craft
				|| part instanceof Goal.Smelt || part instanceof Goal.Give || part instanceof Goal.PutIn
				|| part instanceof Goal.TakeOut
				|| part instanceof Goal.UseBlock || part instanceof Goal.InteractEntity
				|| part instanceof Goal.ConsumeItem;
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
		return goal.ordered() && "BEFORE".equals(effectiveConnector(goal)) ? 0 : last;
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
			default -> Clauses.Link.THEN;
		};
	}

	/**
	 * "A 기 전에 B" says B first and A second, so BEFORE puts the later part in the clause. The other links
	 * put the earlier part in the clause. The last part of an unordered sequence carries the verb.
	 */
	private static String joined(Goal.Sequence goal, SentenceContext c, boolean past) {
		List<Goal> parts = goal.parts();
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
		text.append(past ? Clauses.ceremony(main, c) : Clauses.sentence(main, c, c.level()));
		return text.toString();
	}
}
