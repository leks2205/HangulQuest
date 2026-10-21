package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.util.Set;

/** Everything dialogue needs from a content load: the valid dialogues and the word list. */
public record DialogueContent(DialogueSet set, DialogueLexicon lexicon) {
	public static final DialogueContent EMPTY = new DialogueContent(DialogueSet.EMPTY, DialogueLexicon.EMPTY);

	/** Never throws: dialogue is optional, so a failure leaves it empty and the rest of the content intact. */
	public static DialogueContent load(ResourceManager manager, Catalog catalog, Set<String> trailIds, Logger log) {
		try {
			Set<String> grammar = DialogueResources.grammarIds(manager, log);
			DialogueValidator.Env env = new DialogueValidator.Env(trailIds, grammar.isEmpty() ? null : grammar,
					id -> catalog.byId(id).isPresent(), I18n::has);
			DialogueSet set = DialogueSet.load(DialogueResources.read(manager, log), env, log);
			return new DialogueContent(set, DialogueResources.lexicon(manager, log));
		} catch (RuntimeException failed) {
			log.warn("dialogue not loaded: {}", failed.toString());
			return EMPTY;
		}
	}
}
