package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

class ProbeTest {
	@TempDir
	Path scratch;

	private static QuestStep step(String id, List<String> teach, Goal goal, List<Condition> conditions) {
		return new QuestStep(id, teach, goal, conditions, null, null, null, null, null, null, null);
	}

	@Test
	void probe() {
		Catalog catalog = KoreanFixtures.catalog();
		var log = LoggerFactory.getLogger("probe");
		ProgressStore store = new ProgressStore(scratch.resolve("progress"), log);
		LearningService learn = new LearningService(store.data(), store, catalog);
		learn.grammar().unlockAll();
		List<QuestStep> steps = new java.util.ArrayList<>();
		java.util.Map<String, io.github.leks2205.hangulquest.data.Word> byStep = new java.util.HashMap<>();
		int n = 0;
		steps.add(step("g1", List.of("gold_block"), new Goal.Craft("minecraft:gold_block", 1, null, List.of()), List.of(new Condition.Holding("minecraft:gold_ingot"), new Condition.Time(Condition.Phase.NIGHT))));
		steps.add(step("g2", List.of("gen_mossy_cobblestone"), new Goal.Craft("minecraft:mossy_cobblestone", 1, null, List.of()), List.of(new Condition.Holding("minecraft:vine"), new Condition.Time(Condition.Phase.NIGHT))));
		steps.add(step("g3", List.of("gen_mossy_cobblestone"), new Goal.Craft("minecraft:mossy_cobblestone", 1, null, List.of()), List.of(new Condition.Holding("minecraft:vine"))));
		for (var concept : catalog.concepts()) {
			if (concept.items == null || concept.items.size() != 1 || !concept.ko.contains(" ")) {
				continue;
			}
			steps.add(step("s" + (++n), List.of(concept.id), new Goal.PlaceBlock(concept.items.get(0), 1), List.of()));
			steps.add(step("t" + n, List.of("stone", concept.id), new Goal.PlaceRel("minecraft:torch", io.github.leks2205.hangulquest.quest.spatial.Relation.NEXT_TO, concept.items.get(0), 1), List.of()));
			steps.add(step("u" + n, List.of("torch", concept.id, "make"), new Goal.Craft(concept.items.get(0), 1, null, List.of()), List.of()));
			steps.add(step("v" + n, List.of("make", concept.id), new Goal.Smelt("minecraft:cobblestone", concept.items.get(0), 1), List.of(new Condition.Time(Condition.Phase.NIGHT))));
		}
		Questline trail = new Questline("probe", "시험", "Probe", 1, List.of(), Questline.Requires.none(), steps,
				step("finale", List.of("stone"), new Goal.BreakBlock("minecraft:stone", 1, null), List.of()), "끝", "Ende", null, 1, 6, false);
		QuestEngine engine = new QuestEngine(List.of(trail), catalog, learn, log);
		var fb = new SentenceDumpTest.Feedback();
		var start = new io.github.leks2205.hangulquest.sense.Observation();
		start.now = 1000L;
		start.dimension = "minecraft:overworld";
		engine.startTrail("probe", start, fb);
		System.out.println("PROBE level " + trail.koreanLevel());
		for (QuestStep s : trail.allSteps()) {
			String c = engine.clueKo(s), h = engine.hintKo(s);
			if (s.id().startsWith("g") || c.equals(h) || h.endsWith("야 해요.") || h.endsWith("세요.")) {
				System.out.println("PROBE " + s.id() + " " + s.teach() + " clue: " + c + " | hint: " + h);
			}
		}
	}
}
