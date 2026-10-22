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

import java.util.List;

/**
 * Listen to music: stand within some blocks of a jukebox that is playing a disc ("주크박스 근처에서 노래를
 * 들어요"), or strike a note block ("소리 블록의 소리를 들어요"), or hear a bell ring ("종소리를 들어요"). A jukebox
 * holds its disc in its block state while the song plays, and a bell shakes while it rings, so the sensor can
 * see both from the client (it scans the nearby block entities twice a second, and only while a step like
 * this is tracked). The sound itself is not heard by the mod: the player has to be near enough, which is what
 * the radius is for. A bell counts whoever rang it.
 */
final class HearType implements GoalType<Goal.Hear> {
	static final String JUKEBOX = "minecraft:jukebox";
	static final String NOTE_BLOCK = "minecraft:note_block";
	static final String BELL = "minecraft:bell";
	private static final int DEFAULT_RADIUS = 10;
	private static final long DEFAULT_DWELL_MS = 3000L;

	@Override
	public String id() {
		return "HEAR";
	}

	@Override
	public Class<Goal.Hear> goalClass() {
		return Goal.Hear.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("source"), Field.integer("radius", 2, 16, false),
				Field.integer("dwellMs", 500, 30000, false));
	}

	@Override
	public Goal.Hear parse(JsonObject json) {
		return new Goal.Hear(JsonFields.string(json, "source", JUKEBOX), JsonFields.integer(json, "radius", DEFAULT_RADIUS),
				JsonFields.integer(json, "dwellMs", (int) DEFAULT_DWELL_MS));
	}

	@Override
	public JsonObject toJson(Goal.Hear goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("source", goal.source());
		json.addProperty("radius", goal.radius());
		json.addProperty("dwellMs", goal.dwellMs());
		return json;
	}

	@Override
	public String keyPart(Goal.Hear goal) {
		return goal.source() + "/" + goal.radius() + "/" + goal.dwellMs();
	}

	private static boolean jukebox(Goal.Hear goal) {
		return JUKEBOX.equals(goal.source());
	}

	private static boolean bell(Goal.Hear goal) {
		return BELL.equals(goal.source());
	}

	@Override
	public boolean needsMusicScan(Goal.Hear goal) {
		return jukebox(goal) || bell(goal);
	}

	@Override
	public boolean evaluate(Goal.Hear goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		if (bell(goal)) { // a ring is over in a few seconds: hearing it once is enough
			return obs.bellDistance >= 0 && obs.bellDistance <= goal.radius();
		}
		if (!jukebox(goal)) {
			for (String used : obs.usedBlocks) {
				if (Matching.matches(tracker, goal.source(), used)) {
					tracker.count++;
				}
			}
			return tracker.count >= 1;
		}
		boolean near = obs.jukeboxDistance >= 0 && obs.jukeboxDistance <= goal.radius();
		return Matching.dwell(tracker, "music", near ? "music" : null, goal.dwellMs());
	}

	@Override
	public long dwellNeededMs(Goal.Hear goal) {
		return jukebox(goal) ? goal.dwellMs() : 0L;
	}

	@Override
	public Verb verb(Goal.Hear goal, VerbTable table) {
		return Verb.HEAR;
	}

	@Override
	public String korean(Goal.Hear goal, SentenceContext c) {
		return SentenceBuilder.phrase(what(goal, c), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Hear goal, String obj, Verb verb) {
		return SentenceBuilder.objectPhrase(obj, verb) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.Hear goal, SentenceContext c) {
		return what(goal, c) + " " + c.verb().past();
	}

	/** "주크박스 근처에서 노래를", "소리 블록의 소리를" or "종소리를". */
	private static String what(Goal.Hear goal, SentenceContext c) {
		String name = c.name(ObjectKind.BLOCK, goal.source());
		if (bell(goal)) {
			return KoGrammar.eul((name.isEmpty() ? "종" : name) + "소리");
		}
		if (jukebox(goal)) {
			return KoGrammar.eseo((name.isEmpty() ? "주크박스" : name) + " 근처") + " " + KoGrammar.eul("노래");
		}
		return (name.isEmpty() ? "소리 블록" : name) + "의 " + KoGrammar.eul("소리");
	}

	@Override
	public List<String> grammarIds(Goal.Hear goal, SentenceContext c) {
		List<String> ids = new java.util.ArrayList<>(GoalType.super.grammarIds(goal, c));
		if (jukebox(goal)) {
			ids.add("L2.eseo_activity");
		}
		return ids;
	}

	@Override
	public String status(Goal.Hear goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		if (bell(goal)) {
			return obs.bellDistance >= 0 ? "" : "종소리가 안 들려요";
		}
		if (!jukebox(goal)) {
			return "";
		}
		return obs.jukeboxDistance < 0 ? "주크박스가 안 보여요" : QuestStatus.look(tracker.dwellMs, goal.dwellMs());
	}

	@Override
	public String object(Goal.Hear goal) {
		return goal.source();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}

	@Override
	public void validate(Goal.Hear goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!jukebox(goal) && !bell(goal) && !NOTE_BLOCK.equals(goal.source())) {
			context.problem("a Hear goal listens to " + JUKEBOX + ", " + NOTE_BLOCK + " or " + BELL + ", not "
					+ goal.source());
		}
		if (goal.radius() < 2 || goal.radius() > 16) {
			context.problem("radius must be 2..16");
		}
	}
}
