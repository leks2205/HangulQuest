package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.QuestStep;

import java.util.List;

/** The step being validated and where its problems are collected. {@code koreanLevel} is the trail's level (0 when unknown). */
public record ValidationContext(String where, QuestStep step, Catalog catalog, List<String> problems,
								int koreanLevel) {
	public ValidationContext(String where, QuestStep step, Catalog catalog, List<String> problems) {
		this(where, step, catalog, problems, 0);
	}

	public void problem(String message) {
		problems.add(where + ": " + message);
	}

	/** True when the id is not a valid namespaced identifier. */
	public static boolean badId(String id) {
		if (id == null || id.isBlank()) {
			return true;
		}
		try {
			net.minecraft.resources.Identifier.parse(id);
			return false;
		} catch (RuntimeException broken) {
			return true;
		}
	}
}
