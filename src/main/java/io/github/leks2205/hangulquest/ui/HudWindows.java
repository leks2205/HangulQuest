package io.github.leks2205.hangulquest.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The queue behind every overlay window the mod draws on the HUD (hint, rule card, culture note,
 * banners and toasts, review prompt, next-quest popup, celebrations). Real screens are not part of it.
 * No Minecraft types in here, so the rules are unit tested.
 *
 * <p>The rules:
 * <ul>
 *   <li>A window stays until the hint key closes it. Only the top window is on screen; the others wait
 *       behind it and the HUD shows how many ("+N").
 *   <li>One key press closes exactly one window. {@link #pressHintKey} is the only entry for the key: when a
 *       window was closed, the press is used up and no hint is shown, graded or counted.
 *   <li>A window with the key of one that is already open or waiting is not queued again.
 *   <li>With "close windows automatically" on, a window closes after its own {@link Window#autoCloseMs}
 *       of being displayed (the old timeouts); the clock stops while no HUD is drawn.
 * </ul>
 *
 * <p>Using it from other code: build a window and hand it over, e.g.
 * {@code hud.windows().show(HudWindows.Window.of(Kind.NOTICE, "next:" + trailId, content).priority(1))}.
 * A quest announcement without any UI types goes through {@code QuestEngine.Feedback#notice}, which the
 * HUD turns into such a window. {@code key} is the repeat guard, {@code group} lets a newer window replace
 * older ones of the same group that are still waiting, {@code priority} orders the waiting ones (higher
 * first; the window on screen is never pushed aside), {@link Window#onShown} runs when the window first
 * appears and {@link Window#onClose} when it goes away, with the reason. A window the code removes itself
 * ({@link #remove}) reports {@link Closed#REMOVED}; only the player (or the timer) closes it with
 * {@link Closed#DISMISSED} / {@link Closed#TIMED_OUT}.
 */
public final class HudWindows {
	/** How many windows may wait behind the visible one; beyond that the least important is dropped. */
	public static final int MAX_WAITING = 8;

	public enum Kind {
		HINT, RULE_CARD, CULTURE, NOTICE, REVIEW_PROMPT, NEXT_QUEST, CELEBRATION, QUIZ_RESULT
	}

	/** Why a window went away. */
	public enum Closed {
		/** The player pressed the hint key. */
		DISMISSED,
		/** "Close windows automatically" is on and its time ran out. */
		TIMED_OUT,
		/** The code took it away: it became obsolete, was answered elsewhere, or the HUD was reset. */
		REMOVED
	}

	/** One window. Build it, set what it needs, then {@link HudWindows#show} it. */
	public static final class Window {
		private final Kind kind;
		private final String key;
		private final Object content;
		private int priority;
		private String group;
		private long autoCloseMs;
		private Runnable onShown = () -> { };
		private Consumer<Closed> onClose = reason -> { };
		private boolean shown;
		private long remainingMs;

		private Window(Kind kind, String key, Object content) {
			this.kind = kind;
			this.key = key;
			this.content = content;
		}

		public static Window of(Kind kind, String key, Object content) {
			return new Window(kind, key, content);
		}

		/** Higher goes first among the waiting windows; 0 by default. */
		public Window priority(int value) {
			this.priority = value;
			return this;
		}

		/** A newer window replaces older waiting ones of its group. */
		public Window group(String value) {
			this.group = value;
			return this;
		}

		/** The old timeout, used only while "close windows automatically" is on; 0 means never. */
		public Window autoCloseMs(long value) {
			this.autoCloseMs = Math.max(0, value);
			return this;
		}

		public Window onShown(Runnable value) {
			this.onShown = value == null ? () -> { } : value;
			return this;
		}

		public Window onClose(Consumer<Closed> value) {
			this.onClose = value == null ? reason -> { } : value;
			return this;
		}

		public Kind kind() {
			return kind;
		}

		public String key() {
			return key;
		}

		public Object content() {
			return content;
		}

		public int priority() {
			return priority;
		}

		public String group() {
			return group;
		}

		public long autoCloseMs() {
			return autoCloseMs;
		}

		/** True once it has been on screen for at least one tick. */
		public boolean shown() {
			return shown;
		}

		/** Time left before it closes by itself (only meaningful while auto-close is on). */
		public long remainingMs() {
			return remainingMs;
		}
	}

	/** Index 0 is the window on screen. */
	private final List<Window> list = new ArrayList<>();
	private boolean accepting = true;
	private long lastTick;

	/**
	 * Queues a window. False when it was not taken: the HUD is switched off, a window with the same key is
	 * open or waiting, or the queue is full of more important ones.
	 */
	public boolean show(Window window) {
		if (!accepting || window == null || contains(window.key)) {
			return false;
		}
		if (window.group != null) {
			for (int i = list.size() - 1; i >= 1; i--) { // the one on screen stays
				if (window.group.equals(list.get(i).group)) {
					close(list.remove(i), Closed.REMOVED);
				}
			}
		}
		int waiting = Math.max(0, list.size() - 1);
		if (waiting >= MAX_WAITING) {
			// the list is sorted, so the least important is at the end and its oldest is the first of that priority
			Window victim = list.get(list.size() - 1);
			if (window.priority <= victim.priority) {
				return false;
			}
			int first = list.size() - 1;
			while (first > 1 && list.get(first - 1).priority == victim.priority) {
				first--;
			}
			close(list.remove(first), Closed.REMOVED);
		}
		int at = list.size();
		while (at > 1 && list.get(at - 1).priority < window.priority) {
			at--;
		}
		list.add(at, window);
		return true;
	}

	/** The window on screen, or null. */
	public Window top() {
		return list.isEmpty() ? null : list.get(0);
	}

	public boolean isOpen() {
		return !list.isEmpty();
	}

	/** How many windows wait behind the one on screen. */
	public int waiting() {
		return Math.max(0, list.size() - 1);
	}

	public boolean contains(String key) {
		for (Window window : list) {
			if (window.key.equals(key)) {
				return true;
			}
		}
		return false;
	}

	/** All windows, the one on screen first. */
	public List<Window> all() {
		return List.copyOf(list);
	}

	/**
	 * The hint key. When a window is on screen it closes the top one and nothing else happens: {@code showHint}
	 * is not called, so no hint is shown, graded or counted. Only when no window is open (or none can be seen
	 * right now) the press asks for a hint. Returns true when it closed a window.
	 *
	 * @param displayable whether the HUD is drawing windows at all right now (not behind a screen, not hidden)
	 */
	public boolean pressHintKey(boolean displayable, Runnable showHint) {
		if (displayable && !list.isEmpty() && list.get(0).shown) {
			close(list.remove(0), Closed.DISMISSED);
			return true;
		}
		showHint.run();
		return false;
	}

	/** Takes a window away without the player asking (answered elsewhere, obsolete). */
	public boolean remove(String key) {
		return removeWhere(window -> window.key.equals(key)) > 0;
	}

	public int removeWhere(Predicate<Window> test) {
		int removed = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			if (test.test(list.get(i))) {
				close(list.remove(i), Closed.REMOVED);
				removed++;
			}
		}
		return removed;
	}

	public void clear() {
		while (!list.isEmpty()) {
			close(list.remove(list.size() - 1), Closed.REMOVED);
		}
	}

	/** While off, nothing is queued and what is queued goes away (HUD cards switched off). */
	public void setAccepting(boolean value) {
		accepting = value;
		if (!value) {
			clear();
		}
	}

	/**
	 * Once per client tick. Shows the next window when the HUD can draw, and with {@code autoClose} closes the
	 * top one after its time on screen.
	 */
	public void tick(long now, boolean displayable, boolean autoClose) {
		long elapsed = lastTick == 0 ? 0 : Math.max(0, now - lastTick);
		lastTick = now;
		if (list.isEmpty() || !displayable) {
			return;
		}
		Window top = list.get(0);
		if (!top.shown) {
			top.shown = true;
			top.remainingMs = top.autoCloseMs;
			top.onShown.run();
			return;
		}
		if (autoClose && top.autoCloseMs > 0) {
			top.remainingMs -= elapsed;
			if (top.remainingMs <= 0) {
				close(list.remove(0), Closed.TIMED_OUT);
			}
		}
	}

	private static void close(Window window, Closed reason) {
		window.onClose.accept(reason);
	}
}
