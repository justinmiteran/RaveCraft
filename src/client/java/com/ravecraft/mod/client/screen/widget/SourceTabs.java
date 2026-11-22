package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.library.MusicSource;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The music sources as tabs stuck to the window's left edge, one under the other: the selected one joins the
 * window, an unavailable one is darker with a lock and the tooltip {@code Coming soon}. In 5a only the first
 * source (the local folders) is available, so the tabs only show the choice; clicking does nothing yet.
 */
public final class SourceTabs extends AbstractWidget {
	public static final int TAB_W = 26;
	public static final int TAB_H = 22;
	private static final int GAP = 2;
	/** How far the selected tab reaches into the window, over its outline and bevel. */
	private static final int JOIN = 3;
	private static final int WINDOW_GREY = 0xFFC6C6C6;
	private static final int SHADE = 0x40000000;

	private final List<MusicSource> sources;
	private final int selected;

	/**
	 * {@code windowLeft} is the window's left edge; the tabs sit just outside it, from {@code y} down.
	 *
	 * @param windowLeft the window's left edge
	 * @param y the first tab's top
	 * @param sources one tab per source
	 * @param selected the index of the selected source
	 */
	public SourceTabs(int windowLeft, int y, List<MusicSource> sources, int selected) {
		super(windowLeft + JOIN - TAB_W, y, TAB_W, sources.size() * (TAB_H + GAP) - GAP,
				Component.translatable(SpeakerSprites.KEY + "library"));
		this.sources = List.copyOf(sources);
		this.selected = selected;
	}

	private static Identifier icon(MusicSource source) {
		return switch (source.id()) {
			case "local" -> SpeakerSprites.PC;
			case "usb" -> SpeakerSprites.USB;
			default -> SpeakerSprites.STREAM;
		};
	}

	private static Component name(MusicSource source) {
		return Component.translatable(SpeakerSprites.KEY + "source." + source.id());
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int windowLeft = getX() + TAB_W - JOIN;
		for (int i = 0; i < sources.size(); i++) {
			MusicSource source = sources.get(i);
			int x = getX();
			int y = getY() + i * (TAB_H + GAP);
			boolean locked = !source.available();
			if (i == selected) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.WINDOW, x, y, TAB_W, TAB_H);
				// Erase the window's outline and bevel (and the tab's own right edge) where they meet.
				graphics.fill(windowLeft - 1, y + 2, windowLeft + JOIN, y + TAB_H - 2, WINDOW_GREY);
			} else {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.WINDOW, x + 1, y, TAB_W - JOIN, TAB_H);
				graphics.fill(x + 2, y + 1, windowLeft - 1, y + TAB_H - 1, SHADE);
			}
			int iconX = x + (TAB_W - JOIN - SpeakerSprites.ICON) / 2 + 1;
			int iconY = y + (TAB_H - SpeakerSprites.ICON) / 2;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(source), iconX, iconY, SpeakerSprites.ICON,
					SpeakerSprites.ICON, locked ? 0.4F : 1.0F);
			if (locked) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.LOCK, iconX + 5, iconY + 5,
						SpeakerSprites.ICON, SpeakerSprites.ICON);
			}
			if (isHovered() && mouseY >= y && mouseY < y + TAB_H) {
				List<Component> lines = locked
						? List.of(name(source), Component.translatable(SpeakerSprites.KEY + "soon").withStyle(ChatFormatting.GRAY))
						: List.of(name(source));
				graphics.setTooltipForNextFrame(Minecraft.getInstance().font, lines, Optional.empty(), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return false; // a single available source in 5a: nothing to switch to yet
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
