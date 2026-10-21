package io.github.leks2205.hangulquest.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a click opens a conversation, and which one. */
class DialogueTriggerTest {
	private static final DialogueTrigger.Click SNEAK_CLICK = new DialogueTrigger.Click(true, true, true, false, false);
	private static final String VILLAGER = "minecraft:villager";

	private static Dialogue dialogue(String id, String entity, String profession, Boolean baby) {
		Dialogue.Node only = new Dialogue.Node("a", Speaker.FARMER, SpeechLevel.HAEYO, "안녕하세요!", null, List.of(),
				null, List.of(), true, null);
		return new Dialogue(id, 1, new Dialogue.Trigger(entity, profession, baby), "a", Map.of("a", only));
	}

	private static DialogueSet set(Dialogue... dialogues) {
		return DialogueSet.of(List.of(dialogues));
	}

	private static Optional<Dialogue> select(DialogueSet set, DialogueTrigger.Click click, String type, String profession,
											 boolean baby) {
		return DialogueTrigger.select(set, click, new DialogueTrigger.Target(type, profession, baby));
	}

	@Test
	void sneakingWithTheMainHandOnAVillagerOpensIt() {
		DialogueSet set = set(dialogue("greetings", VILLAGER, "minecraft:farmer", false));
		assertEquals("greetings", select(set, SNEAK_CLICK, VILLAGER, "minecraft:farmer", false).orElseThrow().id());
	}

	@Test
	void aPlainClickIsLeftToTheGameSoTradingWorks() {
		DialogueSet set = set(dialogue("greetings", VILLAGER, "minecraft:farmer", false));
		DialogueTrigger.Click standing = new DialogueTrigger.Click(true, true, false, false, false);
		assertFalse(DialogueTrigger.wanted(standing));
		assertTrue(select(set, standing, VILLAGER, "minecraft:farmer", false).isEmpty());
	}

	@Test
	void theOffHandTheServerSideASpectatorAndAnOpenScreenAreIgnored() {
		DialogueSet set = set(dialogue("greetings", VILLAGER, null, null));
		for (DialogueTrigger.Click click : List.of(
				new DialogueTrigger.Click(true, false, true, false, false),
				new DialogueTrigger.Click(false, true, true, false, false),
				new DialogueTrigger.Click(true, true, true, true, false),
				new DialogueTrigger.Click(true, true, true, false, true))) {
			assertFalse(DialogueTrigger.wanted(click), click.toString());
			assertTrue(select(set, click, VILLAGER, null, false).isEmpty(), click.toString());
		}
	}

	@Test
	void anotherEntityOrProfessionDoesNotMatch() {
		DialogueSet set = set(dialogue("greetings", VILLAGER, "minecraft:farmer", false));
		assertTrue(select(set, SNEAK_CLICK, "minecraft:wandering_trader", null, false).isEmpty());
		assertTrue(select(set, SNEAK_CLICK, VILLAGER, "minecraft:mason", false).isEmpty());
		assertTrue(select(set, SNEAK_CLICK, VILLAGER, "minecraft:farmer", true).isEmpty(), "a baby is not a farmer");
	}

	@Test
	void theBabyFlagSeparatesChildrenFromAdults() {
		DialogueSet set = set(dialogue("child", VILLAGER, null, true), dialogue("adults", VILLAGER, null, false));
		assertEquals("child", select(set, SNEAK_CLICK, VILLAGER, "minecraft:none", true).orElseThrow().id());
		assertEquals("adults", select(set, SNEAK_CLICK, VILLAGER, "minecraft:none", false).orElseThrow().id());
	}

	@Test
	void theMostSpecificConversationWinsAndTiesGoByName() {
		DialogueSet set = set(dialogue("zeta", VILLAGER, null, null), dialogue("alpha", VILLAGER, null, null),
				dialogue("farmer", VILLAGER, "minecraft:farmer", null));
		assertEquals("farmer", select(set, SNEAK_CLICK, VILLAGER, "minecraft:farmer", false).orElseThrow().id());
		assertEquals("alpha", select(set, SNEAK_CLICK, VILLAGER, "minecraft:mason", false).orElseThrow().id());
	}

	@Test
	void noContentMeansNoConversation() {
		assertTrue(select(DialogueSet.EMPTY, SNEAK_CLICK, VILLAGER, null, false).isEmpty());
		assertTrue(select(null, SNEAK_CLICK, VILLAGER, null, false).isEmpty());
		assertTrue(DialogueTrigger.select(DialogueSet.EMPTY, SNEAK_CLICK, null).isEmpty());
	}
}
