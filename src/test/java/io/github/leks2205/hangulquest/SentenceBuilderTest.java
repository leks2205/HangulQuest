package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Origin;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentenceBuilderTest {
	private static Word word(String ko) {
		return new Word("concept:x", ko, "Test", "Test", Category.BLOCKS, 1, "x", null, null, null,
				null, Origin.CURATED, false, null);
	}

	private static String clue(Goal goal, String ko) {
		return SentenceBuilder.normal(goal, word(ko), List.of());
	}

	@Test
	void breakBlockUsesCountAndVerb() {
		assertEquals("돌을 세 개 캐요.",
				SentenceBuilder.normal(new Goal.BreakBlock("minecraft:stone", 3, null), word("돌"), List.of()));
	}

	@Test
	void logUsesChopVerb() {
		assertEquals("통나무를 두 개 베어요.",
				SentenceBuilder.normal(new Goal.BreakBlock("minecraft:oak_log", 2, null), word("통나무"), List.of()));
	}

	@Test
	void placeAndAcquireAndDefeat() {
		assertEquals("조약돌을 세 개 쌓아요.",
				SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:cobblestone", 3), word("조약돌"), List.of()));
		assertEquals("조약돌을 놓아요.",
				SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:cobblestone", 1), word("조약돌"), List.of()));
		assertEquals("사과를 두 개 모아요.",
				SentenceBuilder.normal(new Goal.AcquireItem("minecraft:apple", 2), word("사과"), List.of()));
		assertEquals("좀비를 두 마리 물리쳐요.",
				SentenceBuilder.normal(new Goal.DefeatEntity("minecraft:zombie", 2), word("좀비"), List.of()));
	}

	@Test
	void lookAndReachAndInteract() {
		assertEquals("소를 일 초 동안 바라봐요.",
				SentenceBuilder.normal(new Goal.LookAtEntity("minecraft:cow", 800), word("소"), List.of()));
		assertEquals("숲으로 가요.",
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
	void koreanLevelFollowsTheLadder() {
		Goal goal = new Goal.BreakBlock("minecraft:stone", 3, null);
		assertEquals("돌을 세 개 캐요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 1));
		assertEquals("돌을 세 개 캐세요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 2));
		assertEquals("돌을 세 개 캐세요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 3));
		assertEquals("돌을 세 개 캐야 해요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 4));
		assertEquals("돌을 세 개 캐야 해요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 5));
		assertEquals("돌을 세 개 캐야 해요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 6));
		assertEquals("침대에서 자세요.",
				SentenceBuilder.normal(new Goal.UseBlock("minecraft:red_bed"), word("침대"), List.of(), 2));
		assertEquals("종을 울리세요.",
				SentenceBuilder.normal(new Goal.UseBlock("minecraft:bell"), word("종"), List.of(), 2));
		assertEquals("주민과 이야기하세요.",
				SentenceBuilder.normal(new Goal.InteractEntity("minecraft:villager"), word("주민"), List.of(), 2));
		// the finale ends like any other step
		assertEquals("돌을 세 개 캐야 해요.", SentenceBuilder.normal(goal, word("돌"), List.of(), 5, true));
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
		assertEquals("밤에 모닥불을 피워요.", SentenceBuilder.normal(placeCampfire, campfire,
				List.of(new Condition.Time(Condition.Phase.NIGHT)), 1, false, names));
		assertEquals("해변에서 모닥불을 피워요.", SentenceBuilder.normal(placeCampfire, campfire,
				List.of(new Condition.InBiome("minecraft:beach")), 1, false, names));
		assertEquals("밤에 해변에서 모닥불을 피워요.", SentenceBuilder.normal(placeCampfire, campfire,
				List.of(new Condition.Time(Condition.Phase.NIGHT),
						new Condition.InBiome("minecraft:beach")), 1, false, names));
		assertEquals("네더에서 횃불을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceBlock("minecraft:torch", 1), word("횃불"),
				List.of(new Condition.InDimension("minecraft:the_nether")), 1, false, names));
		assertEquals("엔드에서 횃불을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceBlock("minecraft:torch", 1), word("횃불"),
				List.of(new Condition.InDimension("minecraft:the_end")), 1, false, names));
		assertEquals("높이 0 이하에서 돌을 세 개 캐요.", SentenceBuilder.normal(
				new Goal.BreakBlock("minecraft:stone", 3, null), word("돌"),
				List.of(new Condition.YRange(-64, 0)), 1, false, names));
		assertEquals("높이 120블록 위에서 돌을 세 개 캐요.", SentenceBuilder.normal(
				new Goal.BreakBlock("minecraft:stone", 3, null), word("돌"),
				List.of(new Condition.YRange(120, 320)), 1, false, names));
		assertEquals("좀비 근처에서 좀비를 두 마리 물리쳐요.", SentenceBuilder.normal(
				new Goal.DefeatEntity("minecraft:zombie", 2), word("좀비"),
				List.of(new Condition.NearEntity("minecraft:zombie", 8.0)), 1, false, names));
	}

	@Test
	void unknownBiomeAndDimensionStayHonest() {
		String biome = SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:torch", 1), word("횃불"),
				List.of(new Condition.InBiome("minecraft:unknown")));
		assertEquals("그 생물 군계에서 횃불을 놓아요.", biome);
		String dimension = SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:torch", 1), word("횃불"),
				List.of(new Condition.InDimension("minecraft:somewhere")));
		assertEquals("다른 차원에서 횃불을 놓아요.", dimension);
		assertFalse((biome + dimension).contains("지형"));
	}

	@Test
	void undergroundHeightDescends() {
		// the first level says "down", the number comes with level 2
		assertEquals("아래로 내려가요.", SentenceBuilder.normal(
				new Goal.ReachHeight(-64, 0), word("동굴"), List.of()));
		assertEquals("높이 0 이하로 내려가세요.", SentenceBuilder.normal(
				new Goal.ReachHeight(-64, 0), word("동굴"), List.of(), 2));
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
	void topikSixInstructsAndOnlyAForcedConclusionIsResigned() {
		String sentence = SentenceBuilder.normal(new Goal.BreakBlock("minecraft:stone", 1, null),
				word("돌"), List.of(), 6);
		assertEquals("돌을 캐야 해요.", sentence);
		assertEquals("해변으로 가야 해요.",
				SentenceBuilder.normal(new Goal.ReachBiome("minecraft:beach", 1000), word("해변"),
						List.of(), 6));
		assertEquals("상자를 열어야 해요.",
				SentenceBuilder.normal(new Goal.UseBlock("minecraft:chest"), word("상자"),
						List.of(), 6));
		// the resigned form is kept for a sentence that states a forced conclusion
		assertEquals("돌을 캘 수밖에 없어요.", SentenceBuilder.forcedPhrase("돌을", Verb.MINE, 6));
		assertEquals("돌을 캐야 해요.", SentenceBuilder.forcedPhrase("돌을", Verb.MINE, 5));
	}

	// ------------------------------------------------------------------ numerals and particles

	@Test
	void countsUseNativeNumeralsUpToTwenty() {
		Goal.AcquireItem one = new Goal.AcquireItem("minecraft:apple", 1);
		assertEquals("사과를 구해요.", clue(one, "사과"));
		for (int n = 2; n <= 20; n++) {
			String expected = switch (n) {
				case 2 -> "두 개";
				case 3 -> "세 개";
				case 4 -> "네 개";
				case 10 -> "열 개";
				case 11 -> "열한 개";
				case 12 -> "열두 개";
				case 20 -> "스무 개";
				default -> null;
			};
			String text = clue(new Goal.AcquireItem("minecraft:apple", n), "사과");
			assertFalse(text.matches(".*\\d+개.*"), "digits with a native counter: " + text);
			if (expected != null) {
				assertTrue(text.contains(expected), n + " -> " + text);
			}
		}
		assertEquals("사과를 21개 모아요.", clue(new Goal.AcquireItem("minecraft:apple", 21), "사과"));
		assertEquals("좀비를 세 마리 물리쳐요.", clue(new Goal.DefeatEntity("minecraft:zombie", 3), "좀비"));
		// Seconds and heights stay Sino-Korean.
		assertEquals("돌을 이 초 동안 바라봐요.", clue(new Goal.LookAtBlock("minecraft:stone", 1500), "돌"));
	}

	@Test
	void directionParticleFollowsTheSyllable() {
		assertEquals("숲으로 가요.", clue(new Goal.ReachBiome("minecraft:forest", 1000), "숲"));
		assertEquals("바다로 가요.", clue(new Goal.ReachBiome("minecraft:ocean", 1000), "바다"));
		assertEquals("강으로 가요.", clue(new Goal.ReachBiome("minecraft:river", 1000), "강"));
		assertEquals("얼음 평원으로 가요.", clue(new Goal.ReachBiome("minecraft:snowy_plains", 1000), "얼음 평원"));
		assertEquals("사막으로 가요.", clue(new Goal.ReachBiome("minecraft:desert", 1000), "사막"));
		assertEquals("물로 가요.", clue(new Goal.ReachBiome("minecraft:river", 1000), "물"));
	}

	@Test
	void dimensionsUseTheirOfficialNames() {
		assertEquals("네더로 가요.", clue(new Goal.ReachDimension("minecraft:the_nether"), "지옥"));
		assertEquals("엔드로 가요.", clue(new Goal.ReachDimension("minecraft:the_end"), "엔더 차원"));
		assertEquals("오버월드로 가요.", clue(new Goal.ReachDimension("minecraft:overworld"), "x"));
	}

	@Test
	void livingDeadAreNotCalledAlive() {
		for (String undead : List.of("zombie", "skeleton", "husk", "drowned", "stray", "wither_skeleton",
				"phantom", "zoglin", "zombified_piglin", "zombie_villager")) {
			String text = clue(new Goal.LookAtEntity("minecraft:" + undead, 800), "좀비");
			assertFalse(text.contains("살아"), undead + ": " + text);
		}
	}

	@Test
	void verbDictionaryWordsNeverBecomeObjects() {
		Word verb = new Word("concept:look", "보다", "sehen", "see", "sehen", Category.ACTIONS, 1, "look", null,
				null, null, null, Origin.CURATED, false, null);
		SentenceBuilder.Namer names = new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return null;
			}

			@Override
			public String entity(String id) {
				return null;
			}

			@Override
			public String block(String id) {
				return "망원경";
			}
		};
		String text = SentenceBuilder.normal(new Goal.AcquireItem("minecraft:spyglass", 1), verb, List.of(), 1,
				false, names);
		assertEquals("망원경을 구해요.", text);
	}

	// ------------------------------------------------------------------ the right verb for the object

	@Test
	void breakVerbFitsTheBlock() {
		assertEquals("철 원석을 캐요.", clue(new Goal.BreakBlock("minecraft:iron_ore", 1, null), "철 원석"));
		assertEquals("흙을 파요.", clue(new Goal.BreakBlock("minecraft:dirt", 1, null), "흙"));
		assertEquals("꽃을 꺾어요.", clue(new Goal.BreakBlock("minecraft:poppy", 1, null), "꽃"));
		assertEquals("묘목을 뽑아요.", clue(new Goal.BreakBlock("minecraft:oak_sapling", 1, null), "묘목"));
		assertEquals("유리를 깨요.", clue(new Goal.BreakBlock("minecraft:glass", 1, null), "유리"));
		assertEquals("밀을 수확해요.", clue(new Goal.BreakBlock("minecraft:wheat", 1, null), "밀"));
		assertEquals("버섯을 따요.", clue(new Goal.BreakBlock("minecraft:red_mushroom", 1, null), "버섯"));
		assertEquals("양탄자를 부숴요.", clue(new Goal.BreakBlock("minecraft:red_carpet", 1, null), "양탄자"));
		assertEquals("대나무를 베어요.", clue(new Goal.BreakBlock("minecraft:bamboo", 1, null), "대나무"));
	}

	@Test
	void placeVerbFitsTheObject() {
		assertEquals("묘목을 심어요.", clue(new Goal.PlaceBlock("minecraft:oak_sapling", 1), "묘목"));
		assertEquals("꽃을 세 송이 심어요.", clue(new Goal.PlaceBlock("minecraft:poppy", 3), "꽃"));
		assertEquals("양탄자를 깔아요.", clue(new Goal.PlaceBlock("minecraft:red_carpet", 1), "양탄자"));
		assertEquals("레일을 네 개 깔아요.", clue(new Goal.PlaceBlock("minecraft:rail", 4), "레일"));
		assertEquals("현수막을 걸어요.", clue(new Goal.PlaceBlock("minecraft:black_banner", 1), "현수막"));
		assertEquals("표지판을 세워요.", clue(new Goal.PlaceBlock("minecraft:oak_sign", 1), "표지판"));
		assertEquals("문을 달아요.", clue(new Goal.PlaceBlock("minecraft:oak_door", 1), "문"));
		assertEquals("횃불을 놓아요.", clue(new Goal.PlaceBlock("minecraft:torch", 1), "횃불"));
		assertEquals("모닥불을 피워요.", clue(new Goal.PlaceBlock("minecraft:campfire", 1), "모닥불"));
		assertEquals("거미줄을 쳐요.", clue(new Goal.PlaceBlock("minecraft:cobweb", 1), "거미줄"));
		assertEquals("석재 벽돌을 네 개 쌓아요.", clue(new Goal.PlaceBlock("minecraft:stone_bricks", 4), "석재 벽돌"));
		assertEquals("석재 벽돌을 놓아요.", clue(new Goal.PlaceBlock("minecraft:stone_bricks", 1), "석재 벽돌"));
		assertEquals("작업대를 놓아요.", clue(new Goal.PlaceBlock("minecraft:crafting_table", 1), "작업대"));
	}

	@Test
	void acquireAndConsumeVerbs() {
		assertEquals("에메랄드를 구해요.", clue(new Goal.AcquireItem("minecraft:emerald", 1), "에메랄드"));
		assertEquals("에메랄드를 다섯 개 모아요.", clue(new Goal.AcquireItem("minecraft:emerald", 5), "에메랄드"));
		assertEquals("철 곡괭이를 만들어요.", clue(new Goal.AcquireItem("minecraft:iron_pickaxe", 1), "철 곡괭이"));
		assertEquals("빵을 먹어요.", clue(new Goal.ConsumeItem("minecraft:bread", 1), "빵"));
		assertEquals("물약을 마셔요.", clue(new Goal.ConsumeItem("minecraft:potion", 1), "물약"));
		assertEquals("우유를 마셔요.", clue(new Goal.ConsumeItem("minecraft:milk_bucket", 1), "우유"));
		assertEquals("돌풍구를 던져요.", clue(new Goal.ConsumeItem("minecraft:wind_charge", 1), "돌풍구"));
	}

	@Test
	void animalsAreNotTalkedTo() {
		assertEquals("소에게 먹이를 줘요.", clue(new Goal.InteractEntity("minecraft:cow"), "소"));
		assertEquals("말을 타요.", clue(new Goal.InteractEntity("minecraft:horse"), "말"));
		assertEquals("고양이를 쓰다듬어요.", clue(new Goal.InteractEntity("minecraft:cat"), "고양이"));
		assertEquals("철 골렘을 만져요.", clue(new Goal.InteractEntity("minecraft:iron_golem"), "철 골렘"));
		assertEquals("주민과 이야기해요.", clue(new Goal.InteractEntity("minecraft:villager"), "주민"));
		assertEquals("떠돌이 상인과 이야기해요.",
				clue(new Goal.InteractEntity("minecraft:wandering_trader"), "떠돌이 상인"));
		assertEquals("소에게 먹이를 주세요.", SentenceBuilder.normal(new Goal.InteractEntity("minecraft:cow"),
				word("소"), List.of(), 2));
		assertEquals("소에게 먹이를 줘야 해요.", SentenceBuilder.normal(new Goal.InteractEntity("minecraft:cow"),
				word("소"), List.of(), 6));
	}

	@Test
	void useBlockVerbs() {
		assertEquals("참나무 문을 열어요.", clue(new Goal.UseBlock("minecraft:oak_door"), "참나무 문"));
		assertEquals("버튼을 눌러요.", clue(new Goal.UseBlock("minecraft:stone_button"), "버튼"));
		assertEquals("레버를 당겨요.", clue(new Goal.UseBlock("minecraft:lever"), "레버"));
		assertEquals("화로를 열어요.", clue(new Goal.UseBlock("minecraft:furnace"), "화로"));
		assertEquals("작업대를 열어요.", clue(new Goal.UseBlock("minecraft:crafting_table"), "작업대"));
	}

	// ------------------------------------------------------------------ finale ceremony sentences

	@Test
	void ceremonySentencesAreShortPastTense() {
		SentenceBuilder.Namer none = SentenceBuilder.Namer.NONE;
		Condition.Time night = new Condition.Time(Condition.Phase.NIGHT);
		assertEquals("밤에 횃불을 놓았어요.", SentenceBuilder.ceremony(new Goal.PlaceBlock("minecraft:torch", 1),
				word("횃불"), List.of(night), none));
		assertEquals("밤에 검은색 현수막을 걸었어요.", SentenceBuilder.ceremony(
				new Goal.PlaceBlock("minecraft:black_banner", 1), word("검은색 현수막"), List.of(night), none));
		assertEquals("돌을 세 개 캤어요.", SentenceBuilder.ceremony(new Goal.BreakBlock("minecraft:stone", 3, null),
				word("돌"), List.of(), none));
		assertEquals("에메랄드를 구했어요.", SentenceBuilder.ceremony(new Goal.AcquireItem("minecraft:emerald", 1),
				word("에메랄드"), List.of(), none));
		assertEquals("좀비를 물리쳤어요.", SentenceBuilder.ceremony(new Goal.DefeatEntity("minecraft:zombie", 1),
				word("좀비"), List.of(), none));
		assertEquals("보그드를 세 마리 물리쳤어요.", SentenceBuilder.ceremony(
				new Goal.DefeatEntity("minecraft:bogged", 3), word("보그드"), List.of(), none));
		assertEquals("숲에 도착했어요.", SentenceBuilder.ceremony(new Goal.ReachBiome("minecraft:forest", 1000),
				word("숲"), List.of(), none));
		assertEquals("네더에 도착했어요.", SentenceBuilder.ceremony(new Goal.ReachDimension("minecraft:the_nether"),
				word("x"), List.of(), none));
		assertEquals("침대에서 잤어요.", SentenceBuilder.ceremony(new Goal.UseBlock("minecraft:red_bed"),
				word("침대"), List.of(), none));
		assertEquals("종을 울렸어요.", SentenceBuilder.ceremony(new Goal.UseBlock("minecraft:bell"),
				word("종"), List.of(), none));
		assertEquals("소에게 먹이를 줬어요.", SentenceBuilder.ceremony(new Goal.InteractEntity("minecraft:cow"),
				word("소"), List.of(), none));
		assertEquals("말을 탔어요.", SentenceBuilder.ceremony(new Goal.InteractEntity("minecraft:horse"),
				word("말"), List.of(), none));
		assertEquals("주민과 이야기했어요.", SentenceBuilder.ceremony(new Goal.InteractEntity("minecraft:villager"),
				word("주민"), List.of(), none));
		assertEquals("해변에서 모닥불을 피웠어요.", SentenceBuilder.ceremony(
				new Goal.PlaceBlock("minecraft:campfire", 1), word("모닥불"),
				List.of(new Condition.InBiome("#minecraft:is_beach")), none));
	}

	@Test
	void ceremonyNeverUsesTheOldTemplate() {
		Goal[] goals = {
				new Goal.PlaceBlock("minecraft:poplar_log", 3), new Goal.AcquireItem("minecraft:lantern", 2),
				new Goal.LookAtEntity("minecraft:warden", 800), new Goal.ConsumeItem("minecraft:bread", 1)};
		for (Goal goal : goals) {
			String text = SentenceBuilder.ceremony(goal, word("랜턴"), List.of(), SentenceBuilder.Namer.NONE);
			assertFalse(text.contains("라는 길"), text);
			assertFalse(text.contains("의식"), text);
			assertTrue(text.endsWith("어요."), text);
		}
	}

	@Test
	void everyVerbHasAPastForm() {
		assertEquals("캤어요", Verb.MINE.past());
		assertEquals("놓았어요", Verb.PLACE.past());
		assertEquals("먹었어요", Verb.EAT.past());
		assertEquals("마셨어요", Verb.DRINK.past());
		assertEquals("봤어요", Verb.LOOK.past());
		assertEquals("물리쳤어요", Verb.DEFEAT.past());
		assertEquals("갔어요", Verb.GO.past());
		assertEquals("열었어요", Verb.OPEN.past());
		assertEquals("만들었어요", Verb.CRAFT.past());
		assertEquals("썼어요", Verb.USE.past());
		assertEquals("부쉈어요", Verb.BREAK.past());
		assertEquals("주웠어요", Verb.PICK_UP.past());
		assertEquals("심었어요", Verb.PLANT.past());
		assertEquals("깔았어요", Verb.SPREAD.past());
		assertEquals("걸었어요", Verb.HANG.past());
		assertEquals("줬어요", Verb.FEED.past());
		assertEquals("탔어요", Verb.RIDE.past());
		assertEquals("밝혔어요", Verb.LIGHT.past());
		for (Verb verb : Verb.values()) {
			assertTrue(verb.past().endsWith("어요"), verb + " -> " + verb.past());
			assertFalse(verb.past().contains("ㅆ"), verb.past());
		}
	}
}
