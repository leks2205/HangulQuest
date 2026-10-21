package io.github.leks2205.hangulquest.culture;

import io.github.leks2205.hangulquest.story.MarkFile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The queue behind the culture card on the HUD: one card at a time, each note once. A note is marked as shown
 * the moment its card appears, so a card that was never read is still not repeated; if the player misses it,
 * the Library has the note.
 */
public final class CultureCards {
	/** The card on screen. */
	public record Card(CultureNote note, String key, long until) {
	}

	private final MarkFile shown;
	private final Deque<CultureTrigger.Due> queue = new ArrayDeque<>();
	private final Set<String> queued = new HashSet<>();
	private Card current;

	public CultureCards(MarkFile shown) {
		this.shown = shown;
	}

	/** Adds the notes that are due and not queued yet. */
	public void offer(List<CultureTrigger.Due> due) {
		for (CultureTrigger.Due entry : due) {
			if (queued.add(entry.note().id() + "@" + entry.key())) {
				queue.add(entry);
			}
		}
	}

	/**
	 * Moves on: drops an expired card and, when the screen is free, shows the next note for {@code durationMs}.
	 * Returns the card to draw, or null.
	 */
	public Card tick(long now, boolean canShow, long durationMs) {
		if (current != null && now >= current.until()) {
			current = null;
		}
		if (current == null && canShow && !queue.isEmpty()) {
			CultureTrigger.Due next = queue.poll();
			queued.remove(next.note().id() + "@" + next.key());
			if (!shown.is(next.note().id(), next.key())) {
				shown.set(next.note().id(), next.key());
				current = new Card(next.note(), next.key(), now + durationMs);
			}
		}
		return current;
	}

	public MarkFile shown() {
		return shown;
	}

	public Card current() {
		return current;
	}

	public void dismiss() {
		current = null;
	}

	public int waiting() {
		return queue.size();
	}
}
