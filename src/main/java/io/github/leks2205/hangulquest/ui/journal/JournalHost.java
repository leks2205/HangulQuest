package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.HangulQuestClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

/** What a journal tab may ask of the frame around it: shared widgets, hit areas and the book's state. */
public interface JournalHost {
	/** Index of the quest tab, which the info tab links to. */
	int QUEST_TAB = 4;

	/** A click on a spot; returns true when it was used, so the next area below gets no say. */
	interface PointClick {
		boolean click(double x, double y);
	}

	HangulQuestClient hq();

	Font font();

	/** True while translations are shown next to the Korean text. */
	boolean showDe();

	int mouseX();

	int mouseY();

	/** Counts changes made from inside the journal (reset, marking a word); caches key on it. */
	long epoch();

	void changed();

	/** The short message in the page footer. */
	void status(String text);

	void openTab(int index);

	void close();

	void openWelcome();

	/** Height of the page area a tab may use. */
	int contentHeight();

	void hit(int x, int y, int width, int height, Runnable action);

	void hit(int x, int y, int width, int height, PointClick action);

	void button(GuiGraphicsExtractor g, int x, int y, int width, int height, String label, boolean active,
				Runnable action);

	void checkRow(GuiGraphicsExtractor g, int x, int y, int width, int height, String label, boolean checked,
				  Runnable action);

	/** A labelled on/off row; returns the y of the next row. */
	int toggle(GuiGraphicsExtractor g, int x, int y, int width, String label, boolean value,
			   Consumer<Boolean> setter);
}
