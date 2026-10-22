package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.grammar.Constructions;
import io.github.leks2205.hangulquest.quest.grammar.Constructions.Role;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The validator rule for authored Korean of levels 4 to 6: which construction is allowed at which level, and in which
 * role (instruction, hint, finale). A promise (-(으)ㄹ게요) and the speech levels of 합쇼체 and 반말 belong to dialogue.
 */
class ConstructionGateTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();

	private static void assertProblem(List<String> problems, String part) {
		assertTrue(problems.stream().anyMatch(problem -> problem.contains(part)), part + " in " + problems);
	}

	private static List<String> problems(String text, Role role, int level) {
		return Constructions.problems("t", text, role, level);
	}

	// ----------------------------------------------------------------------- finding them

	@Test
	void theConstructionsAreFoundInAuthoredText() {
		assertEquals(List.of("L4.myeon"), Constructions.found("밤이 되면 횃불을 놓아야 해요."));
		assertEquals(List.of("L4.myeon"), Constructions.found("어두우면 횃불을 놓아요."));
		assertEquals(List.of("L4.jiman"), Constructions.found("밤이지만 밝아요."));
		assertEquals(List.of("L4.eulgeyo"), Constructions.found("제가 열게요."));
		assertEquals(List.of("L5.eunikka"), Constructions.found("밤이니까 횃불을 놓아야 해요."));
		assertEquals(List.of("L5.neun_dongan"), Constructions.found("횃불을 들고 있는 동안 걸어요."));
		assertEquals(List.of("L6.eulsurok"), Constructions.found("높이 올라갈수록 추워져요."));
		assertEquals(List.of("L6.deusi"), Constructions.found("물이 흐르듯이 용암이 흘러요."));
		assertEquals(List.of("L6.gi_maryeon_ida"), Constructions.found("밤에는 몬스터가 나타나기 마련이에요."));
		assertEquals(List.of("L6.su_bakke_eopda"), Constructions.found("돌아갈 수밖에 없어요."));
		assertEquals(List.of("L4.myeon", "L6.su_bakke_eopda"), Constructions.found("횃불이 없으면 돌아갈 수밖에 없어요."),
				"both are in the sentence");
	}

	@Test
	void plainWordsAreNotMistakenForThem() {
		assertEquals(List.of(), Constructions.found("횃불을 놓아요."));
		assertEquals(List.of(), Constructions.found("라면을 먹어요."), "라면 is noodles, not -(으)면");
		assertEquals(List.of(), Constructions.found("화면을 봐요."));
		assertEquals(List.of(), Constructions.found("하지만 멋져요."), "하지만 is the word 'but'");
		assertEquals(List.of(), Constructions.found("책이 필요합니까?"), "합니까 is 합쇼체, not a reason");
		assertEquals(List.of(), Constructions.found("십 초 동안 기다려요."), "N 동안 is a noun phrase, not -는 동안");
		assertEquals(List.of(), Constructions.found("사과를 한 개 주세요."));
	}

	// ----------------------------------------------------------------------- level and role

	@Test
	void aConstructionNeedsItsLevel() {
		assertEquals(List.of(), problems("밤이 되면 횃불을 놓아야 해요.", Role.CLUE, 4));
		assertProblem(problems("밤이 되면 횃불을 놓아요.", Role.CLUE, 3), "L4.myeon");
		assertProblem(problems("밤이 되면 횃불을 놓아요.", Role.CLUE, 3), "level 3");
		assertEquals(List.of(), problems("밤이니까 횃불을 놓아야 해요.", Role.HINT, 5));
		assertProblem(problems("밤이니까 횃불을 놓아야 해요.", Role.HINT, 4), "L5.eunikka");
		assertProblem(problems("횃불을 들고 있는 동안 걸어요.", Role.CLUE, 4), "L5.neun_dongan");
	}

	@Test
	void aContrastIsAHintOrAFinaleNotAnInstruction() {
		assertEquals(List.of(), problems("밤이지만 밝아요.", Role.FINALE, 4));
		assertEquals(List.of(), problems("밤이지만 밝아요.", Role.HINT, 4));
		assertProblem(problems("밤이지만 횃불을 놓아야 해요.", Role.CLUE, 4), "hint or a finale");
		assertProblem(problems("밤이지만 밝아요.", Role.FINALE, 3), "L4.jiman");
	}

	@Test
	void aPromiseBelongsToDialogue() {
		assertProblem(problems("제가 다시 올게요.", Role.FINALE, 6), "dialogue");
		assertProblem(problems("제가 열게요.", Role.CLUE, 4), "dialogue");
		assertProblem(problems("제가 열게요.", Role.HINT, 6), "dialogue");
	}

	@Test
	void theUpperFlavourFormsAreLevelSixHintsAndFinales() {
		assertEquals(List.of(), problems("높이 올라갈수록 추워져요.", Role.FINALE, 6));
		assertEquals(List.of(), problems("밤에는 몬스터가 나타나기 마련이에요.", Role.HINT, 6));
		assertProblem(problems("높이 올라갈수록 추워져요.", Role.FINALE, 5), "L6.eulsurok");
		assertProblem(problems("높이 올라갈수록 추워져요.", Role.CLUE, 6), "hint or a finale");
		assertProblem(problems("물이 흐르듯이 용암이 흘러요.", Role.CLUE, 6), "hint or a finale");
	}

	@Test
	void aForcedEndingNeedsAForcedConclusion() {
		assertEquals(List.of(), problems("횃불이 없으면 돌아갈 수밖에 없어요.", Role.FINALE, 6));
		assertEquals(List.of(), problems("밤이니까 집에 있을 수밖에 없어요.", Role.HINT, 6));
		// the old error: the ending used as the instruction itself
		assertProblem(problems("돌을 캘 수밖에 없어요.", Role.CLUE, 6), "forced conclusion");
		assertProblem(problems("횃불을 놓을 수밖에 없어요.", Role.HINT, 6), "forced conclusion");
		assertProblem(problems("횃불이 없으면 돌아갈 수밖에 없어요.", Role.FINALE, 5), "L6.su_bakke_eopda");
	}

	@Test
	void aTrailSpeaksHaeyoche() {
		assertEquals(List.of(), problems("횃불을 놓았어요.", Role.FINALE, 6));
		assertProblem(problems("어서 오십시오. 횃불을 놓았습니다.", Role.FINALE, 6), "hapsyo");
		assertProblem(problems("우리 같이 놓자!", Role.HINT, 6), "banmal");
		assertProblem(problems("횃불을 놓는다.", Role.CLUE, 6), "plain");
		// the quote of a character before the dash is not the trail speaking
		assertEquals(List.of(), problems("끝 아녔어? — 엔드 수정 네 개로 드래곤을 다시 불러요.", Role.FINALE, 6));
	}

	// ----------------------------------------------------------------------- the validator

	private static JsonObject shipped(String id) throws IOException {
		try (InputStream stream = QuestLoader.class.getResourceAsStream(QuestLoader.BASE + id + ".json");
			 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	@Test
	void theValidatorReportsAuthoredTextOfAnotherLevelAndKeepsTheStep() throws IOException {
		var log = LoggerFactory.getLogger("construction-gate");
		JsonObject json = shipped("w11_07");
		assertEquals(List.of(), QuestValidator.validate(List.of(QuestLoader.parse(json)), CATALOG, log).problems());
		json.addProperty("koreanLevel", 3);
		QuestValidator.Result low = QuestValidator.validate(List.of(QuestLoader.parse(json)), CATALOG, log);
		assertProblem(low.problems(), "w11_07/s2");
		assertProblem(low.problems(), "L4.myeon");
		assertEquals(3, low.trails().get(0).steps().size(), "the step stays: a player's own trail is only told");
	}

	@Test
	void theValidatorChecksTheFinaleText() throws IOException {
		var log = LoggerFactory.getLogger("construction-gate");
		JsonObject json = shipped("w11_07");
		json.getAsJsonObject("finale").addProperty("textKo", "갈색 양털을 구했어요. 높이 올라갈수록 추워요.");
		json.addProperty("koreanLevel", 5);
		QuestValidator.Result low = QuestValidator.validate(List.of(QuestLoader.parse(json)), CATALOG, log);
		assertProblem(low.problems(), "w11_07/finale");
		assertProblem(low.problems(), "L6.eulsurok");
	}
}
