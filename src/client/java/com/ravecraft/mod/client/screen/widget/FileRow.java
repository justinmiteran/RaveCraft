package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.library.SourceNode;
import org.jspecify.annotations.Nullable;

/** A track or playlist: its arrow column stays empty; a double click off its items runs its double-click action, if it has one. */
public final class FileRow extends LibraryRow {
	private @Nullable Runnable doubleClick;

	public FileRow(SourceNode node, int depth) {
		super(node, depth);
	}

	public FileRow onDoubleClick(Runnable action) {
		this.doubleClick = action;
		return this;
	}

	@Override
	public boolean click(boolean doubleClick) {
		if (!doubleClick || this.doubleClick == null) {
			return false;
		}
		this.doubleClick.run();
		return true;
	}
}
