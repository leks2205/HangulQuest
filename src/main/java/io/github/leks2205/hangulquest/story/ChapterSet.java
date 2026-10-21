package io.github.leks2205.hangulquest.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.JsonFields;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The life-situation chapters of the quest tab (data/chapters.json, written by tools/assign_chapters.py): a
 * title, an icon, a level range and the trails in it. A trail is in at most one chapter. The English and
 * German titles are the keys {@code hangulquest.chapter.<id>.title}.
 */
public final class ChapterSet {
	public static final ChapterSet EMPTY = new ChapterSet(List.of());

	public record Chapter(String id, String titleKo, String icon, int minLevel, int maxLevel, List<String> trails) {
		public Chapter {
			trails = List.copyOf(trails);
		}

		public String titleKey() {
			return "hangulquest.chapter." + id + ".title";
		}
	}

	/** How far a player is in one chapter: finished trails of the trails that exist. */
	public record Progress(Chapter chapter, int done, int total) {
		public boolean complete() {
			return total > 0 && done == total;
		}
	}

	private final List<Chapter> chapters;
	private final Map<String, Chapter> byTrail = new HashMap<>();

	private ChapterSet(List<Chapter> chapters) {
		this.chapters = chapters;
		for (Chapter chapter : chapters) {
			chapter.trails().forEach(trail -> byTrail.putIfAbsent(trail, chapter));
		}
	}

	public static ChapterSet of(List<Chapter> chapters) {
		return new ChapterSet(List.copyOf(chapters));
	}

	/** Reads the file; trails that do not exist (a removed trail) are dropped, a trail listed twice stays in its first chapter. */
	public static ChapterSet parse(String json, Set<String> trailIds, Logger log) {
		List<Chapter> chapters = new ArrayList<>();
		Set<String> placed = new HashSet<>();
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		for (JsonElement element : root.getAsJsonArray("chapters")) {
			JsonObject chapter = element.getAsJsonObject();
			String id = JsonFields.string(chapter, "id", "");
			List<Integer> levels = new ArrayList<>();
			if (chapter.has("levels")) {
				chapter.getAsJsonArray("levels").forEach(level -> levels.add(level.getAsInt()));
			}
			List<String> trails = new ArrayList<>();
			for (String trail : JsonFields.strings(chapter, "trails")) {
				if (trailIds != null && !trailIds.contains(trail)) {
					log.warn("chapter {} lists the unknown trail {}", id, trail);
				} else if (!placed.add(trail)) {
					log.warn("chapter {}: trail {} is already in another chapter", id, trail);
				} else {
					trails.add(trail);
				}
			}
			chapters.add(new Chapter(id, JsonFields.string(chapter, "titleKo", id), JsonFields.string(chapter, "icon", ""),
					levels.isEmpty() ? 1 : levels.get(0), levels.size() < 2 ? (levels.isEmpty() ? 1 : levels.get(0)) : levels.get(1),
					trails));
		}
		return new ChapterSet(List.copyOf(chapters));
	}

	public List<Chapter> all() {
		return chapters;
	}

	public Optional<Chapter> byId(String id) {
		return chapters.stream().filter(chapter -> chapter.id().equals(id)).findFirst();
	}

	/** The chapter a trail is in. */
	public Optional<Chapter> ofTrail(String trailId) {
		return Optional.ofNullable(byTrail.get(trailId));
	}

	/** Progress of every chapter. {@code exists} says whether a trail is in the game now, {@code done} whether it is finished. */
	public List<Progress> progress(Predicate<String> exists, Predicate<String> done) {
		List<Progress> out = new ArrayList<>();
		for (Chapter chapter : chapters) {
			int total = 0;
			int finished = 0;
			for (String trail : chapter.trails()) {
				if (exists.test(trail)) {
					total++;
					if (done.test(trail)) {
						finished++;
					}
				}
			}
			out.add(new Progress(chapter, finished, total));
		}
		return out;
	}

	public int size() {
		return chapters.size();
	}
}
