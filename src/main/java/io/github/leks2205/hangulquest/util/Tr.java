package io.github.leks2205.hangulquest.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Interface strings in both languages. The German text is the key, so a missing entry falls
 * back to German instead of showing an empty label.
 */
public final class Tr {
	private static final Map<String, String> ENGLISH = new HashMap<>();

	private Tr() {
	}

	private static void put(String de, String en) {
		ENGLISH.put(de, en);
	}

	static {
		// tabs and headings
		put("Info", "Info");
		put("Wörter", "Words");
		put("Lernen", "Study");
		put("Erfolge", "Achievements");
		put("Quests", "Quests");
		put("Optionen", "Settings");

		// study overview
		put("Lernsystem lädt…", "Loading the learning system…");
		put("Entdeckt ", "Discovered ");
		put("   Beherrscht ", "   Mastered ");
		put("Fällig ", "Due ");
		put("   Serie ", "   Streak ");
		put(" Tage (best ", " days (best ");
		put("Heute ", "Today ");
		put(" gesehen, ", " seen, ");
		put(" richtig", " correct");
		put("Stufe ", "Level ");
		put(" von 10", " of 10");
		put("Wort des Tages", "Word of the day");
		put("Wiederholen", "Review");
		put("Quests öffnen", "Open quests");
		put(" Wörter · Mausrad scrollt", " words · scroll to move");
		put("Wieder lernen", "Study again");
		put("Als bekannt", "Mark as known");
		put("Wird wieder gelernt.", "Marked for review.");
		put("Als bekannt markiert.", "Marked as known.");
		put("Zurück", "Back");
		put("Nichts fällig. Alles im grünen Bereich.", "Nothing due. All caught up.");
		put(" Karten fällig:", " cards due:");
		put("Jetzt wiederholen", "Review now");
		put("Wiederholungen erscheinen auch automatisch im Spiel.",
				"Reviews also appear automatically in game.");
		put("noch nicht entdeckt", "not discovered yet");
		put("gesehen ", "seen ");
		put(" · richtig ", " · correct ");
		put(" · falsch ", " · wrong ");
		put(" · bekannt", " · known");

		// quest tree and journal
		put("Quests laden…", "Loading quests…");
		put("Questbaum · ", "Quest tree · ");
		put(" Linien", " trails");
		put("✓ ▶ ○ ★  Rad:Zoom  Ziehen  F:Fit  ", "✓ ▶ ○ ★  wheel:zoom  drag  F:fit  ");
		put("✓ fertig", "✓ done");
		put("▶ aktiv", "▶ active");
		put("▸ im Log", "▸ in log");
		put("○ offen", "○ open");
		put("· gesperrt", "· locked");
		put(" · Stufe ", " · tier ");
		put(" · TOPIK ", " · TOPIK ");
		put("Vorher: ", "Before: ");
		put("   Danach: ", "   After: ");
		put("Im Log", "In log");
		put("Quest-Log ist voll (6)", "Quest log is full (6)");
		put("ins Log gelegt: ", "added to the log: ");
		put("aus dem Log genommen · Fortschritt bleibt", "removed from the log · progress kept");
		put("Fokus", "Focus");
		put("Fokus: ", "Focus: ");
		put("Starten", "Start");
		put("Gesperrt: ", "Locked: ");
		put("Keine Questlinie gewählt.", "No questline selected.");
		put("ab ", "from ");
		put(" Wörtern", " words");
		put("nach ", "after ");

		// settings
		put("Quest-Tracker", "Quest tracker");
		put("HUD-Karten", "HUD cards");
		put("Tooltips", "Tooltips");
		put("Auto-Quiz", "Auto quiz");
		put("Ton", "Sound");
		put("Deutsch in Quests", "Translations in quests");
		put("Übersetzungen in Quests", "Translations in quests");
		put("Sprache", "Language");
		put("Lute-Import", "Lute import");
		put("Fortschritt zurücksetzen", "Reset progress");
		put("Fortschritt zurückgesetzt (Backup angelegt).", "Progress reset (backup written).");
		put("Noch einmal klicken, um wirklich zu löschen.", "Click again to really delete.");
		put("Lute: ", "Lute: ");
		put("keine Datei", "no file");
		put(" Wörter importiert, ", " words imported, ");
		put(" übersprungen", " skipped");

		// feedback
		put("Richtig.", "Correct.");
		put("Die richtige Antwort steht oben.", "The right answer is at the top.");

		// quiz prompt and screen
		put("Quiz bereit: %s drücken", "Quiz ready: press %s");
		put("Mit %s öffnen", "Open with %s");
		put("%s: antworten · Esc: schließen", "%s: answer · Esc: close");
		put("Gerade ist nichts zu wiederholen.", "Nothing to review right now.");

		// achievements
		put("Erstes Wort", "First word");
		put("10 Wörter", "10 words");
		put("25 Wörter", "25 words");
		put("50 Wörter", "50 words");
		put("100 Wörter", "100 words");
		put("10 Wörter beherrscht", "10 words mastered");
		put("100 Wiederholungen", "100 reviews");
		put("3 Tage in Folge", "3 days in a row");
		put("7 Tage in Folge", "7 days in a row");
		put("30 Tage in Folge", "30 days in a row");
		put("10 Tiere", "10 animals");
		put("25 Blöcke", "25 blocks");
		put("Erste Questlinie", "First questline");
		put("Drei Questlinien", "Three questlines");
		put("5 Nebenquests", "5 sidequests");

		// categories
		put("Blöcke", "Blocks");
		put("Werkzeuge", "Tools");
		put("Nahrung", "Food");
		put("Tiere", "Animals");
		put("Umgebung", "Surroundings");
		put("Handlungen", "Actions");
		put("Materialien", "Materials");
		put("Kampf", "Combat");
		put("Sonstiges", "Other");
		put("Redewendung", "Phrase");

		// adaptive trail
		put("Der Wiederholungspfad", "The review path");
		put("Ziel erreicht · ", "Goal reached · ");
		put("Questline geschafft · ", "Questline complete · ");
		put("Nebenquest geschafft: ", "Sidequest complete: ");
		put("Questlinien neu geladen: ", "Questlines reloaded: ");

		// Hangul welcome screen
		put("Willkommen bei Hangul Quest", "Welcome to Hangul Quest");
		put("Hangul Quest geht davon aus, dass du Hangul, das koreanische Alphabet, schon lesen kannst. "
				+ "Es lehrt Wörter und Grammatik, nicht das Alphabet. "
				+ "Die meisten lernen Hangul in ein paar Stunden.",
				"Hangul Quest assumes you can already read Hangul, the Korean alphabet. "
				+ "It teaches words and grammar, not the alphabet. "
				+ "Most people learn Hangul in a few hours.");
		put("Kannst du das lesen?", "Can you read this?");
		put("Ich kann es lesen, los geht's", "I can read it, let's go");
		put("Noch nicht, wo kann ich es lernen?", "Not yet, where can I learn it?");
		put("Später erinnern", "Remind me later");
		put("Nicht mehr anzeigen", "Don't show this again");
		put("Kostenlos Hangul lernen (öffnet deinen Browser)", "Free ways to learn Hangul (opens your browser)");
		put("Über das Lesen von Hangul", "About reading Hangul");
	}

	/** German interface text, or its English version when the interface language is English. */
	public static String t(String de) {
		if (Lang.current() != Lang.DE) {
			String english = ENGLISH.get(de);
			if (english != null) {
				return english;
			}
		}
		return de;
	}

	/** True when the English interface has its own text for this key (used by the coverage test). */
	public static boolean hasEnglish(String de) {
		return ENGLISH.containsKey(de);
	}

	/** Same, for content that carries both languages already. */
	public static String pick(String en, String de) {
		if (Lang.current() == Lang.DE) {
			return de == null || de.isBlank() ? en : de;
		}
		return en == null || en.isBlank() ? de : en;
	}
}
