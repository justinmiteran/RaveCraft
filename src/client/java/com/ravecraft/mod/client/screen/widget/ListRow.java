package com.ravecraft.mod.client.screen.widget;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * One row of a {@link RowList}, built again each frame from the list's state: what it draws, the items at its right
 * end, and what a click does elsewhere on it. The row lays out its own items: the list only tells it where it is and
 * where the mouse is, through {@link #render}, {@link #clickAt} and {@link #tooltipAt}.
 */
public interface ListRow {
	/** Space between two items, and between the items and the rest of the row. */
	int ITEM_GAP = 2;

	/** Where a row is drawn: from {@code left} to {@code right}, {@code h} high from {@code y}. */
	record Box(int left, int right, int y, int h) {
	}

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

	/** True when the row can be dragged elsewhere in a list that reorders. */
	default boolean draggable() {
		return false;
	}

	/** True when {@code mouseX} is on one of the items laid from {@code right}. */
	default boolean onItem(Font font, double mouseX, int right) {
		return itemAt(font, mouseX, right) != null;
	}

	/** A click on the row outside its items; true when it did something. */
	default boolean click(boolean doubleClick) {
		return false;
	}

	/**
	 * Draws the items from the right of {@code box} leftwards, then the rest of the row up to them. {@code hovered}: the mouse is
	 * on this row, at {@code mouseX}; items drawn only on hover wait for it.
	 */
	default void render(GuiGraphicsExtractor graphics, Font font, Box box, boolean hovered, int mouseX) {
		List<RowItem> items = items();
		int[] xs = itemXs(font, box.right());
		for (int i = 0; i < items.size(); i++) {
			RowItem item = items.get(i);
			if (hovered || !item.hoverOnly()) {
				item.draw(graphics, font, xs[i], box.y(), box.h(), hovered && over(font, item, xs[i], mouseX));
			}
		}
		draw(graphics, font, box.left(), xs[items.size()], box.y());
	}

	/** A click at {@code mouseX}: the item under it takes the click, or else the row; true when it did something. */
	default boolean clickAt(Font font, double mouseX, int right, boolean doubleClick) {
		RowItem item = itemAt(font, mouseX, right);
		return item != null ? item.click() : click(doubleClick);
	}

	/** The tooltip for the mouse at {@code mouseX}: the item's under it, or else the row's; empty for none. */
	default List<Component> tooltipAt(Font font, double mouseX, int right) {
		RowItem item = itemAt(font, mouseX, right);
		if (item == null) {
			return tooltip();
		}
		Component tip = item.tooltip();
		return tip != null ? List.of(tip) : List.of();
	}

	private @Nullable RowItem itemAt(Font font, double mouseX, int right) {
		List<RowItem> items = items();
		int[] xs = itemXs(font, right);
		for (int i = 0; i < items.size(); i++) {
			if (over(font, items.get(i), xs[i], mouseX)) {
				return items.get(i);
			}
		}
		return null;
	}

	/**
	 * The one layout of the items: the left edge of each, laid from {@code right} leftwards, then, last, where the rest
	 * of the row ends.
	 */
	private int[] itemXs(Font font, int right) {
		List<RowItem> items = items();
		int[] xs = new int[items.size() + 1];
		int x = right;
		for (int i = 0; i < items.size(); i++) {
			x -= items.get(i).width(font);
			xs[i] = x;
			x -= ITEM_GAP;
		}
		xs[items.size()] = x;
		return xs;
	}

	private static boolean over(Font font, RowItem item, int x, double mouseX) {
		return mouseX >= x && mouseX < x + item.width(font);
	}
}
