package io.github.leks2205.hangulquest.dialogue;

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

	/** When a dialogue opens: the entity type, and optionally the villager profession and the baby flag. */
	public record Trigger(String entity, String profession, Boolean baby) {
	}

	public enum Kind {
		LINE, CHOICE, TYPED, END
	}

	/**
	 * @param speechLevel the level of {@code ko}; the speaker's own level unless the data says otherwise
	 * @param grammar     grammar point (grammar.json id) the choices of this node practise, or null
	 */
	public record Node(String id, Speaker speaker, SpeechLevel speechLevel, String ko, String next,
					   List<Choice> choices, Typed typed, List<Effect> effects, boolean end, String grammar) {
		public Node {
			choices = choices == null ? List.of() : List.copyOf(choices);
			effects = effects == null ? List.of() : List.copyOf(effects);
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
	}

	/**
	 * What the player can say. {@code correct} is null when nothing is graded; a wrong choice without a
	 * {@code next} keeps the player on the node.
	 */
	public record Choice(String ko, SpeechLevel speechLevel, String next, Boolean correct, List<Effect> effects) {
		public Choice {
			effects = effects == null ? List.of() : List.copyOf(effects);
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

	public sealed interface Effect permits StartTrail, ExposeWords, ExposeGrammar {
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

	/** The gloss of choice {@code index} (0-based) of a node: {@code c1} is the first. */
	public String choiceKey(String nodeId, int index) {
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
