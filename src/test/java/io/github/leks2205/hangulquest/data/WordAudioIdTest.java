package io.github.leks2205.hangulquest.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The voice pack event a word plays: its concept, or for a bare game name the vanilla id. */
class WordAudioIdTest {
	private static Word word(String id, String conceptId) {
		return new Word(id, "자작나무 계단", "Birkentreppe", "Birkentreppe", Category.BLOCKS, 2,
				conceptId, null, null, null, null, Origin.MINECRAFT, false, null);
	}

	@Test
	void aConceptPlaysItsOwnClip() {
		assertEquals("stone", word("concept:stone", "stone").audioId());
	}

	@Test
	void aGameNamePlaysTheClipOfItsVanillaId() {
		assertEquals("mc_birch_stairs", word("mc:item:minecraft:birch_stairs", null).audioId());
		assertEquals("mc_entity_glow_squid", word("mc:entity:minecraft:glow_squid", null).audioId());
	}

	@Test
	void aWordFromAnotherModOrLuteHasNoClip() {
		assertNull(word("mc:item:othermod:gadget", null).audioId());
		assertNull(word("lute:나무", null).audioId());
	}
}
