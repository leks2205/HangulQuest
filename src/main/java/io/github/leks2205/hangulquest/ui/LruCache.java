package io.github.leks2205.hangulquest.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A small bounded cache for values that are expensive to build and cheap to look up: wrapped text,
 * fitted text, item stacks. A loader that returns null is remembered too, so a missing item is
 * not looked up again every frame. Not thread-safe; the render thread owns it.
 */
public final class LruCache<K, V> {
	private static final Object ABSENT = new Object();

	private final Map<K, Object> map;
	private int loads;

	public LruCache(int capacity) {
		this.map = new LinkedHashMap<>(16, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, Object> eldest) {
				return size() > capacity;
			}
		};
	}

	/** The cached value for the key, loading and remembering it on the first request. */
	@SuppressWarnings("unchecked")
	public V get(K key, Function<? super K, ? extends V> loader) {
		Object hit = map.get(key);
		if (hit != null) {
			return hit == ABSENT ? null : (V) hit;
		}
		loads++;
		V value = loader.apply(key);
		map.put(key, value == null ? ABSENT : value);
		return value;
	}

	public void clear() {
		map.clear();
	}

	public int size() {
		return map.size();
	}

	/** How often the loader ran; tests use it to prove that a lookup was served from the cache. */
	public int loads() {
		return loads;
	}
}
