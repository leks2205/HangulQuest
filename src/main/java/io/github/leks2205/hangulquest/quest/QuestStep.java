package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.util.Tr;

import java.util.List;
import java.util.Objects;

/** One clue step: a Korean instruction, the goal it verifies and the words it teaches. */
public final class QuestStep {
	public record Bonus(String structure, double radius) {
	}

	private final String id;
	private final List<String> teach;
	private final Goal goal;
	private final List<Condition> conditions;
	private final String clueKo;
	private final String clueDe;
	private final String clueEn;
	private final String hintKo;
	private final String hintDe;
	private final String hintEn;
	private final Bonus bonus;

	public QuestStep(String id, List<String> teach, Goal goal, List<Condition> conditions,
					 String clueKo, String clueDe, String hintKo, String hintDe, Bonus bonus) {
		this(id, teach, goal, conditions, clueKo, clueDe, null, hintKo, hintDe, null, bonus);
	}

	public QuestStep(String id, List<String> teach, Goal goal, List<Condition> conditions,
					 String clueKo, String clueDe, String clueEn,
					 String hintKo, String hintDe, String hintEn, Bonus bonus) {
		this.id = id;
		this.teach = teach == null ? List.of() : List.copyOf(teach);
		this.goal = goal;
		this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
		this.clueKo = clueKo;
		this.clueDe = clueDe;
		this.clueEn = clueEn;
		this.hintKo = hintKo;
		this.hintDe = hintDe;
		this.hintEn = hintEn;
		this.bonus = bonus;
	}

	public String id() {
		return id;
	}

	public List<String> teach() {
		return teach;
	}

	public Goal goal() {
		return goal;
	}

	public List<Condition> conditions() {
		return conditions;
	}

	public String clueKo() {
		return clueKo;
	}

	public String clueDe() {
		return clueDe;
	}

	public String clueEn() {
		return clueEn;
	}

	public String hintKo() {
		return hintKo;
	}

	public String hintDe() {
		return hintDe;
	}

	public String hintEn() {
		return hintEn;
	}

	public Bonus bonus() {
		return bonus;
	}

	/** Clue in the current interface language. */
	public String clue() {
		return clue(Lang.current());
	}

	public String clue(Lang lang) {
		if (lang == Lang.DE) {
			return clueDe == null || clueDe.isBlank() ? nullToEmpty(clueEn) : clueDe;
		}
		return clueEn == null || clueEn.isBlank() ? nullToEmpty(clueDe) : clueEn;
	}

	/** The easier-Korean hint, in the current interface language. */
	public String hint() {
		return hint(Lang.current());
	}

	public String hint(Lang lang) {
		if (lang == Lang.DE) {
			return hintDe == null || hintDe.isBlank() ? nullToEmpty(hintEn) : hintDe;
		}
		return hintEn == null || hintEn.isBlank() ? nullToEmpty(hintDe) : hintEn;
	}

	public boolean hasClueKo() {
		return clueKo != null && !clueKo.isBlank();
	}

	/** Copy carrying the English texts, used by the loader. */
	public QuestStep withEnglish(String englishClue, String englishHint) {
		return new QuestStep(id, teach, goal, conditions, clueKo, clueDe, englishClue,
				hintKo, hintDe, englishHint, bonus);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof QuestStep step)) {
			return false;
		}
		return Objects.equals(id, step.id) && Objects.equals(goal, step.goal);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, goal);
	}

	@Override
	public String toString() {
		return "QuestStep[" + id + "]";
	}
}
