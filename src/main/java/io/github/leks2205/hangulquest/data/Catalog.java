package io.github.leks2205.hangulquest.data;

import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.util.TextClean;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Merged vocabulary: curated concepts, dynamic vanilla names and imported Lute words. */
public final class Catalog {
	private final Map<String, Word> byId = new LinkedHashMap<>();
	private final Map<String, Word> byItem = new HashMap<>();
	private final Map<String, Word> byEntity = new HashMap<>();
	private final Map<String, Word> byBiome = new HashMap<>();
	private final Map<String, Word> byTranslationKey = new HashMap<>();
	private final Map<String, Word> byConcept = new HashMap<>();
	private final Map<String, Concept> concepts = new LinkedHashMap<>();
	private final List<Word> all = new ArrayList<>();

	public static Catalog build(List<Concept> curated, LangTables tables, List<Word> luteWords) {
		Catalog catalog = new Catalog();
		for (Concept concept : curated) {
			catalog.concepts.put(concept.id, concept);
			catalog.add(concept.toWord());
		}
		catalog.addDynamic(tables);
		if (luteWords != null) {
			for (Word word : luteWords) {
				catalog.add(word);
			}
		}
		return catalog;
	}

	/** Test/authoring helper: curated concepts only, no registry access. */
	public static Catalog ofConcepts(List<Concept> curated) {
		Catalog catalog = new Catalog();
		for (Concept concept : curated) {
			catalog.concepts.put(concept.id, concept);
			catalog.add(concept.toWord());
		}
		return catalog;
	}

	public void add(Word word) {
		if (word == null || word.id == null) {
			return;
		}
		Word previous = byId.putIfAbsent(word.id, word);
		if (previous != null) {
			return;
		}
		all.add(word);
		// Only what the word names, never its picture: gunpowder must not resolve to 크리퍼.
		for (String item : word.referents) {
			byItem.putIfAbsent(item, word);
		}
		if (word.entityId != null) {
			byEntity.putIfAbsent(word.entityId, word);
		}
		if (word.biomeId != null) {
			byBiome.putIfAbsent(word.biomeId, word);
		}
		if (word.conceptId != null) {
			byConcept.putIfAbsent(word.conceptId, word);
		}
	}

