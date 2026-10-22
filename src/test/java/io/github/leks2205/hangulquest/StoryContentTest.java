package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.story.ChapterSet;
import io.github.leks2205.hangulquest.story.Story;
import io.github.leks2205.hangulquest.story.StoryCoverage;
import io.github.leks2205.hangulquest.story.StoryFixtures;
import io.github.leks2205.hangulquest.story.StoryLexicon;
import io.github.leks2205.hangulquest.story.StoryText;
import io.github.leks2205.hangulquest.story.StoryValidator;
import io.github.leks2205.hangulquest.story.StoryVocabulary;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped stories: files, length, grammar level, vocabulary, coverage for this player and glosses. */
class StoryContentTest {
	private final Catalog catalog = KoreanFixtures.catalog();
	private final List<Story> stories = StoryFixtures.shipped();
	private final StoryLexicon lexicon = StoryFixtures.lexicon();
	private final Map<String, String> english = DialogueFixtures.lang("en_us");
	private final Map<String, String> german = DialogueFixtures.lang("de_de");

	private StoryVocabulary vocabulary(Predicate<String> knows) {
		return StoryVocabulary.of(catalog, lexicon, knows, Lang.EN);
	}

	private List<StoryText.Token> tokens(Story story, StoryVocabulary vocabulary) {
		List<StoryText.Token> tokens = new ArrayList<>();
		story.paragraphs().forEach(paragraph -> tokens.addAll(StoryText.analyze(paragraph, vocabulary)));
		return tokens;
	}

