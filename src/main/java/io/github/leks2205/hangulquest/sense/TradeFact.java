package io.github.leks2205.hangulquest.sense;

/**
 * A trade the player made on a merchant screen: the result slot was taken. {@code got} is what arrived,
 * {@code paid} the item that went into the payment slots (null when none shows). {@code merchant} is the
 * entity right-clicked in the five seconds before the screen opened, or null.
 */
public record TradeFact(String merchant, String got, int gotCount, String paid, int paidCount) {
}
