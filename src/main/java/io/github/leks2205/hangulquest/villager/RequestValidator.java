package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.dialogue.DialogueValidator;
import io.github.leks2205.hangulquest.dialogue.SpeechLevel;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Checks a request: its kind claims only what the game can see (the honesty rules), the goal passes the quest
 * goal validator, every personality that may offer it has all five lines in its own speech level, and every line
 * has a gloss.
 */
public final class RequestValidator {
	private static final Pattern ID = Pattern.compile("req_[a-z0-9_]+");
	/** What a vanilla villager picks up from the ground: the only things a GIVE request may ask for. */
	public static final Set<String> GIVE_ITEMS = Set.of("minecraft:bread", "minecraft:wheat", "minecraft:wheat_seeds",
			"minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:beetroot_seeds",
			"minecraft:torchflower_seeds", "minecraft:pitcher_pod", "minecraft:bone_meal");
	/** Words of handing over: a SHOW request takes nothing, so its lines must not thank for a gift. */
	private static final Pattern GIFT = Pattern.compile("줘서|주셔서|주셨|줬|받았|가져다");
	private static final String VILLAGER = "minecraft:villager";

	/**
	 * What a request may refer to; a null part is not checked.
	 *
	 * @param itemExists true for an item id the game has (names.json)
	 */
	public record Env(Catalog catalog, Predicate<String> wordExists, Predicate<String> keyExists,
					  Predicate<String> itemExists) {
		public static Env none() {
			return new Env(null, null, null, null);
		}
	}

	private RequestValidator() {
	}

	public static List<String> validate(Request request, Env env) {
		List<String> problems = new ArrayList<>();
		String id = request.id();
		if (!ID.matcher(id).matches()) {
			problems.add(id + ": a request id is req_ followed by a-z, 0-9 and _");
		}
		if (request.level() < 1 || request.level() > 6) {
			problems.add(id + ": level must be 1 to 6");
		}
		if (request.regions().isEmpty()) {
			problems.add(id + ": a request names the regions where villagers ask for it");
		}
		if (request.kind() != null) {
			checkKind(request, env, problems);
		}
		if (request.words().isEmpty()) {
			problems.add(id + ": words: the catalog words the request practises");
		}
		for (String word : request.words()) {
			if (env.wordExists() != null && !env.wordExists().test(word)) {
				problems.add(id + ": word \"" + word + "\" is not in the catalog");
			}
		}
		checkAnswer(id + ".answers.yes", request.yes(), problems);
		checkAnswer(id + ".answers.no", request.no(), problems);
		for (Personality personality : request.lines().keySet()) {
			if (!request.offeredBy().contains(personality)) {
				problems.add(id + ".lines." + personality.name() + ": never used, the request is only for "
						+ request.personalities());
			}
		}
		for (Personality personality : request.offeredBy()) {
			for (Request.Part part : Request.Part.values()) {
				String where = id + "." + Request.nodeId(personality, part);
				Request.Line line = request.line(personality, part);
				if (line == null || line.ko() == null || line.ko().isBlank()) {
					problems.add(where + ": missing (every personality that may offer it needs offer, accept, decline,"
							+ " reminder and thanks)");
					continue;
				}
				checkLine(request, personality, where, line, problems);
				needKey(env, request.lineKey(personality, part), problems);
			}
		}
		needKey(env, request.answerKey(true), problems);
		needKey(env, request.answerKey(false), problems);
		needKey(env, request.titleKey(), problems);
		return problems;
	}

