package de.zirks.hangulquest.quest;

/** Continuous gates: a goal only makes progress while all conditions hold. */
public sealed interface Condition {
	enum Phase {
		DAY, NIGHT, DAWN, DUSK
	}

	enum Weather {
		CLEAR, RAIN, THUNDER
	}

	record Time(Phase phase) implements Condition {
	}

	record WeatherState(Weather weather) implements Condition {
	}

	record YRange(int minY, int maxY) implements Condition {
	}

	record NearEntity(String entity, double radius) implements Condition {
	}

	record NearBlock(String block, double radius) implements Condition {
	}

	record InDimension(String dimension) implements Condition {
	}

	record InBiome(String biome) implements Condition {
	}
}
