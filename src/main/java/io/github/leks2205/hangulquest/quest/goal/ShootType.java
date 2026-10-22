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
import io.github.leks2205.hangulquest.sense.ShotFact;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shoot or throw: "활로 과녁을 맞혀요", "쇠뇌로 좀비를 맞히세요", "삼지창을 던지세요", "멀리서 활로 …". A bow or a crossbow is
 * shot (쏘다) and a trident, snowball or egg is thrown (던지다); with a target the sentence says 맞히다 (not 맞추다)
 * with the weapon as 으로. The sensor follows every projectile of the player to its end ({@link ShotFact}): a creature
 * struck shows the hurt animation or the projectile vanishes beside it, a target block shows its power.
 *
 * <p>Pierce: "화살 하나로 좀비를 두 마리 맞히세요" (one shot must strike that many). Distance: "멀리서" without a number
 * (and in the hint), "이십 블록 이상 떨어진 곳에서" from level 2.
 */
final class ShootType implements GoalType<Goal.Shoot> {
	static final List<String> WEAPONS = List.of("minecraft:bow", "minecraft:crossbow", "minecraft:trident",
			"minecraft:snowball", "minecraft:egg");
	private static final Set<String> SHOT = Set.of("minecraft:bow", "minecraft:crossbow");
	private static final String BOW_COUNTER = "번";

	@Override
	public String id() {
		return "SHOOT";
	}

