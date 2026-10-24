package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.villager.RequestSet;
import io.github.leks2205.hangulquest.villager.RequestValidator;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.util.Set;

/** Everything dialogue needs from a content load: the valid dialogues, the villager requests and the word list. */
public record DialogueContent(DialogueSet set, DialogueLexicon lexicon, RequestSet requests) {
	public static final DialogueContent EMPTY = new DialogueContent(DialogueSet.EMPTY, DialogueLexicon.EMPTY,
			RequestSet.EMPTY);

	public DialogueContent(DialogueSet set, DialogueLexicon lexicon) {
		this(set, lexicon, RequestSet.EMPTY);
	}

	/** Never throws: dialogue is optional, so a failure leaves it empty and the rest of the content intact. */
	public static DialogueContent load(ResourceManager manager, Catalog catalog, Set<String> trailIds, Logger log) {
		try {
			Set<String> grammar = DialogueResources.grammarIds(manager, log);
			DialogueValidator.Env env = new DialogueValidator.Env(trailIds, grammar.isEmpty() ? null : grammar,
					id -> catalog.byId(id).isPresent(), I18n::has);
			DialogueSet set = DialogueSet.load(DialogueResources.read(manager, log), env, log);
			RequestValidator.Env requestEnv = new RequestValidator.Env(catalog, id -> catalog.byId(id).isPresent(),
					I18n::has, null);
			RequestSet requests = RequestSet.load(DialogueResources.readRequests(manager, log), requestEnv, log);
			return new DialogueContent(set, DialogueResources.lexicon(manager, log), requests);
		} catch (RuntimeException failed) {
			log.warn("dialogue not loaded: {}", failed.toString());
			return EMPTY;
		}
	}
}
