package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QA guard for the new wave content: clue, hint, status and taught word must be usable. */
class WaveQaTest {
	private static final Set<String> WAVE_1 = Set.of("feuerpfad", "tierfreund", "brotpfad",
			"haustierpfad", "hoehlenlicht");
	private static final Set<String> WAVE_2 = Set.of("kraeuterpfad", "wollpfad", "flusslicht",
			"waldlicht", "feuerstelle");
	private static final Set<String> WAVE_3 = Set.of("schmelzofen", "saatfeld", "wanderpfad",
			"nachthafen", "bogenpfad");
	private static final Set<String> WAVE_4 = Set.of("kronenlicht", "kronenwacht", "kronenpfad",
			"kronenglanz", "kronenfeier");
	private static final Set<String> WAVE_5 = Set.of("letzte_reise", "weltenschoepfer", "wurzelpfad",
			"steinherz", "gartenwacht");
	private static final Set<String> BATCH_6 = Set.of("musikpfad", "bienenpfad", "wetterpfad",
			"farbenpfad", "kartenpfad");
	private static final Set<String> PREVIOUS_BATCH = Set.of("musikpfad", "bienenpfad", "wetterpfad",
			"farbenpfad", "kartenpfad");
	private static final Set<String> BATCH_7 = Set.of("freundschaftspfad", "briefpfad", "redstonepfad",
			"schneeballpfad", "blumenpfad");
	private static final Set<String> BATCH_8 = Set.of("alchemiepfad", "schmuckpfad", "vogelpfad",
			"tempelpfad", "dschungelpfad");
	private static final Set<String> BATCH_9 = Set.of("piratenpfad", "vulkanpfad", "pyramidenpfad",
			"bibliothekpfad", "leuchtturmpfad");
	private static final Set<String> BATCH_10 = Set.of("drachenhort", "tempelwaechter", "sternenkarte",
			"handelsflotte", "zeitportal");
	private static final Set<String> BATCH_11 = Set.of("schattenpfad", "handwerksmeister", "seelenpfad",
			"golemwerkstatt", "kronenrat");
	private static final Set<String> BATCH_12 = Set.of("richterpfad", "runenpfad", "schicksalspfad",
			"weltenrichter", "unsterblichkeit");
	private static final Set<String> BATCH_13 = Set.of("kerzenpfad", "kuerbispfad", "glasfenster",
			"glockenpfad", "windpfad");
	private static final Set<String> BATCH_14 = Set.of("schneemannpfad", "pilzpfad", "teppichpfad",
			"inselpfad", "bambuspfad");
	private static final Set<String> BATCH_15 = Set.of("apfelpfad", "froschteich", "sonnenblume",
			"milchpfad", "festungpfad");
	private static final Set<String> BATCH_16 = Set.of("regenbogenpfad", "fuchsbau", "lavapfad",
			"gartenzaun", "schatzkammer");
	private static final Set<String> BATCH_17 = Set.of("blumentopf", "schildkroete", "blumenkranz",
			"uhrmacher", "druckerei");
	private static final Set<String> BATCH_18 = Set.of("muschelpfad", "vogelscheuche", "ziegenpfad",
			"pferderennen", "ziegelpfad");
	private static final Set<String> BATCH_19 = Set.of("brauerei", "bienenzucht", "steinmetz",
			"gartenkunst", "handelsgilde");
	private static final Set<String> BATCH_20 = Set.of("faerberei", "gerberei", "postpfad",
			"wolfpfad", "wolkenstadt");
	private static final Set<String> BATCH_21 = Set.of("obstgarten", "werkzeugpfad", "schmuckgilde",
			"fischergilde", "wuestenstadt");

