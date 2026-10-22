package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.quest.Situation.Tag;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.leks2205.hangulquest.quest.PickerFixtures.obs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the place from an observation, and the tags of a trail's steps. */
class SituationTest {
	@Test
	void anOrdinaryDayInTheOpenIsNoContext() {
		Observation plains = obs(1_000);
		plains.biome = "minecraft:plains";
		plains.y = 70;
		assertEquals(Set.of(), Situation.of(plains));
	}

	@Test
	void eachContextOfTheTableIsRecognised() {
		Observation snowy = obs(1);
		snowy.biome = "minecraft:snowy_plains";
		assertEquals(EnumSet.of(Tag.SNOW), Situation.of(snowy));

		Observation peak = obs(1);
		peak.biome = "minecraft:jagged_peaks";
		peak.biomeTags.add("minecraft:is_mountain");
		peak.y = 150;
		assertTrue(Situation.of(peak).contains(Tag.MOUNTAIN));

		Observation high = obs(1);
		high.y = Situation.MOUNTAIN_Y;
		assertTrue(Situation.of(high).contains(Tag.MOUNTAIN), "height alone is enough");

		Observation cave = obs(1);
		cave.underground = true;
		cave.y = 20;
		assertEquals(EnumSet.of(Tag.CAVE), Situation.of(cave));

		Observation lush = obs(1);
		lush.biome = "minecraft:lush_caves";
		assertTrue(Situation.of(lush).contains(Tag.CAVE));

		Observation pit = obs(1);
		pit.underground = true;
		pit.y = 64;
		assertEquals(Set.of(), Situation.of(pit), "a shallow ditch is not a cave");

		Observation beach = obs(1);
		beach.biomeTags.add("minecraft:is_beach");
		assertEquals(EnumSet.of(Tag.SEA), Situation.of(beach));
		Observation swimming = obs(1);
		swimming.inWater = true;
		assertEquals(EnumSet.of(Tag.SEA), Situation.of(swimming));

		Observation jungle = obs(1);
		jungle.biome = "minecraft:jungle";
		assertEquals(EnumSet.of(Tag.JUNGLE), Situation.of(jungle));
		Observation swamp = obs(1);
		swamp.biome = "minecraft:swamp";
		assertEquals(EnumSet.of(Tag.SWAMP), Situation.of(swamp));
		Observation desert = obs(1);
		desert.biomeTags.add("minecraft:is_desert");
		assertEquals(EnumSet.of(Tag.DESERT), Situation.of(desert));

		Observation village = obs(1);
		village.nearbyEntities.put("minecraft:villager", 2);
		assertEquals(EnumSet.of(Tag.VILLAGE), Situation.of(village));
		Observation bell = obs(1);
		bell.lookedBlock = "minecraft:bell";
		assertEquals(EnumSet.of(Tag.VILLAGE), Situation.of(bell));

		Observation night = obs(1);
		night.clockTime = 15_000L;
		assertEquals(EnumSet.of(Tag.NIGHT), Situation.of(night));
	}

	@Test
	void contextsCombine() {
		Observation caveAtNight = obs(1);
		caveAtNight.underground = true;
		caveAtNight.y = 10;
		caveAtNight.clockTime = 15_000L;
		assertEquals(EnumSet.of(Tag.CAVE, Tag.NIGHT), Situation.of(caveAtNight));
	}

	@Test
	void otherDimensionsAreNoContext() {
		Observation nether = obs(1);
		nether.dimension = "minecraft:the_nether";
		nether.underground = true;
		nether.y = 10;
		assertEquals(Set.of(), Situation.of(nether));
	}

	private static Questline trail(QuestStep... steps) {
		return new Questline("t", "t", "t", "기타", 1, List.of(), Questline.Requires.none(), List.of(steps),
				new QuestStep("finale", List.of(), new Goal.PlaceBlock("minecraft:torch", 1),
						List.of(new Condition.Time(Condition.Phase.NIGHT)), null, null, null, null, null),
				"", "", "t", 1, 1, false);
	}

	private static QuestStep step(Goal goal, Condition... conditions) {
		return new QuestStep("s", List.of(), goal, List.of(conditions), null, null, null, null, null);
	}

	@Test
	void stepsPointAtThePlacesTheyAreMadeFor() {
		Map<Tag, Integer> hits = Situation.stepHits(trail(
				step(new Goal.ReachBiome("minecraft:snowy_taiga", 1000)),
				step(new Goal.ReachHeight(110, 200)),
				step(new Goal.BreakBlock("minecraft:iron_ore", 2, null), new Condition.YRange(0, 30)),
				step(new Goal.LookAtEntity("minecraft:cod", 800)),
				step(new Goal.UseBlock("minecraft:bell"))));

		assertEquals(1, hits.get(Tag.SNOW));
		assertEquals(1, hits.get(Tag.MOUNTAIN));
		assertEquals(1, hits.get(Tag.CAVE));
		assertEquals(1, hits.get(Tag.SEA));
		assertEquals(1, hits.get(Tag.VILLAGE));
		assertEquals(null, hits.get(Tag.NIGHT), "a torch set at night as the finale says nothing about the place");
	}

	@Test
	void aNightStepBeforeTheFinaleDoesCount() {
		Map<Tag, Integer> hits = Situation.stepHits(trail(
				step(new Goal.LieDown("minecraft:red_bed", 3000))));
		assertEquals(1, hits.get(Tag.NIGHT));
	}

	@Test
	void theTopicAndTheChapterAreAWeakerHint() {
		Questline mountain = trail(step(new Goal.PlaceBlock("minecraft:torch", 1))).withTopic("산");
		TrailTraits traits = TrailTraits.of(mountain, "cave");

		assertTrue(traits.fit(EnumSet.of(Tag.MOUNTAIN)) > 0);
		assertTrue(traits.fit(EnumSet.of(Tag.CAVE)) > 0);
		assertEquals(0, traits.fit(EnumSet.of(Tag.SEA)));
		TrailTraits steps = TrailTraits.of(trail(step(new Goal.ReachBiome("minecraft:jagged_peaks", 1000))), null);
		assertTrue(steps.fit(EnumSet.of(Tag.MOUNTAIN)) > traits.fit(EnumSet.of(Tag.MOUNTAIN)),
				"a step beats a topic");
	}
}
