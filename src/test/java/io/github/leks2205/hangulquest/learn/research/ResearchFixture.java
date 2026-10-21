package io.github.leks2205.hangulquest.learn.research;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.Srs;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** A learning service over a small made-up catalog, with a known install salt. */
final class ResearchFixture {
	static final long T0 = 1_800_000_000_000L;
	static final String SALT = "test-salt";

	final Path dir;
	final Catalog catalog;
	final ProgressStore store;
	final LearningService learn;
	final List<String> ids = new ArrayList<>();

	ResearchFixture(Path dir, int words) {
		this.dir = dir;
		List<Concept> concepts = new ArrayList<>();
		for (int i = 0; i < words; i++) {
			Concept concept = new Concept();
			concept.id = String.format("w%03d", i);
			concept.ko = "돌";
			concept.de = "Stein " + i;
			concept.category = "BLOCKS";
			concept.tier = 1;
			concept.items = List.of("minecraft:stone");
			concept.descKo = "땅에서 나와요.";
			concepts.add(concept);
			ids.add("concept:" + concept.id);
		}
		catalog = Catalog.ofConcepts(concepts);
		store = new ProgressStore(dir, LoggerFactory.getLogger("research-test"));
		store.data().meta.installSalt = SALT;
		store.markDirty();
		learn = new LearningService(store.data(), store, catalog);
	}

	/** Same files, new service: what a restart does. */
	ResearchFixture restart() {
		learn.reviewLog().flush();
		store.flush();
		return new ResearchFixture(this);
	}

	private ResearchFixture(ResearchFixture before) {
		dir = before.dir;
		catalog = before.catalog;
		ids.addAll(before.ids);
		store = new ProgressStore(dir, LoggerFactory.getLogger("research-test"));
		learn = new LearningService(store.data(), store, catalog);
	}

	/** The first id for which the predicate holds. */
	String firstWhere(Predicate<String> test) {
		return ids.stream().filter(test).findFirst().orElseThrow();
	}

	String eligible() {
		return firstWhere(id -> Probes.eligible(SALT, id));
	}

	String notEligible() {
		return firstWhere(id -> !Probes.eligible(SALT, id));
	}

	/** Three right answers, the last after three days: the card ends near 14 days of stability. Returns that time. */
	long reachThreeDays(String id) {
		learn.answer(id, true, T0);
		learn.answer(id, true, T0 + 11 * Srs.MINUTE);
		long third = T0 + 3 * Srs.DAY;
		learn.answer(id, true, third);
		return third;
	}
}
