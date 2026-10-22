package io.github.leks2205.hangulquest.quest.goal;

import java.util.Map;

/**
 * Tool and armour tiers. One word stands for the piece in every material (곡괭이, 투구), and the registry
 * names each one by its material (철 곡괭이, 나무 곡괭이). A sentence that asks for one specific tier says it
 * even when the general word is what the learner was taught: "곡괭이" is taught, "철 곡괭이를 만드세요" is
 * printed. The same goes for armour: 갑옷, 투구, 바지, 신발.
 *
 * <p>The material words are the first word of the vanilla names (names.json); a test keeps them in step.
 */
public final class Tiers {
	/** Registry material to the Korean material word. */
	private static final Map<String, String> MATERIALS = Map.of(
			"wooden", "나무", "stone", "돌", "copper", "구리", "golden", "금", "iron", "철",
			"diamond", "다이아몬드", "netherite", "네더라이트", "chainmail", "사슬", "leather", "가죽");

	/** Registry piece to the general word that stands for it in every material. */
	private static final Map<String, String> PIECES = Map.of(
			"pickaxe", "곡괭이", "axe", "도끼", "shovel", "삽", "hoe", "괭이", "sword", "검",
			"helmet", "투구", "chestplate", "갑옷", "leggings", "바지", "boots", "신발");

	private Tiers() {
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

	/** The general Korean word of a registry piece (곡괭이 for pickaxe), or null. */
	public static String piece(String registryPiece) {
		return PIECES.get(registryPiece);
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
		return parts != null && PIECES.get(parts[1]).equals(word);
	}

	/**
	 * The name to say for an item: {@code name} itself, unless it is the general word of a tiered item, which
	 * then gets its material in front ("철 곡괭이").
	 */
	public static String qualify(String id, String name) {
		String[] parts = split(id);
		if (parts == null || name == null || !PIECES.get(parts[1]).equals(name)) {
			return name;
		}
		return MATERIALS.get(parts[0]) + " " + name;
	}
}
