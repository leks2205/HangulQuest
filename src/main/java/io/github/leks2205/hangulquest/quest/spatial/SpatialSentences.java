package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;

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
	public static final String AND_NOUNS = "L2.hago_wa_gwa";
	public static final String GO_SEQUENCE = "L2.go_sequence";
	public static final String ADN_PAST = "L5.adn_past_eun";
	public static final String E_TIME = "L1.e_time";

	private SpatialSentences() {
	}

	// ------------------------------------------------------------------ place relative

	/**
	 * "문 옆에 횃불을 놓아요." {@code past} gives the finale form "놓았어요" without the full stop. The anchor may
	 * be two blocks ("상자와 화로 사이에") or the block of an earlier step ("아까 놓은 횃불 옆에").
	 */
	public static SpatialText placeRel(Goal.PlaceRel goal, String obj, Verb verb, int level, boolean withCounts,
									   boolean finale, boolean past, SentenceBuilder.Namer namer) {
		List<String> grammar = new ArrayList<>(goal.relation().grammarIds(Role.TARGET));
		obj = apartFromAnchors(goal, obj, namer);
		String noun;
		if (goal.anchor2() != null) {
			noun = KoGrammar.waGwa(anchorNoun(goal.anchor(), namer)) + " " + anchorNoun(goal.anchor2(), namer);
			grammar.add(AND_NOUNS);
		} else if (StepRef.isRef(goal.anchor())) {
			String[] names = shorten(placedName(goal.anchor(), namer), obj);
			noun = placedPrefix(goal.anchor(), namer) + names[0];
			obj = names[1];
			grammar.add(ADN_PAST);
		} else {
			String[] names = shorten(anchorNoun(goal.anchor(), namer), obj);
			noun = names[0];
			obj = names[1];
			if (goal.relation() == Relation.BETWEEN) {
				noun = betweenAnchor(noun);
				grammar.add(NUM_NATIVE);
			}
		}
		String head = SentenceBuilder.relationPhrase(noun, goal.relation(), Role.TARGET) + " "
				+ SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), withCounts,
						goal.block(), obj);
		if (withCounts && goal.count() > 1) {
			grammar.add(NUM_NATIVE);
			grammar.add(counterGrammar(goal.block(), obj));
		}
		return finish(head, verb, level, finale, past, grammar);
	}

	/**
	 * A word for a whole kind names every member of it: with 꽃 taught, a dandelion next to a poppy would read
	 * "꽃 옆에 꽃을". When the object and an anchor are different things that get the same name, the object keeps
	 * its own ("꽃 옆에 민들레를"), so the sentence says which is which.
	 */
	private static String apartFromAnchors(Goal.PlaceRel goal, String obj, SentenceBuilder.Namer namer) {
		if (obj == null || obj.isBlank()) {
			return obj;
		}
		String own = BlockSpec.representative(goal.block());
		List<String> anchors = new ArrayList<>();
		anchors.add(goal.anchor());
		if (goal.anchor2() != null) {
			anchors.add(goal.anchor2());
		}
		for (String anchor : anchors) {
			String said = StepRef.isRef(anchor) ? placedName(anchor, namer) : anchorNoun(anchor, namer);
			String anchorBlock = StepRef.isRef(anchor) ? placedBlock(anchor, namer) : BlockSpec.representative(anchor);
			if (said.equals(obj) && own != null && !own.equals(anchorBlock)) {
				String registry = namer.untaught().block(own);
				return registry == null || registry.isBlank() ? obj : registry;
			}
		}
		return obj;
	}

	private static String placedBlock(String ref, SentenceBuilder.Namer namer) {
		SentenceBuilder.Namer.Placed placed = namer.placedBy(StepRef.stepId(ref));
		return placed == null ? null : BlockSpec.representative(placed.block());
	}

	/** "아까 놓은 ": the earlier step's verb as a past attributive, whatever that step did (놓다, 달다, 쌓다). */
	public static String placedPrefix(String ref, SentenceBuilder.Namer namer) {
		SentenceBuilder.Namer.Placed placed = namer.placedBy(StepRef.stepId(ref));
		return "아까 " + (placed == null ? Verb.PLACE : placed.verb()).attributive() + " ";
	}

	/** The name of the block an earlier step placed; "것" (a plain thing) when that step is not known. */
	public static String placedName(String ref, SentenceBuilder.Namer namer) {
		SentenceBuilder.Namer.Placed placed = namer.placedBy(StepRef.stepId(ref));
		String name = placed == null ? null : namer.block(BlockSpec.representative(placed.block()));
		return name == null || name.isBlank() ? "것" : name;
	}

	/**
	 * Drops what the two names of a relation sentence share: "참나무 울타리 옆에 참나무 울타리 문을" reads
	 * "울타리 옆에 문을". The material prefix goes first, then an object name that repeats the anchor.
	 * Returns {anchor, object}; each keeps at least one word.
	 */
	public static String[] shorten(String anchor, String obj) {
		if (anchor == null || obj == null || anchor.isBlank() || obj.isBlank()) {
			return new String[]{anchor, obj};
		}
		String[] a = anchor.split(" ");
		String[] o = obj.split(" ");
		int shared = 0;
		while (shared < a.length && shared < o.length && a[shared].equals(o[shared])) {
			shared++;
		}
		int cut = Math.min(shared, Math.min(a.length, o.length) - 1);
		int from = Math.max(0, cut);
		String anchorRest = String.join(" ", java.util.Arrays.copyOfRange(a, from, a.length));
		String[] objRest = java.util.Arrays.copyOfRange(o, from, o.length);
		String[] anchorWords = anchorRest.split(" ");
		if (objRest.length > anchorWords.length
				&& java.util.Arrays.equals(java.util.Arrays.copyOfRange(objRest, 0, anchorWords.length), anchorWords)) {
			objRest = java.util.Arrays.copyOfRange(objRest, anchorWords.length, objRest.length);
		}
		return new String[]{anchorRest, String.join(" ", objRest)};
	}

	private static String counterGrammar(String blockId, String noun) {
		String counter = io.github.leks2205.hangulquest.quest.Counters.shared().counter(blockId, noun);
		String id = io.github.leks2205.hangulquest.quest.Counters.shared().grammar(counter);
		return id == null ? COUNTER_GAE : id;
	}

	/** Identifies a structure a step builds, to tell whether an earlier step already did. */
	public static String builtKey(Pattern pattern, String material) {
		return pattern + "/" + material;
	}

	/** The structure a goal builds or stands on, as a {@link #builtKey}; null for other goals. */
	public static String builtKey(Goal goal) {
		return switch (goal) {
			case Goal.BuildPattern g -> builtKey(g.pattern(), g.material());
			case Goal.PlaceOnPattern g -> builtKey(g.pattern(), g.material());
			case null, default -> null;
		};
	}

	/** "두 횃불": the anchor of 사이 comes twice. */
	public static String betweenAnchor(String noun) {
		return "두 " + noun;
	}

	/** The Korean name of a block anchor; "그것" when the namer does not know it. */
	public static String anchorNoun(String spec, SentenceBuilder.Namer namer) {
		String name = blockName(spec, namer);
		return name == null || name.isBlank() ? "그것" : name;
	}

	/**
	 * The name of a block or a tag, or null. A tag is said with the taught word when that names it, else
	 * with its everyday word (꽃, 통나무: {@link BlockSpec#tagWord}), so clue and finale agree.
	 */
	public static String blockName(String spec, SentenceBuilder.Namer namer) {
		if (spec == null) {
			return null;
		}
		if (BlockSpec.isTag(spec)) {
			String taught = namer.block(spec);
			if (taught != null && !taught.isBlank()) {
				return taught;
			}
			String general = BlockSpec.tagWord(spec);
			if (general != null) {
				return general;
			}
		}
		if (BlockSpec.isEntityAnchor(spec)) {
			return namer.entity(spec); // 주민 앞에: a creature, not a block
		}
		String plain = BlockSpec.representative(spec);
		return plain == null ? null : namer.block(plain);
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
					head = material + " " + SentenceBuilder.counter(goal.size(), counter(goal.material(), material)) + "로 " + KoGrammar.eul("기둥");
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
				if (Pattern.ringOfFences(goal.material())) {
					head = KoGrammar.eul(material); // 울타리를 쳐요: the fence is what is put up, not what it is made of
				} else {
					head = KoGrammar.euro(material) + " " + KoGrammar.eul(Pattern.RING.ko());
					grammar.add(EURO_MATERIAL);
				}
			}
			case WALL, CROSS, STAIRS -> {
				// 돌로 벽을 쌓아요, 돌로 십자 모양을 만들어요, 흙으로 계단을 쌓아요: the size is left to the status line.
				head = KoGrammar.euro(material) + " " + KoGrammar.eul(goal.pattern().ko());
				grammar.add(EURO_MATERIAL);
			}
			case ROW -> {
				if (withCounts) {
					head = KoGrammar.eul(material + " " + SentenceBuilder.counter(goal.size(), counter(goal.material(), material)))
							+ " 나란히";
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
		return placeOnPattern(goal, obj, material, verb, level, finale, past, SentenceBuilder.Namer.NONE);
	}

	/**
	 * Same. At a level that joins clauses (ladder.json {@code connectives}) a structure that no earlier step
	 * built is built in the same sentence: "조약돌로 기둥을 쌓고 그 위에 횃불을 놓아요."
	 */
	public static SpatialText placeOnPattern(Goal.PlaceOnPattern goal, String obj, String material, Verb verb,
											 int level, boolean finale, boolean past, SentenceBuilder.Namer namer) {
		List<String> grammar = new ArrayList<>(goal.relation().grammarIds(Role.TARGET));
		if (builtHere(goal, material, level, past, namer)) {
			boolean fence = goal.pattern() == Pattern.RING && Pattern.ringOfFences(goal.material());
			Verb building = fence ? Verb.FENCE : buildVerb(goal.pattern());
			String built = fence ? KoGrammar.eul(material) + " " + building.stem() + "고 "
					: KoGrammar.euro(material) + " " + KoGrammar.eul(goal.pattern().ko()) + " " + building.stem() + "고 ";
			if (!fence) {
				grammar.add(EURO_MATERIAL);
			}
			grammar.add(GO_SEQUENCE);
			String head = built + SentenceBuilder.relationPhrase("그", goal.relation(), Role.TARGET) + " "
					+ SentenceBuilder.objectPhrase(obj, verb);
			return finish(head, verb, level, finale, past, grammar);
		}
		if (goal.pattern() == Pattern.ROW) {
			grammar.add(NUM_NATIVE);
			grammar.add(COUNTER_GAE);
		}
		// Both names stay whole: a stone-brick pillar with stone-brick stairs is "석재 벽돌 기둥 앞에 석재 벽돌 계단을",
		// not "기둥 앞에 계단을", which hides the material that the sentence is about.
		String head = SentenceBuilder.relationPhrase(patternNoun(goal.pattern(), material, goal.material()),
				goal.relation(), Role.TARGET) + " " + SentenceBuilder.objectPhrase(obj, verb);
		return finish(head, verb, level, finale, past, grammar);
	}

	/** Relations that read naturally after "…을 쌓고 그": on it, beside it, in it. */
	private static final java.util.Set<Relation> AFTER_BUILDING = java.util.EnumSet.of(Relation.ON_TOP_OF,
			Relation.NEXT_TO, Relation.INSIDE);

	/**
	 * True when the sentence builds the structure itself: the level joins clauses, nothing before the step
	 * built it, and the shape and the relation read as one sentence. A row stays a counted phrase.
	 */
	private static boolean builtHere(Goal.PlaceOnPattern goal, String material, int level, boolean past,
									 SentenceBuilder.Namer namer) {
		return !past && material != null && !material.isBlank() && Ladder.shared().rung(level).connectives()
				&& goal.pattern() != Pattern.ROW && AFTER_BUILDING.contains(goal.relation())
				&& !namer.builtEarlier(builtKey(goal.pattern(), goal.material()));
	}

	/** 쌓다 for what is piled up (stairs too), 만들다 for a cross (see BuildPatternType). */
	private static Verb buildVerb(Pattern pattern) {
		return pattern == Pattern.CROSS ? Verb.CRAFT : Verb.STACK;
	}

	/** The structure as a noun phrase: "조약돌 기둥", "조약돌 피라미드", "조약돌 담", "조약돌 세 개" (a row). */
	public static String patternNoun(Pattern pattern, String material) {
		return patternNoun(pattern, material, null);
	}

	/** The counter of a material: 개 for blocks, 장 for carpets (counters.json). */
	static String counter(String materialSpec, String noun) {
		return io.github.leks2205.hangulquest.quest.Counters.shared()
				.counter(BlockSpec.representative(materialSpec), noun);
	}

	public static String patternNoun(Pattern pattern, String material, String materialId) {
		String stuff = material == null || material.isBlank() ? "" : material + " ";
		if (pattern == Pattern.ROW) {
			return stuff + SentenceBuilder.counter(pattern.defaultSize(), counter(materialId, material));
		}
		if (pattern == Pattern.RING && materialId != null && Pattern.ringOfFences(materialId)) {
			return stuff.isBlank() ? "울타리" : stuff.strip(); // a ring of fences is the fence, not a "fence fence"
		}
		return Compounds.join(material == null ? "" : material, pattern.ko());
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
			case Goal.PlaceOnPattern g -> placeOnPattern(g, "", "재료", Verb.PLACE, level, false, past).grammar();
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
			grammar.add(Ladder.shared().rung(level).grammar());
		}
		return new SpatialText(ko, List.copyOf(grammar));
	}
}
