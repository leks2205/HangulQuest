package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.ui.HudWindows.Closed;
import io.github.leks2205.hangulquest.ui.HudWindows.Kind;
import io.github.leks2205.hangulquest.ui.HudWindows.Window;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The window queue behind every HUD card: open, stack, dismiss, the "+N" count, repeats and the old timeouts. */
class HudWindowsTest {
	private static final long T0 = 1_000_000L;

	private final HudWindows windows = new HudWindows();

	private static Window window(String key) {
		return Window.of(Kind.NOTICE, key, key);
	}

	/** One client tick with the HUD drawing: the top window becomes visible. */
	private void tick(long now) {
		windows.tick(now, true, false);
	}

	@Test
	void aWindowOpensOnTheNextTickAndStaysForever() {
		assertTrue(windows.show(window("a")));
		assertFalse(windows.top().shown(), "not on screen before a tick showed it");

		tick(T0);
		assertTrue(windows.top().shown());
		windows.tick(T0 + 3_600_000L, true, false); // an hour later, automatic closing off
		assertEquals("a", windows.top().key(), "no timeout without the setting");
	}

	@Test
	void moreWindowsWaitBehindTheTopOneAndTheBadgeCountsThem() {
		windows.show(window("a"));
		windows.show(window("b"));
		windows.show(window("c"));
		tick(T0);

		assertEquals("a", windows.top().key());
		assertEquals(2, windows.waiting(), "the +N badge");
	}

	@Test
	void oneKeyPressClosesExactlyOneWindowAndTheNextBecomesVisible() {
		windows.show(window("a"));
		windows.show(window("b"));
		windows.show(window("c"));
		tick(T0);

		assertTrue(windows.pressHintKey(true, () -> { }));
		assertEquals("b", windows.top().key());
		assertEquals(1, windows.waiting());
		assertFalse(windows.top().shown(), "the next one shows on the next tick");

		tick(T0 + 50);
		assertTrue(windows.top().shown());
		assertTrue(windows.pressHintKey(true, () -> { }));
		assertEquals("c", windows.top().key());
		assertEquals(0, windows.waiting());
	}

	@Test
	void theLastPressLeavesNoWindow() {
		windows.show(window("a"));
		tick(T0);

		assertTrue(windows.pressHintKey(true, () -> { }));
		assertNull(windows.top());
		assertFalse(windows.isOpen());
	}

	@Test
	void aPressClosesNothingThatWasNeverOnScreen() {
		windows.show(window("a"));
		int[] hints = {0};

		assertFalse(windows.pressHintKey(true, () -> hints[0]++), "queued this very tick, the player has not seen it");
		assertEquals(1, hints[0]);
		assertTrue(windows.contains("a"));
	}

	@Test
	void aPressClosesNothingWhileTheHudCannotDrawWindows() {
		windows.show(window("a"));
		tick(T0);
		int[] hints = {0};

		assertFalse(windows.pressHintKey(false, () -> hints[0]++), "behind a screen or with F1 hidden it is a plain hint request");
		assertEquals(1, hints[0]);
		assertTrue(windows.contains("a"));
	}

	@Test
	void theSameWindowIsNotQueuedTwice() {
		assertTrue(windows.show(window("hint:x")));
		assertFalse(windows.show(window("hint:x")), "already on screen or waiting");
		windows.show(window("other"));
		assertFalse(windows.show(window("other")));
		assertEquals(1, windows.waiting());

		tick(T0);
		windows.pressHintKey(true, () -> { });
		assertTrue(windows.show(window("hint:x")), "after it was closed it may come again");
	}

	@Test
	void aNewerWindowOfTheSameGroupReplacesOlderWaitingOnesButNotTheOneOnScreen() {
		windows.show(Window.of(Kind.CELEBRATION, "s1", null).group("step"));
		windows.show(Window.of(Kind.CELEBRATION, "s2", null).group("step"));
		tick(T0);
		windows.show(Window.of(Kind.CELEBRATION, "s3", null).group("step"));

		List<String> keys = windows.all().stream().map(Window::key).toList();
		assertEquals(List.of("s1", "s3"), keys, "s1 is on screen and stays, s2 waited and was replaced");
	}

