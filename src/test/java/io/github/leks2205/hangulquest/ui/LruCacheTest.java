package io.github.leks2205.hangulquest.ui;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The cache behind wrapped text, fitted text and item stacks. */
class LruCacheTest {
	@Test
	void theLoaderRunsOncePerKey() {
		LruCache<String, Object> cache = new LruCache<>(8);
		Object first = cache.get("minecraft:stone", key -> new Object());

		for (int i = 0; i < 100; i++) {
			assertSame(first, cache.get("minecraft:stone", key -> new Object()));
		}

		assertEquals(1, cache.loads());
	}

	@Test
	void aMissingValueIsRememberedToo() {
		LruCache<String, Object> cache = new LruCache<>(8);

		assertNull(cache.get("minecraft:not_an_item", key -> null));
		assertNull(cache.get("minecraft:not_an_item", key -> new Object()));

		assertEquals(1, cache.loads(), "an unknown item id is looked up once, not every frame");
	}

	@Test
	void theOldestUnusedEntryLeavesFirst() {
		LruCache<Integer, String> cache = new LruCache<>(2);
		cache.get(1, key -> "one");
		cache.get(2, key -> "two");
		cache.get(1, key -> "one"); // 1 is fresh again, 2 is now the oldest
		cache.get(3, key -> "three");

		assertEquals(2, cache.size());
		int loadsBefore = cache.loads();
		cache.get(1, key -> "one");
		assertEquals(loadsBefore, cache.loads(), "the recently used key survived");
		cache.get(2, key -> "two");
		assertEquals(loadsBefore + 1, cache.loads(), "the evicted key has to be loaded again");
	}

	@Test
	void clearForcesAReload() {
		Map<String, Integer> source = new HashMap<>(Map.of("a", 1));
		LruCache<String, Integer> cache = new LruCache<>(4);
		assertEquals(1, cache.get("a", source::get));

		source.put("a", 2);
		assertEquals(1, cache.get("a", source::get), "still the cached value");
		cache.clear();

		assertEquals(2, cache.get("a", source::get));
	}
}
