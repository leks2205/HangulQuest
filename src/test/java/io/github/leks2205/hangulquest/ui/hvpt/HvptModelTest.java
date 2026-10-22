package io.github.leks2205.hangulquest.ui.hvpt;

import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.hvpt.HvptPhase;
import io.github.leks2205.hangulquest.learn.hvpt.HvptRecorder;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSession;
import io.github.leks2205.hangulquest.learn.hvpt.HvptSet;
import io.github.leks2205.hangulquest.learn.hvpt.HvptTrial;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The screen without drawing: what it offers, what a key does, where an answer ends up. */
class HvptModelTest {
	private static final class FakeAudio implements HvptModel.Audio {
		boolean setting = true;
		boolean pack = true;
		boolean forge = true;
		Predicate<String> has = event -> true;
		final List<String> played = new ArrayList<>();

		@Override
		public boolean settingOn() {
			return setting;
		}

		@Override
		public boolean packLoaded() {
			return pack;
		}

		@Override
		public boolean forgeLoaded() {
			return forge;
		}

		@Override
		public boolean has(String event) {
			return has.test(event);
		}

		@Override
		public void play(String event) {
			played.add(event);
		}
	}

	private final HvptSet set = HvptSet.shared();
	private final ProgressData data = new ProgressData();
	private final FakeAudio audio = new FakeAudio();
	private long now = 1_792_300_000_000L;

	private HvptModel model() {
		HvptRecorder recorder = new HvptRecorder(data, ReviewLog.disabled(), () -> { });
		return new HvptModel(set, audio, recorder, () -> data.hvpt, () -> now, new Random(7), ZoneOffset.UTC);
	}

	/** Answers every trial of the running session; right when {@code right}, otherwise the first wrong option. */
	private static void playThrough(HvptModel model, boolean right) {
		while (model.stage() == HvptModel.Stage.RUN) {
			HvptTrial trial = model.session().current();
			int option = -1;
			for (int i = 0; i < trial.options().size(); i++) {
				if (trial.options().get(i).equals(trial.target()) == right) {
					option = i;
					break;
				}
			}
			model.choose(option);
			model.advance();
		}
	}

	@Test
	void withoutThePackItExplainsInsteadOfStarting() {
		audio.pack = false;
		audio.forge = false;
		HvptModel model = model();
		assertEquals(HvptModel.Stage.BLOCKED, model.stage());
		assertEquals(HvptModel.Block.NO_PACK, model.block());
		assertFalse(model.start(HvptPhase.PRE));
		assertEquals(HvptModel.Stage.BLOCKED, model.stage());
	}

	@Test
	void anOldPackWithoutTheForgeSoundsSaysSo() {
		audio.forge = false;
		assertEquals(HvptModel.Block.NO_FORGE_SOUNDS, model().block());
	}

	@Test
	void theSoundSettingComesFirst() {
		audio.setting = false;
		audio.pack = false;
		assertEquals(HvptModel.Block.SOUND_OFF, model().block());
	}

	@Test
	void withThePackTheStartPageOpensWithThePreTestFirst() {
		HvptModel model = model();
		assertEquals(HvptModel.Stage.INTRO, model.stage());
		assertTrue(model.canStart(HvptPhase.PRE));
		assertFalse(model.canStart(HvptPhase.TRAIN), "the listening test comes first");
		assertFalse(model.canStart(HvptPhase.POST));
	}

	@Test
	void aPackThatIsMissingTheHeldOutVoicesCannotRunTheTest() {
		audio.has = event -> set.trainingVoices().stream().anyMatch(voice -> event.endsWith("." + voice));
		HvptModel model = model();
		assertFalse(model.start(HvptPhase.PRE));
		assertTrue(model.tooFewSounds());
		assertEquals(HvptModel.Stage.INTRO, model.stage());
	}