	@Test
	void importantWindowsGoAheadOfWaitingOnesButNeverPushTheVisibleOneAside() {
		windows.show(window("shown").priority(0));
		tick(T0);
		windows.show(window("low").priority(0));
		windows.show(window("high").priority(3));
		windows.show(window("mid").priority(1));

		assertEquals(List.of("shown", "high", "mid", "low"), windows.all().stream().map(Window::key).toList());
		assertEquals("shown", windows.top().key());
	}

	@Test
	void aFullQueueDropsTheLeastImportantAndNeverLosesAnImportantWindow() {
		windows.show(window("top"));
		for (int i = 0; i < HudWindows.MAX_WAITING; i++) {
			windows.show(window("low" + i).priority(0));
		}
		assertEquals(HudWindows.MAX_WAITING, windows.waiting());

		assertFalse(windows.show(window("another low").priority(0)), "no room for one that is no more important");
		assertTrue(windows.show(window("important").priority(2)));
		assertEquals(HudWindows.MAX_WAITING, windows.waiting());
		assertTrue(windows.contains("important"));
		assertFalse(windows.contains("low0"), "the oldest of the least important went");
		assertTrue(windows.contains("low1"));
	}

	@Test
	void windowsAreOnlyTakenWhileTheHudIsOn() {
		windows.show(window("a"));
		windows.setAccepting(false);

		assertFalse(windows.isOpen(), "switching the cards off clears the queue");
		assertFalse(windows.show(window("b")));
		windows.setAccepting(true);
		assertTrue(windows.show(window("b")));
	}

	@Test
	void onShownRunsOnceWhenTheWindowFirstAppears() {
		int[] shown = {0};
		windows.show(window("a").onShown(() -> shown[0]++));
		windows.show(window("b").onShown(() -> shown[0] += 10));

		windows.tick(T0, false, false);
		assertEquals(0, shown[0], "nothing is on screen behind a screen");
		tick(T0 + 50);
		tick(T0 + 100);
		assertEquals(1, shown[0], "a showed once, b still waits");
		windows.pressHintKey(true, () -> { });
		tick(T0 + 150);
		assertEquals(11, shown[0]);
	}

	@Test
	void onCloseTellsWhyTheWindowWentAway() {
		List<Closed> reasons = new ArrayList<>();
		windows.show(window("a").onClose(reasons::add));
		windows.show(window("b").onClose(reasons::add));
		windows.show(window("c").onClose(reasons::add));
		tick(T0);

		windows.pressHintKey(true, () -> { });
		windows.remove("c");
		windows.clear();

		assertEquals(List.of(Closed.DISMISSED, Closed.REMOVED, Closed.REMOVED), reasons);
	}

	@Test
	void withAutomaticClosingAWindowGoesAfterItsOwnTimeOnScreen() {
		List<Closed> reasons = new ArrayList<>();
		windows.show(window("a").autoCloseMs(3_500).onClose(reasons::add));
		windows.show(window("b").autoCloseMs(3_500));
		windows.tick(T0, true, true); // appears
		windows.tick(T0 + 3_000, true, true);
		assertEquals("a", windows.top().key());

		windows.tick(T0 + 3_600, true, true);
		assertEquals("b", windows.top().key());
		assertEquals(List.of(Closed.TIMED_OUT), reasons);
		assertEquals(0, windows.waiting());
	}

	@Test
	void theTimerOnlyRunsWhileTheWindowCanBeSeen() {
		windows.show(window("a").autoCloseMs(1_000));
		windows.tick(T0, true, true);
		windows.tick(T0 + 500, true, true);
		windows.tick(T0 + 10_000, false, true); // a screen was open for ten seconds
		windows.tick(T0 + 10_100, true, true);
		assertEquals("a", windows.top().key(), "the time behind the screen does not count");

		windows.tick(T0 + 10_700, true, true);
		assertNull(windows.top());
	}

	@Test
	void aWindowWithoutATimeoutIgnoresTheSetting() {
		windows.show(window("a"));
		windows.tick(T0, true, true);
		windows.tick(T0 + 600_000L, true, true);
		assertEquals("a", windows.top().key());
	}

	@Test
	void theKeyIsOnlyAHintRequestWhenNoWindowIsOpen() {
		int[] hints = {0};
		assertFalse(windows.pressHintKey(true, () -> hints[0]++));
		assertEquals(1, hints[0]);

		windows.show(window("a"));
		tick(T0);
		assertTrue(windows.pressHintKey(true, () -> hints[0]++));
		assertEquals(1, hints[0], "the press that closed a window asked for no hint");
	}
}
