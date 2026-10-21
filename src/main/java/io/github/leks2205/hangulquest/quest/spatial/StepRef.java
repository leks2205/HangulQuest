package io.github.leks2205.hangulquest.quest.spatial;

import java.util.regex.Pattern;

/**
 * An anchor that points at an earlier step of the trail instead of a block: {@code "@s2"} is the block
 * the player placed to finish step {@code s2}. Written "아까 놓은 횃불 옆에" in the sentence.
 */
public final class StepRef {
	/** The Korean level that has -(으)ㄴ before a noun (아까 놓은 횃불): L5.adn_past_eun. */
	public static final int MIN_LEVEL = 5;

	private static final Pattern FORMAT = Pattern.compile("@[A-Za-z0-9_-]+");

	private StepRef() {
	}

	public static boolean isRef(String spec) {
		return spec != null && spec.startsWith("@");
	}

	/** True for {@code @} followed by a step id. */
	public static boolean wellFormed(String spec) {
		return spec != null && FORMAT.matcher(spec).matches();
	}

	/** The step id of a reference: {@code s2} for {@code @s2}. */
	public static String stepId(String spec) {
		return spec.substring(1);
	}
}
