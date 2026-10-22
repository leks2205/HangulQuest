package io.github.leks2205.hangulquest.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioServiceTest {
	/** Pretends the voice pack defines these events. */
	private static final class FakeBackend implements SoundBackend {
		final Set<String> events;
		final List<String> played = new ArrayList<>();
		boolean throwing;

		FakeBackend(String... events) {
			this.events = Set.of(events);
		}

		@Override
		public boolean has(String event) {
			if (throwing) {
				throw new IllegalStateException("sound engine is down");
			}
			return events.contains(event);
		}

		@Override
		public boolean hasAny(String prefix) {
			return events.stream().anyMatch(e -> e.startsWith(prefix));
		}

		@Override
		public void play(String event, float volume) {
			played.add(event);
		}
	}

	@Test
	void playsWordsThePackDefines() {
		FakeBackend backend = new FakeBackend("word.stone");
		AudioService audio = new AudioService(backend, () -> true);
		assertTrue(audio.playWord("stone"));
		assertEquals(List.of("word.stone"), backend.played);
	}

	@Test
	void doesNothingWithoutThePack() {
		FakeBackend backend = new FakeBackend();
		AudioService audio = new AudioService(backend, () -> true);
		assertFalse(audio.packLoaded());
		assertFalse(audio.available());
		assertFalse(audio.playWord("stone"));
		assertTrue(backend.played.isEmpty());
	}

	@Test
	void unknownWordIsSilentEvenWithThePack() {
		FakeBackend backend = new FakeBackend("word.stone");
		AudioService audio = new AudioService(backend, () -> true);
		assertTrue(audio.packLoaded());
		assertFalse(audio.playWord("dirt"));
		assertTrue(backend.played.isEmpty());
	}

	@Test
	void settingOffSilencesEverything() {
		FakeBackend backend = new FakeBackend("word.stone");
		AudioService audio = new AudioService(backend, () -> false);
		assertFalse(audio.available());
		assertFalse(audio.playWord("stone"));
		assertTrue(backend.played.isEmpty());
	}

	@Test
	void badIdsNeverReachTheSoundEngine() {
		FakeBackend backend = new FakeBackend("word.stone");
		AudioService audio = new AudioService(backend, () -> true);
		assertFalse(audio.playWord(null));
		assertFalse(audio.playWord(""));
		assertFalse(audio.playWord("Stone"));
		assertFalse(audio.playWord("a b"));
		assertFalse(audio.playWord("../x"));
		assertTrue(backend.played.isEmpty());
	}

	@Test
	void aBrokenSoundSystemIsNotFatal() {
		FakeBackend backend = new FakeBackend("word.stone");
		backend.throwing = true;
		AudioService audio = new AudioService(backend, () -> true);
		assertFalse(audio.playWord("stone"));
	}

	// ------------------------------------------------------------------ the HUD's ambient plays

	/** A clock the test moves by hand. */
	private static final class Clock implements java.util.function.LongSupplier {
		long now = 1_000_000L;

		@Override
		public long getAsLong() {
			return now;
		}
	}

	@Test
	void ambientPlaysAreThrottledToOncePerTenSeconds() {
		FakeBackend backend = new FakeBackend("word.stone", "word.dirt");
		Clock clock = new Clock();
		AudioService audio = new AudioService(backend, () -> true, clock);
		assertTrue(audio.playAmbient("stone"));
		clock.now += AudioService.AMBIENT_GAP_MS - 1;
		assertFalse(audio.playAmbient("dirt"), "one millisecond early");
		assertFalse(audio.playAmbient("stone"));
		clock.now += 1;
		assertTrue(audio.playAmbient("dirt"));
		assertEquals(List.of("word.stone", "word.dirt"), backend.played);
		clock.now += 5_000;
		assertFalse(audio.playAmbient("stone"), "the gap starts again at every clip");
		assertEquals(2, backend.played.size());
	}

	@Test
	void aSilentAmbientCallDoesNotUseUpTheGap() {
		FakeBackend backend = new FakeBackend("word.stone");
		Clock clock = new Clock();
		boolean[] on = {false};
		AudioService audio = new AudioService(backend, () -> on[0], clock);
		assertFalse(audio.playAmbient("stone"), "setting off");
		on[0] = true;
		assertFalse(audio.playAmbient("dirt"), "no recording for dirt");
		assertTrue(audio.playAmbient("stone"), "neither call may have started the gap");
		assertEquals(List.of("word.stone"), backend.played);
	}

	@Test
	void ambientIsSilentWithoutThePackAndWithTheSettingOff() {
		FakeBackend empty = new FakeBackend();
		Clock clock = new Clock();
		assertFalse(new AudioService(empty, () -> true, clock).playAmbient("stone"));
		FakeBackend backend = new FakeBackend("word.stone");
		assertFalse(new AudioService(backend, () -> false, clock).playAmbient("stone"));
		assertTrue(empty.played.isEmpty() && backend.played.isEmpty());
	}

	@Test
	void playingByHandIsNotThrottledAndDoesNotBlockTheAmbient() {
		FakeBackend backend = new FakeBackend("word.stone", "word.dirt");
		Clock clock = new Clock();
		AudioService audio = new AudioService(backend, () -> true, clock);
		assertTrue(audio.playWord("stone"));
		assertTrue(audio.playWord("stone"));
		assertTrue(audio.playAmbient("dirt"));
		assertEquals(3, backend.played.size());
	}

	@Test
	void hasWordAsksThePackAndTheSetting() {
		FakeBackend backend = new FakeBackend("word.stone");
		boolean[] on = {true};
		AudioService audio = new AudioService(backend, () -> on[0]);
		assertTrue(audio.hasWord("stone"));
		assertFalse(audio.hasWord("dirt"));
		assertFalse(audio.hasWord(null));
		assertFalse(audio.hasWord("A b"));
		on[0] = false;
		assertFalse(audio.hasWord("stone"));
		backend.throwing = true;
		on[0] = true;
		assertFalse(audio.hasWord("stone"));
		assertTrue(backend.played.isEmpty());
	}

	@Test
	void soundForgeEventsAreFoundAndPlayedByExactName() {
		FakeBackend backend = new FakeBackend("hvpt.dt_w1.t.male1");
		AudioService audio = new AudioService(backend, () -> true);
		assertTrue(audio.hvptLoaded());
		assertTrue(audio.hasSound("hvpt.dt_w1.t.male1"));
		assertFalse(audio.hasSound("hvpt.dt_w1.t.sohee"));
		assertTrue(audio.playSound("hvpt.dt_w1.t.male1"));
		assertEquals(List.of("hvpt.dt_w1.t.male1"), backend.played);
	}

	@Test
	void aPackWithOnlyWordsHasNoSoundForge() {
		FakeBackend backend = new FakeBackend("word.stone");
		AudioService audio = new AudioService(backend, () -> true);
		assertTrue(audio.packLoaded());
		assertFalse(audio.hvptLoaded());
	}

	@Test
	void exactEventsStillObeyTheSettingAndTheNameRules() {
		FakeBackend backend = new FakeBackend("hvpt.dt_w1.t.male1");
		assertFalse(new AudioService(backend, () -> false).playSound("hvpt.dt_w1.t.male1"));
		AudioService audio = new AudioService(backend, () -> true);
		assertFalse(audio.hasSound("../hvpt.dt_w1.t.male1"));
		assertFalse(audio.hasSound("HVPT.dt"));
		assertFalse(audio.hasSound(null));
		assertTrue(backend.played.isEmpty());
	}
}
