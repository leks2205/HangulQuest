package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.sense.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small trails, observations and a recording feedback for the quest picker tests. */
final class PickerFixtures {
	static final Logger LOG = LoggerFactory.getLogger("quest-picker-test");

	private PickerFixtures() {
	}

	static Catalog catalog() {
		return Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"), concept("torch", "횃불", "Fackel")));
	}

	private static Concept concept(String id, String ko, String de) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = de;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:stone");
		concept.descKo = "땅에서 나와요.";
		return concept;
	}

	/** A learner on a fresh save in {@code dir}. */
	static LearningService learner(Path dir) {
		ProgressStore store = new ProgressStore(dir, LOG);
		return new LearningService(store.data(), store, catalog());
	}

	/** One step breaking {@code block}, then a torch finale: finished by {@link #finish}. */
	static Questline trail(String id, int order, int level, String topic, List<String> parents) {
		return custom(id, order, level, topic, 0, parents, new Goal.BreakBlock("minecraft:" + id, 1, null), List.of());
	}

	static Questline trail(String id, int order, String topic) {
		return trail(id, order, 1, topic, List.of());
	}

	static Questline custom(String id, int order, int level, String topic, int minWords, List<String> parents,
							Goal goal, List<Condition> conditions) {
		QuestStep step = new QuestStep("s1", List.of("stone"), goal, conditions, null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of("torch"),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(), null, null, null, null, null);
		return new Questline(id, id, id + "-de", topic, 1, List.of("stone"),
				new Questline.Requires(minWords, null, parents), List.of(step), finale,
				"끝", "Ende", id, order, level, false);
	}

	static Observation obs(long now) {
		Observation obs = new Observation();
		obs.now = now;
		obs.clockTime = 6000L;
		obs.dimension = "minecraft:overworld";
		return obs;
	}

	static Observation breaking(long now, String block) {
		Observation obs = obs(now);
		obs.brokenBlocks.add(block);
		return obs;
	}

	static Observation placing(long now) {
		Observation obs = obs(now);
		obs.placedBlocks.add("minecraft:torch");
		return obs;
	}

	/** Finishes the one-block trail {@code id}: the block, then the torch. */
	static void finish(QuestEngine engine, String id, long now, QuestEngine.Feedback feedback) {
		engine.tick(breaking(now, "minecraft:" + id), feedback);
		engine.tick(placing(now + 50), feedback);
	}

	/** Records what the engine tells the HUD. */
	static final class Recorder implements QuestEngine.Feedback {
		final List<String> banners = new ArrayList<>();
		final List<List<String>> autoTracked = new ArrayList<>();
		final List<AutoReason> reasons = new ArrayList<>();
		final List<String> situational = new ArrayList<>();
		/** key and Korean title of every notice window. */
		final List<String> notices = new ArrayList<>();
		final List<List<Questline>> choices = new ArrayList<>();

		@Override
		public void banner(String ko, String de, boolean success) {
			banners.add(ko);
		}

		@Override
		public void wordCard(io.github.leks2205.hangulquest.data.Word word) {
		}

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void notice(String key, String titleKo, String titleText, String bodyKo, String bodyText) {
			notices.add(key + "|" + titleKo);
		}

		@Override
		public void chooseNext(List<Questline> options) {
			choices.add(options);
		}

		@Override
		public void autoTracked(List<Questline> picked, AutoReason reason) {
			autoTracked.add(picked.stream().map(Questline::id).toList());
			reasons.add(reason);
		}

		@Override
		public void situationalChanged(Questline trail) {
			situational.add(trail.id());
		}
	}
}
