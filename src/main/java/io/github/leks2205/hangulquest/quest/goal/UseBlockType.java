package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/**
 * Use a block: open a door, ring a bell, sleep in a bed. With {@code change} a door, trapdoor or fence gate
 * has to change state the way the verb says (열어요, 닫아요); the sensor compares its {@code open} state
 * before and after the click. {@code LIT} does the same for a candle or a campfire (켜요, 피워요): its
 * {@code lit} state has to flip on. A candle without a {@code change} is lit by default, because the verb
 * of the table is 켜다 and "초를 켜요" is a checked goal, not a click on the wick. A sign is written on
 * ({@code WRITE}, also the default for a sign): the edit screen has to be closed with text on the sign, which
 * the sensor reports; 표지판에 글을 써요, or 표지판에 이름을 써요 when the step teaches 이름.
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
				Field.choice("change", false, "Open or close a door, trapdoor or fence gate, or light a candle or "
						+ "campfire (LIT): the block must really change state. WRITE is writing on a sign (the edit "
						+ "screen closed with text). Without it any use of the block counts, except that a candle "
						+ "has to be lit and a sign has to be written on.", "OPEN", "CLOSE", "LIT", "WRITE"));
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
		String change = effectiveChange(goal);
		if (change != null) {
			List<String> changes = switch (change) {
				case "CLOSE" -> obs.closedBlocks;
				case "LIT" -> obs.litBlocks;
				case "WRITE" -> obs.signsWritten;
				default -> obs.openedBlocks;
			};
			for (String changed : changes) {
				if (change.equals("LIT") ? Matching.accepts(tracker, goal.block(), changed)
						: Matching.matches(tracker, goal.block(), changed)) {
					tracker.count++;
				}
			}
			return tracker.count >= 1;
		}
		List<String> uses = nightLatch ? obs.usedBlocksAtNight : obs.usedBlocks;
		for (String used : uses) {
			if (Matching.matches(tracker, goal.block(), used)) {
				tracker.count++;
			}
		}
		if (obs.sleeping && obs.sleepingBed != null && Matching.matches(tracker, goal.block(), obs.sleepingBed)) {
			tracker.count++;
		}
		return tracker.count >= 1;
	}

	/** The change the goal checks: its own, or LIT for a candle (the table speaks 켜다 for candles), WRITE for a sign. */
	static String effectiveChange(Goal.UseBlock goal) {
		if (screenOpened(goal)) {
			return null; // 상자를 열다: the click that opens the screen is the use
		}
		if (goal.change() != null) {
			return goal.change();
		}
		if (isSign(goal.block())) {
			return "WRITE";
		}
		return Matching.isCandle(goal.block()) && VerbTable.shared().choose("USE_BLOCK", goal.block(), 1, Verb.USE)
				== Verb.SWITCH_ON ? "LIT" : null;
	}

	/**
	 * OPEN on a chest, a barrel, a furnace and the like: they open a screen, not a door, so the click on the block
	 * is the opening (the lid has no state the client can read). CLOSE has no such event.
	 */
	private static boolean screenOpened(Goal.UseBlock goal) {
		return "OPEN".equals(goal.change()) && !handOpened(goal.block()) && !goal.block().endsWith("_door")
				&& !goal.block().endsWith("_trapdoor") && !goal.block().endsWith("_fence_gate")
				&& VerbTable.shared().choose("USE_BLOCK", goal.block(), 1, Verb.USE) == Verb.OPEN;
	}

	private static boolean isSign(String block) {
		return block != null && Matching.blockFamily(block).equals("sign");
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
		if ("WRITE".equals(effectiveChange(goal))) {
			return Verb.WRITE;
		}
		if (goal.change() != null) {
			return switch (goal.change()) {
				case "CLOSE" -> Verb.CLOSE;
				case "LIT" -> Matching.isCandle(goal.block()) ? Verb.SWITCH_ON : Verb.KINDLE;
				default -> Verb.OPEN;
			};
		}
		return table.choose(id(), goal.block(), 1, Verb.USE);
	}

	@Override
	public String korean(Goal.UseBlock goal, SentenceContext c) {
		if (c.verb() == Verb.WRITE) {
			return SentenceBuilder.phrase(writing(c), c.verb(), c.level(), c.finale());
		}
		return SentenceBuilder.phrase(tool(c) + SentenceBuilder.objectPhrase(c.obj(), c.verb()), c.verb(), c.level(),
				c.finale());
	}

	/**
	 * "표지판에 글을": any sign will do, so the sign is the general word and not the wood it was made from. The
	 * step that teaches 이름 asks for the name: "표지판에 이름을".
	 */
	private static String writing(SentenceContext c) {
		boolean name = c.word() != null && "name".equals(c.word().conceptId);
		return "표지판에 " + KoGrammar.eul(name ? "이름" : "글");
	}

	/**
	 * The request form of 갈다 is 가세요, the same as 가다 (to go): "흙을 가세요" is a trap. Where the ending is
	 * -(으)세요 the tilling sentence names its tool, which no one goes with: "괭이로 흙을 가세요". The other endings
	 * (갈아요, 갈아야 해요) cannot be mistaken.
	 */
	private static String tool(SentenceContext c) {
		boolean request = c.verb() == Verb.TILL && "ending:seyo".equals(Ladder.shared().rung(c.level()).ending());
		return request ? "괭이로 " : "";
	}

	@Override
	public List<String> grammarIds(Goal.UseBlock goal, SentenceContext c) {
		List<String> ids = new java.util.ArrayList<>(GoalType.super.grammarIds(goal, c));
		if (!tool(c).isEmpty()) {
			ids.add("L3.euro_instrument");
		}
		if (c.verb() == Verb.WRITE) {
			ids.add("L1.e_place"); // 표지판에
		}
		return ids;
	}

	@Override
	public String ceremony(Goal.UseBlock goal, String obj, Verb verb) {
		return verb == Verb.WRITE ? "표지판에 " + KoGrammar.eul("글") + " " + verb.past()
				: SentenceBuilder.objectPhrase(obj, verb) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.UseBlock goal, SentenceContext c) {
		return c.verb() == Verb.WRITE ? writing(c) + " " + c.verb().past() : ceremony(goal, c.obj(), c.verb());
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
			if (goal.change().equals("WRITE")) {
				if (!isSign(goal.block())) {
					context.problem("a " + goal.block() + " has no text to write; use a sign");
				}
			} else if (goal.change().equals("LIT")) {
				if (!Matching.isFlame(goal.block())) {
					context.problem("a " + goal.block() + " has no flame to light; use a candle or a campfire");
				}
			} else if (!goal.change().equals("OPEN") && !goal.change().equals("CLOSE")) {
				context.problem("change must be OPEN, CLOSE, LIT or WRITE, not " + goal.change());
			} else if (!handOpened(goal.block()) && !screenOpened(goal)) {
				context.problem("a " + goal.block() + " does not open or close by hand; use a wooden door, "
						+ "trapdoor or fence gate");
			}
		}
	}
}
