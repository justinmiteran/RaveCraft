package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.library.SourceNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A row of a library or folder tree, a {@link FolderRow} or a {@link FileRow}: indented by depth, then an arrow
 * column, the icon and the name, then its items. Greyed rows are dimmed. Built with chained setters, for one frame.
 */
public abstract sealed class LibraryRow implements ListRow permits FolderRow, FileRow {
	private static final int INDENT = 8;
	private static final int ARROW_W = 7;

	private final int depth;
	private String label;
	private boolean greyed;
	private boolean arrowColumn = true;
	private boolean highlights = true;
	private final List<RowItem> items = new ArrayList<>();
	private Supplier<List<Component>> tooltip = List::of;

	/** The row of {@code node}, at {@code depth}, labelled with its name. */
	protected LibraryRow(SourceNode node, int depth) {
		this.depth = depth;
		this.label = node.name();
	}

	/**
	 * Shows {@code label} instead of the name.
	 *
	 * @param label the text to show
	 * @return this row
	 */
	public LibraryRow label(String label) {
		this.label = label;
		return this;
	}

	/**
	 * Dims the row when {@code greyed}.
	 *
	 * @param greyed true to dim the row
	 * @return this row
	 */
	public LibraryRow greyed(boolean greyed) {
		this.greyed = greyed;
		return this;
	}

	/**
	 * Leaves out the arrow column, for a list without folders.
	 *
	 * @return this row
	 */
	public LibraryRow noArrowColumn() {
		this.arrowColumn = false;
		return this;
	}

	/**
	 * A row that reacts to nothing: no highlight under the mouse.
	 *
	 * @return this row
	 */
	public LibraryRow inert() {
		this.highlights = false;
		return this;
	}

	/**
	 * Adds an item left of the ones already added; null adds nothing.
	 *
	 * @param item the item to add, or null
	 * @return this row
	 */
	public LibraryRow item(@Nullable RowItem item) {
		if (item != null) {
			items.add(item);
		}
		return this;
	}

	/**
	 * Shows {@code tooltip} when the row is hovered.
	 *
	 * @param tooltip the tooltip lines
	 * @return this row
	 */
	public LibraryRow tooltip(List<Component> tooltip) {
		return tooltip(() -> tooltip);
	}

	/**
	 * A tooltip built only when the row is hovered.
	 *
	 * @param tooltip builds the tooltip lines
	 * @return this row
	 */
	public LibraryRow tooltip(Supplier<List<Component>> tooltip) {
		this.tooltip = tooltip;
		return this;
	}

	/** Whether the row is dimmed. */
	protected final boolean greyed() {
		return greyed;
	}

	/** The icon left of the name. */
	protected abstract Identifier icon();

	/** Draws what goes in the arrow column at {@code x, y}; nothing by default, the column staying empty. */
	protected void drawArrow(GuiGraphicsExtractor graphics, int x, int y) {
	}

	@Override
	public List<RowItem> items() {
		return items;
	}

	@Override
	public boolean highlights() {
		return highlights;
	}

	@Override
	public List<Component> tooltip() {
		return tooltip.get();
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int left, int end, int y) {
		int x = indent(left, depth);
		drawArrow(graphics, x + 1, y + 3);
		int iconX = arrowColumn ? x + ARROW_W : x + 1;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(), iconX, y + 1, SpeakerSprites.ICON, SpeakerSprites.ICON,
				greyed ? 0.4F : 1.0F);
		int nameX = iconX + SpeakerSprites.ICON + 2;
		graphics.text(font, SpeakerSprites.fit(font, label, end - nameX), nameX, y + 2,
				greyed ? SpeakerSprites.ABSENT : SpeakerSprites.TEXT, !greyed);
	}

	private static int indent(int left, int depth) {
		return left + 1 + depth * INDENT;
	}

	/**
	 * The {@code Loading…} line shown under a folder while its children are listed.
	 *
	 * @param depth the indentation level
	 * @return the loading row
	 */
	public static ListRow loading(int depth) {
		return (graphics, font, left, end, y) -> {
			int x = indent(left, depth) + ARROW_W;
			String loading = Component.translatable(SpeakerSprites.KEY + "loading").getString();
			graphics.text(font, SpeakerSprites.fit(font, loading, end - x), x, y + 2, SpeakerSprites.MUTED, false);
		};
	}
}
