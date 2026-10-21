package io.github.leks2205.hangulquest;

import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.Role;
import io.github.leks2205.hangulquest.quest.spatial.SpatialSentences;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Korean of the spatial goals: a gold table, the particles by role and the never-dropped relation word. */
class SpatialSentencesTest {
	private static final SentenceBuilder.Namer NAMER = KoreanFixtures.namer(KoreanFixtures.catalog());

	private record Row(int line, String kind, int level, boolean night, Goal goal, String expected,
					   List<String> grammar) {
	}

	private static List<Row> gold() {
		try (InputStream stream = SpatialSentencesTest.class.getResourceAsStream("/spatial-sentences-gold.tsv")) {
			assertTrue(stream != null, "src/test/resources/spatial-sentences-gold.tsv is missing");
			List<Row> rows = new ArrayList<>();
			int line = 0;
			for (String text : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				line++;
				if (text.isBlank() || text.startsWith("#")) {
					continue;
				}
				String[] cells = text.split("\t", -1);
				Goal goal = GoalTypes.parse(JsonParser.parseString(cells[3]).getAsJsonObject());
				assertTrue(goal != null, "line " + line + ": unknown goal " + cells[3]);
				List<String> grammar = cells[5].isBlank() ? List.of() : List.of(cells[5].split(";"));
				rows.add(new Row(line, cells[0], Integer.parseInt(cells[1]), cells[2].equals("1"), goal, cells[4],
						grammar));
			}
			return rows;
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	private static String sentence(Row row) {
		List<Condition> conditions = row.night() ? List.of(new Condition.Time(Condition.Phase.NIGHT)) : List.of();
		return switch (row.kind()) {
			case "clue" -> SentenceBuilder.normal(row.goal(), null, conditions, row.level(), false, NAMER);
			case "finale" -> SentenceBuilder.normal(row.goal(), null, conditions, row.level(), true, NAMER);
			case "hint" -> SentenceBuilder.hint(row.goal(), null, conditions, NAMER);
			case "ceremony" -> SentenceBuilder.ceremony(row.goal(), null, conditions, NAMER);
			default -> throw new IllegalStateException("line " + row.line() + ": unknown kind " + row.kind());
		};
	}

	@Test
	void everyGoldSentenceIsWhatTheGeneratorSays() {
		List<Row> rows = gold();
		assertTrue(rows.size() >= 40, "the gold table needs at least 40 sentences, has " + rows.size());
		List<String> wrong = new ArrayList<>();
		for (Row row : rows) {
			String actual = sentence(row);
			if (!actual.equals(row.expected())) {
				wrong.add("line " + row.line() + " (" + row.kind() + " L" + row.level() + "): expected '"
						+ row.expected() + "' but got '" + actual + "'");
			}
		}
		assertTrue(wrong.isEmpty(), String.join("\n", wrong));
	}

	@Test
	void theGoldTableCoversEveryRelationPatternLevelAndKind() {
		Set<Relation> relations = EnumSet.noneOf(Relation.class);
		Set<Pattern> patterns = EnumSet.noneOf(Pattern.class);
		Set<Integer> levels = new java.util.TreeSet<>();
		Set<String> kinds = new java.util.TreeSet<>();
		Set<String> types = new java.util.TreeSet<>();
		for (Row row : gold()) {
			switch (row.goal()) {
				case Goal.PlaceRel g -> relations.add(g.relation());
				case Goal.PlaceOnPattern g -> {
					relations.add(g.relation());
					patterns.add(g.pattern());
				}
				case Goal.BuildPattern g -> patterns.add(g.pattern());
				default -> {
				}
			}
			levels.add(row.level());
			kinds.add(row.kind());
			types.add(row.goal().getClass().getSimpleName());
		}
		assertEquals(EnumSet.allOf(Relation.class), relations, "relations in the gold table");
		assertEquals(EnumSet.allOf(Pattern.class), patterns, "patterns in the gold table");
		assertEquals(Set.of(1, 2, 3, 4, 5, 6), levels);
		assertEquals(Set.of("clue", "finale", "hint", "ceremony"), kinds);
		assertEquals(Set.of("PlaceRel", "BuildPattern", "PlaceOnPattern"), types);
	}

	@Test
	void everySentenceReportsItsGrammarPoints() {
		for (Row row : gold()) {
			List<Condition> conditions = row.night() ? List.of(new Condition.Time(Condition.Phase.NIGHT)) : List.of();
			boolean hint = row.kind().equals("hint");
			assertEquals(row.grammar(), SpatialSentences.grammarPoints(row.goal(), conditions,
					hint ? 1 : row.level(), !hint, row.kind().equals("ceremony")),
					"line " + row.line() + ": " + row.expected());
		}
	}

	@Test
	void everyReportedGrammarIdIsInTheLadder() throws IOException {
		Set<String> ladder = new java.util.HashSet<>();
		try (InputStream stream = SpatialSentencesTest.class.getResourceAsStream("/assets/hangulquest/data/grammar.json")) {
			for (var element : JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonArray("constructions")) {
				ladder.add(element.getAsJsonObject().get("id").getAsString());
			}
		}
		for (Row row : gold()) {
			for (String id : row.grammar()) {
				assertTrue(ladder.contains(id), "line " + row.line() + ": " + id + " is not in grammar.json");
			}
		}
		for (Relation relation : Relation.values()) {
			for (Role role : Role.values()) {
				for (String id : relation.grammarIds(role)) {
					assertTrue(ladder.contains(id), relation + " " + role + ": " + id);
				}
			}
		}
	}

	@Test
	void theTargetTakesEAndTheActivityPlaceTakesEseo() {
		assertEquals("문 옆에", SentenceBuilder.relationPhrase("문", Relation.NEXT_TO, Role.TARGET));
		assertEquals("피라미드 위에", SentenceBuilder.relationPhrase("피라미드", Relation.ON_TOP_OF, Role.TARGET));
		assertEquals("두 횃불 사이에", SentenceBuilder.relationPhrase("두 횃불", Relation.BETWEEN, Role.TARGET));
		assertEquals("횃불 아래에서", SentenceBuilder.relationPhrase("횃불", Relation.UNDER, Role.ACTIVITY_PLACE));
		assertEquals("울타리 안에서", SentenceBuilder.relationPhrase("울타리", Relation.INSIDE, Role.ACTIVITY_PLACE));
		assertEquals("집 밖에서", SentenceBuilder.relationPhrase("집", Relation.OUTSIDE, Role.ACTIVITY_PLACE));
		assertEquals("문 근처에서", SentenceBuilder.relationPhrase("문", Relation.NEAR, Role.ACTIVITY_PLACE));
		for (Relation relation : Relation.values()) {
			assertEquals("집 " + relation.ko() + "에", SentenceBuilder.relationPhrase("집", relation, Role.TARGET));
			assertEquals("집 " + relation.ko() + "에서", SentenceBuilder.relationPhrase("집", relation, Role.ACTIVITY_PLACE));
		}
	}

	@Test
	void theGrammarIdsFollowTheRole() {
		assertEquals(List.of("L1.loc_wi_e"), Relation.ON_TOP_OF.grammarIds(Role.TARGET));
		assertEquals(List.of("L1.loc_wi_e", "L2.eseo_activity"), Relation.ON_TOP_OF.grammarIds(Role.ACTIVITY_PLACE));
		assertEquals(List.of("L2.loc_sai_e"), Relation.BETWEEN.grammarIds(Role.TARGET));
		assertEquals(List.of(), Relation.NEAR.grammarIds(Role.TARGET), "근처 has no entry in the ladder");
		assertEquals(List.of("L2.eseo_activity"), Relation.NEAR.grammarIds(Role.ACTIVITY_PLACE));
	}

	@Test
	void theMaterialTakesTheRightEuroForAnyFinalSound() {
		for (String[] pair : new String[][]{{"minecraft:stone", "돌로"}, {"minecraft:dirt", "흙으로"},
				{"minecraft:sand", "모래로"}, {"minecraft:cobblestone", "조약돌로"}}) {
			Goal goal = new Goal.BuildPattern(Pattern.PYRAMID, pair[0], 3);
			String ko = SentenceBuilder.normal(goal, null, List.of(), 1, false, NAMER);
			assertTrue(ko.startsWith(pair[1] + " "), ko);
		}
	}

	/** The relation is the point of the task: no level, hint, count or finale form may leave it out. */
	@Test
	void theRelationWordIsNeverDropped() {
		List<Goal> goals = new ArrayList<>();
		for (Relation relation : Relation.values()) {
			goals.add(new Goal.PlaceRel("minecraft:torch", relation, "minecraft:chest", 1));
			goals.add(new Goal.PlaceRel("minecraft:torch", relation, "minecraft:chest", 3));
			goals.add(new Goal.PlaceOnPattern("minecraft:torch", relation, Pattern.RING, "minecraft:stone"));
			goals.add(new Goal.PlaceOnPattern("minecraft:torch", relation, Pattern.ROW, "minecraft:stone"));
		}
		for (Goal goal : goals) {
			Relation relation = goal instanceof Goal.PlaceRel g ? g.relation() : ((Goal.PlaceOnPattern) goal).relation();
			String marker = " " + relation.ko() + "에";
			for (int level = 1; level <= 6; level++) {
				for (boolean finale : new boolean[]{false, true}) {
					String ko = SentenceBuilder.normal(goal, null, List.of(), level, finale, NAMER);
					assertTrue(ko.contains(marker), ko);
				}
			}
			assertTrue(SentenceBuilder.hint(goal, null, List.of(), NAMER).contains(marker));
			assertTrue(SentenceBuilder.ceremony(goal, null, List.of(), NAMER).contains(marker));
		}
	}

	@Test
	void sentencesWithoutAnyNamesStillWork() {
		// the default namer knows nothing: the anchor falls back to a neutral word instead of failing
		Goal goal = new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "minecraft:chest", 1);
		assertTrue(SentenceBuilder.normal(goal, null, List.of(), 1).startsWith("그것 옆에 "));
	}

	/**
	 * The shared task-sentence table (task-sentences-gold.tsv): every one-anchor placement row whose
	 * objects are plain blocks must come out exactly as the table says (the lever is left out: the table says
	 * 달아요 beside a door but 놓아요 on a stone, which no object-based verb rule can do).
	 */
	@Test
	void theSharedTaskSentenceTableAgrees() throws IOException {
		java.util.Map<String, String> block = new java.util.HashMap<>();
		for (String id : new String[]{"torch", "chest", "furnace", "crafting_table", "stone", "hay_block", "ladder",
				"apple", "pumpkin", "bell", "painting", "oak_door"}) {
			block.put(id, "minecraft:" + id);
		}
		block.put("#beds", "#minecraft:beds");
		block.put("#doors", "#minecraft:doors");
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		try (InputStream stream = SpatialSentencesTest.class.getResourceAsStream("/task-sentences-gold.tsv")) {
			for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				String[] c = line.split("\t", -1);
				if (c.length < 8 || !c[0].equals("PLACE_RELATIVE") || !c[4].equals("TARGET") || !c[5].equals("1")) {
					continue;
				}
				String object = c[1].replaceAll(" x\\d+$", "");
				int count = c[1].matches(".* x\\d+") ? Integer.parseInt(c[1].replaceAll(".* x", "")) : 1;
				if (!block.containsKey(object) || !block.containsKey(c[3]) || c[2].equals("INSIDE")
						|| c[2].equals("OUTSIDE") && !c[3].equals("#doors") || c[7].contains("e_time")) {
					continue;
				}
				Goal goal = new Goal.PlaceRel(block.get(object), Relation.valueOf(c[2]), block.get(c[3]), count);
				String actual = SentenceBuilder.normal(goal, null, List.of(), 1, false, NAMER);
				checked++;
				if (!actual.equals(c[6] + ".")) {
					wrong.add(c[6] + " | generated " + actual);
				}
			}
		}
		assertTrue(checked >= 20, "only " + checked + " rows compared");
		assertTrue(wrong.isEmpty(), String.join("\n", wrong));
	}
}
