package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.library.SourceNode;

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
	protected boolean showsArrow() {
		return !greyed();
	}

	@Override
	protected boolean expanded() {
		return expanded;
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
