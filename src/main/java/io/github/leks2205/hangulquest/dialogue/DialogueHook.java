package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.ui.dialogue.DialogueScreen;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.villager.ConversationSelector;
import io.github.leks2205.hangulquest.villager.Region;
import io.github.leks2205.hangulquest.villager.Request;
import io.github.leks2205.hangulquest.villager.RequestDialogues;
import io.github.leks2205.hangulquest.villager.Requests;
import io.github.leks2205.hangulquest.villager.VillagerProfile;
import io.github.leks2205.hangulquest.villager.VillagerProfiles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Sneak + right-click on a villager (or trader) opens a conversation. The click is taken over only
 * when there is something to say; otherwise it passes, so trading works as always. Taken over
 * means {@code FAIL}: the game then neither opens the trade window nor uses the held item, and sends
 * nothing to the server. The screen opens on the next tick.
 *
 * <p>A villager's conversation is chosen by the {@link ConversationSelector} from its profile
 * ({@link VillagerProfiles}), the requests and the player's level; this class only describes the click and
 * applies what the conversation changes when it opens.
 */
public final class DialogueHook {
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest");
	private static final String VILLAGER = "minecraft:villager";

	/** What the next tick opens: a plain dialogue, or a villager's choice with what it was chosen for. */
	private record Pending(Dialogue dialogue, UUID npc, ConversationSelector.Choice choice, Region region, boolean baby,
						   String profession, long day) {
	}

	private static volatile DialogueContent content = DialogueContent.EMPTY;
	/** Set by the click, taken by the next tick; main thread only. */
	private static Pending pending;
	private static VillagerProfiles profiles;
	private static Path profilesDir;

