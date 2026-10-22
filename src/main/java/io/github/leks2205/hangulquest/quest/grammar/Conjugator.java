package io.github.leks2205.hangulquest.quest.grammar;

import io.github.leks2205.hangulquest.util.hangul.HangulCompose;
import io.github.leks2205.hangulquest.util.hangul.HangulCompose.Syllable;

/**
 * Korean verb and adjective morphology from the dictionary form. Pure; no Minecraft types.
 *
 * <p>{@link #classify} decides the irregular class (from the lexicon irregulars.json first, then
 * from the spelling), {@link #of} wraps a word, and the {@link Word} methods build the forms:
 * {@code Conjugator.of("돕다").eoyo()} gives 도와요. Honorific replacements (먹다 → 드세요,
 * 자다 → 주무세요) are not handled; 들다 → 드세요 is the ordinary ㄹ-drop.
 */
public final class Conjugator {
	/** The conjugation classes of the gold table. */
	public enum Irregular {
		REG("reg"), HADA("hada"), EU_DROP("eu_drop"), RIEUL_DROP("rieul_drop"), B_IRR("b_irr"), D_IRR("d_irr"),
		REU_IRR("reu_irr"), S_IRR("s_irr"), H_IRR("h_irr"), LEO_IRR("leo_irr"), U_IRR("u_irr");

		private final String id;

		Irregular(String id) {
			this.id = id;
		}

		/** The class name used in irregulars.json and the gold table: reg, b_irr, ... */
		public String id() {
			return id;
		}

		public static Irregular byId(String id) {
			for (Irregular irregular : values()) {
				if (irregular.id.equals(id)) {
					return irregular;
				}
			}
			throw new IllegalArgumentException("unknown conjugation class: " + id);
		}
	}

	private Conjugator() {
	}

	/** The class of a dictionary form: lexicon first, then ㅎ/르/ㄹ/ㅡ by spelling, otherwise regular. */
	public static Irregular classify(String dictionary) {
		requireDictionary(dictionary);
		Irregular listed = Lexicon.SHARED.listed(dictionary);
		if (listed != null) {
			return listed;
		}
		if (dictionary.endsWith("하다")) {
			return Irregular.HADA;
		}
		char last = lastOf(stemOf(dictionary));
		Syllable syllable = syllable(last);
		if (syllable.jong() == 'ㄹ') {
			return Irregular.RIEUL_DROP;
		}
		if (syllable.jong() == 0 && syllable.jung() == 'ㅡ') {
			// 르 verbs double the ㄹ (누르다 → 눌러요); the ones that do not are in the lexicon.
			return syllable.cho() == 'ㄹ' && stemOf(dictionary).length() > 1 ? Irregular.REU_IRR : Irregular.EU_DROP;
		}
		return Irregular.REG;
	}

	public static boolean isAdjective(String dictionary) {
		requireDictionary(dictionary);
		return Lexicon.SHARED.isAdjective(dictionary);
	}

	/**
	 * Whether the text holds a conjugated form of the verb or adjective (reverse analysis for a clue that
	 * teaches a verb: 올라가요, 올라갔어요 and 올라가세요 hold 올라가다). A word of the text is the form itself, or
	 * starts with a form of three syllables or more, so 사람 is no 사다 and 올라가요. keeps its full stop.
	 */
	public static boolean contains(String text, String dictionary) {
		if (text == null || dictionary == null || !dictionary.endsWith("다")) {
			return false;
		}
		if (text.contains(dictionary)) {
			return true;
		}
		java.util.Set<String> forms;
		try {
			forms = of(dictionary).forms();
		} catch (RuntimeException unknown) {
			return false;
		}
		for (String token : text.split("[\\s.,!?]+")) {
			for (String form : forms) {
				if (token.equals(form) || form.length() >= 3 && token.startsWith(form)) {
					return true;
				}
			}
		}
		return false;
	}

	public static Word of(String dictionary) {
		return new Word(dictionary, classify(dictionary), isAdjective(dictionary));
	}

	/** For the second reading of a spelling: {@code of("묻다", Irregular.REG, false)} is "bury". */
	public static Word of(String dictionary, Irregular irregular, boolean adjective) {
		requireDictionary(dictionary);
		return new Word(dictionary, irregular, adjective);
	}

