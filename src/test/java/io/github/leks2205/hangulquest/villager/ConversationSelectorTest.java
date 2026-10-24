package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueParser;
import io.github.leks2205.hangulquest.dialogue.DialogueSet;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order in which a villager picks what to say (PLAN "Conversation selection"). */
class ConversationSelectorTest {
	private static final String VILLAGER = "minecraft:villager";
	private static final String FARMER = "minecraft:farmer";

	private static Dialogue dialogue(String id, int level, String trigger) {
		String speaker = trigger.contains("\"baby\":true") ? "child" : "villager";
		String ko = speaker.equals("child") ? "안녕!" : "안녕하세요!";
		String nodes = trigger.contains("\"intro\"")
				? "\"a\":{\"speaker\":\"" + speaker + "\",\"ko\":\"" + ko + "\",\"next\":\"b\"},\"b\":{\"speaker\":\""
				+ speaker + "\",\"selfIntro\":true,\"end\":true}"
				: "\"a\":{\"speaker\":\"" + speaker + "\",\"ko\":\"" + ko + "\",\"end\":true}";
		String json = "{\"id\":\"" + id + "\",\"level\":" + level + ",\"trigger\":{\"entity\":\"" + VILLAGER + "\"" + trigger
				+ "},\"start\":\"a\",\"nodes\":{" + nodes + "}}";
		DialogueParser.Parsed parsed = DialogueParser.parse(id, json.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.problems().isEmpty(), parsed.problems().toString());
		return parsed.dialogue();
	}

	private static final DialogueSet ALL = DialogueSet.of(List.of(
			dialogue("intro_lazy_plains", 1, ",\"kind\":\"intro\",\"personalities\":[\"LAZY\"],\"regions\":[\"PLAINS\"]"),
			dialogue("intro_lazy", 1, ",\"kind\":\"intro\",\"personalities\":[\"LAZY\"]"),
			dialogue("intro_baby", 1, ",\"baby\":true,\"kind\":\"intro\""),
			dialogue("ms_lazy_3", 1, ",\"kind\":\"milestone\",\"friendship\":3,\"personalities\":[\"LAZY\"]"),
			dialogue("ms_lazy_6", 1, ",\"kind\":\"milestone\",\"friendship\":6,\"personalities\":[\"LAZY\"]"),
			dialogue("banmal_lazy", 1, ",\"kind\":\"banmal_offer\",\"friendship\":6,\"personalities\":[\"LAZY\"]"),
			dialogue("talk_lazy_plains", 1, ",\"personalities\":[\"LAZY\"],\"regions\":[\"PLAINS\"]"),
			dialogue("talk_lazy", 1, ",\"personalities\":[\"LAZY\"]"),
			dialogue("talk_lazy_hard", 4, ",\"personalities\":[\"LAZY\"]"),
			dialogue("talk_farmer", 1, ",\"profession\":\"" + FARMER + "\",\"baby\":false"),
			dialogue("talk_any", 1, ""),
			dialogue("talk_snooty", 1, ",\"personalities\":[\"SNOOTY\"]"),
			dialogue("talk_baby", 1, ",\"baby\":true")));

	private static VillagerProfile lazy() {
		VillagerProfile profile = new VillagerProfile(new UUID(5, 5).toString(), "민준", Gender.MALE, Personality.LAZY,
				"v_lazy");
		profile.region = Region.PLAINS;
		return profile;
	}

	private static final Request BREAD = new Request("req_plains_01", 1, Request.Kind.GIVE, List.of(Region.PLAINS),
			List.of(), List.of(), null, null, 0, List.of(), null, null, Map.of());

	private static ConversationSelector.Situation adult(VillagerProfile profile, int level, long day) {
		return new ConversationSelector.Situation(profile, VILLAGER, FARMER, false, level, day, null, false, true, null);
	}

	private static String talk(ConversationSelector.Situation situation) {
		ConversationSelector.Choice choice = ConversationSelector.select(ALL, situation).orElseThrow();
		ConversationSelector.opened(situation.profile(), choice, situation.day());
		return ((ConversationSelector.Talk) choice).dialogue().id();
	}

	@Test
	void theFirstMeetingIsTheIntroductionForThePersonalityAndRegion() {
		VillagerProfile profile = lazy();
		assertEquals("intro_lazy_plains", talk(adult(profile, 1, 1)));
		assertTrue(!talk(adult(profile, 1, 1)).startsWith("intro"), "only once");
		VillagerProfile elsewhere = lazy();
		elsewhere.region = Region.DESERT;
		assertEquals("intro_lazy", talk(adult(elsewhere, 1, 1)), "the general one when the region has none");
	}

	@Test
	void aBabyGetsTheBabyIntroAndOnlyBabyTalk() {
		VillagerProfile baby = lazy();
		baby.baby = true;
		ConversationSelector.Situation situation = new ConversationSelector.Situation(baby, VILLAGER, null, true, 1, 1,
				BREAD, true, true, BREAD);
		assertEquals("intro_baby", talk(situation));
		assertEquals("talk_baby", talk(situation), "no hand-in, no offer, no adult talk");
		assertEquals("talk_baby", talk(situation));
	}

	@Test
	void aRequestThatCanBeHandedInComesBeforeEverythingButTheIntro() {
		VillagerProfile profile = lazy();
		profile.markSeen("intro_lazy");
		profile.friendship = 3;
		ConversationSelector.Situation ready = new ConversationSelector.Situation(profile, VILLAGER, FARMER, false, 1, 4,
				BREAD, true, false, null);
		ConversationSelector.Choice choice = ConversationSelector.select(ALL, ready).orElseThrow();
		assertInstanceOf(ConversationSelector.HandIn.class, choice);
		ConversationSelector.opened(profile, choice, 4);
		assertEquals(5, profile.friendship, "a finished request is +2");
		assertEquals(4, profile.requestDay);
	}

