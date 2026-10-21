package io.github.leks2205.hangulquest.content;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.LangTables;
import io.github.leks2205.hangulquest.data.LuteImport;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.QuestlineOverlay;
import io.github.leks2205.hangulquest.quest.QuestlineStore;

import java.nio.file.Path;
import java.util.List;

/**
 * Everything that can be built without touching the game state: concepts, language tables, the
 * catalog, the shipped trails, the parsed overlay folder and the validated trail list. Built on a
 * background thread, published on the main thread. When {@code failure} is set, nothing else is.
 */
public record Content(List<Concept> concepts, LangTables tables, Catalog catalog,
					  LuteImport.Result lute, Path luteFile,
					  QuestlineStore.Shipped shipped, QuestlineOverlay overlay,
					  QuestValidator.Result validated, Throwable failure) {

	public static Content failed(Throwable failure) {
		return new Content(null, null, null, null, null, null, null, null, failure);
	}

	public boolean ok() {
		return failure == null;
	}
}
