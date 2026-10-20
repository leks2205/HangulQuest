package de.zirks.hangulquest;

import de.zirks.hangulquest.data.Catalog;
import de.zirks.hangulquest.data.ConceptLoader;
import de.zirks.hangulquest.quest.Condition;
import de.zirks.hangulquest.quest.Goal;
import de.zirks.hangulquest.quest.GoalEvaluator;
import de.zirks.hangulquest.quest.QuestStatus;
import de.zirks.hangulquest.quest.QuestStep;
import de.zirks.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestStatusTest {
	private static final Catalog CATALOG = Catalog.ofConcepts(ConceptLoader.load());

	private static QuestStep step(Goal goal, List<Condition> conditions, String... teach) {
		return new QuestStep("s", List.of(teach), goal, conditions,
				null, null, null, null, null);
	}

	@Test
	void heightShowsYourYAndTarget() {
		Observation obs = new Observation();
		obs.y = 62;
		String text = QuestStatus.text(step(new Goal.ReachHeight(-64, 0), List.of()), obs,
				new GoalEvaluator.Tracker(), CATALOG);
		assertTrue(text.contains("Y 62"), text);
		assertTrue(text.contains("0 이하"), text);
	}

	@Test
	void unmetTimeSaysWhatIsMissing() {
		Observation obs = new Observation();
		obs.clockTime = 1000L;
		String text = QuestStatus.text(step(new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(new Condition.Time(Condition.Phase.NIGHT))), obs,
				new GoalEvaluator.Tracker(), CATALOG);
		assertTrue(text.contains("낮"), text);
		assertTrue(text.contains("밤"), text);
	}

	@Test
	void unmetBiomeNamesBoth() {
		Observation obs = new Observation();
		obs.biome = "minecraft:forest";
		String text = QuestStatus.text(step(new Goal.ReachBiome("minecraft:beach", 1000), List.of()),
				obs, new GoalEvaluator.Tracker(), CATALOG);
		assertTrue(text.contains("숲"), text);
		assertTrue(text.contains("해변"), text);
	}

	@Test
	void acquireShowsHeldOfNeeded() {
		Observation obs = new Observation();
		obs.inventory.put("minecraft:bread", 1);
		String text = QuestStatus.text(step(new Goal.AcquireItem("minecraft:bread", 2), List.of(),
				"bread"), obs, new GoalEvaluator.Tracker(), CATALOG);
		assertTrue(text.contains("1/2"), text);
	}

	@Test
	void lookShowsPercent() {
		Observation obs = new Observation();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.dwellMs = 400;
		String text = QuestStatus.text(step(new Goal.LookAtEntity("minecraft:cow", 800), List.of(),
				"cow"), obs, tracker, CATALOG);
		assertTrue(text.contains("50%"), text);
	}

	@Test
	void placeShowsCount() {
		Observation obs = new Observation();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.count = 1;
		String text = QuestStatus.text(step(new Goal.PlaceBlock("minecraft:torch", 3), List.of(),
				"torch"), obs, tracker, CATALOG);
		assertTrue(text.contains("1/3"), text);
	}

	@Test
	void bedUseWithoutSleepExplainsItself() {
		Observation obs = new Observation();
		obs.clockTime = 1000L;
		obs.bedUseRecent = true;
		String text = QuestStatus.text(step(new Goal.UseBlock("minecraft:red_bed"),
				List.of(new Condition.Time(Condition.Phase.NIGHT))), obs,
				new GoalEvaluator.Tracker(), CATALOG);
		assertTrue(text.contains("잘 수 없어요"), text);
	}

}
