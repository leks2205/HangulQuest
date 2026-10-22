package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.goal.Matching;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.EnumSet;

/** The shared rule of the two "place a block somewhere relative to something" goals. */
public final class Placements {
	private Placements() {
	}

	/**
	 * Counts the placements of {@code block} that stand in {@code relation} to the anchor. A right block
	 * in the wrong relation counts for nothing but leaves a {@link NearMiss} note for the status line.
	 * With {@code nightLatch} only placements made at night count (the morning already came).
	 */
	public static void evaluate(String block, Relation relation, Anchor anchor, Observation obs,
								GoalEvaluator.Tracker tracker, boolean nightLatch) {
		boolean hit = false;
		Relation wrong = null;
		for (PlacementFact fact : obs.placements) {
			if (!Matching.accepts(tracker, block, fact.block()) || nightLatch && !fact.night()) {
				continue;
			}
			EnumSet<Relation> held = Relations.holding(anchor, fact, obs.nearbyEntityPositions);
			if (held.contains(relation)) {
				tracker.count++;
				hit = true;
				remember(tracker, fact);
			} else {
				Relation slip = Relations.wrongRelation(relation, held);
				if (slip != null) {
					tracker.formErrors++;
					wrong = wrong == null ? slip : wrong;
				}
			}
		}
		if (hit) {
			NearMiss.clear(tracker);
		} else if (wrong != null) {
			NearMiss.mark(tracker, wrong, obs.now);
		}
	}

	/** Notes where the placement that counted stands; the step's completion keeps it for "@sN" anchors. */
	public static void remember(GoalEvaluator.Tracker tracker, PlacementFact fact) {
		tracker.lastPlaced = new int[]{fact.pos().getX(), fact.pos().getY(), fact.pos().getZ()};
	}

	/** The status line: the progress counter, or the reminder of what the relation word means. */
	public static String status(Relation relation, GoalEvaluator.Tracker tracker, Observation obs, int needed,
								boolean counted) {
		if (NearMiss.current(tracker, obs.now) != null) {
			return relation.ko() + " = " + relation.gloss(Lang.current())
					+ " · 다시 해 봐요";
		}
		return counted ? QuestStatus.count(tracker.count, needed) : "";
	}
}