	/** The honesty rules: each kind asks only for what the client can check. */
	private static void checkKind(Request request, Env env, List<String> problems) {
		String id = request.id();
		Goal goal = request.goal();
		switch (request.kind()) {
			case SHOW -> {
				if (goal != null) {
					problems.add(id + ": a SHOW request has no goal, only show.item");
				}
				if (request.showItem() == null || ValidationContext.badId(request.showItem())) {
					problems.add(id + ": a SHOW request needs show.item");
				} else if (env.itemExists() != null && !env.itemExists().test(request.showItem())) {
					problems.add(id + ": show.item \"" + request.showItem() + "\" is no item of the game");
				}
				if (request.showCount() < 1 || request.showCount() > 64) {
					problems.add(id + ": show.count must be 1 to 64");
				}
			}
			case GIVE -> {
				if (request.showItem() != null) {
					problems.add(id + ": show is for SHOW requests");
				}
				if (!(goal instanceof Goal.Give give)) {
					problems.add(id + ": a GIVE request has a GIVE goal");
					break;
				}
				if (!VILLAGER.equals(give.entity())) {
					problems.add(id + ": a GIVE request gives to " + VILLAGER);
				}
				if (give.method() != null && !give.method().equals("DROP_NEAR")) {
					problems.add(id + ": a villager only takes what is dropped in front of it (method DROP_NEAR)");
				}
				if (!GIVE_ITEMS.contains(give.item())) {
					problems.add(id + ": a villager does not pick up " + give.item() + "; ask for one of " + GIVE_ITEMS
							.stream().sorted().toList());
				}
				checkGoal(request, env, problems);
			}
			case DO -> {
				if (request.showItem() != null) {
					problems.add(id + ": show is for SHOW requests");
				}
				if (goal == null) {
					problems.add(id + ": a DO request needs a goal");
					break;
				}
				if (goal instanceof Goal.Give) {
					problems.add(id + ": giving to a villager is a GIVE request");
				}
				checkGoal(request, env, problems);
			}
		}
	}

	/** The goal through the same checks as a quest step's. */
	private static void checkGoal(Request request, Env env, List<String> problems) {
		if (env.catalog() == null || request.goal() == null) {
			return;
		}
		QuestStep step = new QuestStep("goal", List.of(), request.goal(), List.of(), null, null, null, null, null);
		GoalTypes.of(request.goal()).validate(request.goal(),
				new ValidationContext(request.id() + "/goal", step, env.catalog(), problems, request.level()));
	}

	private static void checkLine(Request request, Personality personality, String where, Request.Line line,
								  List<String> problems) {
		for (SpeechLevel shown : SpeechLevel.ofLine(line.ko())) {
			if (shown != personality.register()) {
				problems.add(where + ": " + personality.name() + " speaks " + personality.register().id()
						+ ", the line is " + shown.id());
			}
		}
		if (line.koBanmal() != null) {
			if (!personality.mayOfferBanmal()) {
				problems.add(where + ": ko_banmal is never used, " + personality.name() + " does not talk banmal");
			}
			if (line.koBanmal().isBlank() || line.koBanmal().equals(line.ko())) {
				problems.add(where + ": ko_banmal must be the line in banmal, not empty or the same");
			}
			for (SpeechLevel shown : SpeechLevel.ofLine(line.koBanmal())) {
				if (shown != SpeechLevel.BANMAL) {
					problems.add(where + ": ko_banmal is " + shown.id() + ", it must be banmal");
				}
			}
		}
		for (String text : line.koBanmal() == null ? List.of(line.ko()) : List.of(line.ko(), line.koBanmal())) {
			String name = DialogueValidator.nameIn(text);
			if (name != null) {
				problems.add(where + ": \"" + text + "\" says the name " + name);
			}
			if (request.kind() == Request.Kind.SHOW && GIFT.matcher(text).find()) {
				problems.add(where + ": a SHOW request takes nothing; \"" + text + "\" speaks of a gift");
			}
		}
	}

	/** The player's answers are 해요체, like every reply the player picks. */
	private static void checkAnswer(String where, Request.Line answer, List<String> problems) {
		if (answer == null || answer.ko() == null || answer.ko().isBlank()) {
			problems.add(where + ": missing (the player's answer)");
			return;
		}
		for (SpeechLevel shown : SpeechLevel.ofLine(answer.ko())) {
			if (shown != SpeechLevel.HAEYO) {
				problems.add(where + ": the player answers haeyo, the line is " + shown.id());
			}
		}
		if (answer.koBanmal() != null) {
			problems.add(where + ": the player's answers have no ko_banmal");
		}
	}

	private static void needKey(Env env, String key, List<String> problems) {
		if (env.keyExists() != null && !env.keyExists().test(key)) {
			problems.add("no gloss text for " + key);
		}
	}
}
