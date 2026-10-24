package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.ConversationKind;
import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueSet;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks what a villager says when the player sneaks and right-clicks it. Pure: the game describes the villager,
 * its profile and the requests, this class decides. In this order:
 * <ol>
 * <li>its introduction, the first time (one written for its personality, the regional one first);</li>
 * <li>its open request: the thanks when it can be handed in, else a reminder once a day;</li>
 * <li>a friendship milestone it has not had yet (the lowest first), then the offer to talk 반말;</li>
 * <li>a new request, when it has none open, did not offer one today and the player has room;</li>
 * <li>small talk that fits it: an unseen one, the most specific first; when all were seen, the least recent.</li>
 * </ol>
 * Babies only get the conversations written for babies, and no requests or milestones.
 */
public final class ConversationSelector {
	/** What the villager does. */
	public sealed interface Choice permits Talk, Offer, Reminder, HandIn {
	}

	/** A conversation from the dialogue files. */
	public record Talk(Dialogue dialogue) implements Choice {
		public ConversationKind kind() {
			return dialogue.trigger().kind();
		}
	}

	/** The villager asks for something. */
	public record Offer(Request request) implements Choice {
	}

	/** The villager reminds the player of its open request. */
	public record Reminder(Request request) implements Choice {
	}

	/** The open request is done: the villager says thank you and it is closed. */
	public record HandIn(Request request) implements Choice {
	}

	/**
	 * Everything the choice depends on.
	 *
	 * @param profile     the villager's profile (made before choosing; its friendship already counts today's talk)
	 * @param playerLevel the player's learner level (1 to 6)
	 * @param day         the in-game day
	 * @param open        the request this villager has open, or null
	 * @param openReady   true when that request can be handed in now
	 * @param canAccept   true when the player may take on one more request
	 * @param offer       the request the villager would offer now (see {@link Requests#choose}), or null
	 */
	public record Situation(VillagerProfile profile, String entityType, String profession, boolean baby,
							int playerLevel, long day, Request open, boolean openReady, boolean canAccept,
							Request offer) {
	}

	private ConversationSelector() {
	}

	public static Optional<Choice> select(DialogueSet dialogues, Situation s) {
		VillagerProfile profile = s.profile();
		List<Dialogue> fitting = dialogues.all().stream().filter(dialogue -> fits(dialogue, s)).toList();

		List<Dialogue> intros = of(fitting, ConversationKind.INTRO);
		if (!intros.isEmpty() && intros.stream().noneMatch(intro -> profile.hasSeen(intro.id()))
				&& !introSeen(dialogues, profile)) {
			return Optional.of(new Talk(mostSpecific(intros, profile)));
		}
		if (s.open() != null && !s.baby()) {
			if (s.openReady()) {
				return Optional.of(new HandIn(s.open()));
			}
			if (profile.reminderDay < s.day()) {
				return Optional.of(new Reminder(s.open()));
			}
		}
		if (!s.baby()) {
			Optional<Dialogue> milestone = of(fitting, ConversationKind.MILESTONE).stream()
					.filter(dialogue -> !profile.hasSeen(dialogue.id()))
					.min(Comparator.comparingInt((Dialogue dialogue) -> dialogue.trigger().friendship())
							.thenComparing(Dialogue::id));
			if (milestone.isPresent()) {
				return Optional.of(new Talk(milestone.get()));
			}
			if (mayOfferBanmal(profile, s.playerLevel())) {
				Optional<Dialogue> offer = of(fitting, ConversationKind.BANMAL_OFFER).stream()
						.filter(dialogue -> !profile.hasSeen(dialogue.id())).min(Comparator.comparing(Dialogue::id));
				if (offer.isPresent()) {
					return Optional.of(new Talk(offer.get()));
				}
			}
			if (s.open() == null && profile.requestDay < s.day() && s.canAccept() && s.offer() != null) {
				return Optional.of(new Offer(s.offer()));
			}
		}
		List<Dialogue> talks = of(fitting, ConversationKind.SMALLTALK);
		if (talks.isEmpty()) {
			return Optional.empty();
		}
		List<Dialogue> unseen = talks.stream().filter(dialogue -> !profile.hasSeen(dialogue.id())).toList();
		if (!unseen.isEmpty()) {
			return Optional.of(new Talk(mostSpecific(unseen, profile)));
		}
		return talks.stream().min(Comparator.comparingInt((Dialogue dialogue) -> profile.seenRank(dialogue.id()))
				.thenComparing(Dialogue::id)).map(Talk::new);
	}

