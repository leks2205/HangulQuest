package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.ui.WindowPlacement.Point;
import io.github.leks2205.hangulquest.ui.WindowPlacement.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Windows stay until closed, so none may sit on the crosshair or the hotbar. */
class WindowPlacementTest {
	private static final int W = 480;
	private static final int H = 270; // a 1080p screen at GUI scale 4

	private static boolean covers(Point at, int width, int height) {
		Rect window = new Rect(at.x(), at.y(), width, height);
		return WindowPlacement.keepOut(W, H).stream().anyMatch(area -> window.overlap(area) > 0);
	}

	@Test
	void aWindowThatIsAlreadyFreeStaysWhereItWants() {
		Point at = WindowPlacement.resolve(W, H, 100, 20, 200, 60);

		assertEquals(new Point(100, 20), at);
	}

	@Test
	void aCardCentredOnTheScreenMovesOffTheCrosshair() {
		int width = 320;
		int height = 70;
		Point wanted = new Point((W - width) / 2, (H - height) / 2);
		assertTrue(covers(wanted, width, height), "the old spot is on the crosshair");

		Point at = WindowPlacement.resolve(W, H, wanted.x(), wanted.y(), width, height);

		assertFalse(covers(at, width, height));
	}

	@Test
	void theWordCardInTheBottomRightCornerStaysOffTheHotbar() {
		// a narrow GUI: the card would reach over the hotbar
		int narrow = 300;
		int width = 200;
		int height = 36;
		Point at = WindowPlacement.resolve(narrow, 200, narrow - width - 6, 200 - height - 6, width, height);

		Rect window = new Rect(at.x(), at.y(), width, height);
		assertTrue(WindowPlacement.keepOut(narrow, 200).stream().noneMatch(area -> window.overlap(area) > 0));
	}

	@Test
	void theBannerAboveTheBarsIsLeftAlone() {
		int width = 300;
		int height = 35;
		Point wanted = new Point((W - width) / 2, H - height - 70);

		assertEquals(wanted, WindowPlacement.resolve(W, H, wanted.x(), wanted.y(), width, height));
	}

	@Test
	void theResultIsAlwaysOnTheScreen() {
		for (int width : new int[] {130, 250, 320, 380}) {
			for (int height : new int[] {35, 74, 128}) {
				Point at = WindowPlacement.resolve(W, H, (W - width) / 2, (H - height) / 2, width, height);
				assertTrue(at.x() >= 0 && at.y() >= 0 && at.x() + width <= W && at.y() + height <= H,
						width + "x" + height + " at " + at);
			}
		}
	}

	@Test
	void aWindowTooBigForTheScreenStillComesBackOnTheScreen() {
		Point at = WindowPlacement.resolve(100, 60, 20, 20, 400, 300);

		assertEquals(new Point(0, 0), at);
	}

	@Test
	void everyWindowShapeTheModDrawsClearsTheCrosshairOnAnOrdinaryScreen() {
		// width x height of the hint, rule card, celebrations, result and next-quest popups, each centred
		int[][] shapes = {{320, 110}, {320, 120}, {320, 80}, {380, 110}, {300, 88}, {300, 110}, {250, 90}};
		for (int[] shape : shapes) {
			Point at = WindowPlacement.resolve(W, H, (W - shape[0]) / 2, (H - shape[1]) / 2, shape[0], shape[1]);
			assertFalse(covers(at, shape[0], shape[1]), shape[0] + "x" + shape[1] + " at " + at);
		}
	}

	@Test
	void aWindowAlsoKeepsClearOfTheQuestLogWhenThereIsRoom() {
		int width = 320;
		int height = 105;
		Rect log = new Rect(0, 0, 144, 55);

		Point at = WindowPlacement.resolve(W, H, (W - width) / 2, H / 4, width, height, java.util.List.of(log));

		Rect window = new Rect(at.x(), at.y(), width, height);
		assertEquals(0, window.overlap(log), "clear of the log: " + at);
		assertFalse(covers(at, width, height), "and still off the crosshair: " + at);
	}

	@Test
	void theCrosshairComesBeforeTheQuestLog() {
		int width = 470; // no spot beside the log is free of the crosshair
		int height = 110;
		Rect log = new Rect(0, 0, 144, 55);

		Point at = WindowPlacement.resolve(W, H, 5, 60, width, height, java.util.List.of(log));

		assertFalse(covers(at, width, height), "the crosshair is never given up for the log: " + at);
	}
}
