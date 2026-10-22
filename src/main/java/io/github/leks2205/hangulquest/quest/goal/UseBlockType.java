package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/**
 * Use a block: open a door, ring a bell, sleep in a bed. With {@code change} a door, trapdoor or fence gate
 * has to change state the way the verb says (열어요, 닫아요); the sensor compares its {@code open} state
 * before and after the click.
 */
final class UseBlockType implements GoalType<Goal.UseBlock> {
	@Override
	public String id() {
		return "USE_BLOCK";
	}

	@Override
	public Class<Goal.UseBlock> goalClass() {
		return Goal.UseBlock.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"),
				Field.choice("change", false, "Open or close a door, trapdoor or fence gate: the block must really "
						+ "change state. Without it any use of the block counts.", "OPEN", "CLOSE"));
	}

	@Override
	public Goal.UseBlock parse(JsonObject json) {
		String change = JsonFields.string(json, "change", null);
		return new Goal.UseBlock(JsonFields.string(json, "block", ""),
				change == null || change.isBlank() ? null : change.toUpperCase(java.util.Locale.ROOT));
	}

	@Override
	public JsonObject toJson(Goal.UseBlock goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		if (goal.change() != null) {
			json.addProperty("change", goal.change());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.UseBlock goal) {
		return goal.change() == null ? goal.block() : goal.block() + "/" + goal.change();
	}

	@Override
	public boolean evaluate(Goal.UseBlock goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		if (goal.change() != null) {
			for (String changed : goal.change().equals("CLOSE") ? obs.closedBlocks : obs.openedBlocks) {
				if (Matching.matchesBlock(goal.block(), changed)) {
					tracker.count++;
				}
			}
			return tracker.count >= 1;
		}
		List<String> uses = nightLatch ? obs.usedBlocksAtNight : obs.usedBlocks;
		for (String used : uses) {
			if (Matching.matchesBlock(goal.block(), used)) {
				tracker.count++;
			}
		}
		if (obs.sleeping && obs.sleepingBed != null && Matching.matchesBlock(goal.block(), obs.sleepingBed)) {
			tracker.count++;
		}
		return tracker.count >= 1;
	}

	@Override
	public boolean nightLatchable() {
		return true;
	}

	/** Being in bed is proof of the night, even after the server skipped to morning. */
	@Override
	public boolean excusesTimeCondition(Goal.UseBlock goal, Observation obs) {
		return obs.sleeping && Matching.isBed(goal.block());
	}

	@Override
	public Verb verb(Goal.UseBlock goal, VerbTable table) {
		if (goal.change() != null) {
			return goal.change().equals("CLOSE") ? Verb.CLOSE : Verb.OPEN;
		}
		return table.choose(id(), goal.block(), 1, Verb.USE);
	}

	@Override
	public String korean(Goal.UseBlock goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.objectPhrase(c.obj(), c.verb()), c.verb(), c.level(),
				c.finale());
	}

	@Override
	public String ceremony(Goal.UseBlock goal, String obj, Verb verb) {
		return SentenceBuilder.objectPhrase(obj, verb) + " " + verb.past();
	}

	@Override
	public String statusOverride(Goal.UseBlock goal, Observation obs) {
		if (goal.block() == null || !goal.block().endsWith("_bed")) {
			return null;
		}
		if (obs.sleeping) {
			return "침대에서 자는 중";
		}
		return obs.bedUseRecent ? "침대를 사용했지만 잘 수 없어요" : null;
	}

	@Override
	public String status(Goal.UseBlock goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return "";
	}

	@Override
	public String object(Goal.UseBlock goal) {
		return goal.block();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}

	/** The blocks a hand opens and closes: doors (not iron ones), trapdoors and fence gates. */
	static boolean handOpened(String block) {
		if (block == null) {
			return false;
		}
		String path = block.substring(block.indexOf(':') + 1);
		return !path.equals("iron_door") && !path.equals("iron_trapdoor")
				&& (path.endsWith("_door") || path.endsWith("_trapdoor") || path.endsWith("_fence_gate"));
	}

	@Override
	public void validate(Goal.UseBlock goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.change() != null) {
			if (!goal.change().equals("OPEN") && !goal.change().equals("CLOSE")) {
				context.problem("change must be OPEN or CLOSE, not " + goal.change());
			} else if (!handOpened(goal.block())) {
				context.problem("a " + goal.block() + " does not open or close by hand; use a wooden door, "
						+ "trapdoor or fence gate");
			}
		}
	}
}
