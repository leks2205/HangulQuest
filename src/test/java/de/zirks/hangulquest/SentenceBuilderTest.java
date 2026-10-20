package de.zirks.hangulquest;

import de.zirks.hangulquest.data.Category;
import de.zirks.hangulquest.data.Origin;
import de.zirks.hangulquest.data.Word;
import de.zirks.hangulquest.quest.Condition;
import de.zirks.hangulquest.quest.Goal;
import de.zirks.hangulquest.quest.SentenceBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentenceBuilderTest {
	private static Word word(String ko) {
		return new Word("concept:x", ko, "Test", "Test", Category.BLOCKS, 1, "x", null, null, null,
				null, Origin.CURATED, false, null);
	}

	@Test
	void breakBlockUsesCountAndVerb() {
		assertEquals("돌을 3개 캐요.",
				SentenceBuilder.normal(new Goal.BreakBlock("minecraft:stone", 3, null), word("돌"), List.of()));
	}

	@Test
	void logUsesChopVerb() {
		assertEquals("통나무를 2개 베어요.",
				SentenceBuilder.normal(new Goal.BreakBlock("minecraft:oak_log", 2, null), word("통나무"), List.of()));
	}

	@Test
	void placeAndAcquireAndDefeat() {
		assertEquals("조약돌을 3개 놓아요.",
				SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:cobblestone", 3), word("조약돌"), List.of()));
		assertEquals("사과를 2개 모아요.",
				SentenceBuilder.normal(new Goal.AcquireItem("minecraft:apple", 2), word("사과"), List.of()));
		assertEquals("좀비를 2마리 물리쳐요.",
				SentenceBuilder.normal(new Goal.DefeatEntity("minecraft:zombie", 2), word("좀비"), List.of()));
	}

	@Test
	void lookAndReachAndInteract() {
		assertEquals("살아 있는 소를 1초 동안 바라봐요.",
				SentenceBuilder.normal(new Goal.LookAtEntity("minecraft:cow", 800), word("소"), List.of()));
		assertEquals("숲 지형으로 가요.",
				SentenceBuilder.normal(new Goal.ReachBiome("minecraft:forest", 1000), word("숲"), List.of()));
		assertEquals("주민과 이야기해요.",
				SentenceBuilder.normal(new Goal.InteractEntity("minecraft:villager"), word("주민"), List.of()));
	}

	@Test
	void conditionsBecomePrefixes() {
		assertEquals("밤에 횃불을 놓아요.",
				SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:torch", 1), word("횃불"),
						List.of(new Condition.Time(Condition.Phase.NIGHT))));
	}

	@Test
	void koreanLevelRaisesTheEnding() {
		Goal goal = new Goal.BreakBlock("minecraft:stone", 3, null);
		assertEquals("돌을 3개 캐야 해요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 2));
		assertEquals("돌을 3개 캐야만 해요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 3));
		assertEquals("돌을 3개 캐지 않으면 안 돼요.",
				SentenceBuilder.normal(goal, word("돌"), List.of(), 4));
		assertEquals("돌을 3개 캐지 않고서는 다음 단서를 찾을 수 없어요.",
				SentenceBuilder.normal(goal, word("돌"), List.of(), 5));
		assertEquals("침대에서 자야 해요.",
				SentenceBuilder.normal(new Goal.UseBlock("minecraft:red_bed"), word("침대"), List.of(), 2));
		assertEquals("종을 울려야 해요.",
				SentenceBuilder.normal(new Goal.UseBlock("minecraft:bell"), word("종"), List.of(), 2));
		assertEquals("주민과 이야기해야 해요.",
				SentenceBuilder.normal(new Goal.InteractEntity("minecraft:villager"), word("주민"), List.of(), 2));
		assertEquals("돌을 3개 캐지 않고서는 이 길을 끝낼 수 없어요.",
				SentenceBuilder.normal(goal, word("돌"), List.of(), 5, true));
	}

	@Test
	void conditionsAreSpokenInKorean() {
		SentenceBuilder.Namer names = new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return "해변";
			}

			@Override
			public String entity(String id) {
				return "좀비";
			}

			@Override
			public String block(String id) {
				return "횃불";
			}
		};
		Word campfire = word("모닥불");
		Goal placeCampfire = new Goal.PlaceBlock("minecraft:campfire", 1);
		assertEquals("밤에 모닥불을 놓아요.", SentenceBuilder.normal(placeCampfire, campfire,
				List.of(new Condition.Time(Condition.Phase.NIGHT)), 1, false, names));
		assertEquals("해변 지형에서 모닥불을 놓아요.", SentenceBuilder.normal(placeCampfire, campfire,
				List.of(new Condition.InBiome("minecraft:beach")), 1, false, names));
		assertEquals("밤에 해변 지형에서 모닥불을 놓아요.", SentenceBuilder.normal(placeCampfire, campfire,
				List.of(new Condition.Time(Condition.Phase.NIGHT),
						new Condition.InBiome("minecraft:beach")), 1, false, names));
		assertEquals("지옥 차원에서 횃불을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceBlock("minecraft:torch", 1), word("횃불"),
				List.of(new Condition.InDimension("minecraft:the_nether")), 1, false, names));
		assertEquals("높이 0 이하에서 돌을 3개 캐요.", SentenceBuilder.normal(
				new Goal.BreakBlock("minecraft:stone", 3, null), word("돌"),
				List.of(new Condition.YRange(-64, 0)), 1, false, names));
		assertEquals("높이 120블록 위에서 돌을 3개 캐요.", SentenceBuilder.normal(
				new Goal.BreakBlock("minecraft:stone", 3, null), word("돌"),
				List.of(new Condition.YRange(120, 320)), 1, false, names));
		assertEquals("좀비 근처에서 좀비를 2마리 물리쳐요.", SentenceBuilder.normal(
				new Goal.DefeatEntity("minecraft:zombie", 2), word("좀비"),
				List.of(new Condition.NearEntity("minecraft:zombie", 8.0)), 1, false, names));
	}

	@Test
	void undergroundHeightDescends() {
		assertEquals("높이 0 이하로 내려가요.", SentenceBuilder.normal(
				new Goal.ReachHeight(-64, 0), word("동굴"), List.of()));
		assertEquals("땅 위로 올라가요.", SentenceBuilder.normal(
				new Goal.ReachHeight(0, 320), word("산"), List.of()));
	}

	@Test
	void hintDropsCounts() {
		assertEquals("돌을 캐요.", SentenceBuilder.hint(new Goal.BreakBlock("minecraft:stone", 4, null), word("돌")));
		assertTrue(SentenceBuilder.hint(new Goal.ReachBiome("minecraft:forest", 1000), word("숲"))
				.contains("가요"));
	}

	@Test
	void topikSixUsesObligation() {
		String sentence = SentenceBuilder.normal(new Goal.BreakBlock("minecraft:stone", 1, null),
				word("돌"), List.of(), 6);
		assertEquals("돌을 캘 수밖에 없어요.", sentence);
		assertEquals("해변 지형으로 갈 수밖에 없어요.",
				SentenceBuilder.normal(new Goal.ReachBiome("minecraft:beach", 1000), word("해변"),
						List.of(), 6));
		assertEquals("상자를 열 수밖에 없어요.",
				SentenceBuilder.normal(new Goal.UseBlock("minecraft:chest"), word("상자"),
						List.of(), 6));
	}

}
