package io.github.leks2205.hangulquest.quest;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finds questline files in the resource manager, so the mod's own files and any resource pack are
 * read the same way. A pack adds or replaces trails with files under
 * {@code assets/<namespace>/hangulquest/questlines/}; the shipped files sit in
 * {@code assets/hangulquest/data/questlines/}.
 */
public final class QuestResources {
	/** Folders searched in every namespace. */
	public static final List<String> FOLDERS = List.of("data/questlines", "hangulquest/questlines");

	private QuestResources() {
	}

	/**
	 * Raw bytes of every questline file, keyed by resource id. The order decides which file wins
	 * when two share a trail id: the mod's own namespace first, then the others by name, so a
	 * pack's trail replaces the shipped one.
	 */
	public static Map<String, byte[]> read(ResourceManager manager, Logger log) {
		Map<String, Resource> found = new TreeMap<>(QuestResources::compareIds);
		for (String folder : FOLDERS) {
			Map<Identifier, Resource> listed = manager.listResources(folder,
					id -> QuestlineStore.isTrailFile(id.getPath()));
			for (Map.Entry<Identifier, Resource> entry : listed.entrySet()) {
				found.put(entry.getKey().toString(), entry.getValue());
			}
		}
		Map<String, byte[]> files = new LinkedHashMap<>();
		for (Map.Entry<String, Resource> entry : found.entrySet()) {
			try (InputStream stream = entry.getValue().open()) {
				files.put(entry.getKey(), stream.readAllBytes());
			} catch (Exception unreadable) {
				log.warn("questline resource {} could not be read: {}", entry.getKey(), unreadable.toString());
			}
		}
		return files;
	}

	/** Own namespace first, then alphabetical; the id string is "namespace:path". */
	static int compareIds(String a, String b) {
		boolean ownA = a.startsWith("hangulquest:");
		boolean ownB = b.startsWith("hangulquest:");
		if (ownA != ownB) {
			return ownA ? -1 : 1;
		}
		return a.compareTo(b);
	}
}
