package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ObjectKind;
import io.github.leks2205.hangulquest.quest.goal.SentenceContext;
import io.github.leks2205.hangulquest.quest.grammar.Endings;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.Role;

import java.util.List;

/**
 * Turns a goal into the Korean instruction. Sentences are generated from the
 * structured goal, so particles stay correct and the hint can drop complexity
 * without a second authoring pass.
 */
public final class SentenceBuilder {
	private SentenceBuilder() {
	}

	/** Resolves condition ids to Korean words (biome/entity/block). */
	public interface Namer {
		Namer NONE = new Namer() {
			@Override
			public String biome(String id) {
				return null;
			}

			@Override
			public String entity(String id) {
				return null;
			}

			@Override
			public String block(String id) {
				return null;
			}
		};

		String biome(String id);

		String entity(String id);

		String block(String id);

		/** The block an earlier step of the trail placed, with the verb it used; null when unknown. */
		record Placed(String block, Verb verb) {
		}

		/** What the step {@code stepId} before this one placed: "아까 놓은 횃불" for the anchor {@code @s2}. */
		default Placed placedBy(String stepId) {
			return null;
		}

		/** Whether an earlier step already builds the structure; its key is {@code SpatialSentences.builtKey}. */
		default boolean builtEarlier(String key) {
			return false;
		}

		/** Name of an item (for acquire and consume goals); defaults to the block lookup. */
		default String item(String id) {
			return block(id);
		}
	}

	public static String normal(Goal goal, Word word, List<Condition> conditions, int koreanLevel) {
		return normal(goal, word, conditions, koreanLevel, false, Namer.NONE);
	}

	public static String normal(Goal goal, Word word, List<Condition> conditions, int koreanLevel,
								boolean finale) {
		return normal(goal, word, conditions, koreanLevel, finale, Namer.NONE);
	}

	public static String normal(Goal goal, Word word, List<Condition> conditions, int koreanLevel,
								boolean finale, Namer namer) {
		return prefix(conditions, word, namer, clampLevel(koreanLevel))
				+ core(goal, word, true, clampLevel(koreanLevel), finale, namer);
	}

	public static String normal(Goal goal, Word word, List<Condition> conditions) {
		return normal(goal, word, conditions, 1);
	}

	/** The hint always stays at the easiest level. */
	public static String hint(Goal goal, Word word) {
		return hint(goal, word, List.of(), Namer.NONE);
	}

	public static String hint(Goal goal, Word word, List<Condition> conditions, Namer namer) {
		return prefix(conditions, word, namer, 1) + core(goal, word, false, 1, false, namer);
	}

	public static long seconds(long dwellMs) {
		return Math.max(1L, (dwellMs + 999L) / 1000L);
	}

	private static int clampLevel(int level) {
		return Math.max(1, Math.min(6, level));
	}

	/** Verb for a goal, from data/verbs.json (explicit {@code verb} on a break goal wins). */
	public static Verb verbFor(Goal goal) {
		return GoalTypes.of(goal).verb(goal, VerbTable.shared());
	}

	private static String core(Goal goal, Word word, boolean withCounts, int level, boolean finale,
							   Namer namer) {
		GoalType<Goal> type = GoalTypes.of(goal);
		return type.korean(goal, context(goal, word, namer, withCounts, level, finale));
	}

	private static SentenceContext context(Goal goal, Word word, Namer namer, boolean withCounts, int level,
										   boolean finale) {
		GoalType<Goal> type = GoalTypes.of(goal);
		Word named = word == null || isVerbWord(word) ? null : word;
		return new SentenceContext(objectName(goal, word, namer), type.verb(goal, VerbTable.shared()),
				withCounts, level, finale, named, SentenceContext.taught(named, namer));
	}

	/**
	 * The finale line the player reads when a trail is done: what the last step did, in the past
	 * polite form ("밤에 횃불을 밝혔어요."). Built from the same goal, conditions and verb table as the
	 * clue, so it can never describe something other than what was checked.
	 */
	public static String ceremony(Goal goal, Word word, List<Condition> conditions, Namer namer) {
		GoalType<Goal> type = GoalTypes.of(goal);
		// The clue may point at the porkchop with the word for the pig; the line that says what was done must not.
		Word said = word != null && word.entityId != null && word.iconItem != null ? withoutPicture(word) : word;
		String body = type.ceremony(goal, context(goal, said, namer, true, 1, true));
		return prefix(conditions, said, namer, 1) + body + ".";
	}

