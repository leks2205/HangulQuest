package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.List;
import java.util.Objects;

/**
 * One clue step: a Korean instruction, the goal it verifies and the words it teaches.
 *
 * <p>The English and German clue and hint come from the lang files under {@code textKey}
 * ({@code hangulquest.trail.<id>.step.<sid>}, then {@code .clue} or {@code .hint}). The inline
 * {@code clueEn}-style fields are what a player's questline file may carry; they win over the keys.
 */
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
	/** Key prefix of this step's text in the lang files; null for a step built in code. */
	private final String textKey;

	public QuestStep(String id, List<String> teach, Goal goal, List<Condition> conditions,
					 String clueKo, String clueDe, String hintKo, String hintDe, Bonus bonus) {
		this(id, teach, goal, conditions, clueKo, clueDe, null, hintKo, hintDe, null, bonus);
	}

	public QuestStep(String id, List<String> teach, Goal goal, List<Condition> conditions,
					 String clueKo, String clueDe, String clueEn,
					 String hintKo, String hintDe, String hintEn, Bonus bonus) {
		this(id, teach, goal, conditions, clueKo, clueDe, clueEn, hintKo, hintDe, hintEn, bonus, null);
	}

	/** The canonical constructor: every field, including the key prefix of the lang texts. */
	public QuestStep(String id, List<String> teach, Goal goal, List<Condition> conditions,
					 String clueKo, String clueDe, String clueEn,
					 String hintKo, String hintDe, String hintEn, Bonus bonus, String textKey) {
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
		this.textKey = textKey;
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

	/** The German clue only (inline field, else its key); null when the step has none. */
	public String clueDe() {
		return I18n.own(Lang.DE, key("clue"), clueEn, clueDe);
	}

	/** The English clue only (inline field, else its key); null when the step has none. */
	public String clueEn() {
		return I18n.own(Lang.EN, key("clue"), clueEn, clueDe);
	}

	public String hintKo() {
		return hintKo;
	}

	public String hintDe() {
		return I18n.own(Lang.DE, key("hint"), hintEn, hintDe);
	}

	public String hintEn() {
		return I18n.own(Lang.EN, key("hint"), hintEn, hintDe);
	}

	public Bonus bonus() {
		return bonus;
	}

	/** Prefix of this step's keys in the lang files; null for a step that only has inline text. */
	public String textKey() {
		return textKey;
	}

	private String key(String field) {
		return textKey == null ? null : textKey + "." + field;
	}

	/** Clue in the current interface language. */
	public String clue() {
		return clue(Lang.current());
	}

	/** The clue in a language; English where that language has none, empty when the step has no text. */
	public String clue(Lang lang) {
		return nullToEmpty(I18n.content(lang, key("clue"), clueEn, clueDe));
	}

	/** The easier-Korean hint, in the current interface language. */
	public String hint() {
		return hint(Lang.current());
	}

	public String hint(Lang lang) {
		return nullToEmpty(I18n.content(lang, key("hint"), hintEn, hintDe));
	}

	public boolean hasClueKo() {
		return clueKo != null && !clueKo.isBlank();
	}

	/** Copy carrying inline English texts. */
	public QuestStep withEnglish(String englishClue, String englishHint) {
		return new QuestStep(id, teach, goal, conditions, clueKo, clueDe, englishClue,
				hintKo, hintDe, englishHint, bonus, textKey);
	}

	/** Copy whose lang texts are looked up under this key prefix. */
	public QuestStep withTextKey(String prefix) {
		return new QuestStep(id, teach, goal, conditions, clueKo, clueDe, clueEn,
				hintKo, hintDe, hintEn, bonus, prefix);
	}

	/** Copy under another step id; the text keys belong to the old id, so they are not carried over. */
	public QuestStep withId(String newId) {
		return new QuestStep(newId, teach, goal, conditions, clueKo, clueDe, clueEn,
				hintKo, hintDe, hintEn, bonus, null);
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
