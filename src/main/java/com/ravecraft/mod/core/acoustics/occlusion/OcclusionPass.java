package com.ravecraft.mod.core.acoustics.occlusion;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One tick of occlusion tracing: the due streams, loudest first, share one voxel budget. A stream whose trace runs out
 * of budget waits for the next tick, which starts with it; one that was never published meanwhile gets its straight
 * line at once (the audio thread holds it silent until then), as does the first stream of a tick that does not fit
 * even the whole budget: a straight line visits a bounded number of voxels. A trace that throws (a modded block) is
 * reported and skipped: the stream keeps its last occlusion, or gets none if it never had one, and the pass never
 * throws.
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

		/** A trace of {@code stream} threw {@code e} (to log); the pass goes on with the next stream. */
		void failed(T stream, RuntimeException e);
	}

	private OcclusionPass() {
	}

	public static <T> void run(List<T> due, TraceBudget budget, Ops<T> ops) {
		boolean outOfBudget = false;
		for (T stream : due) {
			try {
				if (!outOfBudget) {
					boolean wholeBudget = budget.used() == 0;
					Occlusion result = ops.occlusion(stream, budget);
					if (result != null) {
						ops.publish(stream, result);
						continue;
					}
					if (wholeBudget) {
						ops.publish(stream, ops.direct(stream));
						continue;
					}
					outOfBudget = true;
				}
				ops.unfinished(stream);
				if (!ops.known(stream)) {
					ops.publish(stream, ops.direct(stream));
				}
			} catch (RuntimeException e) {
				ops.failed(stream, e);
				if (!ops.known(stream)) {
					ops.publish(stream, Occlusion.NONE);
				}
			}
		}
	}
}
