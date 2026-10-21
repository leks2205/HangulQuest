package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;

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

	private static long seconds(long dwellMs) {
		return Math.max(1L, (dwellMs + 999L) / 1000L);
	}

	private static int clampLevel(int level) {
		return Math.max(1, Math.min(6, level));
	}

	/** Verb for a goal, from data/verbs.json (explicit {@code verb} on a break goal wins). */
	public static Verb verbFor(Goal goal) {
		VerbTable table = VerbTable.shared();
		return switch (goal) {
			case Goal.BreakBlock g -> Verb.byId(g.verb(),
					table.choose("BREAK_BLOCK", g.block(), g.count(), Verb.BREAK));
			case Goal.PlaceBlock g -> table.choose("PLACE_BLOCK", g.block(), g.count(), Verb.PLACE);
			case Goal.AcquireItem g -> table.choose("ACQUIRE_ITEM", g.item(), g.count(), Verb.COLLECT);
			case Goal.ConsumeItem g -> table.choose("CONSUME_ITEM", g.item(), g.count(), Verb.EAT);
			case Goal.LookAtBlock g -> Verb.WATCH;
			case Goal.LookAtEntity g -> Verb.WATCH;
			case Goal.DefeatEntity g -> Verb.DEFEAT;
			case Goal.ReachBiome g -> Verb.GO;
			case Goal.ReachDimension g -> Verb.GO;
			case Goal.ReachHeight g -> g.minY() > 0 || g.maxY() > 0 ? Verb.CLIMB : Verb.DESCEND;
			case Goal.UseBlock g -> table.choose("USE_BLOCK", g.block(), 1, Verb.USE);
			case Goal.InteractEntity g -> table.choose("INTERACT_ENTITY", g.entity(), 1, Verb.FEED);
		};
	}

	private static String core(Goal goal, Word word, boolean withCounts, int level, boolean finale,
							   Namer namer) {
		String obj = objectName(goal, word, namer);
		Verb verb = verbFor(goal);
		return switch (goal) {
			case Goal.BreakBlock g -> phrase(
					counted(objectPhrase(obj, verb), g.count(), withCounts), verb, level, finale);
			case Goal.PlaceBlock g -> phrase(
					counted(objectPhrase(obj, verb), g.count(), withCounts), verb, level, finale);
			case Goal.AcquireItem g -> phrase(
					counted(objectPhrase(obj, verb), g.count(), withCounts), verb, level, finale);
			case Goal.ConsumeItem g -> phrase(
					counted(objectPhrase(obj, verb), g.count(), withCounts), verb, level, finale);
			case Goal.LookAtBlock g -> phrase(KoGrammar.eul(obj) + " " + seconds(g.dwellMs())
					+ "초 동안", verb, level, finale);
			case Goal.LookAtEntity g -> phrase(KoGrammar.eul(obj) + " " + seconds(g.dwellMs())
					+ "초 동안", verb, level, finale);
			case Goal.DefeatEntity g -> phrase(
					withCounts ? KoGrammar.eul(obj) + " " + counter(g.count(), "마리") : KoGrammar.eul(obj),
					verb, level, finale);
			case Goal.ReachBiome g -> phrase(KoGrammar.euro(obj), verb, level, finale);
			case Goal.ReachDimension g -> phrase(KoGrammar.euro(dimensionName(g.dimension(), obj)),
					verb, level, finale);
			case Goal.ReachHeight g -> {
				if (g.minY() > 0) {
					yield phrase("높이 " + g.minY() + "블록 위로", verb, level, finale);
				}
				if (g.maxY() <= 0) {
					yield phrase("높이 " + g.maxY() + " 이하로", verb, level, finale);
				}
				if (g.minY() <= 0) {
					yield phrase("땅 위로", Verb.CLIMB, level, finale);
				}
				yield phrase("땅속으로", Verb.DESCEND, level, finale);
			}
			case Goal.UseBlock g -> phrase(objectPhrase(obj, verb), verb, level, finale);
			case Goal.InteractEntity g -> phrase(objectPhrase(obj, verb), verb, level, finale);
		};
	}

	/**
	 * The finale line the player reads when a trail is done: what the last step did, in the past
	 * polite form ("밤에 횃불을 밝혔어요."). Built from the same goal, conditions and verb table as the
	 * clue, so it can never describe something other than what was checked.
	 */
	public static String ceremony(Goal goal, Word word, List<Condition> conditions, Namer namer) {
		String obj = objectName(goal, word, namer);
		Verb verb = verbFor(goal);
		String body = switch (goal) {
			case Goal.BreakBlock g -> counted(objectPhrase(obj, verb), g.count(), true) + " " + verb.past();
			case Goal.PlaceBlock g -> counted(objectPhrase(obj, verb), g.count(), true) + " " + verb.past();
			case Goal.AcquireItem g -> counted(objectPhrase(obj, verb), g.count(), true) + " " + verb.past();
			case Goal.ConsumeItem g -> counted(objectPhrase(obj, verb), g.count(), true) + " " + verb.past();
			case Goal.LookAtBlock g -> KoGrammar.eul(obj) + " " + verb.past();
			case Goal.LookAtEntity g -> KoGrammar.eul(obj) + " " + verb.past();
			case Goal.DefeatEntity g -> KoGrammar.eul(obj) + " "
					+ (g.count() > 1 ? counter(g.count(), "마리") + " " : "") + verb.past();
			case Goal.ReachBiome g -> obj + "에 " + Verb.ARRIVE.past();
			case Goal.ReachDimension g -> dimensionName(g.dimension(), obj) + "에 " + Verb.ARRIVE.past();
			case Goal.ReachHeight g -> {
				if (g.minY() > 0) {
					yield "높이 " + g.minY() + "블록 위로 " + Verb.CLIMB.past();
				}
				if (g.maxY() <= 0) {
					yield "높이 " + g.maxY() + " 이하로 " + Verb.DESCEND.past();
				}
				yield g.minY() <= 0 ? "땅 위로 " + Verb.CLIMB.past() : "땅속으로 " + Verb.DESCEND.past();
			}
			case Goal.UseBlock g -> objectPhrase(obj, verb) + " " + verb.past();
			case Goal.InteractEntity g -> objectPhrase(obj, verb) + " " + verb.prefix() + verb.past();
		};
		return prefix(conditions, namer) + body + ".";
	}

	/** Frame-aware object marker: 돌을, 주민과, 소에게, 침대에서. */
	private static String objectPhrase(String obj, Verb verb) {
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
		String resolved = switch (goal) {
			case Goal.BreakBlock g -> namer.block(g.block());
			case Goal.PlaceBlock g -> namer.block(g.block());
			case Goal.AcquireItem g -> namer.item(g.item());
			case Goal.ConsumeItem g -> namer.item(g.item());
			case Goal.LookAtBlock g -> namer.block(g.block());
			case Goal.UseBlock g -> namer.block(g.block());
			case Goal.LookAtEntity g -> namer.entity(g.entity());
			case Goal.DefeatEntity g -> namer.entity(g.entity());
			case Goal.InteractEntity g -> namer.entity(g.entity());
			case Goal.ReachBiome g -> biomeName(g.biome(), namer);
			case Goal.ReachDimension g -> dimension(g.dimension());
			case Goal.ReachHeight g -> null;
		};
		return resolved == null ? "" : resolved;
	}

	private static boolean isVerbWord(Word word) {
		return word.category == Category.ACTIONS && word.ko.endsWith("다");
	}

	private static String dimensionName(String id, String fallback) {
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

	private static String phrase(String head, Verb verb, int level, boolean finale) {
		return head + " " + verbPhrase(verb, level, finale) + ".";
	}

	private static String counted(String head, int count, boolean withCounts) {
		return withCounts && count > 1 ? head + " " + counter(count, "개") : head;
	}

	private static String counter(int count, String counter) {
		return KoGrammar.counter(count, counter);
	}

	private static String prefix(List<Condition> conditions, Namer namer) {
		if (conditions == null || conditions.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Condition condition : conditions) {
			switch (condition) {
				case Condition.Time t -> sb.append(switch (t.phase()) {
					case NIGHT -> "밤에 ";
					case DAY -> "낮에 ";
					case DAWN -> "새벽에 ";
					case DUSK -> "저녁에 ";
				});
				case Condition.WeatherState w -> sb.append(switch (w.weather()) {
					case CLEAR -> "맑을 때 ";
					case RAIN -> "비가 올 때 ";
					case THUNDER -> "천둥이 칠 때 ";
				});
				case Condition.YRange y -> {
					String where = yRange(y);
					if (!where.isEmpty()) {
						sb.append(where).append(' ');
					}
				}
				case Condition.NearEntity n -> sb.append(name(namer.entity(n.entity()), "가까이에서 "));
				case Condition.NearBlock n -> sb.append(name(namer.block(n.block()), "가까이에서 "));
				case Condition.InDimension d -> sb.append(dimensionPhrase(d.dimension()));
				case Condition.InBiome b -> sb.append(biomePhrase(b.biome(), namer));
			}
		}
		return sb.toString();
	}

	private static String yRange(Condition.YRange y) {
		if (y.minY() <= 0 && y.maxY() >= 320) {
			return "";
		}
		if (y.maxY() <= 0) {
			return "높이 " + y.maxY() + " 이하에서";
		}
		if (y.minY() <= 0) {
			return "땅속에서";
		}
		return "높이 " + y.minY() + "블록 위에서";
	}

	private static String name(String noun, String fallback) {
		if (noun == null || noun.isBlank()) {
			return fallback;
		}
		return noun + (fallback.startsWith("가까이") ? " 근처에서 " : "에서 ");
	}

	private static String dimensionPhrase(String id) {
		String name = dimension(id);
		return name == null ? "다른 차원에서 " : name + "에서 ";
	}

	private static String biomePhrase(String id, Namer namer) {
		String name = biomeName(id, namer);
		return name == null ? "그 생물 군계에서 " : name + "에서 ";
	}

	/** The three dimensions by their official Korean names. */
	static String dimension(String id) {
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

	private static String biomeName(String id, Namer namer) {
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
