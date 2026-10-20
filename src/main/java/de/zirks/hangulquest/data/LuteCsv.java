package de.zirks.hangulquest.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Lute's Anki-ready export: {@code term;translation;pronunciation;status;tags}.
 * Handles quoted fields (including newlines and semicolons inside quotes) and BOMs.
 */
public final class LuteCsv {
	public record Row(String term, String translation, String pronunciation, int status, String tags) {
	}

	private LuteCsv() {
	}

	public static List<Row> parseFile(Path file) throws IOException {
		return parse(Files.readString(file, StandardCharsets.UTF_8));
	}

	public static List<Row> parse(String text) {
		List<Row> rows = new ArrayList<>();
		List<List<String>> table = split(text);
		boolean first = true;
		for (List<String> fields : table) {
			if (fields.stream().allMatch(String::isBlank)) {
				continue;
			}
			if (first) {
				first = false;
				String maybeHeader = fields.isEmpty() ? "" : fields.get(0).trim().toLowerCase(java.util.Locale.ROOT);
				if (maybeHeader.equals("term")) {
					continue;
				}
			}
			String term = field(fields, 0);
			String translation = field(fields, 1);
			String pronunciation = field(fields, 2);
			int status = parseStatus(field(fields, 3));
			String tags = field(fields, 4);
			if (term.isBlank() && translation.isBlank()) {
				continue;
			}
			rows.add(new Row(term, translation, pronunciation, status, tags));
		}
		return rows;
	}

	private static String field(List<String> fields, int index) {
		return index < fields.size() ? fields.get(index) : "";
	}

	private static int parseStatus(String raw) {
		String value = raw.trim();
		if (value.isEmpty()) {
			return 0;
		}
		try {
			return Integer.parseInt(value.split("[\\s,]", 2)[0]);
		} catch (NumberFormatException broken) {
			return 0;
		}
	}

	/** Minimal semicolon CSV state machine (quotes may span lines). */
	static List<List<String>> split(String text) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean fieldStarted = false;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\uFEFF' && i == 0) {
				continue;
			}
			if (quoted) {
				if (c == '"') {
					if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
						field.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					field.append(c);
				}
				continue;
			}
			switch (c) {
				case '"' -> {
					quoted = true;
					fieldStarted = true;
				}
				case ';' -> {
					row.add(field.toString());
					field.setLength(0);
					fieldStarted = false;
				}
				case '\r' -> {
					// ignore, \n ends the row
				}
				case '\n' -> {
					row.add(field.toString());
					rows.add(row);
					row = new ArrayList<>();
					field.setLength(0);
					fieldStarted = false;
				}
				default -> {
					field.append(c);
					fieldStarted = true;
				}
			}
		}
		if (fieldStarted || !field.isEmpty() || !row.isEmpty()) {
			row.add(field.toString());
			rows.add(row);
		}
		return rows;
	}
}
