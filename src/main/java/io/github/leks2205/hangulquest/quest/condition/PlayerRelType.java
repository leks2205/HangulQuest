package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.quest.goal.Matching;
import io.github.leks2205.hangulquest.quest.goal.ObjectKind;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialSentences;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.Observation;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Where the player stands next to a block or an entity, spoken as the place of the activity:
 * "나무 아래에서 빵을 먹어요". A block anchor is the block the player looks at; an entity anchor
 * is any loaded entity of that type. 앞 and 뒤 need an entity, because only entities face a way.
 */
final class PlayerRelType implements ConditionType<Condition.PlayerRel> {
	@Override
	public String id() {
		return "PLAYER_REL";
	}

	@Override
	public Class<Condition.PlayerRel> conditionClass() {
		return Condition.PlayerRel.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("relation", true, null,
						Arrays.stream(Relation.values()).map(Enum::name).toArray(String[]::new)),
				Field.anchor("anchor", true, "A block, an entity, or a block tag such as #minecraft:logs."),
				Field.choice("kind", false, "What the anchor is, when its id could be an item and an entity (chicken).",
						"block", "item", "entity"));
	}

	@Override
	public Condition.PlayerRel parse(JsonObject json) {
		Relation relation;
		try {
			relation = Relation.valueOf(JsonFields.string(json, "relation", "NEXT_TO").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			relation = Relation.NEXT_TO;
		}
		String kind = JsonFields.string(json, "kind", null);
		return new Condition.PlayerRel(relation, JsonFields.string(json, "anchor", ""),
				kind == null || kind.isBlank() ? null : kind.toLowerCase(Locale.ROOT));
	}

	@Override
	public JsonObject toJson(Condition.PlayerRel condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("relation", condition.relation().name());
		json.addProperty("anchor", condition.anchor());
		if (condition.kind() != null) {
			json.addProperty("kind", condition.kind());
		}
		return json;
	}

	@Override
	public boolean met(Condition.PlayerRel condition, Observation obs) {
		return met(condition, obs, Accepted.NONE);
	}

	@Override
	public boolean met(Condition.PlayerRel condition, Observation obs, Accepted accepted) {
		EntityFact entity = nearestEntity(condition.anchor(), obs);
		if (entity != null && holds(condition.relation(), obs.position, entity.position().add(0, 0.9, 0), entity, 0.9)) {
			return true;
		}
		if (obs.lookedBlockPos != null && (accepted.isExact(condition.anchor())
				? condition.anchor().equals(obs.lookedBlock) : Matching.matchesBlock(condition.anchor(), obs.lookedBlock))) {
			Vec3 center = new Vec3(obs.lookedBlockPos.getX() + 0.5, obs.lookedBlockPos.getY() + 0.5,
					obs.lookedBlockPos.getZ() + 0.5);
			return holds(condition.relation(), obs.position, center, null, 0.5);
		}
		return false;
	}

	/**
	 * The geometry. {@code anchor} is the middle of a block or of an entity; {@code half} is half its
	 * height, so it is measured from its top and bottom. The player's feet are at {@code player}.
	 */
	private static boolean holds(Relation relation, Vec3 player, Vec3 anchor, EntityFact facing, double half) {
		double flat = Math.hypot(player.x - anchor.x, player.z - anchor.z);
		double above = player.y - (anchor.y + half);          // feet over the top of the anchor
		double below = (anchor.y - half) - (player.y + 1.8);  // head under the bottom of the anchor
		double apart = Math.sqrt(flat * flat + (player.y - anchor.y) * (player.y - anchor.y));
		return switch (relation) {
			case ON_TOP_OF -> flat <= 1.3 && above >= -0.3 && above <= 3.0;
			case UNDER -> flat <= 1.5 && below >= -0.2 && below <= 6.0;
			case NEXT_TO -> flat > 0.4 && flat <= 2.5 && Math.abs(player.y - anchor.y) <= 1.5;
			case NEAR -> apart <= 6.0;
			case INSIDE -> flat <= 1.0 && Math.abs(player.y - anchor.y) <= 1.0;
			case OUTSIDE -> apart > 1.5 && apart <= 8.0;
			case IN_FRONT_OF -> facing != null && flat > 0.4 && flat <= 4.0 && alignment(player, anchor, facing) >= 0.5;
			case BEHIND -> facing != null && flat > 0.4 && flat <= 4.0 && alignment(player, anchor, facing) <= -0.5;
			case BETWEEN -> false; // needs two anchors
		};
	}

	/** Cosine between where the entity looks and the direction to the player, on the ground plane. */
	private static double alignment(Vec3 player, Vec3 anchor, EntityFact entity) {
		Vec3 look = entity.facing();
		double lookLength = Math.hypot(look.x, look.z);
		double dx = player.x - anchor.x;
		double dz = player.z - anchor.z;
		double toLength = Math.hypot(dx, dz);
		if (lookLength < 1.0E-6 || toLength < 1.0E-6) {
			return 0.0;
		}
		return (look.x * dx + look.z * dz) / (lookLength * toLength);
	}

	private static EntityFact nearestEntity(String type, Observation obs) {
		EntityFact best = null;
		double bestDistance = Double.MAX_VALUE;
		for (EntityFact fact : obs.nearbyEntityPositions) {
			if (fact.type().equals(type)) {
				double distance = fact.position().distanceToSqr(obs.position);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = fact;
				}
			}
		}
		return best;
	}

	@Override
	public String prefix(Condition.PlayerRel condition, SentenceBuilder.Namer namer) {
		return KoGrammar.eseo(anchorName(condition, namer) + " " + word(condition.relation())) + " ";
	}

	@Override
	public String unmetText(Condition.PlayerRel condition, Observation obs, Catalog catalog) {
		String name = anchorName(condition, new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return null;
			}

			@Override
			public String entity(String id) {
				return catalog.byEntityId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String block(String id) {
				return catalog.byItemId(id).map(word -> word.ko).orElse(null);
			}
		});
		return name + " " + word(condition.relation()) + " 필요";
	}

	@Override
	public List<GoalType.Mention> mentions(Condition.PlayerRel condition) {
		boolean entity = "entity".equals(condition.kind());
		return List.of(new GoalType.Mention(entity ? ObjectKind.ENTITY : ObjectKind.BLOCK, condition.anchor()),
				new GoalType.Mention(ObjectKind.ENTITY, condition.anchor()));
	}

	@Override
	public List<String> grammarIds(Condition.PlayerRel condition) {
		return List.of("L2.eseo_activity");
	}

	@Override
	public void validate(Condition.PlayerRel condition, ValidationContext context) {
		if (BlockSpec.isTag(condition.anchor()) ? !BlockSpec.valid(condition.anchor())
				: ValidationContext.badId(condition.anchor())) {
			context.problem("bad anchor " + condition.anchor());
		}
		io.github.leks2205.hangulquest.quest.spatial.SpatialRules.checkTagWord(condition.anchor(), context);
		switch (condition.relation()) {
			case BETWEEN -> context.problem("PLAYER_REL cannot use BETWEEN with a single anchor");
			case IN_FRONT_OF, BEHIND -> {
				if (context.catalog().byEntityId(condition.anchor()).isEmpty()) {
					context.problem(condition.relation() + " needs an entity anchor, " + condition.anchor()
							+ " is not one");
				}
			}
			default -> {
			}
		}
	}

	/** 위, 아래, 옆, 앞, 뒤, 안, 밖, 사이, 근처. */
	static String word(Relation relation) {
		return switch (relation) {
			case ON_TOP_OF -> "위";
			case UNDER -> "아래";
			case NEXT_TO -> "옆";
			case IN_FRONT_OF -> "앞";
			case BEHIND -> "뒤";
			case INSIDE -> "안";
			case OUTSIDE -> "밖";
			case BETWEEN -> "사이";
			case NEAR -> "근처";
		};
	}

	/**
	 * The anchor's name: by its declared kind; without one an entity name wins over an item name, because
	 * ids like chicken, cod and salmon are animals and food at once and the player stands next to the animal.
	 */
	static String anchorName(Condition.PlayerRel condition, SentenceBuilder.Namer namer) {
		String id = condition.anchor();
		String name;
		if (BlockSpec.isTag(id)) {
			// a tag is spoken with the word the step teaches for it, else its everyday word (통나무)
			String tag = SpatialSentences.anchorNoun(id, namer);
			return tag == null || tag.isBlank() ? "그것" : tag;
		}
		if ("entity".equals(condition.kind())) {
			name = namer.entity(id);
		} else if (condition.kind() != null) {
			name = "item".equals(condition.kind()) ? namer.item(id) : namer.block(id);
		} else {
			name = namer.entity(id);
			if (name == null || name.isBlank()) {
				name = namer.block(id);
			}
		}
		return name == null || name.isBlank() ? "그것" : name;
	}
}
