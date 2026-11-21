package com.ravecraft.mod.core.acoustics.debug.view;

import com.ravecraft.mod.core.acoustics.Vec;

import java.util.List;

/** A detour summed with the direct path: its points from source to listener, and its loss. */
public record Detour(List<Vec> points, double lossDb) {
	/** Copies {@code points}, so the view never changes. */
	public Detour {
		points = List.copyOf(points);
	}
}
