package io.github.leks2205.hangulquest.sense;

import java.util.UUID;

/** The player handed {@code count} of {@code item} to an entity. */
public record GiveFact(String item, int count, String targetType, UUID targetUuid, Method method) {
	public enum Method {
		/** Right-click on an animal with its food. */
		FEED,
		/** Right-click on any other entity with an item that was then used up. */
		USE_WITH,
		/** The item was dropped with an entity close by. */
		DROP_NEAR,
		/** The item was paid to a merchant. */
		TRADE
	}
}