	private DialogueHook() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(DialogueHook::onUse);
		ClientTickEvents.END_CLIENT_TICK.register(DialogueHook::onTick);
	}

	/** Called with every content load. */
	public static void publish(DialogueContent loaded) {
		content = loaded == null ? DialogueContent.EMPTY : loaded;
	}

	public static DialogueContent content() {
		return content;
	}

	/** The villagers of the current profile; null before the learning data is loaded. */
	public static VillagerProfiles villagers() {
		HangulQuestClient hq = HangulQuestClient.get();
		if (hq == null || hq.learn() == null) {
			return null;
		}
		Path dir = hq.learn().store().file().toAbsolutePath().getParent();
		if (profiles == null || !dir.equals(profilesDir)) {
			profiles = VillagerProfiles.open(dir, LOG);
			profilesDir = dir;
		}
		Requests requests = hq.requests();
		profiles.protect(requests == null ? null : requests::isOpen);
		return profiles;
	}

	/** The in-game day: what friendship and the daily offer count in. */
	static long day(Level level) {
		return Math.floorDiv(level.getDefaultClockTime(), 24_000L);
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity,
										   net.minecraft.world.phys.EntityHitResult hit) {
		DialogueTrigger.Click click = new DialogueTrigger.Click(level.isClientSide(), hand == InteractionHand.MAIN_HAND,
				player.isShiftKeyDown(), player.isSpectator(), Minecraft.getInstance().gui.screen() != null);
		if (entity == null || !DialogueTrigger.wanted(click)) {
			return InteractionResult.PASS;
		}
		Pending found = entity instanceof Villager villager ? villagerTalk(player, level, villager)
				: DialogueTrigger.select(content.set(), click, targetOf(entity))
				.map(dialogue -> new Pending(dialogue, entity.getUUID(), null, null, false, null, 0)).orElse(null);
		if (found == null) {
			return InteractionResult.PASS;
		}
		pending = found;
		return InteractionResult.FAIL;
	}

	/** What a villager says now, tried out on a copy of its profile; null when it has nothing to say. */
	private static Pending villagerTalk(Player player, Level level, Villager villager) {
		HangulQuestClient hq = HangulQuestClient.get();
		VillagerProfiles known = villagers();
		if (hq == null || known == null || hq.requests() == null) {
			return null;
		}
		UUID uuid = villager.getUUID();
		String profession = villager.getVillagerData().profession().unwrapKey()
				.map(key -> key.identifier().toString()).orElse(null);
		Region region = Region.ofVillagerType(villager.getVillagerData().type().unwrapKey()
				.map(key -> key.identifier().toString()).orElse(null));
		boolean baby = villager.isBaby();
		long day = day(level);
		VillagerProfile scratch = known.preview(uuid, region, baby, profession, System.currentTimeMillis());
		scratch.talkedOn(day);
		Requests requests = hq.requests();
		ProgressData.OpenRequest open = requests.openFor(uuid.toString());
		ItemStack held = player.getMainHandItem();
		String heldItem = held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
		int level1 = playerLevel(hq.learn());
		ConversationSelector.Situation situation = new ConversationSelector.Situation(scratch, VILLAGER, profession, baby,
				level1, day, requests.request(open), open != null && requests.ready(open, heldItem, held.getCount()),
				requests.canAccept(uuid.toString()),
				requests.choose(scratch.personality, scratch.region, profession, level1, uuid, day));
		Optional<ConversationSelector.Choice> choice = ConversationSelector.select(content.set(), situation);
		return choice.map(chosen -> new Pending(null, uuid, chosen, region, baby, profession, day)).orElse(null);
	}

	/** The learner level conversations and requests are chosen for (1 to 6), as the quest clues use it. */
	static int playerLevel(LearningService learn) {
		return Math.max(1, Math.min(6, learn.grammarLevel() + 1));
	}

	private static DialogueTrigger.Target targetOf(Entity entity) {
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (key == null) {
			return null;
		}
		boolean baby = entity instanceof net.minecraft.world.entity.LivingEntity living && living.isBaby();
		return new DialogueTrigger.Target(key.toString(), null, baby, entity.getUUID().hashCode());
	}

	private static void onTick(Minecraft mc) {
		Pending open = pending;
		pending = null;
		if (open == null || mc.player == null || mc.gui.screen() != null) {
			return;
		}
		HangulQuestClient hq = HangulQuestClient.get();
		if (hq == null || hq.learn() == null || hq.quests() == null || hq.catalog() == null) {
			return;
		}
		LearningService learn = hq.learn();
		DialogueSink learning = new GameDialogueSink(learn, hq.catalog(), trailStarter(hq, learn),
				System::currentTimeMillis, open.dialogue() != null ? open.dialogue().id() : idOf(open.choice()));
		DialogueCoverage.Vocabulary vocabulary = DialogueCoverage.vocabulary(hq.catalog(), content.lexicon(),
				id -> learn.isKnown(id) || learn.introduced(id), Lang.current());
		if (open.choice() == null) {
			mc.setScreenAndShow(new DialogueScreen(new DialogueSession(open.dialogue(), learning), null, vocabulary,
					DialogueScreen.Voice.NONE, hq.audio()));
			return;
		}
		VillagerProfiles known = villagers();
		Requests requests = hq.requests();
		if (known == null || requests == null) {
			return;
		}
		VillagerProfile profile = known.meet(open.npc(), open.region(), open.baby(), open.profession(),
				System.currentTimeMillis()).profile();
		profile.talkedOn(open.day());
		ConversationSelector.opened(profile, open.choice(), open.day());
		Dialogue dialogue = dialogueOf(open.choice(), profile);
		if (open.choice() instanceof ConversationSelector.HandIn handIn) {
			handIn(hq, requests, profile, handIn.request(), learning);
		}
		known.save();
		DialogueSink sink = new VillagerDialogueSink(learning, new VillagerDialogueSink.Villager() {
			@Override
			public void acceptBanmal() {
				profile.banmal = true;
				known.save();
				hq.hud().banner("말 편하게 해요!", I18n.t("hangulquest.ui.villager.banmal", profile.name), true);
			}

			@Override
			public void acceptRequest(String requestId) {
				Request request = requests.set().get(requestId);
				ProgressData.OpenRequest taken = requests.accept(request, profile.uuid, profile.name,
						System.currentTimeMillis());
				if (taken == null) {
					return;
				}
				hq.hud().banner(profile.name + "의 부탁", taken.tracked
						? I18n.t("hangulquest.ui.request.accepted", I18n.t(request.titleKey()))
						: I18n.t("hangulquest.ui.request.waiting"), true);
			}
		});
		mc.setScreenAndShow(new DialogueScreen(new DialogueSession(dialogue, sink), profile.name, vocabulary,
				new DialogueScreen.Voice(profile.currentVoice(), profile.name, () -> profile.banmal,
						profile.baby ? SpeechLevel.BANMAL
								: profile.personality.register()), hq.audio()));
	}

	private static String idOf(ConversationSelector.Choice choice) {
		return switch (choice) {
			case ConversationSelector.Talk talk -> talk.dialogue().id();
			case ConversationSelector.Offer offer -> offer.request().id();
			case ConversationSelector.Reminder reminder -> reminder.request().id();
			case ConversationSelector.HandIn handIn -> handIn.request().id();
		};
	}

	/** The conversation a choice is spoken through. */
	static Dialogue dialogueOf(ConversationSelector.Choice choice, VillagerProfile profile) {
		return switch (choice) {
			case ConversationSelector.Talk talk -> talk.dialogue();
			case ConversationSelector.Offer offer -> RequestDialogues.offer(offer.request(), profile.personality);
			case ConversationSelector.Reminder reminder ->
					RequestDialogues.single(reminder.request(), profile.personality, Request.Part.REMINDER);
			case ConversationSelector.HandIn handIn ->
					RequestDialogues.single(handIn.request(), profile.personality, Request.Part.THANKS);
		};
	}

	/** Closes the request: counted, its words practised, friendship +2 (already added by the selector). */
	private static void handIn(HangulQuestClient hq, Requests requests, VillagerProfile profile, Request request,
							   DialogueSink learning) {
		if (requests.handIn(profile.uuid) == null) {
			return;
		}
		for (String word : request.words()) {
			learning.practiceWord(word, true);
		}
		hq.learn().markDirty();
		hq.hud().banner(profile.name + "의 부탁", I18n.t("hangulquest.ui.request.finished"), true);
	}

	/** Starts a trail the player may start: it exists, is not finished, and its requirements are met. */
	private static Predicate<String> trailStarter(HangulQuestClient hq, LearningService learn) {
		QuestEngine quests = hq.quests();
		return id -> {
			Questline trail = quests.byId(id);
			var completed = learn.data().quests.completed;
			if (trail == null || completed != null && completed.containsKey(id) || !quests.unlocked(trail)) {
				return false;
			}
			return quests.startTrail(id, hq.lastObservation(), hq.hud());
		};
	}
}
