package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.config.Config;
import io.github.leks2205.hangulquest.config.WelcomePolicy;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.content.Background;
import io.github.leks2205.hangulquest.content.Content;
import io.github.leks2205.hangulquest.content.ContentLoader;
import io.github.leks2205.hangulquest.content.ContentReloadListener;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.AdaptiveTrailGenerator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.OverlayReloader;
import io.github.leks2205.hangulquest.quest.QuestlineStore;
import io.github.leks2205.hangulquest.quest.QuestlineWatcher;
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
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
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
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	private OverlayReloader<QuestValidator.Result> overlayReloader;
	private QuestlineWatcher watcher;
	private final java.util.concurrent.atomic.AtomicBoolean loading = new java.util.concurrent.atomic.AtomicBoolean();
	private boolean contentFailed;
	private boolean failureBannerPending;
	private Config config;
	private ProgressStore store;
	private Catalog catalog;
	private LearningService learn;
	private QuestEngine quests;
	private final LoadBackoff loadBackoff = new LoadBackoff();
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

	/** The signed-in account, or null when the session has none (dev, offline); that maps to the default profile. */
	private java.util.UUID accountId() {
		try {
			return mc.getUser().getProfileId();
		} catch (RuntimeException unavailable) {
			return null;
		}
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
		store = new ProgressStore(io.github.leks2205.hangulquest.learn.ProfilePaths.open(dir, accountId(), LOG), LOG);
		questlineDir = dir.resolve("questlines");
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
			if (watcher != null) {
				watcher.close();
			}
			if (store != null) {
				store.flush();
			}
			saveConfig();
		});
		// Concepts, language tables and trails load with every resource reload (start-up, F3+T).
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id("content"),
				new ContentReloadListener(this::contentInputs, this::publish, LOG));
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
			hud.banner(next.titleKo(), io.github.leks2205.hangulquest.util.I18n.pick(next.titleEn(), next.titleDe()), true);
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
			hud.banner(achievement.ko(), achievement.label(), true);
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
				.append(Component.literal(" · " + found.gloss(io.github.leks2205.hangulquest.util.Lang.current())).withStyle(ChatFormatting.GRAY))));
	}

	/** True once the content is published; after a failed first load, retries with a growing delay. */
	private boolean ensureLoaded() {
		if (quests != null) {
			return true;
		}
		if (failureBannerPending && hud != null) {
			failureBannerPending = false;
			hud.banner("콘텐츠를 불러오지 못했어요.",
					io.github.leks2205.hangulquest.util.I18n.t("hangulquest.ui.hud.load_failed"), false);
		}
		if (contentFailed && loadBackoff.mayAttempt(System.currentTimeMillis())) {
			reloadContent();
		}
		return false;
	}

	private ContentLoader.Inputs contentInputs() {
		Path luteFile = dir.resolve("vocab").resolve("lute-vocab.csv");
		return new ContentLoader.Inputs(questlineDir, config.luteImport ? luteFile : null);
	}

	/**
	 * Reloads concepts, language tables and trails without a resource reload: parsed on a
	 * background thread, published on the main thread. Ignored while another load is running.
	 */
	public void reloadContent() {
		if (!loading.compareAndSet(false, true)) {
			return;
		}
		var manager = mc.getResourceManager();
		ContentLoader.Inputs inputs = contentInputs();
		Background.executor().execute(() -> {
			Content content = ContentLoader.prepare(manager, inputs, LOG);
			mc.execute(() -> {
				loading.set(false);
				publish(content);
			});
		});
	}

	/** Re-reads every file of the local questline folder (not only the changed ones). */
	public void reloadQuestlines() {
		OverlayReloader<QuestValidator.Result> reloader = overlayReloader;
		if (reloader != null) {
			reloader.requestFull();
		}
	}

	/**
	 * Builds the learning service and quest engine from prepared content and publishes everything
	 * together, so a failure never leaves half the services set. A failed first load is retried with
	 * a growing delay; a failed reload keeps what is running.
	 */
	private void publish(Content content) {
		long attemptAt = System.currentTimeMillis();
		try {
			if (!content.ok()) {
				throw new IllegalStateException("content could not be prepared", content.failure());
			}
			var builtCatalog = content.catalog();
			var lute = content.lute();
			var builtLearn = new LearningService(store.data(), store, builtCatalog);
			// Today's review trail must exist before the engine restores, or its progress is dropped.
			List<Questline> trails = new java.util.ArrayList<>(content.validated().trails());
			Questline adaptive = AdaptiveTrailGenerator.restore(builtCatalog,
					store.data().quests.adaptive, io.github.leks2205.hangulquest.learn.Streak.dayKey(java.time.LocalDateTime.now()));
			if (adaptive != null) {
				trails.removeIf(trail -> trail.id().equals(adaptive.id()));
				trails.add(adaptive);
			}
			QuestEngine builtQuests = new QuestEngine(trails, builtCatalog, builtLearn, LOG);

			// Nothing above has thrown: apply the progress changes and publish.
			if (lute != null) {
				long now = System.currentTimeMillis();
				builtLearn.seedFromLute(lute.levels(), lute.known(), now);
				ProgressData.LuteInfo info = new ProgressData.LuteInfo();
				info.file = content.luteFile().getFileName().toString();
				info.mtime = Files.getLastModifiedTime(content.luteFile()).toMillis();
				info.imported = lute.words().size();
				info.skipped = lute.skipped();
				info.phrases = lute.phrases();
				info.at = now;
				store.data().vocab.luteImport = info;
				store.markDirty();
			}
			catalog = builtCatalog;
			learn = builtLearn;
			quests = builtQuests;
			contentFailed = false;
			failureBannerPending = false;
			loadBackoff.succeeded();
			watchQuestlines(content);
			writeVocabReadme();
			LOG.info("catalog: {} words ({} curated), {} questlines, overlay folder {} ({} files)",
					catalog.size(), content.concepts().size(), content.validated().trails().size(),
					questlineDir, QuestlineStore.fileCount(questlineDir));
		} catch (Exception failed) {
			if (quests != null) {
				LOG.warn("content reload failed, keeping the loaded content", failed);
				return;
			}
			contentFailed = true;
			boolean first = loadBackoff.failed(attemptAt);
			if (first) {
				LOG.error("could not initialise hangulquest content, retrying in {} s",
						LoadBackoff.DELAYS_MS[0] / 1000, failed);
				failureBannerPending = true;
			} else {
				LOG.warn("hangulquest content still not loading (attempt {}), next try in {} s: {}",
						loadBackoff.failures(), loadBackoff.waitMs(attemptAt) / 1000, failed.toString());
			}
		}
	}

	/** Points the overlay reloader at the new content; the folder watcher starts once and stays. */
	private void watchQuestlines(Content content) {
		if (overlayReloader != null) {
			overlayReloader.close();
		}
		var builtCatalog = content.catalog();
		overlayReloader = new OverlayReloader<>(questlineDir, content.overlay(),
				content.shipped().trails(), trails -> QuestValidator.validate(trails, builtCatalog, LOG),
				Background.executor(), mc::execute, this::applyOverlay, LOG);
		if (watcher == null) {
			watcher = QuestlineWatcher.start(questlineDir, QuestlineWatcher.DEBOUNCE_MS, () -> {
				OverlayReloader<QuestValidator.Result> reloader = overlayReloader;
				if (reloader != null) {
					reloader.request();
				}
			}, LOG);
		}
	}

	/** Main thread: swaps in the trails after a change in the local folder. */
	private void applyOverlay(QuestValidator.Result validated) {
		if (quests == null) {
			return;
		}
		quests.replaceTrails(validated.trails());
		if (quests.activeTrail() == null) {
			pendingAutoStart = true;
		}
		int count = validated.trails().size();
		LOG.info("questlines reloaded: {} trails, {} problems", count, validated.problems().size());
		if (hud != null) {
			hud.banner("퀘스트 " + count + "개를 다시 읽었어요.",
					io.github.leks2205.hangulquest.util.I18n.t("hangulquest.ui.quest.reloaded", count), true);
		}
	}

	public Path questlineDir() {
		return questlineDir;
	}

	private void ensureAdaptiveTrail(Observation obs) {
		// The study day (it turns over at 04:00), so the review trail lasts as long as the streak day.
		String today = io.github.leks2205.hangulquest.learn.Streak.dayKey(java.time.LocalDateTime.now());
		ProgressData.Adaptive adaptive = store.data().quests.adaptive;
		if (adaptive != null && today.equals(adaptive.generatedFor) && quests.byId("adaptive") != null) {
			return; // rebuilt from the saved concepts before the engine restored
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
					Lute import for 한글 탐험 (Hangul Quest)
					=====================================

					Put a Lute CSV export here as lute-vocab.csv (term;translation;pronunciation;status;tags).
					The mod reads it at start and takes over statuses 1–5 and 99 (known); status 0 is ignored.
					To import again, switch the Lute import off and on in the journal settings.
					The file is never changed.

					Deutsch: Lege hier einen Lute-CSV-Export als lute-vocab.csv ab. Der Mod liest ihn beim Start
					und verändert die Datei nie.
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
