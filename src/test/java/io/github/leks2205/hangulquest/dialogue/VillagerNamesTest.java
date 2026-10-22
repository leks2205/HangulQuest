package io.github.leks2205.hangulquest.dialogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerNamesTest {
	private static final Logger LOG = LoggerFactory.getLogger("villager-names-test");

	@TempDir
	Path profile;

	private static UUID uuid(int n) {
		return new UUID(0x1234_5678_9abc_def0L * (n + 1), 0x0fed_cba9_8765_4321L + n * 7919L);
	}

	@Test
	void aVillagerKeepsItsNameAcrossRestarts() {
		UUID villager = uuid(1);
		String first = VillagerNames.open(profile, LOG).nameFor(villager);
		assertTrue(VillagerNames.GIVEN.contains(first));
		assertEquals(first, VillagerNames.open(profile, LOG).nameFor(villager), "read back from the file");
		assertTrue(Files.isRegularFile(profile.resolve(VillagerNames.FILE)));
	}

	@Test
	void aNameOnDiskStaysEvenIfTheListWouldPickAnotherOne() throws IOException {
		UUID villager = uuid(2);
		Files.writeString(profile.resolve(VillagerNames.FILE),
				"{\"version\":1,\"names\":{\"" + villager + "\":\"옛이름\"}}", StandardCharsets.UTF_8);
		assertEquals("옛이름", VillagerNames.open(profile, LOG).nameFor(villager));
	}

	@Test
	void villagersGetDifferentNamesWhileNamesRemain() {
		VillagerNames names = VillagerNames.open(profile, LOG);
		Set<String> given = new HashSet<>();
		for (int i = 0; i < VillagerNames.GIVEN.size(); i++) {
			assertTrue(given.add(names.nameFor(uuid(i))), "name given twice at villager " + i);
		}
		assertEquals(Set.copyOf(VillagerNames.GIVEN), given);
		// The list is used up: more villagers than names is fine, names then repeat.
		assertTrue(VillagerNames.GIVEN.contains(names.nameFor(uuid(1000))));
	}

	@Test
	void theFirstPickFollowsTheUuidSoItIsNotJustTheOrderOfMeeting() {
		Set<String> picked = new HashSet<>();
		for (int i = 0; i < 12; i++) {
			picked.add(VillagerNames.pick(uuid(i), Set.of()));
		}
		assertTrue(picked.size() > 4, "the first free slot would give " + picked);
		assertEquals(VillagerNames.pick(uuid(3), Set.of()), VillagerNames.pick(uuid(3), Set.of()));
	}

	@Test
	void aTakenNameMovesToTheNextFreeOne() {
		UUID villager = uuid(5);
		String own = VillagerNames.pick(villager, Set.of());
		String other = VillagerNames.pick(villager, Set.of(own));
		assertFalse(own.equals(other));
		assertTrue(VillagerNames.GIVEN.contains(other));
	}

	@Test
	void aBrokenFileStartsEmptyInsteadOfFailing() throws IOException {
		Files.writeString(profile.resolve(VillagerNames.FILE), "{ not json", StandardCharsets.UTF_8);
		VillagerNames names = VillagerNames.open(profile, LOG);
		assertTrue(VillagerNames.GIVEN.contains(names.nameFor(uuid(7))));
	}

	@Test
	void entriesThatAreNotNamesOfUuidsAreDropped() throws IOException {
		UUID villager = uuid(8);
		Files.writeString(profile.resolve(VillagerNames.FILE), "{\"version\":1,\"names\":{\"not-a-uuid\":\"민준\","
				+ "\"" + villager + "\":\"이름이너무너무너무길어요\"}}", StandardCharsets.UTF_8);
		String name = VillagerNames.open(profile, LOG).nameFor(villager);
		assertTrue(VillagerNames.GIVEN.contains(name), name);
	}

	@Test
	void theFileDoesNotGrowWithoutLimit() throws IOException {
		VillagerNames names = VillagerNames.open(profile, LOG);
		for (int i = 0; i < VillagerNames.MAX_ENTRIES + 25; i++) {
			names.nameFor(uuid(i));
		}
		String saved = Files.readString(profile.resolve(VillagerNames.FILE), StandardCharsets.UTF_8);
		assertEquals(VillagerNames.MAX_ENTRIES, saved.split("\"[0-9a-f]{8}-[0-9a-f-]{27}\":", -1).length - 1);
		assertFalse(saved.contains(uuid(0).toString()), "the oldest entry is forgotten first");
	}

	@Test
	void theNamesAreKoreanGivenNames() {
		assertEquals(VillagerNames.GIVEN.size(), Set.copyOf(VillagerNames.GIVEN).size(), "no name twice");
		for (String name : VillagerNames.GIVEN) {
			assertTrue(name.matches("[가-힣]{2}"), name);
		}
	}
}
