package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Verb;

import java.util.List;

/**
 * What the item in the hand makes of an interaction with a creature. A click on a sheep with shears is shearing,
 * with a lead leashing, a click on an armadillo with a brush brushing, and shears on a wolf in armour take the
 * armour off: the same click without the tool is feeding or petting. The verbs follow the item, so the sentence
 * says what really happens. Armour put on a creature and dye put on a sheep are gifts with the item used up
 * ({@code GIVE} with {@code USE_WITH}), and have their verbs here too.
 */
public final class Tools {
	static final String SHEARS = "minecraft:shears";
	static final String LEAD = "minecraft:lead";
	static final String BRUSH = "minecraft:brush";

	private Tools() {
	}

	/** The verb of a click on {@code entity} with {@code held} in the hand, or null when the item changes nothing. */
	public static Verb interaction(String held, String entity) {
		if (held == null || entity == null) {
			return null;
		}
		return switch (held) {
			case SHEARS -> "minecraft:sheep".equals(entity) ? Verb.SHEAR : "minecraft:wolf".equals(entity) ? Verb.UNARMOUR : null;
			case LEAD -> leashable(entity) ? Verb.LEASH : null;
			case BRUSH -> "minecraft:armadillo".equals(entity) ? Verb.BRUSH : null;
			default -> null;
		};
	}

	/** The verb of an interaction under the conditions of the step: the first held item that has one. */
	public static Verb interaction(List<Condition> conditions, String entity) {
		for (Condition condition : conditions) {
			if (condition instanceof Condition.Holding holding) {
				Verb verb = interaction(holding.item(), entity);
				if (verb != null) {
					return verb;
				}
			}
		}
		return null;
	}

	/** The item the sentence of this verb holds in the hand, or null for a verb that needs none. */
	public static String tool(Verb verb) {
		return switch (verb) {
			case SHEAR, UNARMOUR -> SHEARS;
			case LEASH -> LEAD;
			case BRUSH -> Tools.BRUSH;
			default -> null;
		};
	}

	/** Whether the sentence of the goal already says that the item is held (the tool phrase: 가위로). */
	public static boolean saysHolding(String entity, Condition condition) {
		return condition instanceof Condition.Holding holding && interaction(holding.item(), entity) != null;
	}

	/** The verb of an item given to a creature and used up: armour put on, dye put on a sheep; null for a plain gift. */
	public static Verb gift(String item, String entity, String method) {
		if (item == null || entity == null || !"USE_WITH".equals(method)) {
			return null;
		}
		if (armourFor(item, entity)) {
			return Verb.DRESS;
		}
		return item.startsWith("minecraft:") && item.endsWith("_dye") && "minecraft:sheep".equals(entity) ? Verb.DYE : null;
	}

	/** Wolf armour on a wolf, horse armour on a horse. */
	public static boolean armourFor(String item, String entity) {
		return "minecraft:wolf_armor".equals(item) && "minecraft:wolf".equals(entity)
				|| item.endsWith("_horse_armor") && "minecraft:horse".equals(entity);
	}

	private static boolean leashable(String entity) {
		return !List.of("minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem",
				"minecraft:snow_golem", "minecraft:player").contains(entity);
	}
}
