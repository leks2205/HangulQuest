package io.github.leks2205.hangulquest.sense;

/** An item stack reduced to what the detectors compare: the item id and how many. */
public record StackView(String item, int count) {
	public static final StackView EMPTY = new StackView(null, 0);

	public boolean isEmpty() {
		return item == null || count <= 0;
	}
}
