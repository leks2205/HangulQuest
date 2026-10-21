package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;

import java.util.ArrayList;
import java.util.List;

/**
 * The Korean for the three spatial goals, plus the grammar points each sentence practises. The pieces
 * are fixed phrases (noun + location noun + particle, material + (으)로), so the particles stay
 * right for any noun and the relation word can never be left out, not even in the easy hint.
 */
public final class SpatialSentences {
	/** A sentence and the grammar-point ids it carries, e.g. {@code L1.loc_wi_e}, {@code L2.euro_material}. */
	public record SpatialText(String ko, List<String> grammar) {
	}

	public static final String EURO_MATERIAL = "L2.euro_material";
	public static final String COUNTER_GAE = "L1.counter_gae";

	private SpatialSentences() {
	}

	// ------------------------------------------------------------------ place relative

	/** "문 옆에 횃불을 놓아요." {@code past} gives the finale form "놓았어요" without the full stop. */
	public static SpatialText placeRel(Goal.PlaceRel goal, String obj, Verb verb, int level, boolean withCounts,
									   boolean finale, boolean past, SentenceBuilder.Namer namer) {
		List<String> grammar = new ArrayList<>();
		grammar.add(goal.relation().grammarId(Role.TARGET));
		String noun = anchorNoun(goal.anchor(), namer);
		if (goal.relation() == Relation.BETWEEN) {
			noun = betweenAnchor(noun);
		}
		String head = SentenceBuilder.relationPhrase(noun, goal.relation(), Role.TARGET) + " "
				+ SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), withCounts);
		if (withCounts && goal.count() > 1) {
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

	/** "돌로 작은 피라미드를 쌓아요." {@code material} is the Korean name of the stuff it is built from. */
	public static SpatialText buildPattern(Goal.BuildPattern goal, String material, Verb verb, int level,
										   boolean withCounts, boolean finale, boolean past) {
		List<String> grammar = new ArrayList<>();
		String head;
		switch (goal.pattern()) {
			case PILLAR -> {
				head = withCounts
						? material + " " + SentenceBuilder.counter(goal.size(), "개") + "로 " + KoGrammar.eul("기둥")
						: KoGrammar.euro(material) + " " + KoGrammar.eul("기둥");
				grammar.add(EURO_MATERIAL);
				if (withCounts) {
					grammar.add(COUNTER_GAE);
				}
			}
			case PYRAMID -> {
				head = KoGrammar.euro(material) + " " + KoGrammar.eul("작은 피라미드");
				grammar.add(EURO_MATERIAL);
			}
			case RING -> {
				head = KoGrammar.euro(material) + " " + KoGrammar.eul(Pattern.RING.ko());
				grammar.add(EURO_MATERIAL);
			}
			case ROW -> {
				String stones = withCounts ? material + " " + SentenceBuilder.counter(goal.size(), "개") : material;
				head = KoGrammar.eul(stones) + " 한 줄로";
				if (withCounts) {
					grammar.add(COUNTER_GAE);
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
		List<String> grammar = new ArrayList<>();
		grammar.add(goal.relation().grammarId(Role.TARGET));
		String noun = patternNoun(goal.pattern(), material);
		if (goal.pattern() == Pattern.ROW) {
			grammar.add(COUNTER_GAE);
		}
		String head = SentenceBuilder.relationPhrase(noun, goal.relation(), Role.TARGET) + " "
				+ SentenceBuilder.objectPhrase(obj, verb);
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

	/** The grammar-point ids of a spatial goal's sentence, without building the sentence. */
	public static List<String> grammarPoints(Goal goal) {
		Verb verb = Verb.PLACE;
		SentenceBuilder.Namer namer = SentenceBuilder.Namer.NONE;
		return switch (goal) {
			case Goal.PlaceRel g -> placeRel(g, "", verb, 1, true, false, false, namer).grammar();
			case Goal.BuildPattern g -> buildPattern(g, "", verb, 1, true, false, false).grammar();
			case Goal.PlaceOnPattern g -> placeOnPattern(g, "", "", verb, 1, false, false).grammar();
			default -> List.of();
		};
	}

	private static SpatialText finish(String head, Verb verb, int level, boolean finale, boolean past,
									  List<String> grammar) {
		String ko = past ? head + " " + verb.past() : SentenceBuilder.phrase(head, verb, level, finale);
		return new SpatialText(ko, List.copyOf(grammar));
	}
}