	private void addDynamic(LangTables tables) {
		if (tables == null) {
			return;
		}
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (id == null || byItem.containsKey(id.toString())) {
				continue;
			}
			String key = item.getDescriptionId();
			Word word = dynamicWord(id, key, tables, false);
			if (word == null) {
				continue;
			}
			add(word);
			byTranslationKey.putIfAbsent(key, byItem.get(id.toString()));
		}

		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Identifier id = EntityType.getKey(type);
			if (id == null || byEntity.containsKey(id.toString())) {
				continue;
			}
			String key = type.getDescriptionId();
			Word word = dynamicWord(id, key, tables, true);
			if (word == null) {
				continue;
			}
			add(word);
			byTranslationKey.putIfAbsent(key, byEntity.get(id.toString()));
		}
	}

	/**
	 * The word for a vanilla item or entity, or null when it is not learnable. Needs a Korean name;
	 * the German name, English name and the interface language's name are glosses when present.
	 */
	static Word dynamicWord(Identifier id, String key, LangTables tables, boolean entity) {
		if (key == null || !tables.has(key)) {
			return null;
		}
		String ko = tables.ko(key);
		String de = tables.de(key);
		String en = tables.en(key);
		if (!acceptable(ko, de, id)) {
			return null;
		}
		Map<String, String> others = tables.otherNames(key);
		if (entity) {
			Category category = isHostile(id.getPath()) ? Category.COMBAT : Category.ANIMALS;
			return new Word("mc:entity:" + id, ko, de, en, de, category, 2, null, null, id.toString(), null,
					null, Origin.MINECRAFT, false, null, List.of(), others);
		}
		return new Word("mc:item:" + id, ko, de, en, de, itemCategory(id.getPath()), 2, null, id.toString(),
				null, null, null, Origin.MINECRAFT, false, null, List.of(id.toString()), others);
	}

	private static boolean acceptable(String ko, String de, Identifier id) {
		// Korean decides; the German name is only checked when the German file is there.
		if (ko == null || ko.isBlank()) {
			return false;
		}
		if (!TextClean.hasHangul(ko) || !TextClean.isHangulOnly(ko)) {
			return false;
		}
		if (ko.length() > 40 || (de != null && de.length() > 60)) {
			return false;
		}
		if (ko.equalsIgnoreCase(de)) {
			return false;
		}
		return !isTechnical(id.getPath());
	}

	/** Exact ids that are not learnable vocabulary. */
	private static final Set<String> TECHNICAL_IDS = Set.of(
			"air", "cave_air", "void_air", "light", "barrier", "jigsaw", "structure_block",
			"command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
			"debug_stick", "knowledge_book", "moving_piston", "player_head", "test_block",
			"test_instance_block");
	private static final String[] TECHNICAL_PREFIXES = {"music_disc_", "infested_", "debug_", "test_"};
	private static final String[] TECHNICAL_SUFFIXES = {"_spawn_egg", "_banner_pattern", "_smithing_template"};

	/**
	 * Whether a registry path is a technical or creative-only entry. Exact ids and
	 * prefix/suffix rules only: a substring test would also drop stairs (air), light_gray_*,
	 * light_blue_*, lightning_rod and daylight_detector.
	 */
	static boolean isTechnical(String path) {
		if (TECHNICAL_IDS.contains(path)) {
			return true;
		}
		for (String prefix : TECHNICAL_PREFIXES) {
			if (path.startsWith(prefix)) {
				return true;
			}
		}
		for (String suffix : TECHNICAL_SUFFIXES) {
			if (path.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	private static Category itemCategory(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		if (lower.endsWith("_pickaxe") || lower.endsWith("_axe") || lower.endsWith("_shovel")
				|| lower.endsWith("_hoe") || lower.equals("shears") || lower.equals("fishing_rod")
				|| lower.equals("bucket") || lower.endsWith("_bucket")) {
			return Category.TOOLS;
		}
		if (lower.endsWith("_sword") || lower.equals("bow") || lower.equals("crossbow")
				|| lower.equals("arrow") || lower.equals("shield") || lower.equals("trident")) {
			return Category.COMBAT;
		}
		if (lower.contains("helmet") || lower.contains("chestplate") || lower.contains("leggings")
				|| lower.contains("boots")) {
			return Category.TOOLS;
		}
		if (lower.equals("apple") || lower.equals("bread") || lower.equals("cookie")
				|| lower.equals("cake") || lower.contains("cooked") || lower.contains("beef")
				|| lower.contains("porkchop") || lower.contains("chicken") || lower.contains("mutton")
				|| lower.contains("cod") || lower.contains("salmon") || lower.contains("potato")
				|| lower.contains("carrot") || lower.contains("beetroot") || lower.contains("melon")
				|| lower.contains("honey") || lower.contains("stew") || lower.contains("soup")) {
			return Category.FOOD;
		}
		return Category.MISC;
	}

	private static boolean isHostile(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		return lower.contains("zombie") || lower.contains("skeleton") || lower.contains("spider")
				|| lower.contains("creeper") || lower.contains("enderman") || lower.contains("witch")
				|| lower.contains("slime") || lower.contains("phantom") || lower.contains("pillager")
				|| lower.contains("ravager") || lower.contains("blaze") || lower.contains("ghast")
				|| lower.contains("warden") || lower.contains("wither") || lower.contains("silverfish")
				|| lower.contains("magma") || lower.contains("guardian") || lower.contains("dragon");
	}

	public Optional<Word> byId(String id) {
		return Optional.ofNullable(id == null ? null : byId.get(id));
	}

	public Optional<Word> byConceptId(String conceptId) {
		Word word = byConcept.get(conceptId);
		if (word != null) {
			return Optional.of(word);
		}
		return byId("concept:" + conceptId);
	}

	/** The word that names this item. Resolves by referent only; a word's icon never counts. */
	public Optional<Word> byItemId(String itemId) {
		return Optional.ofNullable(itemId == null ? null : byItem.get(itemId));
	}

	public Optional<Word> byEntityId(String entityId) {
		return Optional.ofNullable(entityId == null ? null : byEntity.get(entityId));
	}

	public Optional<Word> byBiomeId(String biomeId) {
		return Optional.ofNullable(biomeId == null ? null : byBiome.get(biomeId));
	}

	public Optional<Word> byTranslationKey(String translationKey) {
		return Optional.ofNullable(translationKey == null ? null : byTranslationKey.get(translationKey));
	}

	public Optional<Concept> concept(String id) {
		return Optional.ofNullable(concepts.get(id));
	}

	public List<Word> all() {
		return all;
	}

	/** The curated concepts this catalog was built from. */
	public java.util.Collection<Concept> concepts() {
		return java.util.Collections.unmodifiableCollection(concepts.values());
	}

	public List<Word> byCategory(Category category) {
		List<Word> result = new ArrayList<>();
		for (Word word : all) {
			if (word.category == category && !word.phrase) {
				result.add(word);
			}
		}
		return result;
	}

	public List<Word> trailWords() {
		List<Word> result = new ArrayList<>();
		for (Word word : all) {
			if (word.conceptId != null && !word.phrase) {
				result.add(word);
			}
		}
		return result;
	}

	public List<Word> distractors(Word word, int count, Random random) {
		List<Word> pool = new ArrayList<>();
		Lang lang = Lang.current();
		String own = word.gloss(lang);
		for (Word candidate : all) {
			if (candidate.id.equals(word.id) || candidate.phrase || candidate.gloss(lang).isBlank()
					|| candidate.gloss(lang).equals(own)) {
				continue;
			}
			if (candidate.category == word.category) {
				pool.add(candidate);
			}
		}
		if (pool.size() < count) {
			for (Word candidate : all) {
				if (candidate.id.equals(word.id) || candidate.phrase || candidate.gloss(lang).isBlank()
						|| pool.contains(candidate) || candidate.gloss(lang).equals(own)) {
					continue;
				}
				pool.add(candidate);
			}
		}
		java.util.Collections.shuffle(pool, random);
		return pool.subList(0, Math.min(count, pool.size()));
	}

	public int size() {
		return all.size();
	}
}
