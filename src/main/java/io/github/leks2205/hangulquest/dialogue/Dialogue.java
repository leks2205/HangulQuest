package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.Region;

import java.util.List;
import java.util.Map;

/**
 * One conversation: a graph of nodes read from {@code assets/hangulquest/dialogue/<id>.json}.
 * A node is one line of an NPC, followed by exactly one of: the next node, choices the player picks
 * from, a typed answer, or the end. The English and German gloss of every line lives in the language
 * files under {@code hangulquest.dialogue.<id>.<node>} (see the key helpers).
 */
public record Dialogue(String id, int level, Trigger trigger, String start, Map<String, Node> nodes) {
	public static final String KEY_PREFIX = "hangulquest.dialogue.";

	public Dialogue {
		nodes = Map.copyOf(nodes);
	}

	/**
	 * When a dialogue opens: the entity type, and optionally the villager profession (one, or any of
	 * {@code professions}) and the baby flag. A villager conversation may also name the personalities and regions
	 * it is written for (empty: any), its {@code kind} and the friendship it needs.
	 */
	public record Trigger(String entity, String profession, Boolean baby, List<String> professions,
						  List<Personality> personalities, List<Region> regions, ConversationKind kind,
						  int friendship) {
		public Trigger {
			professions = professions == null ? List.of() : List.copyOf(professions);
			personalities = personalities == null ? List.of() : List.copyOf(personalities);
			regions = regions == null ? List.of() : List.copyOf(regions);
			kind = kind == null ? ConversationKind.SMALLTALK : kind;
		}

		public Trigger(String entity, String profession, Boolean baby, List<String> professions) {
			this(entity, profession, baby, professions, List.of(), List.of(), ConversationKind.SMALLTALK, 0);
		}

		public Trigger(String entity, String profession, Boolean baby) {
			this(entity, profession, baby, List.of());
		}

		/** True when the trigger names a profession at all. */
		public boolean namesProfession() {
			return profession != null || !professions.isEmpty();
		}

		public boolean allows(String villagerProfession) {
			return !namesProfession() || villagerProfession != null
					&& (villagerProfession.equals(profession) || professions.contains(villagerProfession));
		}

		/** True for a conversation written for villager personalities, regions or a kind other than small talk. */
		public boolean villagerOnly() {
			return !personalities.isEmpty() || !regions.isEmpty() || kind != ConversationKind.SMALLTALK || friendship > 0;
		}

		public boolean allows(Personality personality) {
			return personalities.isEmpty() || personalities.contains(personality);
		}

		/** A region-free conversation fits every villager; a regional one only villagers of a known, listed region. */
		public boolean allows(Region region) {
			return regions.isEmpty() || region != null && regions.contains(region);
		}
	}

	public enum Kind {
		LINE, CHOICE, TYPED, END
	}

	/**
	 * @param speechLevel the level of {@code ko}; the speaker's own level unless the data says otherwise
	 * @param grammar     grammar point (grammar.json id) the choices of this node practise, or null
	 * @param koBanmal    the same line in 반말 for a villager who switched to 반말 with the player, or null
	 * @param selfIntro   the villager says its name here ("저는 민준이에요."); {@code ko} is empty and the game
	 *                    builds the line from the villager's name and speech level
	 */
	public record Node(String id, Speaker speaker, SpeechLevel speechLevel, String ko, String next,
					   List<Choice> choices, Typed typed, List<Effect> effects, boolean end, String grammar,
					   String koBanmal, boolean selfIntro) {
		public Node {
			choices = choices == null ? List.of() : List.copyOf(choices);
			effects = effects == null ? List.of() : List.copyOf(effects);
			ko = ko == null ? "" : ko;
		}

		public Node(String id, Speaker speaker, SpeechLevel speechLevel, String ko, String next,
					List<Choice> choices, Typed typed, List<Effect> effects, boolean end, String grammar) {
			this(id, speaker, speechLevel, ko, next, choices, typed, effects, end, grammar, null, false);
		}

		public Kind kind() {
			if (!choices.isEmpty()) {
				return Kind.CHOICE;
			}
			if (typed != null) {
				return Kind.TYPED;
			}
			return end ? Kind.END : Kind.LINE;
		}

		/** The text to show and speak: the 반말 version for a villager who talks 반말 with the player, when there is one. */
		public String text(boolean banmal) {
			return banmal && koBanmal != null && !koBanmal.isBlank() ? koBanmal : ko;
		}
	}

	/**
	 * What the player can say. {@code correct} is null when nothing is graded; a wrong choice without a
	 * {@code next} keeps the player on the node.
	 */
	public record Choice(String ko, SpeechLevel speechLevel, String next, Boolean correct, List<Effect> effects,
						 String glossKey) {
		public Choice {
			effects = effects == null ? List.of() : List.copyOf(effects);
		}

		public Choice(String ko, SpeechLevel speechLevel, String next, Boolean correct, List<Effect> effects) {
			this(ko, speechLevel, next, correct, effects, null);
		}

		public boolean wrong() {
			return Boolean.FALSE.equals(correct);
		}
	}

	/**
	 * A typed answer. {@code free} accepts anything (the player's name) and grades nothing; otherwise one
	 * of {@code expected} is right. {@code word} is the catalog word practised, or null.
	 */
	public record Typed(List<String> expected, String word, boolean free) {
		public Typed {
			expected = expected == null ? List.of() : List.copyOf(expected);
		}
	}

	public sealed interface Effect permits StartTrail, ExposeWords, ExposeGrammar, AcceptBanmal, AcceptRequest,
			DeclineRequest {
	}

	/** The player agreed to talk 반말 with this villager (only in a {@code banmal_offer} conversation). */
	public record AcceptBanmal() implements Effect {
	}

	/** The player took on the request of a request conversation; made by the game, never read from a file. */
	public record AcceptRequest(String request) implements Effect {
	}

	/** The player said no to the request; made by the game, never read from a file. */
	public record DeclineRequest(String request) implements Effect {
	}

	public record StartTrail(String trail) implements Effect {
	}

	public record ExposeWords(List<String> words) implements Effect {
		public ExposeWords {
			words = List.copyOf(words);
		}
	}

	public record ExposeGrammar(String point) implements Effect {
	}

	// ---------------------------------------------------------------- language keys

	/** The gloss of a node's line. */
	public String lineKey(String nodeId) {
		return KEY_PREFIX + id + "." + nodeId;
	}

	/** The gloss of choice {@code index} (0-based) of a node: {@code c1} is the first, unless the choice names its own. */
	public String choiceKey(String nodeId, int index) {
		Node node = nodes.get(nodeId);
		if (node != null && index >= 0 && index < node.choices().size() && node.choices().get(index).glossKey() != null) {
			return node.choices().get(index).glossKey();
		}
		return lineKey(nodeId) + ".c" + (index + 1);
	}

	/** What to tell the player after this choice (right or wrong); optional. */
	public String choiceNoteKey(String nodeId, int index) {
		return choiceKey(nodeId, index) + ".note";
	}

	/** The instruction above a typed answer. */
	public String promptKey(String nodeId) {
		return lineKey(nodeId) + ".prompt";
	}
}
