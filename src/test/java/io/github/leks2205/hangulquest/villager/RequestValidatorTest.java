package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueSession;
import io.github.leks2205.hangulquest.dialogue.DialogueSink;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The request format: the honesty rules of SHOW, GIVE and DO, the lines of every personality and the conversations. */
class RequestValidatorTest {
	private static final RequestValidator.Env ENV = new RequestValidator.Env(null, id -> true, key -> true,
			id -> Set.of("minecraft:cactus", "minecraft:bread").contains(id));

	/** Lines for every personality in its own speech level. */
	private static String lines(String extra) {
		StringBuilder out = new StringBuilder("\"lines\":{");
		boolean first = true;
		for (Personality personality : Personality.values()) {
			String end = personality == Personality.SNOOTY ? "습니다." : "좋아요.";
			out.append(first ? "" : ",").append('"').append(personality.name()).append("\":{");
			first = false;
			out.append("\"offer\":\"빵이 필요").append(personality == Personality.SNOOTY ? "합니다." : "해요.").append("\",")
					.append("\"accept\":\"").append(personality == Personality.SNOOTY ? "괜찮습니다." : "좋아요.").append("\",")
					.append("\"decline\":\"").append(personality == Personality.SNOOTY ? "괜찮습니다." : "괜찮아요.").append("\",")
					.append("\"reminder\":\"아직 없").append(personality == Personality.SNOOTY ? "습니까?" : "어요?").append("\",")
					.append("\"thanks\":\"").append(personality == Personality.SNOOTY ? "감사합니다." : "고마워요.").append("\"}");
			assertNotNull(end);
		}
		return out.append(extra).append("}").toString();
	}

	private static Request parse(String body) {
		String json = "{\"id\":\"req_test_01\",\"level\":1,\"regions\":[\"PLAINS\"],\"words\":[\"concept:bread\"],"
				+ "\"answers\":{\"yes\":\"네, 좋아요!\",\"no\":\"아니요, 미안해요.\"}," + body + "}";
		RequestParser.Parsed parsed = RequestParser.parse("req_test_01", json.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.problems().isEmpty(), parsed.problems().toString());
		return parsed.request();
	}

	private static List<String> problems(String body) {
		return RequestValidator.validate(parse(body), ENV);
	}

	private static void assertProblem(List<String> problems, String part) {
		assertTrue(problems.stream().anyMatch(problem -> problem.contains(part)), part + " in " + problems);
	}

	private static final String GIVE_BREAD = "\"kind\":\"GIVE\",\"goal\":{\"type\":\"GIVE\",\"item\":\"minecraft:bread\","
			+ "\"entity\":\"minecraft:villager\",\"method\":\"DROP_NEAR\",\"count\":1},";
	private static final String SHOW_CACTUS = "\"kind\":\"SHOW\",\"show\":{\"item\":\"minecraft:cactus\"},";

	@Test
	void aGiveAndAShowRequestWithAllLinesAreValid() {
		assertEquals(List.of(), problems(GIVE_BREAD + lines("")));
		assertEquals(List.of(), problems(SHOW_CACTUS + lines("")));
		Request show = parse(SHOW_CACTUS + lines(""));
		assertEquals("minecraft:cactus", show.showItem());
		assertEquals(1, show.showCount());
	}

	@Test
	void aGiveRequestAsksOnlyForWhatAVillagerPicksUp() {
		assertProblem(problems(GIVE_BREAD.replace("minecraft:bread", "minecraft:diamond") + lines("")),
				"a villager does not pick up minecraft:diamond");
		assertProblem(problems(GIVE_BREAD.replace("minecraft:villager", "minecraft:cow") + lines("")),
				"gives to minecraft:villager");
		assertProblem(problems(GIVE_BREAD.replace("DROP_NEAR", "TRADE") + lines("")), "method DROP_NEAR");
		assertProblem(problems("\"kind\":\"GIVE\",\"goal\":{\"type\":\"CRAFT\",\"item\":\"minecraft:bread\"}," + lines("")),
				"a GIVE request has a GIVE goal");
	}

