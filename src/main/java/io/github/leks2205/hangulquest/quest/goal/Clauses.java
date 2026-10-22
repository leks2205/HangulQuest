package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sub-goal sentences for the types that wrap other goals (sequence, hold, avoid). A part is spoken
 * in full, or cut open at its verb to hang a connective on it: "문을 닫기 전에", "빵을 먹은 후에".
 * The cut is made on the level-1 sentence, which always ends in the verb's polite form.
 */
public final class Clauses {
	/** How two actions are tied together; the suffix replaces the verb ending. */
	public enum Link {
		AND("L2.go_and"),
		THEN("L3.go_naseo"),
		AFTER("L3.eun_hue"),
		BEFORE("L3.gi_jeone");

		private final String grammar;

		Link(String grammar) {
			this.grammar = grammar;
		}

		public String grammar() {
			return grammar;
		}

		/** The verb joined to what follows: 닫기 전에, 먹은 후에, 놓고 나서, 놓고. */
		String attach(Verb verb) {
			return switch (this) {
				case AND -> verb.stem() + "고";
				case THEN -> verb.stem() + "고 나서";
				case AFTER -> verb.attributive() + " 후에";
				case BEFORE -> verb.stem() + "기 전에";
			};
		}
	}

	private static final Pattern NEGATION = Pattern.compile(
			"\\b(not|don'?t|do not|never|avoid|without|stop|nicht|nie|kein\\w*|vermeide\\w*|ohne|unterlass\\w*)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern ORDER_WORDS = Pattern.compile(
			"\\b(before|after|then|first|afterwards|until|vor|bevor|nach\\s+(?:dem|der|einem|einer|diesem|dieser|den)|nachdem|danach|dann|zuerst|erst)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

	private Clauses() {
	}

	/** The sentence context of a part of the step: its own object name and verb at the given level. */
	public static SentenceContext contextFor(Goal part, SentenceContext c, int level) {
		GoalType<Goal> type = GoalTypes.of(part);
		ObjectKind kind = type.objectKind();
		String obj = switch (kind) {
			case BLOCK, ITEM, ENTITY -> c.nameOfObject(kind, type.object(part));
			case NONE -> "";
			default -> {
				String name = type.objectName(part, c.namer());
				yield name == null ? "" : name;
			}
		};
		return c.forPart(obj, type.verb(part, VerbTable.shared()), withoutConnectives(level));
	}

	/**
	 * A part of a sentence that already has a connective does not get one of its own ("횃불을 들고 막대기를 모으고
	 * …"): it is spoken one level down, which has the same ending but no connectives.
	 */
	private static int withoutConnectives(int level) {
		int plain = level;
		while (plain > 1 && Ladder.shared().rung(plain).connectives()) {
			plain--;
		}
		return plain;
	}

	/** The tracker of sub-goal {@code index}, created with the others on first use. */
	static GoalEvaluator.Tracker sub(GoalEvaluator.Tracker parent, int index, int size) {
		if (parent.subs == null || parent.subs.size() != size) {
			parent.subs = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				parent.subs.add(new GoalEvaluator.Tracker());
			}
		}
		GoalEvaluator.Tracker sub = parent.subs.get(index);
		sub.accepted = parent.accepted;
		return sub;
	}

	/** The part as a full sentence at the level, with its full stop. */
	public static String sentence(Goal part, SentenceContext c, int level) {
		return GoalTypes.of(part).korean(part, contextFor(part, c, level));
	}

	/** The part in the past tense, without the full stop. */
	public static String ceremony(Goal part, SentenceContext c) {
		return GoalTypes.of(part).ceremony(part, contextFor(part, c, 1));
	}

	/** A sentence cut in front of its verb: {@code head} keeps the trailing space and any fixed object. */
	public record Split(String head, Verb verb) {
	}

	/** Cuts the part's level-1 sentence in front of its verb; null when it does not end the expected way. */
	public static Split split(Goal part, SentenceContext c) {
		if (part == null) {
			return null;
		}
		SentenceContext partContext = contextFor(part, c, 1);
		String sentence = GoalTypes.of(part).korean(part, partContext);
		String tail = partContext.verb().polite() + ".";
		if (sentence == null || !sentence.endsWith(tail)) {
			return null;
		}
		return new Split(sentence.substring(0, sentence.length() - tail.length()), partContext.verb());
	}

	/** The part with a connective in place of its ending: "문을 닫기 전에". Null when it cannot be cut. */
	public static String clause(Goal part, SentenceContext c, Link link) {
		Split split = split(part, c);
		return split == null ? null : split.head() + link.attach(split.verb());
	}

	/** "침대에서 자지 마세요" (no full stop). */
	public static String negative(Goal part, SentenceContext c) {
		Split split = split(part, withoutCounts(c));
		return split == null ? "" : split.head() + split.verb().stem() + "지 마세요";
	}

	/** "침대에서 자지 않았어요" (no full stop). */
	public static String pastNegative(Goal part, SentenceContext c) {
		Split split = split(part, withoutCounts(c));
		return split == null ? "" : split.head() + split.verb().stem() + "지 않았어요";
	}

	/** "Don't" has no count: not "한 마리 물리치지 마세요" but "물리치지 마세요". */
	private static SentenceContext withoutCounts(SentenceContext c) {
		return new SentenceContext(c.obj(), c.verb(), false, c.level(), c.finale(), c.word(), c.namer());
	}

	/** True for goals that can be a part, an inner goal or an avoided action: simple, and cuttable at the verb. */
	public static boolean usable(Goal part) {
		if (part == null || part instanceof Goal.Sequence || part instanceof Goal.Avoid
				|| part instanceof Goal.HoldWhile) {
			return false;
		}
		try {
			return split(part, new SentenceContext("물건", Verb.USE, true, 1, false)) != null;
		} catch (RuntimeException broken) {
			return false;
		}
	}

	/** Checks the registry ids of a part without comparing it to the taught word. */
	public static void validatePart(Goal part, ValidationContext context, String label) {
		QuestStep step = context.step();
		QuestStep bare = new QuestStep(step.id(), List.of(), part, List.of(), null, null, null, null, null);
		GoalTypes.of(part).validate(part, new ValidationContext(context.where() + "/" + label, bare,
				context.catalog(), context.problems(), context.koreanLevel()));
	}

	/** The translations must not give away the order (sequence) or the "not" (avoid). */
	public static void checkClueDoesNotTeach(ValidationContext context, boolean negation) {
		QuestStep step = context.step();
		Pattern giveaway = negation ? NEGATION : ORDER_WORDS;
		for (String clue : new String[]{step.clueEn(), step.clueDe()}) {
			if (clue != null && giveaway.matcher(clue.toLowerCase(Locale.ROOT)).find()) {
				context.problem("the translation gives away the grammar (" + (negation ? "the negation" : "the order")
						+ "): " + clue);
			}
		}
	}
}
