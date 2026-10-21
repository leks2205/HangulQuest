package io.github.leks2205.hangulquest.ui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one-slot memo that keeps the journal's per-second numbers. */
class StampMemoTest {
	@Test
	void anEmptyMemoHasNothing() {
		assertNull(new StampMemo<String>().value(0L));
	}

	@Test
	void theValueIsServedForItsStampOnly() {
		StampMemo<Object> memo = new StampMemo<>();
		Object built = new Object();
		memo.store(5L, built);

		assertSame(built, memo.value(5L));
		assertNull(memo.value(6L), "a newer stamp means the value is stale");
		assertNull(memo.value(4L));
	}

	@Test
	void storingReplacesTheOldValue() {
		StampMemo<String> memo = new StampMemo<>();
		memo.store(1L, "old");
		memo.store(2L, "new");

		assertNull(memo.value(1L));
		assertEquals("new", memo.value(2L));
	}

	@Test
	void clearDropsTheValue() {
		StampMemo<String> memo = new StampMemo<>();
		memo.store(1L, "x");
		memo.clear();

		assertNull(memo.value(1L));
	}

	@Test
	void aStampIsStableWithinASecondAndMovesOnTheNextOne() {
		long start = 1_700_000_000_000L;

		assertEquals(StampMemo.perSecond(start, 3, 1), StampMemo.perSecond(start + 999, 3, 1));
		assertNotEquals(StampMemo.perSecond(start, 3, 1), StampMemo.perSecond(start + 1000, 3, 1));
	}

	@Test
	void aChangeOrAVariantMovesTheStampEvenInTheSameSecond() {
		long now = 1_700_000_000_000L;

		assertNotEquals(StampMemo.perSecond(now, 3, 1), StampMemo.perSecond(now, 4, 1),
				"a change made inside the journal must refresh the numbers at once");
		assertNotEquals(StampMemo.perSecond(now, 3, 1), StampMemo.perSecond(now, 3, 0),
				"switching the language must refresh the texts at once");
	}

	@Test
	void stampsOfDifferentInputsDoNotCollide() {
		Set<Long> seen = new HashSet<>();
		for (long bucket = 0; bucket < 20; bucket++) {
			for (long changes = 0; changes < 20; changes++) {
				for (int variant = 0; variant < 3; variant++) {
					assertTrue(seen.add(StampMemo.of(bucket, changes, variant)));
				}
			}
		}
	}
}
