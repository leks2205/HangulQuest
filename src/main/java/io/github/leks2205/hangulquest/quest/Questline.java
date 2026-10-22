package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * A Schnitzeljagd clue chain with a ritual finale.
 *
 * <p>Title and finale text come from the lang files ({@code hangulquest.trail.<id>.title} and
 * {@code .finale}); the inline {@code titleEn}-style fields are what a player's questline file may
 * carry, and they win. Each step reads its own text from {@code hangulquest.trail.<id>.step.<sid>.*}.
 */
public final class Questline {
	public record Requires(int minWordsKnown, String dimension, List<String> completed) {
		public Requires {
			completed = completed == null ? List.of() : List.copyOf(completed);
		}

		public static Requires none() {
			return new Requires(0, null, List.of());
		}
	}

	private static final String DEFAULT_FINALE_EN = "You reached the goal.";
	private static final String DEFAULT_FINALE_DE = "Du hast das Ziel erreicht.";

	private final String id;
	private final String titleKo;
	/** Inline German title; null when the text comes from the lang files. */
	private final String titleDe;
	private final String titleEn;
	private final int tier;
	private final List<String> concepts;
	private final Requires requires;
	private final List<QuestStep> steps;
	private final QuestStep finale;
	private final String finaleKo;
	private final String finaleDe;
	private final String finaleEn;
	private final String badge;
	private final int order;
	private final int koreanLevel;
	private final String topic;
	private final boolean adaptive;

	public Questline(String id, String titleKo, String titleDe, int tier, List<String> concepts,
					 Requires requires, List<QuestStep> steps, QuestStep finale,
					 String finaleKo, String finaleDe, String badge, int order, int koreanLevel,
					 boolean adaptive) {
		this(id, titleKo, titleDe, titleDe, tier, concepts, requires, steps, finale, finaleKo,
				finaleDe, badge, order, koreanLevel, adaptive);
	}

	public Questline(String id, String titleKo, String titleDe, String topic, int tier,
					 List<String> concepts, Requires requires, List<QuestStep> steps,
					 QuestStep finale, String finaleKo, String finaleDe, String badge, int order,
					 int koreanLevel, boolean adaptive) {
		this(id, titleKo, titleDe, null, topic, tier, concepts, requires, steps, finale, finaleKo,
				finaleDe, null, badge, order, koreanLevel, adaptive);
	}

	/**
	 * The canonical constructor: every field, including the English title and finale text. All
	 * copies go through here (see the {@code with...} methods), so no field can be dropped.
	 */
	public Questline(String id, String titleKo, String titleDe, String titleEn, String topic, int tier,
					 List<String> concepts, Requires requires, List<QuestStep> steps,
					 QuestStep finale, String finaleKo, String finaleDe, String finaleEn,
					 String badge, int order, int koreanLevel, boolean adaptive) {
		this.id = id;
		this.titleKo = titleKo;
		this.titleDe = titleDe;
		this.titleEn = titleEn;
		this.tier = tier;
		this.concepts = concepts == null ? List.of() : List.copyOf(concepts);
		this.requires = requires == null ? Requires.none() : requires;
		List<QuestStep> keyed = new ArrayList<>();
		if (steps != null) {
			for (QuestStep step : steps) {
				keyed.add(withKey(id, step));
			}
		}
		this.steps = List.copyOf(keyed);
		this.finale = withKey(id, finale);
		List<QuestStep> ordered = allSteps();
		for (int i = 0; i < ordered.size(); i++) {
			ordered.get(i).bindEarlier(ordered.subList(0, i));
		}
		this.finaleKo = finaleKo;
		this.finaleDe = finaleDe;
		this.finaleEn = finaleEn;
		this.badge = badge == null ? id : badge;
		this.order = order;
		this.koreanLevel = Math.max(1, Math.min(6, koreanLevel));
		this.topic = topic != null && !topic.isBlank() ? topic
				: titleDe != null && !titleDe.isBlank() ? titleDe
				: titleEn != null && !titleEn.isBlank() ? titleEn : id;
		this.adaptive = adaptive;
	}

	public String id() {
		return id;
	}

	public String titleKo() {
		return titleKo;
	}

	/** The German title; the English one when German has none. */
	public String titleDe() {
		return title(Lang.DE);
	}

	/** The English title. */
	public String titleEn() {
		return title(Lang.EN);
	}

	/** Title in the current interface language. */
	public String title() {
		return title(Lang.current());
	}

	/** The title in a language; English where that language has none, the trail id when there is no text. */
	public String title(Lang lang) {
		String text = I18n.content(lang, key("title"), titleEn, titleDe);
		return text == null ? id : text;
	}

