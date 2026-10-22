package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.StepNamer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level 3 (ladder.json {@code connectives}) may join the two natural parts of a task with -고: gather and
 * craft, or build and place. Every other level keeps the single clause, and so does the hint.
 */
class ConnectiveSentencesTest {
	private static final String TORCH = "minecraft:torch";
	private static final String STONE = "minecraft:stone";
	private static final String STICK = "minecraft:stick";
	private static final String COAL = "minecraft:coal";

	private final Catalog catalog = KoreanFixtures.catalog();
	private final SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);

	private String say(Goal goal, int level) {
		return SentenceBuilder.normal(goal, null, List.of(), level, false, namer);
	}

	private static Goal craftTorch(String... with) {
		return new Goal.Craft(TORCH, 1, null, List.of(with));
	}

	@Test
	void onlyLevelThreeJoinsClauses() {
		for (int level = 1; level <= Ladder.LEVELS; level++) {
			assertEquals(level == 3, Ladder.shared().rung(level).connectives(), "level " + level);
		}
	}

	// -------------------------------------------------------------------- craft

	@Test
	void craftingWithIngredientsGathersThemFirstAtLevelThree() {
		assertEquals("막대기와 석탄을 모으고 횃불을 만드세요.", say(craftTorch(STICK, COAL), 3));
		assertEquals("석탄을 모으고 횃불을 만드세요.", say(craftTorch(COAL), 3));
		assertEquals("막대기, 돌과 석탄을 모으고 횃불을 만드세요.", say(craftTorch(STICK, STONE, COAL), 3));
	}

	@Test
	void everyOtherLevelKeepsTheMaterialClause() {
		Goal goal = craftTorch(STICK, COAL);
		assertEquals("막대기와 석탄으로 횃불을 만들어요.", say(goal, 1));
		assertEquals("막대기와 석탄으로 횃불을 만드세요.", say(goal, 2));
		assertEquals("막대기와 석탄으로 횃불을 만들어야 해요.", say(goal, 4));
		assertEquals("막대기와 석탄으로 횃불을 만들어야 해요.", say(goal, 6));
		assertEquals("막대기와 석탄으로 횃불을 만들어요.", SentenceBuilder.hint(goal, null, List.of(), namer),
				"the hint is the level-1 sentence");
	}

	@Test
	void craftingWithoutNamedIngredientsHasNothingToGather() {
		assertEquals("횃불을 만드세요.", say(craftTorch(), 3));
		Goal stationOnly = new Goal.Craft("minecraft:oak_planks", 4, "crafting_table", List.of());
		assertEquals("제작대에서 판자를 네 개 만드세요.", say(stationOnly, 3));
	}

	@Test
	void theStationBelongsToTheCraftingClause() {
		Goal goal = new Goal.Craft("minecraft:oak_planks", 4, "crafting_table", List.of("minecraft:oak_log"));
		assertEquals("통나무를 모으고 제작대에서 판자를 네 개 만드세요.", say(goal, 3));
	}

	@Test
	void theConnectiveIsOneConstructionNotTwo() {
		List<String> ids = SentenceBuilder.grammar(craftTorch(STICK, COAL), null, List.of(), 3, false, namer);
		assertTrue(ids.containsAll(List.of("L2.go_and", "L2.wa_gwa", "L1.eul_reul", "L2.seyo")), ids.toString());
		assertFalse(ids.contains("L2.euro_material"), "the material clause is gone from this sentence");
		List<String> plain = SentenceBuilder.grammar(craftTorch(STICK, COAL), null, List.of(), 2, false, namer);
		assertTrue(plain.contains("L2.euro_material") && !plain.contains("L2.go_and"), plain.toString());
	}

	@Test
	void aTimePrefixStaysInFront() {
		List<Condition> night = List.of(new Condition.Time(Condition.Phase.NIGHT));
		assertEquals("밤에 막대기와 석탄을 모으고 횃불을 만드세요.",
				SentenceBuilder.normal(craftTorch(STICK, COAL), null, night, 3, false, namer));
	}

	@Test
	void aPartOfAnotherSentenceNeverGetsAConnectiveOfItsOwn() {
		// "횃불을 들고 …을 모으고 …을 만드세요" would pile three clauses on one another
		Goal hold = new Goal.HoldWhile(TORCH, craftTorch(STICK, COAL), null, 10);
		String ko = say(hold, 3);
		assertEquals("횃불을 들고 막대기와 석탄으로 횃불을 만드세요.", ko);
		Goal sequence = new Goal.Sequence(List.of(new Goal.PlaceBlock(STONE, 1), craftTorch(STICK, COAL)), true, "AFTER");
		assertEquals("돌을 놓은 후에 막대기와 석탄으로 횃불을 만드세요.", say(sequence, 3));
	}

	@Test
	void holdingStillSaysDeulgoAtEveryLevel() {
		Goal hold = new Goal.HoldWhile(TORCH, null, new Condition.Time(Condition.Phase.NIGHT), 10);
		for (int level = 1; level <= 6; level++) {
			assertTrue(say(hold, level).startsWith("횃불을 들고 밤에 걸"), say(hold, level));
		}
	}

	// ------------------------------------------------------------ build and place

	private static Goal onPattern(Relation relation, Pattern pattern, String material) {
		return new Goal.PlaceOnPattern(TORCH, relation, pattern, material);
	}

	@Test
	void aStructureNobodyBuiltIsBuiltInTheSameSentenceAtLevelThree() {
		assertEquals("돌로 기둥을 쌓고 그 위에 횃불을 놓으세요.", say(onPattern(Relation.ON_TOP_OF, Pattern.PILLAR, STONE), 3));
		assertEquals("돌로 피라미드를 쌓고 그 위에 횃불을 놓으세요.", say(onPattern(Relation.ON_TOP_OF, Pattern.PYRAMID, STONE), 3));
		assertEquals("돌로 벽을 쌓고 그 옆에 횃불을 놓으세요.", say(onPattern(Relation.NEXT_TO, Pattern.WALL, STONE), 3));
		assertEquals("돌로 담을 쌓고 그 안에 횃불을 놓으세요.", say(onPattern(Relation.INSIDE, Pattern.RING, STONE), 3));
		assertEquals("돌로 십자 모양을 만들고 그 위에 횃불을 놓으세요.", say(onPattern(Relation.ON_TOP_OF, Pattern.CROSS, STONE), 3));
		assertEquals("돌로 계단을 만들고 그 위에 횃불을 놓으세요.", say(onPattern(Relation.ON_TOP_OF, Pattern.STAIRS, STONE), 3));
	}

	@Test
	void otherLevelsAndTheHintNameTheStructureAsItIs() {
		Goal goal = onPattern(Relation.ON_TOP_OF, Pattern.PILLAR, STONE);
		assertEquals("돌 기둥 위에 횃불을 놓아요.", say(goal, 1));
		assertEquals("돌 기둥 위에 횃불을 놓으세요.", say(goal, 2));
		assertEquals("돌 기둥 위에 횃불을 놓아야 해요.", say(goal, 4));
		assertEquals("돌 기둥 위에 횃불을 놓아요.", SentenceBuilder.hint(goal, null, List.of(), namer));
		assertEquals("돌 기둥 위에 횃불을 놓았어요.", SentenceBuilder.ceremony(goal, null, List.of(), namer));
	}

	@Test
	void relationsAndShapesThatDoNotReadAsOneSentenceStayPlain() {
		assertEquals("돌 기둥 아래에 횃불을 놓으세요.", say(onPattern(Relation.UNDER, Pattern.PILLAR, STONE), 3));
		assertEquals("돌 담 밖에 횃불을 놓으세요.", say(onPattern(Relation.OUTSIDE, Pattern.RING, STONE), 3));
		assertEquals("돌 피라미드 근처에 횃불을 놓으세요.", say(onPattern(Relation.NEAR, Pattern.PYRAMID, STONE), 3));
		assertEquals("돌 세 개 위에 횃불을 놓으세요.", say(onPattern(Relation.ON_TOP_OF, Pattern.ROW, STONE), 3));
	}

	@Test
	void aStructureAnEarlierStepBuiltIsNotBuiltAgain() {
		Goal build = new Goal.BuildPattern(Pattern.PILLAR, STONE, 3);
		Goal place = onPattern(Relation.ON_TOP_OF, Pattern.PILLAR, STONE);
		Questline trail = trail(build, place);
		QuestStep second = trail.step("s2");
		assertEquals("돌 기둥 위에 횃불을 놓으세요.",
				SentenceBuilder.normal(second.goal(), null, List.of(), 3, false, StepNamer.of(namer, second)));

		// a pillar of something else was not built: this one has to be
		Goal other = onPattern(Relation.ON_TOP_OF, Pattern.PILLAR, "minecraft:cobblestone");
		QuestStep otherStep = trail(build, other).step("s2");
		assertEquals("조약돌로 기둥을 쌓고 그 위에 횃불을 놓으세요.",
				SentenceBuilder.normal(otherStep.goal(), null, List.of(), 3, false, StepNamer.of(namer, otherStep)));

		// a structure an earlier step already stood a torch on counts as built, too
		Goal firstPlace = onPattern(Relation.NEXT_TO, Pattern.PILLAR, STONE);
		QuestStep later = trail(firstPlace, place).step("s2");
		assertEquals("돌 기둥 위에 횃불을 놓으세요.",
				SentenceBuilder.normal(later.goal(), null, List.of(), 3, false, StepNamer.of(namer, later)));
		// and an earlier step of another shape does not
		QuestStep wall = trail(new Goal.BuildPattern(Pattern.WALL, STONE, 3), place).step("s2");
		assertEquals("돌로 기둥을 쌓고 그 위에 횃불을 놓으세요.",
				SentenceBuilder.normal(wall.goal(), null, List.of(), 3, false, StepNamer.of(namer, wall)));
	}

	private static Questline trail(Goal... goals) {
		List<QuestStep> steps = new java.util.ArrayList<>();
		for (int i = 0; i < goals.length; i++) {
			steps.add(new QuestStep("s" + (i + 1), List.of(), goals[i], List.of(), null, null, null, null, null, null, null));
		}
		return new Questline("probe", "시험", "Probe", 1, List.of(), null, steps,
				new QuestStep("finale", List.of(), new Goal.PlaceBlock(TORCH, 1), List.of(), null, null, null, null, null, null, null),
				"끝", "Ende", null, 1, 3, false);
	}

	@Test
	void theCombinedSentenceCarriesBothConstructions() {
		List<String> ids = SentenceBuilder.grammar(onPattern(Relation.ON_TOP_OF, Pattern.PILLAR, STONE), null, List.of(), 3,
				false, namer);
		assertTrue(ids.containsAll(List.of("L1.loc_wi_e", "L2.euro_material", "L2.go_sequence", "L1.obj_eul", "L2.seyo")),
				ids.toString());
		List<String> plain = SentenceBuilder.grammar(onPattern(Relation.ON_TOP_OF, Pattern.PILLAR, STONE), null, List.of(), 2,
				false, namer);
		assertFalse(plain.contains("L2.go_sequence"), plain.toString());
	}

	@Test
	void theRelationWordIsStillInTheSentence() {
		for (Relation relation : List.of(Relation.ON_TOP_OF, Relation.NEXT_TO, Relation.INSIDE)) {
			Pattern pattern = relation == Relation.INSIDE ? Pattern.RING : Pattern.PILLAR;
			assertTrue(say(onPattern(relation, pattern, STONE), 3).contains(" 그 " + relation.ko() + "에 "),
					relation.name());
		}
	}
}
