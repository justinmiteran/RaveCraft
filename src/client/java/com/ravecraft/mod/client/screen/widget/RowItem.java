package com.ravecraft.mod.client.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One thing at the right end of a list row: a small icon button, or a text such as a track count. A row lists its
 * items from right to left; {@link RowList} lays them out, draws them, shows their tooltip and hands them the clicks.
 */
public sealed interface RowItem {
	/**
	 * Width of the item, in pixels.
	 *
	 * @param font the font
	 * @return the item's width, in pixels
	 */
	int width(Font font);

	/**
	 * Draws the item at {@code x} in a row of height {@code h}; {@code over}: the mouse is on it.
	 *
	 * @param graphics where to draw
	 * @param font the font
	 * @param x the item's left edge
	 * @param y the row's top
	 * @param h the row's height
	 * @param over true when the mouse is on the item
	 */
	void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int h, boolean over);

	/**
	 * Shown while the mouse is on the item; null for none.
	 *
	 * @return the tooltip, or null
	 */
	@Nullable Component tooltip();

	/**
	 * A click on the item; true when it did something (the button sound plays).
	 *
	 * @return true when the click did something
	 */
	boolean click();

	/**
	 * Drawn only while the row is hovered; its place is kept either way.
	 *
	 * @return true when the item shows only on a hovered row
	 */
	default boolean hoverOnly() {
		return false;
	}

	/**
	 * A 9 px icon button running {@code action} when clicked.
	 *
	 * @param icon the icon's sprite
	 * @param tooltip the button's tooltip
	 * @param action runs on a click
	 * @return the button
	 */
	static Button button(Identifier icon, Component tooltip, Runnable action) {
		return new Button(icon, tooltip, true, false, action);
	}

	/**
	 * A muted text, such as a track count.
	 *
	 * @param text the text shown
	 * @return the text item
	 */
	static Text text(String text) {
		return new Text(text, SpeakerSprites.MUTED);
	}

	/** An icon button; a disabled one is dimmed, still shows its tooltip, and ignores clicks. */
	record Button(Identifier icon, Component tooltip, boolean enabled, boolean hoverOnly, Runnable action)
			implements RowItem {
		/**
		 * This button, disabled when {@code condition} is false, then showing {@code why} as its tooltip.
		 *
		 * @param condition false to disable the button
		 * @param why the tooltip shown while disabled
		 * @return this button
		 */
		public Button enabledIf(boolean condition, Component why) {
			return condition ? this : new Button(icon, why, false, hoverOnly, action);
		}

		/**
		 * This button, drawn only while its row is hovered.
		 *
		 * @return this button
		 */
		public Button onHover() {
			return new Button(icon, tooltip, enabled, true, action);
		}

		@Override
		public int width(Font font) {
			return SpeakerSprites.ICON + 2;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int h, boolean over) {
			if (over && enabled) {
				SpeakerSprites.cellHover(graphics, x, y, x + width(font), y + h);
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + 1, y + 1, SpeakerSprites.ICON,
					SpeakerSprites.ICON, enabled ? 1.0F : 0.4F);
		}

		@Override
		public boolean click() {
			if (!enabled) {
				return false;
			}
			action.run();
			return true;
		}
	}

	/** A text that does nothing when clicked. */
	record Text(String text, int color) implements RowItem {
		@Override
		public int width(Font font) {
			return font.width(text);
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int h, boolean over) {
			graphics.text(font, text, x, y + 2, color, false);
		}

		@Override
		public @Nullable Component tooltip() {
			return null;
		}

		@Override
		public boolean click() {
			return false;
		}
	}
}