	@Test
	void wavesOneAndTwoAreReadable() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		SentenceBuilder.Namer namer = new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return "강";
			}

			@Override
			public String entity(String id) {
				return "동물";
			}

			@Override
			public String block(String id) {
				return "블록";
			}
		};
		int checked = 0;
		for (Questline trail : QuestLoader.loadAll()) {
			if (!WAVE_1.contains(trail.id()) && !WAVE_2.contains(trail.id())
					&& !WAVE_3.contains(trail.id()) && !WAVE_4.contains(trail.id())
					&& !WAVE_5.contains(trail.id()) && !BATCH_6.contains(trail.id())
					&& !PREVIOUS_BATCH.contains(trail.id()) && !BATCH_7.contains(trail.id())
					&& !BATCH_8.contains(trail.id()) && !BATCH_9.contains(trail.id())
					&& !BATCH_10.contains(trail.id()) && !BATCH_11.contains(trail.id())
					&& !BATCH_12.contains(trail.id()) && !BATCH_13.contains(trail.id())
					&& !BATCH_14.contains(trail.id()) && !BATCH_15.contains(trail.id())
					&& !BATCH_16.contains(trail.id()) && !BATCH_17.contains(trail.id())
					&& !BATCH_18.contains(trail.id()) && !BATCH_19.contains(trail.id())
					&& !BATCH_20.contains(trail.id()) && !BATCH_21.contains(trail.id())) {
				continue;
			}
			for (var step : trail.allSteps()) {
				Word word = catalog.byConceptId(step.teach().get(0)).orElseThrow();
				String clue = SentenceBuilder.normal(step.goal(), word, step.conditions(),
						trail.koreanLevel(), step.id().equals("finale"), namer);
				String hint = SentenceBuilder.hint(step.goal(), word, step.conditions(), namer);
				assertFalse(clue.isBlank(), trail.id() + "/" + step.id() + " clue blank");
				assertFalse(hint.isBlank(), trail.id() + "/" + step.id() + " hint blank");
				assertTrue(SentenceBuilder.mentions(clue, word) || step.goal() instanceof io.github.leks2205.hangulquest.quest.Goal.UseBlock,
						trail.id() + "/" + step.id() + " clue omits the taught word: " + clue);
				if (!(step.goal() instanceof io.github.leks2205.hangulquest.quest.Goal.UseBlock)
						&& !(step.goal() instanceof io.github.leks2205.hangulquest.quest.Goal.InteractEntity)) {
					String status = QuestStatus.text(step, new Observation(),
							new GoalEvaluator.Tracker(), catalog);
					assertFalse(status.isBlank(), trail.id() + "/" + step.id() + " has no status line");
				}
				System.out.println("QA " + trail.id() + "/" + step.id() + " | " + clue + " | H: " + hint);
				checked++;
			}
		}
		assertTrue(checked >= 60, "expected all wave steps, got " + checked);
	}

	@Test
	void generatedHintsNameTheTaughtWord() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		SentenceBuilder.Namer namer = new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return "강";
			}

			@Override
			public String entity(String id) {
				return "동물";
			}

			@Override
			public String block(String id) {
				return "블록";
			}
		};
		int checked = 0;
		for (Questline trail : QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				if (step.hintKo() != null || step.teach().isEmpty()) {
					continue; // authored hints are hand written; this guards the generated ones
				}
				Word word = catalog.byConceptId(step.teach().get(0)).orElse(null);
				if (word == null || word.ko.endsWith("다")) {
					continue; // verb concepts (걷다, 짓다) are never the object of a generated hint
				}
				if (step.goal() instanceof io.github.leks2205.hangulquest.quest.Goal.ReachHeight) {
					continue; // "climb above Y 120" never names a noun
				}
				String hint = SentenceBuilder.hint(step.goal(), word, step.conditions(), namer);
				assertFalse(hint.isBlank(), trail.id() + "/" + step.id() + " has no hint");
				if (step.goal() instanceof io.github.leks2205.hangulquest.quest.Goal.UseBlock
						|| step.goal() instanceof io.github.leks2205.hangulquest.quest.Goal.InteractEntity) {
					continue; // "open the chest" does not repeat the noun twice
				}
				assertTrue(hint.contains(word.ko), trail.id() + "/" + step.id()
						+ " hint omits " + word.ko + ": " + hint);
				checked++;
			}
		}
		assertTrue(checked >= 500, "expected the generated hints to be checked, got " + checked);
	}

	@Test
	void multiWordHintsExplainTheExtraNouns() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		SentenceBuilder.Namer namer = KoreanFixtures.namer(KoreanFixtures.catalog());
		int generated = 0;
		for (Questline trail : QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				if (step.teach().size() < 2) {
					continue;
				}
				List<Word> words = new java.util.ArrayList<>();
				for (String concept : step.teach()) {
					var word = catalog.byConceptId(concept).orElse(null);
					if (word != null && !"ACTIONS".equals(word.category.name()) && !word.ko.endsWith("다")) {
						words.add(word); // verbs are conjugated in the hint, not repeated as a dictionary form
					}
				}
				boolean authored = step.hintKo() != null && !step.hintKo().isBlank();
				String hint = authored ? step.hintKo() : SentenceBuilder.hint(step.goal(),
						words.isEmpty() ? null : words.get(0), null, words, step.conditions(), namer);
				generated += authored ? 0 : 1;
				for (Word word : words) {
					// a generated hint can only name a noun the goal has a place for
					boolean placed = authored || step.goal() instanceof io.github.leks2205.hangulquest.quest.Goal.ReachHeight
							|| io.github.leks2205.hangulquest.quest.goal.GoalTypes.of(step.goal()).covers(step.goal(),
							catalog.concept(word.conceptId).orElseThrow())
							&& io.github.leks2205.hangulquest.quest.goal.GoalTypes.of(step.goal()).mentions(step.goal())
							.stream().anyMatch(m -> io.github.leks2205.hangulquest.quest.goal.GoalType.covers(m,
									catalog.concept(word.conceptId).orElseThrow()));
					if (placed) {
						assertTrue(hint.contains(word.ko), trail.id() + "/" + step.id() + " hint omits " + word.ko
								+ ": " + hint);
					}
				}
				assertFalse(hint.isBlank(), trail.id() + "/" + step.id() + " has no hint");
			}
		}
		System.out.println("multi-word steps with a generated hint: " + generated);
	}

}
