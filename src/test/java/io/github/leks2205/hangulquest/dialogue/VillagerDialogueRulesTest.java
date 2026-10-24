package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.Region;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The trigger fields and speech rules of the villager conversations (personalities, regions, kinds, 반말). */
class VillagerDialogueRulesTest {
	private static final DialogueValidator.Env ENV = new DialogueValidator.Env(Set.of(), Set.of(), id -> true, key -> true);

	private static Dialogue parse(String trigger, String nodes) {
		return parse(1, trigger, nodes);
	}

	private static Dialogue parse(int level, String trigger, String nodes) {
		String json = "{\"id\":\"t\",\"level\":" + level + ",\"trigger\":{\"entity\":\"minecraft:villager\"" + trigger
				+ "},\"start\":\"a\",\"nodes\":{" + nodes + "}}";
		DialogueParser.Parsed parsed = DialogueParser.parse("t", json.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.problems().isEmpty(), parsed.problems().toString());
		return parsed.dialogue();
	}

	private static List<String> problems(String trigger, String nodes) {
		return DialogueValidator.validate(parse(trigger, nodes), ENV);
	}

	private static void assertProblem(List<String> problems, String part) {
		assertTrue(problems.stream().anyMatch(problem -> problem.contains(part)), part + " in " + problems);
	}

	private static final String HAEYO = "\"a\":{\"speaker\":\"villager\",\"ko\":\"오늘 날씨가 좋아요.\",\"end\":true}";
	private static final String HAPSYO = "\"a\":{\"speaker\":\"villager\",\"ko\":\"오늘 날씨가 좋습니다.\",\"end\":true}";

	@Test
	void theNewTriggerFieldsAreRead() {
		Dialogue dialogue = parse(",\"personalities\":[\"LAZY\",\"jock\"],\"regions\":[\"PLAINS\"],"
				+ "\"kind\":\"milestone\",\"friendship\":3", HAEYO);
		assertEquals(List.of(Personality.LAZY, Personality.JOCK), dialogue.trigger().personalities());
		assertEquals(List.of(Region.PLAINS), dialogue.trigger().regions());
		assertEquals(ConversationKind.MILESTONE, dialogue.trigger().kind());
		assertEquals(3, dialogue.trigger().friendship());
		assertEquals(List.of(), DialogueValidator.validate(dialogue, ENV));
	}

	@Test
	void unknownPersonalitiesRegionsAndKindsAreReported() {
		String json = "{\"id\":\"t\",\"trigger\":{\"entity\":\"minecraft:villager\",\"personalities\":[\"GRUMPY\"],"
				+ "\"regions\":[\"MOON\"],\"kind\":\"chat\"},\"start\":\"a\",\"nodes\":{" + HAEYO + "}}";
		List<String> problems = DialogueParser.parse("t", json.getBytes(StandardCharsets.UTF_8)).problems();
		assertProblem(problems, "unknown personality \"GRUMPY\"");
		assertProblem(problems, "unknown region \"MOON\"");
		assertProblem(problems, "unknown kind \"chat\"");
	}

	@Test
	void aConversationWithoutKindIsSmallTalk() {
		assertEquals(ConversationKind.SMALLTALK, parse("", HAEYO).trigger().kind());
	}

	@Test
	void snootyTalksHapsyoAndEveryoneElseHaeyo() {
		assertEquals(List.of(), problems(",\"personalities\":[\"SNOOTY\"]", HAPSYO));
		assertProblem(problems(",\"personalities\":[\"SNOOTY\"]", HAEYO), "the line is haeyo, the node says hapsyo");
		assertEquals(List.of(), problems(",\"personalities\":[\"CRANKY\"]", HAEYO));
		assertProblem(problems(",\"personalities\":[\"CRANKY\"]", HAPSYO), "the line is hapsyo, the node says haeyo");
		assertProblem(problems(",\"personalities\":[\"CRANKY\"]", HAPSYO.replace("\"ko\"", "\"speechLevel\":\"hapsyo\",\"ko\"")),
				"villager speaks haeyo, not hapsyo");
		assertProblem(problems(",\"personalities\":[\"SNOOTY\",\"NORMAL\"]", HAEYO), "write SNOOTY's conversations");
	}

	@Test
	void aVillagerConversationIsSpokenByTheVillagerAndForBabiesByTheChild() {
		assertProblem(problems(",\"personalities\":[\"LAZY\"]", HAEYO.replace("villager", "farmer")),
				"is spoken by villager, not farmer");
		String child = "\"a\":{\"speaker\":\"child\",\"ko\":\"나는 사과를 좋아해.\",\"end\":true}";
		assertEquals(List.of(), problems(",\"baby\":true,\"kind\":\"smalltalk\",\"regions\":[\"SNOW\"]", child));
		assertProblem(problems(",\"baby\":true,\"regions\":[\"SNOW\"]", HAEYO), "for babies is spoken by child");
	}

	@Test
	void personalitiesAreOnlyForVillagers() {
		String json = "{\"id\":\"t\",\"trigger\":{\"entity\":\"minecraft:wandering_trader\",\"personalities\":[\"LAZY\"]},"
				+ "\"start\":\"a\",\"nodes\":{" + HAEYO + "}}";
		Dialogue dialogue = DialogueParser.parse("t", json.getBytes(StandardCharsets.UTF_8)).dialogue();
		assertProblem(DialogueValidator.validate(dialogue, ENV), "for minecraft:villager only");
	}

