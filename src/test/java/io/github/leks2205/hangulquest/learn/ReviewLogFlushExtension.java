package io.github.leks2205.hangulquest.learn;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Finishes the review log's background writes before a test ends, so a {@code @TempDir} is never
 * deleted while the writer thread is still adding a file to it. Registered through ServiceLoader.
 */
public final class ReviewLogFlushExtension implements AfterEachCallback {
	@Override
	public void afterEach(ExtensionContext context) {
		ReviewLog.flushAll();
	}
}