	/** The authored English title, null when the trail has none (no fallback to German). */
	public String titleEnRaw() {
		return I18n.own(Lang.EN, key("title"), titleEn, titleDe);
	}

	/** Copy carrying the English title and finale text. The finale step is left untouched. */
	public Questline withEnglish(String englishTitle, String englishFinale) {
		return new Questline(id, titleKo, titleDe, englishTitle, topic, tier, concepts, requires,
				steps, finale, finaleKo, finaleDe, englishFinale, badge, order, koreanLevel, adaptive);
	}

	/** Copy with other clue steps and finale step. */
	public Questline withSteps(List<QuestStep> newSteps, QuestStep newFinale) {
		return new Questline(id, titleKo, titleDe, titleEn, topic, tier, concepts, requires,
				newSteps, newFinale, finaleKo, finaleDe, finaleEn, badge, order, koreanLevel, adaptive);
	}

	/** Copy with other prerequisites. */
	public Questline withRequires(Requires newRequires) {
		return new Questline(id, titleKo, titleDe, titleEn, topic, tier, concepts, newRequires,
				steps, finale, finaleKo, finaleDe, finaleEn, badge, order, koreanLevel, adaptive);
	}

	/** Copy with another branch topic. */
	public Questline withTopic(String newTopic) {
		return new Questline(id, titleKo, titleDe, titleEn, newTopic, tier, concepts, requires,
				steps, finale, finaleKo, finaleDe, finaleEn, badge, order, koreanLevel, adaptive);
	}

	/** Copy with another tree tier and Korean difficulty. */
	public Questline withLevels(int newTier, int newKoreanLevel) {
		return new Questline(id, titleKo, titleDe, titleEn, topic, newTier, concepts, requires,
				steps, finale, finaleKo, finaleDe, finaleEn, badge, order, newKoreanLevel, adaptive);
	}

	/** Copy with the adaptive flag set or cleared. */
	public Questline withAdaptive(boolean newAdaptive) {
		return new Questline(id, titleKo, titleDe, titleEn, topic, tier, concepts, requires,
				steps, finale, finaleKo, finaleDe, finaleEn, badge, order, koreanLevel, newAdaptive);
	}

	public int tier() {
		return tier;
	}

	public List<String> concepts() {
		return concepts;
	}

	public Requires requires() {
		return requires;
	}

	public List<QuestStep> steps() {
		return steps;
	}

	public QuestStep finale() {
		return finale;
	}

	public String finaleKo() {
		return finaleKo;
	}

	public String finaleDe() {
		return finale(Lang.DE);
	}

	/** The authored English finale text, null when the trail has none (no fallback to German). */
	public String finaleEnRaw() {
		return I18n.own(Lang.EN, key("finale"), finaleEn, finaleDe);
	}

	public String finaleEn() {
		return finale(Lang.EN);
	}

	/**
	 * The finale line in a language; English where that language has none. A trail without any text
	 * gets a plain line, so the ceremony never shows nothing.
	 */
	public String finale(Lang lang) {
		String text = I18n.content(lang, key("finale"), finaleEn, finaleDe);
		if (text != null) {
			return text;
		}
		return lang == Lang.DE ? DEFAULT_FINALE_DE : DEFAULT_FINALE_EN;
	}

	private String key(String field) {
		return "hangulquest.trail." + id + "." + field;
	}

	/** The step with its key prefix set, unless it already carries one. */
	private static QuestStep withKey(String trailId, QuestStep step) {
		if (step == null || step.textKey() != null) {
			return step;
		}
		return step.withTextKey("hangulquest.trail." + trailId + ".step." + step.id());
	}

	public String badge() {
		return badge;
	}

	public int order() {
		return order;
	}

	/** Korean difficulty of the instructions, 1 (simple) to 5 (literary). */
	public int koreanLevel() {
		return koreanLevel;
	}

	public String topic() {
		return topic;
	}

	/** Branch topic key for icons and same-topic auto-continue. */
	public String topicKey() {
		return TopicResolver.key(topic);
	}

	public boolean adaptive() {
		return adaptive;
	}

	/** Clue steps followed by the finale, which is the actual last step. */
	public List<QuestStep> allSteps() {
		List<QuestStep> all = new ArrayList<>(steps);
		if (finale != null) {
			all.add(finale);
		}
		return all;
	}

	public QuestStep step(String stepId) {
		for (QuestStep step : allSteps()) {
			if (step.id().equals(stepId)) {
				return step;
			}
		}
		return null;
	}
}
