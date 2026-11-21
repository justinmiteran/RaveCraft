package com.ravecraft.mod.core.acoustics.occlusion;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Walks a straight segment voxel by voxel (Amanatides–Woo) and adds up what it loses, as {@link OcclusionTracer}
 * describes. Keeps what the last trace learned about its ends. Not thread-safe.
 */
final class SegmentTracer {
	/** Voxels crossed over less than this many blocks (a ray grazing an edge or a corner) add nothing. */
	static final double MIN_LENGTH = 1e-9;

	private final AcousticGrid grid;
	private final OcclusionSettings settings;
	private final AcousticMedium air;
	private final VoxelSample sample = new VoxelSample();
	private final VoxelWalk walk = new VoxelWalk();
	/**
	 * Set by each trace: the medium the segment starts in and the one it arrives in (a voxel without a medium of its
	 * own takes the one around it along the segment); null when the trace stopped before knowing it.
	 */
	private @Nullable AcousticMedium startMedium;
	private @Nullable AcousticMedium endMedium;
	/** Set by each trace: true when it was muted by the medium at one of its ends. */
	private boolean endMuted;
	/** Where the trace records the voxels it loses in, if anywhere. */
	private @Nullable PathCapture recording;

	// The trace under way.
	private double length;
	private double loss;
	private double high;
	/**
	 * The medium the walk is in. A voxel without a medium of its own (a solid without fluid) stays in it; until the
	 * first real medium, the length walked is kept aside in {@link #pending} and that medium absorbs it once known.
	 */
	private @Nullable AcousticMedium current;
	private double pending;
	/** The interface the current voxel crossed, if any. */
	private @Nullable String crossed;

	SegmentTracer(AcousticGrid grid, OcclusionSettings settings) {
		this.grid = grid;
		this.settings = settings;
		this.air = settings.air();
	}

	/** The medium the last trace started in, if it learned it. */
	@Nullable AcousticMedium startMedium() {
		return startMedium;
	}

	/** The medium the last trace arrived in, if it learned it. */
	@Nullable AcousticMedium endMedium() {
		return endMedium;
	}

	/** Whether the last trace was muted by the medium at one of its ends. */
	boolean endMuted() {
		return endMuted;
	}

	/**
	 * The loss from {@code from} to {@code to}, recording in {@code capture} (when not null) the voxels that lose
	 * something; null when {@code budget} ran out before the end voxel. One voxel of budget per voxel visited.
	 */
	@Nullable PathLoss trace(double[] from, double[] to, TraceBudget budget, @Nullable PathCapture capture) {
		recording = capture;
		try {
			return walk(from, to, budget);
		} finally {
			recording = null;
		}
	}