	/**
	 * A verb or adjective with its class. Every method returns the finished surface form, including the
	 * fixed words of a phrase ({@code suIsseoyo} gives "먹을 수 있어요"); the bare pieces are {@link #eo},
	 * {@link #future} and {@link #eun}.
	 */
	public record Word(String dictionary, Irregular irregular, boolean adjective) {
		public Word {
			requireDictionary(dictionary);
		}

		public String stem() {
			return stemOf(dictionary);
		}

		// ----------------------------------------------------------------------- the gold table

		/** -아/어요: 먹어요, 가요, 도와요, 빨개요. */
		public String eoyo() {
			return eo() + "요";
		}

		/** -았/었어요: 먹었어요, 갔어요, 도왔어요. */
		public String past() {
			return pastStem() + "어요";
		}

		/** -(으)세요: 먹으세요, 가세요, 만드세요. */
		public String seyo() {
			return eu("세요");
		}

		/** -고: 먹고, 만들고. */
		public String go() {
			return stem() + "고";
		}

		/** The -(으)ㄹ adnominal: 먹을, 갈, 만들, 주울. */
		public String future() {
			return eu("ㄹ");
		}

		/** -(으)면: 먹으면, 가면, 만들면. */
		public String myeon() {
			return eu("면");
		}

		/** -아/어서: 먹어서, 가서. */
		public String aseo() {
			return eo() + "서";
		}

		/** -지 마세요 (verbs): 먹지 마세요. */
		public String jimaseyo() {
			return stem() + "지 마세요";
		}

		// ----------------------------------------------------------------------- the ladder

		/** -아/어야 해요: 먹어야 해요. */
		public String eoyaHaeyo() {
			return eo() + "야 해요";
		}

		/** -기 전에: 먹기 전에. */
		public String giJeone() {
			return stem() + "기 전에";
		}

		/** The past adnominal -(으)ㄴ (and the adjective one): 먹은, 간, 만든, 큰, 어두운. */
		public String eun() {
			return eu("ㄴ");
		}

		/** -(으)ㄴ 후에: 먹은 후에, 걸은 후에. */
		public String eunHue() {
			return eun() + " 후에";
		}

		/** The present adnominal: 먹는, 가는, 만드는; an adjective takes -(으)ㄴ instead (큰), 있다/없다 stay -는. */
		public String neun() {
			return usesNeun() ? plainN() + "는" : eun();
		}

		/** -고서: 먹고서. */
		public String goseo() {
			return stem() + "고서";
		}

		/** -지 않고: 먹지 않고. */
		public String jiAnko() {
			return stem() + "지 않고";
		}

		/** -(으)ㄹ 수 있어요: 먹을 수 있어요. */
		public String suIsseoyo() {
			return future() + " 수 있어요";
		}

		/** -(으)ㄹ 수밖에 없어요: 먹을 수밖에 없어요. */
		public String suBakke() {
			return future() + " 수밖에 없어요";
		}

		/** -(으)러: 먹으러, 캐러, 만들러. */
		public String eureo() {
			return eu("러");
		}

		/** -지만: 먹지만. */
		public String jiman() {
			return stem() + "지만";
		}

		/** -(으)니까: 먹으니까, 가니까, 아니까. */
		public String nikka() {
			return eu("니까");
		}

		/** -아/어도: 먹어도. */
		public String ado() {
			return eo() + "도";
		}

		/** -는데 (verbs) / -(으)ㄴ데 (adjectives): 먹는데, 큰데. */
		public String neunde() {
			return usesNeun() ? plainN() + "는데" : eu("ㄴ데");
		}

		/** The adverbial -게: 크게, 먹게. */
		public String ge() {
			return stem() + "게";
		}

		/** -고 있어요: 먹고 있어요. */
		public String goIsseoyo() {
			return stem() + "고 있어요";
		}

		// ----------------------------------------------------------------------- the other ladder forms

		/** -(으)ㄹ 거예요: 먹을 거예요. */
		public String geoyeyo() {
			return future() + " 거예요";
		}

		/** -고 싶어요: 먹고 싶어요. */
		public String goSipeo() {
			return stem() + "고 싶어요";
		}

		/** -지 않아요: 먹지 않아요. */
		public String jiAnayo() {
			return stem() + "지 않아요";
		}

