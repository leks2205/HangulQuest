package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.quest.Goal;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A villager's request: a small task outside the quest tree, read from
 * {@code assets/hangulquest/requests/<id>.json}. The villager offers it, the player accepts or declines, and
 * reports back by talking to the same villager. Every line is written for each personality that may offer it.
 *
 * @param goal      what must be done (GIVE and DO); null for SHOW
 * @param showItem  the item to hold while talking to the villager (SHOW only)
 * @param words     the catalog words the request practises: exposed on the offer, practised on the turn-in
 */
public record Request(String id, int level, Kind kind, List<Region> regions, List<Personality> personalities,
					  List<String> professions, Goal goal, String showItem, int showCount, List<String> words,
					  Line yes, Line no, Map<Personality, Map<Part, Line>> lines) {
	/** Where the gloss of the short task title lives: {@code hangulquest.request.<id>.title}. */
	public static final String TITLE_PREFIX = "hangulquest.request.";

	/** How the request is done; each kind claims only what the game can check. */
	public enum Kind {
		/** Hold the item while talking to the villager again; nothing is taken. */
		SHOW,
		/** Drop the item in front of the villager, who picks it up. */
		GIVE,
		/** Any other quest goal (craft, place, reach a biome, ...), then report back. */
		DO;

		public static Kind fromId(String id) {
			if (id == null) {
				return null;
			}
			for (Kind kind : values()) {
				if (kind.name().equalsIgnoreCase(id.trim())) {
					return kind;
				}
			}
			return null;
		}
	}

	/** The lines of a request, in the order they are spoken. */
	public enum Part {
		OFFER, ACCEPT, DECLINE, REMINDER, THANKS;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A line and its 반말 version for a villager who switched to 반말 (null when there is none). */
	public record Line(String ko, String koBanmal) {
		public String text(boolean banmal) {
			return banmal && koBanmal != null && !koBanmal.isBlank() ? koBanmal : ko;
		}
	}

	public Request {
		regions = regions == null ? List.of() : List.copyOf(regions);
		personalities = personalities == null ? List.of() : List.copyOf(personalities);
		professions = professions == null ? List.of() : List.copyOf(professions);
		words = words == null ? List.of() : List.copyOf(words);
		Map<Personality, Map<Part, Line>> copy = new EnumMap<>(Personality.class);
		if (lines != null) {
			lines.forEach((personality, parts) -> copy.put(personality, Map.copyOf(parts)));
		}
		lines = copy;
	}

	/** The personalities that may offer it: the listed ones, or all eight. */
	public List<Personality> offeredBy() {
		return personalities.isEmpty() ? List.of(Personality.values()) : personalities;
	}

	/** True when a villager of this personality, region and profession may offer it to a player of this level. */
	public boolean fits(Personality personality, Region region, String profession, int playerLevel) {
		return offeredBy().contains(personality) && region != null && regions.contains(region)
				&& (professions.isEmpty() || professions.contains(profession)) && level <= playerLevel;
	}

	public Line line(Personality personality, Part part) {
		Map<Part, Line> parts = lines.get(personality);
		return parts == null ? null : parts.get(part);
	}

	/** The node id of a line in the conversations built from the request, also its gloss key and sound name. */
	public static String nodeId(Personality personality, Part part) {
		return personality.id() + "_" + part.id();
	}

	/** The gloss of a line: {@code hangulquest.dialogue.<id>.<personality>_<part>}. */
	public String lineKey(Personality personality, Part part) {
		return Dialogue.KEY_PREFIX + id + "." + nodeId(personality, part);
	}

	/** The gloss of the player's yes ({@code true}) or no. */
	public String answerKey(boolean yes) {
		return Dialogue.KEY_PREFIX + id + "." + (yes ? "yes" : "no");
	}

	/** The gloss of the short task title (quest log, journal). */
	public String titleKey() {
		return TITLE_PREFIX + id + ".title";
	}
}
