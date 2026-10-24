package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.quest.grammar.Constructions;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;

import java.util.ArrayList;
import java.util.List;

/**
 * The grammar of a conversation line against the conversation's level: a construction the game can recognise
 * (-(으)면, -지만, -(으)ㄹ게요, -(으)니까, -는 동안, …) may not be above it. 합쇼체 is the speech level of SNOOTY,
 * the librarian and the elder at every level and is not counted here.
 */
public final class LineLevels {
	private LineLevels() {
	}

	public static List<String> problems(String where, String line, int level) {
		List<String> problems = new ArrayList<>();
		for (String id : Constructions.found(line)) {
			int needed = GrammarBook.shared().get(id).level();
			if (needed > level) {
				problems.add(where + ": " + GrammarBook.shared().get(id).form() + " (" + id + ") is level " + needed
						+ ", above the level " + level + " of this conversation");
			}
		}
		return problems;
	}
}
