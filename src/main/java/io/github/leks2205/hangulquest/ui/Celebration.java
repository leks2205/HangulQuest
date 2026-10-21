package io.github.leks2205.hangulquest.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Completion feedback in three sizes: a small flash for a quest step, a medium
 * one for a sidequest, and a large animated card for finishing a whole trail.
 * Particles are spawned on the client only; the server never sees them.
 */
public final class Celebration {
	public enum Kind {
		STEP(2400L, 10),
		SIDE(3200L, 16),
		TRAIL(6500L, 40);

		final long duration;
		final int particles;

		Kind(long duration, int particles) {
			this.duration = duration;
			this.particles = particles;
		}
	}

	private Kind kind;
	private String titleKo = "";
	private String lineKo = "";
	private String line2Ko = "";
	private String lineDe = "";
	private String badge = "";
	private String iconItem = "";
	private long start;
	private long nextBurst;

	// The texts and their wrapped lines only change when a celebration is triggered or the card width does.
	private Component title;
	private Component line;
	private Component line2;
	private Component german;
	private Component badgeText;
	private Font laidOutFont;
	private int laidOutWidth = -1;
	private List<FormattedCharSequence> titleLines = List.of();
	private List<FormattedCharSequence> lineLines = List.of();
	private List<FormattedCharSequence> line2Lines = List.of();
	private int titleHeight;
	private int lineHeight;
	private int line2Height;

	public void trigger(Kind kind, String titleKo, String lineKo, String line2Ko, String lineDe, String badge,
						String iconItem) {
		this.kind = kind;
		this.titleKo = titleKo == null ? "" : titleKo;
		this.lineKo = lineKo == null ? "" : lineKo;
		this.line2Ko = line2Ko == null ? "" : line2Ko;
		this.lineDe = lineDe == null ? "" : lineDe;
		this.badge = badge == null ? "" : badge;
		this.iconItem = iconItem == null ? "" : iconItem;
		this.start = System.currentTimeMillis();
		this.nextBurst = this.start;
		this.title = KoText.ko(this.titleKo);
		this.line = this.lineKo.isBlank() ? null : KoText.ko(this.lineKo);
		this.line2 = this.line2Ko.isBlank() ? null : KoText.ko(this.line2Ko);
		this.german = this.lineDe.isBlank() ? null : Component.literal(this.lineDe);
		this.badgeText = this.badge.isBlank() ? null : Component.literal(this.badge);
		this.laidOutWidth = -1;
	}

	private void layout(Font font, int textWidth) {
		if (font == laidOutFont && textWidth == laidOutWidth) {
			return;
		}
		laidOutFont = font;
		laidOutWidth = textWidth;
		titleLines = Draw.lines(font, title, textWidth);
		lineLines = line == null ? List.of() : Draw.lines(font, line, textWidth);
		line2Lines = line2 == null ? List.of() : Draw.lines(font, line2, textWidth);
		titleHeight = Draw.linesHeight(font, titleLines, 2);
		lineHeight = Draw.linesHeight(font, lineLines, 3);
		line2Height = Draw.linesHeight(font, line2Lines, 3);
	}

	/** Extra bursts for the big celebration while it is on screen. */
	public void tickParticles(Minecraft mc, long now) {
		if (kind == Kind.TRAIL && now < start + 2200L && now >= nextBurst) {
			nextBurst = now + 220L;
			particles(mc, Kind.TRAIL, 14);
		}
	}

	public void clear() {
		kind = null;
	}

	public boolean active(long now) {
		return kind != null && now - start < kind.duration;
	}

	public Kind kind() {
		return kind;
	}

