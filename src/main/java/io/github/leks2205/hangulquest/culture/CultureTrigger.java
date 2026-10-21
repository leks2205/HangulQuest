package io.github.leks2205.hangulquest.culture;

import io.github.leks2205.hangulquest.sense.CraftFact;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.story.MarkFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which culture notes are due. An item note is due the first time the player has crafted or holds one
 * of its anchor items; a holiday note is due once per occurrence while today is close to the real date. What was
 * shown is a mark per note: {@value #ONCE} for an item note, the date for a holiday note, so Seollal comes
 * back every year.
 */
public final class CultureTrigger {
	public static final String ONCE = "once";

	/** A note that should be shown now; {@code key} is what gets marked once it was. */
	public record Due(CultureNote note, String key) {
	}

	private CultureTrigger() {
	}

	/** The item ids the player crafted this tick or holds: the facts the anchors are matched against. */
	public static Set<String> itemsOf(Observation obs) {
		Set<String> items = new HashSet<>();
		for (CraftFact crafted : obs.crafted) {
			items.add(crafted.item());
		}
		// While joining, the inventory is still syncing: a stack that merely arrives is no first find.
		if (!obs.inventorySettling) {
			for (Map.Entry<String, Integer> stack : obs.inventory.entrySet()) {
				if (stack.getValue() != null && stack.getValue() > 0) {
					items.add(stack.getKey());
				}
			}
		}
		return items;
	}

	public static List<Due> due(CultureSet set, Set<String> items, LocalDate today, MarkFile shown) {
		List<Due> due = new ArrayList<>();
		for (CultureNote note : set.notes()) {
			if (note.isHoliday()) {
				Optional<LocalDate> date = set.holiday(note.holiday()).flatMap(holiday -> holiday.activeOn(today));
				if (date.isPresent() && !shown.is(note.id(), date.get().toString())) {
					due.add(new Due(note, date.get().toString()));
				}
			} else if (!shown.is(note.id(), ONCE) && note.anchors().stream().anyMatch(items::contains)) {
				due.add(new Due(note, ONCE));
			}
		}
		return due;
	}
}
