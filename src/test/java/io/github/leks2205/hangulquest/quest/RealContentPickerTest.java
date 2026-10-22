package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.PickerFixtures.Recorder;
import io.github.leks2205.hangulquest.story.ChapterSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.leks2205.hangulquest.quest.PickerFixtures.LOG;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.obs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The picker on the shipped trails: what a new player gets, and what each place offers. */
class RealContentPickerTest {
	@TempDir
	Path dir;

	private QuestEngine engine(Recorder unused) throws IOException {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		ProgressStore store = new ProgressStore(dir, LOG);
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine engine = new QuestEngine(QuestLoader.loadAll(), catalog, learn, LOG);
		engine.setAutoPick(() -> true);
		try (InputStream in = RealContentPickerTest.class.getResourceAsStream("/assets/hangulquest/data/chapters.json")) {
			assertNotNull(in);
			Set<String> ids = engine.trails().stream().map(Questline::id).collect(Collectors.toSet());
			ChapterSet chapters = ChapterSet.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), ids, LOG);
			engine.setChapters(trail -> chapters.ofTrail(trail).map(ChapterSet.Chapter::id).orElse(null),
					chapters.all().stream().map(ChapterSet.Chapter::id).toList());
		}
		return engine;
	}

	@Test
	void aNewPlayerGetsThreeBeginnerTrailsThatNeedNothingRare() throws IOException {
		Recorder hud = new Recorder();
		QuestEngine engine = engine(hud);

		engine.autoStart(obs(10_000), hud);

		List<String> picked = engine.trackedViews().stream().map(QuestEngine.TrackView::id).toList();
		assertEquals(3, picked.size());
		assertEquals(List.of(picked), hud.autoTracked);
		for (String id : picked) {
			Questline trail = engine.byId(id);
			assertEquals(1, trail.koreanLevel(), id);
			assertEquals("minecraft:overworld", trail.requires().dimension(), id);
			Reach.Cost cost = Reach.of(trail);
			assertTrue(!cost.outOfReach() && cost.structure() == 0 && cost.resources() == 0,
					id + " needs nothing rare near the spawn: " + cost);
			assertTrue(trail.requires().minWordsKnown() <= NextQuestPolicy.Weights.WORD_SLACK, id);
			assertTrue(trail.requires().completed().isEmpty(), id);
		}
		assertEquals(List.of("holzpfad", "steinpfad", "essenspfad"), picked,
				"wood, stone and food: the start of the gameplay loop");
		assertTrue(hud.notices.get(0).startsWith("start:"), hud.notices.toString());
	}

	@Test
	void everyContextOfTheTableOffersATrailOnTheShippedContent() throws IOException {
		QuestEngine engine = engine(new Recorder());
		Map<Situation.Tag, String> best = new java.util.EnumMap<>(Situation.Tag.class);
		for (Situation.Tag tag : EnumSet.allOf(Situation.Tag.class)) {
			Questline found = null;
			int bestFit = 0;
			for (Questline trail : engine.trails()) {
				if (trail.adaptive() || Reach.of(trail).outOfReach() || trail.koreanLevel() > 2) {
					continue;
				}
				int fit = engine.traits(trail).fit(EnumSet.of(tag));
				if (fit > bestFit) {
					bestFit = fit;
					found = trail;
				}
			}
			best.put(tag, found == null ? "-" : found.id());
		}
		System.out.println("situational examples (best fit among level 1-2 trails): " + best);
		for (Situation.Tag tag : EnumSet.of(Situation.Tag.CAVE, Situation.Tag.SNOW, Situation.Tag.MOUNTAIN, Situation.Tag.SEA,
				Situation.Tag.VILLAGE, Situation.Tag.NIGHT)) {
			assertNotNull(best.get(tag));
			assertTrue(!best.get(tag).equals("-"), tag + " has no trail on the shipped content");
		}
	}
}
