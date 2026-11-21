package com.ravecraft.mod.core.acoustics.debug.view;

import com.ravecraft.mod.core.acoustics.Vec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoFinder;
import com.ravecraft.mod.core.acoustics.reverb.RayDirections;
import com.ravecraft.mod.core.acoustics.reverb.RayHit;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import com.ravecraft.mod.core.acoustics.reverb.RoomShape;

class RoomViewTest {
	private static final ReverbSettings R = ReverbSettings.DEFAULT;
	private static final Vec ORIGIN = new Vec(10.5, 64.5, -3.5);

	private static List<RayHit> halfOpen() {
		List<RayHit> hits = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			hits.add(i % 2 == 0 ? new RayHit(true, 64, 1) : new RayHit(false, 5, 0.3));
		}
		return hits;
	}

	@Test
	void endsAndOpenRays() {
		RoomView view = RoomView.of(ORIGIN, halfOpen(), R);
		double[][] dirs = RayDirections.sphere(16);
		assertEquals(16, view.rays().size());
		for (int i = 0; i < 16; i++) {
			Ray ray = view.rays().get(i);
			double length = i % 2 == 0 ? 64 : 5;
			assertEquals(i % 2 == 0, ray.open());
			assertEquals(ORIGIN.x() + dirs[i][0] * length, ray.end().x(), 1e-9);
			assertEquals(ORIGIN.y() + dirs[i][1] * length, ray.end().y(), 1e-9);
			assertEquals(ORIGIN.z() + dirs[i][2] * length, ray.end().z(), 1e-9);
			assertEquals(i % 2 == 0 ? 1 : 0.3, ray.absorption(), 1e-9);
		}
		assertNull(view.fluid());
	}

	@Test
	void echoMarks() {
		List<RayHit> hits = new ArrayList<>();
		for (int i = 0; i < 32; i++) {
			hits.add(new RayHit(true, 64, 1));
		}
		List<Integer> walls = List.of(2, 9, 14, 20, 27, 30);
		for (int i = 0; i < walls.size(); i++) {
			hits.set(walls.get(i), new RayHit(false, 24.5 + i / 5.0, 0.02));
		}
		RoomView view = RoomView.of(ORIGIN, hits, R);
		for (int i = 0; i < 32; i++) {
			assertEquals(walls.contains(i), view.rays().get(i).echo(), "ray " + i);
		}
		assertEquals(EchoFinder.find(hits, R.echo()), view.echo());
		assertFalse(view.echo().equals(Echo.NONE));
	}

	@Test
	void shapeAndRt60() {
		List<RayHit> hits = halfOpen();
		RoomView view = RoomView.of(ORIGIN, hits, R);
		RoomShape shape = RoomShape.of(hits, R.rayLength());
		assertEquals(shape, view.shape());
		assertEquals(shape.rt60(R.maxDecaySeconds()), view.rt60());
	}

	@Test
	void fluid() {
		RoomView view = RoomView.fluid(ORIGIN, "water");
		assertTrue(view.rays().isEmpty());
		assertEquals("water", view.fluid());
		assertNull(view.shape());
		assertEquals(Echo.NONE, view.echo());
		assertEquals(ORIGIN, view.origin());
	}
}
