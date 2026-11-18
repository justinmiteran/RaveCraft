package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OcclusionPassTest {
	private static final Occlusion FULL = new Occlusion(10, 4);
	private static final Occlusion DIRECT = new Occlusion(15, 15);

	/** A fake stream: how many voxels its full trace needs, and whether it was already published. */
	private static final class Stream {
		final String name;
		final int voxels;
		boolean known;
		@Nullable Occlusion published;

		Stream(String name, int voxels, boolean known) {
			this.name = name;
			this.voxels = voxels;
			this.known = known;
		}
	}

	/** Records what the pass did. */
	private static final class Recorder implements OcclusionPass.Ops<Stream> {
		final List<String> events = new ArrayList<>();
		final Map<String, RuntimeException> throwing = new HashMap<>();

		@Override
		public boolean known(Stream stream) {
			return stream.known;
		}

		@Override
		public @Nullable Occlusion occlusion(Stream stream, TraceBudget budget) {
			events.add("trace " + stream.name);
			RuntimeException e = throwing.get(stream.name);
			if (e != null) {
				throw e;
			}
			for (int i = 0; i < stream.voxels; i++) {
				if (!budget.take()) {
					return null;
				}
			}
			return FULL;
		}

		@Override
		public Occlusion direct(Stream stream) {
			events.add("direct " + stream.name);
			return DIRECT;
		}

		@Override
		public void publish(Stream stream, Occlusion occlusion) {
			events.add("publish " + stream.name);
			stream.published = occlusion;
			stream.known = true;
		}

		@Override
		public void unfinished(Stream stream) {
			events.add("unfinished " + stream.name);
		}
	}

	@Test
	void fittingStreamsArePublished() {
		Stream a = new Stream("a", 3, false);
		Stream b = new Stream("b", 3, true);
		Recorder r = new Recorder();
		OcclusionPass.run(List.of(a, b), new TraceBudget(10), r);
		assertEquals(List.of("trace a", "publish a", "trace b", "publish b"), r.events);
		assertEquals(FULL, b.published);
	}

	@Test
	void firstStreamTooBigForTheWholeBudgetSettlesForTheStraightLine() {
		Stream a = new Stream("a", 20, false);
		Recorder r = new Recorder();
		OcclusionPass.run(List.of(a), new TraceBudget(10), r);
		assertEquals(List.of("trace a", "direct a", "publish a"), r.events);
		assertEquals(DIRECT, a.published);
	}
}
