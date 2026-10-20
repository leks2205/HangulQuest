package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Builds a daily "복습의 길" trail from the words that are due right now, so the
 * quests always track the vocabulary the player is actually learning.
 */
public final class AdaptiveTrailGenerator {
	private AdaptiveTrailGenerator() {
	}

	public static Questline generate(Catalog catalog, LearningService learn, String worldKey, long now) {
		long seed = Objects.hash(worldKey, LocalDate.now().toString());
		Random random = new Random(seed);
		List<Word> picks = new ArrayList<>(learn.dueWords(now, 16));
		picks.removeIf(word -> word.conceptId == null || word.phrase);
		Collections.shuffle(picks, random);

		List<Word> chosen = new ArrayList<>();
		for (Word word : picks) {
			if (chosen.size() >= 4) {
				break;
			}
			if (chosen.stream().noneMatch(existing -> existing.id.equals(word.id))) {
				chosen.add(word);
			}
		}
		if (chosen.size() < 2) {
			for (Word word : catalog.trailWords()) {
				if (chosen.size() >= 2) {
					break;
				}
				if (word.tier <= 1 && !learn.isKnown(word.id)
						&& chosen.stream().noneMatch(existing -> existing.id.equals(word.id))) {
					chosen.add(word);
				}
			}
		}
		if (chosen.isEmpty()) {
			return null;
		}

		List<QuestStep> steps = new ArrayList<>();
		List<String> concepts = new ArrayList<>();
		int index = 0;
		for (Word word : chosen) {
			Goal goal = goalFor(word);
			if (goal == null) {
				continue;
			}
			concepts.add(word.conceptId);
			steps.add(new QuestStep("a" + (++index), List.of(word.conceptId), goal, List.of(),
					null, null, null, null, null));
		}
		if (steps.isEmpty()) {
			return null;
		}
		QuestStep finale = new QuestStep("finale", List.of(),
				new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(new Condition.Time(Condition.Phase.NIGHT)),
				null, null, null, null, null);
		return new Questline("adaptive", "복습의 길", io.github.leks2205.hangulquest.util.Tr.t("Der Wiederholungspfad"), 1, concepts,
				Questline.Requires.none(), steps, finale,
				"밤이 왔어요. 횃불을 들어요. 오늘의 복습을 마쳤어요!",
				"Die Nacht ist da. Ein Licht entzündet: die Wiederholung ist geschafft.",
				"adaptive", 900, 2, true);
	}

	public static Goal goalFor(Word word) {
		if (word.entityId != null) {
			return new Goal.LookAtEntity(word.entityId, 800);
		}
		if (word.biomeId != null) {
			return new Goal.ReachBiome(word.biomeId, 1000);
		}
		String item = word.iconItem;
		if (item == null) {
			return null;
		}
		if (word.category == Category.FOOD) {
			return new Goal.ConsumeItem(item, 1);
		}
		if (word.category == Category.BLOCKS) {
			return new Goal.BreakBlock(item, 2, null);
		}
		return new Goal.AcquireItem(item, 1);
	}
}