	@Override
	public Class<Goal.Shoot> goalClass() {
		return Goal.Shoot.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("weapon", true, "The item in the hand the projectile leaves from.",
						WEAPONS.toArray(String[]::new)),
				Field.optionalResource("entity", "A creature the projectile must strike."),
				Field.optionalResource("block", "A block the projectile must strike (minecraft:target)."),
				Field.integer("count", 1, 50, false),
				Field.integer("minDistance", 0, 100, false),
				Field.integer("pierce", 0, 5, false));
	}

	@Override
	public Goal.Shoot parse(JsonObject json) {
		return new Goal.Shoot(JsonFields.string(json, "weapon", "minecraft:bow"), blank(JsonFields.string(json, "entity", null)),
				blank(JsonFields.string(json, "block", null)), JsonFields.integer(json, "count", 1),
				JsonFields.integer(json, "minDistance", 0), JsonFields.integer(json, "pierce", 0));
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	@Override
	public JsonObject toJson(Goal.Shoot goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("weapon", goal.weapon());
		if (goal.entity() != null) {
			json.addProperty("entity", goal.entity());
		}
		if (goal.block() != null) {
			json.addProperty("block", goal.block());
		}
		json.addProperty("count", goal.count());
		if (goal.minDistance() > 0) {
			json.addProperty("minDistance", goal.minDistance());
		}
		if (goal.pierce() > 0) {
			json.addProperty("pierce", goal.pierce());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.Shoot goal) {
		return goal.weapon() + ":" + (goal.entity() == null ? goal.block() == null ? "" : goal.block() : goal.entity())
				+ ":" + goal.count() + (goal.minDistance() > 0 ? "/d" + goal.minDistance() : "")
				+ (goal.pierce() > 1 ? "/p" + goal.pierce() : "");
	}

	private static boolean hasTarget(Goal.Shoot goal) {
		return goal.entity() != null || goal.block() != null;
	}

	@Override
	public boolean evaluate(Goal.Shoot goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		for (ShotFact shot : obs.shots) {
			if (!goal.weapon().equals(shot.weapon())) {
				continue;
			}
			if (!hasTarget(goal)) {
				tracker.count++;
			} else if (goal.entity() != null) {
				List<ShotFact.Hit> strikes = shot.hits().stream()
						.filter(hit -> hit.entity().equals(goal.entity()) && hit.distance() >= goal.minDistance()).toList();
				if (goal.pierce() > 1) {
					if (strikes.size() >= goal.pierce()) {
						tracker.count++;
					}
				} else {
					for (ShotFact.Hit hit : strikes) {
						if (tracker.marks.add(hit.uuid())) { // one creature struck twice counts once
							tracker.count++;
						}
					}
				}
			} else if (goal.block().equals(shot.block()) && shot.blockDistance() >= goal.minDistance()) {
				tracker.count++;
			}
		}
		return tracker.count >= needed(goal);
	}

	@Override
	public boolean needsShotScan(Goal.Shoot goal) {
		return true;
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.Shoot goal) {
		return Math.max(1, goal.count());
	}

	// ---------------------------------------------------------------- Korean

	@Override
	public Verb verb(Goal.Shoot goal, VerbTable table) {
		if (hasTarget(goal)) {
			return Verb.HIT;
		}
		return SHOT.contains(goal.weapon()) ? Verb.SHOOT : Verb.THROW;
	}

	/** The weapon as the player was taught it: 활, 쇠뇌, 삼지창, 눈덩이, 달걀. */
	private static String weaponName(Goal.Shoot goal, SentenceContext c) {
		String name = c.name(ObjectKind.ITEM, goal.weapon());
		if (!name.isEmpty()) {
			return name;
		}
		return switch (goal.weapon()) {
			case "minecraft:crossbow" -> "쇠뇌";
			case "minecraft:trident" -> "삼지창";
			case "minecraft:snowball" -> "눈덩이";
			case "minecraft:egg" -> "달걀";
			default -> "활";
		};
	}


	/** "멀리서 " in the hint and at level 1, "이십 블록 이상 떨어진 곳에서 " with the number from level 2 on. */
	private static String far(Goal.Shoot goal, SentenceContext c) {
		if (goal.minDistance() <= 0) {
			return "";
		}
		return c.withCounts() && c.level() >= 2
				? KoGrammar.blocks(goal.minDistance()) + " 이상 떨어진 곳에서 " : "멀리서 ";
	}

	/** The target as it is named: the creature, or the block. */
	private String targetName(Goal.Shoot goal, SentenceContext c) {
		if (c.obj() != null && !c.obj().isEmpty()) {
			return c.obj();
		}
		return goal.entity() != null ? c.name(ObjectKind.ENTITY, goal.entity()) : c.name(ObjectKind.BLOCK, goal.block());
	}

	private String head(Goal.Shoot goal, SentenceContext c, boolean withCounts) {
		int count = Math.max(1, goal.count());
		if (!hasTarget(goal)) {
			String weapon = KoGrammar.eul(weaponName(goal, c));
			if (withCounts && count > 1) {
				weapon += " " + (SHOT.contains(goal.weapon()) ? KoGrammar.nativeNumeral(count) + " " + BOW_COUNTER
						: SentenceBuilder.counter(count, "개"));
			}
			return far(goal, c) + weapon;
		}
		String target = targetName(goal, c);
		String by = goal.pierce() > 1 && SHOT.contains(goal.weapon())
				? "화살 하나로 " : KoGrammar.euro(weaponName(goal, c)) + " ";
		String object = KoGrammar.eul(target);
		if (withCounts && goal.pierce() > 1) {
			object += " " + countOf(goal.pierce(), goal.entity() != null);
		} else if (withCounts && count > 1) {
			object += " " + countOf(count, goal.entity() != null);
		}
		return far(goal, c) + by + object;
	}

	/** 세 마리 for creatures, 세 번 for a block hit again and again. */
	private static String countOf(int n, boolean creature) {
		return creature ? SentenceBuilder.counter(n, "마리") : KoGrammar.nativeNumeral(n) + " " + BOW_COUNTER;
	}

	@Override
	public String korean(Goal.Shoot goal, SentenceContext c) {
		return SentenceBuilder.phrase(head(goal, c, c.withCounts()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Shoot goal, String obj, Verb verb) {
		return head(goal, new SentenceContext(obj, verb, true, 1, true), true) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.Shoot goal, SentenceContext c) {
		return head(goal, c, true) + " " + c.verb().past();
	}

	@Override
	public List<String> grammarIds(Goal.Shoot goal, SentenceContext c) {
		List<String> ids = new ArrayList<>();
		ids.add(io.github.leks2205.hangulquest.quest.grammar.Ladder.shared().rung(c.level()).grammar());
		ids.add("L1.eul_reul");
		if (hasTarget(goal)) {
			ids.add("L2.euro_material"); // 활로: the instrument
		}
		if (goal.minDistance() > 0 && c.level() >= 2) {
			ids.add("L2.num_sino");
		}
		if (goal.count() > 1 || goal.pierce() > 1) {
			ids.add("L1.num_native");
		}
		return ids;
	}

	@Override
	public String status(Goal.Shoot goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, needed(goal));
	}

	// ------------------------------------------------------------ validation

	@Override
	public String object(Goal.Shoot goal) {
		return goal.entity() != null ? goal.entity() : goal.block();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ENTITY;
	}

	@Override
	public ObjectKind objectKind(Goal.Shoot goal) {
		return goal.entity() != null ? ObjectKind.ENTITY : goal.block() != null ? ObjectKind.BLOCK : ObjectKind.ITEM;
	}

	@Override
	public List<Mention> mentions(Goal.Shoot goal) {
		List<Mention> all = new ArrayList<>();
		all.add(new Mention(ObjectKind.ITEM, goal.weapon()));
		if (goal.entity() != null) {
			all.add(new Mention(ObjectKind.ENTITY, goal.entity()));
		}
		if (goal.block() != null) {
			all.add(new Mention(ObjectKind.BLOCK, goal.block()));
		}
		return all;
	}

	@Override
	public String taskSignature(Goal.Shoot goal) {
		return id() + ":" + goal.weapon() + ":" + (object(goal) == null ? "" : object(goal)) + ":"
				+ verb(goal, VerbTable.shared()).name()
				+ (goal.minDistance() > 0 ? ":far" : "") + (goal.pierce() > 1 ? ":pierce" + goal.pierce() : "");
	}

	@Override
	public void validate(Goal.Shoot goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!WEAPONS.contains(goal.weapon())) {
			context.problem("weapon must be one of " + WEAPONS + ", not " + goal.weapon());
		}
		if (goal.entity() != null && goal.block() != null) {
			context.problem("SHOOT takes a creature or a block as its target, not both");
		}
		if (goal.entity() != null && ValidationContext.badId(goal.entity())) {
			context.problem("bad entity " + goal.entity());
		}
		if (goal.block() != null && ValidationContext.badId(goal.block())) {
			context.problem("bad block " + goal.block());
		}
		if (goal.block() != null && !"minecraft:target".equals(goal.block())) {
			context.problem("only the target block shows a hit; " + goal.block() + " cannot be checked");
		}
		if (goal.count() < 1 || goal.count() > 50) {
			context.problem("count must be 1..50");
		}
		if (goal.pierce() > 1 && (!SHOT.contains(goal.weapon()) || goal.entity() == null)) {
			context.problem("pierce needs an arrow shot at a creature (bow or crossbow with an entity)");
		}
		if (goal.minDistance() < 0 || goal.minDistance() > 100) {
			context.problem("minDistance must be 0..100");
		}
	}
}