	/** The medium of the voxel holding {@code p}, or {@link AcousticGrid#defaultMedium()} when it has none. */
	AcousticMedium ownMedium(double[] p) {
		grid.sample((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]), sample);
		AcousticMedium own = sample.medium;
		return own != null ? own : grid.defaultMedium();
	}

	/** Whether the voxel holding {@code p} holds no material and is in {@code medium}. */
	boolean open(double[] p, AcousticMedium medium) {
		grid.sample((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]), sample);
		return sample.material == null && medium.equals(sample.medium);
	}

	/** Walks the segment, as {@link #trace} describes. */
	private @Nullable PathLoss walk(double[] from, double[] to, TraceBudget budget) {
		walk.start(from[0], from[1], from[2], to[0], to[1], to[2]);
		length = walk.length();
		loss = 0;
		high = 0;
		current = null;
		pending = 0;
		startMedium = null;
		endMedium = null;
		endMuted = false;
		boolean more = true;
		while (more) {
			if (!budget.take()) {
				return null;
			}
			double inside = (walk.exitT() - walk.enterT()) * length;
			if (inside >= MIN_LENGTH && !visit(inside)) {
				return PathLoss.mute(length);
			}
			more = !walk.last();
			if (more) {
				walk.step();
			}
		}
		return finish();
	}

	/** Adds what the current voxel loses over {@code inside} blocks; false when it mutes the segment. */
	private boolean visit(double inside) {
		boolean ends = walk.first() || walk.last();
		double before = loss;
		crossed = null;
		grid.sample(walk.x(), walk.y(), walk.z(), sample);
		AcousticMedium own = sample.medium;
		if (own != null && own.impedance() <= 0) {
			endMuted = ends;
			record(own.name(), Double.POSITIVE_INFINITY);
			return false;
		}
		// The end voxels bring no material: the speaker's block, the listener's head.
		AcousticMaterial material = ends ? null : sample.material;
		boolean materialLoss = material != null && crossMaterial(material, inside);
		if (own == null) {
			stay(inside);
		} else if (!enter(own, inside)) {
			return false;
		}
		if (recording != null && loss > before) {
			record(lossName(material, materialLoss), loss - before);
		}
		return true;
	}

	/** What the current voxel lost in: its material, else the interface it crossed, else the medium. */
	private String lossName(@Nullable AcousticMaterial material, boolean materialLoss) {
		if (materialLoss) {
			return Objects.requireNonNull(material).name();
		}
		return crossed != null ? crossed : Objects.requireNonNull(current).name();
	}

	/** Adds what {@code material} loses, weighted by its fill along the entry axis; true when that is a loss. */
	private boolean crossMaterial(AcousticMaterial material, double inside) {
		double weight = sample.fill(walk.entryAxis()) * Math.min(1, inside);
		loss += material.lossDb() * weight;
		high += material.highLossDb() * weight;
		return material.lossDb() * weight > 0;
	}

	/** A voxel without a medium of its own: the walk stays in its medium, or keeps the length aside. */
	private void stay(double inside) {
		if (current == null) {
			pending += inside;
		} else {
			loss += absorption(current, inside);
			high += highAbsorption(current, inside);
		}
	}

	/** A voxel in {@code own}: absorbs in it and pays the interface from the walk's medium; false when mute. */
	private boolean enter(AcousticMedium own, double inside) {
		loss += absorption(own, inside + pending);
		high += highAbsorption(own, inside + pending);
		pending = 0;
		if (current == null) {
			startMedium = own;
		} else if (!current.equals(own)) {
			String name = current.name() + "→" + own.name();
			double interfaceLoss = Interfaces.lossDb(current, own, settings.interfaceScale());
			if (Double.isInfinite(interfaceLoss)) {
				record(name, Double.POSITIVE_INFINITY);
				return false;
			}
			loss += interfaceLoss;
			high += Interfaces.highLossDb(interfaceLoss, settings.interfaceHighRatio());
			crossed = name;
		}
		current = own;
		return true;
	}

	/** The loss once the walk reached the end voxel, capped. */
	private PathLoss finish() {
		if (current == null) {
			// Nothing but voxels without a medium of their own: the segment is in the dimension's medium.
			current = grid.defaultMedium();
			startMedium = current;
			if (current.impedance() <= 0) {
				endMuted = true;
				return PathLoss.mute(length);
			}
			loss += absorption(current, pending);
			high += highAbsorption(current, pending);
		}
		endMedium = current;
		double max = settings.maxLossDb();
		return new PathLoss(Math.min(loss, max), Math.min(high, max), length);
	}

	private void record(String name, double lossDb) {
		if (recording != null) {
			recording.step(walk.x(), walk.y(), walk.z(), name, lossDb);
		}
	}

	/** What {@code medium} absorbs broadband over {@code blocks}; none for air, left to {@code Propagation}. */
	private double absorption(AcousticMedium medium, double blocks) {
		return medium.name().equals(air.name()) ? 0 : medium.lossDbPerBlock() * blocks;
	}

	/** What {@code medium} absorbs in the treble over {@code blocks}; none for air, left to {@code Propagation}. */
	private double highAbsorption(AcousticMedium medium, double blocks) {
		return medium.name().equals(air.name()) ? 0 : medium.highLossDbPerBlock() * blocks;
	}
}
