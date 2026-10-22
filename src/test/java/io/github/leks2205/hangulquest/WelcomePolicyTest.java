package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.config.Config;
import io.github.leks2205.hangulquest.config.WelcomePolicy;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.WordProgress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomePolicyTest {
	private static ProgressData empty() {
		return new ProgressData();
	}

	private static ProgressData withWord() {
		ProgressData data = new ProgressData();
		data.words.put("concept:stone", new WordProgress());
		return data;
	}

	@Test
	void freshInstallSeesTheScreenOnTheFirstJoin() {
		Config config = new Config();
		assertTrue(WelcomePolicy.onFirstJoin(config, empty()));
		assertEquals(1, config.welcomeShown);
		assertFalse(config.welcomeDone);
		assertTrue(WelcomePolicy.canRemindLater(config));
	}

	@Test
	void remindMeLaterShowsItExactlyOnceMore() {
		Config config = new Config();
		assertTrue(WelcomePolicy.onFirstJoin(config, empty()));
		// "Remind me later" changes nothing in the config.
		assertTrue(WelcomePolicy.onFirstJoin(config, empty()), "second start shows the reminder");
		assertFalse(WelcomePolicy.canRemindLater(config), "no second reminder");
		assertFalse(WelcomePolicy.onFirstJoin(config, empty()), "third start stays quiet");
		assertFalse(WelcomePolicy.onFirstJoin(config, empty()));
	}

	@Test
	void theReminderStillShowsAfterThePlayerStartedPlaying() {
		Config config = new Config();
		assertTrue(WelcomePolicy.onFirstJoin(config, empty()));
		assertTrue(WelcomePolicy.onFirstJoin(config, withWord()),
				"words learned meanwhile must not cancel a requested reminder");
	}

	@Test
	void existingPlayersNeverSeeIt() {
		Config config = new Config();
		assertFalse(WelcomePolicy.onFirstJoin(config, withWord()));
		assertTrue(config.welcomeDone);
		assertEquals(0, config.welcomeShown);
		assertFalse(WelcomePolicy.onFirstJoin(config, empty()));
	}

	@Test
	void finishedQuestsAloneCountAsExistingPlayer() {
		ProgressData data = empty();
		data.quests.completed.put("abenteuerpfad", 1L);
		assertTrue(WelcomePolicy.isExistingPlayer(data));
		Config config = new Config();
		assertFalse(WelcomePolicy.onFirstJoin(config, data));
		assertTrue(config.welcomeDone);
	}

	@Test
	void anEmptyOrMissingProgressFileIsNotAnExistingPlayer() {
		assertFalse(WelcomePolicy.isExistingPlayer(empty()));
		assertFalse(WelcomePolicy.isExistingPlayer(null));
	}

	@Test
	void iCanReadItEndsTheScreenForGood() {
		Config config = new Config();
		assertTrue(WelcomePolicy.onFirstJoin(config, empty()));
		WelcomePolicy.confirmed(config);
		assertTrue(config.welcomeDone);
		assertFalse(WelcomePolicy.onFirstJoin(config, empty()));
		assertFalse(WelcomePolicy.canRemindLater(config));
	}

	@Test
	void dontShowAgainEndsTheScreenForGood() {
		Config config = new Config();
		assertTrue(WelcomePolicy.onFirstJoin(config, empty()));
		WelcomePolicy.dismissedForGood(config);
		assertFalse(WelcomePolicy.onFirstJoin(config, empty()));
	}
}
