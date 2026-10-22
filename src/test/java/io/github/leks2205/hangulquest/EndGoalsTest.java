package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.sense.EndWatch;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generator wave 13, items 5 and 13: the goals for an end crystal set down, an end gateway entered, an effect. */
class EndGoalsTest {
	private static final SentenceBuilder.Namer REGISTRY = KoreanFixtures.registryNamer();
	private static final Goal CRYSTAL = new Goal.PlaceEntity("minecraft:end_crystal", 1);
	private static final Goal GATEWAY = new Goal.EnterGateway("minecraft:end_gateway");
	private static final Goal LEVITATE = new Goal.HaveEffect("minecraft:levitation");

	private static String clue(Goal goal, int level) {
		return SentenceBuilder.normal(goal, null, List.of(), level, false, REGISTRY);
	}

	private static List<String> problems(Goal goal) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(), goal, List.of(), null, null, "Do it.", null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, KoreanFixtures.catalog(), problems, 6));
		return problems;
	}

	// ---------------------------------------------------------------- the goals

	@Test
	void anEndCrystalSetDownCounts() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(CRYSTAL, List.of(), new Observation(), tracker));
		Observation obs = new Observation();
		obs.placedEntities.add("minecraft:cow");
		assertFalse(GoalEvaluator.tick(CRYSTAL, List.of(), obs, tracker), "another creature is no crystal");
		Observation crystal = new Observation();
		crystal.placedEntities.add("minecraft:end_crystal");
		assertTrue(GoalEvaluator.tick(CRYSTAL, List.of(), crystal, tracker));
	}

	@Test
	void theGatewayThrowCounts() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(GATEWAY, List.of(), new Observation(), tracker));
		Observation thrown = new Observation();
		thrown.gatewayUsed = true;
		assertTrue(GoalEvaluator.tick(GATEWAY, List.of(), thrown, tracker));
	}

	@Test
	void anEffectCountsWhileThePlayerHasIt() {
		Observation without = new Observation();
		assertFalse(GoalEvaluator.tick(LEVITATE, List.of(), without, new GoalEvaluator.Tracker()));
		Observation other = new Observation();
		other.effects = Set.of("minecraft:speed");
		assertFalse(GoalEvaluator.tick(LEVITATE, List.of(), other, new GoalEvaluator.Tracker()));
		Observation floating = new Observation();
		floating.effects = Set.of("minecraft:speed", "minecraft:levitation");
		assertTrue(GoalEvaluator.tick(LEVITATE, List.of(), floating, new GoalEvaluator.Tracker()));
	}

	@Test
	void theyAreWrittenAndReadAsJson() {
		for (Goal goal : List.of(CRYSTAL, GATEWAY, LEVITATE)) {
			var json = GoalTypes.toJson(goal);
			assertEquals(goal, GoalTypes.parse(json), goal.toString());
		}
	}

	@Test
	void theyAreSpokenInPlainKorean() {
		assertEquals("엔드 수정을 놓아요.", clue(CRYSTAL, 1));
		assertEquals("엔드 수정을 놓으세요.", clue(CRYSTAL, 3));
		assertEquals("엔드 수정을 놓아야 해요.", clue(CRYSTAL, 4));
		assertEquals("엔드 관문에 들어가요.", clue(GATEWAY, 1));
		assertEquals("엔드 관문에 들어가야 해요.", clue(GATEWAY, 5));
		assertEquals("공중 부양 효과를 받아요.", clue(LEVITATE, 1));
		assertEquals("공중 부양 효과를 받으세요.", clue(LEVITATE, 3));
	}

	@Test
	void theFinaleSaysOnlyWhatWasChecked() {
		assertEquals("엔드 수정을 놓았어요.", SentenceBuilder.ceremony(CRYSTAL, null, List.of(), REGISTRY).replaceAll("^.* — ", ""));
		assertEquals("엔드 관문에 들어갔어요.", SentenceBuilder.ceremony(GATEWAY, null, List.of(), REGISTRY));
		assertEquals("공중 부양 효과를 받았어요.", SentenceBuilder.ceremony(LEVITATE, null, List.of(), REGISTRY));
	}

	@Test
	void theyValidateOnlyWhatTheClientCanSee() {
		assertEquals(List.of(), problems(CRYSTAL));
		assertEquals(List.of(), problems(GATEWAY));
		assertEquals(List.of(), problems(LEVITATE));
		assertFalse(problems(new Goal.PlaceEntity("minecraft:cow", 1)).isEmpty());
		assertFalse(problems(new Goal.EnterGateway("minecraft:end_portal")).isEmpty());
		assertFalse(problems(new Goal.HaveEffect("minecraft:speed")).isEmpty());
	}

	@Test
	void theEffectNameIsTheVanillaOne() {
		assertEquals("공중 부양", KoreanFixtures.koNames().get("effect.minecraft.levitation"));
		assertNotNull(clue(LEVITATE, 1));
	}

	@Test
	void twoGoalsOfTheSameTypeAreTwoTasks() {
		Condition night = new Condition.Time(Condition.Phase.NIGHT);
		assertFalse(GoalTypes.of(CRYSTAL).taskSignature(CRYSTAL).equals(GoalTypes.of(GATEWAY).taskSignature(GATEWAY)));
		assertNotNull(night);
	}

	// ---------------------------------------------------------------- what the sensor decides

	@Test
	void aThrowFromBesideAGatewayIsTheGatewayAndNothingElseIs() {
		EndWatch watch = new EndWatch();
		assertFalse(watch.gatewayUsed("minecraft:the_end", 0, 70, 0, true));
		assertTrue(watch.gatewayUsed("minecraft:the_end", 600, 70, 0, false), "far away at once, from beside a gateway");

		EndWatch fall = new EndWatch();
		fall.gatewayUsed("minecraft:the_end", 0, 70, 0, false);
		assertFalse(fall.gatewayUsed("minecraft:the_end", 0, 20, 0, false), "a fall is not beside a gateway");

		EndWatch walk = new EndWatch();
		walk.gatewayUsed("minecraft:the_end", 0, 70, 0, true);
		assertFalse(walk.gatewayUsed("minecraft:the_end", 1, 70, 0, true), "a step is not a throw");

		EndWatch dimension = new EndWatch();
		dimension.gatewayUsed("minecraft:the_end", 0, 70, 0, true);
		assertFalse(dimension.gatewayUsed("minecraft:overworld", 600, 70, 0, false), "a dimension change is no gateway");

		EndWatch again = new EndWatch();
		again.gatewayUsed("minecraft:the_end", 0, 70, 0, true);
		assertTrue(again.gatewayUsed("minecraft:the_end", 600, 70, 0, false));
		assertFalse(again.gatewayUsed("minecraft:the_end", 600, 70, 0, false), "once");
	}

	@Test
	void aCrystalThatAppearsNextToAPlayerWithTheItemIsTheirs() {
		UUID a = UUID.randomUUID();
		EndWatch watch = new EndWatch();
		watch.hands(true, 1000L);
		assertTrue(watch.crystalSeen(a, 1, 3.0, 1100L));
		assertFalse(watch.crystalSeen(a, 2, 3.0, 1150L), "the same crystal is not counted twice");

		EndWatch island = new EndWatch();
		island.hands(true, 1000L);
		assertFalse(island.crystalSeen(UUID.randomUUID(), 4000, 3.0, 1100L), "an old crystal was always there");

		EndWatch empty = new EndWatch();
		assertFalse(empty.crystalSeen(UUID.randomUUID(), 1, 3.0, 1100L), "the item was never in a hand");

		EndWatch far = new EndWatch();
		far.hands(true, 1000L);
		assertFalse(far.crystalSeen(UUID.randomUUID(), 1, 30.0, 1100L), "set down by somebody else");

		EndWatch late = new EndWatch();
		late.hands(true, 1000L);
		assertFalse(late.crystalSeen(UUID.randomUUID(), 1, 3.0, 9000L), "long after the item was in the hand");
	}
}
