package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The morpheme panel of the words tab, without any drawing: for a Sino-Korean word, the other words that
 * share one of its hanja (화 火: 화약, 화로, 화산), and a warning where the same syllable is written with
 * another hanja among the words the player has learned (화 火 against 화 花). Native words have no family.
 *
 * <p>A family groups by the hanja character only, never by the sound. The sound of a character in a word
 * is known only when the tag lists one hanja per syllable (or one eojeol fits exactly); otherwise the
 * word still joins the family of its hanja but takes no part in homophone warnings.
 */
public final class MorphemeFamilies {
	private MorphemeFamilies() {
	}

	/** One word of a family; {@code learned} says whether the player has met it. */
	public record Member(String wordId, String ko, boolean learned) {
	}

	/**
	 * Everything that shares {@code hanja} with the word. {@code reading} is the syllable the word
	 * itself reads it as, or null when that cannot be told.
	 */
	public record Family(String hanja, String reading, List<Member> members) {
	}

	/** The syllable {@code reading} is written {@code hanja} in the selected word and {@code other} in a learned word. */
	public record Warning(String reading, String hanja, String other, String otherWordKo) {
	}

	public record Panel(List<Family> families, List<Warning> warnings) {
		public static final Panel NONE = new Panel(List.of(), List.of());

		public boolean isEmpty() {
			return families.isEmpty();
		}
	}

	/**
	 * The panel for a word. {@code catalog} is every word that may appear in a family, {@code learned}
	 * says which of them the player has met (they are listed first and are the only ones that can
	 * raise a homophone warning).
	 */
	public static Panel of(Word word, LexiconTags tags, Collection<Word> catalog, Predicate<Word> learned) {
		LexiconTags.Tag tag = word == null ? null : tags.tag(word);
		if (tag == null || tag.hanja().isEmpty() || "native".equals(tag.origin())) {
			return Panel.NONE;
		}
		List<String> readings = readings(word.ko, tag.hanja());
		List<Family> families = new ArrayList<>();
		Set<Warning> warnings = new LinkedHashSet<>();
		Set<String> seen = new LinkedHashSet<>();
		for (int i = 0; i < tag.hanja().size(); i++) {
			String hanja = tag.hanja().get(i);
			if (!seen.add(hanja)) {
				continue;
			}
			String reading = readings == null ? null : readings.get(i);
			List<Member> members = new ArrayList<>();
			for (Word other : catalog) {
				if (other.id.equals(word.id)) {
					continue;
				}
				LexiconTags.Tag otherTag = tags.tag(other);
				if (otherTag != null && otherTag.hanja().contains(hanja)) {
					members.add(new Member(other.id, other.ko, learned.test(other)));
				}
			}
			members.sort(Comparator.comparing((Member member) -> !member.learned())
					.thenComparing(Member::ko));
			families.add(new Family(hanja, reading, members));
			if (reading != null) {
				warnings.addAll(homophones(word, hanja, reading, tags, catalog, learned));
			}
		}
		return new Panel(List.copyOf(families), List.copyOf(warnings));
	}

	/** Other hanja read {@code reading} in learned words: one warning per hanja, with the first word that shows it. */
	private static List<Warning> homophones(Word word, String hanja, String reading, LexiconTags tags,
											Collection<Word> catalog, Predicate<Word> learned) {
		Map<String, Warning> found = new LinkedHashMap<>();
		for (Word other : catalog) {
			if (other.id.equals(word.id) || !learned.test(other)) {
				continue;
			}
			LexiconTags.Tag tag = tags.tag(other);
			if (tag == null || tag.hanja().isEmpty() || "native".equals(tag.origin())) {
				continue;
			}
			List<String> readings = readings(other.ko, tag.hanja());
			if (readings == null) {
				continue;
			}
			for (int i = 0; i < readings.size(); i++) {
				String candidate = tag.hanja().get(i);
				if (reading.equals(readings.get(i)) && !candidate.equals(hanja)) {
					found.putIfAbsent(candidate, new Warning(reading, hanja, candidate, other.ko));
				}
			}
		}
		return new ArrayList<>(found.values());
	}

	/**
	 * The syllable each hanja is read as in this word, or null when it cannot be told. The tag lists one
	 * hanja per syllable of the sino part; that part is the whole word when the counts agree, else the
	 * single eojeol of that length (ignoring a final 하다 or 되다), if exactly one fits.
	 */
	static List<String> readings(String ko, List<String> hanja) {
		if (ko == null || hanja.isEmpty()) {
			return null;
		}
		List<String> whole = syllables(ko);
		if (whole.size() == hanja.size()) {
			return whole;
		}
		String[] parts = ko.trim().split("\\s+");
		List<String> match = null;
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (i == parts.length - 1 && part.length() > 2 && (part.endsWith("하다") || part.endsWith("되다"))) {
				part = part.substring(0, part.length() - 2);
			}
			List<String> candidate = syllables(part);
			if (candidate.size() == hanja.size()) {
				if (match != null) {
					return null;
				}
				match = candidate;
			}
		}
		return match;
	}

	private static List<String> syllables(String text) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= 0xAC00 && c <= 0xD7A3) {
				out.add(String.valueOf(c));
			}
		}
		return out;
	}
}
