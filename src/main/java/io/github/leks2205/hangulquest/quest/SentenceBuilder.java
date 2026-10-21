package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.NamerAware;
import io.github.leks2205.hangulquest.quest.goal.SentenceContext;
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
		return prefix(conditions, namer) + core(goal, word, true, clampLevel(koreanLevel), finale, namer);
	}

	public static String normal(Goal goal, Word word, List<Condition> conditions) {
		return normal(goal, word, conditions, 1);
	}

	/** The hint always stays at the easiest level. */
	public static String hint(Goal goal, Word word) {
		return hint(goal, word, List.of(), Namer.NONE);
	}

	public static String hint(Goal goal, Word word, List<Condition> conditions, Namer namer) {
		return prefix(conditions, namer) + core(goal, word, false, 1, false, namer);
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
		SentenceContext context = new SentenceContext(objectName(goal, word, namer),
				type.verb(goal, VerbTable.shared()), withCounts, level, finale);
		return type instanceof NamerAware<?> aware ? asGoal(aware).korean(goal, context, namer)
				: type.korean(goal, context);
	}

	@SuppressWarnings("unchecked")
	private static NamerAware<Goal> asGoal(NamerAware<?> aware) {
		return (NamerAware<Goal>) aware;
	}

	/**
	 * The finale line the player reads when a trail is done: what the last step did, in the past
	 * polite form ("밤에 횃불을 밝혔어요."). Built from the same goal, conditions and verb table as the
	 * clue, so it can never describe something other than what was checked.
	 */
	public static String ceremony(Goal goal, Word word, List<Condition> conditions, Namer namer) {
		GoalType<Goal> type = GoalTypes.of(goal);
		String obj = objectName(goal, word, namer);
		Verb verb = type.verb(goal, VerbTable.shared());
		String body = type instanceof NamerAware<?> aware ? asGoal(aware).ceremony(goal, obj, verb, namer)
				: type.ceremony(goal, obj, verb);
		return prefix(conditions, namer) + body + ".";
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

	/** The noun the player sees: the taught word, or the registry name when a step teaches nothing. */
	private static String objectName(Goal goal, Word word, Namer namer) {
		// A verb concept (보다, 걷다, 짓다) can be taught by a step but is never the object of one.
		if (word != null && word.ko != null && !word.ko.isBlank() && !isVerbWord(word)) {
			return word.ko;
		}
		String resolved = GoalTypes.of(goal).objectName(goal, namer);
		return resolved == null ? "" : resolved;
	}

	private static boolean isVerbWord(Word word) {
		return word.category == Category.ACTIONS && word.ko.endsWith("다");
	}

	public static String dimensionName(String id, String fallback) {
		String name = dimension(id);
		return name == null ? fallback : name;
	}

	/** Level 1 polite, 2 must, 3 emphatic only-if, 4 double negative, 5 literary, 6 resigned. */
	static String verbPhrase(Verb verb, int level, boolean finale) {
		return verb.prefix() + switch (level) {
			case 2 -> verb.eoyo() + "야 해요";
			case 3 -> verb.eoyo() + "야만 해요";
			case 4 -> verb.stem() + "지 않으면 안 돼요";
			case 5 -> verb.stem() + "지 않고서는 "
					+ (finale ? "이 길을 끝낼 수 없어요" : "다음 단서를 찾을 수 없어요");
			case 6 -> verb.future() + " 수밖에 없어요";
			default -> verb.polite();
		};
	}

	public static String phrase(String head, Verb verb, int level, boolean finale) {
		return head + " " + verbPhrase(verb, level, finale) + ".";
	}

	public static String counted(String head, int count, boolean withCounts) {
		return withCounts && count > 1 ? head + " " + counter(count, "개") : head;
	}

	public static String counter(int count, String counter) {
		return KoGrammar.counter(count, counter);
	}

	private static String prefix(List<Condition> conditions, Namer namer) {
		if (conditions == null || conditions.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Condition condition : conditions) {
			sb.append(ConditionTypes.of(condition).prefix(condition, namer));
		}
		return sb.toString();
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