	@Test
	void aShowRequestTakesNothingAndSaysSo() {
		assertProblem(problems(SHOW_CACTUS + GIVE_BREAD.replace("\"kind\":\"GIVE\",", "") + lines("")),
				"a SHOW request has no goal");
		assertProblem(problems("\"kind\":\"SHOW\"," + lines("")), "needs show.item");
		assertProblem(problems(SHOW_CACTUS.replace("minecraft:cactus", "minecraft:unobtainium") + lines("")),
				"is no item of the game");
		assertProblem(problems(SHOW_CACTUS + lines("").replace("\"고마워요.\"", "\"줘서 고마워요.\"")),
				"speaks of a gift");
	}

	@Test
	void aDoRequestHasAnyOtherGoal() {
		String craft = "\"kind\":\"DO\",\"goal\":{\"type\":\"CRAFT\",\"item\":\"minecraft:bread\",\"count\":1},";
		assertEquals(List.of(), problems(craft + lines("")));
		assertProblem(problems("\"kind\":\"DO\"," + lines("")), "a DO request needs a goal");
		assertProblem(problems(GIVE_BREAD.replace("\"kind\":\"GIVE\"", "\"kind\":\"DO\"") + lines("")),
				"giving to a villager is a GIVE request");
	}

	@Test
	void everyPersonalityThatMayOfferItNeedsAllFiveLinesInItsOwnLevel() {
		assertProblem(problems(GIVE_BREAD + lines("").replace("\"thanks\":\"고마워요.\"}", "\"thanks\":\"\"}")),
				"lazy_thanks: missing");
		assertProblem(problems(GIVE_BREAD + lines("").replace("\"감사합니다.\"", "\"고마워요.\"")),
				"SNOOTY speaks hapsyo, the line is haeyo");
		assertProblem(problems(GIVE_BREAD + "\"personalities\":[\"LAZY\"]," + lines("")), "never used");
	}

	@Test
	void banmalLinesAreBanmalAndOnlyForThoseWhoSwitch() {
		String banmalLazy = lines("").replaceFirst("\"thanks\":\"고마워요.\"",
				"\"thanks\":{\"ko\":\"고마워요.\",\"ko_banmal\":\"정말 고마워.\"}");
		Request request = parse(GIVE_BREAD + banmalLazy);
		assertEquals("정말 고마워.", request.line(Personality.LAZY, Request.Part.THANKS).text(true));
		assertEquals(List.of(), RequestValidator.validate(request, ENV));
		String banmalCranky = lines("").replace("\"CRANKY\":{\"offer\":\"빵이 필요해요.\"",
				"\"CRANKY\":{\"offer\":{\"ko\":\"빵이 필요해요.\",\"ko_banmal\":\"빵이 필요해.\"}");
		assertProblem(problems(GIVE_BREAD + banmalCranky), "CRANKY does not talk banmal");
	}

	@Test
	void theFormIsChecked() {
		assertProblem(problems(GIVE_BREAD + lines("").replace("\"빵이 필요해요.\"", "\"민준이 빵이 필요해요.\"")),
				"says the name 민준");
		String json = "{\"id\":\"other\",\"kind\":\"TAKE\",\"regions\":[\"MOON\"]}";
		List<String> parse = RequestParser.parse("req_x", json.getBytes(StandardCharsets.UTF_8)).problems();
		assertProblem(parse, "must be the file name");
		assertProblem(parse, "kind must be SHOW, GIVE or DO");
		assertProblem(parse, "unknown region \"MOON\"");
		Request noRegion = new Request("bad id", 9, Request.Kind.DO, List.of(), List.of(), List.of(), null, null, 0,
				List.of(), null, null, null);
		List<String> problems = RequestValidator.validate(noRegion, ENV);
		assertProblem(problems, "a request id is req_");
		assertProblem(problems, "level must be 1 to 6");
		assertProblem(problems, "names the regions");
		assertProblem(problems, "the player's answer");
	}

