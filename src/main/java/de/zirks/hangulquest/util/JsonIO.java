package de.zirks.hangulquest.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Small JSON helper with atomic writes so a crash never leaves a half file behind. */
public final class JsonIO {
	public static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
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
		Files.createDirectories(path.getParent());
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temp, GSON.toJson(value), StandardCharsets.UTF_8);
		try {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException atomicUnsupported) {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
