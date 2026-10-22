package io.github.leks2205.hangulquest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gold table of the action-variety sentences ({@code action-sentences-gold.tsv}): goal and
 * conditions as JSON, the level, the taught word, and the exact Korean and grammar points expected.
 * Registry names (names.json) stand in for the in-game language table.
 */
class ActionSentenceGoldTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMER = KoreanFixtures.registryNamer();

	record Row(String id, int level, String word, Goal goal, List<Condition> conditions, String ko,
			   Set<String> grammar) {
	}

	static List<Row> rows() throws IOException {
		List<Row> rows = new ArrayList<>();
		try (InputStream stream = ActionSentenceGoldTest.class.getResourceAsStream("/action-sentences-gold.tsv")) {
			assertNotNull(stream, "action-sentences-gold.tsv is missing");
			String[] lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n");
			for (int i = 1; i < lines.length; i++) {
				if (lines[i].isBlank()) {
					continue;
				}
				String[] cells = lines[i].split("\t", -1);
				assertEquals(7, cells.length, "line " + (i + 1) + " needs 7 columns: " + lines[i]);
				Goal goal = QuestLoader.parseGoal(JsonParser.parseString(cells[3]).getAsJsonObject());
				assertNotNull(goal, cells[0] + ": unknown goal");
				List<Condition> conditions = new ArrayList<>();
				if (!cells[4].equals("-")) {
					for (JsonElement element : JsonParser.parseString(cells[4]).getAsJsonArray()) {
						JsonObject object = element.getAsJsonObject();
						Condition condition = QuestLoader.parseCondition(object);
						assertNotNull(condition, cells[0] + ": unknown condition");
						conditions.add(condition);
					}
				}
				rows.add(new Row(cells[0], Integer.parseInt(cells[1]), cells[2], goal, conditions, cells[5],
						Set.of(cells[6].split(","))));
			}
		}
		return rows;
	}

	/** The taught word of a row: "-" none, "door" a noun, "chicken_meat+cook" a noun and a verb, "-+cook" a verb only. */
	private static Word word(String concept) {
		String noun = concept.contains("+") ? concept.substring(0, concept.indexOf('+')) : concept;
		return noun.equals("-") ? null : CATALOG.byConceptId(noun).orElseThrow();
	}

	private static Word verbWord(String concept) {
		return concept.contains("+") ? CATALOG.byConceptId(concept.substring(concept.indexOf('+') + 1)).orElseThrow()
				: null;
	}

	@Test
	void theTableHasAtLeastFiftySentences() throws IOException {
		List<Row> rows = rows();
		assertTrue(rows.size() >= 50, "gold table has only " + rows.size() + " rows");
		Set<String> ids = new HashSet<>();
		rows.forEach(row -> assertTrue(ids.add(row.id()), "duplicate id " + row.id()));
		// Every new goal type and condition is in the table.
		String all = rows.stream().map(row -> row.goal().getClass().getSimpleName()).distinct().sorted().toList().toString();
		for (String type : List.of("Craft", "Smelt", "Give", "PutIn", "HoldWhile", "Sequence", "Avoid", "TakeOut",
				"Transit", "Hear", "LieDown", "Wait", "Breed", "Mount", "Swim")) {
			assertTrue(all.contains(type), type + " is missing from the gold table");
		}
	}

	@Test
	void everySentenceMatchesTheGoldAndCarriesItsGrammarPoints() throws IOException {
		List<String> wrong = new ArrayList<>();
		for (Row row : rows()) {
			SentenceBuilder.Spoken spoken = SentenceBuilder.spoken(row.goal(), word(row.word()), verbWord(row.word()),
					row.conditions(), row.level(), false, NAMER);
			if (!row.ko().equals(spoken.ko())) {
				wrong.add(row.id() + ": expected " + row.ko() + " but got " + spoken.ko());
			}
			if (!row.grammar().equals(new HashSet<>(spoken.grammar()))) {
				wrong.add(row.id() + ": grammar expected " + row.grammar() + " but got " + spoken.grammar());
			}
			assertFalse(spoken.grammar().isEmpty(), row.id() + " reports no grammar point");
			assertEquals(spoken.grammar().size(), new HashSet<>(spoken.grammar()).size(), row.id() + " lists a point twice");
		}
		assertTrue(wrong.isEmpty(), String.join("\n", wrong));
	}

	/** The hint is the clue at level 1; the deciding form (the connective, the "not") must survive. */
	@Test
	void hintsStayAtLevelOneButKeepTheDecidingForm() throws IOException {
		for (Row row : rows()) {
			String hint = SentenceBuilder.hint(row.goal(), word(row.word()), verbWord(row.word()), row.conditions(),
					NAMER);
			String clue = SentenceBuilder.normal(row.goal(), word(row.word()), verbWord(row.word()), row.conditions(),
					1, false, NAMER);
			if (row.goal() instanceof Goal.Sequence sequence) {
				String connective = sequence.ordered() ? switch (sequence.connector()) {
					case "BEFORE" -> "기 전에";
					case "AFTER" -> "후에";
					default -> "고 나서";
				} : "고 ";
				assertTrue(hint.contains(connective), row.id() + " hint lost its connective: " + hint);
			}
			if (row.goal() instanceof Goal.Avoid) {
				assertTrue(hint.endsWith("지 마세요."), row.id() + " hint lost the 'not': " + hint);
			}
			if (!(row.goal() instanceof Goal.Smelt)) {
				// Counts go away in the hint, nothing else changes at level 1.
				assertEquals(clue.replaceAll(" (한|두|세|네|다섯) (개|마리)", ""), hint.replaceAll(" (한|두|세|네|다섯) (개|마리)", ""),
						row.id());
			}
		}
	}

	/** The ceremony (past) finales stay short and natural. */
	@Test
	void ceremonyFinalesAreShortAndInThePast() {
		record Case(Goal goal, List<Condition> conditions, String expected) {
		}
		List<Case> cases = List.of(
				new Case(new Goal.Craft("minecraft:torch", 1, null, List.of("minecraft:stick", "minecraft:coal")),
						List.of(), "횃불을 만들었어요."),
				new Case(new Goal.Craft("minecraft:bread", 3, null, List.of()), List.of(), "빵을 세 개 만들었어요."),
				new Case(new Goal.Smelt("minecraft:sand", "minecraft:glass", 1), List.of(), "화로에서 모래를 구웠어요."),
				new Case(new Goal.Smelt(null, "minecraft:cooked_beef", 1), List.of(), "스테이크를 구웠어요."),
				new Case(new Goal.Smelt("minecraft:beef", "minecraft:cooked_beef", 1), List.of(),
						"익히지 않은 소고기를 구웠어요."),
				new Case(new Goal.Give("minecraft:bread", "minecraft:villager", null, 1), List.of(),
						"주민에게 빵을 줬어요."),
				new Case(new Goal.Give("minecraft:bread", "minecraft:villager", "DROP_NEAR", 1), List.of(),
						"주민 앞에 빵을 떨어뜨렸어요."),
				new Case(new Goal.PutIn("minecraft:apple", "minecraft:chest", 1), List.of(), "상자 안에 사과를 넣었어요."),
				new Case(new Goal.HoldWhile("minecraft:torch", null, new Condition.Time(Condition.Phase.NIGHT), 10),
						List.of(), "횃불을 들고 밤에 걸었어요."),
				new Case(new Goal.HoldWhile("minecraft:torch", new Goal.BreakBlock("minecraft:stone", 1, null), null, 10),
						List.of(), "횃불을 들고 돌을 캤어요."),
				new Case(new Goal.Sequence(List.of(new Goal.PlaceBlock("minecraft:torch", 1),
						new Goal.UseBlock("minecraft:oak_door")), true, "BEFORE"), List.of(),
						"참나무 문을 열기 전에 횃불을 놓았어요."),
				new Case(new Goal.Sequence(List.of(new Goal.ConsumeItem("minecraft:bread", 1),
						new Goal.UseBlock("minecraft:red_bed")), true, "AFTER"), List.of(),
						"빵을 먹은 후에 빨간색 침대에서 잤어요."),
				new Case(new Goal.Avoid(new Goal.UseBlock("minecraft:red_bed"), 60, new Condition.Time(Condition.Phase.NIGHT)),
						List.of(), "밤에 빨간색 침대에서 자지 않았어요."));
		for (Case c : cases) {
			String ceremony = SentenceBuilder.ceremony(c.goal(), null, c.conditions(), NAMER);
			assertEquals(c.expected(), ceremony);
			assertTrue(ceremony.length() <= 30, "too long for a finale line: " + ceremony);
		}
	}

	/** The nine new verbs, with the irregular ones (ㄹ-drop 들다, ㅂ 굽다, ㄷ 걷다) checked form by form. */
	@Test
	void newVerbsConjugateLikeStandardKorean() {
		record Forms(Verb verb, String dictionary, String polite, String past, String future, String attributive,
					 String honorific) {
		}
		List<Forms> gold = List.of(
				new Forms(Verb.PUT_IN, "넣다", "넣어요", "넣었어요", "넣을", "넣은", "넣으세요"),
				new Forms(Verb.HOLD, "들다", "들어요", "들었어요", "들", "든", "드세요"),
				new Forms(Verb.ENTER, "들어가다", "들어가요", "들어갔어요", "들어갈", "들어간", "들어가세요"),
				new Forms(Verb.DROP_AT, "떨어뜨리다", "떨어뜨려요", "떨어뜨렸어요", "떨어뜨릴", "떨어뜨린", "떨어뜨리세요"),
				new Forms(Verb.CLOSE, "닫다", "닫아요", "닫았어요", "닫을", "닫은", "닫으세요"),
				new Forms(Verb.MELT, "녹이다", "녹여요", "녹였어요", "녹일", "녹인", "녹이세요"),
				new Forms(Verb.BAKE, "굽다", "구워요", "구웠어요", "구울", "구운", "구우세요"),
				new Forms(Verb.GIVE, "주다", "줘요", "줬어요", "줄", "준", "주세요"),
				new Forms(Verb.WALK, "걷다", "걸어요", "걸었어요", "걸을", "걸은", "걸으세요"),
				// Existing verbs the sequences lean on.
				new Forms(Verb.CRAFT, "만들다", "만들어요", "만들었어요", "만들", "만든", "만드세요"),
				new Forms(Verb.EAT, "먹다", "먹어요", "먹었어요", "먹을", "먹은", "먹으세요"),
				new Forms(Verb.SLEEP, "자다", "자요", "잤어요", "잘", "잔", "자세요"),
				new Forms(Verb.OPEN, "열다", "열어요", "열었어요", "열", "연", "여세요"),
				new Forms(Verb.PICK_UP, "줍다", "주워요", "주웠어요", "주울", "주운", "주우세요"));
		for (Forms forms : gold) {
			String who = forms.verb().name();
			assertEquals(forms.dictionary(), forms.verb().dictionary(), who);
			assertEquals(forms.polite(), forms.verb().polite(), who);
			assertEquals(forms.past(), forms.verb().past(), who);
			assertEquals(forms.future(), forms.verb().future(), who);
			assertEquals(forms.attributive(), forms.verb().attributive(), who);
			assertEquals(forms.honorific(), forms.verb().honorific(), who);
		}
		// The connectives built from the stem: 닫기 전에, 걷고 나서, 구운 후에.
		assertEquals("닫기 전에", Verb.CLOSE.stem() + "기 전에");
		assertEquals("걷고 나서", Verb.WALK.stem() + "고 나서");
		assertEquals("구운 후에", Verb.BAKE.attributive() + " 후에");
		// The particle helpers behind them.
		assertEquals("주민에게", KoGrammar.ege("주민"));
		assertEquals("화로에서", KoGrammar.eseo("화로"));
		assertEquals("드세요", KoGrammar.seyo("들"));
		assertEquals("가세요", KoGrammar.seyo("가"));
		assertEquals("먹으세요", KoGrammar.seyo("먹"));
		assertEquals("든", KoGrammar.eun("들"));
		assertEquals("잔", KoGrammar.eun("자"));
		assertEquals("먹은", KoGrammar.eun("먹"));
	}
}
