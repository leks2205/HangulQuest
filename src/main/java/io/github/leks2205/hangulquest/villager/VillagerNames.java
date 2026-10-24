package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.SpeechLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The given names villagers get: 40 common Korean names per gender, each with the romanised id its spoken
 * self-introduction is stored under ({@code line.name.<id>.<voice>}). The first 24 are the names of 3.0 and
 * earlier, split by gender, so an old villager keeps its name. {@code data/villager-names.json} is the same table
 * for the voice tooling (a test keeps the two equal).
 */
public final class VillagerNames {
	/** A name, its gender and its romanised id (Revised Romanization, lower case, no separators). */
	public record Name(String ko, Gender gender, String id) {
	}

	public static final List<Name> MALE = List.of(
			// 3.0 names
			n("민준", "minjun"), n("하준", "hajun"), n("도윤", "doyun"), n("예준", "yejun"), n("시우", "siu"),
			n("준서", "junseo"), n("현우", "hyeonu"), n("지호", "jiho"), n("태민", "taemin"), n("한결", "hangyeol"),
			n("지민", "jimin"), n("가온", "gaon"),
			// new in 3.1
			n("서준", "seojun"), n("주원", "juwon"), n("건우", "geonu"), n("우진", "ujin"), n("선우", "seonu"),
			n("도현", "dohyeon"), n("유준", "yujun"), n("정우", "jeongu"), n("승우", "seungu"),
			n("승현", "seunghyeon"), n("준우", "junu"), n("지훈", "jihun"), n("은우", "eunu"), n("민재", "minjae"),
			n("현준", "hyeonjun"), n("윤우", "yunu"), n("이준", "ijun"), n("민성", "minseong"), n("준혁", "junhyeok"),
			n("성민", "seongmin"), n("동현", "donghyeon"), n("재윤", "jaeyun"), n("지환", "jihwan"),
			n("태윤", "taeyun"), n("은찬", "eunchan"), n("준영", "junyeong"), n("상현", "sanghyeon"),
			n("영민", "yeongmin"));

	public static final List<Name> FEMALE = List.of(
			// 3.0 names
			f("서연", "seoyeon"), f("서윤", "seoyun"), f("수아", "sua"), f("하은", "haeun"), f("나은", "naeun"),
			f("채원", "chaewon"), f("유나", "yuna"), f("소율", "soyul"), f("다은", "daeun"), f("보라", "bora"),
			f("나래", "narae"), f("지우", "jiu"),
			// new in 3.1
			f("지아", "jia"), f("하윤", "hayun"), f("서아", "seoa"), f("아린", "arin"), f("지안", "jian"),
			f("하린", "harin"), f("민서", "minseo"), f("윤서", "yunseo"), f("지유", "jiyu"), f("채은", "chaeeun"),
			f("수빈", "subin"), f("예린", "yerin"), f("서현", "seohyeon"), f("예은", "yeeun"), f("다인", "dain"),
			f("수연", "suyeon"), f("은서", "eunseo"), f("혜원", "hyewon"), f("지은", "jieun"), f("민지", "minji"),
			f("수진", "sujin"), f("은지", "eunji"), f("미나", "mina"), f("예나", "yena"), f("소연", "soyeon"),
			f("가은", "gaeun"), f("세아", "sea"), f("주아", "jua"));

	private static final Map<String, Name> BY_KO = new HashMap<>();

	static {
		for (Name name : MALE) {
			BY_KO.put(name.ko(), name);
		}
		for (Name name : FEMALE) {
			BY_KO.put(name.ko(), name);
		}
	}

	private VillagerNames() {
	}

	private static Name n(String ko, String id) {
		return new Name(ko, Gender.MALE, id);
	}

	private static Name f(String ko, String id) {
		return new Name(ko, Gender.FEMALE, id);
	}

	public static List<Name> pool(Gender gender) {
		return gender == Gender.MALE ? MALE : FEMALE;
	}

	/** Every name of both pools. */
	public static List<Name> all() {
		List<Name> all = new java.util.ArrayList<>(MALE);
		all.addAll(FEMALE);
		return all;
	}

	/** The pool entry of a Korean name, or null for a name that is in neither pool. */
	public static Name byKo(String ko) {
		return ko == null ? null : BY_KO.get(ko);
	}

	/**
	 * The name {@code villager} gets when {@code taken} are in use: its own slot in the pool of its gender, or the
	 * next free one; when all are taken, its own slot.
	 */
	public static Name pick(UUID villager, Gender gender, Set<String> taken) {
		List<Name> pool = pool(gender);
		int slot = Math.floorMod(VillagerProfiles.mix(villager, 3), pool.size());
		for (int step = 0; step < pool.size(); step++) {
			Name candidate = pool.get((slot + step) % pool.size());
			if (!taken.contains(candidate.ko())) {
				return candidate;
			}
		}
		return pool.get(slot);
	}

	/**
	 * The one line that says a name: "저는 민준이에요." in 해요체, "저는 서연입니다." in 합쇼체, "나는 지우야." in 반말.
	 * The copula follows the last syllable (이에요/예요, 이야/야).
	 */
	public static String selfIntro(String name, SpeechLevel level) {
		boolean batchim = hasBatchim(name);
		return switch (level) {
			case HAPSYO -> "저는 " + name + "입니다.";
			case BANMAL -> "나는 " + name + (batchim ? "이야." : "야.");
			default -> "저는 " + name + (batchim ? "이에요." : "예요.");
		};
	}

	/** True when the last syllable ends in a consonant. */
	static boolean hasBatchim(String word) {
		if (word == null || word.isEmpty()) {
			return false;
		}
		char last = word.charAt(word.length() - 1);
		return last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
	}
}
