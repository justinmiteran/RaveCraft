package com.ravecraft.mod.core.acoustics.occlusion;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One tick of occlusion tracing: the due streams, loudest first, share one voxel budget. A stream whose trace runs out
 * of budget waits for the next tick, which starts with it; the first stream of a tick that does not fit even the
 * whole budget settles for its straight line, which visits a bounded number of voxels.
 */
public final class OcclusionPass {
	/** What the pass does to a stream; implemented by the client scheduler. */
	public interface Ops<T> {
		/** Whether an occlusion was already published for {@code stream}. */
		boolean known(T stream);

		/** The full occlusion (direct path and detours) under {@code budget}; null when it ran out. */
		@Nullable Occlusion occlusion(T stream, TraceBudget budget);

		/** The straight-line occlusion alone, without a budget. */
		Occlusion direct(T stream);

		/** Hands {@code occlusion} to the audio thread. */
		void publish(T stream, Occlusion occlusion);

		/** {@code stream} goes first on the next tick. */
		void unfinished(T stream);
	}

	private OcclusionPass() {
	}

	public static <T> void run(List<T> due, TraceBudget budget, Ops<T> ops) {
		for (int i = 0; i < due.size(); i++) {
			T stream = due.get(i);
			boolean wholeBudget = budget.used() == 0;
			Occlusion result = ops.occlusion(stream, budget);
			if (result == null) {
				if (!wholeBudget) {
					for (int j = i; j < due.size(); j++) {
						ops.unfinished(due.get(j));
					}
					return;
				}
				result = ops.direct(stream);
			}
			ops.publish(stream, result);
		}
	}
}
