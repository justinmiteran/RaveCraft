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

	public IconButton(int x, int y, int size, Identifier icon, Component label, Runnable onPress) {
		this(x, y, size, size, icon, label, onPress);
	}

	public IconButton(int x, int y, int width, int height, Identifier icon, Component label, Runnable onPress) {
		super(x, y, width, height, label);
		this.onPress = onPress;
		this.icon = icon;
		this.label = label;
		setTooltip(Tooltip.create(label));
	}

	public IconButton copper() {
		copper = true;
		return this;
	}

	/** Changes the icon and the label; the tooltip is rebuilt only when the label changes. */
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
		// Centred on the face: inside the 1 px frame, above the vanilla sprite's 2 px bottom shadow.
		int iconX = x + 1 + (width - 2 - SpeakerSprites.ICON) / 2;
		int iconY = y + 1 + (height - 4 - SpeakerSprites.ICON) / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, SpeakerSprites.ICON, SpeakerSprites.ICON,
				active ? 1.0F : 0.4F);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
