package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Topik;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.util.TextClean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the shipped content: every trail must load, validate and stay Hangul-only. */
class QuestlineValidationTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("questline-test");

	@Test
	void curatedConceptsAreSane() {
		List<Concept> concepts = ConceptLoader.load();
		assertTrue(concepts.size() >= 80, "expected a broad curated vocabulary, got " + concepts.size());
		Set<String> ids = new HashSet<>();
		for (Concept concept : concepts) {
			assertTrue(ids.add(concept.id), "duplicate concept id " + concept.id);
			assertTrue(TextClean.isHangulOnly(concept.ko), "Korean word contains non-Hangul: " + concept.ko);
			assertNotNull(concept.de);
			if (concept.descKo != null) {
				assertTrue(TextClean.isHangulOnly(concept.descKo),
						"descKo is not Hangul-only for " + concept.id + ": " + concept.descKo);
			}
		}
	}

	@Test
	void allShippedQuestlinesValidate() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		List<Questline> loaded = QuestLoader.loadAll();
		assertEquals(422, loaded.size(), "expected four hundred and twenty authored trails and two demos");
		QuestValidator.Result result = QuestValidator.validate(loaded, catalog, LOG);
		assertTrue(result.problems().isEmpty(), "questline problems: " + result.problems());
		assertEquals(422, result.trails().size());

		java.util.Set<String> trailIds = new java.util.HashSet<>();
		java.util.Map<String, Questline> byId = new java.util.HashMap<>();
		for (Questline trail : result.trails()) {
			trailIds.add(trail.id());
			byId.put(trail.id(), trail);
		}
		TrailGraph graph = TrailGraph.of(result.trails());
		assertFalse(graph.hasCycle(), "questline dependency cycle: " + graph.cyclic());
		java.util.Map<String, Integer> depths = new java.util.HashMap<>();
		int maxDepth = 0;
		boolean multiParent = false;
		for (Questline trail : result.trails()) {
			int depth = graph.depth(trail.id());
			depths.put(trail.id(), depth);
			maxDepth = Math.max(maxDepth, depth);
			if (trail.requires().completed().size() >= 3) {
				multiParent = true;
			}
			assertEquals(Topik.levelForDepth(depth), trail.koreanLevel(),
					trail.id() + " should be at TOPIK level " + Topik.levelForDepth(depth));
		}
		assertTrue(maxDepth >= 5, "expected a deep tree, got depth " + maxDepth);
		assertTrue(multiParent, "expected at least one trail behind three parents");

		java.util.Map<String, Integer> children = new java.util.HashMap<>();
		for (Questline trail : result.trails()) {
			for (String parent : trail.requires().completed()) {
				children.merge(parent, 1, Integer::sum);
			}
		}
		for (Questline trail : result.trails()) {
			int depth = depths.get(trail.id());
			if (depth > 1) {
				assertFalse(trail.requires().completed().isEmpty(),
						trail.id() + " is not on level 1 but has no previous quest");
			}
			if (depth < maxDepth && depth < 18 && !trail.id().startsWith("adv_") && !trail.id().startsWith("w")
					&& !trail.id().endsWith("_demo")) {
				assertTrue(children.getOrDefault(trail.id(), 0) > 0,
						trail.id() + " is a dead end on level " + depth
								+ " but the tree continues to level " + maxDepth);
			}
			assertFalse(trail.titleDe().isBlank() || trail.titleDe().equals(trail.id()), trail.id() + " has no title text");
			assertTrue(trail.order() > 0, trail.id() + " has no order");
			assertFalse(trail.concepts().isEmpty(), trail.id() + " teaches no concepts");
			assertFalse(trail.badge().isBlank(), trail.id() + " has no badge");
			assertFalse(trail.topic().isBlank(), trail.id() + " has no topic");
			assertEquals(Math.min(5, 1 + trail.requires().completed().size()), trail.tier(),
					trail.id() + " tier should be 1 + parent count (capped at 5)");
		}
		for (Questline trail : result.trails()) {
			for (String parent : trail.requires().completed()) {
				assertTrue(trailIds.contains(parent),
						trail.id() + " requires unknown trail " + parent);
				assertFalse(trail.id().equals(parent), trail.id() + " requires itself");
			}
			assertNotNull(trail.finale(), trail.id() + " has no finale");
			assertTrue(trail.allSteps().size() >= 3, trail.id() + " is too short");
			assertTrue(TextClean.isHangulOnly(trail.finaleKo()),
					trail.id() + " finale text is not Hangul-only");
			assertFalse(trail.titleKo().isBlank());
			for (String concept : trail.concepts()) {
				assertTrue(catalog.concept(concept).isPresent(),
						trail.id() + " references unknown concept " + concept);
			}
			for (var step : trail.allSteps()) {
				if (step.clueKo() != null) {
					assertTrue(TextClean.isHangulOnly(step.clueKo()),
							trail.id() + "/" + step.id() + " clue contains non-Hangul");
				}
			}
		}
	}

	@Test
	void everyConditionAppearsInTheKoreanClue() {
		// the registry names stand behind the concepts, as in the game: a held item without a concept is still spoken
		Catalog catalog = KoreanFixtures.catalog();
		SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);
		for (Questline trail : QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				if (step.conditions().isEmpty()) {
					continue;
				}
				var word = catalog.byConceptId(step.teach().get(0)).orElse(null);
				String plain = SentenceBuilder.normal(step.goal(), word, List.of(),
						trail.koreanLevel(), "finale".equals(step.id()), namer);
				String spoken = SentenceBuilder.normal(step.goal(), word, step.conditions(),
						trail.koreanLevel(), "finale".equals(step.id()), namer);
				assertFalse(plain.equals(spoken),
						trail.id() + "/" + step.id() + " does not speak its conditions: " + spoken);
				for (var condition : step.conditions()) {
					if (condition instanceof Condition.InBiome biome
							&& biome.biome().equals("minecraft:beach")) {
						assertTrue(spoken.contains("해변에서"), spoken);
					}
					if (condition instanceof Condition.InDimension dimension
							&& dimension.dimension().equals("minecraft:the_nether")) {
						assertTrue(spoken.contains("네더에서"), spoken);
					}
					if (condition instanceof Condition.YRange y && y.maxY() <= 0) {
						assertTrue(spoken.contains("이하에서"), spoken);
					}
				}
			}
		}
	}

	@Test
	void anAuthoredClueOnAStepWithConditionsStillSpeaksEveryCondition() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);
		int checked = 0;
		for (Questline trail : QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				if (step.conditions().isEmpty() || !step.hasClueKo()) {
					continue;
				}
				var word = step.teach().isEmpty() ? null : catalog.byConceptId(step.teach().get(0)).orElse(null);
				for (int level = 1; level <= 6; level++) {
					String said = SentenceBuilder.authored(step.clueKo(), step.conditions(), word, namer, level);
					assertTrue(said.endsWith(step.clueKo()), trail.id() + "/" + step.id() + ": the authored text stays");
					assertTrue(SentenceBuilder.missingPhrases(said, step.conditions(), word, namer, level).isEmpty(),
							trail.id() + "/" + step.id() + " level " + level + " does not speak its conditions: " + said);
				}
				checked++;
			}
		}
		assertTrue(checked > 0, "no shipped step has both an authored clue and a condition");
	}

	@Test
	void everyTrailTeachesKnownWords() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (Questline trail : QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				for (String concept : step.teach()) {
					assertTrue(catalog.concept(concept).isPresent(),
							trail.id() + "/" + step.id() + " teaches unknown concept " + concept);
				}
			}
		}
	}

	/** What a clue may use to place something: the verbs of the table for every placing goal, plus 설치 and 두. */
	private static List<String> placing() {
		List<String> stems = new java.util.ArrayList<>(List.of("설치", "두"));
		for (String type : List.of("PLACE_BLOCK", "PLACE_REL", "PLACE_ON_PATTERN", "BUILD_PATTERN")) {
			for (io.github.leks2205.hangulquest.quest.Verb verb : io.github.leks2205.hangulquest.quest.VerbTable
					.shared().verbsFor(type)) {
				stems.add(verb.stem());
			}
		}
		stems.add(io.github.leks2205.hangulquest.quest.Verb.PLACE.stem());
		return stems;
	}

	private static List<String> concat(List<String> a, List<String> b) {
		List<String> out = new java.util.ArrayList<>(a);
		out.addAll(b);
		return out;
	}

	@Test
	void authoredCluesNameTheAction() {
		for (Questline trail : QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				String ko = step.clueKo();
				if (ko == null) {
					continue;
				}
				assertTrue(namesAction(step.goal(), ko), trail.id() + "/" + step.id()
						+ " clue does not name the action (" + step.goal().getClass().getSimpleName()
						+ "): " + ko);
			}
		}
	}

	/**
	 * Whether the clue says what the goal asks for: a verb stem the goal can be said with, or any conjugated form
	 * of the verbs of its type (줘요 holds no 주). A counter phrase between the object and the verb ("감자를 한 개
	 * 넣어요") changes nothing; the check looks at the verb, not at what stands in front of it.
	 */
	static boolean namesAction(Goal goal, String ko) {
		var verbs = switch (goal) {
			case Goal.PlaceBlock g -> placing();
			case Goal.UseBlock g -> List.of("사용", "자", "열", "울", "먹", "마시", "대", "누르", "눌러", "당기", "당겨", "써", "닫", "쳐");
			case Goal.AcquireItem g -> List.of("모", "얻", "구", "챙", "만들", "가지", "가져", "캐", "잡", "담");
			case Goal.ConsumeItem g -> List.of("먹", "마시");
			case Goal.BreakBlock g -> List.of("캐", "베", "수확", "부수", "채", "파", "꺾", "뽑", "깨", "따");
			case Goal.LookAtBlock g -> List.of("봐", "바라", "보");
			case Goal.LookAtEntity g -> List.of("봐", "바라", "보");
			case Goal.ReachBiome g -> List.of("가", "도착", "걸", "올라");
			case Goal.ReachDimension g -> List.of("가", "도착", "걸");
			case Goal.ReachHeight g -> List.of("올라", "내려", "가");
			case Goal.DefeatEntity g -> List.of("물리", "죽", "잡");
			case Goal.InteractEntity g -> List.of("이야기", "대화", "말", "만나");
			case Goal.PlaceRel g -> placing();
			case Goal.BuildPattern g -> concat(placing(), List.of("만들"));
			case Goal.PlaceOnPattern g -> placing();
			case Goal.Craft g -> List.of("만들");
			case Goal.Smelt g -> List.of("만들", "구", "녹");
			case Goal.Give g -> List.of("주", "떨어뜨");
			case Goal.PutIn g -> List.of("넣");
			case Goal.HoldWhile g -> List.of("들");
			case Goal.Sequence g -> List.of("전에", "후에", "나서", "고");
			case Goal.Avoid g -> List.of("마세요");
			case Goal.TakeOut g -> List.of("꺼내", "꺼낸");
			case Goal.Transit g -> List.of("들어가", "나가", "나와", "나오");
			case Goal.Hear g -> List.of("들어", "듣");
			case Goal.LieDown g -> List.of("누워", "누우");
			case Goal.Wait g -> List.of("기다");
			case Goal.Breed g -> List.of("키워", "키우");
			case Goal.Mount g -> List.of("타", "앉");
			case Goal.Swim g -> List.of("수영");
			case Goal.Trade g -> List.of("사", "팔");
			case Goal.Equip g -> List.of("쓰", "입", "신", "벗");
			case Goal.Fall g -> List.of("뛰어내");
		};
		if (verbs.stream().anyMatch(ko::contains)) {
			return true;
		}
		List<io.github.leks2205.hangulquest.quest.Verb> spoken = new java.util.ArrayList<>(
				io.github.leks2205.hangulquest.quest.VerbTable.shared().verbsFor(GoalTypes.of(goal).id()));
		spoken.add(SentenceBuilder.verbFor(goal));
		spoken.addAll(GoalTypes.of(goal).alternativeVerbs(goal));
		return spoken.stream().anyMatch(
				verb -> io.github.leks2205.hangulquest.quest.grammar.Conjugator.contains(ko, verb.dictionary()));
	}

	@Test
	void authoredCluesMentionTheirTargetWord() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (Questline trail : QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				String ko = step.clueKo();
				if (ko == null || step.teach().isEmpty()) {
					continue;
				}
				var word = catalog.byConceptId(step.teach().get(0)).orElse(null);
				// a verb is in the clue as a conjugated form (올라가요 for 올라가다)
				assertTrue(word == null || SentenceBuilder.mentions(ko, word),
						trail.id() + "/" + step.id() + " clue omits the taught word " + word + ": " + ko);
			}
		}
	}

	@Test
	void noTrailRepeatsItsOwnGoal() {
		for (Questline trail : QuestLoader.loadAll()) {
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (var step : trail.allSteps()) {
				assertTrue(seen.add(step.goal().toString()), trail.id()
						+ " repeats the same goal: " + step.goal());
			}
		}
	}


	@Test
	void rewardWordsAreTaughtInTheirBranch() {
		var trails = QuestLoader.loadAll();
		var byId = new java.util.HashMap<String, Questline>();
		for (Questline trail : trails) {
			byId.put(trail.id(), trail);
		}
		for (Questline trail : trails) {
			java.util.Set<String> taught = new java.util.HashSet<>();
			java.util.Set<String> seen = new java.util.HashSet<>();
			java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
			queue.add(trail.id());
			while (!queue.isEmpty()) {
				String id = queue.poll();
				if (!seen.add(id)) {
					continue;
				}
				Questline current = byId.get(id);
				if (current == null) {
					continue;
				}
				for (var step : current.allSteps()) {
					taught.addAll(step.teach());
				}
				queue.addAll(current.requires().completed());
			}
			for (String concept : trail.concepts()) {
				assertTrue(taught.contains(concept), trail.id() + " rewards untaught word " + concept);
			}
		}
	}


	@Test
	void finaleTextsSpeakTheirConditions() {
		for (Questline trail : QuestLoader.loadAll()) {
			String text = trail.finaleKo();
			for (Condition condition : trail.finale().conditions()) {
				String phrase = switch (condition) {
					case Condition.Time t -> switch (t.phase()) {
						case DAY -> "낮";
						case NIGHT -> "밤";
						case DAWN -> "새벽";
						case DUSK -> "저녁";
					};
					case Condition.InBiome b -> b.biome().contains("mountain") ? "산에서"
							: b.biome().contains("beach") ? "해변"
							: b.biome().contains("river") ? "강" : null;
					case Condition.InDimension d -> d.dimension().contains("nether") ? "네더" : null;
					case Condition.YRange y -> y.maxY() <= 0 ? "높이 " + y.maxY() : null;
					default -> null;
				};
				if (phrase != null) {
					assertTrue(text.contains(phrase), trail.id()
							+ " finale text omits condition " + phrase + ": " + text);
				}
			}
		}
	}


	@Test
	void finalTextsAndAuthoredCluesAreUnique() {
		java.util.Set<String> finales = new java.util.HashSet<>();
		java.util.Set<String> clues = new java.util.HashSet<>();
		for (Questline trail : QuestLoader.loadAll()) {
			assertTrue(finales.add(trail.finaleKo()),
					trail.id() + " duplicates finale text: " + trail.finaleKo());
			for (var step : trail.allSteps()) {
				if (step.clueKo() != null) {
					assertTrue(clues.add(step.clueKo()),
							trail.id() + "/" + step.id() + " duplicates clue: " + step.clueKo());
				}
			}
		}
	}


	@Test
	void noDuplicateQuestlinesOrTitles() {
		java.util.Set<String> sequences = new java.util.HashSet<>();
		java.util.Set<String> titles = new java.util.HashSet<>();
		for (Questline trail : QuestLoader.loadAll()) {
			assertTrue(titles.add(trail.titleKo()),
					trail.id() + " duplicates title: " + trail.titleKo());
			StringBuilder sequence = new StringBuilder();
			for (var step : trail.allSteps()) {
				sequence.append(step.goal().getClass().getSimpleName()).append(':')
						.append(step.goal()).append(';');
			}
			assertTrue(sequences.add(sequence.toString()),
					trail.id() + " repeats an existing goal sequence");
		}
	}

	@Test
	void everyValidatedTrailHasAnEnglishTitleAndFinaleWithoutFallback() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		QuestValidator.Result result = QuestValidator.validate(QuestLoader.loadAll(), catalog, LOG);
		assertEquals(422, result.trails().size());
		for (Questline trail : result.trails()) {
			// The raw fields: the fallback getters would hide a lost English text behind German.
			assertFalse(trail.titleEnRaw() == null || trail.titleEnRaw().isBlank(),
					trail.id() + " lost its English title");
			assertFalse(trail.finaleEnRaw() == null || trail.finaleEnRaw().isBlank(),
					trail.id() + " lost its English finale text");
		}
	}

	@Test
	void everyStepHasAnEnglishClue() {
		for (Questline trail : QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				String english = step.clueEn();
				assertFalse(english == null || english.isBlank(),
						trail.id() + "/" + step.id() + " has no English clue");
				assertFalse(english.matches(".*[\uAC00-\uD7A3].*"),
						trail.id() + "/" + step.id() + " English clue contains Hangul: " + english);
			}
		}
	}
}
