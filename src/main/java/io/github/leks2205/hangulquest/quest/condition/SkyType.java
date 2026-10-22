package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;
import java.util.Locale;

/** Open sky above the player, or a roof within reach. */
final class SkyType implements ConditionType<Condition.Sky> {
	@Override
	public String id() {
		return "SKY";
	}

	@Override
	public Class<Condition.Sky> conditionClass() {
		return Condition.Sky.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("state", true, null, "OPEN", "ROOFED"));
	}

	@Override
	public Condition.Sky parse(JsonObject json) {
		try {
			return new Condition.Sky(Condition.SkyState.valueOf(
					JsonFields.string(json, "state", "OPEN").toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException unknown) {
			return new Condition.Sky(Condition.SkyState.OPEN);
		}
	}

	@Override
	public JsonObject toJson(Condition.Sky condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("state", condition.state().name());
		return json;
	}

	@Override
	public boolean met(Condition.Sky condition, Observation obs) {
		return condition.state() == Condition.SkyState.OPEN ? obs.canSeeSky : obs.roofed;
	}

	@Override
	public String prefix(Condition.Sky condition, SentenceBuilder.Namer namer) {
		return condition.state() == Condition.SkyState.OPEN ? "하늘이 보이는 곳에서 " : "지붕 아래에서 ";
	}

	/** Levels 1 to 4 say 밖에서; 하늘이 보이는 곳에서 holds an adnominal (보이는), a level 5 construction. */
	private static final int OUTSIDE_UP_TO = 4;

	@Override
	public String prefix(Condition.Sky condition, SentenceBuilder.Namer namer, int level) {
		if (condition.state() == Condition.SkyState.OPEN && level <= OUTSIDE_UP_TO) {
			return "밖에서 ";
		}
		return prefix(condition, namer);
	}

	/** A block placed under a roof is put there: 지붕 아래에 (the place of the thing), not 아래에서 (of the player). */
	@Override
	public String targetPrefix(Condition.Sky condition, SentenceBuilder.Namer namer, int level) {
		return condition.state() == Condition.SkyState.ROOFED ? "지붕 아래에 " : prefix(condition, namer, level);
	}

	@Override
	public List<String> targetGrammarIds(Condition.Sky condition, int level) {
		return condition.state() == Condition.SkyState.ROOFED ? List.of("L1.e_place") : grammarIds(condition, level);
	}

	@Override
	public List<String> grammarIds(Condition.Sky condition, int level) {
		return condition.state() == Condition.SkyState.OPEN && level <= OUTSIDE_UP_TO
				? List.of("L2.eseo_activity") : grammarIds(condition);
	}

	@Override
	public List<String> words(Condition.Sky condition, SentenceBuilder.Namer namer) {
		return condition.state() == Condition.SkyState.OPEN ? List.of("밖에서", "밖으로", "하늘 아래", "하늘이 보이")
				: List.of("지붕");
	}

	@Override
	public String unmetText(Condition.Sky condition, Observation obs, Catalog catalog) {
		return condition.state() == Condition.SkyState.OPEN ? "하늘이 보이는 곳 필요" : "지붕 아래 필요";
	}

	@Override
	public List<String> grammarIds(Condition.Sky condition) {
		return condition.state() == Condition.SkyState.OPEN
				? List.of("L2.eseo_activity", "L5.adn_present_neun") : List.of("L2.eseo_activity");
	}
}
