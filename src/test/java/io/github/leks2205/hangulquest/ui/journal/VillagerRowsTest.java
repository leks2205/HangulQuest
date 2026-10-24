package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.Region;
import io.github.leks2205.hangulquest.villager.Request;
import io.github.leks2205.hangulquest.villager.RequestSet;
import io.github.leks2205.hangulquest.villager.Requests;
import io.github.leks2205.hangulquest.villager.VillagerProfile;
import io.github.leks2205.hangulquest.villager.VillagerProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Villagers page: everyone met, in the order they were met, with friendship and the open request. */
class VillagerRowsTest {
	@TempDir
	Path dir;

	@Test
	void villagersStayInTheOrderTheyWereMetWhateverHappensToThem() {
		VillagerProfiles profiles = VillagerProfiles.open(dir, LoggerFactory.getLogger("villager-rows-test"));
		for (int i = 0; i < 5; i++) {
			profiles.meet(new UUID(i + 1, i + 1), Region.PLAINS, false, null, 100L + i);
		}
		ProgressData data = new ProgressData();
		Request bread = new Request("req_plains_01", 1, Request.Kind.GIVE, List.of(Region.PLAINS), List.of(), List.of(),
				new Goal.Give("minecraft:bread", "minecraft:villager", null, 1), null, 0, List.of(), null, null, Map.of());
		Requests requests = new Requests(data, RequestSet.of(List.of(bread)), () -> {
		}, id -> null);
		String third = new UUID(3, 3).toString();
		requests.accept(bread, third, "x", 1L);
		profiles.get(new UUID(5, 5)).friendship = 9;
		profiles.meet(new UUID(2, 2), Region.DESERT, true, null, 999L);

		List<VillagerRows.Row> rows = VillagerRows.of(profiles.all(), requests);
		assertEquals(List.of(1L, 2L, 3L, 4L, 5L), rows.stream().map(row -> UUID.fromString(row.profile().uuid)
				.getMostSignificantBits()).toList());
		assertTrue(rows.get(2).hasRequest());
		assertEquals("req_plains_01", rows.get(2).requestDef().id());
		assertFalse(rows.get(0).hasRequest());
		assertNull(rows.get(0).requestDef());
		assertEquals(Region.PLAINS, rows.get(1).profile().region, "the region stays the first one");
	}

	@Test
	void anOpenRequestOfAVillagerWithoutProfileStillHasARow() {
		ProgressData data = new ProgressData();
		Request bread = new Request("req_plains_01", 1, Request.Kind.SHOW, List.of(Region.PLAINS), List.of(), List.of(),
				null, "minecraft:bread", 1, List.of(), null, null, Map.of());
		Requests requests = new Requests(data, RequestSet.of(List.of(bread)), () -> {
		}, id -> null);
		requests.accept(bread, new UUID(9, 9).toString(), "보라", 1L);
		List<VillagerRows.Row> rows = VillagerRows.of(List.of(), requests);
		assertEquals(1, rows.size());
		assertNull(rows.get(0).profile());
		assertEquals("보라", rows.get(0).name());
		assertFalse(requests.ready(data.requests.open.get(0), "minecraft:bread", 1), "untracked: no log, so no hand-in");
		data.requests.open.get(0).tracked = true;
		assertTrue(requests.ready(data.requests.open.get(0), "minecraft:bread", 1));
		assertFalse(requests.ready(data.requests.open.get(0), "minecraft:apple", 1));
	}

	@Test
	void friendshipIsTenHearts() {
		assertEquals("♥♥♥♡♡♡♡♡♡♡", VillagerRows.hearts(3));
		assertEquals("♡".repeat(10), VillagerRows.hearts(0));
		assertEquals("♥".repeat(10), VillagerRows.hearts(VillagerProfile.MAX_FRIENDSHIP + 4));
	}

	@Test
	void everyLabelOfThePageExistsInEveryLanguage() {
		for (String code : List.of("en_us", "de_de", "es_es", "fr_fr")) {
			Map<String, String> table = DialogueFixtures.lang(code);
			assertFalse(table.getOrDefault("hangulquest.ui.journal.tab.villagers", "").isBlank(), code);
			for (Personality personality : Personality.values()) {
				assertFalse(table.getOrDefault("hangulquest.ui.personality." + personality.id(), "").isBlank(),
						code + " " + personality);
			}
			for (Region region : Region.values()) {
				assertFalse(table.getOrDefault("hangulquest.ui.region." + region.id(), "").isBlank(), code + " " + region);
			}
			for (String key : List.of("empty", "count", "request", "request_done", "track", "untrack", "abandon",
					"abandoned", "log_full", "banmal", "child", "region_unknown", "jobless", "request_gone")) {
				assertFalse(table.getOrDefault("hangulquest.ui.villagers." + key, "").isBlank(), code + " " + key);
			}
		}
	}
}
