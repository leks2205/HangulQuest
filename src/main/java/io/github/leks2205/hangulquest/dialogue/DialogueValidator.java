package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.VillagerNames;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Checks a dialogue graph: every node leads somewhere, every node can be reached and can reach an
 * end, effects point at things that exist, and each line keeps the speech level of its speaker.
 */
public final class DialogueValidator {
	/** Ids end up in line ids and language keys, where a dot or a space would break them. */
	private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");

	/** What the dialogue may refer to. A null part is not checked. */
	public record Env(Set<String> trailIds, Set<String> grammarIds, Predicate<String> wordExists,
					  Predicate<String> keyExists) {
		public static Env none() {
			return new Env(null, null, null, null);
		}
	}

	private DialogueValidator() {
	}

	public static List<String> validate(Dialogue dialogue, Env env) {
		List<String> problems = new ArrayList<>();
		String id = dialogue.id();
		if (!NAME.matcher(id).matches()) {
			problems.add(id + ": the id may only use a-z, 0-9 and _ (it is part of the line ids and language keys)");
		}
		if (dialogue.trigger().entity() == null || dialogue.trigger().entity().isBlank()) {
			problems.add(id + ": no trigger entity");
		}
		if (!dialogue.nodes().containsKey(dialogue.start())) {
			problems.add(id + ": start node \"" + dialogue.start() + "\" does not exist");
			return problems;
		}
		for (Dialogue.Node node : dialogue.nodes().values()) {
			checkNode(dialogue, node, env, problems);
		}
		checkVillagerRules(dialogue, problems);
		checkReachability(dialogue, problems);
		return problems;
	}

	// ---------------------------------------------------------------- villager conversations

	/** The friendship levels a milestone conversation can be written for. */
	public static final Set<Integer> MILESTONES = Set.of(3, 6, 10);
	private static final String VILLAGER = "minecraft:villager";

