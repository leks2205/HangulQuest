package io.github.leks2205.hangulquest.quest.condition;

/**
 * How a condition is spoken in front of the goal. {@link #PLAIN} is the phrase of levels 1 to 3 ("밤에", "비가 올 때");
 * the others are the connective forms of levels 4 and 5 ("밤이 되면", "해가 지는 동안", "밤이 되어도"). There is no
 * -(으)니까 here: a reason cannot be derived from a condition and an action (a generated "낮이니까 가마솥을 써야 해요"
 * would assert a cause nobody checked), so it stays in authored hints and finales, where the validator gates it.
 */
public enum ConditionForm {
	PLAIN(null, 1),
	/** -(으)면: the action is right once the condition holds. */
	IF("L4.myeon", 4),
	/** -는 동안: the condition keeps holding while the action goes on. */
	WHILE("L5.neun_dongan", 5),
	/** -아/어도: the action is wrong (or right) although the condition holds; used inside an avoided action. */
	EVEN_IF("L5.eodo", 5);

	private final String grammar;
	private final int minLevel;

	ConditionForm(String grammar, int minLevel) {
		this.grammar = grammar;
		this.minLevel = minLevel;
	}

	/** The construction of grammar.json this form teaches; null for the plain phrase. */
	public String grammar() {
		return grammar;
	}

	/** The Korean level from which the generator speaks this form. */
	public int minLevel() {
		return minLevel;
	}

	/**
	 * Whether the form says when the action counts, which makes it a deciding form: -(으)면 and -는 동안 name the
	 * condition under which the step is done. -아/어도 only adds a concession.
	 */
	public boolean decides() {
		return this == IF || this == WHILE;
	}
}
