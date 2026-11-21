package com.ravecraft.mod.core.acoustics.debug.view;

import java.util.ArrayList;
import java.util.List;

import com.ravecraft.mod.core.acoustics.occlusion.Occlusion;
import com.ravecraft.mod.core.acoustics.occlusion.PathCapture;
import com.ravecraft.mod.core.acoustics.occlusion.PathLoss;

/**
 * What the sound view shows of one source: the occlusion heard, the blocks the direct path loses in (source to
 * listener, with the loss accumulated from the source), how many were left out, and the detours summed with it.
 * {@code mute} is the combined occlusion; {@code directMute} says the straight path alone is mute (a detour may still
 * carry the sound), its muting block being then the crossing with an infinite loss.
 */
public record PathView(int sessionId, Vec source, Vec listener, double lossDb, double highLossDb, boolean mute,
		boolean directMute, List<Crossing> crossings, int truncated, List<Detour> detours) {
	/** Copies the lists, so the view never changes. */
	public PathView {
		crossings = List.copyOf(crossings);
		detours = List.copyOf(detours);
	}

	/** The view of {@code capture}, filled by the occlusion trace that gave {@code occlusion}. */
	public static PathView of(int sessionId, Vec source, Vec listener, PathCapture capture, Occlusion occlusion) {
		List<PathCapture.Step> steps = capture.steps();
		List<Crossing> crossings = new ArrayList<>(steps.size());
		double cumulative = 0;
		for (int i = steps.size() - 1; i >= 0; i--) {
			PathCapture.Step step = steps.get(i);
			cumulative += step.lossDb();
			crossings.add(new Crossing(step.x(), step.y(), step.z(), step.name(), step.lossDb(), cumulative));
		}
		List<Detour> detours = new ArrayList<>(capture.detours().size());
		for (PathCapture.CapturedDetour captured : capture.detours()) {
			List<Vec> points = new ArrayList<>(captured.points().size());
			for (double[] p : captured.points()) {
				points.add(new Vec(p[0], p[1], p[2]));
			}
			detours.add(new Detour(points, captured.lossDb()));
		}
		PathLoss direct = capture.direct();
		return new PathView(sessionId, source, listener, occlusion.lossDb(), occlusion.highLossDb(), occlusion.mute(),
				direct != null && direct.mute(), crossings, capture.truncated(), detours);
	}
}
