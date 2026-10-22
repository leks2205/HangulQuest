package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.Reach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reachability tiers the quest picker scores with agree with the gate that guards the beginner trails. */
class ReachTierTest {
	private static QuestStep step(Goal goal, Condition... conditions) {
		return new QuestStep("s", List.of(), goal, List.of(conditions), null, null, null, null, null);
	}

	private static Questline trail(QuestStep... steps) {
		return new Questline("t", "t", "t", 1, List.of(), Questline.Requires.none(), List.of(steps),
				step(new Goal.PlaceBlock("minecraft:torch", 1)), "", "", "t", 1, 1, false);
	}

	@Test
	void everyTrailTheGateFlagsIsOutOfReachHere() {
		List<String> missed = new ArrayList<>();
		for (String id : ReachabilityGateTest.violations().keySet()) {
			Questline trail = QuestLoader.loadAll().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
			if (!Reach.of(trail).outOfReach()) {
				missed.add(id);
			}
		}
		assertTrue(missed.isEmpty(), "the gate flags these but the picker would start them: " + missed);
	}

	@Test
	void everyStepTheGateFlagsIsFlaggedHere() {
		for (Questline trail : QuestLoader.loadAll()) {
			for (QuestStep step : trail.allSteps()) {
				if (ReachabilityGateTest.gate(step) != null) {
					assertNotNull(Reach.gate(step), trail.id() + "/" + step.id());
				}
			}
		}
	}

	@Test
	void theTiersCostWhatTheyShould() {
		assertEquals(Reach.Cost.FREE, Reach.of(trail(step(new Goal.PlaceBlock("minecraft:oak_log", 2)))));
		assertEquals(Reach.PLACE, Reach.of(trail(step(new Goal.LookAtEntity("minecraft:chicken", 800)))).penalty());
		assertEquals(Reach.PLACE_CAP, Reach.of(trail(step(new Goal.LookAtEntity("minecraft:chicken", 800)),
				step(new Goal.LookAtEntity("minecraft:cow", 800)), step(new Goal.ReachBiome("minecraft:river", 1000)))).penalty());
		assertEquals(Reach.STRUCTURE, Reach.of(trail(step(new Goal.UseBlock("minecraft:bell")))).penalty());
		assertEquals(Reach.STRUCTURE, Reach.of(trail(step(new Goal.ReachBiome("minecraft:mushroom_fields", 1000)))).penalty());
		assertEquals(Reach.ADVANCED, Reach.of(trail(step(new Goal.AcquireItem("minecraft:diamond", 1)))).penalty());
		assertEquals(Reach.IRON, Reach.of(trail(step(new Goal.Craft("minecraft:iron_pickaxe", 1, null, null)))).penalty());
	}

	@Test
	void whatLivesOnlyInOtherPlacesIsOutOfReach() {
		assertTrue(Reach.of(trail(step(new Goal.ReachDimension("minecraft:the_nether")))).outOfReach());
		assertTrue(Reach.of(trail(step(new Goal.DefeatEntity("minecraft:blaze", 1)))).outOfReach());
		assertTrue(Reach.of(trail(step(new Goal.BreakBlock("minecraft:ancient_debris", 1, null)))).outOfReach());
		assertTrue(Reach.of(trail(step(new Goal.PlaceBlock("minecraft:torch", 1),
				new Condition.InBiome("minecraft:deep_dark")))).outOfReach());
		assertTrue(Reach.of(trail(step(new Goal.BreakBlock("minecraft:stone", 1, null),
				new Condition.YRange(-60, -20)))).outOfReach(), "below y=0");
		assertFalse(Reach.of(trail(step(new Goal.BreakBlock("minecraft:stone", 1, null),
				new Condition.YRange(0, 40)))).outOfReach());
		assertNull(Reach.gate(step(new Goal.BreakBlock("minecraft:stone", 1, null))));
	}
}
