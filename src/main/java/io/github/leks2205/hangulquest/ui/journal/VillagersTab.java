package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.dialogue.DialogueHook;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.villager.Requests;
import io.github.leks2205.hangulquest.villager.VillagerProfile;
import io.github.leks2205.hangulquest.villager.VillagerProfiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Every villager the player has met (주민): name, personality, region, profession, friendship as hearts and the open
 * request, which can be tracked, untracked or abandoned here. Stable order: the order they were met.
 */
public final class VillagersTab extends JournalTab {
	private static final int ROW = 24;
	private static final int REQUEST_ROW = 16;

	private int scroll;

	public VillagersTab(JournalHost host) {
		super(host);
	}

	@Override
	public void onOpen() {
		scroll = 0;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double vertical) {
		scroll = Math.max(0, scroll - (int) Math.signum(vertical));
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		VillagerProfiles villagers = DialogueHook.villagers();
		Requests requests = host.hq().requests();
		List<VillagerRows.Row> rows = VillagerRows.of(villagers == null ? List.of() : villagers.all(), requests);
		if (rows.isEmpty()) {
			Draw.wrapped(g, host.font(), Component.literal(I18n.t("hangulquest.ui.villagers.empty")), x, y, width,
					Ink.INK_SOFT, 4);
			return;
		}
		Draw.korean(g, host.font(), "주민", x, y, Ink.ACCENT);
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.villagers.count", rows.size()), x + 30, y, Ink.INK_FAINT);
		int top = y + 14;
		int bottom = y + height - 4;
		scroll = Math.max(0, Math.min(scroll, rows.size() - 1));
		int yy = top;
		for (int i = scroll; i < rows.size(); i++) {
			VillagerRows.Row row = rows.get(i);
			int rowHeight = ROW + (row.hasRequest() ? REQUEST_ROW : 0);
			if (yy + rowHeight > bottom) {
				break;
			}
			drawRow(g, row, requests, x, yy, width);
			yy += rowHeight;
		}
	}

	private void drawRow(GuiGraphicsExtractor g, VillagerRows.Row row, Requests requests, int x, int y, int width) {
		VillagerProfile profile = row.profile();
		if (profile == null) {
			Draw.korean(g, host.font(), row.name(), x, y, Ink.INK);
			drawRequest(g, row, requests, row.request().villager, x, y, width);
			return;
		}
		String who = profile.name + " · " + profile.personality.ko() + " 주민";
		Draw.korean(g, host.font(), who, x, y, Ink.INK);
		String hearts = VillagerRows.hearts(profile.friendship);
		Draw.label(g, host.font(), hearts, x + width - host.font().width(hearts), y, Ink.BAD);

		StringBuilder about = new StringBuilder();
		if (host.showDe()) {
			about.append(I18n.t("hangulquest.ui.personality." + profile.personality.id())).append(" · ");
		}
		about.append(profile.region == null ? I18n.t("hangulquest.ui.villagers.region_unknown")
				: profile.region.ko() + (host.showDe() ? " (" + I18n.t("hangulquest.ui.region." + profile.region.id()) + ")" : ""));
		about.append(" · ").append(profession(profile));
		if (profile.baby) {
			about.append(" · ").append(I18n.t("hangulquest.ui.villagers.child"));
		}
		if (profile.banmal) {
			about.append(" · ").append(I18n.t("hangulquest.ui.villagers.banmal"));
		}
		Draw.truncated(g, host.font(), Component.literal(about.toString()), x + 8, y + 10, width - 8, Ink.INK_SOFT);
		if (row.hasRequest()) {
			drawRequest(g, row, requests, profile.uuid, x, y, width);
		}
	}

	private void drawRequest(GuiGraphicsExtractor g, VillagerRows.Row row, Requests requests, String villager, int x,
							 int y, int width) {
		int ry = y + ROW - 2;
		String title = row.requestDef() == null ? I18n.t("hangulquest.ui.villagers.request_gone")
				: I18n.t(row.requestDef().titleKey()) + (row.request().done ? " · " + I18n.t("hangulquest.ui.villagers.request_done") : "");
		int buttons = 150;
		Draw.truncated(g, host.font(), Component.literal(I18n.t("hangulquest.ui.villagers.request", title)), x + 8, ry + 3,
				width - buttons - 12, Ink.ACCENT);
		boolean tracked = row.request().tracked;
		host.button(g, x + width - buttons, ry, 72, 14, I18n.t(tracked ? "hangulquest.ui.villagers.untrack"
				: "hangulquest.ui.villagers.track"), tracked, () -> {
			if (requests != null && !requests.setTracked(villager, !tracked, System.currentTimeMillis())) {
				host.status(I18n.t("hangulquest.ui.villagers.log_full"));
			}
			host.changed();
		});
		host.button(g, x + width - 74, ry, 74, 14, I18n.t("hangulquest.ui.villagers.abandon"), false, () -> {
			if (requests != null && requests.abandon(villager)) {
				host.status(I18n.t("hangulquest.ui.villagers.abandoned"));
			}
			host.changed();
		});
	}

	/** The profession in the game's own words, or "no profession". */
	private static String profession(VillagerProfile profile) {
		String id = profile.profession;
		if (id == null || id.endsWith(":none") || id.endsWith(":nitwit")) {
			return I18n.t("hangulquest.ui.villagers.jobless");
		}
		String path = id.substring(id.indexOf(':') + 1);
		return Component.translatable("entity.minecraft.villager." + path).getString();
	}
}