	@Test
	void everyShippedStoryParsesAndPassesTheGameChecks() {
		assertTrue(stories.size() >= 4, "expected the four first stories");
		ChapterSet chapters = StoryFixtures.chapters();
		StoryValidator.Env env = new StoryValidator.Env(
				chapters.all().stream().map(ChapterSet.Chapter::id).collect(Collectors.toSet()),
				DialogueFixtures.trailIds(), DialogueFixtures.grammarIds(), id -> catalog.byId(id).isPresent(),
				english::containsKey);
		List<String> problems = new ArrayList<>();
		stories.forEach(story -> problems.addAll(StoryValidator.validate(story, env)));
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void thereIsAStoryForEachOfTheFirstSevenChapters() {
		Set<String> chapters = stories.stream().map(Story::chapter).collect(Collectors.toSet());
		assertTrue(chapters.containsAll(Set.of("house", "fire_light", "field", "animals", "colors", "market", "meals")),
				chapters.toString());
		assertEquals(stories.size(), stories.stream().map(Story::id).distinct().count(), "story ids are unique");
	}

	@Test
	void theGradedStoriesReachLevelsThreeToSix() {
		Set<Integer> levels = stories.stream().map(Story::level).collect(Collectors.toSet());
		assertTrue(levels.containsAll(Set.of(1, 2, 3, 4, 5, 6)), levels.toString());
	}

	@Test
	void eachStoryIsEightyToFourHundredSyllables() {
		for (Story story : stories) {
			assertTrue(story.syllables() >= 80 && story.syllables() <= 400, story.id() + " has " + story.syllables());
			assertTrue(story.level() >= 1 && story.level() <= 6, story.id() + " is level " + story.level());
		}
	}

	@Test
	void aStoryUsesNoGrammarAboveItsLevelAndShowsOneOfItsOwn() {
		GrammarBook book = GrammarBook.shared();
		for (Story story : stories) {
			int highest = 0;
			for (String id : story.grammar()) {
				assertTrue(book.get(id) != null, story.id() + " names the unknown construction " + id);
				highest = Math.max(highest, book.get(id).level());
			}
			assertTrue(highest <= story.level(), story.id() + " is level " + story.level() + " but uses level " + highest);
			assertEquals(story.level(), highest, story.id() + " shows no construction of its own level");
		}
	}

	@Test
	void everyEojeolOfEveryStoryResolvesAndNoneIsLeftUnexplained() {
		StoryVocabulary everything = vocabulary(id -> true);
		for (Story story : stories) {
			List<String> unresolved = tokens(story, everything).stream()
					.filter(token -> token.state() == DialogueCoverage.State.UNRESOLVED).map(StoryText.Token::text).toList();
			assertTrue(unresolved.isEmpty(), story.id() + " has eojeol the word lists do not know: " + unresolved);
			assertEquals(100, StoryCoverage.of(tokens(story, everything)).percent(), story.id());
		}
	}

	@Test
	void theWordAndGrammarListsMatchWhatTheTextReallyUses() {
		StoryVocabulary everything = vocabulary(id -> true);
		for (Story story : stories) {
			Set<String> used = new LinkedHashSet<>();
			Set<String> rules = new LinkedHashSet<>();
			for (StoryText.Token token : tokens(story, everything)) {
				if (token.id() != null && !token.id().startsWith("lexicon:")) {
					used.add(token.id());
				}
				if (token.breakdown() != null && !token.breakdown().grammar().isEmpty()) {
					rules.add(token.breakdown().grammar());
				}
			}
			assertEquals(new LinkedHashSet<>(story.words()).stream().sorted().toList(), used.stream().sorted().toList(),
					story.id() + ": the words list differs from the catalog words in the text");
			Set<String> missing = new LinkedHashSet<>(rules);
			missing.removeAll(story.grammar());
			assertTrue(missing.isEmpty(), story.id() + ": the text shows " + missing + " but the grammar list does not");
		}
	}

	@Test
	void aStoryOpensWhenItsWordsAreKnownAndItsTrailsAreDone() {
		for (Story story : stories) {
			Set<String> words = new LinkedHashSet<>(story.words());
			StoryVocabulary knowsAll = vocabulary(words::contains);
			StoryCoverage.Status open = StoryCoverage.status(story, knowsAll, trail -> true, story.unlock().minWords());
			assertTrue(open.open(), story.id() + " should open: " + open.coverage());
			assertTrue(open.newWords().isEmpty());

			StoryCoverage.Status noWords = StoryCoverage.status(story, vocabulary(id -> false), trail -> true, 1000);
			assertFalse(noWords.open(), story.id() + " must not open for a player who knows no catalog word");
			assertFalse(noWords.newWords().isEmpty());

			StoryCoverage.Status noTrails = StoryCoverage.status(story, knowsAll, trail -> false, 1000);
			assertFalse(noTrails.open(), story.id() + " needs its trails");
			assertEquals(story.unlock().trails(), noTrails.missingTrails());
			assertTrue(noTrails.coverage().meets(), "the words alone are not what blocks it");
		}
	}

	@Test
	void everyWordOfTheListIsNeededToOpenTheStory() {
		for (Story story : stories) {
			List<StoryText.Token> all = tokens(story, vocabulary(id -> true));
			long total = all.stream().filter(StoryText.Token::word).count();
			for (String missing : story.words()) {
				Set<String> knows = new LinkedHashSet<>(story.words());
				knows.remove(missing);
				StoryCoverage.Coverage coverage = StoryCoverage.of(tokens(story, vocabulary(knows::contains)));
				assertTrue(coverage.known() < total, story.id() + ": " + missing + " is in the list but not in the text");
			}
		}
	}

	@Test
	void everyParagraphHasAnEnglishAndADifferentGermanGloss() {
		for (Story story : stories) {
			assertTrue(english.containsKey(story.titleKey()) && german.containsKey(story.titleKey()), story.titleKey());
			for (int i = 0; i < story.paragraphs().size(); i++) {
				String key = story.paragraphKey(i);
				assertTrue(english.containsKey(key), "missing English " + key);
				assertTrue(german.containsKey(key), "missing German " + key);
				assertNotEquals(english.get(key), german.get(key), key + " is not translated into German");
			}
		}
	}

	@Test
	void everyStoryChapterIsInTheChapterList() {
		ChapterSet chapters = StoryFixtures.chapters();
		for (Story story : stories) {
			assertTrue(chapters.byId(story.chapter()).isPresent(), story.id());
		}
	}
}
