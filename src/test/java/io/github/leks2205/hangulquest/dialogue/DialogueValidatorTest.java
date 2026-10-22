package io.github.leks2205.hangulquest.dialogue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The graph checks: dead ends, loops, unknown targets, effects and speech levels. */
class DialogueValidatorTest {
	private static final DialogueValidator.Env REAL = new DialogueValidator.Env(Set.of("essenspfad"),
			Set.of("L5.hapsyo", "L6.banmal"), id -> id.equals("concept:apple"), key -> true);

	/** A conversation with the given nodes (JSON members), starting at "a". */
	private static Dialogue dialogue(String nodes) {
		String json = "{\"id\":\"t\",\"trigger\":{\"entity\":\"minecraft:villager\"},\"start\":\"a\",\"nodes\":{" + nodes + "}}";
		DialogueParser.Parsed parsed = DialogueParser.parse("t", json.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.problems().isEmpty(), parsed.problems().toString());
		return parsed.dialogue();
	}

	private static List<String> problems(String nodes) {
		return DialogueValidator.validate(dialogue(nodes), REAL);
	}

	private static void assertProblem(List<String> problems, String part) {
		assertTrue(problems.stream().anyMatch(problem -> problem.contains(part)), part + " in " + problems);
	}

	private static final String A_THEN_END = "\"a\":{\"speaker\":\"farmer\",\"ko\":\"안녕하세요!\",\"next\":\"b\"},"
			+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}";

	@Test
	void aSimpleChainIsValid() {
		assertEquals(List.of(), problems(A_THEN_END));
	}

	@Test
	void aNodeWithoutAnExitIsADeadEnd() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"안녕하세요!\"}"), "exactly one of next, choices, typed or end");
	}

	@Test
	void aNodeWithTwoExitsIsRejected() {
		assertProblem(problems(A_THEN_END.replace("\"end\":true", "\"end\":true,\"next\":\"a\"")),
				"exactly one of next, choices, typed or end");
	}

	@Test
	void aNextThatDoesNotExistIsFound() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"안녕하세요!\",\"next\":\"nowhere\"}"),
				"next \"nowhere\" does not exist");
	}

	@Test
	void aMissingStartNodeIsFound() {
		Dialogue broken = new Dialogue("t", 1, new Dialogue.Trigger("minecraft:villager", null, null), "zzz",
				dialogue(A_THEN_END).nodes());
		assertProblem(DialogueValidator.validate(broken, REAL), "start node");
	}

	@Test
	void anUnreachableNodeIsFound() {
		assertProblem(problems(A_THEN_END + ",\"c\":{\"speaker\":\"farmer\",\"ko\":\"안녕!\",\"end\":true}"),
				"c: cannot be reached");
	}

	@Test
	void aLoopThatNeverEndsIsFound() {
		List<String> found = problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"안녕하세요!\",\"next\":\"b\"},"
				+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"next\":\"a\"}");
		assertProblem(found, "a: the conversation cannot end");
		assertProblem(found, "b: the conversation cannot end");
	}

	@Test
	void aLoopThatCanBeLeftIsFine() {
		assertEquals(List.of(), problems("\"a\":{\"speaker\":\"child\",\"ko\":\"너도 좋아해?\",\"grammar\":\"L6.banmal\","
				+ "\"choices\":[{\"ko\":\"응, 나도 좋아해.\",\"speechLevel\":\"banmal\",\"correct\":true,\"next\":\"b\"},"
				+ "{\"ko\":\"네, 저도 좋아합니다.\",\"speechLevel\":\"hapsyo\",\"correct\":false,\"next\":\"a\"}]},"
				+ "\"b\":{\"speaker\":\"child\",\"ko\":\"잘 가!\",\"end\":true}"));
	}

	@Test
	void choicesThatAllStayOnTheNodeTrapThePlayer() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"어떻게 해요?\",\"choices\":["
				+ "{\"ko\":\"네.\",\"correct\":false},{\"ko\":\"아니요.\",\"correct\":false}]}"), "no choice leads on");
	}

	@Test
	void aChoiceWithoutNextMustBeMarkedWrong() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"어떻게 해요?\",\"choices\":["
				+ "{\"ko\":\"네.\",\"next\":\"b\"},{\"ko\":\"아니요.\"}]},"
				+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}"), "must be marked wrong");
	}

	@Test
	void aTrailThatDoesNotExistIsFound() {
		assertProblem(problems(A_THEN_END.replace("\"next\":\"b\"",
				"\"next\":\"b\",\"effects\":[{\"type\":\"startTrail\",\"trail\":\"gibtesnicht\"}]")),
				"trail \"gibtesnicht\" does not exist");
	}

	@Test
	void aKnownTrailIsAccepted() {
		assertEquals(List.of(), problems(A_THEN_END.replace("\"next\":\"b\"",
				"\"next\":\"b\",\"effects\":[{\"type\":\"startTrail\",\"trail\":\"essenspfad\"}]")));
	}

	@Test
	void wordsAndGrammarPointsMustExist() {
		List<String> found = problems(A_THEN_END.replace("\"next\":\"b\"", "\"next\":\"b\",\"effects\":["
				+ "{\"type\":\"exposeWords\",\"words\":[\"concept:apple\",\"concept:nope\"]},"
				+ "{\"type\":\"exposeGrammar\",\"point\":\"L9.nope\"}]"));
		assertProblem(found, "concept:nope");
		assertProblem(found, "L9.nope");
		assertTrue(found.stream().noneMatch(problem -> problem.contains("concept:apple")), found.toString());
	}

	@Test
	void anEffectTypeThatDoesNotExistIsAParseProblem() {
		String json = "{\"id\":\"t\",\"trigger\":{\"entity\":\"x\"},\"start\":\"a\",\"nodes\":{\"a\":{\"speaker\":\"farmer\","
				+ "\"ko\":\"안녕!\",\"end\":true,\"effects\":[{\"type\":\"fly\"}]}}}";
		DialogueParser.Parsed parsed = DialogueParser.parse("t", json.getBytes(StandardCharsets.UTF_8));
		assertProblem(parsed.problems(), "unknown effect type");
	}

	@Test
	void aSpeakerKeepsItsOwnLevel() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"speechLevel\":\"banmal\",\"ko\":\"안녕!\",\"end\":true}"),
				"farmer speaks haeyo, not banmal");
	}

	@Test
	void aLineMustShowTheLevelOfItsNode() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"저는 농부입니다.\",\"end\":true}"),
				"the line is hapsyo, the node says haeyo");
		assertProblem(problems("\"a\":{\"speaker\":\"child\",\"ko\":\"저는 아이예요.\",\"end\":true}"),
				"the line is haeyo, the node says banmal");
	}

	@Test
	void twoLevelsInOneLineAreRejected() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"돌을 캤어요. 곡괭이를 썼습니다.\",\"end\":true}"),
				"the line is hapsyo");
	}

	@Test
	void aChoiceMustShowTheLevelItClaims() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"어떻게 해요?\",\"choices\":["
				+ "{\"ko\":\"네, 저도 좋아합니다.\",\"speechLevel\":\"banmal\",\"next\":\"b\"}]},"
				+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}"), "the line is hapsyo, the choice says banmal");
	}

	@Test
	void aGradedNodeNeedsARightAnswerAndAGrammarPoint() {
		String choices = "\"choices\":[{\"ko\":\"네.\",\"correct\":false,\"next\":\"b\"}]},"
				+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}";
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"어때요?\",\"grammar\":\"L5.hapsyo\"," + choices),
				"needs a right and a wrong choice");
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"어때요?\"," + choices.replace("false", "true")),
				"names no grammar point");
	}

	private static final String ASK_RIGHT_HAPSYO = "\"choices\":["
			+ "{\"ko\":\"네, 가져오겠습니다.\",\"speechLevel\":\"hapsyo\",\"correct\":true,\"next\":\"b\"},"
			+ "{\"ko\":\"응, 가져올 거야.\",\"speechLevel\":\"banmal\",\"correct\":false}]},"
			+ "\"b\":{\"speaker\":\"librarian\",\"ko\":\"감사합니다.\",\"end\":true}";

	@Test
	void aSpeechLevelTaskIsAskedByASpeakerOfThatLevel() {
		assertEquals(List.of(), problems("\"a\":{\"speaker\":\"librarian\",\"ko\":\"책을 가져와 주시겠습니까?\","
				+ "\"grammar\":\"L5.hapsyo\"," + ASK_RIGHT_HAPSYO));
		// the farmer talks 해요체: a task about 합쇼체 would grade the player against the wrong person
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"책을 가져와 주시겠어요?\","
				+ "\"grammar\":\"L5.hapsyo\"," + ASK_RIGHT_HAPSYO.replace("librarian", "farmer")), "teaches hapsyo");
	}

	@Test
	void theRightChoiceOfASpeechLevelTaskIsInThatLevel() {
		String swapped = ASK_RIGHT_HAPSYO.replace("\"speechLevel\":\"hapsyo\",\"correct\":true", "\"speechLevel\":\"hapsyo\",\"correct\":false")
				.replace("\"speechLevel\":\"banmal\",\"correct\":false", "\"speechLevel\":\"banmal\",\"correct\":true,\"next\":\"b\"");
		assertProblem(problems("\"a\":{\"speaker\":\"librarian\",\"ko\":\"책을 가져와 주시겠습니까?\","
				+ "\"grammar\":\"L5.hapsyo\"," + swapped), "the right choice must be hapsyo");
	}

	@Test
	void aTypedNodeNeedsNextAndSomethingToExpect() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"뭐예요?\",\"typed\":{\"expected\":[\"돌\"]}}"),
				"a typed node needs next");
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"뭐예요?\",\"typed\":{},\"next\":\"b\"},"
				+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}"), "expected answer or free");
	}

	@Test
	void aMissingGlossIsFound() {
		DialogueValidator.Env noKeys = new DialogueValidator.Env(null, null, null, key -> false);
		List<String> found = DialogueValidator.validate(dialogue(A_THEN_END), noKeys);
		assertProblem(found, "no gloss text for hangulquest.dialogue.t.a");
		assertProblem(found, "no gloss text for hangulquest.dialogue.t.b");
	}

	@Test
	void idsThatWouldBreakKeysAreRejected() {
		assertProblem(problems("\"a\":{\"speaker\":\"farmer\",\"ko\":\"안녕!\",\"next\":\"B.x\"},"
				+ "\"B.x\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}"), "node id may only use");
	}

	@Test
	void anUnknownSpeakerIsAParseProblem() {
		String json = "{\"id\":\"t\",\"trigger\":{\"entity\":\"x\"},\"start\":\"a\",\"nodes\":{\"a\":{\"speaker\":\"dragon\","
				+ "\"ko\":\"안녕!\",\"end\":true}}}";
		assertProblem(DialogueParser.parse("t", json.getBytes(StandardCharsets.UTF_8)).problems(), "unknown speaker");
	}

	@Test
	void brokenJsonDoesNotThrow() {
		DialogueParser.Parsed parsed = DialogueParser.parse("t", "{\"id\": ".getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.dialogue() == null && !parsed.problems().isEmpty());
		DialogueParser.Parsed wrongType = DialogueParser.parse("t",
				"{\"id\":\"t\",\"nodes\":{\"a\":{\"speaker\":\"farmer\",\"choices\":\"x\",\"typed\":{\"expected\":3}}}}"
						.getBytes(StandardCharsets.UTF_8));
		assertTrue(!wrongType.problems().isEmpty());
	}
}
