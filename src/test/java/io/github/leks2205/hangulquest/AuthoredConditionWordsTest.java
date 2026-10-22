package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An authored clue on a step with conditions is kept as written when it says the condition in its own
 * words (한밤에, 밤이 되면, 비가 내려요, 물속을 헤엄쳐요), instead of getting the generated phrase in front of it.
 * The check stays strict for everything else: 밤을 먹어요 is about a chestnut, not about the night.
 */
class AuthoredConditionWordsTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMER = KoreanFixtures.namer(CATALOG);
	private static final Goal TORCH = new Goal.PlaceBlock("minecraft:torch", 1);

	private static String authored(String clue, Condition condition) {
		return SentenceBuilder.authored(TORCH, clue, List.of(condition), null, NAMER, 1);
	}

	@Test
	void aClueThatNamesTheNightInItsOwnWordsIsLeftAlone() {
		Condition night = new Condition.Time(Condition.Phase.NIGHT);
		assertEquals("밤이 되면 횃불을 놓아요.", authored("밤이 되면 횃불을 놓아요.", night));
		assertEquals("한밤에 횃불을 놓아요.", authored("한밤에 횃불을 놓아요.", night));
		assertEquals("어두운 밤마다 횃불을 놓아요.", authored("어두운 밤마다 횃불을 놓아요.", night));
	}

	@Test
	void aChestnutIsNotTheNight() {
		Condition night = new Condition.Time(Condition.Phase.NIGHT);
		assertEquals("밤에 밤을 먹어요.", authored("밤을 먹어요.", night));
		Condition day = new Condition.Time(Condition.Phase.DAY);
		assertEquals("낮에 낮은 담장을 놓아요.", authored("낮은 담장을 놓아요.", day), "낮다 is not the daytime");
	}

	@Test
	void aClueThatTalksAboutRainOrWaterIsLeftAlone() {
		Condition rain = new Condition.WeatherState(Condition.Weather.RAIN);
		assertEquals("비가 내려요. 횃불을 놓아요.", authored("비가 내려요. 횃불을 놓아요.", rain));
		assertEquals("비가 올 때 비료를 줘요.", authored("비료를 줘요.", rain));
		Condition water = new Condition.InWater();
		assertEquals("물속을 헤엄쳐요.", authored("물속을 헤엄쳐요.", water));
		assertEquals("물속에서 횃불을 놓아요.", authored("횃불을 놓아요.", water));
	}

	@Test
	void theBiomeNameCountsAsTheBiomePhrase() {
		Condition beach = new Condition.InBiome("minecraft:beach");
		assertEquals("해변에서 횃불을 놓아요.", authored("횃불을 놓아요.", beach));
		assertEquals("해변으로 가서 횃불을 놓아요.", authored("해변으로 가서 횃불을 놓아요.", beach));
	}

	@Test
	void everyConditionOfAnAuthoredClueIsStillSpoken() {
		// with a word of its own or with the phrase in front, never without both
		Condition night = new Condition.Time(Condition.Phase.NIGHT);
		for (String clue : List.of("횃불을 놓아요.", "밤이 되면 횃불을 놓아요.", "밤을 먹어요.")) {
			String said = authored(clue, night);
			assertTrue(said.contains("밤에") || said.contains("밤이"), said);
		}
	}
}
