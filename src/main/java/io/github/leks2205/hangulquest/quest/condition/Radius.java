package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;

/** Writes a radius the way the files spell it: a whole number when it is one. */
final class Radius {
	private Radius() {
	}

	static void write(JsonObject json, double radius) {
		if (radius == Math.rint(radius)) {
			json.addProperty("radius", (long) radius);
		} else {
			json.addProperty("radius", radius);
		}
	}
}