		/** -(으)ㄹ게요: 먹을게요, 갈게요. */
		public String eulgeyo() {
			return eu("ㄹ게요");
		}

		/** -(으)면 안 돼요: 먹으면 안 돼요. */
		public String myeonAnDoeyo() {
			return myeon() + " 안 돼요";
		}

		/** -(스)ㅂ니다: 먹습니다, 갑니다, 엽니다. */
		public String hapnida() {
			String stem = stem();
			char last = lastOf(stem);
			Syllable syllable = syllable(last);
			if (syllable.jong() == 0 || syllable.jong() == 'ㄹ') {
				return initOf(stem) + withJong(last, 'ㅂ') + "니다";
			}
			return stem + "습니다";
		}

		/** 반말 -아/어: 먹어, 가. */
		public String banmal() {
			return eo();
		}

		/** The plain written style (한다체): 먹는다, 간다, 연다; an adjective keeps the dictionary form. */
		public String handa() {
			if (adjective || existential()) {
				return dictionary;
			}
			String stem = stem();
			char last = lastOf(stem);
			Syllable syllable = syllable(last);
			if (syllable.jong() == 0 || syllable.jong() == 'ㄹ') {
				return initOf(stem) + withJong(last, 'ㄴ') + "다";
			}
			return stem + "는다";
		}

		/** -듯이: 먹듯이. */
		public String deusi() {
			return stem() + "듯이";
		}

		/** -(으)ㄹ수록: 먹을수록, 갈수록. */
		public String eulsurok() {
			return eu("ㄹ수록");
		}

		/** -기 마련이에요: 먹기 마련이에요. */
		public String giMaryeon() {
			return stem() + "기 마련이에요";
		}

		/** -는 동안: 먹는 동안, 만드는 동안. */
		public String neunDongan() {
			return plainN() + "는 동안";
		}

		// ----------------------------------------------------------------------- building blocks

		/** The 아/어 form without 요: 먹어, 가, 와, 해, 도와, 빨개. The base of 요, 서, 도, 야 and the past. */
		public String eo() {
			String stem = stem();
			return switch (irregular) {
				case HADA -> hada(stem);
				case D_IRR -> {
					String liquid = closed(stem, 'ㄹ');
					yield liquid + harmony(lastOf(liquid));
				}
				case B_IRR -> open(stem) + (Lexicon.SHARED.takesWa(dictionary) ? '와' : '워');
				case S_IRR -> {
					// no contraction after the dropped ㅅ: 지어요, 나아요, 부어요
					String open = open(stem);
					yield open + harmony(lastOf(open));
				}
				case H_IRR -> hIrregular(stem);
				case LEO_IRR -> stem + "러";
				case REU_IRR -> reuIrregular(stem);
				case EU_DROP -> euDrop(stem);
				case U_IRR -> uIrregular(stem);
				case REG, RIEUL_DROP -> regularEo(stem);
			};
		}

		/** The past stem: 먹었, 갔, 했. */
		public String pastStem() {
			String eo = eo();
			return initOf(eo) + withJong(lastOf(eo), 'ㅆ');
		}

		/** The inflected forms a clue can hold for this word: 가요, 갔어요, 가세요, 가고, 갈, 가면, 가서, 가지 마세요 ... */
		public java.util.Set<String> forms() {
			java.util.Set<String> forms = new java.util.LinkedHashSet<>();
			for (String form : new String[] {stem(), eo(), eoyo(), past(), seyo(), go(), future(), myeon(), aseo(),
					eun(), neun(), ge(), goseo(), eureo(), jiman(), nikka(), ado(), neunde(), eoyaHaeyo(), giJeone(),
					jiAnko(), jimaseyo()}) {
				if (form != null && !form.isBlank()) {
					forms.add(form.strip());
				}
			}
			return forms;
		}

		// ----------------------------------------------------------------------- internals

