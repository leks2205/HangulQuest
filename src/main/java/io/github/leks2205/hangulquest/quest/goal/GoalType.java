package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.quest.spatial.SpatialSentences;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;
import java.util.stream.Stream;

/**
 * Everything the game knows about one kind of goal: how it is read from JSON, checked against the
 * world, spoken in Korean, shown in the status line and validated. The sealed {@link Goal} records
 * only carry the data; the callers dispatch through {@link GoalTypes}.
 *
 * <p>Adding a goal type takes three small edits:
 * <ol>
 * <li>a record in {@link Goal} (this keeps the compiler's exhaustiveness checks);</li>
 * <li>one class implementing this interface, holding all the logic;</li>
 * <li>one line in {@link GoalTypes}.</li>
 * </ol>
 * The JSON schema in docs/questline.schema.json is generated from {@link #fields()}.
 */
public interface GoalType<G extends Goal> {
	/** The JSON {@code type} string, e.g. BREAK_BLOCK. */
	String id();

	Class<G> goalClass();

	/** The JSON properties besides {@code type}, in the order the schema lists them. */
	List<Field> fields();

	/** Reads the goal; a missing property takes its default. */
	G parse(JsonObject json);

	/** The JSON form that {@link #parse} reads back. */
	JsonObject toJson(G goal);

	/** The part of the progress key that tells two goals of this type apart. */
	String keyPart(G goal);

	// ------------------------------------------------------------- evaluation

	/**
	 * Advances the tracker with this tick's observation; true when the goal is met. Conditions have
	 * been checked already. {@code nightLatch} is set when a "night" condition is excused because the
	 * event happened at night (only for types that are {@link #nightLatchable()}).
	 */
	boolean evaluate(G goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch);

	/** Runs before every tick, even while the conditions fail. */
	default void beforeTick(G goal, Observation obs, GoalEvaluator.Tracker tracker) {
	}

	/** Whether an event at night still counts after the night is over (a bed skipped the time). */
	default boolean nightLatchable() {
		return false;
	}

	/** Whether being asleep excuses a time-of-day condition for this goal. */
	default boolean excusesTimeCondition(G goal, Observation obs) {
		return false;
	}

	/** Whether the sensor must pick entities along the camera ray far beyond vanilla's reach. */
	default boolean needsFarEntityLook() {
		return false;
	}

	/**
	 * Whether the sensor must look through the crosshair for water or lava, which the vanilla pick passes
	 * through (so looking at a pond picks the sand under it).
	 */
	default boolean needsFluidLook(G goal) {
		return false;
	}

	/** Whether the sensor must scan the surroundings for a playing jukebox while this goal is tracked. */
	default boolean needsMusicScan(G goal) {
		return false;
	}

	/** True when the status line and the HUD show a counter for this goal. */
	default boolean countable() {
		return false;
	}

	/** How much the counter must reach. */
	default int needed(G goal) {
		return 1;
	}

	/** How long the player has to look, in milliseconds; 0 for goals without a dwell time. */
	default long dwellNeededMs(G goal) {
		return 0L;
	}

	// ---------------------------------------------------------------- Korean

	/** The verb of the sentence; an explicit verb on the goal wins over the table. */
	Verb verb(G goal, VerbTable table);

	/** The verb under the conditions of the step: swimming in a sea says 수영해요 where the plain goal says 가요. */
	default Verb verb(G goal, VerbTable table, List<io.github.leks2205.hangulquest.quest.Condition> conditions) {
		return verb(goal, table);
	}

	/**
	 * Other verbs the sentence may use when the step teaches that verb: 요리하다 for a food that is baked
	 * (구워요), 타다 for a boat that is sat in. The default verb stays the default for every other step.
	 */
	default List<Verb> alternativeVerbs(G goal) {
		return List.of();
	}

	/** Whether the sentence of the goal already says this condition, so the condition's own phrase is left out. */
	default boolean says(G goal, io.github.leks2205.hangulquest.quest.Condition condition) {
		return false;
	}

	/** The instruction sentence, without the condition prefix. */
	String korean(G goal, SentenceContext context);

	/** The past-tense finale body, without the condition prefix and the full stop. */
	String ceremony(G goal, String obj, Verb verb);

	/** The past-tense finale with the whole sentence context; types with more than one noun override this. */
	default String ceremony(G goal, SentenceContext context) {
		return ceremony(goal, context.obj(), context.verb());
	}

	/**
	 * The grammar points the clue of this goal teaches, as ids such as {@code L2.ege}. The default is the
	 * ending of the level and the marker of the object; types add what is special about them.
	 */
	default List<String> grammarIds(G goal, SentenceContext context) {
		return SentenceBuilder.baseGrammar(context.verb(), context.level(), objectKind());
	}

	/**
	 * The constructions whose form decides this goal: the relation word of a placement, the recipient marker
	 * of a hand-over, the connective of an ordered sequence, the "don't" of an avoid. Completing the step
	 * rightly is evidence for them, a slip in the form is evidence against. Ids as {@link #grammarIds}
	 * emits them (the ladder resolves aliases); empty when no form decides.
	 */
	default List<String> decidingForms(G goal) {
		return List.of();
	}

	/** The Korean name of the object, or null when there is none. */
	default String objectName(G goal, SentenceBuilder.Namer namer) {
		String object = object(goal);
		return switch (objectKind()) {
			case BLOCK -> SpatialSentences.blockName(object, namer);
			case ITEM -> BlockSpec.isTag(object) ? SpatialSentences.blockName(object, namer) : namer.item(object);
			case ENTITY -> namer.entity(object);
			case BIOME -> SentenceBuilder.biomeName(object, namer);
			case DIMENSION -> SentenceBuilder.dimension(object);
			case NONE -> null;
		};
	}

