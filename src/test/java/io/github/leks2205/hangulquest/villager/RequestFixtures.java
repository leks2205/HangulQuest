package io.github.leks2205.hangulquest.villager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** The shipped request files as the tests see them, read from the source tree. */
public final class RequestFixtures {
	public static final Path DIR = Path.of("src/main/resources/assets/hangulquest/requests");
	private static final Path NAMES = Path.of("src/main/resources/assets/hangulquest/data/names.json");

	private RequestFixtures() {
	}

	public static Map<String, byte[]> files() {
		Map<String, byte[]> files = new TreeMap<>();
		if (!Files.isDirectory(DIR)) {
			return files;
		}
		try (Stream<Path> listing = Files.list(DIR)) {
			for (Path file : listing.toList()) {
				String name = file.getFileName().toString();
				if (name.endsWith(".json") && !name.startsWith("_")) {
					files.put(name.substring(0, name.length() - ".json".length()), Files.readAllBytes(file));
				}
			}
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
		return files;
	}

	/** Every shipped request, parsed; fails the test on a parse problem. */
	public static List<Request> shipped() {
		List<Request> requests = new ArrayList<>();
		for (Map.Entry<String, byte[]> file : files().entrySet()) {
			RequestParser.Parsed parsed = RequestParser.parse(file.getKey(), file.getValue());
			if (!parsed.problems().isEmpty() || parsed.request() == null) {
				throw new AssertionError(file.getKey() + ": " + parsed.problems());
			}
			requests.add(parsed.request());
		}
		return requests;
	}

	/** True for an item or block id the game names (names.json). */
	public static java.util.function.Predicate<String> itemExists() {
		try {
			JsonObject names = JsonParser.parseString(Files.readString(NAMES, StandardCharsets.UTF_8)).getAsJsonObject();
			return id -> {
				String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : null;
				return path != null && (names.has("item.minecraft." + path) || names.has("block.minecraft." + path));
			};
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}

	/** The checks the game makes when it loads, against the real catalog, items and English file. */
	public static RequestValidator.Env env(Catalog catalog) {
		Map<String, String> english = DialogueFixtures.lang("en_us");
		return new RequestValidator.Env(catalog, id -> catalog.byId(id).isPresent(), english::containsKey, itemExists());
	}

	/** Every Korean line of a request with where it sits: id suffix (node, node.banmal, yes, no), pointer and text. */
	public record Spoken(String node, String pointer, String ko, String speaker, String level) {
	}

	public static List<Spoken> lines(Request request) {
		List<Spoken> lines = new ArrayList<>();
		for (Personality personality : request.lines().keySet()) {
			for (Request.Part part : Request.Part.values()) {
				Request.Line line = request.line(personality, part);
				if (line == null) {
					continue;
				}
				String node = Request.nodeId(personality, part);
				String pointer = "lines." + personality.name() + "." + part.id();
				boolean object = line.koBanmal() != null;
				lines.add(new Spoken(node, pointer + (object ? ".ko" : ""), line.ko(), "villager",
						personality.register().id()));
				if (object) {
					lines.add(new Spoken(node + ".banmal", pointer + ".ko_banmal", line.koBanmal(), "villager", "banmal"));
				}
			}
		}
		lines.add(new Spoken("yes", "answers.yes", request.yes().ko(), "player", "haeyo"));
		lines.add(new Spoken("no", "answers.no", request.no().ko(), "player", "haeyo"));
		return lines;
	}
}
