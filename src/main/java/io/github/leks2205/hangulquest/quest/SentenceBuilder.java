package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.condition.ConditionType;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ObjectKind;
import io.github.leks2205.hangulquest.quest.goal.SentenceContext;
import io.github.leks2205.hangulquest.quest.goal.Tiers;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator;
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

		/**
		 * The hand-made concept that names exactly this item (눈 블록 for the snow block), or null when only a
		 * generated name or none does. A word that merely covers or resembles the item does not name it then.
		 */
		default String owner(String id) {
			return null;
		}

		/** The namer without the words the step teaches: what the registry calls things (꽃 stands for the poppy). */
		default Namer untaught() {
			return this;
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
		return normal(goal, word, verbWord(word), conditions, koreanLevel, finale, namer);
	}

	/**
	 * Same, for a step that also teaches a verb ({@code verbWord}): when the goal can say that verb (요리하다 for a
	 * food in the furnace, 타다 for a boat) the sentence uses it instead of its default.
	 */
	public static String normal(Goal goal, Word word, Word verbWord, List<Condition> conditions, int koreanLevel,
								boolean finale, Namer namer) {
		int level = clampLevel(koreanLevel);
		return attach(prefix(goal, conditions, word, namer, level), core(goal, word, verbWord, conditions, true, level,
				finale, namer), level);
	}

	public static String normal(Goal goal, Word word, List<Condition> conditions) {
		return normal(goal, word, conditions, 1);
	}

	/** The hint always stays at the easiest level. */
	public static String hint(Goal goal, Word word) {
		return hint(goal, word, List.of(), Namer.NONE);
	}

	public static String hint(Goal goal, Word word, List<Condition> conditions, Namer namer) {
		return hint(goal, word, verbWord(word), conditions, namer);
	}

	public static String hint(Goal goal, Word word, Word verbWord, List<Condition> conditions, Namer namer) {
		Goal shown = GoalTypes.of(goal).forHint(goal);
		return attach(prefix(shown, conditions, word, namer, 1), core(shown, word, verbWord, conditions, false, 1, false,
				namer), 1);
	}

	/**
	 * The hint of a step that teaches several nouns: every noun after the first names the ids it stands for
	 * too (the anchor, the recipient, the container), so the hint says all of them without an authored text.
	 */
	public static String hint(Goal goal, Word word, Word verbWord, List<Word> more, List<Condition> conditions,
							  Namer namer) {
		Namer all = namer;
		for (Word extra : more) {
			if (extra != null && extra != word && !isVerbWord(extra)) {
				all = SentenceContext.taught(extra, all);
			}
		}
		return hint(goal, word, verbWord, conditions, all);
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

	private static String core(Goal goal, Word word, Word verbWord, List<Condition> conditions, boolean withCounts,
							   int level, boolean finale, Namer namer) {
		GoalType<Goal> type = GoalTypes.of(goal);
		return type.korean(goal, context(goal, word, verbWord, conditions, namer, withCounts, level, finale));
	}

	private static SentenceContext context(Goal goal, Word word, Word verbWord, List<Condition> conditions,
										   Namer namer, boolean withCounts, int level, boolean finale) {
		Word named = word == null || isVerbWord(word) ? null : word;
		return new SentenceContext(objectName(goal, word, namer), verbOf(goal, verbWord, conditions),
				withCounts, level, finale, named, SentenceContext.taught(named, namer));
	}

	/**
	 * Whether a clue says the word a step teaches: a noun as it is, a verb in any conjugated form (올라가요 for
	 * 올라가다, 구워요 for 굽다), because a clue never holds the dictionary form of its verb.
	 */
	public static boolean mentions(String clue, Word word) {
		if (clue == null || word == null || word.ko == null || word.ko.isBlank()) {
			return false;
		}
		return clue.contains(word.ko) || word.ko.endsWith("다") && Conjugator.contains(clue, word.ko);
	}

	/** The verb word among what a step teaches: the word itself when it is one, else null. */
	private static Word verbWord(Word word) {
		return word != null && isVerbWord(word) ? word : null;
	}

	/**
	 * The verb the sentence uses: the goal's own under the conditions of the step, unless the step teaches one
	 * of the verbs the goal can say instead.
	 */
	public static Verb verbOf(Goal goal, Word verbWord, List<Condition> conditions) {
		GoalType<Goal> type = GoalTypes.of(goal);
		if (verbWord != null) {
			for (Verb alternative : type.alternativeVerbs(goal)) {
				if (alternative.dictionary().equals(verbWord.ko)) {
					return alternative;
				}
			}
		}
		return type.verb(goal, VerbTable.shared(), conditions == null ? List.of() : conditions);
	}

	/**
	 * The finale line the player reads when a trail is done: what the last step did, in the past
	 * polite form ("밤에 횃불을 밝혔어요."). Built from the same goal, conditions and verb table as the
	 * clue, so it can never describe something other than what was checked.
	 */
	public static String ceremony(Goal goal, Word word, List<Condition> conditions, Namer namer) {
		return ceremony(goal, word, verbWord(word), conditions, namer);
	}

	public static String ceremony(Goal goal, Word word, Word verbWord, List<Condition> conditions, Namer namer) {
		GoalType<Goal> type = GoalTypes.of(goal);
		// The clue may point at the porkchop with the word for the pig; the line that says what was done must not.
		Word said = word != null && word.entityId != null && word.iconItem != null ? withoutPicture(word) : word;
		String body = type.ceremony(goal, context(goal, said, verbWord, conditions, namer, true, 1, true));
		return attach(prefix(goal, conditions, said, namer, 1), body, 1) + ".";
	}

	private static Word withoutPicture(Word word) {
		return new Word(word.id, word.ko, word.de, word.untranslated ? "" : word.en, word.deShort, word.category,
				word.tier, word.conceptId, null, word.entityId, word.biomeId, word.descKo, word.origin,
				word.phrase, word.grammar, word.referents, word.glosses, word.covers);
	}

	// ---- Grammar points: which constructions a generated sentence teaches.

	/** A generated clue and the grammar points it carries, e.g. {@code L2.ege}, {@code L3.gi_jeone}. */
	public record Spoken(String ko, List<String> grammar) {
	}

	/** Same text as {@link #normal(Goal, Word, List, int, boolean, Namer)}, plus its grammar points. */
	public static Spoken spoken(Goal goal, Word word, List<Condition> conditions, int koreanLevel,
								boolean finale, Namer namer) {
		return spoken(goal, word, verbWord(word), conditions, koreanLevel, finale, namer);
	}

	public static Spoken spoken(Goal goal, Word word, Word verbWord, List<Condition> conditions, int koreanLevel,
								boolean finale, Namer namer) {
		String ko = normal(goal, word, verbWord, conditions, koreanLevel, finale, namer);
		return new Spoken(ko, grammar(goal, word, verbWord, conditions, koreanLevel, finale, namer));
	}

	/** The grammar points of the clue: the goal's own, then those of the condition prefixes. */
	public static List<String> grammar(Goal goal, Word word, List<Condition> conditions, int koreanLevel,
									   boolean finale, Namer namer) {
		return grammar(goal, word, verbWord(word), conditions, koreanLevel, finale, namer);
	}

	public static List<String> grammar(Goal goal, Word word, Word verbWord, List<Condition> conditions,
									   int koreanLevel, boolean finale, Namer namer) {
		int level = clampLevel(koreanLevel);
		GoalType<Goal> type = GoalTypes.of(goal);
		List<String> ids = new java.util.ArrayList<>(
				type.grammarIds(goal, context(goal, word, verbWord, conditions, namer, true, level, finale)));
		if (conditions != null) {
			for (Condition condition : conditions) {
				if (!type.says(goal, condition)) {
					ids.addAll(type.takesOver(goal, condition)
							? ConditionTypes.of(condition).targetGrammarIds(condition, level)
							: ConditionTypes.of(condition).grammarIds(condition, level));
				}
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
				case IN -> "L1.e_place";
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
			case IN -> obj + "에";
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
			// A biome is typed too: a step that teaches 개구리 on a swamp still says 늪.
			boolean typed = kind == ObjectKind.BLOCK || kind == ObjectKind.ITEM || kind == ObjectKind.ENTITY
					|| kind == ObjectKind.BIOME;
			if (!typed || id == null || SentenceContext.wordCovers(word, kind, id, namer)) {
				return Tiers.qualify(id, word.ko);
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

	/**
	 * The phrases the conditions put in front of a sentence, in order. A phrase that says a place with 에서 also
	 * keeps the place noun ("높이 90블록 위"), so a body that starts with a place of its own can take it as a
	 * modifier instead of doubling the 에서 ({@link #attach}).
	 */
	record Front(List<String> phrases, List<String> places) {
		static final Front NONE = new Front(List.of(), List.of());

		String text() {
			return String.join("", phrases);
		}
	}

	/** What a request to till says before its object (see UseBlockType): the tool that tells 갈다 from 가다. */
	private static final String TILL_TOOL = "괭이로 ";

	/** Words that open a body with 에서 although they are no place a modifier could stand in front of. */
	private static final java.util.Set<String> ADVERB_PLACES = java.util.Set.of("밖에서", "안에서", "물속에서",
			"땅속에서", "여기에서", "그곳에서", "거기에서");

	/**
	 * The front and the body as one sentence. When the last place phrase of the front meets a body that starts
	 * with a place of its own, the two 에서 would double ("높이 90블록 위에서 침대에서 자요"): the first becomes a
	 * modifier of the second, bare at levels 1-2 and with 의 from level 3 ("높이 90블록 위 침대에서",
	 * "높이 90블록 위의 침대에서"), and moves right in front of it.
	 */
	static String attach(Front front, String body, int level) {
		body = withoutRepeatedPlace(front, body);
		if (body.startsWith(TILL_TOOL) && front.text().contains("괭이를 들고")) {
			body = body.substring(TILL_TOOL.length()); // "나무 괭이를 들고 흙을 가세요": the hoe is already in the hand
		}
		int merge = -1;
		String first = body.contains(" ") ? body.substring(0, body.indexOf(' ')) : body;
		if (!ADVERB_PLACES.contains(first)) {
			String head = openingPlace(body);
			if (head != null) {
				for (int i = front.places().size() - 1; i >= 0; i--) {
					if (front.places().get(i) != null) {
						merge = i;
						break;
					}
				}
			}
		}
		if (merge < 0) {
			return front.text() + body;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < front.phrases().size(); i++) {
			if (i != merge) {
				sb.append(front.phrases().get(i));
			}
		}
		return sb + front.places().get(merge) + (level >= 3 ? "의 " : " ") + body;
	}

	/**
	 * A body that opens with the place the front already stands at drops it: "제작대 옆에서" in front of "제작대에서
	 * 철 곡괭이를 만드세요" is one phrase, "제작대 옆에서 철 곡괭이를 만드세요", not the station named twice.
	 */
	private static String withoutRepeatedPlace(Front front, String body) {
		String place = openingPlace(body);
		if (place == null) {
			return body;
		}
		for (String phrase : front.phrases()) {
			String said = phrase.strip();
			if (said.startsWith(place + " ") && said.endsWith("에서")) {
				return body.substring(place.length() + "에서 ".length());
			}
		}
		return body;
	}

	/** The place a body opens with ("침대에서", "석재 절단기에서"), or null: the words up to the first one ending in 에서. */
	private static String openingPlace(String body) {
		int cut = body.indexOf("에서 ");
		if (cut <= 0) {
			return null;
		}
		String head = body.substring(0, cut);
		// only a noun phrase: no object, topic or verb before the 에서 ("횃불을 들고 물속에서" is not one)
		for (String word : head.split(" ")) {
			if (word.matches(".*[을를은는이가고서며도만]$")) {
				return null;
			}
		}
		return head;
	}

	private static Front prefix(Goal goal, List<Condition> conditions, Word word, Namer namer, int level) {
		if (conditions == null || conditions.isEmpty()) {
			return Front.NONE;
		}
		GoalType<Goal> type = GoalTypes.of(goal);
		Namer taught = SentenceContext.taught(word == null || isVerbWord(word) ? null : word, namer);
		List<String> phrases = new java.util.ArrayList<>();
		List<String> places = new java.util.ArrayList<>();
		for (Condition condition : conditions) {
			if (!type.says(goal, condition)) { // 바다에서 수영해요 already says IN_WATER
				ConditionType<Condition> conditionType = ConditionTypes.of(condition);
				phrases.add(phraseOf(goal, condition, taught, level));
				places.add(conditionType.placeNoun(condition, taught));
			}
		}
		return new Front(phrases, places);
	}

	/**
	 * The phrase of one condition. A goal that checks the condition on its target (a block placed under a roof)
	 * names that target's place instead of the player's.
	 */
	private static String phraseOf(Goal goal, Condition condition, Namer taught, int level) {
		ConditionType<Condition> type = ConditionTypes.of(condition);
		return goal != null && GoalTypes.of(goal).takesOver(goal, condition)
				? type.targetPrefix(condition, taught, level) : type.prefix(condition, taught, level);
	}

	/** The phrases the conditions put in front of the goal ("밤에", "해변에서"), one per condition that has one. */
	public static List<String> conditionPhrases(List<Condition> conditions, Word word, Namer namer, int level) {
		return conditionPhrases(null, conditions, word, namer, level);
	}

	/** Same, without the conditions the sentence of {@code goal} says by itself (null: every condition). */
	public static List<String> conditionPhrases(Goal goal, List<Condition> conditions, Word word, Namer namer,
												int level) {
		List<String> phrases = new java.util.ArrayList<>();
		if (conditions != null) {
			Namer taught = SentenceContext.taught(word == null || isVerbWord(word) ? null : word, namer);
			GoalType<Goal> type = goal == null ? null : GoalTypes.of(goal);
			for (Condition condition : conditions) {
				if (type != null && type.says(goal, condition)) {
					continue;
				}
				String phrase = phraseOf(goal, condition, taught, clampLevel(level)).trim();
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
		return missingPhrases(null, clue, conditions, word, namer, level);
	}

	/**
	 * Same for the conditions of a step: a clue that says a condition in its own words (the keywords of
	 * {@link ConditionType#words}: 밤, 비, 물속) or that the goal says by itself does not get the phrase again.
	 */
	public static List<String> missingPhrases(Goal goal, String clue, List<Condition> conditions, Word word,
											  Namer namer, int level) {
		List<String> missing = new java.util.ArrayList<>();
		if (conditions == null) {
			return missing;
		}
		Namer taught = SentenceContext.taught(word == null || isVerbWord(word) ? null : word, namer);
		GoalType<Goal> type = goal == null ? null : GoalTypes.of(goal);
		for (Condition condition : conditions) {
			if (type != null && type.says(goal, condition)) {
				continue;
			}
			ConditionType<Condition> conditionType = ConditionTypes.of(condition);
			String phrase = phraseOf(goal, condition, taught, clampLevel(level)).trim();
			if (phrase.isEmpty() || clue.contains(phrase)) {
				continue;
			}
			if (conditionType.words(condition, taught).stream().anyMatch(clue::contains)) {
				continue;
			}
			String place = conditionType.placeNoun(condition, taught);
			if (place != null && (clue.contains(place + " ") || clue.contains(place + "의 "))) {
				continue; // "높이 90블록 위의 침대에서 자요" says it as a modifier
			}
			missing.add(phrase);
		}
		return missing;
	}

	/**
	 * An authored clue on a step with conditions: the text stays as written, and a condition it does not
	 * speak is put in front, so every condition is always said.
	 */
	public static String authored(String clue, List<Condition> conditions, Word word, Namer namer, int level) {
		return authored(null, clue, conditions, word, namer, level);
	}

	public static String authored(Goal goal, String clue, List<Condition> conditions, Word word, Namer namer,
								  int level) {
		List<String> phrases = new java.util.ArrayList<>();
		List<String> places = new java.util.ArrayList<>();
		Namer taught = SentenceContext.taught(word == null || isVerbWord(word) ? null : word, namer);
		for (String phrase : missingPhrases(goal, clue, conditions, word, namer, level)) {
			phrases.add(phrase + ' ');
			String place = null;
			for (Condition condition : conditions) {
				ConditionType<Condition> type = ConditionTypes.of(condition);
				if (phraseOf(goal, condition, taught, clampLevel(level)).trim().equals(phrase)) {
					place = type.placeNoun(condition, taught);
				}
			}
			places.add(place);
		}
		return attach(new Front(phrases, places), clue, clampLevel(level));
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
