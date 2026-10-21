package io.github.leks2205.hangulquest.dialogue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Walking a conversation: where choices lead, what each step reports, and that effects happen once. */
class DialogueSessionTest {
	/** Remembers every call, in order. */
	private static final class Recorder implements DialogueSink {
		final List<String> calls = new ArrayList<>();

		@Override
		public void exposeWord(String wordId) {
			calls.add("word " + wordId);
		}

		@Override
		public void exposeGrammar(String point) {
			calls.add("see " + point);
		}

		@Override
		public void practiceGrammar(String point, int options, boolean correct) {
			calls.add("grammar " + point + " " + options + (correct ? " right" : " wrong"));
		}

		@Override
		public void practiceWord(String wordId, boolean correct) {
			calls.add("typed " + wordId + (correct ? " right" : " wrong"));
		}

		@Override
		public boolean startTrail(String trailId) {
			calls.add("trail " + trailId);
			return true;
		}
	}

	private static Dialogue parse(String nodes) {
		String json = "{\"id\":\"t\",\"trigger\":{\"entity\":\"minecraft:villager\"},\"start\":\"a\",\"nodes\":{" + nodes + "}}";
		DialogueParser.Parsed parsed = DialogueParser.parse("t", json.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.problems().isEmpty(), parsed.problems().toString());
		return parsed.dialogue();
	}

	/** a (line, opens a trail) -> b (choices: wrong stays, right goes on with an effect) -> c (typed) -> d (end). */
	private static final Dialogue TALK = parse("""
			"a": {"speaker": "child", "ko": "안녕!", "next": "b",
			      "effects": [{"type": "startTrail", "trail": "essenspfad"}, {"type": "exposeWords", "words": ["w1", "w2"]}]},
			"b": {"speaker": "child", "ko": "너도 좋아해?", "grammar": "L6.banmal", "choices": [
			      {"ko": "네, 저도 좋아합니다.", "speechLevel": "hapsyo", "correct": false,
			       "effects": [{"type": "exposeGrammar", "point": "wrongpoint"}]},
			      {"ko": "응, 나도 좋아해.", "speechLevel": "banmal", "correct": true, "next": "c",
			       "effects": [{"type": "exposeGrammar", "point": "L6.banmal"}]}]},
			"c": {"speaker": "child", "ko": "뭐가 좋아?", "typed": {"expected": ["사과"], "word": "concept:apple"}, "next": "d"},
			"d": {"speaker": "child", "ko": "잘 가!", "end": true}
			""");

	@Test
	void theFirstNodeAppliesItsEffectsAtOnce() {
		Recorder sink = new Recorder();
		DialogueSession session = new DialogueSession(TALK, sink);
		assertEquals("a", session.node().id());
		assertEquals(List.of("trail essenspfad", "word w1", "word w2"), sink.calls);
	}

	@Test
	void aWrongPickStaysAndIsGradedThenTheRightOneMovesOn() {
		Recorder sink = new Recorder();
		DialogueSession session = new DialogueSession(TALK, sink);
		session.advance();
		sink.calls.clear();

		DialogueSession.Pick wrong = session.choose(0);
		assertEquals(Boolean.FALSE, wrong.correct());
		assertFalse(wrong.moved());
		assertEquals("b", session.node().id());
		assertEquals(List.of("grammar L6.banmal 2 wrong", "see wrongpoint"), sink.calls);

		sink.calls.clear();
		DialogueSession.Pick right = session.choose(1);
		assertEquals(Boolean.TRUE, right.correct());
		assertTrue(right.moved());
		assertEquals("c", session.node().id());
		assertEquals(List.of("grammar L6.banmal 2 right", "see L6.banmal"), sink.calls);
	}

	@Test
	void aTypedAnswerIsPracticedAndMovesOn() {
		Recorder sink = new Recorder();
		DialogueSession session = new DialogueSession(TALK, sink);
		session.advance();
		session.choose(1);
		sink.calls.clear();
		session.typed(false);
		assertEquals(List.of("typed concept:apple wrong"), sink.calls);
		assertEquals("d", session.node().id());
	}

	@Test
	void aFreeAnswerGradesNothing() {
		Dialogue free = parse("\"a\":{\"speaker\":\"farmer\",\"ko\":\"이름이 뭐예요?\",\"typed\":{\"free\":true},\"next\":\"b\"},"
				+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}");
		Recorder sink = new Recorder();
		DialogueSession session = new DialogueSession(free, sink);
		session.typed(true);
		assertEquals("b", session.node().id());
		assertEquals(List.of(), sink.calls);
	}

	@Test
	void theLastLineEndsTheConversation() {
		DialogueSession session = new DialogueSession(TALK, new Recorder());
		session.advance();
		session.choose(1);
		session.typed(true);
		assertFalse(session.finished());
		session.advance();
		assertTrue(session.finished());
		assertNull(session.node());
		session.advance();
		assertNull(session.node());
	}

	@Test
	void theWrongKindOfInputDoesNothing() {
		Recorder sink = new Recorder();
		DialogueSession session = new DialogueSession(TALK, sink);
		sink.calls.clear();
		assertEquals(new DialogueSession.Pick(null, false), session.choose(0), "a line has no choices");
		session.typed(true);
		assertEquals("a", session.node().id());
		session.advance();
		session.advance();
		assertEquals("b", session.node().id(), "a choice node is not left by continuing");
		assertEquals(new DialogueSession.Pick(null, false), session.choose(7));
		assertEquals(List.of(), sink.calls);
	}

	@Test
	void anEffectHappensOnlyTheFirstTimeANodeIsShown() {
		Dialogue loop = parse("\"a\":{\"speaker\":\"farmer\",\"ko\":\"안녕하세요!\",\"effects\":[{\"type\":\"startTrail\",\"trail\":\"x\"}],"
				+ "\"choices\":[{\"ko\":\"다시요.\",\"next\":\"a\"},{\"ko\":\"끝이에요.\",\"next\":\"b\"}]},"
				+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}");
		Recorder sink = new Recorder();
		DialogueSession session = new DialogueSession(loop, sink);
		session.choose(0);
		session.choose(0);
		session.choose(1);
		assertEquals(List.of("trail x"), sink.calls);
	}

	@Test
	void anUngradedChoiceReportsNothingToTheLearningData() {
		Dialogue plain = parse("\"a\":{\"speaker\":\"farmer\",\"ko\":\"어때요?\",\"choices\":[{\"ko\":\"좋아요.\",\"next\":\"b\"}]},"
				+ "\"b\":{\"speaker\":\"farmer\",\"ko\":\"잘 가요!\",\"end\":true}");
		Recorder sink = new Recorder();
		DialogueSession session = new DialogueSession(plain, sink);
		DialogueSession.Pick pick = session.choose(0);
		assertNull(pick.correct());
		assertEquals(List.of(), sink.calls);
	}
}
