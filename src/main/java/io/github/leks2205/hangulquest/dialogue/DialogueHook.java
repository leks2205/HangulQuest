package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.ui.dialogue.DialogueScreen;
import io.github.leks2205.hangulquest.util.Lang;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Sneak + right-click on a villager (or trader) opens a conversation. The click is taken over only
 * when a dialogue exists for that NPC; otherwise it passes, so trading works as always. Taken over
 * means {@code FAIL}: the game then neither opens the trade window nor uses the held item, and sends
 * nothing to the server. The screen opens on the next tick.
 */
public final class DialogueHook {
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest");

	private record Pending(Dialogue dialogue, UUID npc) {
	}

	private static volatile DialogueContent content = DialogueContent.EMPTY;
	/** Set by the click, taken by the next tick; main thread only. */
	private static Pending pending;
	private static VillagerNames names;
	private static Path namesDir;

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

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity,
										   net.minecraft.world.phys.EntityHitResult hit) {
		DialogueTrigger.Click click = new DialogueTrigger.Click(level.isClientSide(), hand == InteractionHand.MAIN_HAND,
				player.isShiftKeyDown(), player.isSpectator(), Minecraft.getInstance().gui.screen() != null);
		if (entity == null || !DialogueTrigger.wanted(click)) {
			return InteractionResult.PASS;
		}
		Optional<Dialogue> found = DialogueTrigger.select(content.set(), click, targetOf(entity));
		if (found.isEmpty()) {
			return InteractionResult.PASS;
		}
		pending = new Pending(found.get(), entity.getUUID());
		return InteractionResult.FAIL;
	}

	private static DialogueTrigger.Target targetOf(Entity entity) {
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (key == null) {
			return null;
		}
		String profession = null;
		if (entity instanceof Villager villager) {
			profession = villager.getVillagerData().profession().unwrapKey()
					.map(professionKey -> professionKey.identifier().toString()).orElse(null);
		}
		boolean baby = entity instanceof net.minecraft.world.entity.LivingEntity living && living.isBaby();
		return new DialogueTrigger.Target(key.toString(), profession, baby, entity.getUUID().hashCode());
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
		DialogueSink sink = new GameDialogueSink(learn, hq.catalog(), trailStarter(hq, learn),
				System::currentTimeMillis, open.dialogue().id());
		DialogueSession session = new DialogueSession(open.dialogue(), sink);
		DialogueCoverage.Vocabulary vocabulary = DialogueCoverage.vocabulary(hq.catalog(), content.lexicon(),
				id -> learn.isKnown(id) || learn.introduced(id), Lang.current());
		mc.setScreenAndShow(new DialogueScreen(session, nameOf(learn, open.npc()), vocabulary));
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

	private static String nameOf(LearningService learn, UUID npc) {
		Path dir = learn.store().file().toAbsolutePath().getParent();
		if (names == null || !dir.equals(namesDir)) {
			names = VillagerNames.open(dir, LOG);
			namesDir = dir;
		}
		return names.nameFor(npc);
	}
}
