package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.Tiers;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Content rules for the spatial goals: the relation word must be in the Korean, the translation must
 * not give it away, and the materials must be reachable at the trail's level.
 */
public final class SpatialRules {
	/** Materials of the Nether and the End; they are out of reach for a beginner. */
	private static final String[] FAR_AWAY = {"nether", "soul_", "basalt", "blackstone", "crimson", "warped",
			"glowstone", "magma", "quartz", "shroomlight", "ancient_debris", "end_", "purpur", "chorus", "gilded"};

	/** Levels 1 and 2 stay in the overworld. */
	private static final int OVERWORLD_ONLY_UP_TO = 2;

	private SpatialRules() {
	}

	/** The namer the quest text uses, built from a catalog. */
	public static SentenceBuilder.Namer namer(Catalog catalog) {
		return new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return catalog.byBiomeId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String entity(String id) {
				return catalog.byEntityId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String block(String id) {
				return catalog.byItemId(id).map(word -> Tiers.exact(id, word.ko)).orElse(null);
			}
		};
	}

	/**
	 * A tag the clue has to say needs a word of its own (문 for the doors, 표지판 for the signs), or the sentence
	 * names one block of it, or "그것". The word is a curated concept whose blocks all lie in the tag.
	 */
	public static void checkTagWord(String spec, ValidationContext context) {
		if (BlockSpec.isTag(spec) && BlockSpec.valid(spec) && BlockSpec.tagWord(spec, context.catalog().concepts()) == null) {
			context.problem("the tag " + spec + " has no word of its own: add a concept that covers its blocks");
		}
	}

	/** The relation-revealing word in a translation, whichever relation it names, or null when it is clean. */
	public static String leak(String translation) {
		return RelationWords.leak(translation, null, List.of());
	}

	/** True when the Korean has the relation as a place word: " 옆에", " 위에서". */
	public static boolean namesRelation(String korean, Relation relation) {
		return korean != null && korean.contains(" " + relation.ko() + "에");
	}

	/**
	 * The shared checks of the two relation goals: the generated sentence (and any authored one) names
	 * the relation, and neither the English nor the German line gives it away.
	 */
	public static void checkRelationText(Goal goal, Relation relation, ValidationContext context) {
		QuestStep step = context.step();
		Word word = step.teach().isEmpty() ? null : context.catalog().byConceptId(step.teach().get(0)).orElse(null);
		String generated = SentenceBuilder.normal(goal, word, List.of(), 1, false,
				StepNamer.of(namer(context.catalog()), step));
		if (!namesRelation(generated, relation)) {
			context.problem("the Korean sentence does not name the relation " + relation.ko() + ": " + generated);
		}
		for (String authored : new String[]{step.clueKo(), step.hintKo()}) {
			if (authored != null && !authored.isBlank() && !namesRelation(authored, relation)) {
				context.problem("authored Korean does not name the relation " + relation.ko() + ": " + authored);
			}
		}
		String[] translations = {step.clueEn(), step.clueDe(), step.hintEn(), step.hintDe()};
		for (String translation : translations) {
			String leaked = RelationWords.leak(translation, relation, step.conditions());
			if (leaked != null) {
				context.problem("the translation gives the relation away ('" + leaked + "'): " + translation);
			}
		}
	}

	/** Whether a block, family or tag can be had at this Korean level (no Nether stuff before level 3). */
	public static boolean reachable(String blockOrFamily, int koreanLevel) {
		if (blockOrFamily == null || koreanLevel > OVERWORLD_ONLY_UP_TO) {
			return true;
		}
		String path = blockOrFamily.substring(blockOrFamily.indexOf(':') + 1).toLowerCase(Locale.ROOT);
		for (String far : FAR_AWAY) {
			if (path.contains(far)) {
				return false;
			}
		}
		return true;
	}

	/** Every block a spatial goal needs from the player or the world. */
	public static List<String> blocksOf(Goal goal) {
		return switch (goal) {
			case Goal.PlaceRel g -> {
				List<String> blocks = new ArrayList<>(List.of(g.block()));
				if (!StepRef.isRef(g.anchor()) && !BlockSpec.isEntityAnchor(g.anchor())) {
					blocks.add(g.anchor());
				}
				if (g.anchor2() != null) {
					blocks.add(g.anchor2());
				}
				yield blocks;
			}
			case Goal.BuildPattern g -> List.of(g.material());
			case Goal.PlaceOnPattern g -> List.of(g.block(), g.material());
			default -> List.of();
		};
	}

	/**
	 * The block a goal places to finish itself, which a later step can call "아까 놓은" with an {@code @stepId}
	 * anchor; null for goals that place nothing.
	 */
	public static String placedBlock(Goal goal) {
		return switch (goal) {
			case Goal.PlaceBlock g -> g.block();
			case Goal.PlaceRel g -> g.block();
			case Goal.PlaceOnPattern g -> g.block();
			case null, default -> null;
		};
	}

	/** The reachability problems of a trail: spatial steps whose blocks are out of reach at its level. */
	public static List<String> reachabilityProblems(Questline trail) {
		List<String> problems = new ArrayList<>();
		for (QuestStep step : trail.allSteps()) {
			for (String block : blocksOf(step.goal())) {
				if (!reachable(block, trail.koreanLevel())) {
					problems.add(trail.id() + "/" + step.id() + ": " + block
							+ " is out of reach at Korean level " + trail.koreanLevel());
				}
			}
		}
		return problems;
	}
}
