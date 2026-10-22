package io.github.leks2205.hangulquest.quest.grammar;

import io.github.leks2205.hangulquest.dialogue.SpeechLevel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The upper constructions (levels 4 to 6) in authored Korean, and the rule for where each may stand. The generator
 * makes its own sentences from the ladder; text a person wrote (a clue, a hint, a finale) is read here:
 * <ul>
 * <li>a construction is not used below its level;</li>
 * <li>-지만, -(으)ㄹ수록, -듯이 and -기 마련이다 are hints and finales (flavour and contrast), not the instruction;</li>
 * <li>-(으)ㄹ게요 is a promise and belongs to dialogue, since no goal checks one;</li>
 * <li>-(으)ㄹ 수밖에 없어요 states a forced conclusion: it needs the reason in the same sentence, and is no instruction
 * ending (the old error);</li>
 * <li>a trail speaks 해요체: 합쇼체 and 반말 are for dialogue lines.</li>
 * </ul>
 * Only constructions that cannot be taken for another word are looked for; -아/어서, -(으)러 and -아/어도 are generated
 * or not at all.
 */
public final class Constructions {
	/** Where a piece of authored text stands. */
	public enum Role {
		CLUE("an instruction"), HINT("a hint"), FINALE("a finale");

		private final String noun;

		Role(String noun) {
			this.noun = noun;
		}
	}

	private record Rule(String id, String form, Set<Role> roles, String why) {
	}

	private static final Set<Role> ALL = EnumSet.allOf(Role.class);
	private static final Set<Role> FLAVOUR = EnumSet.of(Role.HINT, Role.FINALE);

	/** In the order they are reported; the level of each comes from grammar.json. */
	private static final Map<String, Rule> RULES = new LinkedHashMap<>();

	static {
		rule("L4.myeon", "-(으)면", ALL, null);
		rule("L4.jiman", "-지만", FLAVOUR, "a contrast is a hint or a finale, not the instruction");
		rule("L4.eulgeyo", "-(으)ㄹ게요", EnumSet.noneOf(Role.class),
				"a promise belongs to dialogue; no goal checks one");
		rule("L5.eunikka", "-(으)니까", ALL, null);
		rule("L5.neun_dongan", "-는 동안", ALL, null);
		rule("L6.eulsurok", "-(으)ㄹ수록", FLAVOUR, "a flavour fact is a hint or a finale, not the instruction");
		rule("L6.deusi", "-듯이", FLAVOUR, "a flavour fact is a hint or a finale, not the instruction");
		rule("L6.gi_maryeon_ida", "-기 마련이다", FLAVOUR, "a flavour fact is a hint or a finale, not the instruction");
		rule("L6.su_bakke_eopda", "-(으)ㄹ 수밖에 없다", ALL, null);
	}

	private static void rule(String id, String form, Set<Role> roles, String why) {
		RULES.put(id, new Rule(id, form, roles, why));
	}

	/** Nouns that end in 면 and are no conditional ("라면" is noodles, "화면" the screen). */
	private static final Set<String> NOUNS_IN_MYEON = Set.of("라면", "화면", "평면", "표면", "지면", "수면", "해면", "국면",
			"측면", "방면", "단면", "전면", "후면", "내면", "외면", "정면", "가면", "도면", "면");

	/** The conjunctions that are words of their own ("하지만" is 'but'), not the construction. */
	private static final Set<String> CONJUNCTIONS = Set.of("하지만", "그렇지만", "그러나");

	private static final Pattern MYEON = Pattern.compile("([가-힣]+)면(?![가-힣])");
	private static final Pattern JIMAN = Pattern.compile("([가-힣]*지만)(?![가-힣])");
	private static final Pattern SUROK = Pattern.compile("[가-힣]수록(?![가-힣])");
	private static final Pattern REASON = Pattern.compile("(면|니까|때문에|라서|[아어해와워여]서|므로)[ ,]");
	private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s*");

	private Constructions() {
	}

	private static int jong(char c) {
		return c >= 0xAC00 && c <= 0xD7A3 ? (c - 0xAC00) % 28 : -1;
	}

	private static final int RIEUL = 8;
	private static final int BIEUP = 17;

