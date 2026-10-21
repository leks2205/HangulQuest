package io.github.leks2205.hangulquest.content;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.LangTables;
import io.github.leks2205.hangulquest.data.LuteCsv;
import io.github.leks2205.hangulquest.data.LuteImport;
import io.github.leks2205.hangulquest.quest.QuestResources;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.QuestlineOverlay;
import io.github.leks2205.hangulquest.quest.QuestlineStore;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The prepare phase of a content load. Pure with respect to the game state: safe off the main thread. */
public final class ContentLoader {
	/**
	 * @param questlineDir the local overlay folder
	 * @param luteFile     the Lute export to import, or null when the import is off
	 */
	public record Inputs(Path questlineDir, Path luteFile) {
	}

	private ContentLoader() {
	}

	/** Never throws: a failure comes back as {@link Content#failed}. */
	public static Content prepare(ResourceManager manager, Inputs inputs, Logger log) {
		try {
			long started = System.nanoTime();
			List<Concept> concepts = ConceptLoader.load(manager);
			LangTables tables = LangTables.load(manager);
			LuteImport.Result lute = null;
			Path luteFile = inputs.luteFile();
			if (luteFile != null && Files.exists(luteFile)) {
				try {
					lute = LuteImport.importRows(LuteCsv.parseFile(luteFile), concepts);
				} catch (Exception unreadable) {
					// A bad vocabulary export must not take the whole mod down with it.
					log.warn("could not import {}, continuing without it: {}", luteFile, unreadable.toString());
				}
			}
			Catalog catalog = Catalog.build(concepts, tables, lute == null ? List.of() : lute.words());

			QuestlineStore.exportShipped(inputs.questlineDir(), log);
			QuestlineStore.Shipped shipped = QuestlineStore.readShipped(QuestResources.read(manager, log), log);
			QuestlineOverlay overlay = new QuestlineOverlay(shipped.digests(), log);
			overlay.refresh(inputs.questlineDir());
			QuestValidator.Result validated = QuestValidator.validate(
					QuestlineStore.merge(shipped.trails(), overlay.files(), log), catalog, log);
			log.info("content prepared in {} ms: {} concepts, {} words, {} shipped trails, {} overlay files",
					(System.nanoTime() - started) / 1_000_000L, concepts.size(), catalog.size(),
					shipped.trails().size(), overlay.files().size());
			return new Content(concepts, tables, catalog, lute, luteFile, shipped, overlay, validated, null);
		} catch (Throwable failed) {
			return Content.failed(failed);
		}
	}
}
