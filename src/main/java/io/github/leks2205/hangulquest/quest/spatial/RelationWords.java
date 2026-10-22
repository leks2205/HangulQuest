package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.Condition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words of the English and German translations that say where something goes, per relation, for the check
 * that a translation does not give the task away. Three rules keep the check honest:
 * <ul>
 * <li>only the words of the step's own relation count (an "auf" in a step about 옆 gives nothing away);</li>
 * <li>only separate words count, and a German preposition that ends its clause is the prefix of a separable verb
 * (<i>Stelle ein Schild auf.</i>), not a relation;</li>
 * <li>a phrase that a condition of the step produces ("unter der Erde" for a step below ground) is not
 * checked.</li>
 * </ul>
 */
public final class RelationWords {
	private record Words(List<String> en, List<String> dePrepositions, List<String> deAdverbs) {
	}

	private static final Map<Relation, Words> WORDS = new EnumMap<>(Relation.class);

	static {
		WORDS.put(Relation.ON_TOP_OF, new Words(List.of("on top", "atop", "on", "above", "over"),
				List.of("auf", "über"), List.of("oben", "drauf", "darauf", "obendrauf", "darüber")));
		WORDS.put(Relation.UNDER, new Words(List.of("under", "underneath", "beneath", "below"),
				List.of("unter", "unterhalb"), List.of("darunter")));
		WORDS.put(Relation.NEXT_TO, new Words(List.of("next to", "beside", "besides", "adjacent"),
				List.of("neben"), List.of("daneben")));
		WORDS.put(Relation.IN_FRONT_OF, new Words(List.of("in front"), List.of("vor"), List.of("davor")));
		WORDS.put(Relation.BEHIND, new Words(List.of("behind"), List.of("hinter"), List.of("dahinter")));
		WORDS.put(Relation.INSIDE, new Words(List.of("inside", "within"), List.of("innerhalb"),
				List.of("innen", "drinnen")));
		WORDS.put(Relation.OUTSIDE, new Words(List.of("outside"), List.of("außerhalb"), List.of("draußen", "außen")));
		WORDS.put(Relation.BETWEEN, new Words(List.of("between"), List.of("zwischen"), List.of("dazwischen")));
		WORDS.put(Relation.NEAR, new Words(List.of("near", "nearby", "around"), List.of("nahe"),
				List.of("nähe")));
	}

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

	private RelationWords() {
	}

	/**
	 * The relation word a translation gives away, or null. {@code relation} null checks the words of every
	 * relation; {@code conditions} are the conditions of the step, whose own phrases are left out.
	 */
	public static String leak(String translation, Relation relation, List<Condition> conditions) {
		if (translation == null) {
			return null;
		}
		String text = withoutConditionPhrases(translation, conditions);
		for (Relation each : Relation.values()) {
			if (relation != null && each != relation) {
				continue;
			}
			Words words = WORDS.get(each);
			String found = first(text, words.en(), false);
			if (found == null) {
				found = first(text, words.deAdverbs(), false);
			}
			if (found == null) {
				found = first(text, words.dePrepositions(), true);
			}
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/**
	 * The first of {@code words} that stands in the text as a word of its own. A preposition must be followed by
	 * a word: at the end of a clause it is the particle of a separable verb.
	 */
	private static String first(String text, List<String> words, boolean preposition) {
		for (String word : words) {
			String tail = preposition ? "(?=[\\s]+[\\p{L}])" : "\\b";
			Matcher found = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(word) + tail, FLAGS).matcher(text);
			if (found.find()) {
				return found.group();
			}
		}
		return null;
	}

	/** What the conditions of a step say by themselves, English and German. */
	private static List<String> phrases(Condition condition) {
		return switch (condition) {
			case Condition.YRange c -> List.of("underground", "under the ground", "below ground", "below the ground",
					"beneath the surface", "below the surface", "unter der Erde", "unter Tage", "unterirdisch",
					"unter der Oberfläche");
			case Condition.Sky c -> c.state() == Condition.SkyState.ROOFED
					? List.of("under a roof", "under the roof", "unter einem Dach", "unter dem Dach", "unter Dach")
					: List.of("outdoors", "outside", "in the open", "draußen", "im Freien", "unter freiem Himmel");
			case Condition.InWater c -> List.of("underwater", "under water", "unter Wasser");
			case Condition.NearEntity c -> List.of("near", "nearby", "close to", "in der Nähe", "nahe");
			case Condition.NearBlock c -> List.of("near", "nearby", "close to", "in der Nähe", "nahe");
			default -> List.of();
		};
	}

	private static String withoutConditionPhrases(String text, List<Condition> conditions) {
		if (conditions == null || conditions.isEmpty()) {
			return text;
		}
		List<String> all = new ArrayList<>();
		for (Condition condition : conditions) {
			all.addAll(phrases(condition));
		}
		// longest first, so "unter der Erde" goes before a bare "unter"
		all.sort((a, b) -> b.length() - a.length());
		String out = text;
		for (String phrase : all) {
			out = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(phrase) + "(?![\\p{L}])", FLAGS).matcher(out)
					.replaceAll(" ");
		}
		return out;
	}
}
