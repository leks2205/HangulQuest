package io.github.leks2205.hangulquest.ui.journal;

import com.sun.management.ThreadMXBean;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import io.github.leks2205.hangulquest.ui.LruCache;
import io.github.leks2205.hangulquest.ui.StampMemo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cached paths of the quest tab allocate nothing per frame. The old tab rebuilt the whole tree
 * (sorting 420 trails, resolving topics, filling hash maps) every frame; here building is measured once
 * and a cached frame is measured against it. Bytes come from the JVM's per-thread allocation counter, so
 * the numbers do not depend on timing.
 */
class AllocationBudgetTest {
	private static final int TRAILS = 420;
	private static final int FRAMES = 2_000;
	/** A cached frame may not allocate; this slack covers the counter's own bookkeeping. */
	private static final long NOISE_BYTES = 2_048;

	private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

	/** 420 trails in 14 chains of 30, like the real tree: deep, narrow, with a few cross links. */
	private static List<Questline> bigTree() {
		List<Questline> trails = new ArrayList<>();
		String[] topics = {"돌", "나무", "음식", "물", "밤", "산", "마을", "불", "동물", "가족"};
		for (int chain = 0; chain < 14; chain++) {
			for (int step = 0; step < 30; step++) {
				String id = "t" + chain + "-" + step;
				List<String> requires = new ArrayList<>();
				if (step > 0) {
					requires.add("t" + chain + "-" + (step - 1));
				}
				if (step > 5 && step % 7 == 0 && chain > 0) {
					requires.add("t" + (chain - 1) + "-" + (step - 3));
				}
				trails.add(new Questline(id, id, id, topics[chain % topics.length], 1, List.of(),
						new Questline.Requires(0, null, requires), List.of(), null, "", "", null,
						chain * 30 + step, 1 + step / 6, false));
			}
		}
		return trails;
	}

	private static long allocated() {
		return THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId());
	}

	private static void assumeMeasurable() {
		Assumptions.assumeTrue(THREADS.isThreadAllocatedMemorySupported() && THREADS.isThreadAllocatedMemoryEnabled(),
				"this JVM does not report per-thread allocation");
	}

	@Test
	void buildingTheTreeIsExpensiveWhichIsWhyItIsCached() {
		assumeMeasurable();
		List<Questline> trails = bigTree();
		TrailGraph graph = TrailGraph.of(trails);

		long before = allocated();
		QuestTreeLayout layout = QuestTreeLayout.build(trails, graph);
		long built = allocated() - before;

		assertEquals(TRAILS, layout.size());
		System.out.println("[alloc] building the tree layout for " + TRAILS + " trails: " + built + " bytes");
		assertTrue(built > 50_000, "the measurement works: building really allocates (" + built + ")");
	}

	@Test
	void aFrameThatChangedNothingAllocatesNothing() {
		assumeMeasurable();
		List<Questline> trails = bigTree();
		QuestTreeLayout layout = QuestTreeLayout.build(trails, TrailGraph.of(trails));
		Map<String, Long> completed = new LinkedHashMap<>(Map.of("t0-0", 1L));
		NodeStates states = NodeStates.build(layout, completed, 10, 2, "t0-1", trail -> true, id -> false, null);
		StampMemo<String> memo = new StampMemo<>();
		long second = StampMemo.perSecond(1_700_000_000_000L, 0, 0);
		memo.store(second, "numbers");
		LruCache<String, Object> icons = new LruCache<>(8);
		icons.get("minecraft:stone", key -> new Object());
		int unchanged = 0;

		// warm up so class loading and JIT do not count
		for (int i = 0; i < 200; i++) {
			unchanged += cachedFrame(layout, trails, states, completed, memo, second, icons);
		}
		long before = allocated();
		for (int i = 0; i < FRAMES; i++) {
			unchanged += cachedFrame(layout, trails, states, completed, memo, second, icons);
		}
		long bytes = allocated() - before;

		assertEquals(FRAMES + 200, unchanged, "every frame found everything cached");
		System.out.println("[alloc] " + FRAMES + " cached quest-tab frames: " + bytes + " bytes");
		assertTrue(bytes <= NOISE_BYTES, FRAMES + " cached frames allocated " + bytes + " bytes");
	}

	/** What a frame of the quest tab does when nothing changed; returns 1 when all of it was cached. */
	private static int cachedFrame(QuestTreeLayout layout, List<Questline> trails, NodeStates states,
								   Map<String, Long> completed, StampMemo<String> memo, long second,
								   LruCache<String, Object> icons) {
		boolean listUnchanged = layout.matches(trails);
		layout.project(120.0, 40.0, 0.5F);
		boolean statesUnchanged = states.matches(layout, completed, 1, 10, 2, "t0-1", null);
		layout.nodeAt(130, 60, 0, 200, 0, 300);
		boolean numbersCached = memo.value(second) != null;
		boolean iconCached = icons.get("minecraft:stone", key -> new Object()) != null;
		return listUnchanged && statesUnchanged && numbersCached && iconCached ? 1 : 0;
	}

	@Test
	void panningAndZoomingAllocateNothingEither() {
		assumeMeasurable();
		List<Questline> trails = bigTree();
		QuestTreeLayout layout = QuestTreeLayout.build(trails, TrailGraph.of(trails));
		for (int i = 0; i < 200; i++) {
			layout.project(100.0 + i, 40.0, 0.5F);
		}

		long before = allocated();
		for (int i = 0; i < FRAMES; i++) {
			layout.project(100.0 + i, 40.0 - i, 0.5F + (i % 7) / 10.0F); // the projection really runs each time
		}
		long bytes = allocated() - before;

		assertTrue(layout.projections() >= FRAMES, "the projection ran on every frame");
		System.out.println("[alloc] " + FRAMES + " panning frames (" + TRAILS + " nodes projected each): "
				+ bytes + " bytes");
		assertTrue(bytes <= NOISE_BYTES, "projecting allocated " + bytes + " bytes");
	}
}
