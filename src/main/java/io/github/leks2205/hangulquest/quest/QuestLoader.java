package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Parses questline JSON. In the game the files come from the resource manager (see
 * {@code QuestResources}); {@link #loadAll()} reads the same files from the classpath for tools and tests.
 */
public final class QuestLoader {
	public static final String BASE = "/assets/hangulquest/data/questlines/";

	private QuestLoader() {
	}

	/** The shipped trails, read from the classpath. */
	public static List<Questline> loadAll() {
		return QuestlineStore.readShipped(classpathFiles(), org.slf4j.LoggerFactory.getLogger("hangulquest")).trails();
	}

	/** File names of the shipped trails on the classpath, sorted. */
	public static List<String> index() {
		return new ArrayList<>(classpathFiles().keySet());
	}

	/** The shipped questline files on the classpath, name to bytes, sorted by name. */
	public static Map<String, byte[]> classpathFiles() {
		Map<String, byte[]> files = new TreeMap<>();
		try {
			var url = QuestLoader.class.getResource(BASE);
			if (url == null) {
				return files;
			}
			URI uri = url.toURI();
			if ("file".equals(uri.getScheme())) {
				collect(Path.of(uri), files);
			} else {
				collectFromJar(uri, files);
			}
		} catch (Exception broken) {
			logWarn("could not list the shipped questlines: " + broken);
		}
		return files;
	}

	private static void collectFromJar(URI uri, Map<String, byte[]> files) throws IOException {
		try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
			collect(jar.getPath(BASE), files);
		} catch (FileSystemAlreadyExistsException shared) {
			collect(FileSystems.getFileSystem(uri).getPath(BASE), files);
		}
	}

	private static void collect(Path directory, Map<String, byte[]> files) throws IOException {
		try (Stream<Path> stream = Files.list(directory)) {
			for (Path path : (Iterable<Path>) stream::iterator) {
				String name = path.getFileName().toString();
				if (QuestlineStore.isTrailFile(name)) {
					files.put(name, Files.readAllBytes(path));
				}
			}
		}
	}

	public static Questline parse(JsonObject root) {
		String id = string(root, "id", null);
		if (id == null || id.isBlank()) {
			return null;
		}
		List<QuestStep> steps = new ArrayList<>();
		if (root.has("steps")) {
			for (JsonElement element : root.getAsJsonArray("steps")) {
				QuestStep step = parseStep(element.getAsJsonObject());
				if (step != null) {
					steps.add(step);
				}
			}
		}
		QuestStep finale = root.has("finale") ? parseFinale(root.getAsJsonObject("finale")) : null;
		JsonObject finaleWrapper = root.has("finale") ? root.getAsJsonObject("finale") : null;
		String finaleKo = finaleWrapper != null ? string(finaleWrapper, "textKo", null) : null;
		// English and German ceremony text are lang keys (hangulquest.trail.<id>.finale); a questline
		// file may still carry them inline ("textDe" / "textEn" inside "finale"), and then they win.
		String finaleDe = finaleWrapper != null ? string(finaleWrapper, "textDe", null) : null;
		// A root "textEn" is the older spelling of the same inline field.
		String finaleEn = finaleWrapper != null ? string(finaleWrapper, "textEn", null) : null;
		if (finaleEn == null) {
			finaleEn = string(root, "textEn", null);
		}

		JsonObject requiresObject = root.has("requires") ? root.getAsJsonObject("requires") : null;
		Questline.Requires requires = requiresObject == null ? Questline.Requires.none()
				: new Questline.Requires(integer(requiresObject, "minWordsKnown", 0),
				string(requiresObject, "dimension", null),
				strings(requiresObject, "completed"));

		JsonObject reward = root.has("reward") ? root.getAsJsonObject("reward") : null;
		String badge = reward != null ? string(reward, "badge", id) : id;

		return new Questline(id,
				string(root, "titleKo", id),
				string(root, "titleDe", null),
				string(root, "titleEn", null),
				string(root, "topic", null),
				integer(root, "tier", 1),
				strings(root, "concepts"),
				requires,
				steps,
				finale,
				finaleKo == null ? "여기까지 왔어요. 수고했어요!" : finaleKo,
				finaleDe,
				finaleEn,
				badge,
				integer(root, "order", 1000),
				integer(root, "koreanLevel", integer(root, "tier", 1)),
				false);
	}

	private static QuestStep parseStep(JsonObject object) {
		String id = string(object, "id", null);
		JsonObject goalObject = object.has("goal") ? object.getAsJsonObject("goal") : null;
		if (id == null || goalObject == null) {
			return null;
		}
		Goal goal = parseGoal(goalObject);
		if (goal == null) {
			return null;
		}
		List<Condition> conditions = parseConditions(object);
		JsonObject clue = object.has("clue") ? object.getAsJsonObject("clue") : null;
		JsonObject hint = object.has("hint") ? object.getAsJsonObject("hint") : null;
		QuestStep.Bonus bonus = null;
		if (object.has("bonus")) {
			JsonObject bonusObject = object.getAsJsonObject("bonus");
			if ("NEAR_STRUCTURE".equals(string(bonusObject, "type", ""))) {
				bonus = new QuestStep.Bonus(string(bonusObject, "structure", ""),
						number(bonusObject, "radius", 48));
			}
		}
		return new QuestStep(id, strings(object, "teach"), goal, conditions,
				clue == null ? null : string(clue, "ko", null),
				clue == null ? null : string(clue, "de", null),
				clue == null ? null : string(clue, "en", null),
				hint == null ? null : string(hint, "ko", null),
				hint == null ? null : string(hint, "de", null),
				hint == null ? null : string(hint, "en", null),
				bonus);
	}

	private static QuestStep parseFinale(JsonObject object) {
		if (object == null) {
			return null;
		}
		JsonObject step = object.has("step") ? object.getAsJsonObject("step") : object;
		QuestStep parsed = parseStep(step);
		if (parsed == null) {
			return null;
		}
		if (!"finale".equals(parsed.id())) {
			parsed = parsed.withId("finale");
		}
		return parsed;
	}

	/** Reads a goal object; null for an unknown type. */
	public static Goal parseGoal(JsonObject object) {
		return GoalTypes.parse(object);
	}

	/** Reads a condition object; null for an unknown type. */
	public static Condition parseCondition(JsonObject object) {
		return ConditionTypes.parse(object);
	}

	private static List<Condition> parseConditions(JsonObject step) {
		List<Condition> conditions = new ArrayList<>();
		if (step.has("conditions")) {
			for (JsonElement element : step.getAsJsonArray("conditions")) {
				Condition condition = parseCondition(element.getAsJsonObject());
				if (condition != null) {
					conditions.add(condition);
				}
			}
		}
		return conditions;
	}

	private static String string(JsonObject object, String key, String fallback) {
		return JsonFields.string(object, key, fallback);
	}

	private static int integer(JsonObject object, String key, int fallback) {
		return JsonFields.integer(object, key, fallback);
	}

	private static double number(JsonObject object, String key, double fallback) {
		return JsonFields.number(object, key, fallback);
	}

	private static List<String> strings(JsonObject object, String key) {
		return JsonFields.strings(object, key);
	}

	private static void logWarn(String message) {
		org.slf4j.LoggerFactory.getLogger("hangulquest").warn(message);
	}
}
