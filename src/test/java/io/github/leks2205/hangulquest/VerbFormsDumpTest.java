package io.github.leks2205.hangulquest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes {@code build/reports/content/verb-forms.json}: every surface form the Conjugator makes for each
 * verb the game knows (the verb table, the ACTIONS concepts, the curriculum's core verbs). The coverage
 * tool (tools/coverage.py) reads it so that 걸어요 counts for 걷다, 구워요 for 굽다, 쌓아요 for 쌓다.
 */
class VerbFormsDumpTest {
	static final Path DUMP = Path.of("build", "reports", "content", "verb-forms.json");

	/** Dictionary forms to export: the verb table, the action concepts and the core verbs of the curriculum. */
	static Set<String> verbs() throws IOException {
		Set<String> verbs = new TreeSet<>();
		for (Verb verb : Verb.values()) {
			verbs.add(verb.dictionary());
		}
		for (Concept concept : ConceptLoader.load()) {
			if ("ACTIONS".equals(concept.category) && concept.ko.endsWith("다") && !concept.ko.contains(" ")) {
				verbs.add(concept.ko);
			}
		}
		Path gap = Path.of("content", "curriculum", "core-gap.csv");
		if (Files.exists(gap)) {
			for (String line : Files.readAllLines(gap, StandardCharsets.UTF_8)) {
				String[] cells = line.split(",", -1);
				if (cells.length > 2 && (cells[2].equals("verb") || cells[2].equals("adjective"))
						&& cells[0].endsWith("다") && !cells[0].contains(" ")) {
					verbs.add(cells[0]);
				}
			}
		}
		return verbs;
	}

	/** Every finished surface form of one verb, from the Conjugator's own methods. */
	static Set<String> forms(String dictionary) throws ReflectiveOperationException {
		Conjugator.Word word = Conjugator.of(dictionary);
		Set<String> forms = new TreeSet<>();
		for (Method method : Conjugator.Word.class.getMethods()) {
			if (method.getParameterCount() == 0 && method.getReturnType() == String.class
					&& method.getDeclaringClass() == Conjugator.Word.class && !method.getName().equals("toString")) {
				String form = (String) method.invoke(word);
				if (form != null && !form.isBlank()) {
					forms.add(form);
				}
			}
		}
		return forms;
	}

	/** Writes the file; CoverageTest calls it too, so the shipped metrics never depend on test order. */
	static Map<String, Set<String>> write() throws Exception {
		Map<String, Set<String>> table = new TreeMap<>();
		for (String verb : verbs()) {
			table.put(verb, forms(verb));
		}
		JsonObject forms = new JsonObject();
		for (Map.Entry<String, Set<String>> entry : table.entrySet()) {
			JsonArray array = new JsonArray();
			entry.getValue().forEach(array::add);
			forms.add(entry.getKey(), array);
		}
		JsonObject root = new JsonObject();
		root.add("forms", forms);
		Files.createDirectories(DUMP.getParent());
		Files.writeString(DUMP, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root),
				StandardCharsets.UTF_8);
		return table;
	}

	@Test
	void writesTheFormsOfEveryKnownVerb() throws Exception {
		Map<String, Set<String>> table = write();

		// the irregular ones the tokenizer used to miss
		assertTrue(table.get("걷다").contains("걸어요"), "걷다 " + table.get("걷다"));
		assertTrue(table.get("굽다").contains("구워요"), "굽다 " + table.get("굽다"));
		assertTrue(table.get("쌓다").contains("쌓아요"), "쌓다 " + table.get("쌓다"));
		assertTrue(table.get("들다").contains("들어요"), "들다 " + table.get("들다"));
		assertTrue(table.get("올라가다").contains("올라가요"));
		assertTrue(table.get("들어가다").contains("들어가요"));
		assertEquals(List.of("들어가다"), List.of(Verb.ENTER.dictionary()));
	}

	@Test
	void theCoverageToolSeesTheIrregularFormsOfTheEnactedVerbs() throws Exception {
		writesTheFormsOfEveryKnownVerb();
		Process process = new ProcessBuilder("python3", "tools/coverage.py", "--tokens",
				"횃불을 들고 걸어요. 돌을 쌓았어요. 빵을 구워요. 언덕 위로 올라가요. 동굴로 들어가요.")
				.redirectErrorStream(true).start();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, process.waitFor(), out);
		List<String> words = List.of(out.split("\\R"));
		for (String verb : List.of("들다", "걷다", "쌓다", "굽다", "올라가다", "들어가다")) {
			assertTrue(words.contains(verb), verb + " not found in " + words);
		}
	}
}
