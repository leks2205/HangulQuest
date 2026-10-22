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
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Go into a roofed space (집 안으로 들어가요) or out under the open sky (집 밖으로 나가요). The sensor
 * knows whether the sky is open above the player (a heightmap lookup, refreshed twice a second); a flip of
 * it that lasts a second is the passage. Standing inside when the step starts does not count: the
 * player has to be outside first for 들어가다, and the other way round for 나가다.
 *
 * <p>A cave, a tunnel and a tree's crown are "inside" to the sensor too; the sentence only names a place
 * when the step teaches a word for a built or hollow one ({@link #PLACES}), so it never says 집 for a cave.
 */
final class TransitType implements GoalType<Goal.Transit> {
	/** How long the new state has to last before it counts. */
	private static final long STABLE_MS = 1000L;
	/** Concepts that name a roofed place; any other taught word is not spoken here. */
	private static final Set<String> PLACES = Set.of("house", "room", "cave");
	private static final int INSIDE = 1;
	private static final int OUTSIDE = 2;

	@Override
	public String id() {
		return "TRANSIT";
	}

	@Override
	public Class<Goal.Transit> goalClass() {
		return Goal.Transit.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("direction", true, "IN goes under a roof, OUT goes under the open sky.",
				"IN", "OUT"));
	}

	@Override
	public Goal.Transit parse(JsonObject json) {
		String direction = JsonFields.string(json, "direction", "IN").toUpperCase(Locale.ROOT);
		return new Goal.Transit(direction.equals("OUT") ? "OUT" : "IN");
	}

	@Override
	public JsonObject toJson(Goal.Transit goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("direction", goal.direction());
		return json;
	}

	@Override
	public String keyPart(Goal.Transit goal) {
		return goal.direction();
	}

	private static boolean in(Goal.Transit goal) {
		return !"OUT".equals(goal.direction());
	}

	@Override
	public boolean evaluate(Goal.Transit goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		int state = obs.canSeeSky ? OUTSIDE : INSIDE;
		if (state != tracker.progress) { // the state seen now, and since when
			tracker.progress = state;
			tracker.mark = obs.now;
			return false;
		}
		if (obs.now - tracker.mark < STABLE_MS) {
			return false;
		}
		if (tracker.phase == 0) { // the state the step started in
			tracker.phase = state;
			return false;
		}
		if (tracker.phase == state) {
			return false;
		}
		tracker.phase = state;
		return state == (in(goal) ? INSIDE : OUTSIDE);
	}

	@Override
	public Verb verb(Goal.Transit goal, VerbTable table) {
		return in(goal) ? Verb.ENTER : Verb.LEAVE;
	}

	@Override
	public List<Verb> alternativeVerbs(Goal.Transit goal) {
		return in(goal) ? List.of() : List.of(Verb.COME_OUT);
	}

	/** The place the step names ("집 "), or the empty string. */
	private static String place(SentenceContext c) {
		return c.word() != null && PLACES.contains(c.word().conceptId) ? c.word().ko + " " : "";
	}

	@Override
	public String korean(Goal.Transit goal, SentenceContext c) {
		String place = place(c);
		String where = in(goal) ? KoGrammar.euro(place + "안")
				: c.verb() == Verb.COME_OUT && !place.isEmpty() ? KoGrammar.eseo(place.strip())
				: KoGrammar.euro(place + "밖");
		return SentenceBuilder.phrase(where, c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Transit goal, String obj, Verb verb) {
		return (in(goal) ? "안으로 " : "밖으로 ") + verb.past();
	}

	@Override
	public String ceremony(Goal.Transit goal, SentenceContext c) {
		String place = place(c);
		if (in(goal)) {
			return KoGrammar.euro(place + "안") + " " + c.verb().past();
		}
		// Leaving is told with 나오다 when the place is named: 집에서 나왔어요.
		return place.isEmpty() ? KoGrammar.euro("밖") + " " + c.verb().past()
				: KoGrammar.eseo(place.strip()) + " " + Verb.COME_OUT.past();
	}

	@Override
	public List<String> grammarIds(Goal.Transit goal, SentenceContext c) {
		List<String> ids = new ArrayList<>(GoalType.super.grammarIds(goal, c));
		ids.add(in(goal) ? "L1.loc_an_e" : "L1.loc_bak_e");
		ids.add("L1.euro_direction");
		return ids;
	}

	@Override
	public String status(Goal.Transit goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return "지금 " + (obs.canSeeSky ? "밖" : "안") + " · " + (in(goal) ? "안" : "밖") + " 필요";
	}

	@Override
	public String object(Goal.Transit goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}

	@Override
	public void validate(Goal.Transit goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!goal.direction().equals("IN") && !goal.direction().equals("OUT")) {
			context.problem("direction must be IN or OUT, not " + goal.direction());
		}
	}
}
