package de.zirks.hangulquest.quest;

import java.util.List;

/** One clue step: a Korean instruction, the goal it verifies and the words it teaches. */
public record QuestStep(String id, List<String> teach, Goal goal, List<Condition> conditions,
						String clueKo, String clueDe, String hintKo, String hintDe, Bonus bonus) {
	public record Bonus(String structure, double radius) {
	}

	public QuestStep {
		teach = teach == null ? List.of() : List.copyOf(teach);
		conditions = conditions == null ? List.of() : List.copyOf(conditions);
	}

	public boolean hasClueKo() {
		return clueKo != null && !clueKo.isBlank();
	}
}
