package com.ravecraft.mod.core.acoustics.occlusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An in-memory {@link AcousticGrid} for tests: a background medium (air by default) everywhere unless told otherwise. */
final class TestGrid implements AcousticGrid {
	private record Solid(AcousticMaterial material, double fillX, double fillY, double fillZ) {
	}

	private record Box(int x0, int y0, int z0, int x1, int y1, int z1, AcousticMedium medium) {
		boolean contains(int x, int y, int z) {
			return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
		}
	}

	private final Map<Long, Solid> solids = new HashMap<>();
	private final Map<Long, AcousticMedium> fluids = new HashMap<>();
	private final List<Box> boxes = new ArrayList<>();
	private final AcousticMedium background;

	TestGrid(AcousticMedium background) {
		this.background = background;
	}

	TestGrid() {
		this(OcclusionSettings.DEFAULT.air());
	}

	TestGrid solid(int x, int y, int z, AcousticMaterial material) {
		solids.put(key(x, y, z), new Solid(material, 1, 1, 1));
		return this;
	}

	TestGrid slab(int x, int y, int z, AcousticMaterial material) {
		solids.put(key(x, y, z), new Solid(material, 0.5, 0.5, 0.5));
		return this;
	}

	TestGrid fluid(int x, int y, int z, AcousticMedium medium) {
		fluids.put(key(x, y, z), medium);
		return this;
	}

	/** Fills the box from (x0, y0, z0) to (x1, y1, z1), inclusive, with {@code medium}. */
	TestGrid fluidBox(int x0, int y0, int z0, int x1, int y1, int z1, AcousticMedium medium) {
		boxes.add(new Box(x0, y0, z0, x1, y1, z1, medium));
		return this;
	}

	/** Fills the plane at {@code x} for y, z in [-8, 8]. */
	TestGrid wall(int x, AcousticMaterial material) {
		for (int y = -8; y <= 8; y++) {
			for (int z = -8; z <= 8; z++) {
				solid(x, y, z, material);
			}
		}
		return this;
	}

	@Override
	public void sample(int x, int y, int z, VoxelSample out) {
		long k = key(x, y, z);
		Solid s = solids.get(k);
		if (s != null) {
			out.material = s.material();
			out.fillX = s.fillX();
			out.fillY = s.fillY();
			out.fillZ = s.fillZ();
		} else {
			out.material = null;
			out.fillX = 0;
			out.fillY = 0;
			out.fillZ = 0;
		}
		out.medium = medium(x, y, z, k);
	}

	private AcousticMedium medium(int x, int y, int z, long k) {
		AcousticMedium m = fluids.get(k);
		if (m != null) {
			return m;
		}
		for (int i = boxes.size() - 1; i >= 0; i--) {
			if (boxes.get(i).contains(x, y, z)) {
				return boxes.get(i).medium();
			}
		}
		return background;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
	}
}
