package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
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
			valid.add(new Questline(trail.id(), trail.titleKo(), trail.titleDe(), trail.topic(), trail.tier(),
					trail.concepts(), trail.requires(), clueSteps, finale,
					trail.finaleKo(), trail.finaleDe(), trail.badge(), trail.order(),
					trail.koreanLevel(), trail.adaptive()));
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
		if (step.teach() != null && !step.teach().isEmpty()) {
			var first = catalog.concept(step.teach().get(0));
			if (first.isPresent() && !covers(first.get(), step.goal())) {
				problems.add(trail.id() + "/" + step.id() + ": the sentence word '"
						+ first.get().ko + "' (" + first.get().id + ") does not describe the goal "
						+ describe(step.goal()));
			}
		}
		if (step.goal() instanceof Goal.BreakBlock g && bad(g.block())) {
			problems.add(trail.id() + "/" + step.id() + ": bad block " + g.block());
		}
		if (step.goal() instanceof Goal.PlaceBlock g && bad(g.block())) {
			problems.add(trail.id() + "/" + step.id() + ": bad block " + g.block());
		}
		if (step.goal() instanceof Goal.AcquireItem g && bad(g.item())) {
			problems.add(trail.id() + "/" + step.id() + ": bad item " + g.item());
		}
		if (step.goal() instanceof Goal.ConsumeItem g && bad(g.item())) {
			problems.add(trail.id() + "/" + step.id() + ": bad item " + g.item());
		}
		if (step.goal() instanceof Goal.LookAtBlock g && bad(g.block())) {
			problems.add(trail.id() + "/" + step.id() + ": bad block " + g.block());
		}
		if (step.goal() instanceof Goal.LookAtEntity g && bad(g.entity())) {
			problems.add(trail.id() + "/" + step.id() + ": bad entity " + g.entity());
		}
		if (step.goal() instanceof Goal.DefeatEntity g && bad(g.entity())) {
			problems.add(trail.id() + "/" + step.id() + ": bad entity " + g.entity());
		}
		if (step.goal() instanceof Goal.UseBlock g && bad(g.block())) {
			problems.add(trail.id() + "/" + step.id() + ": bad block " + g.block());
		}
		if (step.goal() instanceof Goal.InteractEntity g && bad(g.entity())) {
			problems.add(trail.id() + "/" + step.id() + ": bad entity " + g.entity());
		}
		if (step.goal() instanceof Goal.ReachBiome g) {
			String biome = g.biome();
			if (biome == null || biome.isBlank()) {
				problems.add(trail.id() + "/" + step.id() + ": no biome");
			}
		}
		return problems;
	}

	/** The taught word must actually name what the step asks for. */
	private static boolean covers(io.github.leks2205.hangulquest.data.Concept concept, Goal goal) {
		String item = goalItem(goal);
		if (item != null) {
			return concept.items != null && concept.items.contains(item);
		}
		String entity = goalEntity(goal);
		if (entity != null) {
			return entity.equals(concept.entity);
		}
		if (goal instanceof Goal.ReachBiome biome && biome.biome() != null
				&& !biome.biome().startsWith("#")) {
			return biome.biome().equals(concept.biome);
		}
		return true;
	}

	private static String describe(Goal goal) {
		String item = goalItem(goal);
		if (item != null) {
			return item;
		}
		String entity = goalEntity(goal);
		return entity != null ? entity : goal.getClass().getSimpleName();
	}

	private static String goalItem(Goal goal) {
		return switch (goal) {
			case Goal.BreakBlock g -> g.block();
			case Goal.PlaceBlock g -> g.block();
			case Goal.AcquireItem g -> g.item();
			case Goal.ConsumeItem g -> g.item();
			case Goal.LookAtBlock g -> g.block();
			case Goal.UseBlock g -> g.block();
			default -> null;
		};
	}

	private static String goalEntity(Goal goal) {
		return switch (goal) {
			case Goal.LookAtEntity g -> g.entity();
			case Goal.DefeatEntity g -> g.entity();
			case Goal.InteractEntity g -> g.entity();
			default -> null;
		};
	}

	private static boolean bad(String id) {
		if (id == null || id.isBlank()) {
			return true;
		}
		try {
			net.minecraft.resources.Identifier.parse(id);
			return false;
		} catch (RuntimeException broken) {
			return true;
		}
	}
}
