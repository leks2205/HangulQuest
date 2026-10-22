package io.github.leks2205.hangulquest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What the content metrics need to know about every step, taken from the mod's own types
 * ({@link GoalType#taskSignature}, {@link SentenceBuilder}) and written to
 * {@code build/reports/content/steps.json}. tools/variety.py and tools/coverage.py read that file, so
 * the Python tools and the Java tests measure the same signatures.
 *
 * <p>Three notions the metrics share:
 * <ul>
 * <li>the <b>signature</b> is the goal type's task signature plus the conditions: two steps with the
 * same one ask for the same task;</li>
 * <li>the <b>shape</b> is the signature with every object word taken out (item, block, entity, anchor,
 * material), so two steps that differ only in the object have the same shape;</li>
 * <li>the <b>mechanics</b> are what a step makes the player do: goal types, relations, patterns,
 * conditions, holding, sequences. They do not include verbs or object words.</li>
 * </ul>
 */
final class ContentFacts {
	static final Path DUMP = Path.of("build", "reports", "content", "steps.json");
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

	private ContentFacts() {
	}

	record Target(String role, String id) {
	}

	record Step(String id, String goalType, String signature, String shape, String verb, String object,
				String relation, List<String> conditions, List<String> mechanics, boolean bare,
				boolean formDependent, List<String> grammar, List<String> teach, List<Target> targets, String clueKo, String hintKo) {
	}

	/** {@code grammar} is the trail-level list its file declares ("grammar": [...]); empty when it has none. */
	record Trail(String id, int level, int order, boolean demo, List<String> requires, String titleKo,
				 String finaleKo, List<Step> steps, List<String> grammar) {
		Trail(String id, int level, int order, boolean demo, List<String> requires, String titleKo,
			  String finaleKo, List<Step> steps) {
			this(id, level, order, demo, requires, titleKo, finaleKo, steps, List.of());
		}
	}

	// ------------------------------------------------------------------ building

	/** Every shipped trail, with the Korean lines the engine shows (one engine run, memoized). */
	private static List<Trail> shipped;

	static synchronized List<Trail> shipped(Path scratch) {
		if (shipped == null) {
			Map<String, String> texts = SentenceDumpTest.collect(scratch).stream()
					.collect(Collectors.toMap(SentenceDumpTest.Row::id, SentenceDumpTest.Row::ko, (a, b) -> a));
			shipped = of(QuestLoader.loadAll(), texts);
		}
		return shipped;
	}

	static List<Trail> of(List<Questline> trails, Map<String, String> texts) {
		Catalog catalog = KoreanFixtures.catalog();
		SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);
		List<Trail> out = new ArrayList<>();
		for (Questline trail : trails) {
			List<Step> steps = new ArrayList<>();
			for (QuestStep step : trail.allSteps()) {
				boolean finale = "finale".equals(step.id());
				String base = "trail." + trail.id() + "." + step.id();
				steps.add(step(step, trail.koreanLevel(), finale, catalog, namer,
						texts.getOrDefault(base + ".clue", ""), texts.getOrDefault(base + ".hint", "")));
			}
			out.add(new Trail(trail.id(), trail.koreanLevel(), trail.order(), isDemo(trail.id()),
					List.copyOf(trail.requires().completed()), trail.titleKo() == null ? "" : trail.titleKo(),
					trail.finaleKo() == null ? "" : trail.finaleKo(), List.copyOf(steps), trail.grammar()));
		}
		return out;
	}

	/** A demo shows off a task type; it is measured but exempt from the ratchets. */
	static boolean isDemo(String trailId) {
		return trailId.endsWith("_demo");
	}

	static Step step(QuestStep step, int level, boolean finale, Catalog catalog, SentenceBuilder.Namer namer,
					 String clueKo, String hintKo) {
		Goal goal = step.goal();
		GoalType<Goal> type = GoalTypes.of(goal);
		List<String> conditions = new ArrayList<>();
		for (Condition condition : step.conditions()) {
			conditions.add(conditionKey(condition, true));
		}
		conditions.sort(null);
		List<String> shapeConditions = new ArrayList<>();
		for (Condition condition : step.conditions()) {
			shapeConditions.add(conditionKey(condition, false));
		}
		shapeConditions.sort(null);

		String signature = type.taskSignature(goal) + conditionSuffix(conditions);
		String shape = shape(goal) + conditionSuffix(shapeConditions);
		Set<String> mechanics = new TreeSet<>();
		mechanics(goal, mechanics);
		for (Condition condition : step.conditions()) {
			mechanics.add("cond:" + ConditionTypes.of(condition).id());
		}
		Word word = step.teach().isEmpty() ? null : catalog.byConceptId(step.teach().get(0)).orElse(null);
		List<String> grammar = SentenceBuilder.grammar(goal, word, step.conditions(), level, finale, namer);
		List<Target> targets = new ArrayList<>();
		targets(goal, targets);
		for (Condition condition : step.conditions()) {
			conditionTargets(condition, targets);
		}
		boolean bare = step.conditions().isEmpty() && (goal instanceof Goal.PlaceBlock
				|| goal instanceof Goal.BreakBlock || goal instanceof Goal.AcquireItem);
		String relation = relation(goal);
		// the Korean form decides which action is right: a relation word, a recipient, a connective, a "don't"
		boolean formDependent = !type.decidingForms(goal).isEmpty();
		return new Step(step.id(), type.id(), signature, shape, SentenceBuilder.verbFor(goal).name(),
				type.object(goal), relation == null ? "" : relation, List.copyOf(conditions),
				List.copyOf(mechanics), bare, formDependent, List.copyOf(grammar), List.copyOf(step.teach()),
				List.copyOf(targets), clueKo, hintKo);
	}

	private static String conditionSuffix(List<String> keys) {
		return keys.isEmpty() ? "" : "|" + String.join(",", keys);
	}

	/** The condition as one token. {@code withObjects} false drops ids of blocks, entities, biomes and items. */
	static String conditionKey(Condition condition, boolean withObjects) {
		return switch (condition) {
			case Condition.Time c -> "TIME:" + c.phase();
			case Condition.WeatherState c -> "WEATHER:" + c.weather();
			case Condition.YRange c -> "Y_RANGE" + (withObjects ? ":" + c.minY() + ".." + c.maxY() : "");
			case Condition.NearEntity c -> "NEAR_ENTITY" + (withObjects ? ":" + c.entity() : "");
			case Condition.NearBlock c -> "NEAR_BLOCK" + (withObjects ? ":" + c.block() : "");
			case Condition.InDimension c -> "IN_DIMENSION" + (withObjects ? ":" + c.dimension() : "");
			case Condition.InBiome c -> "IN_BIOME" + (withObjects ? ":" + c.biome() : "");
			case Condition.Holding c -> "HOLDING" + (withObjects ? ":" + c.item() : "");
			case Condition.Sky c -> "SKY:" + c.state();
			case Condition.InWater c -> "IN_WATER";
			case Condition.PlayerRel c -> "PLAYER_REL:" + c.relation() + (withObjects ? ":" + c.anchor() : "");
		};
	}

	/** The signature without any object word: type, relation, pattern, verb, and the parts of a combined goal. */
	static String shape(Goal goal) {
		GoalType<Goal> type = GoalTypes.of(goal);
		String verb = SentenceBuilder.verbFor(goal).name();
		return switch (goal) {
			case Goal.PlaceRel g -> type.id() + ":" + g.relation() + ":" + verb;
			case Goal.BuildPattern g -> type.id() + ":" + g.pattern() + "/" + g.size() + ":" + verb;
			case Goal.PlaceOnPattern g -> type.id() + ":" + g.relation() + ":" + g.pattern() + ":" + verb;
			case Goal.HoldWhile g -> type.id() + ":HOLD+" + (g.innerGoal() != null ? shape(g.innerGoal())
					: g.innerCondition() == null ? "" : conditionKey(g.innerCondition(), false));
			case Goal.Sequence g -> {
				List<String> parts = new ArrayList<>();
				g.parts().forEach(part -> parts.add(shape(part)));
				if (!g.ordered()) {
					parts.sort(null);
				}
				yield type.id() + ":" + (g.ordered() ? ">" : "&") + (g.connector() == null ? "" : g.connector())
						+ ":" + String.join(g.ordered() ? ">" : "&", parts);
			}
			case Goal.Avoid g -> type.id() + ":" + (g.action() == null ? "" : shape(g.action()));
			default -> type.id() + ":" + verb;
		};
	}

	/** Goal types, relations, patterns, holding, sequences and avoid, recursively. */
	static void mechanics(Goal goal, Set<String> into) {
		into.add("goal:" + GoalTypes.of(goal).id());
		switch (goal) {
			case Goal.PlaceRel g -> into.add("rel:" + g.relation());
			case Goal.BuildPattern g -> into.add("pattern:" + g.pattern());
			case Goal.PlaceOnPattern g -> {
				into.add("rel:" + g.relation());
				into.add("pattern:" + g.pattern());
			}
			case Goal.HoldWhile g -> {
				if (g.innerGoal() != null) {
					mechanics(g.innerGoal(), into);
				}
				if (g.innerCondition() != null) {
					into.add("cond:" + ConditionTypes.of(g.innerCondition()).id());
				}
			}
			case Goal.Sequence g -> {
				into.add("seq:" + (g.ordered() ? "ordered" : "any") + (g.connector() == null ? "" : ":" + g.connector()));
				g.parts().forEach(part -> mechanics(part, into));
			}
			case Goal.Avoid g -> {
				if (g.action() != null) {
					mechanics(g.action(), into);
				}
			}
			default -> {
			}
		}
	}

	private static String relation(Goal goal) {
		return switch (goal) {
			case Goal.PlaceRel g -> g.relation().name();
			case Goal.PlaceOnPattern g -> g.relation().name();
			case Goal.HoldWhile g -> g.innerGoal() == null ? null : relation(g.innerGoal());
			case Goal.Avoid g -> g.action() == null ? null : relation(g.action());
			case Goal.Sequence g -> g.parts().stream().map(ContentFacts::relation)
					.filter(java.util.Objects::nonNull).findFirst().orElse(null);
			default -> null;
		};
	}

	/** Every id the goal points at, with the role it plays: object, anchor, recipient, container, tool. */
	static void targets(Goal goal, List<Target> into) {
		switch (goal) {
			case Goal.BreakBlock g -> add(into, "object", g.block());
			case Goal.PlaceBlock g -> add(into, "object", g.block());
			case Goal.AcquireItem g -> add(into, "object", g.item());
			case Goal.ConsumeItem g -> add(into, "object", g.item());
			case Goal.LookAtBlock g -> add(into, "object", g.block());
			case Goal.LookAtEntity g -> add(into, "object", g.entity());
			case Goal.DefeatEntity g -> add(into, "object", g.entity());
			case Goal.ReachBiome g -> add(into, "object", g.biome());
			case Goal.UseBlock g -> add(into, "object", g.block());
			case Goal.InteractEntity g -> add(into, "object", g.entity());
			case Goal.Equip g -> add(into, "object", g.item());
			case Goal.PlaceRel g -> {
				add(into, "object", g.block());
				add(into, "anchor", g.anchor());
			}
			case Goal.BuildPattern g -> add(into, "material", g.material());
			case Goal.PlaceOnPattern g -> {
				add(into, "object", g.block());
				add(into, "material", g.material());
			}
			case Goal.Craft g -> {
				add(into, "object", g.item());
				g.with().forEach(item -> add(into, "tool", item));
			}
			case Goal.Smelt g -> {
				add(into, "object", g.output());
				add(into, "tool", g.input());
			}
			case Goal.Give g -> {
				add(into, "object", g.item());
				add(into, "recipient", g.entity());
			}
			case Goal.PutIn g -> {
				add(into, "object", g.item());
				add(into, "anchor", g.container());
			}
			case Goal.TakeOut g -> {
				add(into, "object", g.item());
				add(into, "anchor", g.container());
			}
			case Goal.Hear g -> add(into, "object", g.source());
			case Goal.LieDown g -> add(into, "object", g.block());
			case Goal.Breed g -> add(into, "object", g.entity());
			case Goal.Trade g -> {
				add(into, "object", g.item());
				add(into, "recipient", g.entity());
			}
			case Goal.Mount g -> add(into, "object", g.entity());
			case Goal.HoldWhile g -> {
				add(into, "tool", g.item());
				if (g.innerGoal() != null) {
					targets(g.innerGoal(), into);
				}
				if (g.innerCondition() != null) {
					conditionTargets(g.innerCondition(), into);
				}
			}
			case Goal.Sequence g -> g.parts().forEach(part -> targets(part, into));
			case Goal.Avoid g -> {
				if (g.action() != null) {
					targets(g.action(), into);
				}
			}
			case Goal.ReachDimension g -> {
			}
			case Goal.ReachHeight g -> {
			}
			case Goal.Transit g -> {
			}
			case Goal.Wait g -> {
			}
			case Goal.Swim g -> {
			}
			case Goal.Fall g -> add(into, "landing", g.landing());
		}
	}

	private static void conditionTargets(Condition condition, List<Target> into) {
		switch (condition) {
			case Condition.NearEntity c -> add(into, "condition", c.entity());
			case Condition.NearBlock c -> add(into, "condition", c.block());
			case Condition.InBiome c -> add(into, "condition", c.biome());
			case Condition.Holding c -> add(into, "tool", c.item());
			case Condition.PlayerRel c -> add(into, "anchor", c.anchor());
			default -> {
			}
		}
	}

	private static void add(List<Target> into, String role, String id) {
		if (id != null && !id.isBlank()) {
			into.add(new Target(role, id));
		}
	}

	// ------------------------------------------------------------------ JSON

	static JsonObject toJson(List<Trail> trails) {
		JsonObject root = new JsonObject();
		root.addProperty("schema", 1);
		JsonArray array = new JsonArray();
		for (Trail trail : trails) {
			JsonObject t = new JsonObject();
			t.addProperty("id", trail.id());
			t.addProperty("level", trail.level());
			t.addProperty("order", trail.order());
			t.addProperty("demo", trail.demo());
			t.add("requires", strings(trail.requires()));
			t.addProperty("titleKo", trail.titleKo());
			t.addProperty("finaleKo", trail.finaleKo());
			t.add("grammar", strings(trail.grammar()));
			JsonArray steps = new JsonArray();
			for (Step step : trail.steps()) {
				JsonObject s = new JsonObject();
				s.addProperty("id", step.id());
				s.addProperty("goalType", step.goalType());
				s.addProperty("signature", step.signature());
				s.addProperty("shape", step.shape());
				s.addProperty("verb", step.verb());
				s.addProperty("object", step.object() == null ? "" : step.object());
				s.addProperty("relation", step.relation());
				s.add("conditions", strings(step.conditions()));
				s.add("mechanics", strings(step.mechanics()));
				s.addProperty("bare", step.bare());
				s.addProperty("formDependent", step.formDependent());
				s.add("grammar", strings(step.grammar()));
				s.add("teach", strings(step.teach()));
				JsonArray targets = new JsonArray();
				for (Target target : step.targets()) {
					JsonObject o = new JsonObject();
					o.addProperty("role", target.role());
					o.addProperty("id", target.id());
					targets.add(o);
				}
				s.add("targets", targets);
				s.addProperty("clueKo", step.clueKo());
				s.addProperty("hintKo", step.hintKo());
				steps.add(s);
			}
			t.add("steps", steps);
			array.add(t);
		}
		root.add("trails", array);
		return root;
	}

	static List<Trail> fromJson(JsonObject root) {
		List<Trail> trails = new ArrayList<>();
		for (JsonElement te : root.getAsJsonArray("trails")) {
			JsonObject t = te.getAsJsonObject();
			List<Step> steps = new ArrayList<>();
			for (JsonElement se : t.getAsJsonArray("steps")) {
				JsonObject s = se.getAsJsonObject();
				List<Target> targets = new ArrayList<>();
				for (JsonElement oe : s.getAsJsonArray("targets")) {
					JsonObject o = oe.getAsJsonObject();
					targets.add(new Target(o.get("role").getAsString(), o.get("id").getAsString()));
				}
				steps.add(new Step(s.get("id").getAsString(), s.get("goalType").getAsString(),
						s.get("signature").getAsString(), s.get("shape").getAsString(), s.get("verb").getAsString(),
						s.get("object").getAsString().isEmpty() ? null : s.get("object").getAsString(),
						s.get("relation").getAsString(), list(s, "conditions"), list(s, "mechanics"),
						s.get("bare").getAsBoolean(), s.has("formDependent") && s.get("formDependent").getAsBoolean(),
						list(s, "grammar"), list(s, "teach"), targets,
						s.get("clueKo").getAsString(), s.get("hintKo").getAsString()));
			}
			trails.add(new Trail(t.get("id").getAsString(), t.get("level").getAsInt(), t.get("order").getAsInt(),
					t.get("demo").getAsBoolean(), list(t, "requires"), t.get("titleKo").getAsString(),
					t.get("finaleKo").getAsString(), steps, t.has("grammar") ? list(t, "grammar") : List.of()));
		}
		return trails;
	}

	private static JsonArray strings(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}

	private static List<String> list(JsonObject object, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement element : object.getAsJsonArray(key)) {
			out.add(element.getAsString());
		}
		return out;
	}

	static void write(List<Trail> trails, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		Path temp = target.resolveSibling(target.getFileName() + ".tmp" + ProcessHandle.current().pid());
		Files.writeString(temp, GSON.toJson(toJson(trails)) + "\n", StandardCharsets.UTF_8);
		Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
				java.nio.file.StandardCopyOption.ATOMIC_MOVE);
	}

	static List<Trail> read(Path source) throws IOException {
		return fromJson(JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject());
	}

	/** The canonical learner path: prerequisites first, then lower Korean level, then {@code order}, then id. */
	static List<Trail> learnerPath(List<Trail> trails, boolean includeDemo) {
		Map<String, Trail> byId = new java.util.LinkedHashMap<>();
		for (Trail trail : trails) {
			if (includeDemo || !trail.demo()) {
				byId.putIfAbsent(trail.id(), trail);
			}
		}
		Comparator<Trail> order = Comparator.comparingInt(Trail::level).thenComparingInt(Trail::order)
				.thenComparing(Trail::id);
		Map<String, Integer> waiting = new java.util.HashMap<>();
		Map<String, List<Trail>> children = new java.util.HashMap<>();
		for (Trail trail : byId.values()) {
			Set<String> parents = new LinkedHashSet<>();
			for (String parent : trail.requires()) {
				if (byId.containsKey(parent) && !parent.equals(trail.id())) {
					parents.add(parent);
				}
			}
			waiting.put(trail.id(), parents.size());
			parents.forEach(parent -> children.computeIfAbsent(parent, key -> new ArrayList<>()).add(trail));
		}
		java.util.PriorityQueue<Trail> ready = new java.util.PriorityQueue<>(order);
		byId.values().stream().filter(trail -> waiting.get(trail.id()) == 0).forEach(ready::add);
		List<Trail> path = new ArrayList<>();
		Set<String> placed = new java.util.HashSet<>();
		while (!ready.isEmpty()) {
			Trail next = ready.poll();
			path.add(next);
			placed.add(next.id());
			for (Trail child : children.getOrDefault(next.id(), List.of())) {
				if (waiting.merge(child.id(), -1, Integer::sum) == 0) {
					ready.add(child);
				}
			}
		}
		// Trails on a prerequisite cycle cannot be ordered; they go last, in the same order.
		byId.values().stream().filter(trail -> !placed.contains(trail.id())).sorted(order).forEach(path::add);
		return path;
	}
}