	@Test
	void aSoundIsPlayedByItselfWhenATrialAppearsAndKeysDriveTheRest() {
		HvptModel model = model();
		assertTrue(model.start(HvptPhase.PRE));
		assertEquals(HvptModel.Stage.RUN, model.stage());
		assertTrue(audio.played.isEmpty());

		model.tick();
		HvptTrial first = model.session().current();
		assertEquals(List.of(first.targetEvent()), audio.played);
		model.tick();
		assertEquals(1, audio.played.size(), "heard once, not on every tick");

		model.replay();
		assertEquals(2, audio.played.size(), "R plays it again");

		model.choose(0);
		assertEquals(HvptSession.State.FEEDBACK, model.session().state());
		model.advance();
		assertEquals(1, model.session().index());
		model.tick();
		assertEquals(model.session().current().targetEvent(), audio.played.get(audio.played.size() - 1),
				"the next trial plays by itself");
	}

	@Test
	void aWholePreTestLeavesItsScoreAndOpensTraining() {
		HvptModel model = model();
		model.start(HvptPhase.PRE);
		playThrough(model, true);
		assertEquals(HvptModel.Stage.RESULT, model.stage());
		assertEquals(30, model.session().result().trials());
		assertEquals(30, model.session().result().correct());
		assertEquals(1, data.hvpt.sessions.size());
		assertTrue(model.progress().preDone());
		assertEquals(1.0, model.progress().preAccuracy(), 1e-9);

		model.refresh();
		assertEquals(HvptModel.Stage.INTRO, model.stage());
		assertTrue(model.canStart(HvptPhase.TRAIN));
		assertFalse(model.canStart(HvptPhase.PRE), "the baseline is taken once");
	}

	@Test
	void aTrainingRunAddsToTheContrastTallies() {
		HvptModel model = model();
		model.start(HvptPhase.PRE);
		playThrough(model, false);
		model.refresh();
		assertTrue(model.start(HvptPhase.TRAIN));
		assertEquals(60, model.session().size());
		playThrough(model, true);
		assertEquals(60, data.hvpt.contrasts.values().stream().mapToInt(tally -> tally.trials).sum());
		assertEquals(60, data.hvpt.contrasts.values().stream().mapToInt(tally -> tally.correct).sum());
		assertEquals(2, data.hvpt.sessions.size());
		assertEquals(0.0, model.progress().preAccuracy(), 1e-9);
		assertEquals(1.0, model.progress().recentAccuracy(), 1e-9);
	}

	@Test
	void leavingARunEarlyKeepsNoFinishedSession() {
		HvptModel model = model();
		model.start(HvptPhase.PRE);
		model.choose(0);
		model.abandon();
		assertEquals(HvptModel.Stage.INTRO, model.stage());
		assertTrue(data.hvpt.sessions.isEmpty());
		assertTrue(model.canStart(HvptPhase.PRE), "the test is still to do");
	}

	@Test
	void duringTheFeedbackTheNumberKeysHearEachOption() {
		HvptModel model = model();
		model.start(HvptPhase.PRE);
		model.tick();
		model.choose(0);
		audio.played.clear();
		HvptTrial trial = model.session().current();
		model.choose(1);
		assertEquals(List.of(trial.event(trial.options().get(1))), audio.played);
		assertEquals(HvptSession.State.FEEDBACK, model.session().state(), "still on the same trial");
		assertNotNull(model.session().feedback());
	}

	@Test
	void thePostTestOpensWhenTheProgrammeIsDone() {
		HvptModel model = model();
		model.start(HvptPhase.PRE);
		playThrough(model, false);
		model.refresh();
		for (int i = 0; i < set.params().minSessions(); i++) {
			now += 86_400_000L;
			model.start(HvptPhase.TRAIN);
			playThrough(model, true);
			model.refresh();
		}
		assertFalse(model.canStart(HvptPhase.POST), "the weeks have not passed");
		now += 21L * 86_400_000L;
		model.refresh();
		assertTrue(model.canStart(HvptPhase.POST));
		model.start(HvptPhase.POST);
		playThrough(model, true);
		assertEquals(1.0, model.progress().gain() + model.progress().preAccuracy(), 1e-9);
		assertTrue(model.progress().postDone());
	}
}
