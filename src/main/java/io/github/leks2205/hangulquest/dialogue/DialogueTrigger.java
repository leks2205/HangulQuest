package io.github.leks2205.hangulquest.dialogue;

import java.util.Comparator;
import java.util.Optional;

/**
 * Decides whether a right-click on an entity opens a dialogue, and which. Pure: the game code only
 * describes what happened. The dialogue is opened by sneaking and right-clicking with the main hand;
 * a plain right-click is left to the game (trading).
 */
public final class DialogueTrigger {
	/** What was clicked. {@code profession} is a registry id such as {@code minecraft:farmer}, or null. */
	public record Target(String entityType, String profession, boolean baby) {
	}

	/** The circumstances of the click. */
	public record Click(boolean clientSide, boolean mainHand, boolean sneaking, boolean spectator,
						boolean screenOpen) {
	}

	private DialogueTrigger() {
	}

	/** True when this click should be taken over (and not passed on to the game). */
	public static boolean wanted(Click click) {
		return click.clientSide() && click.mainHand() && click.sneaking() && !click.spectator() && !click.screenOpen();
	}

	/** The most specific dialogue for the target: a profession counts more than the baby flag; ties go by id. */
	public static Optional<Dialogue> select(DialogueSet set, Click click, Target target) {
		if (set == null || target == null || !wanted(click)) {
			return Optional.empty();
		}
		return set.all().stream()
				.filter(dialogue -> matches(dialogue.trigger(), target))
				.max(Comparator.comparingInt((Dialogue dialogue) -> specificity(dialogue.trigger()))
						.thenComparing(Dialogue::id, Comparator.reverseOrder()));
	}

	static boolean matches(Dialogue.Trigger trigger, Target target) {
		if (!trigger.entity().equals(target.entityType())) {
			return false;
		}
		if (trigger.profession() != null && !trigger.profession().equals(target.profession())) {
			return false;
		}
		return trigger.baby() == null || trigger.baby() == target.baby();
	}

	private static int specificity(Dialogue.Trigger trigger) {
		return (trigger.profession() != null ? 2 : 0) + (trigger.baby() != null ? 1 : 0);
	}
}
