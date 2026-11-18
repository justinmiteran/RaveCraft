package com.ravecraft.mod.world.acoustics;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * What a block state sounds like: its acoustic material (tag first, then footstep sound, then stone for any other
 * solid) and how much of the voxel it fills seen along each axis. Cached per state; safe to use from any thread.
 */
public final class BlockAcoustics {
	/** A thin panel (2/16 to 4/16: glass pane, door, trapdoor) that spans the voxel seals it entirely. */
	private static final double SEALING_THICKNESS = 2.0 / 16.0;
	private static final double MAX_PANEL_THICKNESS = 4.0 / 16.0;
	private static final double EPSILON = 1e-6;

	private static final Map<SoundType, String> BY_SOUND = new HashMap<>();

	static {
		put("wood", SoundType.WOOD, SoundType.NETHER_WOOD, SoundType.BAMBOO_WOOD, SoundType.CHERRY_WOOD,
				SoundType.LADDER, SoundType.SCAFFOLDING, SoundType.BAMBOO, SoundType.STEM,
				SoundType.CHISELED_BOOKSHELF, SoundType.SHELF, SoundType.HANGING_SIGN,
				SoundType.NETHER_WOOD_HANGING_SIGN, SoundType.BAMBOO_WOOD_HANGING_SIGN,
				SoundType.CHERRY_WOOD_HANGING_SIGN, SoundType.CREAKING_HEART, SoundType.MANGROVE_ROOTS);
		put("glass", SoundType.GLASS, SoundType.AMETHYST, SoundType.AMETHYST_CLUSTER, SoundType.SMALL_AMETHYST_BUD,
				SoundType.MEDIUM_AMETHYST_BUD, SoundType.LARGE_AMETHYST_BUD);
		put("metal", SoundType.METAL, SoundType.IRON, SoundType.ANVIL, SoundType.LANTERN, SoundType.CHAIN,
				SoundType.NETHERITE_BLOCK, SoundType.COPPER, SoundType.COPPER_BULB, SoundType.COPPER_GRATE,
				SoundType.COPPER_GOLEM_STATUE, SoundType.HEAVY_CORE);
		put("earth", SoundType.GRAVEL, SoundType.SAND, SoundType.SUSPICIOUS_SAND, SoundType.SUSPICIOUS_GRAVEL,
				SoundType.GRASS, SoundType.ROOTED_DIRT, SoundType.MUD, SoundType.MUDDY_MANGROVE_ROOTS,
				SoundType.SOUL_SAND, SoundType.SOUL_SOIL, SoundType.MOSS, SoundType.NYLIUM, SoundType.SLIME_BLOCK,
				SoundType.HONEY_BLOCK, SoundType.RESIN, SoundType.SCULK);
		put("snow", SoundType.SNOW, SoundType.POWDER_SNOW);
		put("wool", SoundType.WOOL, SoundType.SPONGE, SoundType.WET_SPONGE, SoundType.STRAW_BED);
		put("foliage", SoundType.AZALEA_LEAVES, SoundType.CHERRY_LEAVES, SoundType.POPLAR_LEAVES, SoundType.AZALEA,
				SoundType.FLOWERING_AZALEA, SoundType.VINE, SoundType.CAVE_VINES, SoundType.WART_BLOCK,
				SoundType.BIG_DRIPLEAF);
	}

	private static void put(String material, SoundType... sounds) {
		for (SoundType s : sounds) {
			BY_SOUND.put(s, material);
		}
	}

	private record Entry(@Nullable AcousticMaterial material, double fillX, double fillY, double fillZ) {
	}

	private final OcclusionSettings settings;
	private final Map<BlockState, Entry> cache = new ConcurrentHashMap<>();

	public BlockAcoustics(OcclusionSettings settings) {
		this.settings = settings;
	}

	public @Nullable AcousticMaterial material(BlockState state) {
		return entry(state).material();
	}

	/** How much of the voxel the block fills seen along {@code axis}, 0 to 1. */
	public double fill(BlockState state, Direction.Axis axis) {
		Entry e = entry(state);
		return switch (axis) {
			case X -> e.fillX();
			case Y -> e.fillY();
			case Z -> e.fillZ();
		};
	}

	/** Fills {@code out} with the state's material and fills, and the given medium. */
	public void sample(BlockState state, AcousticMedium medium, VoxelSample out) {
		Entry e = entry(state);
		out.material = e.material();
		out.fillX = e.fillX();
		out.fillY = e.fillY();
		out.fillZ = e.fillZ();
		out.medium = medium;
	}

	public void clearCache() {
		cache.clear();
	}

	private Entry entry(BlockState state) {
		Entry e = cache.get(state);
		if (e == null) {
			e = compute(state);
			cache.put(state, e);
		}
		return e;
	}

	private Entry compute(BlockState state) {
		VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		AcousticMaterial material = null;
		for (AcousticMaterial m : settings.materials()) {
			if (state.is(AcousticTags.material(m.name()))) {
				material = m;
				break;
			}
		}
		if (material == null && !shape.isEmpty()) {
			// Footstep sounds only count for blocks with a collision shape: grass and torches are not obstacles.
			String name = BY_SOUND.get(state.getSoundType());
			material = name == null ? null : named(name);
			if (material == null) {
				material = named("stone");
			}
		}
		if (material == null) {
			return new Entry(null, 0, 0, 0);
		}
		List<AABB> boxes = shape.toAabbs();
		return new Entry(material, fill(boxes, Direction.Axis.X), fill(boxes, Direction.Axis.Y),
				fill(boxes, Direction.Axis.Z));
	}

	private @Nullable AcousticMaterial named(String name) {
		for (AcousticMaterial m : settings.materials()) {
			if (m.name().equals(name)) {
				return m;
			}
		}
		return null;
	}

	private static double fill(List<AABB> boxes, Direction.Axis axis) {
		Direction.Axis b = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
		Direction.Axis c = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
		double volume = 0;
		for (AABB box : boxes) {
			boolean inside = box.minX >= -EPSILON && box.minY >= -EPSILON && box.minZ >= -EPSILON
					&& box.maxX <= 1 + EPSILON && box.maxY <= 1 + EPSILON && box.maxZ <= 1 + EPSILON;
			boolean spans = box.min(b) <= EPSILON && box.max(b) >= 1 - EPSILON
					&& box.min(c) <= EPSILON && box.max(c) >= 1 - EPSILON;
			double thickness = box.max(axis) - box.min(axis);
			// Only a panel that sits entirely inside the voxel seals it: a fence (1.5 high) is not a wall.
			if (inside && spans && thickness >= SEALING_THICKNESS - EPSILON
					&& thickness <= MAX_PANEL_THICKNESS + EPSILON) {
				return 1;
			}
			volume += clippedVolume(box);
		}
		return Math.min(volume, 1);
	}

	/** The volume of the part of the box inside the unit cube. */
	private static double clippedVolume(AABB box) {
		double x = Math.min(box.maxX, 1) - Math.max(box.minX, 0);
		double y = Math.min(box.maxY, 1) - Math.max(box.minY, 0);
		double z = Math.min(box.maxZ, 1) - Math.max(box.minZ, 0);
		return x <= 0 || y <= 0 || z <= 0 ? 0 : x * y * z;
	}
}
