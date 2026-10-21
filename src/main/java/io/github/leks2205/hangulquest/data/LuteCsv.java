package io.github.leks2205.hangulquest.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Lute's Anki-ready export: {@code term;translation;pronunciation;status;tags}.
 * Handles quoted fields (including newlines and semicolons inside quotes) and BOMs. Files are
 * decoded leniently: UTF-8 first, then CP949 (Korean Windows) and windows-1252, so an export from
 * a spreadsheet in a legacy encoding still imports.
 */
public final class LuteCsv {
	public record Row(String term, String translation, String pronunciation, int status, String tags) {
	}

	/** Decoded file content and the name of the decoder that worked. */
	public record Decoded(String text, String charset) {
	}

	private static final Logger LOG = LoggerFactory.getLogger("hangulquest");
	/** Tried in this order after UTF-8; the JRE may lack the first one (it ships in jdk.charsets). */
	private static final List<String> LEGACY = List.of("x-windows-949", "windows-1252");

	private LuteCsv() {
	}

	public static List<Row> parseFile(Path file) throws IOException {
		Decoded decoded = decode(Files.readAllBytes(file));
		if (!decoded.charset().equals("UTF-8")) {
			LOG.warn("{} is not valid UTF-8, decoded as {}", file.getFileName(), decoded.charset());
		} else {
			LOG.info("{} decoded as UTF-8", file.getFileName());
		}
		return parse(decoded.text());
	}

	/**
	 * Decodes an export: honours a UTF-8 or UTF-16 byte order mark, otherwise strict UTF-8, then
	 * CP949, then windows-1252 (which accepts any byte). The BOM is removed.
	 */
	public static Decoded decode(byte[] bytes) {
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
				&& (bytes[2] & 0xFF) == 0xBF) {
			bytes = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
		} else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
			return new Decoded(stripBom(new String(bytes, 2, bytes.length - 2,
					StandardCharsets.UTF_16LE)), "UTF-16LE");
		} else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
			return new Decoded(stripBom(new String(bytes, 2, bytes.length - 2,
					StandardCharsets.UTF_16BE)), "UTF-16BE");
		}
		String text = strict(bytes, StandardCharsets.UTF_8);
		if (text != null) {
			return new Decoded(stripBom(text), "UTF-8");
		}
		for (String name : LEGACY) {
			if (!Charset.isSupported(name)) {
				continue;
			}
			Charset charset = Charset.forName(name);
			text = strict(bytes, charset);
			if (text != null) {
				return new Decoded(stripBom(text), charset.name());
			}
		}
		// Last resort: whatever cannot be mapped becomes U+FFFD instead of failing the import.
		return new Decoded(stripBom(new String(bytes, Charset.forName("windows-1252"))), "windows-1252");
	}

	private static String strict(byte[] bytes, Charset charset) {
		try {
			return charset.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException failed) {
			return null;
		}
	}

	private static String stripBom(String text) {
		return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
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
