package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.grammar.Constructions;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
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

	/** Goals whose target the player can recognise by looking at it or handling it: the reverse flag fits these. */
	public static final Set<String> REVERSE_GOALS = Set.of(
			"LOOK_AT_BLOCK", "LOOK_AT_ENTITY", "BREAK_BLOCK", "ACQUIRE_ITEM", "PLACE_REL");

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
			problems.addAll(constructionProblems(trail));
			valid.add(trail.withSteps(clueSteps, finale).withGrammar(knownGrammar(trail, problems, log)));
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

	/**
	 * The authored Korean of the trail against the level and role rules of the upper constructions. These are
	 * reported, but the step stays: a player's own trail is told, not cut.
	 */
	private static List<String> constructionProblems(Questline trail) {
		List<String> problems = new ArrayList<>();
		int level = trail.koreanLevel();
		for (QuestStep step : trail.allSteps()) {
			String where = trail.id() + "/" + step.id();
			problems.addAll(Constructions.problems(where + " clue", step.clueKo(), Constructions.Role.CLUE, level));
			problems.addAll(Constructions.problems(where + " hint", step.hintKo(), Constructions.Role.HINT, level));
		}
		problems.addAll(Constructions.problems(trail.id() + "/finale text", trail.finaleKo(),
				Constructions.Role.FINALE, level));
		return problems;
	}

	/**
	 * The trail's declared grammar ids that grammar.json knows, a ladder alias counting as its construction.
	 * An unknown id is a problem and is dropped; the trail still loads.
	 */
	private static List<String> knownGrammar(Questline trail, List<String> problems, Logger log) {
		List<String> known = new ArrayList<>();
		GrammarBook book = GrammarBook.shared();
		for (String id : trail.grammar()) {
			if (book.has(Ladder.shared().resolve(id))) {
				known.add(id);
			} else {
				problems.add(trail.id() + ": unknown grammar id " + id);
				log.warn("questline {}: unknown grammar id {}", trail.id(), id);
			}
		}
		return known;
	}

	/** Where the step happens: its own IN_DIMENSION, else the dimension the trail asks for; the overworld if neither. */
	private static String dimensionOf(Questline trail, QuestStep step) {
		for (Condition condition : step.conditions()) {
			if (condition instanceof Condition.InDimension in) {
				return in.dimension();
			}
		}
		String asked = trail.requires() == null ? null : trail.requires().dimension();
		return asked == null || asked.isBlank() ? "minecraft:overworld" : asked;
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
		if (step.reverse() && !REVERSE_GOALS.contains(GoalTypes.of(step.goal()).id())) {
			problems.add(trail.id() + "/" + step.id() + ": reverse does not fit a "
					+ GoalTypes.of(step.goal()).id() + " goal");
		}
		String dimension = dimensionOf(trail, step);
		if (!"minecraft:overworld".equals(dimension)) {
			for (Condition condition : step.conditions()) {
				if (ConditionTypes.of(condition).timeOfDay()) {
					problems.add(trail.id() + "/" + step.id() + ": a time of day in " + dimension
							+ ", which has no day and night");
				}
			}
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
