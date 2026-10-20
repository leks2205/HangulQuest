package io.github.leks2205.hangulquest.data;

import io.github.leks2205.hangulquest.util.TextClean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns parsed Lute rows into Words plus the SRS seed implied by their status. */
public final class LuteImport {
	public record Result(List<Word> words, Map<String, Integer> levels, List<String> known,
						 int skipped, int phrases) {
	}

	private LuteImport() {
	}

	public static Result importRows(List<LuteCsv.Row> rows, List<Concept> concepts) {
		Map<String, Concept> byTerm = new LinkedHashMap<>();
		for (Concept concept : concepts) {
			if (concept.ko != null && !concept.ko.isBlank()) {
				byTerm.put(TextClean.normalizeTerm(concept.ko), concept);
			}
			if (concept.aliases != null) {
				for (String alias : concept.aliases) {
					byTerm.putIfAbsent(TextClean.normalizeTerm(alias), concept);
				}
			}
		}

		Map<String, Word> words = new LinkedHashMap<>();
		Map<String, Integer> levels = new HashMap<>();
		List<String> known = new ArrayList<>();
		Map<String, Integer> statusById = new HashMap<>();
		int skipped = 0;
		int phrases = 0;

		for (LuteCsv.Row row : rows) {
			String term = TextClean.clean(row.term());
			String translation = TextClean.clean(row.translation());
			if (row.status() <= 0 || term.isBlank() || translation.isBlank()) {
				skipped++;
				continue;
			}
			if (!TextClean.hasHangul(term) || !TextClean.isHangulOnly(term) || term.length() > 40) {
				skipped++;
				continue;
			}
			boolean phrase = term.contains(" ");

			Concept concept = byTerm.get(TextClean.normalizeTerm(term));
			Category category = concept != null ? concept.categoryEnum()
					: phrase ? Category.PHRASE : Category.MISC;
			int tier = concept != null ? concept.tier : 2;
			String icon = concept != null && concept.items != null && !concept.items.isEmpty()
					? concept.items.get(0) : null;
			String entity = concept == null ? null : concept.entity;
			String biome = concept == null ? null : concept.biome;
			String descKo = concept == null ? null : concept.descKo;

			String id = concept != null ? "concept:" + concept.id : "lute:" + TextClean.normalizeTerm(term);
			Integer previousStatus = statusById.get(id);
			if (previousStatus != null && previousStatus >= row.status()) {
				continue;
			}
			// A Lute word that maps onto a curated concept reuses that word, so the
			// journal does not show two entries for the same vocabulary.
			Word word;
			if (concept != null) {
				word = concept.toWord();
			} else {
				word = new Word(id, term, translation, TextClean.shortGloss(translation), category, tier,
						null, icon, entity, biome, null, Origin.LUTE, phrase, null);
			}
			words.put(word.id, word);
			statusById.put(word.id, row.status());
			if (phrase) {
				phrases++;
			}
			if (row.status() == 99) {
				if (!known.contains(word.id)) {
					known.add(word.id);
				}
			} else {
				levels.put(word.id, seedLevel(row.status()));
			}
		}
		return new Result(new ArrayList<>(words.values()), levels, known, skipped, phrases);
	}

	private static int seedLevel(int status) {
		return switch (status) {
			case 5 -> 5;
			case 4 -> 4;
			case 3 -> 3;
			case 2 -> 2;
			default -> 0;
		};
	}

	public static String tagCategory(String tags) {
		if (tags == null) {
			return null;
		}
		String lower = tags.toLowerCase(Locale.ROOT);
		if (lower.contains("verb")) {
			return "ACTIONS";
		}
		if (lower.contains("adjektiv")) {
			return "MISC";
		}
		return null;
	}
}