	@Test
	void anOpenRequestIsRemindedOfOnceADay() {
		VillagerProfile profile = lazy();
		profile.markSeen("intro_lazy_plains");
		ConversationSelector.Situation waiting = new ConversationSelector.Situation(profile, VILLAGER, FARMER, false, 1, 4,
				BREAD, false, false, null);
		ConversationSelector.Choice first = ConversationSelector.select(ALL, waiting).orElseThrow();
		assertInstanceOf(ConversationSelector.Reminder.class, first);
		ConversationSelector.opened(profile, first, 4);
		assertInstanceOf(ConversationSelector.Talk.class, ConversationSelector.select(ALL, waiting).orElseThrow(),
				"the second talk that day is small talk");
	}

	@Test
	void milestonesComeOnceInOrderThenTheBanmalOfferForAnAdvancedPlayer() {
		VillagerProfile profile = lazy();
		profile.markSeen("intro_lazy_plains");
		profile.friendship = 7;
		assertEquals("ms_lazy_3", talk(adult(profile, 3, 1)));
		assertEquals("ms_lazy_6", talk(adult(profile, 3, 1)));
		assertTrue(!talk(adult(profile, 3, 1)).startsWith("banmal"), "not below level 4");
		assertEquals("banmal_lazy", talk(adult(profile, 4, 1)));
		assertTrue(!talk(adult(profile, 4, 1)).startsWith("banmal"), "offered once");
		VillagerProfile friend = lazy();
		friend.markSeen("intro_lazy_plains");
		friend.markSeen("ms_lazy_3");
		friend.friendship = 5;
		assertTrue(!talk(adult(friend, 6, 1)).startsWith("banmal"), "only from friendship 6");
	}

	@Test
	void personalitiesThatNeverSwitchNeverOfferBanmal() {
		VillagerProfile snooty = new VillagerProfile(new UUID(6, 6).toString(), "서연", Gender.FEMALE, Personality.SNOOTY,
				"v_snooty");
		snooty.friendship = 10;
		assertTrue(!ConversationSelector.mayOfferBanmal(snooty, 6));
		VillagerProfile switched = lazy();
		switched.friendship = 10;
		assertTrue(ConversationSelector.mayOfferBanmal(switched, 4));
		switched.banmal = true;
		assertTrue(!ConversationSelector.mayOfferBanmal(switched, 6), "already switched");
	}

	@Test
	void aNewRequestIsOfferedOnceADayWhenThereIsRoom() {
		VillagerProfile profile = lazy();
		profile.markSeen("intro_lazy_plains");
		ConversationSelector.Situation offering = new ConversationSelector.Situation(profile, VILLAGER, FARMER, false, 1, 9,
				null, false, true, BREAD);
		ConversationSelector.Choice choice = ConversationSelector.select(ALL, offering).orElseThrow();
		assertEquals(new ConversationSelector.Offer(BREAD), choice);
		ConversationSelector.opened(profile, choice, 9);
		assertInstanceOf(ConversationSelector.Talk.class, ConversationSelector.select(ALL, offering).orElseThrow(),
				"one offer a day, accepted or not");
		ConversationSelector.Situation tomorrow = new ConversationSelector.Situation(profile, VILLAGER, FARMER, false, 1, 10,
				null, false, true, BREAD);
		assertInstanceOf(ConversationSelector.Offer.class, ConversationSelector.select(ALL, tomorrow).orElseThrow());
		ConversationSelector.Situation full = new ConversationSelector.Situation(profile, VILLAGER, FARMER, false, 1, 10,
				null, false, false, BREAD);
		assertInstanceOf(ConversationSelector.Talk.class, ConversationSelector.select(ALL, full).orElseThrow(),
				"three requests open: no offer");
	}

	@Test
	void smallTalkIsUnseenMostSpecificFirstThenTheLeastRecent() {
		VillagerProfile profile = lazy();
		profile.markSeen("intro_lazy_plains");
		List<String> order = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			order.add(talk(adult(profile, 1, 1)));
		}
		assertEquals(List.of("talk_lazy_plains", "talk_lazy", "talk_farmer", "talk_any", "talk_lazy_plains", "talk_lazy"),
				order, "personality and region, personality, profession, anything; then round again");
	}

	@Test
	void smallTalkAboveThePlayersLevelOrForAnotherPersonalityIsNeverPicked() {
		VillagerProfile profile = lazy();
		profile.markSeen("intro_lazy_plains");
		List<String> said = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			said.add(talk(adult(profile, 3, 1)));
		}
		assertTrue(!said.contains("talk_lazy_hard") && !said.contains("talk_snooty") && !said.contains("talk_baby"), said.toString());
		assertTrue(said.contains("talk_any"));
		VillagerProfile advanced = lazy();
		advanced.markSeen("intro_lazy_plains");
		List<String> later = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			later.add(talk(adult(advanced, 4, 1)));
		}
		assertTrue(later.contains("talk_lazy_hard"));
	}

	@Test
	void nothingFitsMeansNoConversation() {
		VillagerProfile profile = lazy();
		ConversationSelector.Situation trader = new ConversationSelector.Situation(profile, "minecraft:wandering_trader",
				null, false, 1, 1, null, false, true, null);
		assertEquals(Optional.empty(), ConversationSelector.select(ALL, trader));
	}
}
