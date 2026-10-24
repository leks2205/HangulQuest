package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.SpeechLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerProfilesTest {
	private static final Logger LOG = LoggerFactory.getLogger("villager-profiles-test");

	@TempDir
	Path dir;

	static UUID uuid(int n) {
		return new UUID(0x1234_5678_9abc_def0L * (n + 1), 0x0fed_cba9_8765_4321L + n * 7919L);
	}

	private VillagerProfile meet(VillagerProfiles profiles, int n) {
		return profiles.meet(uuid(n), Region.PLAINS, false, "minecraft:farmer", 1000L + n).profile();
	}

	@Test
	void aProfileIsMadeOnceAndReadBackUnchanged() {
		VillagerProfiles profiles = VillagerProfiles.open(dir, LOG);
		VillagerProfiles.Meeting first = profiles.meet(uuid(1), Region.DESERT, false, "minecraft:mason", 42L);
		assertTrue(first.fresh());
		VillagerProfile made = first.profile();
		made.friendship = 4;
		made.markSeen("vt_lazy_gen_01");
		made.banmal = true;
		profiles.save();

		VillagerProfile again = VillagerProfiles.open(dir, LOG).get(uuid(1));
		assertNotNull(again);
		assertEquals(made.name, again.name);
		assertEquals(made.gender, again.gender);
		assertEquals(made.personality, again.personality);
		assertEquals(Region.DESERT, again.region);
		assertEquals(made.voice, again.voice);
		assertEquals(4, again.friendship);
		assertEquals(42L, again.firstMet);
		assertTrue(again.hasSeen("vt_lazy_gen_01"));
		assertTrue(again.banmal);
		assertEquals("minecraft:mason", again.profession);
		assertFalse(VillagerProfiles.open(dir, LOG).meet(uuid(1), Region.SNOW, false, null, 99L).fresh());
		assertEquals(Region.DESERT, VillagerProfiles.open(dir, LOG).get(uuid(1)).region, "the region never changes");
	}

	@Test
	void genderAndPersonalityFollowTheUuidSoALostFileGivesTheSameVillager() throws IOException {
		VillagerProfile before = meet(VillagerProfiles.open(dir, LOG), 7);
		Files.delete(dir.resolve(VillagerProfiles.FILE));
		VillagerProfile after = meet(VillagerProfiles.open(dir, LOG), 7);
		assertEquals(before.gender, after.gender);
		assertEquals(before.personality, after.personality);
		assertEquals(before.name, after.name);
		assertEquals(before.personality.gender(), before.gender, "personalities are gender-locked");
	}

	@Test
	void everyPersonalityAndBothGendersComeUp() {
		Set<Personality> seen = EnumSet.noneOf(Personality.class);
		for (int i = 0; i < 200; i++) {
			Gender gender = VillagerProfiles.genderOf(uuid(i));
			seen.add(VillagerProfiles.personalityOf(uuid(i), gender));
		}
		assertEquals(EnumSet.allOf(Personality.class), seen);
	}

	@Test
	void theVoiceIsStoredAndStaysAfterReloadEvenIfTheFileSaysAnother() throws IOException {
		VillagerProfile made = meet(VillagerProfiles.open(dir, LOG), 3);
		assertEquals(made.personality.voice(), made.voice);
		String text = Files.readString(dir.resolve(VillagerProfiles.FILE), StandardCharsets.UTF_8)
				.replace("\"" + made.voice + "\"", "\"v_custom\"");
		Files.writeString(dir.resolve(VillagerProfiles.FILE), text, StandardCharsets.UTF_8);
		assertEquals("v_custom", VillagerProfiles.open(dir, LOG).get(uuid(3)).voice,
				"a stored voice wins over the current table");
	}

	@Test
	void aBabyKeepsNameGenderAndPersonalityWhenItGrowsUpAndThenUsesTheAdultVoice() {
		VillagerProfiles profiles = VillagerProfiles.open(dir, LOG);
		VillagerProfile child = profiles.meet(uuid(11), Region.TAIGA, true, null, 1L).profile();
		assertEquals(child.gender.childVoice(), child.currentVoice());
		String name = child.name;
		Personality personality = child.personality;
		profiles.save();

		VillagerProfiles later = VillagerProfiles.open(dir, LOG);
		VillagerProfile grown = later.meet(uuid(11), Region.TAIGA, false, "minecraft:farmer", 99L).profile();
		assertEquals(name, grown.name);
		assertEquals(personality, grown.personality);
		assertEquals(personality.voice(), grown.currentVoice());
	}

	@Test
	void villagersGetDifferentNamesOfTheirGenderWhileNamesRemain() {
		VillagerProfiles profiles = VillagerProfiles.open(dir, LOG);
		Set<String> given = new HashSet<>();
		int males = 0;
		for (int i = 0; males < VillagerNames.MALE.size(); i++) {
			VillagerProfile profile = meet(profiles, i);
			assertEquals(profile.gender, VillagerNames.byKo(profile.name).gender(), profile.name);
			if (profile.gender == Gender.MALE) {
				males++;
				assertTrue(given.add(profile.name), "name given twice: " + profile.name);
			}
		}
	}

	@Test
	void theFirstPickFollowsTheUuid() {
		Set<String> picked = new HashSet<>();
		for (int i = 0; i < 12; i++) {
			picked.add(VillagerNames.pick(uuid(i), Gender.FEMALE, Set.of()).ko());
		}
		assertTrue(picked.size() > 4, picked.toString());
		assertEquals(VillagerNames.pick(uuid(3), Gender.MALE, Set.of()), VillagerNames.pick(uuid(3), Gender.MALE, Set.of()));
		VillagerNames.Name own = VillagerNames.pick(uuid(5), Gender.MALE, Set.of());
		assertFalse(own.equals(VillagerNames.pick(uuid(5), Gender.MALE, Set.of(own.ko()))));
	}

	@Test
	void theNamesFileOf30IsMigratedAndItsNamesAreKept() throws IOException {
		UUID male = uuid(20);
		UUID female = uuid(21);
		UUID odd = uuid(22);
		Files.writeString(dir.resolve(VillagerProfiles.LEGACY_FILE), "{\"version\":1,\"names\":{"
				+ "\"" + male + "\":\"태민\",\"" + female + "\":\"보라\",\"" + odd + "\":\"옛이름\",\"not-a-uuid\":\"민준\"}}",
				StandardCharsets.UTF_8);
		VillagerProfiles profiles = VillagerProfiles.open(dir, LOG);
		assertEquals(3, profiles.size());
		assertEquals("태민", profiles.get(male).name);
		assertEquals(Gender.MALE, profiles.get(male).gender);
		assertEquals(Gender.MALE, profiles.get(male).personality.gender());
		assertEquals("보라", profiles.get(female).name);
		assertEquals(Gender.FEMALE, profiles.get(female).gender);
		assertEquals("옛이름", profiles.get(odd).name);
		assertEquals(VillagerProfiles.genderOf(odd), profiles.get(odd).gender, "a name in no pool: gender from the uuid");
		assertNull(profiles.get(female).region, "the region is not known yet");
		assertTrue(Files.isRegularFile(dir.resolve(VillagerProfiles.FILE)));

		VillagerProfile met = profiles.meet(female, Region.SWAMP, false, null, 5L).profile();
		assertEquals(Region.SWAMP, met.region, "a migrated villager gets its region at the next meeting");
		assertEquals("보라", met.name);
		// the old file is read only once
		Files.writeString(dir.resolve(VillagerProfiles.LEGACY_FILE), "{\"version\":1,\"names\":{}}", StandardCharsets.UTF_8);
		assertEquals(3, VillagerProfiles.open(dir, LOG).size());
	}

	@Test
	void aBrokenFileStartsEmptyInsteadOfFailing() throws IOException {
		Files.writeString(dir.resolve(VillagerProfiles.FILE), "{ not json", StandardCharsets.UTF_8);
		VillagerProfiles profiles = VillagerProfiles.open(dir, LOG);
		assertEquals(0, profiles.size());
		assertNotNull(meet(profiles, 1));
	}

	@Test
	void aPersonalityOfTheWrongGenderOnDiskIsNotTrusted() throws IOException {
		VillagerProfile made = meet(VillagerProfiles.open(dir, LOG), 4);
		Personality wrong = Personality.of(made.gender == Gender.MALE ? Gender.FEMALE : Gender.MALE).get(0);
		String text = Files.readString(dir.resolve(VillagerProfiles.FILE), StandardCharsets.UTF_8)
				.replace("\"" + made.personality.name() + "\"", "\"" + wrong.name() + "\"");
		Files.writeString(dir.resolve(VillagerProfiles.FILE), text, StandardCharsets.UTF_8);
		assertEquals(made.personality, VillagerProfiles.open(dir, LOG).get(uuid(4)).personality);
	}

	@Test
	void theFileIsCappedButFriendsAndVillagersWithARequestAreNeverForgotten() {
		VillagerProfiles profiles = VillagerProfiles.open(dir, LOG);
		VillagerProfile friend = meet(profiles, 0);
		friend.friendship = VillagerProfile.KEEP_FRIENDSHIP;
		VillagerProfile waiting = meet(profiles, 1);
		meet(profiles, 2);
		profiles.protect(uuid -> uuid.equals(waiting.uuid));
		for (int i = 3; i < VillagerProfiles.MAX_ENTRIES + 10; i++) {
			meet(profiles, i);
		}
		assertEquals(VillagerProfiles.MAX_ENTRIES, profiles.size());
		assertNotNull(profiles.get(uuid(0)), "a friend stays");
		assertNotNull(profiles.get(uuid(1)), "a villager with an open request stays");
		assertNull(profiles.get(uuid(2)), "the oldest stranger goes first");
		assertNotNull(profiles.get(uuid(VillagerProfiles.MAX_ENTRIES + 9)));
	}

	@Test
	void friendshipRisesOncePerDayAndTwoForARequestUpToTen() {
		VillagerProfile profile = meet(VillagerProfiles.open(dir, LOG), 1);
		assertTrue(profile.talkedOn(5));
		assertFalse(profile.talkedOn(5), "the second talk of a day adds nothing");
		assertEquals(1, profile.friendship);
		assertTrue(profile.talkedOn(6));
		assertEquals(2, profile.friendship);
		profile.requestFinished();
		assertEquals(4, profile.friendship);
		for (int day = 7; day < 30; day++) {
			profile.talkedOn(day);
		}
		assertEquals(VillagerProfile.MAX_FRIENDSHIP, profile.friendship);
		profile.requestFinished();
		assertEquals(VillagerProfile.MAX_FRIENDSHIP, profile.friendship);
	}

	@Test
	void theSeenListKeepsTheMostRecentLast() {
		VillagerProfile profile = meet(VillagerProfiles.open(dir, LOG), 1);
		profile.markSeen("a");
		profile.markSeen("b");
		profile.markSeen("a");
		assertEquals(java.util.List.of("b", "a"), profile.seen);
		assertTrue(profile.seenRank("a") > profile.seenRank("b"));
		assertEquals(-1, profile.seenRank("c"));
	}

	@Test
	void theNamePoolsHave40UniqueKoreanNamesEachWithUniqueIds() {
		assertEquals(40, VillagerNames.MALE.size());
		assertEquals(40, VillagerNames.FEMALE.size());
		Set<String> ko = new HashSet<>();
		Set<String> ids = new HashSet<>();
		for (VillagerNames.Name name : VillagerNames.all()) {
			assertTrue(name.ko().matches("[가-힣]{2}"), name.ko());
			assertTrue(name.id().matches("[a-z]+"), name.id());
			assertTrue(ko.add(name.ko()), "twice: " + name.ko());
			assertTrue(ids.add(name.id()), "id twice: " + name.id());
		}
		for (String old : java.util.List.of("민준", "서연", "지우", "하준", "서윤", "도윤", "수아", "예준", "지민", "하은", "시우",
				"나은", "준서", "채원", "현우", "유나", "지호", "소율", "태민", "다은", "보라", "한결", "가온", "나래")) {
			assertNotNull(VillagerNames.byKo(old), "the 3.0 name " + old + " is in a pool");
		}
	}

	@Test
	void theSelfIntroductionPicksTheCopulaByTheLastSyllable() {
		assertEquals("저는 민준이에요.", VillagerNames.selfIntro("민준", SpeechLevel.HAEYO));
		assertEquals("저는 지우예요.", VillagerNames.selfIntro("지우", SpeechLevel.HAEYO));
		assertEquals("저는 서연입니다.", VillagerNames.selfIntro("서연", SpeechLevel.HAPSYO));
		assertEquals("나는 지우야.", VillagerNames.selfIntro("지우", SpeechLevel.BANMAL));
		assertEquals("나는 태민이야.", VillagerNames.selfIntro("태민", SpeechLevel.BANMAL));
	}

	@Test
	void regionsComeFromTheVillagerType() {
		assertEquals(Region.SNOW, Region.ofVillagerType("minecraft:snow"));
		assertEquals(Region.SWAMP, Region.ofVillagerType("minecraft:swamp"));
		assertNull(Region.ofVillagerType("othermod:moon"));
	}
}
