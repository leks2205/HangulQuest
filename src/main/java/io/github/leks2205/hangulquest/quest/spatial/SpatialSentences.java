package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;

import java.util.ArrayList;
import java.util.List;

/**
 * The Korean for the three spatial goals, plus the grammar points each sentence practises (ids from
 * data/grammar.json). The pieces are fixed phrases (noun + location noun + particle, material + (으)로),
 * so the particles stay right for any noun and the relation word can never be left out, not even in
 * the easy hint.
 */
public final class SpatialSentences {
	/** A sentence and the grammar-point ids it carries, e.g. {@code L1.loc_wi_e}, {@code L2.euro_material}. */
	public record SpatialText(String ko, List<String> grammar) {
	}

	public static final String EURO_MATERIAL = "L2.euro_material";
	public static final String NUM_NATIVE = "L1.num_native";
	public static final String COUNTER_GAE = "L1.counter_gae";
	public static final String OBJ_EUL = "L1.obj_eul";
	public static final String PAST = "L3.past";
	public static final String E_TIME = "L1.e_time";

	/** The ending construction of each ladder level (level 5 is the literary one; it has no entry yet). */
	private static final String[] ENDING = {null, "L1.eoyo", "L4.eoya_haeyo", "L4.eoya_haeyo",
			"L4.myeon_an_doeyo", null, "L6.su_bakke_eopda"};

	private SpatialSentences() {
	}

	// ------------------------------------------------------------------ place relative

	/** "문 옆에 횃불을 놓아요." {@code past} gives the finale form "놓았어요" without the full stop. */
	public static SpatialText placeRel(Goal.PlaceRel goal, String obj, Verb verb, int level, boolean withCounts,
									   boolean finale, boolean past, SentenceBuilder.Namer namer) {
		List<String> grammar = new ArrayList<>(goal.relation().grammarIds(Role.TARGET));
		String noun = anchorNoun(goal.anchor(), namer);
		if (goal.relation() == Relation.BETWEEN) {
			noun = betweenAnchor(noun);
			grammar.add(NUM_NATIVE);
		}
		String head = SentenceBuilder.relationPhrase(noun, goal.relation(), Role.TARGET) + " "
				+ SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), withCounts);
		if (withCounts && goal.count() > 1) {
			grammar.add(NUM_NATIVE);
			grammar.add(COUNTER_GAE);
		}
		return finish(head, verb, level, finale, past, grammar);
	}

	/** "두 횃불": the anchor of 사이 comes twice. */
	public static String betweenAnchor(String noun) {
		return "두 " + noun;
	}

	/** The Korean name of a block anchor; "그것" when the namer does not know it. */
	public static String anchorNoun(String spec, SentenceBuilder.Namer namer) {
		String name = namer.block(BlockSpec.representative(spec));
		return name == null || name.isBlank() ? "그것" : name;
	}

	// ----------------------------------------------------------------- build a pattern

	/** "돌로 피라미드를 쌓아요." {@code material} is the Korean name of the stuff it is built from. */
	public static SpatialText buildPattern(Goal.BuildPattern goal, String material, Verb verb, int level,
										   boolean withCounts, boolean finale, boolean past) {
		List<String> grammar = new ArrayList<>();
		String head;
		switch (goal.pattern()) {
			case PILLAR -> {
				grammar.add(EURO_MATERIAL);
				if (withCounts) {
					head = material + " " + SentenceBuilder.counter(goal.size(), "개") + "로 " + KoGrammar.eul("기둥");
					grammar.add(NUM_NATIVE);
					grammar.add(COUNTER_GAE);
				} else {
					head = KoGrammar.euro(material) + " " + KoGrammar.eul("기둥");
				}
			}
			case PYRAMID -> {
				head = KoGrammar.euro(material) + " " + KoGrammar.eul("피라미드");
				grammar.add(EURO_MATERIAL);
			}
			case RING -> {
				head = KoGrammar.euro(material) + " " + KoGrammar.eul(Pattern.RING.ko());
				grammar.add(EURO_MATERIAL);
			}
			case ROW -> {
				if (withCounts) {
					head = KoGrammar.eul(material + " " + SentenceBuilder.counter(goal.size(), "개")) + " 나란히";
					grammar.add(NUM_NATIVE);
					grammar.add(COUNTER_GAE);
				} else {
					head = KoGrammar.eul(material) + " 나란히";
				}
			}
			default -> throw new IllegalStateException("no sentence for " + goal.pattern());
		}
		return finish(head, verb, level, finale, past, grammar);
	}

	// ------------------------------------------------------------- place on a pattern

	/** "조약돌 기둥 위에 횃불을 놓아요." */
	public static SpatialText placeOnPattern(Goal.PlaceOnPattern goal, String obj, String material, Verb verb,
											 int level, boolean finale, boolean past) {
		List<String> grammar = new ArrayList<>(goal.relation().grammarIds(Role.TARGET));
		if (goal.pattern() == Pattern.ROW) {
			grammar.add(NUM_NATIVE);
			grammar.add(COUNTER_GAE);
		}
		String head = SentenceBuilder.relationPhrase(patternNoun(goal.pattern(), material), goal.relation(),
				Role.TARGET) + " " + SentenceBuilder.objectPhrase(obj, verb);
		return finish(head, verb, level, finale, past, grammar);
	}

	/** The structure as a noun phrase: "조약돌 기둥", "조약돌 피라미드", "조약돌 울타리", "조약돌 세 개" (a row). */
	public static String patternNoun(Pattern pattern, String material) {
		String stuff = material == null || material.isBlank() ? "" : material + " ";
		if (pattern == Pattern.ROW) {
			return stuff + SentenceBuilder.counter(pattern.defaultSize(), "개");
		}
		return stuff + pattern.ko();
	}

	// ----------------------------------------------------------------------- grammar

	/** The grammar-point ids of a spatial goal's plain level-1 clue, without building the sentence. */
	public static List<String> grammarPoints(Goal goal) {
		return grammarPoints(goal, List.of(), 1, true, false);
	}

	/** The ids of the sentence the generator builds for these settings (conditions add 에 for the time). */
	public static List<String> grammarPoints(Goal goal, List<Condition> conditions, int level, boolean withCounts,
											 boolean past) {
		SentenceBuilder.Namer namer = SentenceBuilder.Namer.NONE;
		List<String> ids = new ArrayList<>(switch (goal) {
			case Goal.PlaceRel g -> placeRel(g, "", Verb.PLACE, level, withCounts, false, past, namer).grammar();
			case Goal.BuildPattern g -> buildPattern(g, "", Verb.PLACE, level, withCounts, false, past).grammar();
			case Goal.PlaceOnPattern g -> placeOnPattern(g, "", "", Verb.PLACE, level, false, past).grammar();
			default -> List.of();
		});
		if (!ids.isEmpty() && conditions.stream().anyMatch(c -> c instanceof Condition.Time)) {
			ids.add(0, E_TIME);
		}
		return ids;
	}

	private static SpatialText finish(String head, Verb verb, int level, boolean finale, boolean past,
									  List<String> content) {
		List<String> grammar = new ArrayList<>(new java.util.LinkedHashSet<>(content));
		String ko;
		if (past) {
			ko = head + " " + verb.past();
			grammar.add(PAST);
			grammar.add(OBJ_EUL);
		} else {
			ko = SentenceBuilder.phrase(head, verb, level, finale);
			grammar.add(OBJ_EUL);
			String ending = ENDING[Math.max(1, Math.min(6, level))];
			if (ending != null) {
				grammar.add(ending);
			}
		}
		return new SpatialText(ko, List.copyOf(grammar));
	}
}