	/**
	 * Personality, region, kind and friendship: a villager conversation is for adult villagers spoken by
	 * {@code villager} (or for babies spoken by {@code child}); SNOOTY is written apart because it talks 합쇼체;
	 * every kind has its own shape.
	 */
	private static void checkVillagerRules(Dialogue dialogue, List<String> problems) {
		Dialogue.Trigger trigger = dialogue.trigger();
		String id = dialogue.id();
		boolean baby = Boolean.TRUE.equals(trigger.baby());
		if (trigger.villagerOnly() && !VILLAGER.equals(trigger.entity())) {
			problems.add(id + ": personalities, regions, kind and friendship are for " + VILLAGER + " only");
		}
		if (!trigger.personalities().isEmpty() && DialogueParser.villagerLevel(trigger) == null) {
			problems.add(id + ": SNOOTY speaks hapsyo and the others haeyo; write SNOOTY's conversations in their own file");
		}
		if (trigger.friendship() < 0 || trigger.friendship() > 10) {
			problems.add(id + ": friendship must be 0 to 10");
		}
		if (trigger.villagerOnly()) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				Speaker wanted = baby ? Speaker.CHILD : Speaker.VILLAGER;
				if (node.speaker() != wanted) {
					problems.add(id + "." + node.id() + ": a villager conversation" + (baby ? " for babies" : "")
							+ " is spoken by " + wanted.id() + ", not " + node.speaker().id());
				}
			}
		}
		List<Dialogue.Node> intros = dialogue.nodes().values().stream().filter(Dialogue.Node::selfIntro).toList();
		boolean acceptsBanmal = dialogue.nodes().values().stream().flatMap(node -> node.choices().stream())
				.flatMap(choice -> choice.effects().stream()).anyMatch(effect -> effect instanceof Dialogue.AcceptBanmal)
				|| dialogue.nodes().values().stream().flatMap(node -> node.effects().stream())
				.anyMatch(effect -> effect instanceof Dialogue.AcceptBanmal);
		switch (trigger.kind()) {
			case INTRO -> {
				if (dialogue.level() != 1) {
					problems.add(id + ": an intro is level 1");
				}
				if (!baby && trigger.personalities().isEmpty()) {
					problems.add(id + ": an intro names the personalities it is for");
				}
				if (intros.size() != 1) {
					problems.add(id + ": an intro has exactly one selfIntro node, not " + intros.size());
				}
			}
			case MILESTONE -> {
				if (!MILESTONES.contains(trigger.friendship())) {
					problems.add(id + ": a milestone needs friendship 3, 6 or 10");
				}
				if (trigger.personalities().isEmpty()) {
					problems.add(id + ": a milestone names the personalities it is for");
				}
			}
			case BANMAL_OFFER -> {
				if (trigger.personalities().isEmpty()
						|| trigger.personalities().stream().anyMatch(personality -> !personality.mayOfferBanmal())) {
					problems.add(id + ": only LAZY, JOCK, PEPPY and SISTERLY offer banmal, and the offer names them");
				}
				if (!acceptsBanmal) {
					problems.add(id + ": a banmal offer needs a choice with the acceptBanmal effect");
				}
			}
			case REQUEST -> problems.add(id + ": requests are written in requests/, not as a dialogue");
			default -> {
			}
		}
		if (trigger.kind() != ConversationKind.INTRO && !intros.isEmpty()) {
			problems.add(id + ": selfIntro is only for intro conversations");
		}
		if (trigger.kind() != ConversationKind.BANMAL_OFFER && acceptsBanmal) {
			problems.add(id + ": acceptBanmal is only for banmal_offer conversations");
		}
		boolean someoneSwitches = trigger.personalities().isEmpty()
				|| trigger.personalities().stream().anyMatch(Personality::mayOfferBanmal);
		for (Dialogue.Node node : dialogue.nodes().values()) {
			String where = id + "." + node.id();
			if (node.koBanmal() != null) {
				if (node.speaker() != Speaker.VILLAGER) {
					problems.add(where + ": ko_banmal is for villager lines only");
				} else if (!someoneSwitches) {
					problems.add(where + ": ko_banmal is never used: none of " + trigger.personalities() + " talks banmal");
				}
				if (node.koBanmal().isBlank() || node.koBanmal().equals(node.ko())) {
					problems.add(where + ": ko_banmal must be the line in banmal, not empty or the same");
				}
				for (SpeechLevel shown : SpeechLevel.ofLine(node.koBanmal())) {
					if (shown != SpeechLevel.BANMAL) {
						problems.add(where + ": ko_banmal is " + shown.id() + ", it must be banmal");
					}
				}
			}
			if (node.selfIntro()) {
				if (!node.ko().isBlank() || node.koBanmal() != null) {
					problems.add(where + ": a selfIntro node has no ko: the game says the name");
				}
			}
			for (String line : linesOf(node)) {
				String name = nameIn(line);
				if (name != null) {
					problems.add(where + ": \"" + line + "\" says the name " + name
							+ "; only the selfIntro line may (every line is recorded once for every villager)");
				}
			}
		}
	}

	private static List<String> linesOf(Dialogue.Node node) {
		List<String> lines = new ArrayList<>();
		lines.add(node.ko());
		if (node.koBanmal() != null) {
			lines.add(node.koBanmal());
		}
		node.choices().forEach(choice -> lines.add(choice.ko()));
		return lines;
	}

	/** What may follow a name in a sentence: particles, the copula and address forms. */
	private static final Set<String> NAME_TAILS = Set.of("", "이", "가", "은", "는", "을", "를", "의", "에게", "한테",
			"와", "과", "도", "만", "이에요", "예요", "이야", "야", "아", "입니다", "이랑", "랑", "씨", "님", "이가", "이는");

	/** The villager name a line says, or null. A name counts as a word of its own, followed by at most a particle. */
	public static String nameIn(String line) {
		if (line == null) {
			return null;
		}
		for (String eojeol : line.strip().split("\\s+")) {
			String core = DialogueCoverage.strip(eojeol);
			for (VillagerNames.Name name : VillagerNames.all()) {
				if (core.startsWith(name.ko()) && NAME_TAILS.contains(core.substring(name.ko().length()))) {
					return name.ko();
				}
			}
		}
		return null;
	}

	private static void checkNode(Dialogue dialogue, Dialogue.Node node, Env env, List<String> problems) {
		String where = dialogue.id() + "." + node.id();
		if (!NAME.matcher(node.id()).matches()) {
			problems.add(where + ": the node id may only use a-z, 0-9 and _");
		}
		if (node.ko().isBlank() && !node.selfIntro()) {
			problems.add(where + ": no Korean line");
		}
		boolean typed = node.typed() != null;
		int exits = (typed ? 1 : 0) + (node.choices().isEmpty() ? 0 : 1) + (node.end() ? 1 : 0)
				+ (!typed && node.next() != null ? 1 : 0);
		if (typed) {
			if (node.next() == null) {
				problems.add(where + ": a typed node needs next");
			}
			if (!node.typed().free() && node.typed().expected().isEmpty()) {
				problems.add(where + ": typed needs an expected answer or free");
			}
			if (node.typed().free() && !node.typed().expected().isEmpty()) {
				problems.add(where + ": a free typed answer has nothing to expect");
			}
		}
		if (exits != 1) {
			problems.add(where + ": needs exactly one of next, choices, typed or end");
		}
		if (node.next() != null && !dialogue.nodes().containsKey(node.next())) {
			problems.add(where + ": next \"" + node.next() + "\" does not exist");
		}
		checkSpeech(dialogue, where, node, problems);
		checkChoices(dialogue, node, where, env, problems);
		checkEffects(where, node.effects(), env, problems);
		if (node.typed() != null && node.typed().word() != null && env != null && env.wordExists() != null
				&& !env.wordExists().test(node.typed().word())) {
			problems.add(where + ": typed word \"" + node.typed().word() + "\" is not in the catalog");
		}
		if (node.grammar() != null && env != null && env.grammarIds() != null
				&& !env.grammarIds().contains(node.grammar())) {
			problems.add(where + ": grammar point \"" + node.grammar() + "\" does not exist");
		}
		if (env != null && env.keyExists() != null) {
			needKey(env, dialogue.lineKey(node.id()), problems);
			if (node.typed() != null) {
				needKey(env, dialogue.promptKey(node.id()), problems);
			}
			for (int i = 0; i < node.choices().size(); i++) {
				needKey(env, dialogue.choiceKey(node.id(), i), problems);
			}
		}
	}

	private static void needKey(Env env, String key, List<String> problems) {
		if (!env.keyExists().test(key)) {
			problems.add("no gloss text for " + key);
		}
	}

	/**
	 * The line keeps the level its speaker uses (a villager in a personality conversation: that personality's
	 * level); a level the ending does not show is not an error.
	 */
	private static void checkSpeech(Dialogue dialogue, String where, Dialogue.Node node, List<String> problems) {
		SpeechLevel wanted = expectedLevel(dialogue, node);
		if (node.speechLevel() != wanted) {
			problems.add(where + ": " + node.speaker().id() + " speaks " + wanted.id() + ", not "
					+ node.speechLevel().id());
		}
		for (SpeechLevel shown : SpeechLevel.ofLine(node.ko())) {
			if (shown != node.speechLevel()) {
				problems.add(where + ": the line is " + shown.id() + ", the node says " + node.speechLevel().id());
			}
		}
	}

	/** The level an NPC line must have: the speaker's, or for a villager the register of the dialogue's personalities. */
	public static SpeechLevel expectedLevel(Dialogue dialogue, Dialogue.Node node) {
		SpeechLevel register = DialogueParser.villagerLevel(dialogue.trigger());
		return node.speaker() == Speaker.VILLAGER && register != null ? register : node.speaker().level();
	}

	private static void checkChoices(Dialogue dialogue, Dialogue.Node node, String where, Env env,
									 List<String> problems) {
		if (node.choices().isEmpty()) {
			return;
		}
		boolean moves = false;
		boolean graded = false;
		boolean right = false;
		for (int i = 0; i < node.choices().size(); i++) {
			Dialogue.Choice choice = node.choices().get(i);
			String at = where + ".c" + (i + 1);
			if (choice.ko().isBlank()) {
				problems.add(at + ": no Korean line");
			}
			for (SpeechLevel shown : SpeechLevel.ofLine(choice.ko())) {
				if (shown != choice.speechLevel()) {
					problems.add(at + ": the line is " + shown.id() + ", the choice says " + choice.speechLevel().id());
				}
			}
			if (choice.next() != null) {
				moves = true;
				if (!dialogue.nodes().containsKey(choice.next())) {
					problems.add(at + ": next \"" + choice.next() + "\" does not exist");
				}
			}
			if (choice.correct() != null) {
				graded = true;
				right |= choice.correct();
			}
			if (choice.next() == null && !choice.wrong()) {
				problems.add(at + ": a choice without next must be marked wrong");
			}
			checkEffects(at, choice.effects(), env, problems);
		}
		if (!moves) {
			problems.add(where + ": no choice leads on");
		}
		if (node.grammar() != null && !(graded && right)) {
			problems.add(where + ": a graded node needs a right and a wrong choice marked");
		}
		checkSpeechTask(node, where, problems);
		if (node.grammar() == null && graded) {
			problems.add(where + ": choices are marked right or wrong but the node names no grammar point");
		}
	}

	/** The speech-level constructions of grammar.json and the level each one teaches. */
	private static final Map<String, SpeechLevel> TASKS = Map.of("L5.hapsyo", SpeechLevel.HAPSYO,
			"L6.banmal", SpeechLevel.BANMAL);

	/**
	 * A node that grades 합쇼체 or 반말 is asked by someone who speaks that level, the right answer is in that level and
	 * no wrong answer is: the player is graded on talking the way the listener is talked to.
	 */
	private static void checkSpeechTask(Dialogue.Node node, String where, List<String> problems) {
		SpeechLevel taught = node.grammar() == null ? null : TASKS.get(node.grammar());
		if (taught == null) {
			return;
		}
		if (node.speaker().level() != taught) {
			problems.add(where + ": the task teaches " + taught.id() + " but " + node.speaker().id() + " speaks "
					+ node.speaker().level().id());
		}
		for (int i = 0; i < node.choices().size(); i++) {
			Dialogue.Choice choice = node.choices().get(i);
			String at = where + ".c" + (i + 1);
			if (Boolean.TRUE.equals(choice.correct()) && choice.speechLevel() != taught) {
				problems.add(at + ": the right choice must be " + taught.id() + ", not " + choice.speechLevel().id());
			}
			if (choice.wrong() && choice.speechLevel() == taught) {
				problems.add(at + ": a wrong choice cannot be " + taught.id() + ", the level the task asks for");
			}
		}
	}

	private static void checkEffects(String where, List<Dialogue.Effect> effects, Env env, List<String> problems) {
		for (Dialogue.Effect effect : effects) {
			if (effect instanceof Dialogue.StartTrail start) {
				if (start.trail().isBlank()) {
					problems.add(where + ": startTrail without a trail");
				} else if (env != null && env.trailIds() != null && !env.trailIds().contains(start.trail())) {
					problems.add(where + ": trail \"" + start.trail() + "\" does not exist");
				}
			} else if (effect instanceof Dialogue.ExposeWords words) {
				if (words.words().isEmpty()) {
					problems.add(where + ": exposeWords without words");
				}
				for (String word : words.words()) {
					if (env != null && env.wordExists() != null && !env.wordExists().test(word)) {
						problems.add(where + ": word \"" + word + "\" is not in the catalog");
					}
				}
			} else if (effect instanceof Dialogue.ExposeGrammar grammar) {
				if (env != null && env.grammarIds() != null && !env.grammarIds().contains(grammar.point())) {
					problems.add(where + ": grammar point \"" + grammar.point() + "\" does not exist");
				}
			}
		}
	}

	/** Every node is reachable from the start, and from every node an end can be reached. */
	private static void checkReachability(Dialogue dialogue, List<String> problems) {
		Set<String> reached = new LinkedHashSet<>();
		Deque<String> queue = new ArrayDeque<>(List.of(dialogue.start()));
		while (!queue.isEmpty()) {
			String id = queue.poll();
			Dialogue.Node node = dialogue.nodes().get(id);
			if (node == null || !reached.add(id)) {
				continue;
			}
			successors(node).forEach(queue::add);
		}
		for (String id : dialogue.nodes().keySet()) {
			if (!reached.contains(id)) {
				problems.add(dialogue.id() + "." + id + ": cannot be reached from the start");
			}
		}
		Set<String> canEnd = new HashSet<>();
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Dialogue.Node node : dialogue.nodes().values()) {
				if (canEnd.contains(node.id())) {
					continue;
				}
				if (node.end() || successors(node).anyMatch(canEnd::contains)) {
					canEnd.add(node.id());
					changed = true;
				}
			}
		}
		for (String id : dialogue.nodes().keySet()) {
			if (!canEnd.contains(id)) {
				problems.add(dialogue.id() + "." + id + ": the conversation cannot end from here");
			}
		}
	}

	private static java.util.stream.Stream<String> successors(Dialogue.Node node) {
		List<String> next = new ArrayList<>();
		if (node.next() != null) {
			next.add(node.next());
		}
		node.choices().stream().map(Dialogue.Choice::next).filter(java.util.Objects::nonNull).forEach(next::add);
		return next.stream();
	}
}