	// ----------------------------------------------------------------- status

	/** A fixed status that beats everything else (sleeping in a bed), or null. */
	default String statusOverride(G goal, Observation obs) {
		return null;
	}

	/** The status line: progress toward the goal. {@code word} is the Korean noun of the first taught word. */
	String status(G goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog, String word);

	// ------------------------------------------------------------- validation

	/** The id of the thing the goal points at, null for goals without one. */
	String object(G goal);

	ObjectKind objectKind();

	/**
	 * Type, object and verb as one string: two steps with the same signature ask for the same task.
	 * Types with more than one target or sub-goal (give, put in, sequences) put all of them in.
	 */
	default String taskSignature(G goal) {
		String object = object(goal);
		return id() + ":" + (object == null ? "" : object) + ":" + verb(goal, VerbTable.shared()).name();
	}

	/** A thing a goal involves, in any role: the object, an anchor, a recipient, a container, a held item. */
	record Mention(ObjectKind kind, String id) {
	}

	/** Everything the goal points at; the default is its object. Types with more roles add them. */
	default List<Mention> mentions(G goal) {
		String object = object(goal);
		return object == null ? List.of() : List.of(new Mention(objectKind(), object));
	}

	/** Whether the taught word names something the goal involves, in any role. */
	default boolean covers(G goal, Concept concept) {
		ObjectKind kind = objectKind();
		if (kind == ObjectKind.NONE || kind == ObjectKind.DIMENSION || kind == ObjectKind.BIOME && object(goal) == null) {
			return true; // a height or a dimension has no object the word could miss
		}
		return mentions(goal).stream().anyMatch(mention -> covers(mention, concept));
	}

	/** Whether the concept stands for the mentioned thing: a referent, a family member, its picture or its entity. */
	static boolean covers(Mention mention, Concept concept) {
		String id = mention.id();
		return switch (mention.kind()) {
			case BLOCK, ITEM -> {
				String plain = io.github.leks2205.hangulquest.quest.spatial.BlockSpec.representative(id);
				// the word names its items and covers the others (any sapling for 묘목)
				boolean referent = Stream.of(concept.items, concept.covers, concept.accepts)
						.flatMap(list -> list == null ? Stream.<String>empty() : list.stream())
						.anyMatch(item -> item.equals(id) || plain != null && Matching.matchesBlock(plain, item));
				yield referent || id.equals(concept.icon);
			}
			case ENTITY -> id.equals(concept.entity);
			case BIOME -> id == null || id.startsWith("#") || id.equals(concept.biome)
					|| concept.biome != null && concept.covers != null && concept.covers.contains(id);
			default -> true;
		};
	}

	/** Whether the object may be a block tag such as {@code #minecraft:flowers} (see BlockSpec). */
	default boolean tagObject() {
		return false;
	}

	/** Adds the problems of this goal: a taught word that does not fit, and bad ids. */
	default void validate(G goal, ValidationContext context) {
		if (!context.step().teach().isEmpty()) {
			var first = context.catalog().concept(context.step().teach().get(0));
			if (first.isPresent() && !covers(goal, first.get())
					&& !conditionsMention(context.step().conditions(), first.get())
					&& !speaksVerb(goal, first.get())) {
				context.problem("the sentence word '" + first.get().ko + "' (" + first.get().id
						+ ") does not describe the goal " + describe(goal));
			}
		}
		String noun = objectKind().noun();
		boolean tag = tagObject() && BlockSpec.isTag(object(goal));
		if (tag && !BlockSpec.valid(object(goal))) {
			context.problem("unknown tag " + object(goal));
		} else if (noun != null && !tag && ValidationContext.badId(object(goal))) {
			context.problem("bad " + noun + " " + object(goal));
		}
	}

	/** True for a verb or adjective concept (보다, 올라가다): a word that names an action, not a thing. */
	static boolean isVerbConcept(Concept concept) {
		return "ACTIONS".equals(concept.category) && concept.ko != null && concept.ko.endsWith("다")
				&& !concept.ko.contains(" ");
	}

	/**
	 * Whether the taught word is a verb the sentence of this goal can say: the verb of the goal, one of the
	 * verbs the table uses for its type or one of its {@link #alternativeVerbs}. Such a word may be the
	 * first taught word, because the clue then contains a conjugated form of it (올라가요 for 올라가다).
	 */
	default boolean speaksVerb(G goal, Concept concept) {
		if (!isVerbConcept(concept)) {
			return false;
		}
		java.util.List<Verb> speakable = new java.util.ArrayList<>(List.of(verb(goal, VerbTable.shared())));
		speakable.addAll(alternativeVerbs(goal));
		speakable.addAll(VerbTable.shared().verbsFor(id()));
		return speakable.stream().anyMatch(verb -> verb.dictionary().equals(concept.ko));
	}

	private static boolean conditionsMention(List<io.github.leks2205.hangulquest.quest.Condition> conditions,
											 Concept concept) {
		for (var condition : conditions) {
			for (Mention mention : io.github.leks2205.hangulquest.quest.condition.ConditionTypes.of(condition)
					.mentions(condition)) {
				if (covers(mention, concept)) {
					return true;
				}
			}
		}
		return false;
	}

	private String describe(G goal) {
		ObjectKind kind = objectKind();
		boolean named = kind == ObjectKind.BLOCK || kind == ObjectKind.ITEM || kind == ObjectKind.ENTITY;
		return named && object(goal) != null ? object(goal) : goal.getClass().getSimpleName();
	}
}
