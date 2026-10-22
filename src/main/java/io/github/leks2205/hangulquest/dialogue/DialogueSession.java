package io.github.leks2205.hangulquest.dialogue;

import java.util.HashSet;
import java.util.Set;

/**
 * One run through a dialogue. Moves along the graph, applies the effects of every node the first
 * time it is shown, and reports the answers to the sink. Knows nothing about screens.
 */
public final class DialogueSession {
	/** What a pick did: whether it was right (null when nothing was graded) and whether the talk moved on. */
	public record Pick(Boolean correct, boolean moved) {
	}

	private final Dialogue dialogue;
	private final DialogueSink sink;
	private final Set<String> applied = new HashSet<>();
	private Dialogue.Node node;
	private boolean finished;

	public DialogueSession(Dialogue dialogue, DialogueSink sink) {
		this.dialogue = dialogue;
		this.sink = sink;
		enter(dialogue.start());
	}

	public Dialogue dialogue() {
		return dialogue;
	}

	/** The node on screen; null once the conversation is over. */
	public Dialogue.Node node() {
		return finished ? null : node;
	}

	public boolean finished() {
		return finished;
	}

	/** Continues from a line; on the last line it ends the conversation. */
	public void advance() {
		if (finished || node.kind() != Dialogue.Kind.LINE && node.kind() != Dialogue.Kind.END) {
			return;
		}
		if (node.kind() == Dialogue.Kind.END) {
			finished = true;
		} else {
			enter(node.next());
		}
	}

	/** The player picked choice {@code index}. A wrong pick without a next node stays on the node. */
	public Pick choose(int index) {
		if (finished || node.kind() != Dialogue.Kind.CHOICE || index < 0 || index >= node.choices().size()) {
			return new Pick(null, false);
		}
		Dialogue.Choice choice = node.choices().get(index);
		if (node.grammar() != null && choice.correct() != null) {
			sink.practiceGrammar(node.grammar(), node.choices().size(), choice.correct());
		}
		apply(choice.effects());
		if (choice.next() == null) {
			return new Pick(choice.correct(), false);
		}
		enter(choice.next());
		return new Pick(choice.correct(), true);
	}

	/** The typed answer was submitted and acknowledged; {@code exact} says whether it was spelled right. */
	public void typed(boolean exact) {
		if (finished || node.kind() != Dialogue.Kind.TYPED) {
			return;
		}
		Dialogue.Typed typed = node.typed();
		if (!typed.free() && typed.word() != null) {
			sink.practiceWord(typed.word(), exact);
		}
		enter(node.next());
	}

	private void enter(String id) {
		node = dialogue.nodes().get(id);
		if (applied.add(id)) {
			apply(node.effects());
		}
	}

	private void apply(Iterable<Dialogue.Effect> effects) {
		for (Dialogue.Effect effect : effects) {
			if (effect instanceof Dialogue.StartTrail start) {
				sink.startTrail(start.trail());
			} else if (effect instanceof Dialogue.ExposeWords words) {
				words.words().forEach(sink::exposeWord);
			} else if (effect instanceof Dialogue.ExposeGrammar grammar) {
				sink.exposeGrammar(grammar.point());
			}
		}
	}
}