	private static Word withoutPicture(Word word) {
		return new Word(word.id, word.ko, word.de, word.untranslated ? "" : word.en, word.deShort, word.category,
				word.tier, word.conceptId, null, word.entityId, word.biomeId, word.descKo, word.origin,
				word.phrase, word.grammar, word.referents, word.glosses);
	}

	// ---- Grammar points: which constructions a generated sentence teaches.

	/** A generated clue and the grammar points it carries, e.g. {@code L2.ege}, {@code L3.gi_jeone}. */
	public record Spoken(String ko, List<String> grammar) {
	}

	/** Same text as {@link #normal(Goal, Word, List, int, boolean, Namer)}, plus its grammar points. */
	public static Spoken spoken(Goal goal, Word word, List<Condition> conditions, int koreanLevel,
								boolean finale, Namer namer) {
		String ko = normal(goal, word, conditions, koreanLevel, finale, namer);
		return new Spoken(ko, grammar(goal, word, conditions, koreanLevel, finale, namer));
	}

	/** The grammar points of the clue: the goal's own, then those of the condition prefixes. */
	public static List<String> grammar(Goal goal, Word word, List<Condition> conditions, int koreanLevel,
									   boolean finale, Namer namer) {
		int level = clampLevel(koreanLevel);
		List<String> ids = new java.util.ArrayList<>(
				GoalTypes.of(goal).grammarIds(goal, context(goal, word, namer, true, level, finale)));
		if (conditions != null) {
			for (Condition condition : conditions) {
				ids.addAll(ConditionTypes.of(condition).grammarIds(condition, level));
			}
		}
		return ids.stream().distinct().toList();
	}

	/** The ending of the level and the marker of the object: what every sentence has. */
	public static List<String> baseGrammar(Verb verb, int level, ObjectKind kind) {
		List<String> ids = new java.util.ArrayList<>(3);
		ids.add(Ladder.shared().rung(clampLevel(level)).grammar());
		if (kind == ObjectKind.BIOME || kind == ObjectKind.DIMENSION) {
			ids.add("L1.euro_direction");
		} else if (kind != ObjectKind.NONE) {
			ids.add(switch (verb.frame()) {
				case ACC -> "L1.eul_reul";
				case COM -> "L2.wa_gwa_with";
				case DAT -> "L2.ege";
				case LOC -> "L2.eseo_activity";
			});
		}
		return ids;
	}

	/**
	 * Anchor noun + location noun + particle: "문 옆에" (the thing goes there), "횃불 아래에서" (the
	 * activity happens there). The particle comes from the role the place word plays in the task.
	 */
	public static String relationPhrase(String anchorNoun, Relation relation, Role role) {
		return anchorNoun + " " + relation.ko() + role.particle();
	}

	/** Frame-aware object marker: 돌을, 주민과, 소에게, 침대에서. */
	public static String objectPhrase(String obj, Verb verb) {
		return switch (verb.frame()) {
			case ACC -> KoGrammar.eul(obj);
			case COM -> KoGrammar.waGwa(obj);
			case DAT -> obj + "에게";
			case LOC -> obj + "에서";
		};
	}

	/**
	 * The noun the player sees: the taught word when it stands for the goal's object (it names it, or is
	 * drawn with it), else the registry name. A word that covers another role of the goal (the anchor, the
	 * recipient) is spoken in that role by the goal type.
	 */
	private static String objectName(Goal goal, Word word, Namer namer) {
		GoalType<Goal> type = GoalTypes.of(goal);
		// A verb concept (보다, 걷다, 짓다) can be taught by a step but is never the object of one.
		if (word != null && word.ko != null && !word.ko.isBlank() && !isVerbWord(word)) {
			ObjectKind kind = type.objectKind();
			String id = type.object(goal);
			boolean typed = kind == ObjectKind.BLOCK || kind == ObjectKind.ITEM || kind == ObjectKind.ENTITY;
			if (!typed || id == null || SentenceContext.wordCovers(word, kind, id)) {
				return word.ko;
			}
			String registry = type.objectName(goal, namer);
			return registry == null || registry.isBlank() ? word.ko : registry;
		}
		String resolved = type.objectName(goal, namer);
		return resolved == null ? "" : resolved;
	}

	private static boolean isVerbWord(Word word) {
		return word.category == Category.ACTIONS && word.ko.endsWith("다");
	}

	public static String dimensionName(String id, String fallback) {
		String name = dimension(id);
		return name == null ? fallback : name;
	}

