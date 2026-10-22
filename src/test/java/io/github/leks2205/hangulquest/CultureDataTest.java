package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.culture.CultureNote;
import io.github.leks2205.hangulquest.culture.CultureSet;
import io.github.leks2205.hangulquest.culture.LunarHolidays;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.story.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** data/culture.json: the notes, the lunar holiday table for 2026 to 2030 and the language keys. */
class CultureDataTest {
	private final CultureSet culture = StoryFixtures.culture();
	private final Map<String, String> english = DialogueFixtures.lang("en_us");
	private final Map<String, String> german = DialogueFixtures.lang("de_de");

	@Test
	void theRoadmapNotesAreThere() {
		assertEquals(List.of("ondol", "hanok", "jangdok", "hanbok", "kimchi", "seollal", "chuseok", "yutnori"),
				culture.notes().stream().map(CultureNote::id).toList());
	}

	@Test
	void seollalAndChuseokHaveADateForEachYearFrom2026To2030() {
		for (String id : List.of("seollal", "chuseok")) {
			LunarHolidays holiday = culture.holiday(id).orElseThrow();
			assertEquals(List.of(2026, 2027, 2028, 2029, 2030), holiday.dates().stream().map(LocalDate::getYear).toList(), id);
		}
	}

	/**
	 * The dates and weekdays of the Korean calendar (Seollal is the 1st day of the 1st lunar month, Chuseok the 15th
	 * of the 8th, both in Korea Standard Time, which is why Seollal 2028 is the 27th and not the 26th as in China).
	 * Typing a date wrongly cannot pass both columns.
	 */
	@Test
	void theDatesAreThoseOfTheKoreanCalendar() {
		Object[][] seollal = {{"2026-02-17", DayOfWeek.TUESDAY}, {"2027-02-07", DayOfWeek.SUNDAY},
				{"2028-01-27", DayOfWeek.THURSDAY}, {"2029-02-13", DayOfWeek.TUESDAY}, {"2030-02-03", DayOfWeek.SUNDAY}};
		Object[][] chuseok = {{"2026-09-25", DayOfWeek.FRIDAY}, {"2027-09-15", DayOfWeek.WEDNESDAY},
				{"2028-10-03", DayOfWeek.TUESDAY}, {"2029-09-22", DayOfWeek.SATURDAY}, {"2030-09-12", DayOfWeek.THURSDAY}};
		check("seollal", seollal);
		check("chuseok", chuseok);
	}

	private void check(String id, Object[][] expected) {
		List<LocalDate> dates = culture.holiday(id).orElseThrow().dates();
		for (int i = 0; i < expected.length; i++) {
			assertEquals(LocalDate.parse((String) expected[i][0]), dates.get(i), id + " " + i);
			assertEquals(expected[i][1], dates.get(i).getDayOfWeek(), id + " " + dates.get(i));
		}
	}

	@Test
	void theDatesFallWhereTheLunarMonthsPutThem() {
		for (LocalDate date : culture.holiday("seollal").orElseThrow().dates()) {
			// the 1st lunar month starts between January 21 and February 20
			assertTrue(!date.isBefore(LocalDate.of(date.getYear(), 1, 21)) && !date.isAfter(LocalDate.of(date.getYear(), 2, 20)), date.toString());
		}
		for (LocalDate date : culture.holiday("chuseok").orElseThrow().dates()) {
			// the 15th of the 8th lunar month falls between September 8 and October 7
			assertTrue(!date.isBefore(LocalDate.of(date.getYear(), 9, 8)) && !date.isAfter(LocalDate.of(date.getYear(), 10, 7)), date.toString());
		}
	}

	@Test
	void everyHolidayNoteNamesAHolidayOfTheTable() {
		for (CultureNote note : culture.notes()) {
			if (note.isHoliday()) {
				assertTrue(culture.holiday(note.holiday()).isPresent(), note.id());
				assertTrue(note.anchors().isEmpty(), note.id() + " is date-driven");
			} else {
				assertFalse(note.anchors().isEmpty(), note.id() + " needs an anchor item");
			}
		}
	}

	@Test
	void everyAnchorIsAnItemOrBlockOfTheGame() throws IOException {
		JsonObject names = JsonParser.parseString(Files.readString(
				Path.of("src/main/resources/assets/hangulquest/data/names.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		for (CultureNote note : culture.notes()) {
			for (String anchor : note.anchors()) {
				String name = anchor.substring("minecraft:".length());
				assertTrue(names.has("item.minecraft." + name) || names.has("block.minecraft." + name), note.id() + ": " + anchor);
			}
			assertTrue(note.icon().startsWith("minecraft:"), note.id());
		}
	}

	@Test
	void everyNoteHasAKoreanSummaryAndEnglishAndGermanTexts() {
		for (CultureNote note : culture.notes()) {
			assertFalse(note.titleKo().isBlank(), note.id());
			assertTrue(note.ko().matches(".*[가-힣].*") && note.ko().length() <= 60, note.id() + ": " + note.ko());
			for (String key : Set.of(note.titleKey(), note.summaryKey(), note.textKey())) {
				assertTrue(english.containsKey(key), "missing English " + key);
				assertTrue(german.containsKey(key), "missing German " + key);
			}
		}
	}

	@Test
	void theExplanationIsTwoOrThreeSentencesInBothLanguages() {
		for (CultureNote note : culture.notes()) {
			for (Map<String, String> language : List.of(english, german)) {
				String text = language.get(note.textKey());
				int sentences = text.split("(?<=[^0-9][.!?])\\s+(?=[A-ZÄÖÜ])").length;
				assertTrue(sentences >= 2 && sentences <= 3, note.id() + " has " + sentences + " sentences: " + text);
			}
		}
	}
}
