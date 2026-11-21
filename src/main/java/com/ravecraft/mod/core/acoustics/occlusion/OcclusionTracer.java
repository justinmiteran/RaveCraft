package com.ravecraft.mod.core.acoustics.occlusion;

import com.ravecraft.mod.core.acoustics.Decibels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Walks a straight segment voxel by voxel (Amanatides–Woo) and adds up what it loses: the materials it crosses
 * (weighted by their fill along the entry axis), the media it travels through (except air, whose absorption
 * {@code Propagation} applies) and the interfaces between media. The start and end voxels bring no material (the
 * speaker's block, the listener's head). A voxel without a medium of its own (a solid without fluid) stays in the
 * medium the walk is in, with no interface: a segment starting in one takes the first medium it meets, and one that
 * meets none is in {@link AcousticGrid#defaultMedium()}. A medium of impedance 0 anywhere on the way mutes the
 * segment. Both totals are capped at {@link OcclusionSettings#maxLossDb()}.
 *
 * <p>{@link #occlusion} adds detours: when the straight line loses 3 dB or more, sound may also bend through
 * waypoints picked around both ends (an open door, a corridor), and the direct path and the best detours are summed
 * in energy. Not thread-safe: one tracer per thread.
 */
public final class OcclusionTracer {
	/** Voxels crossed over less than this many blocks (a ray grazing an edge or a corner) add nothing. */
	private static final double MIN_LENGTH = 1e-9;
	/** A direct path losing less than this tries no detour, and a waypoint must be reached losing less than this. */
	private static final double DETOUR_THRESHOLD_DB = 3;
	/** How far from an end its waypoints are looked for, in blocks. */
	private static final double[] WAYPOINT_RADII = {3, 6};
	/** How many waypoints each end keeps (the closest to the other end). */
	private static final int WAYPOINTS_PER_END = 6;
	/** How many detours are summed with the direct path. */
	private static final int BEST_DETOURS = 3;
	/** A detour no longer than the direct path plus this many blocks runs along the straight line and is ignored. */
	private static final double COLLINEAR_EPSILON = 1e-6;
	/** Detours whose last legs are closer than this, in degrees, come through the same opening: only the best counts. */
	private static final double DISTINCT_OPENING_DEGREES = 45;
	/** Tolerance on angles computed from lattice directions (exactly 45° apart in theory). */
	private static final double ANGLE_EPSILON = 1e-9;
	/** The 26 unit directions to the neighbours of a voxel, in a fixed order. */
	private static final double[][] DIRECTIONS = directions();

	private final AcousticGrid grid;
	private final OcclusionSettings settings;
	private final AcousticMedium air;
	private final VoxelSample sample = new VoxelSample();
	private final VoxelWalk walk = new VoxelWalk();
	/**
	 * Set by each {@link #trace}: the medium the segment starts in and the one it arrives in (a voxel without a medium
	 * of its own takes the one around it along the segment); null when the trace stopped before knowing it.
	 */
	private @Nullable AcousticMedium startMedium;
	private @Nullable AcousticMedium endMedium;
	/** Set by each {@link #trace}: true when it was muted by the medium at one of its ends. */
	private boolean endMuted;
	/** Where {@link #trace} records the voxels it loses in; set only around the direct path of {@link #occlusion}. */
	private @Nullable PathCapture recording;

	/** A candidate waypoint near one end, with the trace that reaches it from that end. */
	private record Waypoint(double x, double y, double z, PathLoss fromEnd) {
	}

	/** A detour's points from source to listener, and its segments already traced (null: still to trace). */
	private record Path(double[][] points, @Nullable PathLoss[] known) {
	}

	/**
	 * One way the sound gets through: broadband loss and extra treble loss, both infinite when it is mute, and for a
	 * detour the unit direction of its last leg (waypoint → listener).
	 */
	private record Route(double lossDb, double highLossDb, double @Nullable [] lastLeg,
			double @Nullable [][] points) {
		/** A detour that is not taken. */
		static final Route SKIPPED = new Route(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, null, null);
	}

	/**
	 * Creates a tracer over {@code grid}.
	 *
	 * @param grid the blocks around the listener
	 * @param settings the media and materials
	 */
	public OcclusionTracer(AcousticGrid grid, OcclusionSettings settings) {
		this.grid = grid;
		this.settings = settings;
		this.air = settings.air();
	}

	/**
	 * The loss from (x0, y0, z0) to (x1, y1, z1), or null when {@code budget} ran out before the end voxel; one voxel
	 * of budget is taken per voxel visited.
	 */
	public @Nullable PathLoss trace(double x0, double y0, double z0, double x1, double y1, double z1,
			TraceBudget budget) {
		walk.start(x0, y0, z0, x1, y1, z1);
		double length = walk.length();
		double loss = 0;
		double high = 0;
		// The medium the walk is in. A voxel without a medium of its own (a solid without fluid) stays in it; until
		// the first real medium, the length walked is kept aside and that medium absorbs it once known.
		@Nullable AcousticMedium current = null;
		double pending = 0;
		startMedium = null;
		endMedium = null;
		endMuted = false;
		while (true) {
			if (!budget.take()) {
				return null;
			}
			boolean first = walk.first();
			boolean last = walk.last();
			double inside = (walk.exitT() - walk.enterT()) * length;

			if (inside >= MIN_LENGTH) {
				double before = loss;
				@Nullable String crossed = null;
				grid.sample(walk.x(), walk.y(), walk.z(), sample);
				AcousticMedium own = sample.medium;
				if (own != null && own.impedance() <= 0) {
					endMuted = first || last;
					if (recording != null) {
						recording.step(walk.x(), walk.y(), walk.z(), own.name(), Double.POSITIVE_INFINITY);
					}
					return PathLoss.mute(length);
				}
				AcousticMaterial material = sample.material;
				boolean materialLoss = false;
				if (material != null && !first && !last) {
					double weight = sample.fill(walk.entryAxis()) * Math.min(1, inside);
					loss += material.lossDb() * weight;
					high += material.highLossDb() * weight;
					materialLoss = material.lossDb() * weight > 0;
				}
				if (own == null) {
					if (current == null) {
						pending += inside;
					} else {
						loss += absorption(current, inside);
						high += highAbsorption(current, inside);
					}
				} else {
					loss += absorption(own, inside + pending);
					high += highAbsorption(own, inside + pending);
					pending = 0;
					if (current == null) {
						startMedium = own;
					} else if (!current.equals(own)) {
						double interfaceLoss = Interfaces.lossDb(current, own, settings.interfaceScale());
						if (Double.isInfinite(interfaceLoss)) {
							if (recording != null) {
								recording.step(walk.x(), walk.y(), walk.z(), current.name() + "→" + own.name(),
										Double.POSITIVE_INFINITY);
							}
							return PathLoss.mute(length);
						}
						loss += interfaceLoss;
						high += Interfaces.highLossDb(interfaceLoss, settings.interfaceHighRatio());
						crossed = current.name() + "→" + own.name();
					}
					current = own;
				}
				if (recording != null && loss > before) {
					String name = materialLoss ? Objects.requireNonNull(material).name()
							: crossed != null ? crossed : Objects.requireNonNull(current).name();
					recording.step(walk.x(), walk.y(), walk.z(), name, loss - before);
				}
			}

			if (last) {
				break;
			}
			walk.step();
		}
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

	/** The medium of the voxel holding (x, y, z), or {@link AcousticGrid#defaultMedium()} when it has none. */
	private AcousticMedium ownMedium(double x, double y, double z) {
		grid.sample((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), sample);
		AcousticMedium own = sample.medium;
		return own != null ? own : grid.defaultMedium();
	}

	/** What {@code medium} absorbs broadband over {@code blocks}; none for air, left to {@code Propagation}. */
	private double absorption(AcousticMedium medium, double blocks) {
		return medium.name().equals(air.name()) ? 0 : medium.lossDbPerBlock() * blocks;
	}

	/** What {@code medium} absorbs in the treble over {@code blocks}; none for air, left to {@code Propagation}. */
	private double highAbsorption(AcousticMedium medium, double blocks) {
		return medium.name().equals(air.name()) ? 0 : medium.highLossDbPerBlock() * blocks;
	}

	/**
	 * The occlusion between a listener at (lx, ly, lz) and a source at (sx, sy, sz): the direct path, plus up to
	 * {@value #BEST_DETOURS} detours through one or two waypoints when detours are enabled and the direct path loses
	 * 3 dB or more. A detour pays its segments, the extra distance ({@code 20·log10(length / direct)·rolloff}) and
	 * each bend ({@code bendLossDb} and {@code bendHighLossDb} per 90°); detours longer than {@code maxPathLength},
	 * running along the straight line or losing at least as much as the direct path are ignored. Null when
	 * {@code budget} ran out; {@link Occlusion#NONE} without visiting any voxel when occlusion is disabled.
	 */
	public @Nullable Occlusion occlusion(double lx, double ly, double lz, double sx, double sy, double sz,
			double rolloff, double maxPathLength, TraceBudget budget) {
		return occlusion(lx, ly, lz, sx, sy, sz, rolloff, maxPathLength, budget, BEST_DETOURS, null);
	}

	/**
	 * {@link #occlusion(double, double, double, double, double, double, double, double, TraceBudget)}, recording in
	 * {@code capture} (when not null) the voxels of the direct path that lose something and the detours summed with
	 * it. The capture is reset first, even when occlusion is disabled.
	 */
	public @Nullable Occlusion occlusion(double lx, double ly, double lz, double sx, double sy, double sz,
			double rolloff, double maxPathLength, TraceBudget budget, @Nullable PathCapture capture) {
		return occlusion(lx, ly, lz, sx, sy, sz, rolloff, maxPathLength, budget, BEST_DETOURS, capture);
	}

	/**
	 * {@link #occlusion(double, double, double, double, double, double, double, double, TraceBudget)} summing at most
	 * {@code maxDetours} detours, each through a distinct opening (package-private for tests).
	 */
	@Nullable Occlusion occlusion(double lx, double ly, double lz, double sx, double sy, double sz,
			double rolloff, double maxPathLength, TraceBudget budget, int maxDetours) {
		return occlusion(lx, ly, lz, sx, sy, sz, rolloff, maxPathLength, budget, maxDetours, null);
	}

	private @Nullable Occlusion occlusion(double lx, double ly, double lz, double sx, double sy, double sz,
			double rolloff, double maxPathLength, TraceBudget budget, int maxDetours, @Nullable PathCapture capture) {
		if (capture != null) {
			capture.reset();
		}
		if (!settings.enabled()) {
			return Occlusion.NONE;
		}
		recording = capture;
		PathLoss direct;
		try {
			direct = trace(lx, ly, lz, sx, sy, sz, budget);
		} finally {
			recording = null;
		}
		if (direct == null) {
			return null;
		}
		if (capture != null) {
			capture.direct(direct);
		}
		List<Route> routes = new ArrayList<>();
		routes.add(new Route(direct.lossDb(), direct.highLossDb(), null, null));
		// An end in a medium that carries nothing (vacuum) mutes every detour as well.
		if (!settings.detours() || direct.lossDb() < DETOUR_THRESHOLD_DB || (direct.mute() && endMuted)) {
			return combine(routes);
		}
		// The ends' media as the direct path found them (the waypoints must lie in the same medium); a path muted on
		// the way may not have reached them: then the end's own medium, or the dimension's.
		AcousticMedium listenerMedium = startMedium != null ? startMedium : ownMedium(lx, ly, lz);
		AcousticMedium sourceMedium = endMedium != null ? endMedium : ownMedium(sx, sy, sz);

		List<Waypoint> nearListener = waypoints(lx, ly, lz, listenerMedium, sx, sy, sz, budget);
		if (nearListener == null) {
			return null;
		}
		List<Waypoint> nearSource = waypoints(sx, sy, sz, sourceMedium, lx, ly, lz, budget);
		if (nearSource == null) {
			return null;
		}
		double[] source = {sx, sy, sz};
		double[] listener = {lx, ly, lz};
		Set<List<Long>> seen = new HashSet<>();
		List<Path> paths = new ArrayList<>();
		for (Waypoint p : nearListener) {
			addPath(paths, seen, new double[][] {source, point(p), listener}, new PathLoss[] {null, p.fromEnd()});
		}
		for (Waypoint q : nearSource) {
			addPath(paths, seen, new double[][] {source, point(q), listener}, new PathLoss[] {q.fromEnd(), null});
		}
		for (Waypoint q : nearSource) {
			for (Waypoint p : nearListener) {
				addPath(paths, seen, new double[][] {source, point(q), point(p), listener},
						new PathLoss[] {q.fromEnd(), null, p.fromEnd()});
			}
		}
		List<Route> detours = new ArrayList<>();
		for (Path path : paths) {
			Route route = route(path, direct, rolloff, maxPathLength, budget, capture != null);
			if (route == null) {
				return null;
			}
			// A detour losing at least as much as the direct path mostly crosses the same obstacles (the same wall a
			// little further along): adding its energy would count that transmission twice.
			if (route != Route.SKIPPED && route.lossDb() < direct.lossDb()) {
				detours.add(route);
			}
		}
		detours.sort(Comparator.comparingDouble(Route::lossDb));
		List<Route> chosen = new ArrayList<>();
		for (Route detour : detours) {
			if (chosen.size() >= maxDetours) {
				break;
			}
			if (distinctOpening(detour, chosen)) {
				chosen.add(detour);
			}
		}
		routes.addAll(chosen);
		if (capture != null) {
			for (Route detour : chosen) {
				capture.detour(Objects.requireNonNull(detour.points()), detour.lossDb());
			}
		}
		return combine(routes);
	}

	/**
	 * Adds the path through {@code points} unless a waypoint shares a voxel with an end or with the other waypoint
	 * (its bends would be meaningless), or the same waypoint voxels were already added.
	 */
	private static void addPath(List<Path> paths, Set<List<Long>> seen, double[][] points,
			@Nullable PathLoss[] known) {
		long source = voxelKey(points[0]);
		long listener = voxelKey(points[points.length - 1]);
		List<Long> voxels = new ArrayList<>(points.length - 2);
		for (int i = 1; i + 1 < points.length; i++) {
			long voxel = voxelKey(points[i]);
			if (voxel == source || voxel == listener || voxels.contains(voxel)) {
				return;
			}
			voxels.add(voxel);
		}
		if (seen.add(voxels)) {
			paths.add(new Path(points, known));
		}
	}

	/**
	 * True when the last leg of {@code detour} is at least {@value #DISTINCT_OPENING_DEGREES}° away from the last leg
	 * of every detour in {@code chosen}: detours arriving from nearly the same direction come through the same
	 * opening and would count it twice.
	 */
	private static boolean distinctOpening(Route detour, List<Route> chosen) {
		double[] d = Objects.requireNonNull(detour.lastLeg());
		for (Route other : chosen) {
			double[] o = Objects.requireNonNull(other.lastLeg());
			double cos = Math.clamp(d[0] * o[0] + d[1] * o[1] + d[2] * o[2], -1, 1);
			if (Math.toDegrees(Math.acos(cos)) < DISTINCT_OPENING_DEGREES - ANGLE_EPSILON) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The waypoints around the end at (ax, ay, az), in {@code medium}: for each of the 26 directions and each radius, the
	 * point that far from the centre of the end's voxel, a candidate when its voxel holds no material and is in
	 * {@code medium}.
	 * Candidates are traced from the end in ascending distance to (ox, oy, oz) and the first
	 * {@value #WAYPOINTS_PER_END} reached losing less than 3 dB are returned, each with the trace that reached it so
	 * that it is not traced again. Null when {@code budget} ran out.
	 */
	private @Nullable List<Waypoint> waypoints(double ax, double ay, double az, AcousticMedium medium,
			double ox, double oy, double oz, TraceBudget budget) {
		int bx = (int) Math.floor(ax);
		int by = (int) Math.floor(ay);
		int bz = (int) Math.floor(az);
		Set<Long> seen = new HashSet<>();
		List<double[]> candidates = new ArrayList<>();
		for (double[] d : DIRECTIONS) {
			for (double r : WAYPOINT_RADII) {
				double[] p = {bx + 0.5 + d[0] * r, by + 0.5 + d[1] * r, bz + 0.5 + d[2] * r};
				int vx = (int) Math.floor(p[0]);
				int vy = (int) Math.floor(p[1]);
				int vz = (int) Math.floor(p[2]);
				if (!seen.add(key(vx, vy, vz))) {
					continue;
				}
				grid.sample(vx, vy, vz, sample);
				if (sample.material == null && medium.equals(sample.medium)) {
					candidates.add(p);
				}
			}
		}
		double[] other = {ox, oy, oz};
		candidates.sort(Comparator.comparingDouble(p -> distance(p, other)));
		List<Waypoint> found = new ArrayList<>(WAYPOINTS_PER_END);
		for (double[] p : candidates) {
			if (found.size() >= WAYPOINTS_PER_END) {
				break;
			}
			PathLoss reach = trace(ax, ay, az, p[0], p[1], p[2], budget);
			if (reach == null) {
				return null;
			}
			if (reach.lossDb() < DETOUR_THRESHOLD_DB) {
				found.add(new Waypoint(p[0], p[1], p[2], reach));
			}
		}
		return found;
	}

	/**
	 * The cost of {@code path}, tracing only its unknown segments. {@link Route#SKIPPED}, nothing traced, when a leg
	 * has no length, when the path is longer than {@code maxPathLength} or no longer than the direct path, or when its
	 * extra length and bends alone already lose as much as {@code direct}; also {@link Route#SKIPPED} when a segment
	 * is mute. Null when {@code budget} ran out.
	 */
	private @Nullable Route route(Path path, PathLoss direct, double rolloff, double maxPathLength,
			TraceBudget budget, boolean keepPoints) {
		double[][] points = path.points();
		double length = 0;
		for (int i = 0; i + 1 < points.length; i++) {
			double leg = distance(points[i], points[i + 1]);
			if (leg < MIN_LENGTH) {
				return Route.SKIPPED;
			}
			length += leg;
		}
		double directLength = direct.length();
		// Waypoints on the straight line give the direct path again: counting it twice would halve its loss.
		if (length > maxPathLength || length <= directLength + COLLINEAR_EPSILON) {
			return Route.SKIPPED;
		}
		double loss = directLength > 0 ? Decibels.fromAmplitude(length / directLength) * rolloff : 0;
		double high = 0;
		for (int i = 1; i + 1 < points.length; i++) {
			double bend = bendDegrees(points[i - 1], points[i], points[i + 1]) / 90;
			loss += settings.bendLossDb() * bend;
			high += settings.bendHighLossDb() * bend;
		}
		// Segments never lose less than nothing: such a detour could not beat the direct path.
		if (loss >= direct.lossDb()) {
			return Route.SKIPPED;
		}
		@Nullable PathLoss[] segments = path.known();
		for (int i = 0; i < segments.length; i++) {
			PathLoss segment = segments[i];
			if (segment == null) {
				double[] a = points[i];
				double[] b = points[i + 1];
				segment = trace(a[0], a[1], a[2], b[0], b[1], b[2], budget);
				if (segment == null) {
					return null;
				}
			}
			if (segment.mute()) {
				return Route.SKIPPED;
			}
			loss += segment.lossDb();
			high += segment.highLossDb();
		}
		double[] a = points[points.length - 2];
		double[] b = points[points.length - 1];
		double leg = distance(a, b);
		return new Route(loss, high, new double[] {(b[0] - a[0]) / leg, (b[1] - a[1]) / leg, (b[2] - a[2]) / leg},
				keepPoints ? points : null);
	}

	/** Sums the routes in energy, broadband and treble apart; {@link Occlusion#MUTE} when none gets through. */
	private Occlusion combine(List<Route> routes) {
		double energy = 0;
		double highEnergy = 0;
		for (Route route : routes) {
			energy += Decibels.toPower(-route.lossDb());
			highEnergy += Decibels.toPower(-(route.lossDb() + route.highLossDb()));
		}
		if (energy <= 0) {
			return Occlusion.MUTE;
		}
		double max = settings.maxLossDb();
		// Math.max also turns the -0.0 of a lossless path into 0.0.
		double loss = Math.max(0, -Decibels.fromPower(energy));
		double total = highEnergy > 0 ? -Decibels.fromPower(highEnergy) : Double.POSITIVE_INFINITY;
		return new Occlusion(Math.min(loss, max), Math.clamp(total - loss, 0, max));
	}

	/** The deviation in degrees between the directions a → b and b → c, both of non-zero length. */
	private static double bendDegrees(double[] a, double[] b, double[] c) {
		double ux = b[0] - a[0];
		double uy = b[1] - a[1];
		double uz = b[2] - a[2];
		double vx = c[0] - b[0];
		double vy = c[1] - b[1];
		double vz = c[2] - b[2];
		double norms = Math.sqrt((ux * ux + uy * uy + uz * uz) * (vx * vx + vy * vy + vz * vz));
		return Math.toDegrees(Math.acos(Math.clamp((ux * vx + uy * vy + uz * vz) / norms, -1, 1)));
	}

	private static double distance(double[] a, double[] b) {
		double dx = b[0] - a[0];
		double dy = b[1] - a[1];
		double dz = b[2] - a[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double[] point(Waypoint w) {
		return new double[] {w.x(), w.y(), w.z()};
	}

	private static long voxelKey(double[] p) {
		return key((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]));
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
	}

	private static double[][] directions() {
		double[][] out = new double[26][];
		int i = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
					out[i++] = new double[] {dx / n, dy / n, dz / n};
				}
			}
		}
		return out;
	}
}