	@Test
	void theBanmalLineMustBeBanmalAndOnlyForPersonalitiesThatSwitch() {
		String withBanmal = "\"a\":{\"speaker\":\"villager\",\"ko\":\"같이 밥을 먹어요.\",\"ko_banmal\":\"같이 밥을 먹자.\",\"end\":true}";
		Dialogue dialogue = parse(",\"personalities\":[\"LAZY\"]", withBanmal);
		assertEquals(List.of(), DialogueValidator.validate(dialogue, ENV));
		assertEquals("같이 밥을 먹자.", dialogue.nodes().get("a").text(true));
		assertEquals("같이 밥을 먹어요.", dialogue.nodes().get("a").text(false));
		assertProblem(problems(",\"personalities\":[\"LAZY\"]", withBanmal.replace("같이 밥을 먹자.", "같이 밥을 먹습니다.")),
				"ko_banmal is hapsyo, it must be banmal");
		assertProblem(problems(",\"personalities\":[\"LAZY\"]", withBanmal.replace("같이 밥을 먹자.", "같이 밥을 먹어요.")),
				"not empty or the same");
		assertProblem(problems(",\"personalities\":[\"CRANKY\",\"SMUG\"]", withBanmal), "ko_banmal is never used");
		assertEquals(List.of(), problems("", withBanmal), "a conversation for anyone may carry it");
	}

	@Test
	void anIntroHasOneSelfIntroAndIsLevelOne() {
		String nodes = "\"a\":{\"speaker\":\"villager\",\"ko\":\"안녕하세요!\",\"next\":\"b\"},"
				+ "\"b\":{\"speaker\":\"villager\",\"selfIntro\":true,\"end\":true}";
		Dialogue intro = parse(",\"personalities\":[\"LAZY\"],\"regions\":[\"PLAINS\"],\"kind\":\"intro\"", nodes);
		assertEquals(List.of(), DialogueValidator.validate(intro, ENV));
		assertTrue(intro.nodes().get("b").selfIntro());
		assertProblem(DialogueValidator.validate(parse(2, ",\"personalities\":[\"LAZY\"],\"kind\":\"intro\"", nodes), ENV),
				"an intro is level 1");
		assertProblem(problems(",\"kind\":\"intro\"", nodes), "names the personalities");
		assertProblem(problems(",\"personalities\":[\"LAZY\"],\"kind\":\"intro\"", HAEYO), "exactly one selfIntro node");
		assertProblem(problems(",\"personalities\":[\"LAZY\"]", nodes), "selfIntro is only for intro");
		assertProblem(problems(",\"personalities\":[\"LAZY\"],\"kind\":\"intro\"",
				nodes.replace("\"selfIntro\":true", "\"selfIntro\":true,\"ko\":\"저는 민준이에요.\"")), "has no ko");
	}

	@Test
	void aMilestoneNeedsThreeSixOrTen() {
		assertEquals(List.of(), problems(",\"personalities\":[\"CRANKY\"],\"kind\":\"milestone\",\"friendship\":6", HAEYO));
		assertProblem(problems(",\"personalities\":[\"CRANKY\"],\"kind\":\"milestone\",\"friendship\":4", HAEYO),
				"friendship 3, 6 or 10");
		assertProblem(problems(",\"kind\":\"milestone\",\"friendship\":3", HAEYO), "names the personalities");
		assertProblem(problems(",\"friendship\":11", HAEYO), "friendship must be 0 to 10");
	}

	@Test
	void aBanmalOfferIsForTheFourThatSwitchAndHasTheAcceptEffect() {
		String offer = "\"a\":{\"speaker\":\"villager\",\"ko\":\"말 편하게 할까요?\",\"choices\":["
				+ "{\"ko\":\"좋아요!\",\"next\":\"b\",\"effects\":[{\"type\":\"acceptBanmal\"}]},"
				+ "{\"ko\":\"아직은 아니에요.\",\"next\":\"b\"}]},"
				+ "\"b\":{\"speaker\":\"villager\",\"ko\":\"알겠어요.\",\"end\":true}";
		assertEquals(List.of(), problems(",\"personalities\":[\"JOCK\"],\"kind\":\"banmal_offer\"", offer));
		assertProblem(problems(",\"personalities\":[\"NORMAL\"],\"kind\":\"banmal_offer\"", offer), "only LAZY, JOCK");
		assertProblem(problems(",\"personalities\":[\"JOCK\"],\"kind\":\"banmal_offer\"", HAEYO), "acceptBanmal effect");
		assertProblem(problems(",\"personalities\":[\"JOCK\"]", offer), "acceptBanmal is only for banmal_offer");
	}

	@Test
	void requestsAreNotDialogueFiles() {
		assertProblem(problems(",\"kind\":\"request\"", HAEYO), "requests are written in requests/");
	}

	@Test
	void noLineSaysAVillagerName() {
		assertProblem(problems("", "\"a\":{\"speaker\":\"villager\",\"ko\":\"민준이 왔어요.\",\"end\":true}"),
				"says the name 민준");
		assertProblem(problems("", "\"a\":{\"speaker\":\"villager\",\"ko\":\"서연 씨, 안녕하세요.\",\"end\":true}"),
				"says the name 서연");
		assertNull(DialogueValidator.nameIn("지우세요."), "a verb that starts like a name is no name");
		assertNull(DialogueValidator.nameIn("오늘 날씨가 좋아요."));
		assertEquals("지우", DialogueValidator.nameIn("지우야, 놀자!"));
	}

	@Test
	void aConversationForVillagersIsNeverPickedByTheOldTrigger() {
		DialogueSet set = DialogueSet.of(List.of(parse(",\"personalities\":[\"LAZY\"]", HAEYO)));
		assertTrue(DialogueTrigger.select(set, new DialogueTrigger.Click(true, true, true, false, false),
				new DialogueTrigger.Target("minecraft:villager", "minecraft:mason", false)).isEmpty());
	}
}