	/** The ids of the constructions the text holds, in the order of {@link #RULES}. */
	public static List<String> found(String text) {
		List<String> ids = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return ids;
		}
		for (String id : RULES.keySet()) {
			if (holds(id, text)) {
				ids.add(id);
			}
		}
		return ids;
	}

	private static boolean holds(String id, String text) {
		return switch (id) {
			case "L4.myeon" -> conditional(text);
			case "L4.jiman" -> contrast(text);
			case "L4.eulgeyo" -> promise(text);
			case "L5.eunikka" -> reason(text);
			case "L5.neun_dongan" -> text.contains("는 동안");
			case "L6.eulsurok" -> SUROK.matcher(text).find();
			case "L6.deusi" -> text.contains("듯이");
			case "L6.gi_maryeon_ida" -> text.contains("기 마련");
			case "L6.su_bakke_eopda" -> text.contains("수밖에 없");
			default -> false;
		};
	}

	private static boolean conditional(String text) {
		Matcher match = MYEON.matcher(text);
		while (match.find()) {
			String word = match.group(1) + "면";
			if (!NOUNS_IN_MYEON.contains(word) && !NOUNS_IN_MYEON.contains(word.substring(word.length() - 2))) {
				return true;
			}
		}
		return false;
	}

	private static boolean contrast(String text) {
		Matcher match = JIMAN.matcher(text);
		while (match.find()) {
			if (!CONJUNCTIONS.contains(match.group(1))) {
				return true;
			}
		}
		return false;
	}

	/** -(으)ㄹ게요: a 게요 after a final ㄹ (열게요, 올게요). */
	private static boolean promise(String text) {
		for (int at = text.indexOf("게요"); at > 0; at = text.indexOf("게요", at + 1)) {
			if (jong(text.charAt(at - 1)) == RIEUL) {
				return true;
			}
		}
		return false;
	}

	/** -(으)니까: a 니까 that is no 합쇼체 question (갑니까, 있습니까 have a final ㅂ before it). */
	private static boolean reason(String text) {
		for (int at = text.indexOf("니까"); at > 0; at = text.indexOf("니까", at + 1)) {
			if (jong(text.charAt(at - 1)) != BIEUP) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The problems of one piece of authored text: {@code where} names it (trail and step). {@code koreanLevel} 0 means
	 * the level is not known and is not checked.
	 */
	public static List<String> problems(String where, String text, Role role, int koreanLevel) {
		List<String> problems = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return problems;
		}
		for (String id : found(text)) {
			Rule rule = RULES.get(id);
			int level = GrammarBook.shared().get(id).level();
			if (koreanLevel > 0 && level > koreanLevel) {
				problems.add(where + ": " + rule.form() + " (" + id + ") is level " + level
						+ ", above the level " + koreanLevel + " of this trail");
			}
			if (!rule.roles().contains(role)) {
				problems.add(where + ": " + rule.form() + " (" + id + ") is not for " + role.noun + ": " + rule.why());
			}
			if (id.equals("L6.su_bakke_eopda")) {
				forcedConclusion(where, text, problems);
			}
		}
		// a finale may open with the quote of a character ("끝 아녔어? — 엔드 수정 네 개로 …"): the line after the dash counts
		String spoken = text.substring(text.lastIndexOf(" — ") + 1);
		for (SpeechLevel level : SpeechLevel.ofLine(spoken)) {
			if (level != SpeechLevel.HAEYO) {
				problems.add(where + ": the text is " + level.id() + "; a trail speaks 해요체, "
						+ "합쇼체 and 반말 are for dialogue lines");
			}
		}
		return problems;
	}

	/** Each sentence with 수밖에 없다 gives the reason before it: it states a conclusion and does not instruct. */
	private static void forcedConclusion(String where, String text, List<String> problems) {
		for (String sentence : SENTENCE_BREAK.split(text)) {
			int at = sentence.indexOf("수밖에 없");
			if (at >= 0 && !REASON.matcher(sentence.substring(0, at)).find()) {
				problems.add(where + ": -(으)ㄹ 수밖에 없어요 states a forced conclusion, so the reason comes in the "
						+ "same sentence; it is no instruction ending");
			}
		}
	}
}
