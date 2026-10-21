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
}
