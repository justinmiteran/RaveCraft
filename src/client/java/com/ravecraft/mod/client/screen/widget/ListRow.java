package com.ravecraft.mod.client.screen.widget;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One row of a {@link RowList}, built again each frame from the list's state: what it draws, the items at its right
 * end, and what a click does elsewhere on it.
 */
public interface ListRow {
	/** The items at the right end, from right to left. */
	default List<RowItem> items() {
		return List.of();
	}

	/** True when the row lights up under the mouse. */
	default boolean highlights() {
		return true;
	}

	/** Shown while the mouse is on the row but not on one of its items; empty for none. */
	default List<Component> tooltip() {
		return List.of();
	}

	/** Draws the row between {@code left} and {@code end}, where its items begin. */
	void draw(GuiGraphicsExtractor graphics, Font font, int left, int end, int y);

	/** A click on the row outside its items; true when it did something. */
	default boolean click(boolean doubleClick) {
		return false;
	}
}
