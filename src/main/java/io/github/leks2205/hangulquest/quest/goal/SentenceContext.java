package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;

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
	public SentenceContext(String obj, Verb verb, boolean withCounts, int level, boolean finale) {
		this(obj, verb, withCounts, level, finale, null, SentenceBuilder.Namer.NONE);
	}

	/** Whether the taught word stands for this id (an item it names, or its entity). */
	public boolean wordNames(ObjectKind kind, String id) {
		if (word == null || id == null || word.ko == null || word.ko.isBlank()) {
			return false;
		}
		return switch (kind) {
			case BLOCK, ITEM -> word.referents.contains(id) || id.equals(word.iconItem);
			case ENTITY -> id.equals(word.entityId);
			default -> false;
		};
	}

	/** The Korean name of an id: the taught word when it stands for it, else the registry name, else "". */
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

	/** The same sentence context for another goal of the same step (a part of a sequence). */
	public SentenceContext forPart(String partObj, Verb partVerb, int partLevel) {
		return new SentenceContext(partObj, partVerb, withCounts, partLevel, finale, word, namer);
	}
}