	@Test
	void everyLineNeedsAGloss() {
		RequestValidator.Env glossless = new RequestValidator.Env(null, id -> true, key -> !key.endsWith("lazy_offer"),
				id -> true);
		assertProblem(RequestValidator.validate(parse(GIVE_BREAD + lines("")), glossless),
				"no gloss text for hangulquest.dialogue.req_test_01.lazy_offer");
	}

	@Test
	void theOfferConversationAsksAndAcceptingOrDecliningReachesTheSink() {
		Request request = parse(GIVE_BREAD + lines(""));
		Dialogue offer = RequestDialogues.offer(request, Personality.LAZY);
		assertEquals("req_test_01", offer.id());
		assertEquals("lazy_offer", offer.start());
		assertEquals("hangulquest.dialogue.req_test_01.yes", offer.choiceKey("lazy_offer", 0));
		assertEquals("hangulquest.dialogue.req_test_01.lazy_accept", offer.lineKey("lazy_accept"));
		List<String> events = new ArrayList<>();
		DialogueSink sink = new DialogueSink() {
			public void exposeWord(String wordId) {
				events.add("expose " + wordId);
			}

			public void exposeGrammar(String point) {
			}

			public void practiceGrammar(String point, int options, boolean correct) {
			}

			public void practiceWord(String wordId, boolean correct) {
			}

			public boolean startTrail(String trailId) {
				return false;
			}

			public void acceptRequest(String requestId) {
				events.add("accept " + requestId);
			}

			public void declineRequest(String requestId) {
				events.add("decline " + requestId);
			}
		};
		DialogueSession yes = new DialogueSession(offer, sink);
		yes.choose(0);
		assertEquals("lazy_accept", yes.node().id());
		assertEquals(List.of("expose concept:bread", "accept req_test_01"), events);
		events.clear();
		DialogueSession no = new DialogueSession(offer, sink);
		no.choose(1);
		assertEquals("lazy_decline", no.node().id());
		assertEquals(List.of("expose concept:bread", "decline req_test_01"), events);

		Dialogue thanks = RequestDialogues.single(request, Personality.SNOOTY, Request.Part.THANKS);
		assertEquals("snooty_thanks", thanks.start());
		assertEquals("감사합니다.", thanks.nodes().get("snooty_thanks").ko());
		assertEquals(io.github.leks2205.hangulquest.dialogue.SpeechLevel.HAPSYO,
				thanks.nodes().get("snooty_thanks").speechLevel());
	}

	@Test
	void aRequestFitsOnlyItsRegionPersonalitiesProfessionsAndLevel() {
		Request request = parse(GIVE_BREAD + "\"professions\":[\"minecraft:farmer\"]," + lines(""));
		assertTrue(request.fits(Personality.LAZY, Region.PLAINS, "minecraft:farmer", 1));
		assertTrue(!request.fits(Personality.LAZY, Region.DESERT, "minecraft:farmer", 1));
		assertTrue(!request.fits(Personality.LAZY, null, "minecraft:farmer", 1), "an unknown region gets no request");
		assertTrue(!request.fits(Personality.LAZY, Region.PLAINS, "minecraft:mason", 1));
		Request hard = new Request("req_hard", 3, Request.Kind.DO, List.of(Region.PLAINS), List.of(Personality.JOCK),
				List.of(), null, null, 0, List.of(), null, null, null);
		assertTrue(!hard.fits(Personality.JOCK, Region.PLAINS, null, 2));
		assertTrue(hard.fits(Personality.JOCK, Region.PLAINS, null, 3));
		assertTrue(!hard.fits(Personality.LAZY, Region.PLAINS, null, 3));
	}
}
