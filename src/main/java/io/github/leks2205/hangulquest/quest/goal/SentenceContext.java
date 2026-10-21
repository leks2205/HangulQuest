package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;

import java.util.Map;
import java.util.Set;

/**
 * What a goal needs to speak Korean: the name of its object, the chosen verb and how hard the
 * sentence should be.
 *
 * @param obj        the noun the player sees, "" when nothing names it
 * @param verb       the verb picked from the verb table
 * @param withCounts whether a count shows up in the sentence (the hint leaves it out)
 * @param level      Korean level 1..6
 * @param finale     whether the sentence ends a trail
 * @param word       the word the step teaches, or null; it names the thing it stands for
 * @param namer      names for the other nouns of the sentence (the recipient, the container ...)
 */
public record SentenceContext(String obj, Verb verb, boolean withCounts, int level, boolean finale,
							  Word word, SentenceBuilder.Namer namer) {
	/**
	 * Words that stand for a whole family of blocks although they own none as a referent: 나무 is every log.
	 * Concept id to block families (see {@link Matching#blockFamily}).
	 */
	private static final Map<String, Set<String>> FAMILY_WORDS = Map.of("tree", Set.of("log"));

	public SentenceContext(String obj, Verb verb, boolean withCounts, int level, boolean finale) {
		this(obj, verb, withCounts, level, finale, null, SentenceBuilder.Namer.NONE);
	}

	/**
	 * Whether the taught word names this id: a block or item it owns as a referent (or one of the same
	 * family: any door for 문), or its entity. A word's picture does not count, so a cat shown by a cod
	 * never names the cod.
	 */
	public boolean wordNames(ObjectKind kind, String id) {
		return wordNames(word, kind, id);
	}

	public static boolean wordNames(Word word, ObjectKind kind, String id) {
		if (word == null || id == null || word.ko == null || word.ko.isBlank()) {
			return false;
		}
		return switch (kind) {
			case BLOCK, ITEM -> namesBlock(word, id);
			case ENTITY -> id.equals(word.entityId);
			default -> false;
		};
	}

	private static boolean namesBlock(Word word, String spec) {
		String id = BlockSpec.representative(spec);
		if (id == null) {
			return false;
		}
		for (String referent : word.referents) {
			if (referent.equals(id) || Matching.matchesBlock(id, referent)) {
				return true;
			}
		}
		Set<String> families = word.conceptId == null ? null : FAMILY_WORDS.get(word.conceptId);
		return families != null && families.contains(Matching.blockFamily(id));
	}

	/**
	 * Whether the word is only drawn with this id (its icon), as 우유 is with the milk bucket. That is enough
	 * to name the goal's own object (the clue already does), but never a second noun of the sentence.
	 */
	public static boolean wordPictures(Word word, String id) {
		return word != null && id != null && id.equals(word.iconItem);
	}

	/**
	 * Whether the word can stand for the object of the goal: it names it, or it is drawn with it. A word
	 * that says nothing about any id (a bare test word, an abstract concept) is taken to cover it.
	 */
	public static boolean wordCovers(Word word, ObjectKind kind, String id) {
		if (word == null) {
			return false;
		}
		boolean identity = !word.referents.isEmpty() || word.iconItem != null || word.entityId != null
				|| word.biomeId != null;
		return !identity || wordNames(word, kind, id) || wordPictures(word, id);
	}

	/** The Korean name of an id: the taught word when it names it, else the registry name, else "". */
	public String name(ObjectKind kind, String id) {
		if (id == null) {
			return "";
		}
		if (wordNames(kind, id)) {
			return word.ko;
		}
		String name = switch (kind) {
			case BLOCK -> namer.block(id);
			case ITEM -> namer.item(id);
			case ENTITY -> namer.entity(id);
			default -> null;
		};
		return name == null ? "" : name;
	}

	/** Same, for the main object of a part: the word also counts when it is only drawn with the id. */
	public String nameOfObject(ObjectKind kind, String id) {
		return id != null && word != null && wordCovers(word, kind, id) ? word.ko : name(kind, id);
	}

	/** The same sentence context for another goal of the same step (a part of a sequence). */
	public SentenceContext forPart(String partObj, Verb partVerb, int partLevel) {
		return new SentenceContext(partObj, partVerb, withCounts, partLevel, finale, word, namer);
	}

	/** A namer that says the taught word for the ids it names and defers to {@code base} for the rest. */
	public static SentenceBuilder.Namer taught(Word word, SentenceBuilder.Namer base) {
		if (word == null || word.ko == null || word.ko.isBlank()) {
			return base;
		}
		return new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return id != null && id.equals(word.biomeId) ? word.ko : base.biome(id);
			}

			@Override
			public String entity(String id) {
				return wordNames(word, ObjectKind.ENTITY, id) ? word.ko : base.entity(id);
			}

			@Override
			public String block(String id) {
				return wordNames(word, ObjectKind.BLOCK, id) ? word.ko : base.block(id);
			}

			@Override
			public String item(String id) {
				return wordNames(word, ObjectKind.ITEM, id) ? word.ko : base.item(id);
			}
		};
	}
}
