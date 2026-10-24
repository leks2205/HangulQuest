package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.villager.RequestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The authors' check of drafted conversations and requests (tools/validate_dialogue_drafts.py). */
class DraftValidatorTest {
	@TempDir
	Path dir;

	/** The shipped samples as drafts, with their glosses in the drafts' language files. */
	private void copySamples() throws IOException {
		Files.createDirectories(dir.resolve("dialogue"));
		Files.createDirectories(dir.resolve("requests"));
		Files.createDirectories(dir.resolve("lang"));
		for (String id : List.of("vt_lazy_plains_intro", "vt_peppy_gen_01", "vt_cranky_ms3", "vt_jock_banmal")) {
			Files.copy(DialogueFixtures.DIR.resolve(id + ".json"), dir.resolve("dialogue/" + id + ".json"));
		}
		for (String id : List.of("req_plains_01", "req_desert_01")) {
			Files.copy(RequestFixtures.DIR.resolve(id + ".json"), dir.resolve("requests/" + id + ".json"));
		}
		for (String code : List.of("en_us", "de_de")) {
			Map<String, String> glosses = DialogueFixtures.lang(code).entrySet().stream()
					.filter(entry -> entry.getKey().startsWith("hangulquest.dialogue.vt_")
							|| entry.getKey().startsWith("hangulquest.dialogue.req_")
							|| entry.getKey().startsWith("hangulquest.request."))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			Files.writeString(dir.resolve("lang/" + code + ".json"),
					io.github.leks2205.hangulquest.util.JsonIO.GSON.toJson(glosses), StandardCharsets.UTF_8);
		}
	}

	private static void assertProblem(DraftValidator.Result result, String text) {
		assertTrue(result.problems().stream().anyMatch(problem -> problem.toString().contains(text)),
				text + " in\n" + result.problems().stream().map(Object::toString).collect(Collectors.joining("\n")));
	}

	@Test
	void theShippedSamplesAreCleanDrafts() throws IOException {
		copySamples();
		DraftValidator.Result result = DraftValidator.check(dir);
		assertEquals(List.of(), result.problems());
		assertEquals(4, result.dialogues());
		assertEquals(2, result.requests());
		assertTrue(result.rows().stream().anyMatch(row -> row.id().equals("dialogue.req_plains_01.lazy_offer.banmal")));
		assertTrue(result.rows().stream().allMatch(row -> row.source().startsWith("draft:")));
	}

	@Test
	void everyProblemNamesTheFileAndTheNode() throws IOException {
		copySamples();
		Path talk = dir.resolve("dialogue/vt_peppy_gen_01.json");
		Files.writeString(talk, Files.readString(talk, StandardCharsets.UTF_8)
				.replace("저는 꽃이 정말 좋아요!", "서연은 꽃이면 좋아요!")
				.replace("\"vt_peppy_gen_01\"", "\"vt_peppy_gen_01\""), StandardCharsets.UTF_8);
		Path offer = dir.resolve("requests/req_plains_01.json");
		Files.writeString(offer, Files.readString(offer, StandardCharsets.UTF_8)
				.replace("\"minecraft:bread\", \"entity\"", "\"minecraft:diamond\", \"entity\"")
				.replace("\"offer\": \"흠. 빵이 필요해요.\"", "\"offer\": \"흠. 빵이 필요합니다.\""), StandardCharsets.UTF_8);
		Files.move(dir.resolve("dialogue/vt_cranky_ms3.json"), dir.resolve("dialogue/vt_cranky_milestone.json"));
		Files.writeString(dir.resolve("dialogue/vt_cranky_milestone.json"), Files.readString(
				dir.resolve("dialogue/vt_cranky_milestone.json"), StandardCharsets.UTF_8)
				.replace("\"vt_cranky_ms3\"", "\"vt_cranky_milestone\""), StandardCharsets.UTF_8);

		DraftValidator.Result result = DraftValidator.check(dir);
		assertProblem(result, "dialogue/vt_peppy_gen_01.json: flower: ");
		assertProblem(result, "says the name 서연");
		assertProblem(result, "(L4.myeon) is level 4, above the level 1");
		assertProblem(result, "requests/req_plains_01.json: file: req_plains_01: a villager does not pick up minecraft:diamond");
		assertProblem(result, "requests/req_plains_01.json: cranky_offer: req_plains_01.cranky_offer: CRANKY speaks haeyo");
		assertProblem(result, "dialogue/vt_cranky_milestone.json: id: the id \"vt_cranky_milestone\" should be vt_cranky_ms3");
		assertProblem(result, "no English gloss hangulquest.dialogue.vt_cranky_milestone.again");
	}

	@Test
	void newWordsComeWithTheDraftsLexicon() throws IOException {
		copySamples();
		Path talk = dir.resolve("dialogue/vt_peppy_gen_01.json");
		Files.writeString(talk, Files.readString(talk, StandardCharsets.UTF_8).replace("저는 꽃이 정말 좋아요!",
				"저는 솜사탕이 정말 좋아요!"), StandardCharsets.UTF_8);
		assertProblem(DraftValidator.check(dir), "'솜사탕이' in \"저는 솜사탕이 정말 좋아요!\" matches no word");
		Files.writeString(dir.resolve("_lexicon.json"), "{\"words\": [{\"id\": \"cotton_candy\", \"forms\": [\"솜사탕\"],"
				+ " \"known\": false, \"en\": \"cotton candy\", \"de\": \"Zuckerwatte\"}]}", StandardCharsets.UTF_8);
		assertEquals(List.of(), DraftValidator.check(dir).problems());
		Files.writeString(dir.resolve("_lexicon.json"), "{\"words\": [{\"id\": \"um\", \"forms\": [\"음\"], \"en\": \"\"}]}",
				StandardCharsets.UTF_8);
		DraftValidator.Result twice = DraftValidator.check(dir);
		assertProblem(twice, "_lexicon.json: um: a new word needs a new id");
		assertProblem(twice, "form \"음\" is already in the lexicon");
		assertProblem(twice, "needs an \"en\" gloss");
	}

	@Test
	void anEmptyFolderIsAProblemToo() {
		DraftValidator.Result result = DraftValidator.check(dir);
		assertProblem(result, "no drafted conversation or request found");
		assertProblem(result, "lang/en_us.json: file: missing");
	}
}
