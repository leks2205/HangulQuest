package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.util.TextClean;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates loaded trails; bad steps are dropped instead of crashing the client. */
public final class QuestValidator {
	public record Result(List<Questline> trails, List<String> problems) {
	}

	private QuestValidator() {
	}

	public static Result validate(List<Questline> trails, Catalog catalog, Logger log) {
		List<Questline> valid = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (Questline trail : trails) {
			List<QuestStep> steps = new ArrayList<>();
			Set<String> ids = new HashSet<>();
			for (QuestStep step : trail.allSteps()) {
				List<String> stepProblems = validateStep(trail, step, catalog);
				if (!stepProblems.isEmpty()) {
					problems.addAll(stepProblems);
					if (step.id().equals("finale")) {
						continue;
					}
					log.warn("skipping step {} of {}: {}", step.id(), trail.id(), stepProblems.get(0));
					continue;
				}
				if (!ids.add(step.id())) {
					problems.add(trail.id() + ": duplicate step id " + step.id());
					continue;
				}
				steps.add(step);
			}
			if (steps.isEmpty()) {
				problems.add(trail.id() + ": no valid steps");
				log.warn("skipping questline {}: no valid steps", trail.id());
				continue;
			}
			QuestStep finale = null;
			List<QuestStep> clueSteps = new ArrayList<>();
			for (QuestStep step : steps) {
				if (step.id().equals("finale")) {
					finale = step;
				} else {
					clueSteps.add(step);
				}
			}
			if (finale == null) {
				problems.add(trail.id() + ": missing finale");
				log.warn("skipping questline {}: missing finale", trail.id());
				continue;
			}
			valid.add(trail.withSteps(clueSteps, finale));
		}
		// Trails that require each other can never unlock; drop them instead of looping over them.
		TrailGraph graph = TrailGraph.of(valid);
		if (graph.hasCycle()) {
			for (String id : graph.cyclic()) {
				problems.add(id + ": prerequisite cycle");
			}
			valid.removeIf(trail -> graph.cyclic().contains(trail.id()));
		}
		for (String problem : problems) {
			log.warn("questline problem: {}", problem);
		}
		return new Result(valid, problems);
	}

	private static List<String> validateStep(Questline trail, QuestStep step, Catalog catalog) {
		List<String> problems = new ArrayList<>();
		if (step.goal() == null) {
			problems.add(trail.id() + "/" + step.id() + ": no goal");
			return problems;
		}
		for (String concept : step.teach()) {
			if (catalog.concept(concept).isEmpty()) {
				problems.add(trail.id() + "/" + step.id() + ": unknown concept " + concept);
			}
		}
		if (step.clueKo() != null && !TextClean.isHangulOnly(step.clueKo())) {
			problems.add(trail.id() + "/" + step.id() + ": clueKo is not Hangul-only");
		}
		if (step.hintKo() != null && !TextClean.isHangulOnly(step.hintKo())) {
			problems.add(trail.id() + "/" + step.id() + ": hintKo is not Hangul-only");
		}
		ValidationContext context = new ValidationContext(trail.id() + "/" + step.id(), step, catalog,
				problems, trail.koreanLevel());
		GoalTypes.of(step.goal()).validate(step.goal(), context);
		for (Condition condition : step.conditions()) {
			ConditionTypes.of(condition).validate(condition, context);
		}
		return problems;
	}
}
