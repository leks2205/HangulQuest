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
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;

/**
 * Jump from a height: "높은 곳에서 가루눈 위로 뛰어내려요" (land on a soft block), "10블록 높이에서 안 다치고
 * 뛰어내려요" (a fall that costs no health). The sensor gives the distance fallen since the feet left the ground and
 * the block landed on; the game sets the distance back to 0 on the landing, so the longest value seen while in the
 * air is the fall. The damage packet comes a moment after the landing, so the verdict waits {@link #SETTLE_MS}.
 */
final class FallType implements GoalType<Goal.Fall> {
	private static final int DEFAULT_BLOCKS = 6;
	/** How long after the landing the health is read: the server's damage arrives a few ticks later. */
	static final long SETTLE_MS = 300L;
	private static final float HEALTH_SLACK = 0.05F;
	private static final String WATER = "minecraft:water";

	@Override
	public String id() {
		return "FALL";
	}

	@Override
	public Class<Goal.Fall> goalClass() {
		return Goal.Fall.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.integer("blocks", 4, 250, false),
				Field.optionalResource("landing", "A soft block to land on (powder snow, hay, slime, water); "
						+ "left out, the fall must cost no health."));
	}

	@Override
	public Goal.Fall parse(JsonObject json) {
		String landing = JsonFields.string(json, "landing", null);
		return new Goal.Fall(JsonFields.integer(json, "blocks", DEFAULT_BLOCKS),
				landing == null || landing.isBlank() ? null : landing);
	}

	@Override
	public JsonObject toJson(Goal.Fall goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("blocks", goal.blocks());
		if (goal.landing() != null) {
			json.addProperty("landing", goal.landing());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.Fall goal) {
		return goal.blocks() + (goal.landing() == null ? "" : "@" + goal.landing());
	}

	/**
	 * tracker.distance is the longest fall of the jump in progress, tracker.at[0] the health when the fall began,
	 * tracker.mark the time of the landing (0 while in the air), tracker.target the block landed on.
	 */
	@Override
	public boolean evaluate(Goal.Fall goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		boolean falling = obs.fallDistance > 0.0F && !obs.onGround;
		if (falling) {
			if (tracker.mark != 0L) { // jumped again before the last landing was judged
				forget(tracker);
			}
			if (obs.fallDistance > tracker.distance) {
				tracker.distance = obs.fallDistance;
			}
			if (tracker.at == null) {
				tracker.at = new double[] {obs.health};
			}
			tracker.count = (int) tracker.distance;
			return false;
		}
		if (tracker.distance <= 0.0) {
			return false;
		}
		if (tracker.mark == 0L) { // the first tick on the ground: where it landed
			tracker.mark = Math.max(1L, obs.now);
			tracker.target = obs.landedOn;
			return false;
		}
		if (obs.now - tracker.mark < SETTLE_MS) {
			return false;
		}
		boolean far = tracker.distance >= goal.blocks();
		boolean unhurt = tracker.at != null && obs.health >= tracker.at[0] - HEALTH_SLACK;
		boolean soft = goal.landing() == null ? unhurt : landedOn(goal.landing(), tracker.target, obs);
		forget(tracker);
		return far && soft;
	}

	private static boolean landedOn(String landing, String landed, Observation obs) {
		if (landing.equals(landed)) {
			return true;
		}
		// water is entered, not stood on: the block the sensor saw may be the bed under the surface
		return WATER.equals(landing) && obs.inWater;
	}

	private static void forget(GoalEvaluator.Tracker tracker) {
		tracker.distance = 0.0;
		tracker.at = null;
		tracker.mark = 0L;
		tracker.target = null;
	}

	@Override
	public boolean countable() {
		return false;
	}

	@Override
	public Verb verb(Goal.Fall goal, VerbTable table) {
		return Verb.JUMP_DOWN;
	}

	@Override
	public String korean(Goal.Fall goal, SentenceContext c) {
		return SentenceBuilder.phrase(head(goal, c, c.withCounts()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Fall goal, String obj, Verb verb) {
		return head(goal, null, true) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.Fall goal, SentenceContext c) {
		return head(goal, c, true) + " " + c.verb().past();
	}

	/**
	 * "높은 곳에서 가루눈 위로" onto a soft block; "10블록 높이에서 안 다치고" for a fall that costs nothing (the
	 * hint names no number). 안 up to level 2, 다치지 않고 from level 3.
	 */
	private static String head(Goal.Fall goal, SentenceContext c, boolean withCounts) {
		int level = c == null ? 1 : c.level();
		if (goal.landing() != null) {
			return "높은 곳에서 " + landing(goal, c);
		}
		String from = withCounts && level >= 2 ? KoGrammar.blocks(goal.blocks()) + " 높이에서" : "높은 곳에서";
		return from + " " + (level >= 3 ? "다치지 않고" : "안 다치고");
	}

	/** 가루눈 위로, 건초 더미 위로; water is entered, not stood on: 물로. */
	private static String landing(Goal.Fall goal, SentenceContext c) {
		String name = c == null ? "" : c.name(ObjectKind.BLOCK, goal.landing());
		if (name.isEmpty()) {
			name = KoreanNames.softBlock(goal.landing());
		}
		return WATER.equals(goal.landing()) || "minecraft:lava".equals(goal.landing()) ? KoGrammar.euro(name)
				: name + " 위로";
	}

	@Override
	public List<String> grammarIds(Goal.Fall goal, SentenceContext c) {
		List<String> ids = new ArrayList<>();
		ids.add(Ladder.shared().rung(c.level()).grammar());
		ids.add("L2.eseo_activity"); // 높은 곳에서
		if (goal.landing() != null) {
			ids.add("L2.euro_direction"); // 가루눈 위로
		} else {
			ids.add(c.level() >= 3 ? "L3.ji_anayo" : "L1.neg_an");
		}
		return ids;
	}

	@Override
	public String status(Goal.Fall goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return tracker.count > 0 && tracker.distance > 0.0 ? QuestStatus.count(tracker.count, goal.blocks()) : "";
	}

	@Override
	public String object(Goal.Fall goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}

	@Override
	public List<Mention> mentions(Goal.Fall goal) {
		return goal.landing() == null ? List.of() : List.of(new Mention(ObjectKind.BLOCK, goal.landing()));
	}

	@Override
	public String taskSignature(Goal.Fall goal) {
		return id() + ":" + (goal.landing() == null ? "unhurt" : goal.landing()) + ":" + verb(goal, VerbTable.shared()).name();
	}

	@Override
	public void validate(Goal.Fall goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.blocks() < 4 || goal.blocks() > 250) {
			context.problem("blocks must be 4..250 (a fall of 3 or less is no jump)");
		}
		if (goal.landing() != null && ValidationContext.badId(goal.landing())) {
			context.problem("bad landing block " + goal.landing());
		}
	}

	/** Fallback names for the soft blocks when the namer knows none. */
	private static final class KoreanNames {
		static String softBlock(String id) {
			return switch (id) {
				case "minecraft:powder_snow" -> "가루눈";
				case "minecraft:hay_block" -> "건초 더미";
				case "minecraft:slime_block" -> "슬라임 블록";
				case "minecraft:honey_block" -> "꿀 블록";
				case WATER -> "물";
				default -> "푹신한 곳";
			};
		}
	}
}