	public void draw(GuiGraphicsExtractor graphics, Font font, int guiWidth, int guiHeight, long now) {
		if (kind == null) {
			return;
		}
		long elapsed = now - start;
		if (elapsed < 0 || elapsed >= kind.duration) {
			kind = null;
			return;
		}
		float fadeIn = Math.min(1.0F, elapsed / 160.0F);
		float fadeOut = Math.min(1.0F, (kind.duration - elapsed) / 450.0F);
		float alpha = Math.max(0.0F, Math.min(1.0F, Math.min(fadeIn, fadeOut)));
		int slide = (int) ((1.0F - fadeIn) * 6.0F);

		int width = Math.min(kind == Kind.TRAIL ? 380 : 320, guiWidth - 16);
		int textX = iconItem == null || iconItem.isBlank() ? 10 : 32;
		int textWidth = width - textX - 10;
		layout(font, textWidth);
		int germanHeight = german == null ? 0 : font.lineHeight + 1;
		int height = 12 + titleHeight + (line == null ? 0 : 6 + lineHeight)
				+ (line2 == null ? 0 : 5 + line2Height)
				+ (german == null ? 0 : 5 + germanHeight) + 10;

		int x = (guiWidth - width) / 2;
		int y = (guiHeight - height) / 2 - (kind == Kind.TRAIL ? 0 : 12) + slide;

		int border = withAlpha(kind == Kind.TRAIL ? pulse(now) : Draw.GOLD, alpha);
		int background = withAlpha(0xE6151518, Math.min(1.0F, alpha + 0.15F));
		graphics.fill(x, y, x + width, y + height, background);
		frame(graphics, x, y, width, height, border);
		if (kind == Kind.TRAIL) {
			frame(graphics, x + 3, y + 3, width - 6, height - 6, withAlpha(Draw.GOLD, alpha * 0.55F));
		}

		int cursor = y + 8;
		if (!iconItem.isBlank()) {
			Draw.itemIcon(graphics, iconItem, x + 10, y + 9);
		}
		Draw.drawLines(graphics, font, titleLines, x + textX, cursor, withAlpha(Draw.GOLD, alpha), 2);
		cursor += titleHeight + 6;
		if (line != null) {
			Draw.drawLines(graphics, font, lineLines, x + textX, cursor, withAlpha(Draw.WHITE, alpha), 3);
			cursor += lineHeight + 5;
		}
		if (line2 != null) {
			Draw.drawLines(graphics, font, line2Lines, x + textX, cursor, withAlpha(Draw.WHITE, alpha), 3);
			cursor += line2Height + 5;
		}
		if (german != null) {
			Draw.wrapped(graphics, font, german, x + textX, cursor, textWidth,
					withAlpha(Draw.GRAY, alpha), 2);
			cursor += germanHeight + 2;
		}
		if (badgeText != null && kind == Kind.TRAIL) {
			Draw.truncated(graphics, font, badgeText, x + 10, y + height - 11,
					width - 20, withAlpha(Draw.GOLD, alpha));
		}
	}

	private static int pulse(long now) {
		double wave = (Math.sin(now / 260.0) + 1.0) / 2.0;
		int r = (int) (255);
		int g = (int) (200 + 45 * wave);
		int b = (int) (90 + 80 * wave);
		return (r << 16) | (g << 8) | b;
	}

	private static int withAlpha(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y, x + 1, y + height, color);
		graphics.fill(x + width - 1, y, x + width, y + height, color);
	}

	/** Client-only particle burst around the player; nothing is sent to the server. */
	public static void particles(Minecraft mc, Kind kind) {
		particles(mc, kind, kind.particles);
	}

	public static void particles(Minecraft mc, Kind kind, int count) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		Vec3 base = mc.player.position();
		Random random = new Random();
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double radius = 0.6 + random.nextDouble() * (kind == Kind.TRAIL ? 2.6 : 1.0);
			double x = base.x + Math.cos(angle) * radius;
			double z = base.z + Math.sin(angle) * radius;
			double y = base.y + 0.9 + random.nextDouble() * 1.1;
			switch (kind) {
				case STEP -> mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0.0, 0.03, 0.0);
				case SIDE -> mc.level.addParticle(ParticleTypes.END_ROD, x, y, z,
						0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
				case TRAIL -> {
					if (i % 3 == 0) {
						mc.level.addParticle(ParticleTypes.FIREWORK, x, y + 0.4, z,
								(random.nextDouble() - 0.5) * 0.05, 0.06, (random.nextDouble() - 0.5) * 0.05);
					} else if (i % 3 == 1) {
						mc.level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.05, 0.0);
					} else {
						mc.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y, z,
								(random.nextDouble() - 0.5) * 0.1, 0.08, (random.nextDouble() - 0.5) * 0.1);
					}
				}
			}
		}
	}
}
