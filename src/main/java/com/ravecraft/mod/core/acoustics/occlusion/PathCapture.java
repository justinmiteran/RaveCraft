package com.ravecraft.mod.core.acoustics.occlusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * What an {@link OcclusionTracer#occlusion} call found, for the debug view: each voxel of the direct path that lost
 * something (in the order walked, listener to source), the direct path's total, and the detours summed with it.
 * Reset by the tracer at the start of each call. Not thread-safe.
 */
public final class PathCapture {
	/** At most this many steps are kept; the rest are only counted. */
	public static final int MAX_STEPS = 64;

	/**
	 * A voxel of the direct path and what it lost: a material, an interface ("air→water") or a medium. The voxel that
	 * mutes the path (vacuum, an interface nothing crosses) is the last step, with an infinite loss.
	 */
	public record Step(int x, int y, int z, String name, double lossDb) {
	}

	/** A detour summed with the direct path: its points from source to listener, and its loss. */
	public record CapturedDetour(List<double[]> points, double lossDb) {
	}

	private final List<Step> steps = new ArrayList<>();
	private final List<CapturedDetour> detours = new ArrayList<>();
	private int truncated;
	private @Nullable PathLoss direct;

	/**
	 * The voxels of the direct path that lost something, listener to source, at most {@value #MAX_STEPS}.
	 *
	 * @return the lossy voxels, listener to source
	 */
	public List<Step> steps() {
		return Collections.unmodifiableList(steps);
	}

	/**
	 * How many steps were dropped past {@value #MAX_STEPS}.
	 *
	 * @return the number of steps dropped
	 */
	public int truncated() {
		return truncated;
	}

	/**
	 * The detours summed with the direct path, best first.
	 *
	 * @return the detours, best first
	 */
	public List<CapturedDetour> detours() {
		return Collections.unmodifiableList(detours);
	}

	/**
	 * The direct path's loss, or null when it was not traced (occlusion disabled, budget out).
	 *
	 * @return the direct path's loss, or null
	 */
	public @Nullable PathLoss direct() {
		return direct;
	}

	/** Forgets everything captured. */
	public void reset() {
		steps.clear();
		detours.clear();
		truncated = 0;
		direct = null;
	}

	void step(int x, int y, int z, String name, double lossDb) {
		if (steps.size() < MAX_STEPS) {
			steps.add(new Step(x, y, z, name, lossDb));
		} else {
			truncated++;
		}
	}

	void direct(PathLoss loss) {
		direct = loss;
	}

	void detour(double[][] points, double lossDb) {
		List<double[]> copy = new ArrayList<>(points.length);
		for (double[] p : points) {
			copy.add(p.clone());
		}
		detours.add(new CapturedDetour(Collections.unmodifiableList(copy), lossDb));
	}
}
