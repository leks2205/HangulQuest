package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.culture.CultureHook;

/** Where the Library content of the last content load is kept, and the one place the client starts the Library from. */
public final class LibraryHook {
	private static volatile LibraryContent content = LibraryContent.EMPTY;

	private LibraryHook() {
	}

	public static void register() {
		CultureHook.register();
	}

	/** Called with every content load. */
	public static void publish(LibraryContent loaded) {
		content = loaded == null ? LibraryContent.EMPTY : loaded;
		CultureHook.publish(content.culture());
	}

	public static LibraryContent content() {
		return content;
	}
}
