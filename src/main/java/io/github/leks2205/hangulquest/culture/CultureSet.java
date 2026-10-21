package io.github.leks2205.hangulquest.culture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.JsonFields;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The culture notes and the holiday table of data/culture.json. */
public final class CultureSet {
	public static final CultureSet EMPTY = new CultureSet(List.of(), Map.of());

	private final List<CultureNote> notes;
	private final Map<String, LunarHolidays> holidays;

	private CultureSet(List<CultureNote> notes, Map<String, LunarHolidays> holidays) {
		this.notes = notes;
		this.holidays = holidays;
	}

	/** Throws on a file that is not JSON or lacks its parts: culture is optional, the loader catches it. */
	public static CultureSet parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		Map<String, LunarHolidays> holidays = new LinkedHashMap<>();
		JsonObject table = root.has("holidays") ? root.getAsJsonObject("holidays") : new JsonObject();
		for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
			JsonObject holiday = entry.getValue().getAsJsonObject();
			List<LocalDate> dates = new ArrayList<>();
			JsonFields.strings(holiday, "dates").forEach(date -> dates.add(LocalDate.parse(date)));
			holidays.put(entry.getKey(), new LunarHolidays(entry.getKey(), JsonFields.integer(holiday, "before", 0),
					JsonFields.integer(holiday, "after", 0), dates));
		}
		List<CultureNote> notes = new ArrayList<>();
		for (JsonElement element : root.getAsJsonArray("notes")) {
			JsonObject note = element.getAsJsonObject();
			notes.add(new CultureNote(JsonFields.string(note, "id", ""), JsonFields.string(note, "titleKo", ""),
					JsonFields.string(note, "ko", ""), JsonFields.string(note, "icon", ""),
					JsonFields.strings(note, "anchors"), JsonFields.string(note, "holiday", "")));
		}
		return new CultureSet(List.copyOf(notes), Map.copyOf(holidays));
	}

	public List<CultureNote> notes() {
		return notes;
	}

	public Optional<CultureNote> byId(String id) {
		return notes.stream().filter(note -> note.id().equals(id)).findFirst();
	}

	public Optional<LunarHolidays> holiday(String id) {
		return Optional.ofNullable(holidays.get(id));
	}

	public Map<String, LunarHolidays> holidays() {
		return holidays;
	}
}
