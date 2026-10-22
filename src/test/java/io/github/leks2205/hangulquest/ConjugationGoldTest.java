package io.github.leks2205.hangulquest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two gold tables are well-formed and agree with themselves: conjugation-gold.tsv (forms of
 * every verb and adjective we use) and task-sentences-gold.tsv (target sentences for the variety
 * engine). conjugatorMatchesTheGoldTable runs every row through quest/grammar/Conjugator.
 */
class ConjugationGoldTest {
	private static final String CONJUGATION = "/conjugation-gold.tsv";
	private static final String TASKS = "/task-sentences-gold.tsv";
	private static final String GRAMMAR = "/assets/hangulquest/data/grammar.json";

	/** Column order of conjugation-gold.tsv. */
	private static final String[] COLUMNS = {"dictionary", "class", "-아/어요", "-았/었어요", "-(으)세요", "-고",
			"-(으)ㄹ", "-(으)면", "-아/어서", "-지 마세요", "note"};
	private static final int DICT = 0, CLASS = 1, POLITE = 2, PAST = 3, SEYO = 4, GO = 5, FUTURE = 6, MYEON = 7,
			EOSEO = 8, JIMA = 9;

	private static final Set<String> CLASSES = Set.of("reg", "hada", "eu_drop", "rieul_drop", "b_irr", "d_irr",
			"reu_irr", "s_irr", "h_irr", "leo_irr", "u_irr");

	/** Every dictionary form in quest/Verb.java when this table was written. */
	private static final String[] VERB_ENUM = {"캐다", "베다", "수확하다", "놓다", "모으다", "먹다", "마시다", "보다", "바라보다",
			"물리치다", "가다", "올라가다", "내려가다", "열다", "이야기하다", "자다", "만들다", "쓰다", "울리다", "부수다", "파다", "뽑다",
			"꺾다", "따다", "깨다", "심다", "깔다", "걸다", "세우다", "달다", "쌓다", "밝히다", "피우다", "구하다", "줍다", "치다", "누르다",
			"당기다", "켜다", "도착하다", "주다", "던지다", "쓰다듬다", "타다", "만지다"};

	// --------------------------------------------------------------------------- Hangul helpers

	private static boolean syllable(char c) {
		return c >= 0xAC00 && c <= 0xD7A3;
	}

	/** Final consonant index (0 = none, 8 = ㄹ, 17 = ㅂ, 19 = ㅅ, 20 = ㅆ, 7 = ㄷ, 27 = ㅎ). */
	private static int jong(char c) {
		return syllable(c) ? (c - 0xAC00) % 28 : -1;
	}

	private static int initial(char c) {
		return (c - 0xAC00) / 588;
	}

	private static char withJong(char c, int jong) {
		return (char) (c - jong(c) + jong);
	}

	private static String dropLast(String s) {
		return s.substring(0, s.length() - 1);
	}

	// --------------------------------------------------------------------------- loading

	private static List<String[]> table(String resource, int columns) {
		List<String[]> rows = new ArrayList<>();
		try (InputStream stream = ConjugationGoldTest.class.getResourceAsStream(resource)) {
			assertTrue(stream != null, resource + " is missing");
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				String line = reader.readLine();
				assertTrue(line != null && line.split("\t", -1).length == columns, resource + ": bad header " + line);
				int number = 1;
				while ((line = reader.readLine()) != null) {
					number++;
					if (line.isBlank() || line.startsWith("#")) {
						continue;
					}
					String[] cells = line.split("\t", -1);
					assertEquals(columns, cells.length, resource + " line " + number + " has " + cells.length
							+ " columns: " + line);
					rows.add(cells);
				}
			}
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
		return rows;
	}

	private static List<String[]> conjugation() {
		return table(CONJUGATION, COLUMNS.length);
	}

	private static String stem(String[] row) {
		return dropLast(row[DICT]);
	}

	private static boolean adjective(String[] row) {
		return row[CLASS].endsWith(".adj");
	}

	private static String baseClass(String[] row) {
		return row[CLASS].replace(".adj", "");
	}

	// --------------------------------------------------------------------------- conjugation-gold.tsv

	@Test
	void everyRowHasEveryColumnAndNoCellIsBlankOrUntidy() {
		for (String[] row : conjugation()) {
			for (int i = 0; i < row.length; i++) {
				String cell = row[i];
				String where = row[DICT] + " / " + COLUMNS[i];
				assertFalse(cell.isEmpty(), where + " is empty");
				assertEquals(cell.strip(), cell, where + " has stray whitespace");
				assertTrue(Normalizer.isNormalized(cell, Normalizer.Form.NFC), where + " is not NFC");
			}
		}
	}

