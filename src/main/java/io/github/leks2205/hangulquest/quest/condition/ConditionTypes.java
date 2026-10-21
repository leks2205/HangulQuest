package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.JsonFields;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All condition types. A new condition type is one line in {@link #ALL}. */
public final class ConditionTypes {
	/** In the order the schema lists them. */
	private static final List<ConditionType<?>> ALL = List.of(
			new TimeType(),
			new WeatherType(),
			new YRangeType(),
			new InBiomeType(),
			new InDimensionType(),
			new NearEntityType(),
			new NearBlockType());

	private static final Map<String, ConditionType<?>> BY_ID = new HashMap<>();
	private static final Map<Class<?>, ConditionType<?>> BY_CLASS = new HashMap<>();

	static {
		for (ConditionType<?> type : ALL) {
			if (BY_ID.put(type.id(), type) != null || BY_CLASS.put(type.conditionClass(), type) != null) {
				throw new IllegalStateException("condition type registered twice: " + type.id());
			}
		}
	}

	private ConditionTypes() {
	}

	public static List<ConditionType<?>> all() {
		return ALL;
	}

	/** The type for a JSON {@code type} string (any case), or null. */
	public static ConditionType<?> byId(String id) {
		return id == null ? null : BY_ID.get(id.toUpperCase(Locale.ROOT));
	}

	@SuppressWarnings("unchecked")
	public static <C extends Condition> ConditionType<C> of(C condition) {
		ConditionType<?> type = BY_CLASS.get(condition.getClass());
		if (type == null) {
			throw new IllegalStateException("no condition type registered for " + condition.getClass().getName());
		}
		return (ConditionType<C>) type;
	}

	/** Reads a condition object; null for an unknown {@code type}. */
	public static Condition parse(JsonObject json) {
		ConditionType<?> type = byId(JsonFields.string(json, "type", ""));
		return type == null ? null : type.parse(json);
	}

	public static JsonObject toJson(Condition condition) {
		return of(condition).toJson(condition);
	}
}
