package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.List;

/** A Schnitzeljagd clue chain with a ritual finale. */
public final class Questline {
	public record Requires(int minWordsKnown, String dimension, List<String> completed) {
		public Requires {
			completed = completed == null ? List.of() : List.copyOf(completed);
		}

		public static Requires none() {
			return new Requires(0, null, List.of());
		}
	}

	private final String id;
	private final String titleKo;
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
		this.steps = steps == null ? List.of() : List.copyOf(steps);
		this.finale = finale;
		this.finaleKo = finaleKo;
		this.finaleDe = finaleDe;
		this.finaleEn = finaleEn;
		this.badge = badge == null ? id : badge;
		this.order = order;
		this.koreanLevel = Math.max(1, Math.min(6, koreanLevel));
		this.topic = topic == null || topic.isBlank() ? titleDe : topic;
		this.adaptive = adaptive;
	}

	public String id() {
		return id;
	}

	public String titleKo() {
		return titleKo;
	}

	public String titleDe() {
		return titleDe;
	}

	public String titleEn() {
		return titleEn == null || titleEn.isBlank() ? titleDe : titleEn;
	}

	/** Title in the current interface language. */
	public String title() {
		return title(Lang.current());
	}

	public String title(Lang lang) {
		return lang == Lang.DE ? titleDe : titleEn();
	}

	/** The authored English title, null when the trail has none (no fallback to German). */
	public String titleEnRaw() {
		return titleEn;
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
		return finaleDe;
	}

	/** The authored English finale text, null when the trail has none (no fallback to German). */
	public String finaleEnRaw() {
		return finaleEn;
	}

	public String finaleEn() {
		return finaleEn == null || finaleEn.isBlank() ? finaleDe : finaleEn;
	}

	public String finale(Lang lang) {
		return lang == Lang.DE ? finaleDe : finaleEn();
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
