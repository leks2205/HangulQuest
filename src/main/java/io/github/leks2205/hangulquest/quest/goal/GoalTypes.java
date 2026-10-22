package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.JsonFields;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All goal types. A new goal type is one line in {@link #ALL}. */
public final class GoalTypes {
	/** In the order the schema lists them. */
	private static final List<GoalType<?>> ALL = List.of(
			new BreakBlockType(),
			new PlaceBlockType(),
			new AcquireItemType(),
			new ConsumeItemType(),
			new LookAtBlockType(),
			new LookAtEntityType(),
			new DefeatEntityType(),
			new ReachBiomeType(),
			new ReachDimensionType(),
			new ReachHeightType(),
			new UseBlockType(),
			new InteractEntityType(),
			// spatial goals (quest/spatial)
			new PlaceRelType(),
			new BuildPatternType(),
			new PlaceOnPatternType(),
			// Action variety.
			new CraftType(),
			new SmeltType(),
			new GiveType(),
			new PutInType(),
			new HoldWhileType(),
			new SequenceType(),
			new AvoidType(),
			// Wave 3: taking out, coming and going, resting, waiting, raising, riding, swimming.
			new TakeOutType(),
			new TransitType(),
			new HearType(),
			new LieDownType(),
			new WaitType(),
			new BreedType(),
			new MountType(),
			new SwimType(),
			new TradeType());

	private static final Map<String, GoalType<?>> BY_ID = new HashMap<>();
	private static final Map<Class<?>, GoalType<?>> BY_CLASS = new HashMap<>();

	static {
		for (GoalType<?> type : ALL) {
			if (BY_ID.put(type.id(), type) != null || BY_CLASS.put(type.goalClass(), type) != null) {
				throw new IllegalStateException("goal type registered twice: " + type.id());
			}
		}
	}

	private GoalTypes() {
	}

	public static List<GoalType<?>> all() {
		return ALL;
	}

	/** The type for a JSON {@code type} string (any case), or null. */
	public static GoalType<?> byId(String id) {
		return id == null ? null : BY_ID.get(id.toUpperCase(Locale.ROOT));
	}

	/** The type that handles this goal. */
	@SuppressWarnings("unchecked")
	public static <G extends Goal> GoalType<G> of(G goal) {
		GoalType<?> type = BY_CLASS.get(goal.getClass());
		if (type == null) {
			throw new IllegalStateException("no goal type registered for " + goal.getClass().getName());
		}
		return (GoalType<G>) type;
	}

	/** Reads a goal object; null for an unknown {@code type}. */
	public static Goal parse(JsonObject json) {
		GoalType<?> type = byId(JsonFields.string(json, "type", ""));
		return type == null ? null : type.parse(json);
	}

	public static JsonObject toJson(Goal goal) {
		return of(goal).toJson(goal);
	}
}
