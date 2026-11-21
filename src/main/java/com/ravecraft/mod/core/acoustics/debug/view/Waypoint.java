package com.ravecraft.mod.core.acoustics.debug.view;

import com.ravecraft.mod.core.acoustics.Vec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A point some detours bend at, and the loss of each detour through it, in detour order. */
public record Waypoint(Vec point, List<Double> lossesDb) {
	/** Copies {@code lossesDb}, so the view never changes. */
	public Waypoint {
		lossesDb = List.copyOf(lossesDb);
	}

	/** The waypoints of {@code detours} (their points but the ends), each once, in the order first met. */
	public static List<Waypoint> of(List<Detour> detours) {
		Map<Vec, List<Double>> losses = new LinkedHashMap<>();
		for (Detour detour : detours) {
			List<Vec> points = detour.points();
			for (int i = 1; i + 1 < points.size(); i++) {
				losses.computeIfAbsent(points.get(i), p -> new ArrayList<>()).add(detour.lossDb());
			}
		}
		List<Waypoint> waypoints = new ArrayList<>(losses.size());
		losses.forEach((point, list) -> waypoints.add(new Waypoint(point, list)));
		return waypoints;
	}
}
