package com.ravecraft.mod.world.acoustics;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
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
	/** Blocks whose shape could not be read, each logged once. */
	private static final Set<Block> FAILED_BLOCKS = ConcurrentHashMap.newKeySet();

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

	/** What a block state is made of and how much of its voxel it fills along each axis. */
	private record Entry(@Nullable AcousticMaterial material, double fillX, double fillY, double fillZ) {
	}

	/** Where a block state's collision shape comes from. */
	@FunctionalInterface
	public interface ShapeSource {
		/**
		 * The collision shape of {@code state}.
		 *
		 * @param state the block state
		 * @return the shape
		 */
		VoxelShape shape(BlockState state);
	}

	/** The shape of the block alone, as if at the origin of an empty world. */
	private static final ShapeSource COLLISION = state -> state.getCollisionShape(EmptyBlockGetter.INSTANCE,
			BlockPos.ZERO);

	private final OcclusionSettings settings;
	private final ShapeSource shapes;
	private final Map<BlockState, Entry> cache = new ConcurrentHashMap<>();

	/**
	 * With the materials of {@code settings} and the blocks' collision shapes.
	 *
	 * @param settings the occlusion settings
	 */
	public BlockAcoustics(OcclusionSettings settings) {
		this(settings, COLLISION);
	}

	/**
	 * With the shapes taken from {@code shapes} instead of the collision shapes (for tests).
	 *
	 * @param settings the occlusion settings
	 * @param shapes gives each state's shape
	 */
	public BlockAcoustics(OcclusionSettings settings, ShapeSource shapes) {
		this.settings = settings;
		this.shapes = shapes;
	}

	/**
	 * The material of {@code state}, or null when sound goes through it.
	 *
	 * @param state the block state
	 * @return the material, or null
	 */
	public @Nullable AcousticMaterial material(BlockState state) {
		return entry(state).material();
	}

	/**
	 * How much of the voxel the block fills seen along {@code axis}, 0 to 1.
	 *
	 * @param state the block state
	 * @param axis the axis of the ray
	 * @return the fill, 0 to 1
	 */
	public double fill(BlockState state, Direction.Axis axis) {
		Entry e = entry(state);
		return switch (axis) {
			case X -> e.fillX();
			case Y -> e.fillY();
			case Z -> e.fillZ();
		};
	}

	/**
	 * Fills {@code out} with the state's material and fills, and its own medium as {@code media} resolves it
	 * ({@link MediumResolver#voxelMedium}): {@code dimension} must come from the same settings.
	 *
	 * @param state the block state
	 * @param media resolves the mediums
	 * @param dimension the dimension's medium
	 * @param out receives the voxel
	 */
	public void sample(BlockState state, MediumResolver media, AcousticMedium dimension, VoxelSample out) {
		Entry e = entry(state);
		out.material = e.material();
		out.fillX = e.fillX();
		out.fillY = e.fillY();
		out.fillZ = e.fillZ();
		out.medium = media.voxelMedium(state.getFluidState(), e.material() != null, dimension);
	}

	/** Forgets what it learned about each state, so that changed tags apply. */
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

	/** Works out the material and fills of {@code state}: its tag, else its footstep sound, else stone. */
	private Entry compute(BlockState state) {
		VoxelShape shape;
		try {
			shape = shapes.shape(state);
		} catch (RuntimeException e) {
			// Modded blocks may read a block entity or cast the getter: count it as a full cube of its material.
			if (FAILED_BLOCKS.add(state.getBlock())) {
				RaveCraft.LOGGER.warn("Cannot get the collision shape of {}; it counts as a full block for sound",
						state.getBlock(), e);
			}
			shape = Shapes.block();
		}
		AcousticMaterial material = material(state, shape);
		if (material == null) {
			return new Entry(null, 0, 0, 0);
		}
		List<AABB> boxes = shape.toAabbs();
		return new Entry(material, fill(boxes, Direction.Axis.X), fill(boxes, Direction.Axis.Y),
				fill(boxes, Direction.Axis.Z));
	}

	/** The material of {@code state}: its tag, else its footstep sound when it has a {@code shape}, else stone. */
	private @Nullable AcousticMaterial material(BlockState state, VoxelShape shape) {
		for (AcousticMaterial m : settings.materials()) {
			if (state.is(AcousticTags.material(m.name()))) {
				return m;
			}
		}
		if (shape.isEmpty()) {
			return null;
		}
		// Footstep sounds only count for blocks with a collision shape: grass and torches are not obstacles.
		String name = BY_SOUND.get(state.getSoundType());
		AcousticMaterial material = name == null ? null : named(name);
		return material != null ? material : named("stone");
	}

	private @Nullable AcousticMaterial named(String name) {
		for (AcousticMaterial m : settings.materials()) {
			if (m.name().equals(name)) {
				return m;
			}
		}
		return null;
	}

	/** How much of the voxel {@code boxes} fill seen along {@code axis}: 1 for a sealing panel. */
	private static double fill(List<AABB> boxes, Direction.Axis axis) {
		Direction.Axis b = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
		Direction.Axis c = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
		double volume = 0;
		for (AABB box : boxes) {
			boolean inside = startsInside(box) && endsInside(box);
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

	/** Whether {@code box} starts inside the voxel on every axis. */
	private static boolean startsInside(AABB box) {
		return box.minX >= -EPSILON && box.minY >= -EPSILON && box.minZ >= -EPSILON;
	}

	/** Whether {@code box} ends inside the voxel on every axis. */
	private static boolean endsInside(AABB box) {
		return box.maxX <= 1 + EPSILON && box.maxY <= 1 + EPSILON && box.maxZ <= 1 + EPSILON;
	}

	/** The volume of the part of the box inside the unit cube. */
	private static double clippedVolume(AABB box) {
		double x = Math.min(box.maxX, 1) - Math.max(box.minX, 0);
		double y = Math.min(box.maxY, 1) - Math.max(box.minY, 0);
		double z = Math.min(box.maxZ, 1) - Math.max(box.minZ, 0);
		return x <= 0 || y <= 0 || z <= 0 ? 0 : x * y * z;
	}
}
