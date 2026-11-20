package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.library.SourceNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** A folder: an arrow, and a click anywhere off its items opens or folds it, unless it is greyed. */
public final class FolderRow extends LibraryRow {
	private final boolean expanded;
	private final Runnable toggle;

	public FolderRow(SourceNode node, int depth, boolean expanded, Runnable toggle) {
		super(node, depth);
		this.expanded = expanded;
		this.toggle = toggle;
	}

	@Override
	protected Identifier icon() {
		return SpeakerSprites.FOLDER;
	}

	/** The open or folded arrow; a greyed folder can't be opened, so it shows none. */
	@Override
	protected void drawArrow(GuiGraphicsExtractor graphics, int x, int y) {
		if (!greyed()) {
			SpeakerSprites.drawArrow(graphics, x, y, expanded);
		}
	}

	@Override
	public boolean click(boolean doubleClick) {
		if (greyed()) {
			return false;
		}
		toggle.run();
		return true;
	}
}
