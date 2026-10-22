package io.github.leks2205.hangulquest.quest.condition;

import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Which conditions of a sentence are spoken as a clause of the level-4 and level-5 grammar. Only one clause
 * stands in front of a sentence: the last condition (in spoken order) that has such a form takes it, the others keep
 * their plain phrase ("밤이 되면 해변에서 …", not "밤이 되면 비가 오면 …").
 */
public final class ConditionForms {
	private ConditionForms() {
	}

	/**
	 * The form of each spoken condition. Level 4 speaks -(으)면; from level 5 a wait speaks its condition as -는 동안,
	 * since the waiting goes on while it holds.
	 */
	public static List<ConditionForm> choose(boolean waiting, List<Condition> spoken, int level) {
		List<ConditionForm> forms = new ArrayList<>();
		ConditionForm wanted = waiting && level >= ConditionForm.WHILE.minLevel() ? ConditionForm.WHILE : ConditionForm.IF;
		// the last one that has the form takes it: "밤에 비가 오면 …", not "밤이 되면 비가 올 때 …"
		int chosen = -1;
		if (level >= ConditionForm.IF.minLevel()) {
			for (int i = 0; i < spoken.size(); i++) {
				if (has(spoken.get(i), wanted)) {
					chosen = i;
				}
			}
		}
		for (int i = 0; i < spoken.size(); i++) {
			forms.add(i == chosen ? wanted : ConditionForm.PLAIN);
		}
		return forms;
	}

	/** The form of the condition inside an avoided action: -(으)면 at level 4, -아/어도 from level 5. */
	public static ConditionForm forAvoid(Condition during, int level) {
		ConditionForm wanted = level >= ConditionForm.EVEN_IF.minLevel() ? ConditionForm.EVEN_IF
				: level >= ConditionForm.IF.minLevel() ? ConditionForm.IF : ConditionForm.PLAIN;
		return wanted != ConditionForm.PLAIN && has(during, wanted) ? wanted : ConditionForm.PLAIN;
	}

	/** The spoken clause of the condition in this form, or null for the plain form and for a condition without one. */
	public static String phrase(Condition condition, SentenceBuilder.Namer namer, ConditionForm form) {
		return form == ConditionForm.PLAIN ? null : ConditionTypes.of(condition).phrase(condition, namer, form);
	}

	private static boolean has(Condition condition, ConditionForm form) {
		return phrase(condition, SentenceBuilder.Namer.NONE, form) != null;
	}
}
