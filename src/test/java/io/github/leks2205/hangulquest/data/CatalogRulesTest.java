package io.github.leks2205.hangulquest.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The technical-entry filter of Catalog.addDynamic and the category mapping of Concept. */
class CatalogRulesTest {
	@Test
	void learnableBlocksWithAirOrLightInTheirNameAreKept() {
		// The old substring test dropped all of these.
		for (String path : List.of("oak_stairs", "stone_brick_stairs", "cut_copper_stairs", "light_gray_wool",
				"light_gray_concrete", "light_blue_wool", "light_blue_stained_glass", "lightning_rod",
				"daylight_detector", "light_weighted_pressure_plate", "lantern", "chair", "repair_table")) {
			assertFalse(Catalog.isTechnical(path), path + " is real vocabulary");
		}
	}

	@Test
	void technicalEntriesAreStillDropped() {
		for (String path : List.of("air", "cave_air", "void_air", "light", "barrier", "jigsaw", "structure_block",
				"command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
				"debug_stick", "knowledge_book", "moving_piston", "player_head", "test_block",
				"test_instance_block", "creeper_spawn_egg", "music_disc_13", "music_disc_pigstep",
				"creeper_banner_pattern", "netherite_upgrade_smithing_template", "silence_armor_trim_smithing_template",
				"infested_stone", "infested_deepslate")) {
			assertTrue(Catalog.isTechnical(path), path + " is not vocabulary");
		}
	}

	@Test
	void everyConceptCategoryMapsToItself() {
		List<String> problems = new ArrayList<>();
		for (Concept concept : ConceptLoader.load()) {
			if (!concept.hasKnownCategory()) {
				problems.add(concept.id + ": unknown category " + concept.category);
			} else if (!concept.category.equals(concept.categoryEnum().name())) {
				problems.add(concept.id + ": " + concept.category + " became " + concept.categoryEnum());
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void itemsAndBuildingAreRealCategories() {
		Concept concept = new Concept();
		concept.category = "ITEMS";
		assertEquals(Category.ITEMS, concept.categoryEnum());
		concept.category = "BUILDING";
		assertEquals(Category.BUILDING, concept.categoryEnum());
		concept.category = "NOPE";
		assertFalse(concept.hasKnownCategory());
		assertEquals(Category.MISC, concept.categoryEnum());
		assertNotEquals(Category.MISC, Category.ITEMS);
		assertFalse(Category.ITEMS.label().isBlank());
		assertFalse(Category.BUILDING.label().isBlank());
	}

	@Test
	void shippedItemsAndBuildingWordsStayOutOfMisc() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		assertTrue(catalog.byCategory(Category.ITEMS).size() >= 200, "ITEMS words were folded into MISC");
		assertTrue(catalog.byCategory(Category.BUILDING).size() >= 6);
	}
}