	@Test
	void formsAreHangulOnlyAndTheClassIsKnown() {
		Pattern hangul = Pattern.compile("[가-힣 ]+");
		for (String[] row : conjugation()) {
			assertTrue(row[DICT].matches("[가-힣]+다"), "dictionary form: " + row[DICT]);
			assertTrue(CLASSES.contains(baseClass(row)), row[DICT] + ": unknown class " + row[CLASS]);
			for (int i = POLITE; i <= JIMA; i++) {
				if (i == JIMA && adjective(row)) {
					continue;
				}
				assertTrue(hangul.matcher(row[i]).matches(), row[DICT] + " / " + COLUMNS[i] + ": " + row[i]);
			}
		}
	}

	@Test
	void noDuplicateDictionaryForms() {
		Set<String> seen = new HashSet<>();
		for (String[] row : conjugation()) {
			assertTrue(seen.add(row[DICT]), "duplicate row for " + row[DICT]);
		}
	}

	@Test
	void theTableIsBigEnoughAndCoversEveryClassAndEveryVerbWeHave() {
		List<String[]> rows = conjugation();
		assertTrue(rows.size() >= 300, "only " + rows.size() + " rows");
		Set<String> classes = new HashSet<>();
		Set<String> dictionary = new HashSet<>();
		for (String[] row : rows) {
			classes.add(baseClass(row));
			dictionary.add(row[DICT]);
		}
		assertEquals(CLASSES, classes);
		for (String verb : VERB_ENUM) {
			assertTrue(dictionary.contains(verb), "Verb.java has " + verb + " but the table does not");
		}
		for (String needed : new String[]{"줍다", "돕다", "덥다", "걷다", "듣다", "모르다", "부르다", "누르다", "짓다", "낫다",
				"빨갛다", "파랗다", "노랗다", "하얗다", "까맣다", "쓰다", "모으다", "끄다", "만들다", "열다", "살다", "걸다", "깔다",
				"달다", "크다", "작다", "높다", "낮다", "어둡다", "밝다", "춥다", "뜨겁다"}) {
			assertTrue(dictionary.contains(needed), "missing " + needed);
		}
	}

	@Test
	void theFormsOfOneRowAgreeWithEachOther() {
		for (String[] row : conjugation()) {
			String word = row[DICT];
			String stem = stem(row);
			// -아/어요 ends in 요; the past is the same base with ㅆ and 어요
			assertTrue(row[POLITE].endsWith("요"), word + ": -아/어요 must end in 요: " + row[POLITE]);
			String base = dropLast(row[POLITE]);
			assertEquals(0, jong(base.charAt(base.length() - 1)), word + ": the 아/어 form ends in an open syllable");
			assertEquals(dropLast(base) + withJong(base.charAt(base.length() - 1), 20) + "어요", row[PAST],
					word + ": past does not match -아/어요");
			assertEquals(base + "서", row[EOSEO], word + ": -아/어서 does not match -아/어요");
			assertTrue(row[SEYO].endsWith("세요"), word + ": -(으)세요 must end in 세요: " + row[SEYO]);
			assertEquals(stem + "고", row[GO], word + ": -고 is stem + 고");
			assertTrue(row[MYEON].endsWith("면"), word + ": -(으)면 must end in 면");
			assertEquals(8, jong(row[FUTURE].charAt(row[FUTURE].length() - 1)), word + ": -(으)ㄹ must end in ㄹ: " + row[FUTURE]);
			if (adjective(row)) {
				assertEquals("-", row[JIMA], word + ": adjectives have no -지 마세요");
			} else {
				assertEquals(stem + "지 마세요", row[JIMA], word + ": -지 마세요 is stem + 지 마세요");
			}
		}
	}

