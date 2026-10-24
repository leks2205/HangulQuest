package io.github.leks2205.hangulquest.dialogue;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether a right-click on an entity opens a dialogue, and which. Pure: the game code only
 * describes what happened. The dialogue is opened by sneaking and right-clicking with the main hand;
 * a plain right-click is left to the game (trading).
 */
public final class DialogueTrigger {
	/**
	 * What was clicked. {@code profession} is a registry id such as {@code minecraft:farmer}, or null.
	 * {@code seed} (the villager's identity) shares equal conversations out between villagers.
	 */
	public record Target(String entityType, String profession, boolean baby, int seed) {
		public Target(String entityType, String profession, boolean baby) {
			this(entityType, profession, baby, 0);
		}
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

	/**
	 * The most specific dialogue for the target: a profession counts more than the baby flag. Equally specific
	 * ones are ordered by id and the target's seed picks one, so a villager keeps its conversation.
	 */
	public static Optional<Dialogue> select(DialogueSet set, Click click, Target target) {
		if (set == null || target == null || !wanted(click)) {
			return Optional.empty();
		}
		List<Dialogue> matching = set.all().stream().filter(dialogue -> matches(dialogue.trigger(), target)).toList();
		int best = matching.stream().mapToInt(dialogue -> specificity(dialogue.trigger())).max().orElse(-1);
		List<Dialogue> tied = matching.stream().filter(dialogue -> specificity(dialogue.trigger()) == best)
				.sorted(Comparator.comparing(Dialogue::id)).toList();
		return tied.isEmpty() ? Optional.empty() : Optional.of(tied.get(Math.floorMod(target.seed(), tied.size())));
	}

	/** A conversation written for personalities, regions or a kind is chosen by the villager selector, never here. */
	static boolean matches(Dialogue.Trigger trigger, Target target) {
		if (trigger.villagerOnly() || !trigger.entity().equals(target.entityType())) {
			return false;
		}
		if (!trigger.allows(target.profession())) {
			return false;
		}
		return trigger.baby() == null || trigger.baby() == target.baby();
	}

	private static int specificity(Dialogue.Trigger trigger) {
		return (trigger.namesProfession() ? 2 : 0) + (trigger.baby() != null ? 1 : 0);
	}
}