		/**
		 * Joins a -(으)X ending: the stem is the one before a vowel (주우, 걸, 빨가), 으 is added after a
		 * consonant, and a ㄹ stem loses its ㄹ before ㄴ, ㅂ and ㅅ. The ending starts with a syllable
		 * (면, 세요, 니까) or with a bare consonant that becomes the final of the stem (ㄴ, ㄹ).
		 */
		private String eu(String ending) {
			String stem = stem();
			String base = stem;
			boolean needsEu = false;
			boolean rieulStem = false;
			switch (irregular) {
				case D_IRR -> {
					base = closed(stem, 'ㄹ');
					needsEu = true;
				}
				case B_IRR -> base = open(stem) + "우";
				case S_IRR -> {
					base = open(stem);
					needsEu = true;
				}
				case H_IRR -> base = open(stem);
				default -> {
					int jong = syllable(lastOf(stem)).jong();
					rieulStem = jong == 'ㄹ';
					needsEu = jong != 0 && !rieulStem;
				}
			}
			char head = ending.charAt(0);
			boolean bare = !HangulCompose.isSyllable(head);
			char onset = bare ? head : syllable(head).cho();
			if (rieulStem && (onset == 'ㄴ' || onset == 'ㅂ' || onset == 'ㅅ')) {
				base = open(base);
				rieulStem = false;
			}
			if (bare) {
				String rest = ending.substring(1);
				if (needsEu) {
					return base + HangulCompose.compose('ㅇ', 'ㅡ', head) + rest;
				}
				if (rieulStem && head == 'ㄹ') {
					return base + rest;
				}
				return initOf(base) + withJong(lastOf(base), head) + rest;
			}
			return base + (needsEu ? "으" : "") + ending;
		}

		/** The stem before ㄴ (는, ㄴ다): a final ㄹ drops, 살다 → 사는. */
		private String plainN() {
			String stem = stem();
			return syllable(lastOf(stem)).jong() == 'ㄹ' ? open(stem) : stem;
		}

		/** 있다 and 없다 words are conjugated like verbs even when they are adjectives (맛있는, 없는). */
		private boolean usesNeun() {
			return !adjective || existential();
		}

		private boolean existential() {
			String stem = stem();
			return stem.endsWith("있") || stem.endsWith("없");
		}

	}

	// --------------------------------------------------------------------------- 아/어 rules

	/** A stem with no irregularity: contract the vowel where the syllable is open, otherwise add 아/어. */
	private static String regularEo(String stem) {
		char last = lastOf(stem);
		Syllable syllable = syllable(last);
		String ending = harmony(last);
		if (syllable.jong() != 0) {
			return stem + ending;
		}
		String head = initOf(stem);
		return switch (syllable.jung()) {
			// the vowel absorbs the ending: 가+아 → 가, 서+어 → 서, 캐+어 → 캐, 켜+어 → 켜
			case 'ㅏ', 'ㅓ', 'ㅐ', 'ㅕ', 'ㅘ', 'ㅝ', 'ㅙ' -> stem;
			case 'ㅗ' -> head + HangulCompose.compose(syllable.cho(), 'ㅘ', (char) 0);
			case 'ㅜ' -> head + HangulCompose.compose(syllable.cho(), 'ㅝ', (char) 0);
			case 'ㅣ' -> head + HangulCompose.compose(syllable.cho(), 'ㅕ', (char) 0);
			case 'ㅚ' -> head + HangulCompose.compose(syllable.cho(), 'ㅙ', (char) 0);
			// no contraction: 베어, 쉬어, 쥐어, 뛰어
			default -> stem + ending;
		};
	}

	private static String hada(String stem) {
		if (lastOf(stem) != '하') {
			throw new IllegalArgumentException(stem + "다 is not a 하다 word");
		}
		return initOf(stem) + '해';
	}

	/** 푸다: the 우 drops, 푸 → 퍼. */
	private static String uIrregular(String stem) {
		Syllable syllable = syllable(lastOf(stem));
		if (syllable.jung() != 'ㅜ' || syllable.jong() != 0) {
			throw new IllegalArgumentException(stem + "다 is not a 푸다 word");
		}
		return initOf(stem) + HangulCompose.compose(syllable.cho(), 'ㅓ', (char) 0);
	}

	/** ㅡ-drop: 쓰 → 써, 모으 → 모아, 아프 → 아파, 따르 → 따라; the vowel follows the syllable before. */
	private static String euDrop(String stem) {
		char last = lastOf(stem);
		Syllable syllable = syllable(last);
		if (syllable.jung() != 'ㅡ' || syllable.jong() != 0) {
			throw new IllegalArgumentException(stem + " does not end in ㅡ");
		}
		String head = initOf(stem);
		char vowel = head.isEmpty() ? 'ㅓ' : yang(lastOf(head)) ? 'ㅏ' : 'ㅓ';
		return head + HangulCompose.compose(syllable.cho(), vowel, (char) 0);
	}

