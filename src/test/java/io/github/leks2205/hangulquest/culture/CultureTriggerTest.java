package io.github.leks2205.hangulquest.culture;

import io.github.leks2205.hangulquest.sense.CraftFact;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.story.MarkFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which notes are due: the first craft or find of an anchor item, and the real dates of the lunar holidays. */
class CultureTriggerTest {
	private static final CultureSet SET = CultureSet.parse("""
			{"holidays": {"seollal": {"before": 7, "after": 1, "dates": ["2027-02-07", "2028-01-27"]}},
			 "notes": [
			  {"id": "ondol", "titleKo": "온돌", "ko": "x", "icon": "minecraft:furnace", "anchors": ["minecraft:furnace"]},
			  {"id": "hanbok", "titleKo": "한복", "ko": "x", "icon": "minecraft:red_dye",
			   "anchors": ["minecraft:red_dye", "minecraft:blue_dye"]},
			  {"id": "seollal", "titleKo": "설날", "ko": "x", "icon": "minecraft:cake", "holiday": "seollal"}
			 ]}""");
	private static final LocalDate QUIET = LocalDate.of(2026, 10, 22);

	@TempDir
	Path tempDir;

	private MarkFile shown;

	@BeforeEach
	void setUp() {
		shown = MarkFile.open(tempDir, "culture-seen.json", LoggerFactory.getLogger("culture-test"));
	}

	private static List<String> ids(List<CultureTrigger.Due> due) {
		return due.stream().map(entry -> entry.note().id()).toList();
	}

	@Test
	void anAnchorItemMakesItsNoteDue() {
		assertEquals(List.of("ondol"), ids(CultureTrigger.due(SET, Set.of("minecraft:furnace", "minecraft:dirt"), QUIET, shown)));
		assertEquals(List.of("hanbok"), ids(CultureTrigger.due(SET, Set.of("minecraft:blue_dye"), QUIET, shown)));
		assertEquals(List.of(), ids(CultureTrigger.due(SET, Set.of("minecraft:dirt"), QUIET, shown)));
	}

	@Test
	void aNoteIsNotDueAgainOnceItWasShown() {
		CultureTrigger.Due due = CultureTrigger.due(SET, Set.of("minecraft:furnace"), QUIET, shown).get(0);
		assertEquals(CultureTrigger.ONCE, due.key());
		shown.set(due.note().id(), due.key());
		assertEquals(List.of(), ids(CultureTrigger.due(SET, Set.of("minecraft:furnace"), QUIET, shown)));
	}

	@Test
	void aHolidayNoteIsDueNearTheRealDateAndOnlyThen() {
		assertEquals(List.of(), ids(CultureTrigger.due(SET, Set.of(), LocalDate.of(2027, 1, 20), shown)));
		assertEquals(List.of("seollal"), ids(CultureTrigger.due(SET, Set.of(), LocalDate.of(2027, 2, 3), shown)));
		assertEquals(List.of("seollal"), ids(CultureTrigger.due(SET, Set.of(), LocalDate.of(2027, 2, 8), shown)));
		assertEquals(List.of(), ids(CultureTrigger.due(SET, Set.of(), LocalDate.of(2027, 2, 9), shown)));
	}

	@Test
	void aHolidayComesBackTheNextYearButNotTwiceInTheSameOne() {
		CultureTrigger.Due due = CultureTrigger.due(SET, Set.of(), LocalDate.of(2027, 2, 3), shown).get(0);
		assertEquals("2027-02-07", due.key(), "the mark is the date of the holiday");
		shown.set("seollal", due.key());
		assertEquals(List.of(), ids(CultureTrigger.due(SET, Set.of(), LocalDate.of(2027, 2, 7), shown)));
		assertEquals(List.of("seollal"), ids(CultureTrigger.due(SET, Set.of(), LocalDate.of(2028, 1, 22), shown)));
	}

	@Test
	void aHolidayWithoutADateInTheTableIsNeverDue() {
		assertEquals(List.of(), ids(CultureTrigger.due(SET, Set.of(), LocalDate.of(2031, 2, 3), shown)));
	}

	@Test
	void theItemsAreWhatWasCraftedAndWhatIsHeld() {
		Observation obs = new Observation();
		obs.addCrafted(new CraftFact("minecraft:furnace", 1, "crafting_table"));
		obs.inventory.put("minecraft:red_dye", 3);
		obs.inventory.put("minecraft:dirt", 0);
		assertEquals(Set.of("minecraft:furnace", "minecraft:red_dye"), CultureTrigger.itemsOf(obs));
	}

	@Test
	void whileTheInventoryIsStillSyncingNothingCountsAsFound() {
		Observation obs = new Observation();
		obs.inventory.put("minecraft:furnace", 1);
		obs.inventorySettling = true;
		assertTrue(CultureTrigger.itemsOf(obs).isEmpty());
		obs.addCrafted(new CraftFact("minecraft:stick", 4, "inventory"));
		assertEquals(Set.of("minecraft:stick"), CultureTrigger.itemsOf(obs), "a craft is the player's own act");
	}
}
