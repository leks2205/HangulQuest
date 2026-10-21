package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;

/**
 * A goal type whose sentence names more than its object (an anchor, a material), so it needs the
 * namer that resolves block ids to Korean. {@link SentenceBuilder} calls these instead of the plain
 * {@link GoalType#korean} and {@link GoalType#ceremony} when a type implements this.
 */
public interface NamerAware<G extends Goal> {
	String korean(G goal, SentenceContext context, SentenceBuilder.Namer namer);

	String ceremony(G goal, String obj, Verb verb, SentenceBuilder.Namer namer);
}
