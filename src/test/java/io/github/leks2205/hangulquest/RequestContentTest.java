package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.dialogue.DialogueLexicon;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.Request;
import io.github.leks2205.hangulquest.villager.RequestFixtures;
import io.github.leks2205.hangulquest.villager.RequestValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped villager requests: the game's checks, glosses, Korean and vocabulary coverage. */
class RequestContentTest {
	private final Catalog catalog = KoreanFixtures.catalog();
	private final List<Request> requests = RequestFixtures.shipped();
	private final Predicate<String> earlyWords = id -> catalog.byId(id)
			.filter(word -> word.conceptId != null && word.tier <= 2).isPresent();

	@Test
	void everyShippedRequestPassesTheGameChecks() {
		assertTrue(requests.size() >= 2, "expected the two sample requests");
		RequestValidator.Env env = RequestFixtures.env(catalog);
		List<String> problems = new ArrayList<>();
		for (Request request : requests) {
			problems.addAll(RequestValidator.validate(request, env));
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void everyRequestLineHasAnEnglishAndAGermanGloss() {
		Map<String, String> english = DialogueFixtures.lang("en_us");
		Map<String, String> german = DialogueFixtures.lang("de_de");
		List<String> missing = new ArrayList<>();
		for (Request request : requests) {
			List<String> keys = new ArrayList<>(List.of(request.titleKey(), request.answerKey(true), request.answerKey(false)));
			for (Personality personality : request.offeredBy()) {
				for (Request.Part part : Request.Part.values()) {
					keys.add(request.lineKey(personality, part));
				}
			}
			for (String key : keys) {
				if (english.getOrDefault(key, "").isBlank()) {
					missing.add(key + " (en)");
				}
				if (german.getOrDefault(key, "").isBlank()) {
					missing.add(key + " (de)");
				}
			}
		}
		assertTrue(missing.isEmpty(), "missing glosses: " + missing);
	}

	@Test
	void everyLineIsPlainHangulAndHasAtMostOneNewWord() {
		DialogueLexicon lexicon = DialogueFixtures.lexicon();
		DialogueCoverage.Vocabulary vocabulary = DialogueCoverage.vocabulary(catalog, lexicon, earlyWords, Lang.EN);
		List<String> broken = new ArrayList<>();
		for (Request request : requests) {
			for (RequestFixtures.Spoken line : RequestFixtures.lines(request)) {
				String where = request.id() + "." + line.node();
				if (!line.ko().matches("[가-힣 ,.!?]+") || !line.ko().matches(".*[.!?]")) {
					broken.add(where + ": \"" + line.ko() + "\" is not plain Hangul ending in . ! or ?");
				}
				List<DialogueCoverage.Token> tokens = DialogueCoverage.analyze(line.ko(), vocabulary);
				for (DialogueCoverage.Token token : tokens) {
					if (token.state() == DialogueCoverage.State.UNRESOLVED) {
						broken.add(where + ": '" + token.text() + "' matches no word");
					}
				}
				if (!DialogueCoverage.meetsRule(tokens)) {
					broken.add(where + ": \"" + line.ko() + "\" has " + DialogueCoverage.unknownWords(tokens) + " new words");
				}
			}
		}
		assertTrue(broken.isEmpty(), String.join("\n", broken));
	}
}
