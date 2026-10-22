package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.Anchor;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.Relations;
import io.github.leks2205.hangulquest.quest.spatial.SpatialSentences;
import io.github.leks2205.hangulquest.quest.spatial.World;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generator limits BATCH-002 ran into: covers in the validator, every breaking verb, anchors without a
 * block name (water), tags spoken with their everyday word, snow against the snow block, the sky
 * condition on the ladder and the note block. Authored clues with conditions and the ratchet are in
 * their own tests.
 */
class GeneratorLimitsTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMER = KoreanFixtures.namer(CATALOG);
	private static final String TORCH = "minecraft:torch";

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	private static String clue(Goal goal, String concept, int level, Condition... conditions) {
		return SentenceBuilder.normal(goal, concept == null ? null : word(concept), List.of(conditions), level, false,
				NAMER);
	}

	private static String clue(Goal goal, String concept) {
		return clue(goal, concept, 1);
	}

	private static String ceremony(Goal goal, String concept) {
		return SentenceBuilder.ceremony(goal, concept == null ? null : word(concept), List.of(),
				KoreanFixtures.registryNamer());
	}

	private static List<String> problems(Goal goal, String... teach) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(teach), goal, List.of(), null, null, "Do it.", null, null, null,
				null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	private static boolean done(Goal goal, Observation obs) {
		return GoalEvaluator.tick(goal, List.of(), obs, new GoalEvaluator.Tracker());
	}

	// ------------------------------------------------------- 2. the validator reads items and covers

	@Test
	void aWordAcceptsTheGoalsItCoversNotOnlyItsFirstItem() {
		// 묘목 lists oak_sapling as its item and every other sapling as covers
		assertEquals(List.of(), problems(new Goal.PlaceBlock("minecraft:spruce_sapling", 1), "sapling"));
		assertEquals(List.of(), problems(new Goal.PlaceBlock("minecraft:oak_sapling", 1), "sapling"));
		assertEquals(List.of(), problems(new Goal.PlaceBlock("minecraft:white_bed", 1), "bed"));
		assertEquals(List.of(), problems(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:birch_door", 1),
				"torch"));
		// an anchor is covered too
		assertEquals(List.of(), problems(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:birch_door", 1),
				"door"));
		// but a word does not cover what it neither names nor covers
		List<String> wrong = problems(new Goal.PlaceBlock("minecraft:oak_log", 1), "sapling");
		assertEquals(1, wrong.size(), wrong.toString());
		assertTrue(wrong.get(0).contains("does not describe the goal"), wrong.get(0));
	}

	@Test
	void theSnowWordCoversTheSnowBlock() {
		assertEquals(List.of(), problems(new Goal.LookAtBlock("minecraft:snow_block", 600), "snow"));
		assertEquals(List.of(), problems(new Goal.LookAtBlock("minecraft:snow", 600), "snow"));
		assertEquals(List.of(), problems(new Goal.LookAtBlock("minecraft:snow_block", 600), "snow_block"));
	}

	// ---------------------------------------------------------------- 3. any verb for breaking

	@Test
	void breakBlockAcceptsEveryVerbTheTableUsesForBreaking() {
		List<String> allowed = VerbTable.shared().verbsFor("BREAK_BLOCK").stream().map(Verb::name).toList();
		assertTrue(allowed.containsAll(List.of("MINE", "CHOP", "HARVEST", "DIG", "SNAP")), allowed.toString());
		for (String verb : allowed) {
			assertEquals(List.of(), problems(new Goal.BreakBlock("minecraft:dirt", 1, verb), "dirt"), verb);
		}
		assertEquals("흙을 파요.", clue(new Goal.BreakBlock("minecraft:dirt", 1, "DIG"), "dirt"));
		assertEquals("꽃을 꺾어요.", clue(new Goal.BreakBlock("minecraft:poppy", 1, "SNAP"), "flower"));
		// the table's own choice is still the default when no verb is written
		assertEquals("흙을 파요.", clue(new Goal.BreakBlock("minecraft:dirt", 1, null), "dirt"));
		assertEquals("꽃을 꺾어요.", clue(new Goal.BreakBlock("minecraft:poppy", 1, null), "flower"));
		assertEquals("돌을 캐요.", clue(new Goal.BreakBlock("minecraft:stone", 1, null), "stone"));
	}

	@Test
	void breakBlockRefusesAVerbThatIsNotForBreaking() {
		List<String> wrong = problems(new Goal.BreakBlock("minecraft:dirt", 1, "PLACE"), "dirt");
		assertEquals(1, wrong.size(), wrong.toString());
		assertTrue(wrong.get(0).contains("does not fit breaking"), wrong.get(0));
		assertFalse(problems(new Goal.BreakBlock("minecraft:dirt", 1, "NOPE"), "dirt").isEmpty());
	}

	@Test
	void theSchemaListsTheVerbsOfTheTable() {
		Field verb = GoalTypes.of(new Goal.BreakBlock("minecraft:dirt", 1, null)).fields().stream()
				.filter(field -> field.name().equals("verb")).findFirst().orElseThrow();
		List<String> fromTable = VerbTable.shared().verbsFor("BREAK_BLOCK").stream().map(Verb::name).toList();
		assertEquals(fromTable, verb.values());
		assertTrue(verb.values().contains("DIG") && verb.values().contains("SNAP"));
	}

	@Test
	void aBlockTagBreaksWithTheVerbOfItsMembers() {
		Goal flowers = new Goal.BreakBlock("#minecraft:flowers", 1, null);
		assertEquals(List.of(), problems(flowers, "flower"));
		assertEquals("꽃을 꺾어요.", clue(flowers, "flower"));
		assertEquals("꽃을 꺾어요.", clue(flowers, null));
		assertEquals("꽃을 꺾었어요.", ceremony(flowers, null));
		assertEquals("꽃을 두 송이 꺾어요.", clue(new Goal.BreakBlock("#minecraft:flowers", 2, null), null));
		assertFalse(problems(new Goal.BreakBlock("#minecraft:nonsense", 1, null), "flower").isEmpty());
		// any flower counts, a stone does not
		Observation dandelion = new Observation();
		dandelion.brokenBlocks.add("minecraft:dandelion");
		assertTrue(done(flowers, dandelion));
		Observation stone = new Observation();
		stone.brokenBlocks.add("minecraft:stone");
		assertFalse(done(flowers, stone));
	}

	@Test
	void theSnowCanBeDugAndFlowersSnappedFromJson() {
		JsonObject snow = JsonParser.parseString(
				"{\"type\":\"BREAK_BLOCK\",\"block\":\"minecraft:snow_block\",\"count\":1,\"verb\":\"DIG\"}")
				.getAsJsonObject();
		Goal parsed = QuestLoader.parseGoal(snow);
		assertEquals(new Goal.BreakBlock("minecraft:snow_block", 1, "DIG"), parsed);
		assertEquals("눈 블록을 파요.", clue(parsed, "snow_block"));
	}

	// ---------------------------------------------- 4. a fluid is an anchor like any other block

	@Test
	void waterAndLavaHaveKoreanNames() {
		assertEquals("물", CATALOG.byItemId("minecraft:water").orElseThrow().ko);
		assertEquals("용암", CATALOG.byItemId("minecraft:lava").orElseThrow().ko);
		assertEquals("물 옆에 횃불을 놓아요.", clue(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:water", 1), null));
		assertEquals("물 옆에 횃불을 놓았어요.", ceremony(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:water", 1), null));
		assertEquals("용암 아래에 횃불을 놓아요.", clue(new Goal.PlaceRel(TORCH, Relation.UNDER, "minecraft:lava", 1), null));
	}

	@Test
	void aWaterAnchorValidatesAndIsFoundInTheNeighbourhood() {
		Goal beside = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:water", 1);
		assertEquals(List.of(), problems(beside, "torch"));
		assertEquals(List.of(), problems(beside, "water"));

		// a torch placed beside a block of water, then one placed far away from it
		PlacementFact near = new World().put(10, 64, 10, TORCH).put(11, 64, 10, "minecraft:water")
				.placed(new BlockPos(10, 64, 10));
		PlacementFact far = new World().put(10, 64, 10, TORCH).put(30, 64, 10, "minecraft:water")
				.placed(new BlockPos(10, 64, 10));
		Anchor water = new Anchor.Block("minecraft:water");
		assertTrue(Relations.holds(Relation.NEXT_TO, water, near));
		assertTrue(Relations.holds(Relation.NEAR, water, near));
		assertFalse(Relations.holds(Relation.NEXT_TO, water, far));
		assertFalse(Relations.holds(Relation.NEAR, water, far));

		Observation beside1 = new Observation();
		beside1.addPlacement(near);
		assertTrue(done(beside, beside1));
		Observation beside2 = new Observation();
		beside2.addPlacement(far);
		assertFalse(done(beside, beside2));
	}

	// ----------------------------------------------- 5. a tag is spoken with its everyday word

	private static final Map<String, String> TAG_WORDS = Map.ofEntries(
			Map.entry("#minecraft:flowers", "꽃"), Map.entry("#minecraft:logs", "통나무"),
			Map.entry("#minecraft:beds", "침대"), Map.entry("#minecraft:wool", "양털"),
			Map.entry("#minecraft:planks", "판자"), Map.entry("#minecraft:doors", "문"),
			Map.entry("#minecraft:fences", "울타리"), Map.entry("#minecraft:saplings", "묘목"),
			Map.entry("#minecraft:leaves", "나뭇잎"), Map.entry("#minecraft:torches", "횃불"),
			Map.entry("#minecraft:chests", "상자"));

	@Test
	void everyTagHasItsGeneralWord() {
		for (var entry : TAG_WORDS.entrySet()) {
			assertTrue(BlockSpec.valid(entry.getKey()), entry.getKey());
			assertEquals(entry.getValue(), BlockSpec.tagWord(entry.getKey()), entry.getKey());
		}
		assertNull(BlockSpec.tagWord("minecraft:oak_log"));
		assertNull(BlockSpec.tagWord("#minecraft:nonsense"));
	}

	@Test
	void theWordOfATagIsTheConceptWithTheLargestSetInsideIt() {
		List<Concept> concepts = ConceptLoader.load();
		for (var entry : TAG_WORDS.entrySet()) {
			String tag = entry.getKey();
			int best = 0;
			for (Concept concept : concepts) {
				List<String> blocks = new ArrayList<>(concept.items);
				blocks.addAll(concept.covers);
				if (blocks.stream().allMatch(block -> BlockSpec.matches(tag, block))) {
					best = Math.max(best, blocks.size());
				}
			}
			Concept chosen = concepts.stream().filter(c -> c.ko.equals(entry.getValue())).findFirst().orElseThrow();
			assertEquals(best, chosen.items.size() + chosen.covers.size(), tag + " should name the biggest family");
			assertTrue(best > 1 || tag.equals("#minecraft:torches") || tag.equals("#minecraft:chests"), tag);
		}
	}

	@Test
	void aTagAnchorIsNeverThatThing() {
		for (var entry : TAG_WORDS.entrySet()) {
			Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, entry.getKey(), 1);
			String said = clue(goal, null);
			assertFalse(said.contains("그것"), said);
			assertTrue(said.startsWith(entry.getValue() + " 옆에"), said);
		}
		assertEquals("꽃 옆에 횃불을 놓아요.",
				clue(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "#minecraft:flowers", 1), null));
		assertEquals("묘목 위에 횃불을 놓아요.",
				clue(new Goal.PlaceRel(TORCH, Relation.ON_TOP_OF, "#minecraft:saplings", 1), null));
		assertEquals("나뭇잎 옆에 횃불을 놓아요.",
				clue(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "#minecraft:leaves", 1), null));
	}

	@Test
	void theCeremonySaysTheWordOfTheClue() {
		for (var entry : TAG_WORDS.entrySet()) {
			Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, entry.getKey(), 1);
			assertEquals(clue(goal, null).replace("놓아요.", "놓았어요."), ceremony(goal, null), entry.getKey());
		}
		// the vanilla name of the log is 참나무 원목, the word of the clue 통나무
		Goal logs = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "#minecraft:logs", 1);
		assertEquals("통나무 옆에 횃불을 놓았어요.", ceremony(logs, null));
		// and a word the step teaches wins over the general one in both
		assertEquals("나무 옆에 횃불을 놓아요.", clue(logs, "tree"));
		assertEquals("나무 옆에 횃불을 놓았어요.", ceremony(logs, "tree"));
	}

	@Test
	void aTagMatchesItsMembersAndNothingElse() {
		assertTrue(BlockSpec.matches("#minecraft:flowers", "minecraft:cornflower"));
		assertFalse(BlockSpec.matches("#minecraft:flowers", "minecraft:oak_leaves"));
		assertTrue(BlockSpec.matches("#minecraft:saplings", "minecraft:cherry_sapling"));
		assertFalse(BlockSpec.matches("#minecraft:saplings", "minecraft:oak_log"));
		assertTrue(BlockSpec.matches("#minecraft:leaves", "minecraft:azalea_leaves"));
		assertFalse(BlockSpec.matches("#minecraft:leaves", "minecraft:oak_sapling"));
		// a torch beside a flower bed counts for the anchor "flowers"
		PlacementFact fact = new World().put(10, 64, 10, TORCH).put(10, 63, 10, "minecraft:cornflower")
				.placed(new BlockPos(10, 64, 10));
		assertTrue(Relations.holds(Relation.ON_TOP_OF, new Anchor.Block("#minecraft:flowers"), fact));
	}

	// ------------------------------------------------------------- 6. snow and the snow block

	@Test
	void theSnowBlockHasItsOwnWord() {
		assertEquals("눈 블록", CATALOG.byItemId("minecraft:snow_block").orElseThrow().ko);
		assertEquals("눈", CATALOG.byItemId("minecraft:snow").orElseThrow().ko);
		assertEquals("snow_block", CATALOG.byItemId("minecraft:snow_block").orElseThrow().conceptId);
		Concept snow = ConceptLoader.load().stream().filter(c -> c.id.equals("snow")).findFirst().orElseThrow();
		assertEquals(List.of("minecraft:snow"), snow.items, "the layer");
		assertTrue(snow.covers.contains("minecraft:snow_block"), "the word 눈 still covers the block");
	}

	@Test
	void craftingOrBuildingSnowBlocksSaysSnowBlock() {
		Goal craft = new Goal.Craft("minecraft:snow_block", 1, null, List.of());
		assertEquals("눈 블록을 만들어요.", clue(craft, null));
		assertEquals("눈 블록을 만들어요.", clue(craft, "snow_block"));
		assertEquals("눈 블록을 만들었어요.", ceremony(craft, "snow_block"));
		Goal pillar = new Goal.BuildPattern(io.github.leks2205.hangulquest.quest.spatial.Pattern.PILLAR,
				"minecraft:snow_block", 3);
		assertTrue(ceremony(pillar, "snow_block").startsWith("눈 블록 세 개로 기둥을"), ceremony(pillar, "snow_block"));
		assertTrue(clue(pillar, null).startsWith("눈 블록"), clue(pillar, null));
		// the layer and the snowball keep their words
		assertTrue(clue(new Goal.LookAtBlock("minecraft:snow", 600), "snow").startsWith("눈을 "));
	}

	// -------------------------------------------------------------- 7. the sky on the ladder

	@Test
	void anOpenSkyIsOutsideOnTheFirstTwoLevels() {
		Goal bread = new Goal.ConsumeItem("minecraft:bread", 1);
		Condition open = new Condition.Sky(Condition.SkyState.OPEN);
		assertEquals("밖에서 빵을 먹어요.", clue(bread, null, 1, open));
		assertEquals("밖에서 빵을 먹으세요.", clue(bread, null, 2, open));
		assertEquals("하늘이 보이는 곳에서 빵을 먹으세요.", clue(bread, null, 3, open));
		assertEquals("하늘이 보이는 곳에서 빵을 먹어야 해요.", clue(bread, null, 4, open));
		// the easy hint is level 1
		assertEquals("밖에서 빵을 먹어요.", SentenceBuilder.hint(bread, null, List.of(open), NAMER));
		// a roof is the same on every level
		Condition roofed = new Condition.Sky(Condition.SkyState.ROOFED);
		for (int level = 1; level <= 4; level++) {
			assertTrue(clue(bread, null, level, roofed).startsWith("지붕 아래에서 "), "level " + level);
		}
	}

	@Test
	void theLongSkyPhraseCarriesItsLevelThreeGrammarOnlyFromLevelThree() {
		Goal bread = new Goal.ConsumeItem("minecraft:bread", 1);
		List<Condition> open = List.of(new Condition.Sky(Condition.SkyState.OPEN));
		for (int level = 1; level <= 2; level++) {
			assertFalse(SentenceBuilder.grammar(bread, null, open, level, false, NAMER).contains("L3.neun_attr"),
					"level " + level);
		}
		assertTrue(SentenceBuilder.grammar(bread, null, open, 3, false, NAMER).contains("L3.neun_attr"));
	}

	// --------------------------------------------------------------------- 8. the note block

	@Test
	void aNoteBlockIsStruckAndABellIsRung() {
		assertEquals(Verb.STRIKE, SentenceBuilder.verbFor(new Goal.UseBlock("minecraft:note_block")));
		assertEquals(Verb.RING, SentenceBuilder.verbFor(new Goal.UseBlock("minecraft:bell")));
		String note = clue(new Goal.UseBlock("minecraft:note_block"), null);
		assertTrue(note.endsWith(" 쳐요."), note);
		assertFalse(note.contains("울려요"), note);
		assertEquals("종을 울려요.", clue(new Goal.UseBlock("minecraft:bell"), null));
		assertTrue(ceremony(new Goal.UseBlock("minecraft:note_block"), null).endsWith(" 쳤어요."));
		assertEquals("쳐요", Verb.STRIKE.polite());
		assertEquals("쳤어요", Verb.STRIKE.past());
		assertTrue(VerbTable.shared().inFamily("notes", "minecraft:note_block"));
		assertFalse(VerbTable.shared().inFamily("notes", "minecraft:bell"));
		assertTrue(VerbTable.shared().inFamily("bells", "minecraft:bell"));
		assertNotNull(Verb.byId("strike", null));
	}

	// ---------------------------------------------------------- the anchor words of the namer

	@Test
	void anchorNounFallsBackToThatThingOnlyForAnUnknownBlock() {
		SentenceBuilder.Namer none = SentenceBuilder.Namer.NONE;
		assertEquals("그것", SpatialSentences.anchorNoun("minecraft:no_such_block", none));
		// a tag still has its word without any namer
		assertEquals("꽃", SpatialSentences.anchorNoun("#minecraft:flowers", none));
		assertEquals("통나무", SpatialSentences.anchorNoun("#minecraft:logs", none));
	}
}
