package io.github.leks2205.hangulquest.data;

import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManager.Selector;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which game language files {@link LangTables} reads: Korean, English, German if there, and the interface language. */
class LangTablesLoadTest {
	private static final String KEY = "item.minecraft.apple";

	/** A resource manager that serves only the given {@code lang/<file>} texts. */
	private static ResourceManager manager(Map<String, String> files) {
		return new ResourceManager() {
			@Override
			public Optional<Resource> getResource(Identifier id) {
				String text = files.get(id.getPath().substring("lang/".length()));
				return text == null ? Optional.empty() : Optional.of(new Resource((PackResources) null,
						() -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
			}

			@Override
			public Set<String> getNamespaces() {
				return Set.of("minecraft");
			}

			@Override
			public List<Resource> getResourceStack(Identifier id) {
				return List.of();
			}

			@Override
			public Map<Identifier, Resource> listResources(String path, Selector filter) {
				return Map.of();
			}

			@Override
			public Map<Identifier, List<Resource>> listResourceStacks(String path, Selector filter) {
				return Map.of();
			}

			@Override
			public Stream<PackResources> listPacks() {
				return Stream.empty();
			}
		};
	}

	private static String entry(String value) {
		return "{\"" + KEY + "\":\"" + value + "\"}";
	}

	@Test
	void anEnglishPlayerWithoutGermanStillGetsDynamicVocabulary() {
		LangTables tables = LangTables.load(manager(Map.of("ko_kr.json", entry("사과"), "en_us.json", entry("Apple"))),
				Lang.EN);
		assertTrue(tables.has(KEY));
		assertEquals("사과", tables.ko(KEY));
		assertEquals("Apple", tables.en(KEY));
		assertNull(tables.de(KEY));
		assertEquals(1, tables.size());
	}

	@Test
	void theInterfaceLanguageFileIsReadWhenPresent() {
		LangTables tables = LangTables.load(manager(Map.of("ko_kr.json", entry("사과"), "en_us.json", entry("Apple"),
				"es_es.json", entry("Manzana"), "fr_fr.json", entry("Pomme"))), Lang.of("es_es"));
		assertEquals(Map.of("es_es", "Manzana"), tables.otherNames(KEY));
	}

	@Test
	void anInterfaceLanguageWithoutAFileAddsNothing() {
		LangTables tables = LangTables.load(manager(Map.of("ko_kr.json", entry("사과"), "en_us.json", entry("Apple"))),
				Lang.of("es_es"));
		assertTrue(tables.otherNames(KEY).isEmpty());
		assertFalse(tables.keys().isEmpty());
	}

	@Test
	void germanIsLoadedAsOneMoreLanguage() {
		LangTables tables = LangTables.load(manager(Map.of("ko_kr.json", entry("사과"), "en_us.json", entry("Apple"),
				"de_de.json", entry("Apfel"))), Lang.DE);
		assertEquals("Apfel", tables.de(KEY));
		assertTrue(tables.otherNames(KEY).isEmpty());
	}

	@Test
	void withoutAnyKoreanFileTheBundledNamesAreTheFallback() {
		LangTables tables = LangTables.load(manager(Map.of()), Lang.EN);
		assertTrue(tables.size() > 100, "bundled names.json should supply Korean names");
		assertFalse(tables.en(tables.keys().get(0)).isBlank());
	}
}
