package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One page of the journal. The frame (cover, tab bar, footer) lives in the screen; a tab draws its page. */
public abstract class JournalTab {
	protected final JournalHost host;

	protected JournalTab(JournalHost host) {
		this.host = host;
	}

	/** The tab was chosen in the tab bar: back to the top, nothing selected. */
	public void onOpen() {
	}

	/** Draws the page into the box. */
	public abstract void render(GuiGraphicsExtractor g, int x, int y, int width, int height);

	public boolean keyPressed(int key) {
		return false;
	}

	/** First look at a click, before the buttons; return true to consume it. */
	public boolean mouseClicked(double x, double y, int button) {
		return false;
	}

	/** True when a press that hit no button still belongs to this tab (a drag started on empty space). */
	public boolean holdsPress() {
		return false;
	}

	public boolean mouseDragged(double dragX, double dragY, int button) {
		return false;
	}

	public void mouseReleased(int button) {
	}

	/** Returns true when the wheel was used. */
	public boolean mouseScrolled(double x, double y, double vertical) {
		return false;
	}

	/** Which language the cached strings of this tab were built for. */
	protected static int language() {
		return Lang.current().ordinal();
	}
}
