package io.github.leks2205.hangulquest.ui.hvpt;

import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.hvpt.HvptPhase;
import io.github.leks2205.hangulquest.learn.hvpt.HvptProgress;
import io.github.leks2205.hangulquest.learn.hvpt.HvptScheduler;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSession;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSet;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSink;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSounds;
import io.github.leks2205.hangulquest.learn.hvpt.HvptTrial;

import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * What the sound forge screen shows and does, without drawing anything: whether it can run at all (and if not,
 * why), the progress page, the running session and the result. {@link HvptScreen} only paints this and passes
 * keys and clicks on.
 */
public final class HvptModel {
	/** Why the forge cannot run, in the order a player fixes it. */
	public enum Block { NONE, SOUND_OFF, NO_PACK, NO_FORGE_SOUNDS }

	public enum Stage { BLOCKED, INTRO, RUN, RESULT }

	/** The sound state the model needs besides playing: is the setting on, is the pack there, does it carry the forge. */
	public interface Audio extends HvptSounds {
		boolean settingOn();

		boolean packLoaded();

		boolean forgeLoaded();
	}

	private final HvptSet set;
	private final Audio audio;
	private final HvptSink sink;
	private final Supplier<ProgressData.HvptData> data;
	private final LongSupplier clock;
	private final Random random;
	private final ZoneId zone;
	private Stage stage;
	private HvptSession session;
	private HvptProgress progress;
	private boolean tooFew;
	private int listenedIndex = -1;

	public HvptModel(HvptSet set, Audio audio, HvptSink sink, Supplier<ProgressData.HvptData> data, LongSupplier clock,
					 Random random, ZoneId zone) {
		this.set = set;
		this.audio = audio;
		this.sink = sink;
		this.data = data;
		this.clock = clock;
		this.random = random;
		this.zone = zone;
		refresh();
	}

	public HvptSet set() {
		return set;
	}

	public Stage stage() {
		return stage;
	}

	/** Why nothing can be played; {@link Block#NONE} when it can. */
	public Block block() {
		if (set.isEmpty() || !audio.settingOn()) {
			return audio.settingOn() ? Block.NO_FORGE_SOUNDS : Block.SOUND_OFF;
		}
		if (!audio.packLoaded()) {
			return Block.NO_PACK;
		}
		return audio.forgeLoaded() ? Block.NONE : Block.NO_FORGE_SOUNDS;
	}

	/** True after a start found too few sounds in the pack for that phase. */
	public boolean tooFewSounds() {
		return tooFew;
	}

	public HvptProgress progress() {
		return progress;
	}

	public HvptSession session() {
		return session;
	}

	/** Re-reads the saved progress and goes back to the start page (or the block message). */
	public void refresh() {
		progress = HvptProgress.of(data.get(), set, clock.getAsLong(), zone);
		session = null;
		tooFew = false;
		stage = block() == Block.NONE ? Stage.INTRO : Stage.BLOCKED;
	}

	/** Which phases the start page offers right now. */
	public boolean canStart(HvptPhase phase) {
		if (stage != Stage.INTRO) {
			return false;
		}
		return switch (phase) {
			case PRE -> !progress.preDone();
			case TRAIN -> progress.preDone();
			case POST -> progress.postReady();
		};
	}

	/** Builds the trials from what the pack has and starts the run; false (and {@link #tooFewSounds()}) when there are none. */
	public boolean start(HvptPhase phase) {
		if (!canStart(phase)) {
			return false;
		}
		List<HvptTrial> trials = HvptScheduler.build(set, phase, audio::has, random);
		if (trials.isEmpty()) {
			tooFew = true;
			return false;
		}
		tooFew = false;
		session = new HvptSession(phase, trials, audio, sink, clock);
		listenedIndex = -1;
		stage = Stage.RUN;
		return true;
	}

	/** Once per game tick: plays a fresh trial on its own, and spaces the replay. */
	public void tick() {
		if (stage != Stage.RUN) {
			return;
		}
		if (session.state() == HvptSession.State.LISTENING && listenedIndex != session.index()) {
			listenedIndex = session.index();
			session.listen();
		}
		session.tick();
	}

	/** R: hear the trial again (while listening), or both sounds again (in the feedback). */
	public void replay() {
		if (stage != Stage.RUN) {
			return;
		}
		if (session.state() == HvptSession.State.LISTENING) {
			session.listen();
		} else {
			session.replay();
		}
	}

	/** An answer by position; during the feedback the same keys play that option's sound instead. */
	public void choose(int option) {
		if (stage != Stage.RUN) {
			return;
		}
		if (session.state() == HvptSession.State.LISTENING) {
			session.answer(option);
		} else if (session.state() == HvptSession.State.FEEDBACK && option >= 0 && option < session.current().options().size()) {
			session.playForm(session.current().options().get(option));
		}
	}

	/** Enter or Space: on from the feedback; at the end the run is over and the result shows. */
	public void advance() {
		if (stage == Stage.RUN && session.state() == HvptSession.State.FEEDBACK) {
			session.next();
			if (session.state() == HvptSession.State.DONE) {
				stage = Stage.RESULT;
				progress = HvptProgress.of(data.get(), set, clock.getAsLong(), zone);
			}
		}
	}

	/** Leaves a run early: what was answered stays in the log, but it counts as no finished run. */
	public void abandon() {
		if (stage == Stage.RUN) {
			refresh();
		}
	}
}
