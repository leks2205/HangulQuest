package io.github.leks2205.hangulquest.quest;

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

	/** Instructions get harder with the trail's Korean level (1–5). */
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
		return prefix(conditions, namer) + core(goal, word, true, clampLevel(koreanLevel), finale);
	}

	public static String normal(Goal goal, Word word, List<Condition> conditions) {
		return normal(goal, word, conditions, 1);
	}

	/** The hint always stays at the easiest level. */
	public static String hint(Goal goal, Word word) {
		return hint(goal, word, List.of(), Namer.NONE);
	}

	public static String hint(Goal goal, Word word, List<Condition> conditions, Namer namer) {
		return prefix(conditions, namer) + core(goal, word, false, 1, false);
	}

	private static long seconds(long dwellMs) {
		return Math.max(1L, (dwellMs + 999L) / 1000L);
	}

	private static int clampLevel(int level) {
		return Math.max(1, Math.min(6, level));
	}

	private static String core(Goal goal, Word word, boolean withCounts, int level, boolean finale) {
		String obj = word == null ? "" : word.ko;
		return switch (goal) {
			case Goal.BreakBlock g -> phrase(
					counted(KoGrammar.eul(obj), g.count(), withCounts),
					Verb.byId(g.verb(), breakVerb(g.block())), level, finale);
			case Goal.PlaceBlock g -> phrase(
					counted(KoGrammar.eul(obj), g.count(), withCounts), Verb.PLACE, level, finale);
			case Goal.AcquireItem g -> phrase(
					counted(KoGrammar.eul(obj), g.count(), withCounts), Verb.COLLECT, level, finale);
			case Goal.ConsumeItem g -> phrase(
					counted(KoGrammar.eul(obj), g.count(), withCounts), Verb.EAT, level, finale);
			case Goal.LookAtBlock g -> phrase(KoGrammar.eul(obj) + " " + seconds(g.dwellMs())
					+ "초 동안", Verb.WATCH, level, finale);
			case Goal.LookAtEntity g -> phrase("살아 있는 " + KoGrammar.eul(obj) + " "
					+ seconds(g.dwellMs()) + "초 동안", Verb.WATCH, level, finale);
			case Goal.DefeatEntity g -> phrase(
					withCounts ? KoGrammar.eul(obj) + " " + counter(g.count(), "마리") : KoGrammar.eul(obj),
					Verb.DEFEAT, level, finale);
			case Goal.ReachBiome g -> phrase(obj + " 지형으로", Verb.GO, level, finale);
			case Goal.ReachDimension g -> phrase(obj + " 차원으로", Verb.GO, level, finale);
			case Goal.ReachHeight g -> {
				if (g.minY() > 0) {
					yield phrase("높이 " + g.minY() + "블록 위로", Verb.CLIMB, level, finale);
				}
				if (g.maxY() <= 0) {
					yield phrase("높이 " + g.maxY() + " 이하로", Verb.DESCEND, level, finale);
				}
				if (g.minY() <= 0) {
					yield phrase("땅 위로", Verb.CLIMB, level, finale);
				}
				yield phrase("땅속으로", Verb.DESCEND, level, finale);
			}
			case Goal.UseBlock g -> useBlockPhrase(obj, g.block(), level, finale);
			case Goal.InteractEntity g -> phrase(KoGrammar.waGwa(obj), Verb.TALK, level, finale);
		};
	}

	/** Level 1 polite, 2 must, 3 emphatic only-if, 4 double negative, 5 literary. */
	static String verbPhrase(Verb verb, int level, boolean finale) {
		return switch (level) {
			case 2 -> verb.eoyo() + "야 해요";
			case 3 -> verb.eoyo() + "야만 해요";
			case 4 -> verb.stem() + "지 않으면 안 돼요";
			case 5 -> verb.stem() + "지 않고서는 "
					+ (finale ? "이 길을 끝낼 수 없어요" : "다음 단서를 찾을 수 없어요");
			case 6 -> eulR(verb.stem()) + " 수밖에 없어요";
			default -> verb.polite();
		};
	}

	/** Appends the object/obligation marker: 먹 → 먹을, 가 → 갈 (ㄹ becomes the final consonant). */
	private static String eulR(String stem) {
		if (stem == null || stem.isEmpty()) {
			return "";
		}
		char last = stem.charAt(stem.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3) {
			return stem + "ㄹ";
		}
		int jong = (last - 0xAC00) % 28;
		if (jong == 8) {
			// ㄹ-final stems already end in ㄹ: 열다 → 열 수밖에, 만들다 → 만들 수밖에.
			return stem;
		}
		if (jong != 0) {
			return stem + "을";
		}
		return stem.substring(0, stem.length() - 1) + (char) (last + 8);
	}

	private static boolean hasBatchim(String stem) {
		if (stem == null || stem.isEmpty()) {
			return false;
		}
		char last = stem.charAt(stem.length() - 1);
		return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
	}

	private static String phrase(String head, Verb verb, int level, boolean finale) {
		return head + " " + verbPhrase(verb, level, finale) + ".";
	}

	private static String counted(String head, int count, boolean withCounts) {
		return withCounts && count > 1 ? head + " " + counter(count, "개") : head;
	}

	private static String useBlockPhrase(String obj, String block, int level, boolean finale) {
		if (block != null && block.contains("bell")) {
			return phrase(KoGrammar.eul(obj), Verb.RING, level, finale);
		}
		if (block != null && block.contains("bed")) {
			return phrase(obj + "에서", Verb.SLEEP, level, finale);
		}
		if (block != null && (block.contains("chest") || block.contains("barrel"))) {
			return phrase(KoGrammar.eul(obj), Verb.OPEN, level, finale);
		}
		return phrase(KoGrammar.eul(obj), Verb.USE, level, finale);
	}

	private static String counter(int count, String counter) {
		return KoGrammar.counter(count, counter);
	}

	private static Verb breakVerb(String block) {
		if (block == null) {
			return Verb.MINE;
		}
		if (block.endsWith("_log") || block.endsWith("_wood") || block.endsWith("_stem")) {
			return Verb.CHOP;
		}
		if (block.contains("wheat") || block.contains("carrot") || block.contains("potato")
				|| block.contains("beetroot") || block.contains("pumpkin") || block.contains("melon")) {
			return Verb.HARVEST;
		}
		return Verb.MINE;
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
		return name == null ? "다른 차원에서 " : name + " 차원에서 ";
	}

	private static String biomePhrase(String id, Namer namer) {
		String name = biomeName(id, namer);
		return name == null ? "그 지형에서 " : name + " 지형에서 ";
	}

	private static String dimension(String id) {
		if (id == null) {
			return null;
		}
		return switch (id) {
			case "minecraft:the_nether" -> "지옥";
			case "minecraft:the_end" -> "엔더";
			case "minecraft:overworld" -> "지상";
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
