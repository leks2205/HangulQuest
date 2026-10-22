package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.quest.condition.ConditionForm;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The constructions whose form decides a step: the relation word of a placement, the recipient marker of a
 * hand-over, the connective of an ordered sequence, the "don't" of an avoid, and from level 4 the clause that says
 * when the step counts (-(으)면, -(으)니까, -는 동안). Completing the step rightly is evidence for them, a slip in the
 * form is evidence against. Ids are those of grammar.json.
 */
public final class DecidingForms {
	private DecidingForms() {
	}

	/** The deciding constructions of the step as its text is written at {@code level}. */
	public static List<String> of(QuestStep step, int level) {
		Goal goal = step.goal();
		if (goal == null) {
			return List.of();
		}
		GoalType<Goal> type = GoalTypes.of(goal);
		Set<String> ids = new LinkedHashSet<>(type.decidingForms(goal, level));
		for (ConditionForm form : SentenceBuilder.conditionForms(goal, step.conditions(), level)) {
			if (form.decides()) {
				ids.add(form.grammar());
			}
		}
		return GrammarBook.shared().resolve(List.copyOf(ids), Ladder.shared());
	}
}