	/** 르-irregular: 누르 → 눌러, 자르 → 잘라, 서두르 → 서둘러; the ㄹ goes on the syllable before. */
	private static String reuIrregular(String stem) {
		String head = initOf(stem);
		if (lastOf(stem) != '르' || head.isEmpty()) {
			throw new IllegalArgumentException(stem + " is not a 르 stem");
		}
		char before = lastOf(head);
		if (syllable(before).jong() != 0) {
			throw new IllegalArgumentException(stem + ": a 르 stem needs an open syllable before it");
		}
		return initOf(head) + withJong(before, 'ㄹ') + (yang(before) ? "라" : "러");
	}

	/** ㅎ-irregular adjectives: 빨갛 → 빨개, 하얗 → 하얘, 그렇 → 그래, 퍼렇 → 퍼레. */
	private static String hIrregular(String stem) {
		String open = open(stem);
		char last = lastOf(open);
		Syllable syllable = syllable(last);
		char vowel = switch (syllable.jung()) {
			case 'ㅏ' -> 'ㅐ';
			case 'ㅑ' -> 'ㅒ';
			case 'ㅕ' -> 'ㅖ';
			case 'ㅓ' -> demonstrative(stem) ? 'ㅐ' : 'ㅔ';
			default -> throw new IllegalArgumentException(stem + " is not a ㅎ-irregular stem");
		};
		return initOf(open) + HangulCompose.compose(syllable.cho(), vowel, (char) 0);
	}

	/** 그렇다, 이렇다, 저렇다, 어떻다, 아무렇다 contract to ㅐ (그래요); other ㅓ stems to ㅔ (퍼레요). */
	private static boolean demonstrative(String stem) {
		return stem.endsWith("그렇") || stem.endsWith("이렇") || stem.endsWith("저렇") || stem.endsWith("어떻")
				|| stem.endsWith("아무렇");
	}

	/** The ending vowel 아 after the bright vowels ㅏ ㅑ ㅗ ㅛ (얇아요), 어 otherwise, by the vowel of this syllable. */
	private static String harmony(char syllable) {
		return yang(syllable) ? "아" : "어";
	}

	private static boolean yang(char syllable) {
		char vowel = syllable(syllable).jung();
		return vowel == 'ㅏ' || vowel == 'ㅑ' || vowel == 'ㅗ' || vowel == 'ㅛ';
	}

	// --------------------------------------------------------------------------- syllable helpers

	private static void requireDictionary(String dictionary) {
		if (dictionary == null || dictionary.length() < 2 || !dictionary.endsWith("다")) {
			throw new IllegalArgumentException("not a dictionary form: " + dictionary);
		}
		for (int i = 0; i < dictionary.length(); i++) {
			if (!HangulCompose.isSyllable(dictionary.charAt(i))) {
				throw new IllegalArgumentException("not a dictionary form: " + dictionary);
			}
		}
	}

	private static String stemOf(String dictionary) {
		return dictionary.substring(0, dictionary.length() - 1);
	}

	private static char lastOf(String text) {
		return text.charAt(text.length() - 1);
	}

	private static String initOf(String text) {
		return text.substring(0, text.length() - 1);
	}

	private static Syllable syllable(char c) {
		Syllable syllable = HangulCompose.decompose(c);
		if (syllable == null) {
			throw new IllegalArgumentException("not a Hangul syllable: " + c);
		}
		return syllable;
	}

	/** The syllable with this final consonant; 0 for none. */
	private static char withJong(char c, char jong) {
		Syllable syllable = syllable(c);
		return HangulCompose.compose(syllable.cho(), syllable.jung(), jong);
	}

	/** The text with its last syllable open: 줍 → 주, 알 → 아. */
	private static String open(String text) {
		return initOf(text) + withJong(lastOf(text), (char) 0);
	}

	/** The text with its last final consonant replaced: 걷 → 걸. */
	private static String closed(String text, char jong) {
		return initOf(text) + withJong(lastOf(text), jong);
	}
}
