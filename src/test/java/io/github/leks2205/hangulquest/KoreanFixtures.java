package io.github.leks2205.hangulquest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Origin;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Test-side stand-in for the in-game catalog: the curated concepts plus the vanilla Korean names
 * from names.json for every item, entity and biome (the game builds the same from the language
 * tables), so generated sentences resolve conditions exactly as a player would see them.
 */
final class KoreanFixtures {
	private KoreanFixtures() {
	}

	static Catalog catalog() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		try (InputStream stream = KoreanFixtures.class.getResourceAsStream(
				"/assets/hangulquest/data/names.json")) {
			if (stream == null) {
				return catalog;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonObject names = JsonParser.parseReader(reader).getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : names.entrySet()) {
					addName(catalog, entry.getKey(), entry.getValue());
				}
			}
		} catch (java.io.IOException broken) {
			throw new IllegalStateException(broken);
		}
		return catalog;
	}

	private static void addName(Catalog catalog, String key, JsonElement value) {
		if (!value.isJsonArray()) {
			return;
		}
		JsonArray pair = value.getAsJsonArray();
		if (pair.size() < 2) {
			return;
		}
		String[] parts = key.split("\\.");
		if (parts.length != 3 || !parts[1].equals("minecraft")) {
			return;
		}
		String id = "minecraft:" + parts[2];
		String ko = pair.get(0).getAsString();
		String de = pair.get(1).getAsString();
		switch (parts[0]) {
			case "item", "block" -> catalog.add(new Word("mc:item:" + id, ko, de, de, de, Category.MISC, 2,
					null, id, null, null, null, Origin.MINECRAFT, false, null));
			case "entity" -> catalog.add(new Word("mc:entity:" + id, ko, de, de, de, Category.ANIMALS, 2,
					null, null, id, null, null, Origin.MINECRAFT, false, null));
			case "biome" -> catalog.add(new Word("mc:biome:" + id, ko, de, de, de, Category.MISC, 2,
					null, null, null, id, null, Origin.MINECRAFT, false, null));
			default -> {
			}
		}
	}

	static SentenceBuilder.Namer namer(Catalog catalog) {
		return new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return catalog.byBiomeId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String entity(String id) {
				return catalog.byEntityId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String block(String id) {
				return catalog.byItemId(id).map(word -> word.ko).orElse(null);
			}
		};
	}

	private static Map<String, String> koNames;

	/** Vanilla Korean names straight from names.json: {@code block.minecraft.stone} -> 돌. */
	static synchronized Map<String, String> koNames() {
		if (koNames == null) {
			Map<String, String> map = new java.util.HashMap<>();
			try (InputStream stream = KoreanFixtures.class.getResourceAsStream(
					"/assets/hangulquest/data/names.json")) {
				try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
					for (Map.Entry<String, JsonElement> entry
							: JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
						if (entry.getValue().isJsonArray() && entry.getValue().getAsJsonArray().size() > 0) {
							map.put(entry.getKey(), entry.getValue().getAsJsonArray().get(0).getAsString());
						}
					}
				}
			} catch (java.io.IOException broken) {
				throw new IllegalStateException(broken);
			}
			koNames = map;
		}
		return koNames;
	}

	private static String path(String id) {
		return id == null ? "" : id.substring(id.indexOf(':') + 1);
	}

	/**
	 * Names every goal target by its official vanilla Korean name, never by a taught concept. Used for
	 * the finale lines that are written into the questline JSON, where the text must say what the
	 * step really checks.
	 */
	static SentenceBuilder.Namer registryNamer() {
		return new SentenceBuilder.Namer() {
			private String first(String... keys) {
				for (String key : keys) {
					String name = koNames().get(key);
					if (name != null) {
						return name;
					}
				}
				return null;
			}

			@Override
			public String biome(String id) {
				return first("biome.minecraft." + path(id));
			}

			@Override
			public String entity(String id) {
				return first("entity.minecraft." + path(id));
			}

			@Override
			public String block(String id) {
				return first("block.minecraft." + path(id), "item.minecraft." + path(id));
			}

			@Override
			public String item(String id) {
				return first("item.minecraft." + path(id), "block.minecraft." + path(id));
			}
		};
	}

	private static Catalog shared;

	private static synchronized Catalog sharedCatalog() {
		if (shared == null) {
			shared = catalog();
		}
		return shared;
	}

	/**
	 * The finale sentence for a trail, from its last step's goal and conditions. The word the last step
	 * teaches names what it stands for (우유, not 우유 양동이); every other noun is its registry name.
	 */
	static String ceremony(io.github.leks2205.hangulquest.quest.Questline trail) {
		io.github.leks2205.hangulquest.quest.QuestStep step = trail.finale();
		Word word = step.teach().isEmpty() ? null : sharedCatalog().byConceptId(step.teach().get(0)).orElse(null);
		return SentenceBuilder.ceremony(step.goal(), word, step.conditions(),
				io.github.leks2205.hangulquest.quest.spatial.StepNamer.of(registryNamer(), step));
	}

	/** The registry id a goal points at, or null for height goals. */
	static String targetOf(Goal goal) {
		return switch (goal) {
			case Goal.BreakBlock g -> g.block();
			case Goal.PlaceBlock g -> g.block();
			case Goal.AcquireItem g -> g.item();
			case Goal.ConsumeItem g -> g.item();
			case Goal.LookAtBlock g -> g.block();
			case Goal.UseBlock g -> g.block();
			case Goal.LookAtEntity g -> g.entity();
			case Goal.DefeatEntity g -> g.entity();
			case Goal.InteractEntity g -> g.entity();
			case Goal.ReachBiome g -> g.biome();
			case Goal.ReachDimension g -> g.dimension();
			case Goal.ReachHeight g -> null;
			case Goal.PlaceRel g -> g.block();
			case Goal.BuildPattern g -> g.material();
			case Goal.PlaceOnPattern g -> g.block();
			case Goal.Craft g -> g.item();
			case Goal.Smelt g -> g.output();
			case Goal.Give g -> g.item();
			case Goal.PutIn g -> g.item();
			case Goal.HoldWhile g -> g.item();
			case Goal.Sequence g -> null;
			case Goal.Avoid g -> g.action() == null ? null : targetOf(g.action());
		};
	}

	/** Compact, stable description of a goal for reports: "PlaceBlock minecraft:torch x1". */
	static String describe(Goal goal) {
		String target = targetOf(goal);
		int count = switch (goal) {
			case Goal.BreakBlock g -> g.count();
			case Goal.PlaceBlock g -> g.count();
			case Goal.AcquireItem g -> g.count();
			case Goal.ConsumeItem g -> g.count();
			case Goal.DefeatEntity g -> g.count();
			case Goal.Craft g -> g.count();
			case Goal.Smelt g -> g.count();
			case Goal.Give g -> g.count();
			case Goal.PutIn g -> g.count();
			default -> 0;
		};
		String suffix = switch (goal) {
			case Goal.ReachHeight g -> g.minY() + ".." + g.maxY();
			case Goal.PlaceRel g -> g.relation() + " " + g.anchor() + (g.count() > 1 ? " x" + g.count() : "");
			case Goal.BuildPattern g -> g.pattern() + " " + g.size();
			case Goal.PlaceOnPattern g -> g.relation() + " " + g.pattern() + " " + g.material();
			case Goal.Sequence g -> g.parts().stream().map(KoreanFixtures::describe)
					.collect(java.util.stream.Collectors.joining(" > ", "[", "]"));
			case Goal.Avoid g -> "[" + (g.action() == null ? "" : describe(g.action())) + "]";
			default -> count > 0 ? "x" + count : "";
		};
		return goal.getClass().getSimpleName() + " " + (target == null ? "" : target + " ") + suffix;
	}
}
