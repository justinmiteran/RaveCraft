package com.ravecraft.mod.core.acoustics.debug.view;

import java.util.List;

/** A detour summed with the direct path: its points from source to listener, and its loss. */
public record Detour(List<Vec> points, double lossDb) {
	public Detour {
		points = List.copyOf(points);
	}
}