	@Test
	void theClassDecidesTheShapeOfTheForms() {
		for (String[] row : conjugation()) {
			String word = row[DICT];
			String stem = stem(row);
			char last = stem.charAt(stem.length() - 1);
			String polite = row[POLITE];
			switch (baseClass(row)) {
				case "hada" -> {
					assertTrue(word.endsWith("하다"), word);
					assertEquals(dropLast(stem) + "해요", polite, word);
					assertEquals(stem + "세요", row[SEYO], word);
					assertEquals(dropLast(stem) + "했어요", row[PAST], word);
				}
				case "rieul_drop" -> {
					assertEquals(8, jong(last), word + ": ㄹ-drop verbs end in ㄹ");
					assertEquals(dropLast(stem) + withJong(last, 0) + "세요", row[SEYO], word + ": ㄹ drops before 세요");
					assertEquals(stem, row[FUTURE], word + ": the ㄹ stays in -(으)ㄹ");
					assertEquals(stem + "면", row[MYEON], word + ": the ㄹ stays before 면");
					assertTrue(polite.startsWith(stem), word + ": -아/어요 keeps the ㄹ");
					noLiquidBeforeNBS(row);
				}
				case "eu_drop" -> {
					assertFalse(polite.equals(stem + "어요") || polite.equals(stem + "아요"), word + ": 으 must drop in -아/어요");
					assertEquals(stem + "세요", row[SEYO], word + ": 으 stays before 세요");
				}
				case "b_irr" -> {
					assertEquals(17, jong(last), word + ": ㅂ-irregular stems end in ㅂ");
					String open = dropLast(stem) + withJong(last, 0);
					assertEquals(open + "우세요", row[SEYO], word);
					assertEquals(open + "우면", row[MYEON], word);
					assertEquals(open + "울", row[FUTURE], word);
					assertTrue(polite.equals(open + "워요") || polite.equals(open + "와요"), word + ": " + polite);
					if (!word.equals("돕다") && !word.equals("곱다")) {
						assertEquals(open + "워요", polite, word + ": only 돕다 and 곱다 take 와");
					}
				}
				case "d_irr" -> {
					assertEquals(7, jong(last), word);
					String liquid = dropLast(stem) + withJong(last, 8);
					assertTrue(polite.startsWith(liquid), word + ": ㄷ becomes ㄹ before a vowel");
					assertEquals(liquid + "으세요", row[SEYO], word);
					assertEquals(liquid + "을", row[FUTURE], word);
					assertEquals(liquid + "으면", row[MYEON], word);
				}
				case "s_irr" -> {
					assertEquals(19, jong(last), word);
					String open = dropLast(stem) + withJong(last, 0);
					assertTrue(polite.startsWith(open), word + ": ㅅ drops before a vowel");
					assertEquals(open + "으세요", row[SEYO], word);
					assertEquals(open + "을", row[FUTURE], word);
					assertEquals(open + "으면", row[MYEON], word);
				}
				case "h_irr" -> {
					assertEquals(27, jong(last), word);
					String open = dropLast(stem) + withJong(last, 0);
					assertEquals(open + "세요", row[SEYO], word);
					assertEquals(open + "면", row[MYEON], word);
					for (int i = POLITE; i <= MYEON; i++) {
						if (i != GO) {
							assertFalse(row[i].contains(String.valueOf(last)), word + ": ㅎ must be gone in " + COLUMNS[i]);
						}
					}
				}
				case "reu_irr" -> {
					assertEquals("르", String.valueOf(last), word);
					String before = dropLast(stem);
					String doubled = dropLast(before) + withJong(before.charAt(before.length() - 1), 8);
					assertTrue(polite.startsWith(doubled + "라") || polite.startsWith(doubled + "러"), word + ": 르 → ㄹ라/ㄹ러: " + polite);
					assertEquals(stem + "세요", row[SEYO], word);
				}
				case "leo_irr" -> assertEquals(stem + "러요", polite, word);
				case "u_irr" -> assertEquals("퍼요", polite, word);
				default -> {
					// regular: nothing is dropped or changed in the stem
					assertTrue(polite.startsWith(stem) || jong(last) == 0, word + ": regular stems keep their stem");
					assertEquals(stem + (jong(last) == 0 ? "" : "으") + "면", row[MYEON], word);
					assertEquals(stem + (jong(last) == 0 ? "" : "으") + "세요", row[SEYO], word);
				}
			}
		}
	}

	/** A ㄹ-final stem loses the ㄹ in front of ㄴ, ㅂ and ㅅ: 만드세요, not 만들세요. */
	private static void noLiquidBeforeNBS(String[] row) {
		for (int i = POLITE; i <= JIMA; i++) {
			String form = row[i];
			for (int c = 0; c + 1 < form.length(); c++) {
				char a = form.charAt(c);
				char b = form.charAt(c + 1);
				if (syllable(a) && syllable(b) && jong(a) == 8) {
					int next = initial(b);
					assertFalse(next == 2 || next == 7 || next == 9,
							row[DICT] + " / " + COLUMNS[i] + ": ㄹ before ㄴ/ㅂ/ㅅ in " + form);
				}
			}
		}
	}

	@Test
	void noRowHasALiquidBeforeSeyo() {
		// 들세요 / 만들세요 / 열세요 are the classic slips; the gold table must never contain them
		for (String[] row : conjugation()) {
			String form = row[SEYO];
			char beforeSeyo = form.charAt(form.length() - 3);
			assertTrue(jong(beforeSeyo) != 8, row[DICT] + ": " + form);
		}
	}

