package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.TopicResolver;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import io.github.leks2205.hangulquest.ui.QuestTheme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shape of the quest tree for one trail list: which trail sits on which level, in which slot,
 * which parent draws its connector, which icon and colour it gets. Building it sorts every trail and
 * resolves every topic, so it is done once per trail list and then only projected onto the screen
 * (zoom, pan), which is a handful of multiplications and allocates nothing.
 *
 * <p>Everything is indexed by position in {@link #trail(int)} order (trail order, then id).
 */
public final class QuestTreeLayout {
	static final int SLOT = 30;
	static final int NODE_WIDTH = 22;
	static final int NODE_HEIGHT = 20;
	static final int ROW_GAP = 28;

	private final List<Questline> source;
	private final Questline[] trails;
	private final Map<String, Integer> indexById;
	private final int[] depth;
	private final int[] primaryParent;
	private final int[][] primaryChildren;
	private final int[][] parents;
	private final int[][] dependents;
	private final String[] topic;
	private final String[] icon;
	private final int[] color;
	private final int[] baseCx;
	private final int[] baseTop;
	private final int levels;
	private final int baseWidth;
	private final int baseHeight;

	// The projection onto the screen, recomputed only when zoom or origin moved.
	private final int[] screenCx;
	private final int[] screenTop;
	private int nodeWidth;
	private int nodeHeight;
	private double projectedX = Double.NaN;
	private double projectedY = Double.NaN;
	private float projectedZoom = Float.NaN;
	private int projections;

	private boolean[] connectedSet;
	private int connectedFor = -2;

	private QuestTreeLayout(List<Questline> source, List<Questline> sorted, TrailGraph graph) {
		this.source = source;
		int count = sorted.size();
		this.trails = sorted.toArray(new Questline[0]);
		this.indexById = new HashMap<>();
		for (int i = 0; i < count; i++) {
			indexById.put(trails[i].id(), i);
		}
		Map<String, Questline> byId = graph.byId();

		depth = new int[count];
		int maxLevel = 1;
		for (int i = 0; i < count; i++) {
			depth[i] = Math.max(1, graph.depth(trails[i].id()));
			maxLevel = Math.max(maxLevel, depth[i]);
		}
		levels = maxLevel;

		topic = new String[count];
		icon = new String[count];
		color = new int[count];
		for (int i = 0; i < count; i++) {
			topic[i] = TopicResolver.keyFor(trails[i], byId);
			icon[i] = QuestTheme.icon(topic[i]);
			color[i] = QuestTheme.color(trails[i].topicKey());
		}

		// One primary parent per quest gives the tree a real shape; additional prerequisites stay in the detail.
		primaryParent = new int[count];
		parents = new int[count][];
		for (int i = 0; i < count; i++) {
			primaryParent[i] = -1;
			List<Integer> known = new ArrayList<>();
			int chosen = -1;
			for (String parentId : trails[i].requires().completed()) {
				Integer parent = indexById.get(parentId);
				if (parent == null) {
					continue;
				}
				known.add(parent);
				if (chosen < 0) {
					chosen = parent;
				}
			}
			for (int parent : known) {
				if (topic[parent].equals(topic[i])) {
					chosen = parent;
					break;
				}
			}
			primaryParent[i] = chosen;
			parents[i] = known.stream().mapToInt(Integer::intValue).toArray();
		}
		List<List<Integer>> childLists = new ArrayList<>();
		List<List<Integer>> dependentLists = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			childLists.add(new ArrayList<>());
			dependentLists.add(new ArrayList<>());
		}
		for (int i = 0; i < count; i++) {
			if (primaryParent[i] >= 0) {
				childLists.get(primaryParent[i]).add(i);
			}
			Set<String> seen = new HashSet<>();
			for (String parentId : trails[i].requires().completed()) {
				Integer parent = indexById.get(parentId);
				if (parent != null && seen.add(parentId)) {
					dependentLists.get(parent).add(i);
				}
			}
		}
		primaryChildren = toArrays(childLists);
		dependents = toArrays(dependentLists);

		// Rows per level; a deeper row is ordered by where its parent sits in the row above.
		List<List<Integer>> rowLists = new ArrayList<>();
		for (int level = 0; level < levels; level++) {
			rowLists.add(new ArrayList<>());
		}
		for (int i = 0; i < count; i++) {
			rowLists.get(depth[i] - 1).add(i);
		}
		int[] xIndex = new int[count];
		Arrays.fill(xIndex, Integer.MAX_VALUE);
		int maxRow = 1;
		for (List<Integer> row : rowLists) {
			maxRow = Math.max(maxRow, row.size());
		}
		baseWidth = SLOT * maxRow;
		baseHeight = (levels - 1) * ROW_GAP + NODE_HEIGHT;
		baseCx = new int[count];
		baseTop = new int[count];
		for (int level = 0; level < levels; level++) {
			List<Integer> row = rowLists.get(level);
			if (level > 0) {
				row.sort(Comparator
						.comparingInt((Integer node) -> primaryParent[node] < 0 ? Integer.MAX_VALUE
								: xIndex[primaryParent[node]])
						.thenComparingInt(node -> node));
			}
			int startX = (baseWidth - SLOT * row.size()) / 2;
			for (int k = 0; k < row.size(); k++) {
				int node = row.get(k);
				xIndex[node] = k;
				baseCx[node] = startX + SLOT * k + SLOT / 2;
				baseTop[node] = level * ROW_GAP;
			}
		}
		screenCx = new int[count];
		screenTop = new int[count];
	}

	private static int[][] toArrays(List<List<Integer>> lists) {
		int[][] arrays = new int[lists.size()][];
		for (int i = 0; i < arrays.length; i++) {
			arrays[i] = lists.get(i).stream().mapToInt(Integer::intValue).toArray();
		}
		return arrays;
	}

	/** Lays out the trails; {@code graph} must be the graph of the same list. */
	public static QuestTreeLayout build(List<Questline> trails, TrailGraph graph) {
		List<Questline> sorted = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Questline trail : trails) {
			if (seen.add(trail.id())) {
				sorted.add(trail);
			}
		}
		sorted.sort(Comparator.comparingInt(Questline::order).thenComparing(Questline::id));
		return new QuestTreeLayout(trails, sorted, graph);
	}

	/**
	 * True when this layout was built for exactly these trails. The usual answer is the same list
	 * instance; a copy with the same trails in the same order counts too, so a caller that hands out
	 * a fresh list every time does not force a rebuild every frame.
	 */
	public boolean matches(List<Questline> trails) {
		if (trails == source) {
			return true;
		}
		if (trails.size() != source.size()) {
			return false;
		}
		for (int i = 0; i < trails.size(); i++) {
			if (trails.get(i) != source.get(i)) {
				return false;
			}
		}
		return true;
	}

	public int size() {
		return trails.length;
	}

	public Questline trail(int index) {
		return trails[index];
	}

	/** Position of the trail with this id, or -1. */
	public int indexOf(String id) {
		Integer index = id == null ? null : indexById.get(id);
		return index == null ? -1 : index;
	}

	/** Tree level of the trail, roots are 1. */
	public int depth(int index) {
		return depth[index];
	}

	/** Number of tree levels. */
	public int levels() {
		return levels;
	}

	public String topic(int index) {
		return topic[index];
	}

	public String icon(int index) {
		return icon[index];
	}

	public int color(int index) {
		return color[index];
	}

	/** The parent that draws this trail's connector, or -1 for a root. */
	public int primaryParent(int index) {
		return primaryParent[index];
	}

	/** Every existing prerequisite, in the order the trail lists them. */
	public int[] parents(int index) {
		return parents[index];
	}

	/** Every trail that lists this one as a prerequisite, in trail order. */
	public int[] dependents(int index) {
		return dependents[index];
	}

	public int baseWidth() {
		return baseWidth;
	}

	public int baseHeight() {
		return baseHeight;
	}

	// ---------------------------------------------------------------- projection

	/**
	 * Places every node on the screen. Skipped when zoom and origin are what the last call used, which
	 * is every frame the player neither zooms, pans nor resizes the window.
	 */
	public void project(double originX, double originY, float zoom) {
		if (originX == projectedX && originY == projectedY && zoom == projectedZoom) {
			return;
		}
		projectedX = originX;
		projectedY = originY;
		projectedZoom = zoom;
		projections++;
		nodeWidth = Math.max(10, (int) Math.round(NODE_WIDTH * zoom));
		nodeHeight = Math.max(8, (int) Math.round(NODE_HEIGHT * zoom));
		for (int i = 0; i < trails.length; i++) {
			screenCx[i] = (int) Math.round(originX + baseCx[i] * zoom);
			screenTop[i] = (int) Math.round(originY + baseTop[i] * zoom);
		}
	}

	/** Horizontal centre of the node on screen, valid after {@link #project}. */
	public int cx(int index) {
		return screenCx[index];
	}

	public int top(int index) {
		return screenTop[index];
	}

	public int nodeWidth() {
		return nodeWidth;
	}

	public int nodeHeight() {
		return nodeHeight;
	}

	/** How often the projection really ran; tests use it to prove that an unchanged view is free. */
	int projections() {
		return projections;
	}

	/** Left edge of the node on screen. */
	public int left(int index) {
		return screenCx[index] - nodeWidth / 2;
	}

	/** True when the point is on the node; nodes that are far off screen are not asked. */
	public boolean contains(int index, double x, double y) {
		return x >= screenCx[index] - nodeWidth / 2.0 && x < screenCx[index] + nodeWidth / 2.0
				&& y >= screenTop[index] && y < screenTop[index] + nodeHeight;
	}

	/** The topmost node under the point inside the given view, or -1. */
	public int nodeAt(double x, double y, int viewTop, int viewBottom, int viewLeft, int viewRight) {
		for (int i = trails.length - 1; i >= 0; i--) {
			int left = left(i);
			if (left + nodeWidth < viewLeft || left > viewRight
					|| screenTop[i] + nodeHeight < viewTop || screenTop[i] > viewBottom) {
				continue;
			}
			if (x >= left && x < left + nodeWidth && y >= screenTop[i] && y < screenTop[i] + nodeHeight) {
				return i;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ highlight

	/**
	 * The trail, its ancestors up the primary chain and everything below it: what stays lit when a
	 * trail is selected. Cached for the last selection.
	 */
	public boolean[] connected(int selected) {
		if (selected == connectedFor) {
			return connectedSet;
		}
		boolean[] set = new boolean[trails.length];
		if (selected >= 0 && selected < trails.length) {
			set[selected] = true;
			int parent = primaryParent[selected];
			while (parent >= 0 && !set[parent]) {
				set[parent] = true;
				parent = primaryParent[parent];
			}
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(selected);
			while (!queue.isEmpty()) {
				int node = queue.poll();
				for (int child : primaryChildren[node]) {
					if (!set[child]) {
						set[child] = true;
						queue.add(child);
					}
				}
			}
		}
		connectedSet = set;
		connectedFor = selected;
		return set;
	}
}
