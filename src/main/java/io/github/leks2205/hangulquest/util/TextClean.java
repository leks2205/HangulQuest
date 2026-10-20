package io.github.leks2205.hangulquest.util;

import java.util.Locale;

/** Text cleaning shared by the Lute import, the validators and the UI. */
public final class TextClean {
	private TextClean() {
	}

	/** Strips HTML markup and entities, zero-width characters and collapses whitespace. */
	public static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		String text = raw
				.replace("\u200B", "")
				.replace("\u200C", "")
				.replace("\u200D", "")
				.replace("\uFEFF", "");
		text = text.replaceAll("(?is)<br\\s*/?>", "\n");
		text = text.replaceAll("(?is)<[^>]+>", "");
		text = text.replace("&nbsp;", " ")
				.replace("&amp;", "&")
				.replace("&quot;", "\"")
				.replace("&#39;", "'")
				.replace("&lt;", "<")
				.replace("&gt;", ">");
		text = text.replace('\u00A0', ' ');
		text = text.replaceAll("[ \\t]+", " ");
		text = text.replaceAll(" ?\\n ?", "\n");
		return text.trim();
	}

	/** First meaningful gloss segment, for compact cards. */
	public static String shortGloss(String cleaned) {
		if (cleaned.isEmpty()) {
			return "";
		}
		String first = cleaned;
		int newline = first.indexOf('\n');
		if (newline >= 0) {
			first = first.substring(0, newline);
		}
		int example = first.indexOf("예:");
		if (example >= 0) {
			first = first.substring(0, example);
		}
		int bracket = first.indexOf(" (");
		if (bracket > 3) {
			first = first.substring(0, bracket);
		}
		first = first.replaceAll("[\\s;]+$", "").trim();
		if (first.isEmpty()) {
			first = cleaned;
		}
		return first;
	}

	public static boolean hasHangul(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (isHangul(text.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	public static boolean isHangulOnly(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				continue;
			}
			if (isHangul(c) || isAllowedPunctuation(c)) {
				continue;
			}
			return false;
		}
		return true;
	}

	public static boolean isHangul(char c) {
		return (c >= 0xAC00 && c <= 0xD7A3)
				|| (c >= 0x1100 && c <= 0x11FF)
				|| (c >= 0x3130 && c <= 0x318F)
				|| (c >= 0xA960 && c <= 0xA97F)
				|| (c >= 0xD7B0 && c <= 0xD7FF);
	}

	private static boolean isAllowedPunctuation(char c) {
		return ".,!?·…'\"“”‘’-–—:;()[]{}/~%+=*&<>".indexOf(c) >= 0 || Character.isDigit(c);
	}

	public static String normalizeTerm(String term) {
		return clean(term).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	public static String truncate(String text, int max) {
		if (text.length() <= max) {
			return text;
		}
		return text.substring(0, Math.max(0, max - 1)) + "…";
	}
}
