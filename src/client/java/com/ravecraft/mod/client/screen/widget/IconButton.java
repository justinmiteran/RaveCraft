package com.ravecraft.mod.client.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A vanilla button showing a 9×9 icon instead of a label, with the label as its tooltip and narration. A copper
 * button (play/pause) fills the vanilla frame with copper while active.
 */
public final class IconButton extends AbstractButton {
	private static final int COPPER_DULL = 0xFF8A7666;
	private final Runnable onPress;
	private Identifier icon;
	private Component label;
	private boolean copper;

	/**
	 * A square button of side {@code size} showing {@code icon}, {@code label} as its tooltip.
	 *
	 * @param x the button's left edge
	 * @param y the button's top
	 * @param size the button's side
	 * @param icon the icon's sprite
	 * @param label the tooltip and narration
	 * @param onPress runs on a click
	 */
	public IconButton(int x, int y, int size, Identifier icon, Component label, Runnable onPress) {
		super(x, y, size, size, label);
		this.onPress = onPress;
		this.icon = icon;
		this.label = label;
		setTooltip(Tooltip.create(label));
	}

	/**
	 * Draws the button in copper.
	 *
	 * @return this button
	 */
	public IconButton copper() {
		copper = true;
		return this;
	}

	/**
	 * Changes the icon and the label; the tooltip is rebuilt only when the label changes.
	 *
	 * @param icon the new icon's sprite
	 * @param label the new label
	 */
	public void show(Identifier icon, Component label) {
		this.icon = icon;
		if (!label.equals(this.label)) {
			this.label = label;
			setMessage(label);
			setTooltip(Tooltip.create(label));
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		extractDefaultSprite(graphics);
		int x = getX();
		int y = getY();
		int right = x + width - 1;
		int bottom = y + height - 1;
		if (copper && active) {
			// Inside the frame (kept, so hover still shows): copper lit from the top-left.
			graphics.fill(x + 1, y + 1, right, bottom, SpeakerSprites.COPPER);
			graphics.fill(x + 1, y + 1, right, y + 2, SpeakerSprites.COPPER_LIGHT);
			graphics.fill(x + 1, y + 1, x + 2, bottom, SpeakerSprites.COPPER_LIGHT);
			graphics.fill(x + 1, bottom - 1, right, bottom, SpeakerSprites.COPPER_DARK);
			graphics.fill(right - 1, y + 1, right, bottom, SpeakerSprites.COPPER_DARK);
		}
		if (copper && !active) {
			// Dull copper: the dark play/pause glyph, drawn for copper, would vanish on the disabled grey.
			graphics.fill(x + 1, y + 1, right, bottom, COPPER_DULL);
		}
		// Centred on the whole button, frame and bottom shadow included, as vanilla centres its icons.
		int iconX = x + (width - SpeakerSprites.ICON) / 2;
		int iconY = y + (height - SpeakerSprites.ICON) / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, SpeakerSprites.ICON, SpeakerSprites.ICON,
				active ? 1.0F : 0.4F);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
