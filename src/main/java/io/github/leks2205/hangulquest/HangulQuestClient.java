package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.config.Config;
import io.github.leks2205.hangulquest.config.WelcomePolicy;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.LangTables;
import io.github.leks2205.hangulquest.data.LuteCsv;
import io.github.leks2205.hangulquest.data.LuteImport;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.AdaptiveTrailGenerator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestlineStore;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.sense.EnvironmentSensor;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.ui.HudController;
import io.github.leks2205.hangulquest.ui.JournalScreen;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.ui.WelcomeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Client entrypoint and service container. Everything is local: no packets, no server side. */
public final class HangulQuestClient implements ClientModInitializer {
	public static final String MOD_ID = "hangulquest";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
	private static final boolean DEBUG_UI = Boolean.getBoolean("hangulquest.debug");
	public static final FontDescription.Resource HANGUL_FONT = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath(MOD_ID, "hangul"));

	private static HangulQuestClient instance;

	private final Minecraft mc = Minecraft.getInstance();
	private Path dir;
	private Path questlineDir;
	private long questlineSnapshot;
	private Config config;
	private ProgressStore store;
	private Catalog catalog;
	private LearningService learn;
	private QuestEngine quests;
	private HudController hud;
	private EnvironmentSensor sensor;
	private KeyMapping keyJournal;
	private KeyMapping keyHint;
	private KeyMapping keyReview;
	private KeyMapping keyHud;
	private KeyMapping keyFocus;
	private KeyMapping keyAnswer;

	private Observation lastObservation = new Observation();
	private final Map<String, Long> wordCooldowns = new HashMap<>();
	private Map<String, Integer> lastInventory = new HashMap<>();
	private String lastLooked;
	private long lookedSince;
	private long lastAmbientAt;
	private boolean pendingAutoStart;
	private boolean welcomeChecked;
	private long lastTouch;
	private static final boolean SELFTEST = Boolean.getBoolean("hangulquest.selftest");
	private static final boolean CELEBRATE_DEMO = Boolean.getBoolean("hangulquest.celebrateDemo");
	private static final boolean UI_DEMO = Boolean.getBoolean("hangulquest.uidemo");
	private static final boolean QUESTLOG_DEMO = Boolean.getBoolean("hangulquest.questlogdemo");
	private int selftestTicks;
	private int questDemoTicks;
	private int uiDemoTicks;

	public static HangulQuestClient get() {
		return instance;
	}

	/** Temporary UI diagnostics (only logs when -Dhangulquest.debug=true). */
	public void debugLog(String message, Object... args) {
		if (DEBUG_UI) {
			LOG.info("[ui] " + message, args);
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static Style hangul() {
		return Style.EMPTY.withFont(HANGUL_FONT);
	}

	public Config config() {
		return config;
	}

	public Catalog catalog() {
		return catalog;
	}

	public LearningService learn() {
		return learn;
	}

	public QuestEngine quests() {
		return quests;
	}

	public HudController hud() {
		return hud;
	}

	/** The rebindable key that opens the quiz screen. */
	public KeyMapping answerKey() {
		return keyAnswer;
	}

	public Observation lastObservation() {
		return lastObservation;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		try {
			Files.createDirectories(dir);
		} catch (IOException failed) {
			LOG.warn("could not create config folder {}: {}", dir, failed.toString());
		}
		config = Config.load(dir.resolve("config.json"));
		io.github.leks2205.hangulquest.util.Lang.set(config.language);
		store = new ProgressStore(dir, LOG);
		hud = new HudController(this);
		hud.register();
		sensor = new EnvironmentSensor();
		sensor.register();
		registerKeys();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			pendingAutoStart = true;
			lastInventory = new HashMap<>();
			if (hud != null) {
				hud.onWorldJoin();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (store != null) {
				store.flush();
			}
			lastObservation = new Observation();
			sensor.reset();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (store != null) {
				store.flush();
			}
			saveConfig();
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ItemTooltipCallback.EVENT.register(this::addTooltip);

		LOG.info("hangulquest ready (config {})", dir);
	}

	private void registerKeys() {
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		keyJournal = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hangulquest.journal", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));
		keyHint = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hangulquest.hint", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, category));
		keyReview = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hangulquest.review", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
		keyHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hangulquest.hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, category));
		keyFocus = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hangulquest.focus", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, category));
		// R: vanilla leaves it unbound (G is "quick actions", O "friends", P "social interactions").
		keyAnswer = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hangulquest.answer", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, category));
	}

	private void onTick(Minecraft client) {
		long now = System.currentTimeMillis();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			if (store != null) {
				store.tick(now);
			}
			return;
		}
		if (!ensureLoaded()) {
			return;
		}

		Observation obs = sensor.tick();
		lastObservation = obs;
		if (now - lastTouch > 20_000L) {
			lastTouch = now;
			learn.touch(now);
		}
		if (!welcomeChecked && mc.gui.screen() == null) {
			// Once per game start, on the first world join: the screen never blocks quests.
			welcomeChecked = true;
			if (WelcomePolicy.onFirstJoin(config, store.data())) {
				WelcomeScreen.openOnJoin();
			}
			saveConfig();
		}
		if (pendingAutoStart) {
			pendingAutoStart = false;
			ensureAdaptiveTrail(obs);
			quests.autoStart(obs, hud);
		}
		quests.tick(obs, hud);
		hud.tick(obs);
		ambientCards(obs);
		handleKeys(obs);
		store.tick(now);
		if (SELFTEST) {
			selftest(obs);
		}
		if (UI_DEMO) {
			uiDemo();
		}
		if (QUESTLOG_DEMO) {
			questLogDemo(obs);
		}
	}

	/** Dev-only: quiz -> reveal -> journal, for UI screenshots. */
	private void uiDemo() {
		uiDemoTicks++;
		if (uiDemoTicks == 80) {
			hud.selftestStartQuiz("concept:stone");
			LOG.info("[uidemo] quiz shown");
		} else if (uiDemoTicks == 150) {
			hud.selftestRevealQuiz();
			LOG.info("[uidemo] quiz revealed");
		} else if (uiDemoTicks == 230) {
			mc.setScreenAndShow(new JournalScreen());
			LOG.info("[uidemo] journal opened");
		}
	}

	/** Dev-only: -Dhangulquest.questlogdemo=true fills the quest log with open quests for screenshots. */
	private void questLogDemo(Observation obs) {
		questDemoTicks++;
		if (mc.player == null || quests == null) {
			return;
		}
		questLogDemoJournal();
		if (questDemoTicks != 100) {
			return;
		}
		List<Questline> roots = quests.trails().stream()
				.filter(trail -> !trail.adaptive())
				.filter(trail -> trail.requires().completed().isEmpty())
				.sorted(java.util.Comparator.comparingInt(Questline::order))
				.limit(4)
				.toList();
		for (Questline root : roots) {
			quests.startTrail(root.id(), obs, hud);
		}
		if (!roots.isEmpty()) {
			quests.focus(roots.get(0).id());
		}
		for (int i = 0; i < roots.size(); i++) {
			quests.demoProgress(roots.get(i).id(), i == 1 ? 1 : i == 2 ? 0 : i == 3 ? 2 : 1);
		}
		// Leave the last one unchecked, like a player who keeps it for later.
		if (roots.size() > 3) {
			quests.setTracked(roots.get(roots.size() - 1).id(), false);
		}
		quests.focus(roots.get(0).id());
		LOG.info("[questlog] tracked {} roots={}", quests.trackedCount(), roots.size());
	}

	/** Dev-only: opens the journal on the quest tab a moment after filling the log. */
	private void questLogDemoJournal() {
		if (questDemoTicks == 400) {
			mc.setScreenAndShow(new JournalScreen(4));
			LOG.info("[questlog] journal opened on the quest tab");
		}
	}

	/** Dev-only: -Dhangulquest.selftest=true drives the UI for screenshots. */
	private void selftest(Observation obs) {
		selftestTicks++;
		if (!CELEBRATE_DEMO && selftestTicks == 40) {
			int width = mc.font.width(Component.literal("한").setStyle(hangul()));
			LOG.info("[selftest] hangul glyph width={} catalog={} trails={} active={}", width,
					catalog.size(), quests.trails().size(),
					quests.activeTrail() == null ? "-" : quests.activeTrail().id());
			QuestEngine.HintView view = quests.hint(obs, hud);
			if (view != null) {
				hud.showHint(view, obs.now);
				LOG.info("[selftest] hint shown, sidequest={}", view.sidequestStarted());
			}
		} else if (!CELEBRATE_DEMO && selftestTicks == 900) {
			mc.setScreenAndShow(new JournalScreen());
			LOG.info("[selftest] journal opened");
		}
		if (CELEBRATE_DEMO) {
			if (selftestTicks == 80) {
				hud.selftestCelebration(io.github.leks2205.hangulquest.ui.Celebration.Kind.STEP);
				LOG.info("[selftest] step celebration");
			} else if (selftestTicks == 300) {
				hud.selftestCelebration(io.github.leks2205.hangulquest.ui.Celebration.Kind.SIDE);
				LOG.info("[selftest] side celebration");
			} else if (selftestTicks == 520) {
				hud.selftestCelebration(io.github.leks2205.hangulquest.ui.Celebration.Kind.TRAIL);
				LOG.info("[selftest] trail celebration");
			}
		}
	}

	/** Cycles the focused quest in the log; the hint key and the sidequest follow the focus. */
	private void cycleFocus() {
		var views = quests.trackedViews();
		if (views.size() < 2) {
			return;
		}
		int index = 0;
		for (int i = 0; i < views.size(); i++) {
			if (views.get(i).id().equals(quests.focusedId())) {
				index = i;
				break;
			}
		}
		QuestEngine.TrackView next = views.get((index + 1) % views.size());
		if (quests.focus(next.id())) {
			hud.banner(next.titleKo(), io.github.leks2205.hangulquest.util.Tr.pick(next.titleEn(), next.titleDe()), true);
		}
	}

	private void handleKeys(Observation obs) {
		if (mc.gui.screen() != null) {
			return;
		}
		while (keyJournal.consumeClick()) {
			Screen screen = new JournalScreen();
			mc.setScreenAndShow(screen);
			return;
		}
		while (keyHint.consumeClick()) {
			QuestEngine.HintView view = quests.hint(obs, hud);
			if (view != null) {
				hud.showHint(view, obs.now);
			}
		}
		while (keyReview.consumeClick()) {
			hud.startFromDue();
		}
		while (keyFocus.consumeClick()) {
			cycleFocus();
		}
		while (keyAnswer.consumeClick()) {
			hud.openAnswer();
		}
		while (keyHud.consumeClick()) {
			config.hudCards = !config.hudCards;
			saveConfig();
		}
	}

	private void ambientCards(Observation obs) {
		if (!config.hudCards || !config.lookAtCards || obs.dead) {
			return;
		}
		long now = obs.now;
		if (now - lastAmbientAt < 4000L) {
			return;
		}
		if (obs.lookedBlock != null && !obs.lookedBlock.isBlank()) {
			if (obs.lookedBlock.equals(lastLooked)) {
				if (now - lookedSince >= 700L) {
					debugLog("ambient dwell on {} (word={})", obs.lookedBlock,
							catalog.byItemId(obs.lookedBlock).map(word -> word.id).orElse("-"));
					catalog.byItemId(obs.lookedBlock).ifPresent(word -> ambientWord(word, now));
					lookedSince = now;
				}
			} else {
				lastLooked = obs.lookedBlock;
				lookedSince = now;
			}
		} else if (lastLooked != null) {
			lastLooked = null;
		}

		if (!obs.creative && obs.inventory != null && !obs.inventory.isEmpty()) {
			for (Map.Entry<String, Integer> entry : obs.inventory.entrySet()) {
				int previous = lastInventory.getOrDefault(entry.getKey(), 0);
				if (entry.getValue() > previous) {
					final int gained = entry.getValue() - previous;
					if (gained > 0) {
						catalog.byItemId(entry.getKey()).ifPresent(word -> ambientWord(word, now));
					}
					break;
				}
			}
		}
		lastInventory = obs.inventory == null ? Map.of() : new HashMap<>(obs.inventory);
	}

	private void ambientWord(Word word, long now) {
		if (word == null || word.phrase) {
			return;
		}
		Long until = wordCooldowns.get(word.id);
		if (until != null && until > now) {
			return;
		}
		wordCooldowns.put(word.id, now + 120_000L);
		learn.introduce(word, now);
		debugLog("ambient card {}", word.id);
		hud.wordCard(word);
		lastAmbientAt = now;
		for (var achievement : learn.checkAchievements()) {
			hud.banner(achievement.ko(), achievement.de(), true);
		}
	}

	private void addTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (config == null || !config.tooltips || catalog == null) {
			return;
		}
		Optional<Word> word = catalog.byTranslationKey(stack.getItem().getDescriptionId());
		if (word.isEmpty()) {
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (id != null) {
				word = catalog.byItemId(id.toString());
			}
		}
		word.ifPresent(found -> lines.add(Component.empty()
				.append(KoText.ko(found.ko))
				.append(Component.literal(" · " + io.github.leks2205.hangulquest.util.Tr.pick(found.en, found.deShort)).withStyle(ChatFormatting.GRAY))));
	}

	private boolean ensureLoaded() {
		if (catalog != null) {
			return true;
		}
		if (mc.getResourceManager() == null) {
			return false;
		}
		try {
			List<Concept> concepts = ConceptLoader.load();
			LangTables tables = LangTables.load(mc);
			LuteImport.Result lute = null;
			Path luteFile = dir.resolve("vocab").resolve("lute-vocab.csv");
			if (config.luteImport && Files.exists(luteFile)) {
				lute = LuteImport.importRows(LuteCsv.parseFile(luteFile), concepts);
			}
			catalog = Catalog.build(concepts, tables,
					lute == null ? List.of() : lute.words());
			learn = new LearningService(store.data(), store, catalog);
			if (lute != null) {
				long now = System.currentTimeMillis();
				learn.seedFromLute(lute.levels(), lute.known(), now);
				ProgressData.LuteInfo info = new ProgressData.LuteInfo();
				info.file = luteFile.getFileName().toString();
				info.mtime = Files.getLastModifiedTime(luteFile).toMillis();
				info.imported = lute.words().size();
				info.skipped = lute.skipped();
				info.phrases = lute.phrases();
				info.at = now;
				store.data().vocab.luteImport = info;
				store.markDirty();
			}
			questlineDir = dir.resolve("questlines");
			QuestlineStore.exportShipped(questlineDir, LOG);
			questlineSnapshot = QuestlineStore.snapshot(questlineDir);
			QuestValidator.Result validated = QuestValidator.validate(
					QuestlineStore.loadAll(questlineDir, LOG), catalog, LOG);
			quests = new QuestEngine(validated.trails(), catalog, learn, LOG);
			LOG.info("questline folder: {} ({} trail files)", questlineDir,
					QuestlineStore.fileCount(questlineDir));
			writeVocabReadme();
			LOG.info("catalog: {} words ({} curated), {} questlines", catalog.size(), concepts.size(),
					validated.trails().size());
			return true;
		} catch (Exception failed) {
			LOG.error("could not initialise hangulquest content", failed);
			return false;
		}
	}

	/** Re-reads config/hangulquest/questlines when a file was added or changed. */
	public void reloadQuestlinesIfChanged(Observation obs) {
		if (questlineDir == null || quests == null) {
			return;
		}
		long snapshot = QuestlineStore.snapshot(questlineDir);
		if (snapshot == questlineSnapshot) {
			return;
		}
		questlineSnapshot = snapshot;
		QuestValidator.Result validated = QuestValidator.validate(
				QuestlineStore.loadAll(questlineDir, LOG), catalog, LOG);
		quests.replaceTrails(validated.trails());
		if (quests.activeTrail() == null) {
			quests.autoStart(obs, hud);
		}
		int count = validated.trails().size();
		LOG.info("questlines reloaded: {} trails, {} problems", count, validated.problems().size());
		if (hud != null) {
			hud.banner("퀘스트 " + count + "개를 다시 읽었어요.",
					io.github.leks2205.hangulquest.util.Tr.t("Questlinien neu geladen: ") + count + ".", true);
		}
	}

	public Path questlineDir() {
		return questlineDir;
	}

	private void ensureAdaptiveTrail(Observation obs) {
		String today = LocalDate.now().toString();
		ProgressData.Adaptive adaptive = store.data().quests.adaptive;
		if (adaptive != null && today.equals(adaptive.generatedFor)) {
			quests.byId("adaptive");
			return;
		}
		String worldKey = mc.level == null ? "unknown" : mc.level.dimension().identifier() + "@" + today;
		Questline generated = AdaptiveTrailGenerator.generate(catalog, learn, worldKey, obs.now);
		if (generated == null) {
			return;
		}
		quests.addTrail(generated);
		ProgressData.Adaptive info = new ProgressData.Adaptive();
		info.seed = generated.id().hashCode() * 31L + today.hashCode();
		info.generatedFor = today;
		info.wordIds = generated.concepts();
		store.data().quests.adaptive = info;
		store.markDirty();
	}

	private void writeVocabReadme() {
		Path vocabDir = dir.resolve("vocab");
		Path readme = vocabDir.resolve("README.txt");
		if (Files.exists(readme)) {
			return;
		}
		try {
			Files.createDirectories(vocabDir);
			Files.writeString(readme, """
					Lute-Import für 한글 탐험
					=======================

					Lege hier eine Lute-CSV ab (Term;Übersetzung;Aussprache;Status;Tags).
					Der Mod liest sie beim Start und übernimmt Status 1–5 sowie 99 (bekannt).
					Status 0 wird ignoriert. Neu importieren: Optionen → Lute-Import aus/an.
					Die Datei wird nie verändert.
					""");
		} catch (IOException failed) {
			LOG.warn("could not write vocab README: {}", failed.toString());
		}
	}

	public void saveConfig() {
		if (config != null) {
			config.save(dir.resolve("config.json"));
		}
	}

	public void openJournal() {
		mc.setScreenAndShow(new JournalScreen());
	}
}
