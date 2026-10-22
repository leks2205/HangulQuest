package io.github.leks2205.hangulquest.learn.audio;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PitchTest {
	private static final int RATE = 24_000;

	/** A voiced-ish tone: the fundamental and two harmonics. */
	private static float[] tone(double hz, double seconds) {
		float[] samples = new float[(int) (RATE * seconds)];
		for (int i = 0; i < samples.length; i++) {
			double t = (double) i / RATE;
			samples[i] = (float) (0.6 * Math.sin(2 * Math.PI * hz * t) + 0.3 * Math.sin(4 * Math.PI * hz * t)
					+ 0.1 * Math.sin(6 * Math.PI * hz * t));
		}
		return samples;
	}

	@Test
	void findsThePitchOfASteadyTone() {
		for (double hz : new double[] {85, 120, 180, 220, 310}) {
			double found = Pitch.estimate(tone(hz, 0.05), RATE);
			assertEquals(hz, found, hz * 0.02, "tone of " + hz + " Hz");
		}
	}

	@Test
	void doesNotJumpAnOctave() {
		double found = Pitch.estimate(tone(150, 0.05), RATE);
		assertTrue(found > 140 && found < 160, "got " + found);
	}

	@Test
	void silenceAndNoiseAreUnvoiced() {
		assertEquals(Pitch.UNVOICED, Pitch.estimate(new float[1200], RATE));
		Random random = new Random(1);
		float[] noise = new float[1200];
		for (int i = 0; i < noise.length; i++) {
			noise[i] = (float) (random.nextGaussian() * 0.3);
		}
		assertEquals(Pitch.UNVOICED, Pitch.estimate(noise, RATE));
	}

	@Test
	void aFrameThatIsTooShortIsUnvoiced() {
		assertEquals(Pitch.UNVOICED, Pitch.estimate(tone(120, 0.01), RATE));
	}

	@Test
	void theContourFollowsARisingPitch() {
		float[] low = tone(120, 0.15);
		float[] high = tone(200, 0.15);
		float[] both = new float[low.length + high.length];
		System.arraycopy(low, 0, both, 0, low.length);
		System.arraycopy(high, 0, both, low.length, high.length);
		double[] contour = Pitch.contour(both, RATE);
		assertTrue(contour.length > 20);
		assertEquals(120, contour[2], 3);
		assertEquals(200, contour[contour.length - 3], 4);
	}

	@Test
	void theOnsetIsTheMeanOfTheFirstVoicedFrames() {
		double[] contour = {0, 0, 100, 110, 120, 300};
		assertEquals(105, Pitch.onset(contour, 2), 1e-9);
		assertEquals(Pitch.UNVOICED, Pitch.onset(new double[] {0, 0}, 3));
	}
}
