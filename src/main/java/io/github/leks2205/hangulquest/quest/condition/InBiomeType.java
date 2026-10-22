package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** The biome the player stands in. */
final class InBiomeType implements ConditionType<Condition.InBiome> {
	@Override
	public String id() {
		return "IN_BIOME";
	}

	@Override
	public Class<Condition.InBiome> conditionClass() {
		return Condition.InBiome.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("biome"));
	}

	@Override
	public Condition.InBiome parse(JsonObject json) {
		return new Condition.InBiome(JsonFields.string(json, "biome", ""));
	}

	@Override
	public JsonObject toJson(Condition.InBiome condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("biome", condition.biome());
		return json;
	}

	@Override
	public boolean met(Condition.InBiome condition, Observation obs) {
		return obs.biomeIs(condition.biome());
	}

	@Override
	public List<io.github.leks2205.hangulquest.quest.goal.GoalType.Mention> mentions(Condition.InBiome condition) {
		return List.of(new io.github.leks2205.hangulquest.quest.goal.GoalType.Mention(io.github.leks2205.hangulquest.quest.goal.ObjectKind.BIOME, condition.biome()));
	}

	@Override
	public String prefix(Condition.InBiome condition, SentenceBuilder.Namer namer) {
		String name = SentenceBuilder.biomeName(condition.biome(), namer);
		return name == null ? "그 생물 군계에서 " : name + "에서 ";
	}

	@Override
	public List<String> words(Condition.InBiome condition, SentenceBuilder.Namer namer) {
		String name = SentenceBuilder.biomeName(condition.biome(), namer);
		return name == null ? List.of() : List.of(name + "에서", name + "에", name + "으로", name + "로");
	}

	@Override
	public String unmetText(Condition.InBiome condition, Observation obs, Catalog catalog) {
		return "지금 " + QuestStatus.biome(obs.biome, catalog) + " · "
				+ QuestStatus.biome(condition.biome(), catalog) + " 필요";
	}
}
