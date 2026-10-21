package io.github.leks2205.hangulquest.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Small JSON helper with atomic writes so a crash never leaves a half file behind. */
public final class JsonIO {
	public static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();
	/** Same settings without indentation, for large files that are written often. */
	public static final Gson COMPACT = new GsonBuilder()
			.disableHtmlEscaping()
			.create();

	private JsonIO() {
	}

	public static <T> T read(Path path, Class<T> type) throws IOException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, type);
		}
	}

	public static JsonObject readObject(Path path) throws IOException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	public static List<JsonObject> readArray(Path path) throws IOException {
		List<JsonObject> result = new ArrayList<>();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
			array.forEach(element -> result.add(element.getAsJsonObject()));
		}
		return result;
	}

	public static void write(Path path, Object value) throws IOException {
		writeString(path, GSON.toJson(value));
	}

	public static void writeCompact(Path path, JsonElement tree) throws IOException {
		writeString(path, COMPACT.toJson(tree));
	}

	/**
	 * Writes to a unique temp file, forces it to disk, then renames it over the target, so a crash or a
	 * second writer can never leave a truncated file behind.
	 */
	public static void writeString(Path path, String text) throws IOException {
		Files.createDirectories(path.getParent());
		Path temp = Files.createTempFile(path.getParent(), path.getFileName() + ".", ".tmp");
		try {
			try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
				while (bytes.hasRemaining()) {
					channel.write(bytes);
				}
				channel.force(true);
			}
			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicUnsupported) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}
}
