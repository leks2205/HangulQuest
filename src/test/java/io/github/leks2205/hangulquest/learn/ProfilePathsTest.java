package io.github.leks2205.hangulquest.learn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilePathsTest {
	private static final Logger LOG = LoggerFactory.getLogger("profile-test");
	private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final UUID BOB = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

	@TempDir
	Path config;

	private Path legacy;
	private byte[] legacyBytes;

	@BeforeEach
	void setUp() throws IOException {
		legacy = config.resolve("progress.json");
		legacyBytes = "{\"version\": 2, \"words\": {\"concept:stone\": {\"level\": 3, \"seen\": 9}}}".getBytes(StandardCharsets.UTF_8);
		Files.write(legacy, legacyBytes);
	}

	@Test
	void profilesLiveUnderTheAccountUuid() {
		Path dir = ProfilePaths.open(config, ALICE, LOG);
		assertEquals(config.resolve("profiles").resolve(ALICE.toString()), dir);
		assertTrue(Files.isDirectory(dir));
	}

	@Test
	void theOldFileIsCopiedNeverMoved() throws IOException {
		Path dir = ProfilePaths.open(config, ALICE, LOG);
		assertTrue(Files.exists(legacy), "the old file stays where it was");
		assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
		assertArrayEquals(legacyBytes, Files.readAllBytes(dir.resolve("progress.json")));
		assertTrue(Files.exists(config.resolve("profiles").resolve(ProfilePaths.MARKER)));
	}

	@Test
	void theCopyHappensOnlyOnce() throws IOException {
		Path dir = ProfilePaths.open(config, ALICE, LOG);
		byte[] edited = "{\"version\": 3, \"words\": {}}".getBytes(StandardCharsets.UTF_8);
		Files.write(dir.resolve("progress.json"), edited);
		ProfilePaths.open(config, ALICE, LOG);
		assertArrayEquals(edited, Files.readAllBytes(dir.resolve("progress.json")), "a second open must not copy again");

		Files.delete(dir.resolve("progress.json"));
		ProfilePaths.open(config, ALICE, LOG);
		assertFalse(Files.exists(dir.resolve("progress.json")), "the marker stops a later copy, too");
	}

	@Test
	void twoProfilesStaySeparate() throws IOException {
		Path alice = ProfilePaths.open(config, ALICE, LOG);
		Path bob = ProfilePaths.open(config, BOB, LOG);
		assertFalse(alice.equals(bob));
		assertTrue(Files.exists(alice.resolve("progress.json")), "the first profile got the old progress");
		assertFalse(Files.exists(bob.resolve("progress.json")), "the second one starts fresh");

		ProgressStore aliceStore = new ProgressStore(alice, LOG);
		ProgressStore bobStore = new ProgressStore(bob, LOG);
		aliceStore.data().totals.reviews = 7;
		bobStore.data().totals.reviews = 2;
		aliceStore.markDirty();
		bobStore.markDirty();
		aliceStore.flush();
		bobStore.flush();
		assertEquals(7, new ProgressStore(alice, LOG).data().totals.reviews);
		assertEquals(2, new ProgressStore(bob, LOG).data().totals.reviews);
		assertEquals(1, new ProgressStore(alice, LOG).data().words.size());
		assertEquals(0, new ProgressStore(bob, LOG).data().words.size());
	}

	@Test
	void nothingIsLostWhenTheProfileAlreadyHasProgress() throws IOException {
		Path dir = ProfilePaths.profileDir(config, ALICE.toString());
		Files.createDirectories(dir);
		byte[] own = "{\"version\": 3}".getBytes(StandardCharsets.UTF_8);
		Files.write(dir.resolve("progress.json"), own);
		ProfilePaths.open(config, ALICE, LOG);
		assertArrayEquals(own, Files.readAllBytes(dir.resolve("progress.json")), "an existing profile file is never overwritten");
		assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
	}

	@Test
	void theCopiedProgressLoadsAndMigrates() {
		ProgressStore store = new ProgressStore(ProfilePaths.open(config, ALICE, LOG), LOG);
		assertEquals(3, store.data().words.get("concept:stone").level);
		assertEquals(ProgressData.CURRENT_VERSION, store.data().version);
	}

	@Test
	void withoutAnOldFileAFreshProfileIsEmptyAndNoMarkerIsWritten() throws IOException {
		Files.delete(legacy);
		Path dir = ProfilePaths.open(config, ALICE, LOG);
		assertFalse(Files.exists(dir.resolve("progress.json")));
		assertFalse(Files.exists(config.resolve("profiles").resolve(ProfilePaths.MARKER)));
	}

	@Test
	void anUnavailableUuidFallsBackToTheDefaultProfile() {
		assertEquals("default", ProfilePaths.profileName((UUID) null));
		assertEquals("default", ProfilePaths.profileName(new UUID(0, 0)));
		assertEquals("default", ProfilePaths.profileName((String) null));
		assertEquals("default", ProfilePaths.profileName("../../etc"));
		assertEquals("default", ProfilePaths.profileName("not a uuid"));
		assertEquals(ALICE.toString(), ProfilePaths.profileName(ALICE.toString().toUpperCase()));
		assertEquals(config.resolve("profiles").resolve("default"), ProfilePaths.open(config, null, LOG));
	}
}
