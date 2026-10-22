package io.github.leks2205.hangulquest.quest.goal;

import java.util.Map;

/**
 * Tool and armour tiers. One word stands for the piece in every material (곡괭이, 갑옷), and the registry
 * names each one by its material (철 곡괭이, 철 흉갑, 가죽 조끼). A sentence that asks for one exact tier says the
 * official name even when the general word is what the learner was taught: "곡괭이" is taught, "철 곡괭이를
 * 만드세요" is printed; "갑옷" is taught, "철 흉갑을 입으세요" is printed (not "철 갑옷", which is no
 * vanilla name). A goal on any piece of the kind (a tag such as {@code #minecraft:chest_armor}) keeps the
 * general word: "갑옷을 입으세요".
 *
 * <p>Material and piece words are the ones of the vanilla names (names.json); a test keeps them in step.
 */
public final class Tiers {
	/** Registry material to the Korean material word. */
	private static final Map<String, String> MATERIALS = Map.of(
			"wooden", "나무", "stone", "돌", "copper", "구리", "golden", "금", "iron", "철",
			"diamond", "다이아몬드", "netherite", "네더라이트", "chainmail", "사슬", "leather", "가죽");

	/** Registry piece to the last word of its vanilla name (철 흉갑, 철 레깅스, 철 부츠). */
	private static final Map<String, String> PIECES = Map.of(
			"pickaxe", "곡괭이", "axe", "도끼", "shovel", "삽", "hoe", "괭이", "sword", "검",
			"helmet", "투구", "chestplate", "흉갑", "leggings", "레깅스", "boots", "부츠");

	/** Registry piece to the general word that stands for it in every material (the taught concept). */
	private static final Map<String, String> GENERAL = Map.of(
			"pickaxe", "곡괭이", "axe", "도끼", "shovel", "삽", "hoe", "괭이", "sword", "검",
			"helmet", "투구", "chestplate", "갑옷", "leggings", "바지", "boots", "신발");

	/** The leather set is named like clothes in the vanilla list, not by the piece word (가죽 조끼, 가죽 장화). */
	private static final Map<String, String> LEATHER = Map.of(
			"helmet", "가죽 모자", "chestplate", "가죽 조끼", "leggings", "가죽 바지", "boots", "가죽 장화");

	private static final java.util.Set<String> ARMOUR = java.util.Set.of("helmet", "chestplate", "leggings", "boots");

	/** Set while a hint is built: there a taught general word for armour stands in for the exact piece. */
	private static final ThreadLocal<Boolean> HINT = ThreadLocal.withInitial(() -> false);

	private Tiers() {
	}

	/** Runs the hint builder with the general armour words allowed to stand for the exact pieces. */
	public static <T> T forHint(java.util.function.Supplier<T> hint) {
		boolean before = HINT.get();
		HINT.set(true);
		try {
			return hint.get();
		} finally {
			HINT.set(before);
		}
	}

	/** The materials, as the registry spells them in item ids: wooden, stone, iron ... */
	public static java.util.Set<String> materials() {
		return MATERIALS.keySet();
	}

	/** The pieces, as the registry spells them: pickaxe, axe, helmet ... */
	public static java.util.Set<String> pieces() {
		return PIECES.keySet();
	}

	/** The Korean material word (철, 나무) of a registry material, or null. */
	public static String material(String registryMaterial) {
		return MATERIALS.get(registryMaterial);
	}

	/** The piece word of the vanilla names (흉갑 for chestplate), or null. */
	public static String piece(String registryPiece) {
		return PIECES.get(registryPiece);
	}

	/** The general Korean word of a registry piece (갑옷 for chestplate), or null. */
	public static String general(String registryPiece) {
		return GENERAL.get(registryPiece);
	}

	/** {material, piece} of a tiered item id such as minecraft:iron_pickaxe, or null for any other id. */
	private static String[] split(String id) {
		if (id == null || id.startsWith("#") || id.indexOf('|') >= 0) {
			return null;
		}
		String path = id.substring(id.indexOf(':') + 1);
		int cut = path.indexOf('_');
		if (cut <= 0) {
			return null;
		}
		String material = path.substring(0, cut);
		String piece = path.substring(cut + 1);
		return MATERIALS.containsKey(material) && PIECES.containsKey(piece) ? new String[]{material, piece} : null;
	}

	/** Whether the id is a tool or armour piece of one specific material. */
	public static boolean isTiered(String id) {
		return split(id) != null;
	}

	/**
	 * Whether {@code word} is the general word of this tiered item: 곡괭이 for an iron pickaxe, 갑옷 for an iron
	 * chestplate. A word that already holds the material ("철 곡괭이", "철 흉갑") is not.
	 */
	public static boolean isGeneralWordOf(String id, String word) {
		String[] parts = split(id);
		return parts != null && GENERAL.get(parts[1]).equals(word);
	}

	/**
	 * The name to say for an exact item: {@code name} itself, unless it is the general word of a tiered item,
	 * which then becomes the vanilla name (철 곡괭이, 철 흉갑, 가죽 조끼).
	 */
	public static String qualify(String id, String name) {
		String[] parts = split(id);
		if (parts == null || name == null || !GENERAL.get(parts[1]).equals(name)) {
			return name;
		}
		if (HINT.get() && ARMOUR.contains(parts[1])) {
			return name; // the hint keeps the word the player was taught: 신발, not 철 부츠
		}
		return official(parts[0], parts[1]);
	}

	/** The vanilla name of a material and piece: 철 흉갑, 가죽 조끼. */
	public static String official(String registryMaterial, String registryPiece) {
		if ("leather".equals(registryMaterial) && LEATHER.containsKey(registryPiece)) {
			return LEATHER.get(registryPiece);
		}
		return MATERIALS.get(registryMaterial) + " " + PIECES.get(registryPiece);
	}
}