	/** True once the villager has had any introduction (also one that no longer fits, a migrated one). */
	private static boolean introSeen(DialogueSet dialogues, VillagerProfile profile) {
		for (String id : profile.seen) {
			Dialogue dialogue = dialogues.get(id);
			if (dialogue != null && dialogue.trigger().kind() == ConversationKind.INTRO) {
				return true;
			}
		}
		return false;
	}

	/** A close friend of a personality that switches, a player of level 4 or more, and not switched yet. */
	static boolean mayOfferBanmal(VillagerProfile profile, int playerLevel) {
		return profile.personality.mayOfferBanmal() && !profile.banmal && !profile.baby
				&& profile.friendship >= Personality.BANMAL_FRIENDSHIP && playerLevel >= Personality.BANMAL_PLAYER_LEVEL;
	}

	private static List<Dialogue> of(List<Dialogue> dialogues, ConversationKind kind) {
		return dialogues.stream().filter(dialogue -> dialogue.trigger().kind() == kind).toList();
	}

	/** Whether a conversation may be said by this villager to this player now. */
	static boolean fits(Dialogue dialogue, Situation s) {
		Dialogue.Trigger trigger = dialogue.trigger();
		VillagerProfile profile = s.profile();
		if (!trigger.entity().equals(s.entityType())) {
			return false;
		}
		// A baby only says what was written for babies; an adult never does.
		if (s.baby() != Boolean.TRUE.equals(trigger.baby())) {
			return false;
		}
		return trigger.allows(s.profession()) && trigger.allows(profile.personality) && trigger.allows(profile.region)
				&& dialogue.level() <= Math.max(1, s.playerLevel()) && trigger.friendship() <= profile.friendship;
	}

	/** How closely a conversation is written for a villager: its personality counts most, then region and profession. */
	static int specificity(Dialogue.Trigger trigger) {
		return (trigger.personalities().isEmpty() ? 0 : 4) + (trigger.regions().isEmpty() ? 0 : 2)
				+ (trigger.namesProfession() ? 2 : 0);
	}

	/** The most specific of the conversations; equally specific ones are shared out by the villager's identity. */
	private static Dialogue mostSpecific(List<Dialogue> dialogues, VillagerProfile profile) {
		int best = dialogues.stream().mapToInt(dialogue -> specificity(dialogue.trigger())).max().orElse(0);
		List<Dialogue> tied = dialogues.stream().filter(dialogue -> specificity(dialogue.trigger()) == best)
				.sorted(Comparator.comparing(Dialogue::id)).toList();
		return tied.get(Math.floorMod(VillagerProfiles.mix(java.util.UUID.fromString(profile.uuid), 31), tied.size()));
	}

	/**
	 * What the conversation changes in the profile when it opens: it counts as seen, an offer or a reminder uses up
	 * the day, a handed-in request adds friendship.
	 */
	public static void opened(VillagerProfile profile, Choice choice, long day) {
		switch (choice) {
			case Talk talk -> profile.markSeen(talk.dialogue().id());
			case Offer offer -> profile.requestDay = day;
			case Reminder reminder -> profile.reminderDay = day;
			case HandIn handIn -> {
				profile.requestFinished();
				profile.requestDay = day;
			}
		}
	}
}