	@Test
	void everyRowWithAHonorificReplacementSaysSo() {
		Map<String, String> expected = new HashMap<>();
		expected.put("먹다", "드세요");
		expected.put("마시다", "드세요");
		expected.put("자다", "주무세요");
		expected.put("있다", "계세요");
		expected.put("말하다", "말씀하세요");
		for (String[] row : conjugation()) {
			String honorific = expected.get(row[DICT]);
			if (honorific != null) {
				assertTrue(row[10].contains(honorific), row[DICT] + " should mention " + honorific);
			}
		}
	}

	// --------------------------------------------------------------------------- the conjugator

	/**
	 * Gold cells we know the conjugator disagrees with, as "dictionary / column header". Empty means
	 * every one of the rows matches; an entry needs a comment saying why the gold cell is wrong.
	 */
	private static final Set<String> KNOWN_ISSUES = Set.of();

	/**
	 * {@code form} is one of the column headers above, for example "-아/어요" or "-(으)세요"; the class
	 * column is only there to explain a mismatch, the conjugator works from the dictionary form alone.
	 */
	private static String conjugate(String dictionary, String form) {
		Conjugator.Word word = Conjugator.of(dictionary);
		return switch (form) {
			case "-아/어요" -> word.eoyo();
			case "-았/었어요" -> word.past();
			case "-(으)세요" -> word.plainSeyo(); // 먹다 is 드세요 in the generator; the gold is the regular form
			case "-고" -> word.go();
			case "-(으)ㄹ" -> word.future();
			case "-(으)면" -> word.myeon();
			case "-아/어서" -> word.aseo();
			case "-지 마세요" -> word.jimaseyo();
			default -> throw new IllegalArgumentException("no conjugator form for " + form);
		};
	}

	@Test
	void conjugatorMatchesTheGoldTable() {
		List<String> misses = new ArrayList<>();
		Set<String> unexpectedPasses = new HashSet<>(KNOWN_ISSUES);
		for (String[] row : conjugation()) {
			for (int i = POLITE; i <= JIMA; i++) {
				if (row[i].equals("-")) {
					continue;
				}
				String got = conjugate(row[DICT], COLUMNS[i]);
				String cell = row[DICT] + " / " + COLUMNS[i];
				if (!row[i].equals(got)) {
					if (!KNOWN_ISSUES.contains(cell)) {
						misses.add(row[DICT] + " (" + row[CLASS] + ") " + COLUMNS[i] + ": expected " + row[i] + ", got " + got);
					}
				} else {
					unexpectedPasses.remove(cell);
				}
			}
		}
		assertTrue(misses.isEmpty(), misses.size() + " mismatches:\n" + String.join("\n", misses));
		assertTrue(unexpectedPasses.isEmpty(), "listed as known issues but they match now: " + unexpectedPasses);
	}

	@Test
	void conjugatorClassifiesEveryGoldRowTheWayTheTableDoes() {
		List<String> misses = new ArrayList<>();
		for (String[] row : conjugation()) {
			Conjugator.Irregular expected = Conjugator.Irregular.byId(baseClass(row));
			if (Conjugator.classify(row[DICT]) != expected) {
				misses.add(row[DICT] + ": expected " + expected + ", got " + Conjugator.classify(row[DICT]));
			}
			if (Conjugator.isAdjective(row[DICT]) != adjective(row)) {
				misses.add(row[DICT] + ": adjective should be " + adjective(row));
			}
		}
		assertTrue(misses.isEmpty(), misses.size() + " misclassified:\n" + String.join("\n", misses));
	}

	// --------------------------------------------------------------------------- task-sentences-gold.tsv

	private static final Set<String> RELATIONS = Set.of("-", "ON_TOP_OF", "UNDER", "NEXT_TO", "IN_FRONT_OF", "BEHIND",
			"INSIDE", "OUTSIDE", "BETWEEN", "NEAR");
	private static final Set<String> ROLES = Set.of("TARGET", "ACTIVITY_PLACE", "RECIPIENT", "MATERIAL", "DIRECTION",
			"INSTRUMENT", "HELD", "TIME", "CONDITION", "NONE");

