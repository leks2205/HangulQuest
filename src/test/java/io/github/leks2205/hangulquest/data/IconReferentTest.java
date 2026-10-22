package io.github.leks2205.hangulquest.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The picture of a word is not what the word names. Before the split, tooltips and look-at
 * cards showed 크리퍼 on gunpowder, 늑대 on a bone and 요리하다 on a campfire.
 */
class IconReferentTest {
	/** Item that used to resolve to the concept (its icon), and the concept it wrongly resolved to. */
	private static final Map<String, String> WRONGLY_SHOWN = new LinkedHashMap<>();

	static {
		String[][] pairs = {
				{"beef", "cow"}, {"porkchop", "pig"}, {"mutton", "sheep"}, {"chicken", "chicken"},
				{"saddle", "horse"}, {"bone", "wolf"}, {"cod", "cat"}, {"rabbit_foot", "rabbit"},
				{"sweet_berries", "fox"}, {"honeycomb", "bee"}, {"scute", "turtle"}, {"ink_sac", "squid"},
				{"rotten_flesh", "zombie"}, {"gunpowder", "creeper"}, {"string", "spider"},
				{"ender_pearl", "enderman"}, {"glass_bottle", "witch"}, {"emerald", "villager"},
				{"goat_horn", "goat"}, {"dragon_egg", "ender_dragon"}, {"parrot_spawn_egg", "parrot"},
				{"bat_spawn_egg", "bat"}, {"frog_spawn_egg", "frog"}, {"slime_ball", "slime"},
				{"lily_pad", "river"}, {"netherrack", "nether"}, {"jungle_log", "jungle"},
				{"sunflower", "sky"}, {"clock", "moon"}, {"lightning_rod", "thunder"},
				{"candle", "ritual"}, {"player_head", "friend"}, {"golden_helmet", "crown"},
				{"oak_sapling", "tree"}, {"spyglass", "look"}, {"leather_boots", "walk"},
				{"scaffolding", "build"}, {"campfire", "cook"}, {"book", "talk"},
				{"water_bucket", "water"}, {"lava_bucket", "lava"}, {"milk_bucket", "milk"},
				{"honey_bottle", "honey"}};
		for (String[] pair : pairs) {
			WRONGLY_SHOWN.put("minecraft:" + pair[0], pair[1]);
		}
	}

	private static Catalog catalog() {
		return Catalog.ofConcepts(ConceptLoader.load());
	}

	private static String ko(Catalog catalog, String item) {
		return catalog.byItemId("minecraft:" + item).map(word -> word.ko).orElse(null);
	}

	@Test
	void knownCasesNoLongerResolveToTheWordTheyIllustrate() {
		Catalog catalog = catalog();
		assertEquals(43, WRONGLY_SHOWN.size());
		List<String> problems = new ArrayList<>();
		WRONGLY_SHOWN.forEach((item, wrongConcept) -> {
			Optional<Word> found = catalog.byItemId(item);
			if (found.isPresent() && wrongConcept.equals(found.get().conceptId)) {
				problems.add(item + " still shows " + found.get().ko);
			}
		});
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void tooltipLookupGivesTheRealName() {
		Catalog catalog = catalog();
		assertEquals("화약", ko(catalog, "gunpowder"));
		assertEquals("모닥불", ko(catalog, "campfire"));
		assertEquals("뼈", ko(catalog, "bone"));
		assertEquals("실", ko(catalog, "string"));
		assertEquals("에메랄드", ko(catalog, "emerald"));
		assertEquals("시계", ko(catalog, "clock"));
		assertEquals("해바라기", ko(catalog, "sunflower"));
		assertEquals("묘목", ko(catalog, "oak_sapling"));
		assertEquals("용암 양동이", ko(catalog, "lava_bucket"));
	}

	@Test
	void itemsWithoutAWordOfTheirOwnStayFreeForTheirVanillaName() {
		// No curated concept names these items, so Catalog.addDynamic may add the vanilla names.
		Catalog catalog = catalog();
		for (String item : List.of("spyglass", "scaffolding", "saddle", "scute", "goat_horn", "water_bucket",
				"milk_bucket", "honey_bottle", "rotten_flesh", "candle")) {
			Optional<Word> found = catalog.byItemId("minecraft:" + item);
			assertTrue(found.isEmpty() || found.get().referents.contains("minecraft:" + item),
					item + " resolved to " + found);
		}
		assertTrue(catalog.byItemId("minecraft:spyglass").isEmpty());
		assertTrue(catalog.byItemId("minecraft:scaffolding").isEmpty());
	}

	@Test
	void ambientCardsNeverIntroduceAConceptThroughItsIcon() {
		// HangulQuestClient.ambientCards resolves the looked-at block and each picked-up item with
		// Catalog.byItemId and introduces whatever word comes back.
		Catalog catalog = catalog();
		List<String> problems = new ArrayList<>();
		for (Concept concept : ConceptLoader.load()) {
			String icon = concept.iconItem();
			if (icon == null || concept.items.contains(icon)) {
				continue;
			}
			Optional<Word> found = catalog.byItemId(icon);
			if (found.isPresent() && concept.id.equals(found.get().conceptId)) {
				problems.add(concept.id + " is introduced by its icon " + icon);
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void everyResolvedWordActuallyNamesTheItem() {
		Catalog catalog = catalog();
		for (Concept concept : ConceptLoader.load()) {
			for (String item : concept.items) {
				Word found = catalog.byItemId(item).orElseThrow();
				assertTrue(found.referents.contains(item), item + " -> " + found);
			}
		}
	}

	@Test
	void animalsKeepTheirEntityAndTheirPicture() {
		Catalog catalog = catalog();
		Word cat = catalog.byConceptId("cat").orElseThrow();
		assertEquals("minecraft:cat", cat.entityId);
		assertEquals("minecraft:cod", cat.iconItem, "the picture is still drawn");
		assertTrue(cat.referents.isEmpty());
		assertEquals("고양이", catalog.byEntityId("minecraft:cat").orElseThrow().ko);

		Word creeper = catalog.byConceptId("creeper").orElseThrow();
		assertEquals("minecraft:gunpowder", creeper.iconItem);
		assertFalse(creeper.referents.contains("minecraft:gunpowder"));
	}

	@Test
	void iconDefaultsToTheFirstItem() {
		Concept concept = new Concept();
		concept.id = "x";
		concept.ko = "엑스";
		concept.de = "X";
		concept.en = "x";
		concept.items = List.of("minecraft:stone", "minecraft:dirt");
		assertEquals("minecraft:stone", concept.toWord().iconItem);
		assertEquals(List.of("minecraft:stone", "minecraft:dirt"), concept.toWord().referents);
		concept.icon = "minecraft:torch";
		assertEquals("minecraft:torch", concept.toWord().iconItem);
		assertEquals(List.of("minecraft:stone", "minecraft:dirt"), concept.toWord().referents);
	}

	@Test
	void iconFieldIsOnlyUsedWhenItDiffersFromTheReferents() {
		for (Concept concept : ConceptLoader.load()) {
			if (concept.icon != null) {
				assertTrue(concept.icon.startsWith("minecraft:"), concept.id + " icon " + concept.icon);
				assertFalse(concept.items.contains(concept.icon),
						concept.id + " lists its icon as a referent; drop the icon field");
			}
		}
	}
}
