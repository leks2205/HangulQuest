package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;

/**
 * A namer that knows the steps before the one being spoken: what they placed (for "아까 놓은 횃불") and
 * which structures they build (so a step does not say "build it" for a pillar that already stands).
 * Everything else is the wrapped namer's.
 */
public final class StepNamer implements SentenceBuilder.Namer {
	private final SentenceBuilder.Namer base;
	private final QuestStep step;

	private StepNamer(SentenceBuilder.Namer base, QuestStep step) {
		this.base = base;
		this.step = step;
	}

	/** {@code base} itself for a step without earlier steps. */
	public static SentenceBuilder.Namer of(SentenceBuilder.Namer base, QuestStep step) {
		return step == null || step.earlier().isEmpty() ? base : new StepNamer(base, step);
	}

	@Override
	public String biome(String id) {
		return base.biome(id);
	}

	@Override
	public String entity(String id) {
		return base.entity(id);
	}

	@Override
	public String block(String id) {
		return base.block(id);
	}

	@Override
	public String item(String id) {
		return base.item(id);
	}

	@Override
	public Placed placedBy(String stepId) {
		for (QuestStep earlier : step.earlier()) {
			if (earlier.id().equals(stepId)) {
				String block = SpatialRules.placedBlock(earlier.goal());
				return block == null ? null : new Placed(block,
						GoalTypes.of(earlier.goal()).verb(earlier.goal(), VerbTable.shared()));
			}
		}
		return null;
	}

	@Override
	public boolean builtEarlier(String key) {
		for (QuestStep earlier : step.earlier()) {
			if (key.equals(SpatialSentences.builtKey(earlier.goal()))) {
				return true;
			}
		}
		return false;
	}
}