	/**
	 * The verb with the instruction ending of the level, from the ladder (ladder.json): -아/어요, then
	 * -(으)세요, then -아/어야 해요. {@code forced} says the sentence states a forced conclusion, where the
	 * top level may use -(으)ㄹ 수밖에 없어요; any other sentence keeps the plain instruction.
	 */
	static String verbPhrase(Verb verb, int level, boolean forced) {
		return verb.prefix() + Endings.apply(Ladder.shared().rung(clampLevel(level)).endingFor(forced),
				verb.dictionary());
	}

	public static String phrase(String head, Verb verb, int level, boolean finale) {
		return head + " " + verbPhrase(verb, level, false) + ".";
	}

	/** Same, for a sentence that states a forced conclusion ("…, so only … is left"). */
	public static String forcedPhrase(String head, Verb verb, int level) {
		return head + " " + verbPhrase(verb, level, true) + ".";
	}

	public static String counted(String head, int count, boolean withCounts) {
		return withCounts && count > 1 ? head + " " + counter(count, Counters.DEFAULT) : head;
	}

	/** Same, with the counter that fits the thing: 종이 세 장, 꽃 두 송이, 책 한 권 (counters.json). */
	public static String counted(String head, int count, boolean withCounts, String id, String noun) {
		return withCounts && count > 1 ? head + " " + counter(count, Counters.shared().counter(id, noun)) : head;
	}

	public static String counter(int count, String counter) {
		return KoGrammar.counter(count, counter);
	}

	private static String prefix(List<Condition> conditions, Word word, Namer namer, int level) {
		if (conditions == null || conditions.isEmpty()) {
			return "";
		}
		Namer taught = SentenceContext.taught(word == null || isVerbWord(word) ? null : word, namer);
		StringBuilder sb = new StringBuilder();
		for (Condition condition : conditions) {
			sb.append(ConditionTypes.of(condition).prefix(condition, taught, level));
		}
		return sb.toString();
	}

	/** The phrases the conditions put in front of the goal ("밤에", "해변에서"), one per condition that has one. */
	public static List<String> conditionPhrases(List<Condition> conditions, Word word, Namer namer, int level) {
		List<String> phrases = new java.util.ArrayList<>();
		if (conditions != null) {
			Namer taught = SentenceContext.taught(word == null || isVerbWord(word) ? null : word, namer);
			for (Condition condition : conditions) {
				String phrase = ConditionTypes.of(condition).prefix(condition, taught, clampLevel(level)).trim();
				if (!phrase.isEmpty()) {
					phrases.add(phrase);
				}
			}
		}
		return phrases;
	}

	/** The condition phrases an authored clue does not contain yet. */
	public static List<String> missingPhrases(String clue, List<Condition> conditions, Word word, Namer namer,
											  int level) {
		return conditionPhrases(conditions, word, namer, level).stream().filter(phrase -> !clue.contains(phrase)).toList();
	}

	/**
	 * An authored clue on a step with conditions: the text stays as written, and a condition it does not
	 * speak is put in front, so every condition is always said.
	 */
	public static String authored(String clue, List<Condition> conditions, Word word, Namer namer, int level) {
		StringBuilder front = new StringBuilder();
		for (String phrase : missingPhrases(clue, conditions, word, namer, level)) {
			front.append(phrase).append(' ');
		}
		return front + clue;
	}

	/** "곰 근처에서 ", or {@code fallback} when the thing has no name. */
	public static String nearName(String noun, String fallback) {
		if (noun == null || noun.isBlank()) {
			return fallback;
		}
		return noun + (fallback.startsWith("가까이") ? " 근처에서 " : "에서 ");
	}

	/** The three dimensions by their official Korean names. */
	public static String dimension(String id) {
		if (id == null) {
			return null;
		}
		return switch (id) {
			case "minecraft:the_nether" -> "네더";
			case "minecraft:the_end" -> "엔드";
			case "minecraft:overworld" -> "오버월드";
			default -> null;
		};
	}

	public static String biomeName(String id, Namer namer) {
		if (id == null) {
			return null;
		}
		if (id.startsWith("#")) {
			return switch (id) {
				case "#minecraft:is_beach" -> "해변";
				case "#minecraft:is_ocean" -> "바다";
				case "#minecraft:is_forest" -> "숲";
				case "#minecraft:is_mountain" -> "산";
				case "#minecraft:is_taiga" -> "타이가";
				case "#minecraft:is_jungle" -> "정글";
				case "#minecraft:is_badlands" -> "메사";
				case "#minecraft:is_savanna" -> "사바나";
				case "#minecraft:is_river" -> "강";
				default -> null;
			};
		}
		return namer.biome(id);
	}
}
