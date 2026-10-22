package io.github.leks2205.hangulquest.dialogue;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The dialogues that passed validation, by id. */
public final class DialogueSet {
	public static final DialogueSet EMPTY = new DialogueSet(Map.of());

	private final Map<String, Dialogue> byId;

	private DialogueSet(Map<String, Dialogue> byId) {
		this.byId = byId;
	}

	public static DialogueSet of(Collection<Dialogue> dialogues) {
		Map<String, Dialogue> sorted = new TreeMap<>();
		dialogues.forEach(dialogue -> sorted.put(dialogue.id(), dialogue));
		return new DialogueSet(Map.copyOf(sorted));
	}

	/**
	 * Parses and validates the files (file id to bytes). A dialogue with problems is left out and logged;
	 * the others still load.
	 */
	public static DialogueSet load(Map<String, byte[]> files, DialogueValidator.Env env, Logger log) {
		List<Dialogue> good = new ArrayList<>();
		for (Map.Entry<String, byte[]> file : files.entrySet()) {
			DialogueParser.Parsed parsed = DialogueParser.parse(file.getKey(), file.getValue());
			List<String> problems = new ArrayList<>(parsed.problems());
			if (parsed.dialogue() != null && problems.isEmpty()) {
				problems.addAll(DialogueValidator.validate(parsed.dialogue(), env));
			}
			if (problems.isEmpty()) {
				good.add(parsed.dialogue());
			} else {
				log.warn("dialogue {} not loaded: {}", file.getKey(), String.join("; ", problems));
			}
		}
		return of(good);
	}

	public Dialogue get(String id) {
		return byId.get(id);
	}

	public Collection<Dialogue> all() {
		return byId.values();
	}

	public int size() {
		return byId.size();
	}
}
