package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The villager requests the player has taken on: accepting, tracking in the quest log, checking the goal, handing
 * in and abandoning. The state lives in {@link ProgressData#requests}; this class only adds the goal trackers.
 *
 * <p>A request takes a regular slot of the quest log. Accepting one when the log is full takes the oldest trail the
 * game picked out of the log; when every slot holds a trail the player chose, the request waits in the journal,
 * untracked. An untracked request does not advance.
 */
public final class Requests implements QuestEngine.Extra {
	/** Open requests at once, over all villagers. */
	public static final int MAX_OPEN = 3;
	/** Quest log row ids of requests start with this, followed by the villager's UUID. */
	public static final String ROW_PREFIX = "request:";

	/** The quest log the requests share their slots with. */
	public interface Log {
		/** True when there is a free slot now, making one by taking out the game's oldest pick if needed. */
		boolean makeRoom(long now);

		/** The clue of a goal at a Korean level, as the quest log writes it. */
		String clue(QuestStep step, int koreanLevel);

		/** What is still missing for a goal (a condition), or "". */
		String status(QuestStep step, GoalEvaluator.Tracker tracker);
	}

	/** A tracked request whose goal was just met. */
	public interface Listener {
		void done(ProgressData.OpenRequest open, Request request);
	}

	private static final Log NO_LOG = new Log() {
		@Override
		public boolean makeRoom(long now) {
			return false;
		}

		@Override
		public String clue(QuestStep step, int koreanLevel) {
			return "";
		}

		@Override
		public String status(QuestStep step, GoalEvaluator.Tracker tracker) {
			return "";
		}
	};

	private final ProgressData.RequestData data;
	private final RequestSet set;
	private final Runnable dirty;
	private final Function<String, String> itemKo;
	private final Map<String, GoalEvaluator.Tracker> trackers = new HashMap<>();
	private final Map<String, QuestStep> steps = new HashMap<>();
	private Log log = NO_LOG;

	/**
	 * @param dirty  called after every change of the saved state
	 * @param itemKo the Korean name of an item id (for the clue of a SHOW request), or null when unknown
	 */
	public Requests(ProgressData data, RequestSet set, Runnable dirty, Function<String, String> itemKo) {
		if (data.requests == null) {
			data.requests = new ProgressData.RequestData();
		}
		this.data = data.requests;
		this.set = set == null ? RequestSet.EMPTY : set;
		this.dirty = dirty == null ? () -> {
		} : dirty;
		this.itemKo = itemKo == null ? id -> null : itemKo;
	}

	/** Connects the quest log; without one, accepted requests wait untracked. */
	public void attach(Log log) {
		this.log = log == null ? NO_LOG : log;
	}

	public RequestSet set() {
		return set;
	}

	/** The open requests in the order they were accepted. */
	public List<ProgressData.OpenRequest> open() {
		return List.copyOf(data.open);
	}

	public ProgressData.OpenRequest openFor(String villager) {
		for (ProgressData.OpenRequest open : data.open) {
			if (open.villager.equals(villager)) {
				return open;
			}
		}
		return null;
	}

	public boolean isOpen(String villager) {
		return openFor(villager) != null;
	}

	/** The request of an open entry; null when its file is gone. */
	public Request request(ProgressData.OpenRequest open) {
		return open == null ? null : set.get(open.id);
	}

	/** True when this villager may ask for something now: it has nothing open and fewer than three are open. */
	public boolean canAccept(String villager) {
		return openFor(villager) == null && data.open.size() < MAX_OPEN;
	}

	/** How often a request was handed in. */
	public int finishedCount(String requestId) {
		return data.finished.getOrDefault(requestId, 0);
	}

	/**
	 * The request a villager offers: one that fits it and the player and is not open elsewhere, the least often
	 * done first; among those the villager and the day pick one, so it stays the same all day.
	 */
	public Request choose(Personality personality, Region region, String profession, int playerLevel, UUID villager,
						  long day) {
		List<Request> fitting = new ArrayList<>();
		for (Request request : set.all()) {
			boolean busy = data.open.stream().anyMatch(open -> open.id.equals(request.id()));
			if (!busy && request.fits(personality, region, profession, playerLevel)) {
				fitting.add(request);
			}
		}
		if (fitting.isEmpty()) {
			return null;
		}
		int least = fitting.stream().mapToInt(request -> finishedCount(request.id())).min().orElse(0);
		List<Request> fresh = fitting.stream().filter(request -> finishedCount(request.id()) == least)
				.sorted(Comparator.comparing(Request::id)).toList();
		return fresh.get(Math.floorMod(VillagerProfiles.mix(villager, 17 + day), fresh.size()));
	}

	/** Takes on a request; null when the villager or the player already has too many. */
	public ProgressData.OpenRequest accept(Request request, String villager, String name, long now) {
		if (request == null || !canAccept(villager)) {
			return null;
		}
		ProgressData.OpenRequest open = new ProgressData.OpenRequest();
		open.id = request.id();
		open.villager = villager;
		open.name = name;
		open.acceptedAt = now;
		open.tracked = log.makeRoom(now);
		trackers.remove(villager);
		data.open.add(open);
		dirty.run();
		return open;
	}

	/**
	 * True when the request can be handed in now: a tracked SHOW request while the player holds enough of the item,
	 * the others once their goal was met (which only happens while tracked).
	 */
	public boolean ready(ProgressData.OpenRequest open, String heldItem, int heldCount) {
		Request request = request(open);
		if (request == null) {
			return false;
		}
		if (request.kind() == Request.Kind.SHOW) {
			return open.tracked && request.showItem().equals(heldItem) && heldCount >= request.showCount();
		}
		return open.done;
	}

	/** Hands in the villager's request: it is closed and counted. Returns it, or null when there was none. */
	public Request handIn(String villager) {
		ProgressData.OpenRequest open = openFor(villager);
		if (open == null) {
			return null;
		}
		data.open.remove(open);
		trackers.remove(villager);
		data.finished.merge(open.id, 1, Integer::sum);
		dirty.run();
		return set.get(open.id);
	}

	/** Drops the villager's request without a penalty (the journal's "abandon"). */
	public boolean abandon(String villager) {
		ProgressData.OpenRequest open = openFor(villager);
		if (open == null) {
			return false;
		}
		data.open.remove(open);
		trackers.remove(villager);
		dirty.run();
		return true;
	}

	/** "Reset progress": every open request ends and nothing counts as handed in any more. */
	public void reset() {
		data.open.clear();
		data.finished.clear();
		trackers.clear();
		dirty.run();
	}

	/** Puts a request into the quest log or takes it out; false when the log has no room for it. */
	public boolean setTracked(String villager, boolean on, long now) {
		ProgressData.OpenRequest open = openFor(villager);
		if (open == null) {
			return false;
		}
		if (open.tracked == on) {
			return true;
		}
		if (on && !log.makeRoom(now)) {
			return false;
		}
		open.tracked = on;
		dirty.run();
		return true;
	}

	/** Checks the goals of the tracked requests against the world. */
	public void tick(Observation obs, Listener listener) {
		for (ProgressData.OpenRequest open : List.copyOf(data.open)) {
			Request request = request(open);
			if (!open.tracked || open.done || request == null || request.goal() == null) {
				continue;
			}
			GoalEvaluator.Tracker tracker = tracker(open, request);
			boolean complete = GoalEvaluator.tick(request.goal(), List.of(), obs, tracker);
			if (tracker.count != open.progress) {
				open.progress = tracker.count;
				dirty.run();
			}
			if (complete) {
				open.done = true;
				dirty.run();
				if (listener != null) {
					listener.done(open, request);
				}
			}
		}
	}

	private GoalEvaluator.Tracker tracker(ProgressData.OpenRequest open, Request request) {
		return trackers.computeIfAbsent(open.villager, villager -> {
			GoalEvaluator.Tracker made = new GoalEvaluator.Tracker();
			GoalEvaluator.seed(made, request.goal(), open.progress);
			if (request.kind() == Request.Kind.GIVE) {
				made.onlyEntity = uuidOf(villager);
			}
			return made;
		});
	}

	private static UUID uuidOf(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException notAUuid) {
			return null;
		}
	}

	/** The goal as a quest step, made once per request so the clue cache keeps it. */
	QuestStep step(Request request) {
		return request.goal() == null ? null : steps.computeIfAbsent(request.id(),
				id -> new QuestStep("request", List.of(), request.goal(), List.of(), null, null, null, null, null));
	}

	// ---------------------------------------------------------------- quest log

	@Override
	public int trackedCount() {
		int count = 0;
		for (ProgressData.OpenRequest open : data.open) {
			// a request whose file is gone shows no row, so it takes no slot either
			if (open.tracked && request(open) != null) {
				count++;
			}
		}
		return count;
	}

	@Override
	public List<QuestEngine.TrackView> views() {
		List<QuestEngine.TrackView> views = new ArrayList<>();
		for (ProgressData.OpenRequest open : data.open) {
			Request request = request(open);
			if (!open.tracked || request == null) {
				continue;
			}
			views.add(view(open, request));
		}
		return views;
	}

	/** The row of a request: "<name>의 부탁", the clue, and what to do when it is done. */
	QuestEngine.TrackView view(ProgressData.OpenRequest open, Request request) {
		String name = open.name == null ? "" : open.name;
		String titleKo = name + "의 부탁";
		String clue;
		String status;
		int needed = 1;
		boolean countable = false;
		if (request.kind() == Request.Kind.SHOW) {
			clue = showClue(request);
			status = "";
		} else {
			QuestStep step = step(request);
			clue = log.clue(step, request.level());
			GoalType<Goal> type = GoalTypes.of(request.goal());
			needed = type.needed(request.goal());
			countable = type.countable();
			GoalEvaluator.Tracker tracker = trackers.get(open.villager);
			status = open.done ? name + "에게 말해요" : tracker == null ? "" : log.status(step, tracker);
		}
		return new QuestEngine.TrackView(ROW_PREFIX + open.villager, titleKo,
				I18n.t(Lang.DE, "hangulquest.ui.request.title", name), I18n.t(Lang.EN, "hangulquest.ui.request.title", name),
				"request", request.level(), clue, I18n.t(request.titleKey()), status,
				open.done ? needed : open.progress, needed, countable, false, 0, 1);
	}

	/** "선인장을 들고 주민에게 말을 걸어요": hold the item and talk to the villager. */
	String showClue(Request request) {
		String item = itemKo.apply(request.showItem());
		if (item == null || item.isBlank()) {
			return "주민에게 말을 걸어요";
		}
		char last = item.charAt(item.length() - 1);
		boolean batchim = last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
		return item + (batchim ? "을" : "를") + " 들고 주민에게 말을 걸어요";
	}
}
