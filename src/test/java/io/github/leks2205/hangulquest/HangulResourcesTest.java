package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.HangulResources;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangulResourcesTest {
	@Test
	void shippedListParsesAndEveryEntryIsUsable() {
		List<HangulResources.Entry> entries = HangulResources.load();
		assertTrue(entries.size() >= 4 && entries.size() <= 6, "expected 4-6 entries, got " + entries.size());
		Set<String> urls = new HashSet<>();
		for (HangulResources.Entry entry : entries) {
			assertFalse(entry.title().isBlank(), "title");
			assertTrue(entry.url().startsWith("https://"), "https only: " + entry.url());
			assertNotNull(URI.create(entry.url()).getHost(), "host: " + entry.url());
			assertTrue(entry.free(), "only free resources: " + entry.title());
			assertTrue(entry.lang().equals("en") || entry.lang().equals("de"), "lang of " + entry.title());
			assertFalse(entry.kind().isBlank(), "kind of " + entry.title());
			assertTrue(urls.add(entry.url()), "duplicate url " + entry.url());
		}
		assertTrue(entries.stream().anyMatch(entry -> entry.lang().equals("de")), "a German resource");
		assertTrue(entries.stream().anyMatch(entry -> entry.lang().equals("en")), "an English resource");
	}

	@Test
	void rowsWithoutHttpsOrTitleAreDropped() {
		String json = """
				{"resources":[
				 {"title":"ok","url":"https://example.org/a","lang":"EN","free":true,"kind":"lessons"},
				 {"title":"plain http","url":"http://example.org/b","lang":"en","free":true,"kind":"lessons"},
				 {"title":"","url":"https://example.org/c","lang":"en","free":true,"kind":"lessons"},
				 {"title":"script","url":"javascript:alert(1)","lang":"en","free":true,"kind":"lessons"},
				 {"title":"no url","lang":"en","free":true,"kind":"lessons"}
				]}""";
		List<HangulResources.Entry> entries = HangulResources.parse(new StringReader(json));
		assertEquals(1, entries.size());
		assertEquals("ok", entries.get(0).title());
		assertEquals("en", entries.get(0).lang());
	}

	@Test
	void ownLanguageComesFirst() {
		List<HangulResources.Entry> entries = HangulResources.load();
		assertEquals("de", HangulResources.orderedFor(entries, Lang.DE).get(0).lang());
		assertEquals("en", HangulResources.orderedFor(entries, Lang.EN).get(0).lang());
		assertEquals(entries.size(), HangulResources.orderedFor(entries, Lang.DE).size());
	}
}
