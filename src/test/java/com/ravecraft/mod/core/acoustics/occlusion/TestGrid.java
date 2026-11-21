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
public final class TestGrid implements AcousticGrid {
	private record Solid(AcousticMaterial material, double fillX, double fillY, double fillZ) {
	}

	/** An inclusive box of voxels, from (x0, y0, z0) to (x1, y1, z1). */
	public record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
		boolean contains(int x, int y, int z) {
			return within(x, x0, x1) && within(y, y0, y1) && within(z, z0, z1);
		}

		/** Calls {@code action} on every voxel of the box. */
		void forEach(VoxelAction action) {
			int w = x1 - x0 + 1;
			int h = y1 - y0 + 1;
			int d = z1 - z0 + 1;
			if (w <= 0 || h <= 0 || d <= 0) {
				return;
			}
			for (int i = 0; i < w * h * d; i++) {
				action.at(x0 + i % w, y0 + i / w % h, z0 + i / (w * h));
			}
		}

		/** This box grown by one voxel on every side. */
		Box grown() {
			return new Box(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1);
		}
	}

	@FunctionalInterface
	private interface VoxelAction {
		void at(int x, int y, int z);
	}

	private record FluidBox(Box box, AcousticMedium medium) {
	}

	private final Map<Long, Solid> solids = new HashMap<>();
	private final Map<Long, AcousticMedium> fluids = new HashMap<>();
	private final List<FluidBox> boxes = new ArrayList<>();
	private final AcousticMedium background;

	public TestGrid(AcousticMedium background) {
		this.background = background;
	}

	public TestGrid() {
		this(OcclusionSettings.DEFAULT.air());
	}

	public TestGrid solid(int x, int y, int z, AcousticMaterial material) {
		solids.put(key(x, y, z), new Solid(material, 1, 1, 1));
		return this;
	}

	public TestGrid slab(int x, int y, int z, AcousticMaterial material) {
		solids.put(key(x, y, z), new Solid(material, 0.5, 0.5, 0.5));
		return this;
	}

	public TestGrid fluid(int x, int y, int z, AcousticMedium medium) {
		fluids.put(key(x, y, z), medium);
		return this;
	}

	/** The box from (x0, y0, z0) to (x1, y1, z1), inclusive. */
	public static Box box(int x0, int y0, int z0, int x1, int y1, int z1) {
		return new Box(x0, y0, z0, x1, y1, z1);
	}

	/** Fills {@code box} with {@code medium}. */
	public TestGrid fluidBox(Box box, AcousticMedium medium) {
		boxes.add(new FluidBox(box, medium));
		return this;
	}

	/** Fills the plane at {@code x} for y, z in [-8, 8]. */
	public TestGrid wall(int x, AcousticMaterial material) {
		return fill(box(x, -8, -8, x, 8, 8), material);
	}

	/** Fills {@code box} with full blocks of {@code material}. */
	public TestGrid fill(Box box, AcousticMaterial material) {
		box.forEach((x, y, z) -> solid(x, y, z, material));
		return this;
	}

	/** Removes every solid in {@code box}. */
	public TestGrid clear(Box box) {
		box.forEach((x, y, z) -> solids.remove(key(x, y, z)));
		return this;
	}

	/** A one-block-thick shell of {@code material} around the open {@code box}. */
	public TestGrid shell(Box box, AcousticMaterial material) {
		return fill(box.grown(), material).clear(box);
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
			if (boxes.get(i).box().contains(x, y, z)) {
				return boxes.get(i).medium();
			}
		}
		return background;
	}

	private static boolean within(int v, int min, int max) {
		return v >= min && v <= max;
	}

	private static long key(int x, int y, int z) {
		return bits(x) << 42 | bits(y) << 21 | bits(z);
	}

	private static long bits(int v) {
		return v & 0x1FFFFF;
	}
}
