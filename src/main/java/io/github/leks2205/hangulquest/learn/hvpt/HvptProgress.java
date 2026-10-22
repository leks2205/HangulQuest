package io.github.leks2205.hangulquest.learn.hvpt;

import io.github.leks2205.hangulquest.learn.ProgressData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Where the player stands in the programme (a pre-test, 8 to 10 training runs over at least 3 weeks, a post-test),
 * read from the saved record. Accuracy is the share of right answers, 0 to 1; {@code null} where nothing was answered.
 */
public final class HvptProgress {
	/** Training runs pooled for the "lately" column. */
	public static final int RECENT_RUNS = 3;

	/** One contrast across the programme. */
	public record Line(String contrast, Double pre, Double recent, Double post) {
	}

	private final HvptSet.Params params;
	private final boolean preDone;
	private final boolean postDone;
	private final int trainingRuns;
	private final long startedAt;
	private final int daysSinceStart;
	private final boolean trainedToday;
	private final Double preAccuracy;
	private final Double recentAccuracy;
	private final Double postAccuracy;
	private final List<Line> lines;

	private HvptProgress(HvptSet.Params params, boolean preDone, boolean postDone, int trainingRuns, long startedAt,
						 int daysSinceStart, boolean trainedToday, Double preAccuracy, Double recentAccuracy,
						 Double postAccuracy, List<Line> lines) {
		this.params = params;
		this.preDone = preDone;
		this.postDone = postDone;
		this.trainingRuns = trainingRuns;
		this.startedAt = startedAt;
		this.daysSinceStart = daysSinceStart;
		this.trainedToday = trainedToday;
		this.preAccuracy = preAccuracy;
		this.recentAccuracy = recentAccuracy;
		this.postAccuracy = postAccuracy;
		this.lines = List.copyOf(lines);
	}

	public static HvptProgress of(ProgressData.HvptData data, HvptSet set, long now, ZoneId zone) {
		ProgressData.HvptData hvpt = data == null ? new ProgressData.HvptData() : data;
		List<ProgressData.HvptSession> all = new ArrayList<>(hvpt.sessions.values());
		all.sort(Comparator.comparingLong(session -> session.t));
		List<ProgressData.HvptSession> pre = phase(all, HvptPhase.PRE);
		List<ProgressData.HvptSession> train = phase(all, HvptPhase.TRAIN);
		List<ProgressData.HvptSession> post = phase(all, HvptPhase.POST);
		ProgressData.HvptSession firstPre = pre.isEmpty() ? null : pre.get(0);
		ProgressData.HvptSession lastPost = post.isEmpty() ? null : post.get(post.size() - 1);
		List<ProgressData.HvptSession> recent = train.subList(Math.max(0, train.size() - RECENT_RUNS), train.size());

		long started = firstPre != null ? firstPre.t : hvpt.startedAt;
		LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		int days = started == 0 ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(
				Instant.ofEpochMilli(started).atZone(zone).toLocalDate(), today));
		boolean trainedToday = !train.isEmpty()
				&& Instant.ofEpochMilli(train.get(train.size() - 1).t).atZone(zone).toLocalDate().equals(today);

		List<Line> lines = new ArrayList<>();
		for (HvptSet.Contrast contrast : set.contrasts()) {
			lines.add(new Line(contrast.id(), accuracy(firstPre, contrast.id()), pooled(recent, contrast.id()),
					accuracy(lastPost, contrast.id())));
		}
		return new HvptProgress(set.params(), firstPre != null, lastPost != null, train.size(), started, days,
				trainedToday, firstPre == null ? null : overall(firstPre), recent.isEmpty() ? null : pooledOverall(recent),
				lastPost == null ? null : overall(lastPost), lines);
	}

	private static List<ProgressData.HvptSession> phase(List<ProgressData.HvptSession> all, HvptPhase phase) {
		return all.stream().filter(session -> phase.name().equals(session.phase)).toList();
	}

	private static Double overall(ProgressData.HvptSession session) {
		return session.trials == 0 ? null : (double) session.correct / session.trials;
	}

	private static Double pooledOverall(List<ProgressData.HvptSession> sessions) {
		int trials = sessions.stream().mapToInt(session -> session.trials).sum();
		int correct = sessions.stream().mapToInt(session -> session.correct).sum();
		return trials == 0 ? null : (double) correct / trials;
	}

	private static Double accuracy(ProgressData.HvptSession session, String contrast) {
		return session == null ? null : pooled(List.of(session), contrast);
	}

	private static Double pooled(List<ProgressData.HvptSession> sessions, String contrast) {
		int trials = 0;
		int correct = 0;
		for (ProgressData.HvptSession session : sessions) {
			ProgressData.HvptTally tally = session.byContrast == null ? null : session.byContrast.get(contrast);
			if (tally != null) {
				trials += tally.trials;
				correct += tally.correct;
			}
		}
		return trials == 0 ? null : (double) correct / trials;
	}

	public boolean preDone() {
		return preDone;
	}

	public boolean postDone() {
		return postDone;
	}

	public int trainingRuns() {
		return trainingRuns;
	}

	public int daysSinceStart() {
		return daysSinceStart;
	}

	public boolean trainedToday() {
		return trainedToday;
	}

	public long startedAt() {
		return startedAt;
	}

	public Double preAccuracy() {
		return preAccuracy;
	}

	public Double recentAccuracy() {
		return recentAccuracy;
	}

	public Double postAccuracy() {
		return postAccuracy;
	}

	public List<Line> lines() {
		return lines;
	}

	public HvptSet.Params params() {
		return params;
	}

	/** Training runs still missing before the post-test makes sense. */
	public int runsUntilPost() {
		return Math.max(0, params.minSessions() - trainingRuns);
	}

	/** Days still missing before the post-test makes sense. */
	public int daysUntilPost() {
		return Math.max(0, params.weeks() * 7 - daysSinceStart);
	}

	/** The post-test follows enough practice spread over enough time. */
	public boolean postReady() {
		return preDone && runsUntilPost() == 0 && daysUntilPost() == 0;
	}

	/** What the main button starts: the pre-test first, training after that. */
	public HvptPhase next() {
		return preDone ? HvptPhase.TRAIN : HvptPhase.PRE;
	}

	/** Mean accuracy gain from pre to post in percentage points, or null before both exist. */
	public Double gain() {
		return preAccuracy == null || postAccuracy == null ? null : postAccuracy - preAccuracy;
	}

	/** Lifetime training accuracy of the contrast from the small per-contrast record, or null. */
	public static Double lifetime(ProgressData.HvptData data, String contrast) {
		Map<String, ProgressData.HvptTally> tallies = data == null ? null : data.contrasts;
		ProgressData.HvptTally tally = tallies == null ? null : tallies.get(contrast);
		return tally == null || tally.trials == 0 ? null : (double) tally.correct / tally.trials;
	}
}
