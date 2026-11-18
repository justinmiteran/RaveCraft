package com.ravecraft.mod.core.acoustics.occlusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory {@link AcousticGrid} for tests: a background medium (air by default, the dimension's medium) everywhere
 * unless told otherwise. Like the world, a solid voxel has no medium of its own unless {@link #fluid} waterlogs it: the
 * background and {@link #fluidBox} only reach open voxels.
 */
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

	/** Fills the box from (x0, y0, z0) to (x1, y1, z1), inclusive, with full blocks of {@code material}. */
	TestGrid fill(int x0, int y0, int z0, int x1, int y1, int z1, AcousticMaterial material) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					solid(x, y, z, material);
				}
			}
		}
		return this;
	}

	/** Removes every solid in the box from (x0, y0, z0) to (x1, y1, z1), inclusive. */
	TestGrid clear(int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					solids.remove(key(x, y, z));
				}
			}
		}
		return this;
	}

	/** A one-block-thick shell of {@code material} around the open box from (x0, y0, z0) to (x1, y1, z1). */
	TestGrid shell(int x0, int y0, int z0, int x1, int y1, int z1, AcousticMaterial material) {
		return fill(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1, material).clear(x0, y0, z0, x1, y1, z1);
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
		AcousticMedium fluid = fluids.get(k);
		out.medium = fluid != null ? fluid : s != null ? null : medium(x, y, z);
	}

	@Override
	public AcousticMedium defaultMedium() {
		return background;
	}

	private AcousticMedium medium(int x, int y, int z) {
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
