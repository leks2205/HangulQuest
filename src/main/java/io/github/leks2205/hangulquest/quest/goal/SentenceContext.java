package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.quest.Verb;

/**
 * What a goal needs to speak Korean: the name of its object, the chosen verb and how hard the
 * sentence should be.
 *
 * @param obj        the noun the player sees, "" when nothing names it
 * @param verb       the verb picked from the verb table
 * @param withCounts whether a count shows up in the sentence (the hint leaves it out)
 * @param level      Korean level 1..6
 * @param finale     whether the sentence ends a trail
 */
public record SentenceContext(String obj, Verb verb, boolean withCounts, int level, boolean finale) {
}
