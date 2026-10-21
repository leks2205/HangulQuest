package io.github.leks2205.hangulquest.sense;

/** Something the player took from a result slot. {@code station} is the {@link Station#id()}. */
public record CraftFact(String item, int count, String station) {
}
