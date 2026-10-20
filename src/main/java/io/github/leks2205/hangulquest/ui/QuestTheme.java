package io.github.leks2205.hangulquest.ui;

/** Shared branch-topic visuals for the journal tree and the HUD tracker. */
public final class QuestTheme {
	private QuestTheme() {
	}

	public static String icon(String topic) {
		if (topic == null) {
			return "minecraft:book";
		}
		return switch (topic) {
			case "돌" -> "minecraft:stone";
			case "나무" -> "minecraft:oak_log";
			case "음식" -> "minecraft:bread";
			case "물" -> "minecraft:water_bucket";
			case "밤" -> "minecraft:torch";
			case "산" -> "minecraft:goat_spawn_egg";
			case "마을" -> "minecraft:emerald";
			case "가족" -> "minecraft:red_bed";
			case "불" -> "minecraft:campfire";
			case "동물" -> "minecraft:cow_spawn_egg";
			case "우정" -> "minecraft:poppy";
			case "편지" -> "minecraft:paper";
			case "레드스톤" -> "minecraft:redstone";
			case "겨울" -> "minecraft:snowball";
			case "꽃" -> "minecraft:dandelion";
			default -> "minecraft:book";
		};
	}

	public static int color(String topic) {
		return switch (topic) {
			case "돌" -> 0xFF8C8C8C;
			case "나무" -> 0xFF8A5A2B;
			case "음식" -> 0xFFC98A2E;
			case "물" -> 0xFF3D6E9E;
			case "밤" -> 0xFF4B3A6B;
			case "산" -> 0xFF6E6E5A;
			case "마을" -> 0xFF4F7A3A;
			case "가족" -> 0xFF9E5A6B;
			case "불" -> 0xFFB04A2A;
			case "동물" -> 0xFF9A7B4F;
			case "우정" -> 0xFFC45A7A;
			case "편지" -> 0xFFC9B78A;
			case "레드스톤" -> 0xFFB03030;
			case "겨울" -> 0xFF8FB8D8;
			case "꽃" -> 0xFFD06A9A;
			default -> 0xFF8A7A62;
		};
	}
}