	private static Map<String, Integer> grammarLevels() {
		Map<String, Integer> levels = new HashMap<>();
		try (InputStream stream = ConjugationGoldTest.class.getResourceAsStream(GRAMMAR);
			 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonArray all = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("constructions");
			for (JsonElement element : all) {
				JsonObject construction = element.getAsJsonObject();
				levels.put(construction.get("id").getAsString(), construction.get("level").getAsInt());
			}
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
		return levels;
	}

	@Test
	void taskSentencesHaveEveryColumnAndASaneValueInEach() {
		List<String[]> rows = table(TASKS, 8);
		assertTrue(rows.size() >= 120, "only " + rows.size() + " task sentences");
		Pattern korean = Pattern.compile("[가-힣 ]+");
		for (String[] row : rows) {
			String where = row[6];
			for (int i = 0; i < row.length; i++) {
				assertFalse(row[i].isEmpty(), where + ": column " + i + " is empty");
				assertEquals(row[i].strip(), row[i], where + ": stray whitespace in column " + i);
			}
			assertTrue(row[0].matches("[A-Z_]+"), where + ": goal type " + row[0]);
			assertTrue(RELATIONS.contains(row[2]), where + ": relation " + row[2]);
			assertTrue(ROLES.contains(row[4]), where + ": role " + row[4]);
			int level = Integer.parseInt(row[5]);
			assertTrue(level >= 1 && level <= 6, where + ": level " + level);
			assertTrue(korean.matcher(row[6]).matches(), where + ": the sentence is Hangul and spaces only");
			assertEquals(row[6].replaceAll(" +", " "), row[6], where + ": double space");
			assertTrue(Normalizer.isNormalized(row[6], Normalizer.Form.NFC), where + ": not NFC");
		}
	}

	@Test
	void taskSentencesAreUniqueAndSpreadOverLevelsRelationsAndRoles() {
		List<String[]> rows = table(TASKS, 8);
		Set<String> sentences = new HashSet<>();
		Set<String> relations = new HashSet<>();
		Set<String> roles = new HashSet<>();
		Set<Integer> levels = new HashSet<>();
		for (String[] row : rows) {
			assertTrue(sentences.add(row[6]), "duplicate sentence " + row[6]);
			relations.add(row[2]);
			roles.add(row[4]);
			levels.add(Integer.parseInt(row[5]));
		}
		assertTrue(relations.containsAll(Set.of("ON_TOP_OF", "UNDER", "NEXT_TO", "IN_FRONT_OF", "BEHIND", "INSIDE",
				"OUTSIDE", "BETWEEN")), "missing relations: " + relations);
		assertTrue(roles.containsAll(Set.of("TARGET", "ACTIVITY_PLACE", "RECIPIENT", "MATERIAL", "DIRECTION",
				"INSTRUMENT")), "missing roles: " + roles);
		assertEquals(Set.of(1, 2, 3, 4, 5, 6), levels);
	}

	@Test
	void taskSentenceLevelsFollowTheirGrammarIds() {
		Map<String, Integer> grammar = grammarLevels();
		for (String[] row : table(TASKS, 8)) {
			String[] ids = row[7].split(";");
			int top = 0;
			for (String id : ids) {
				assertTrue(grammar.containsKey(id), row[6] + ": unknown grammar id " + id);
				top = Math.max(top, grammar.get(id));
			}
			assertEquals(top, Integer.parseInt(row[5]), row[6] + ": the level is the highest grammar level in the sentence");
		}
	}

	@Test
	void taskSentenceParticlesMatchTheirFinalConsonant() {
		// 을/를, 이/가 and 와/과 after a noun; words that merely end in these syllables are listed.
		Set<String> notParticles = Set.of("마을", "사과", "지을", "놓을", "마실");
		for (String[] row : table(TASKS, 8)) {
			for (String token : row[6].split(" ")) {
				if (token.length() < 2 || notParticles.contains(token)) {
					continue;
				}
				char particle = token.charAt(token.length() - 1);
				char before = token.charAt(token.length() - 2);
				boolean batchim = jong(before) > 0;
				switch (particle) {
					case '을' -> assertTrue(batchim, row[6] + ": " + token);
					case '를' -> assertFalse(batchim, row[6] + ": " + token);
					case '와' -> assertFalse(batchim, row[6] + ": " + token);
					case '과' -> assertTrue(batchim, row[6] + ": " + token);
					default -> {
					}
				}
				if (token.endsWith("으로")) {
					assertTrue(jong(token.charAt(token.length() - 3)) > 0 && jong(token.charAt(token.length() - 3)) != 8,
							row[6] + ": " + token);
				} else if (token.endsWith("로") && token.length() >= 2 && syllable(before)) {
					assertTrue(jong(before) == 0 || jong(before) == 8, row[6] + ": " + token);
				}
			}
		}
	}
}
