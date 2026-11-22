package com.ravecraft.mod.core.acoustics.occlusion;

import com.ravecraft.mod.core.acoustics.Decibels;
import com.ravecraft.mod.core.acoustics.Vec;

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
	/** The bend the bend losses are given for. */
	private static final double RIGHT_ANGLE_DEGREES = 90;

	private final OcclusionSettings settings;
	private final SegmentTracer segments;

	/** A candidate waypoint near one end, with the trace that reaches it from that end. */
	private record Waypoint(double[] point, PathLoss fromEnd) {
	}

	/** A detour's points from source to listener, and its segments already traced (null: still to trace). */
	private record Path(double[][] points, @Nullable PathLoss[] known) {
	}

	/** One occlusion asked for: the ends, how loss grows with distance, and what to sum and record. */
	private record Query(double[] listener, double[] source, double rolloff, double maxPathLength, int maxDetours,
			@Nullable PathCapture capture) {
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
		this.settings = settings;
		this.segments = new SegmentTracer(grid, settings);
	}

	/**
	 * The loss from {@code from} to {@code to}, or null when {@code budget} ran out before the end voxel; one voxel of
	 * budget is taken per voxel visited.
	 *
	 * @param from the start point
	 * @param to the end point
	 * @param budget the voxels left this tick
	 * @return the loss, or null when the budget ran out
	 */
	public @Nullable PathLoss trace(Vec from, Vec to, TraceBudget budget) {
		return segments.trace(point(from), point(to), budget, null);
	}

	/**
	 * The occlusion between a listener and a source: the direct path, plus up to {@value #BEST_DETOURS} detours
	 * through one or two waypoints when detours are enabled and the direct path loses 3 dB or more. A detour pays its
	 * segments, the extra distance ({@code 20·log10(length / direct)·rolloff}) and each bend ({@code bendLossDb} and
	 * {@code bendHighLossDb} per 90°); detours longer than {@code maxPathLength}, running along the straight line or
	 * losing at least as much as the direct path are ignored. Null when {@code budget} ran out; {@link Occlusion#NONE}
	 * without visiting any voxel when occlusion is disabled.
	 *
	 * @param listener the listener's position
	 * @param source the source's position
	 * @param rolloff the distance law's exponent
	 * @param maxPathLength the longest detour, in blocks
	 * @param budget the voxels left this tick
	 * @return the occlusion, or null when the budget ran out
	 */
	public @Nullable Occlusion occlusion(Vec listener, Vec source, double rolloff, double maxPathLength,
			TraceBudget budget) {
		return occlusion(new Query(point(listener), point(source), rolloff, maxPathLength, BEST_DETOURS, null), budget);
	}

	/**
	 * {@link #occlusion(Vec, Vec, double, double, TraceBudget)}, recording in {@code capture} (when not null) the
	 * voxels of the direct path that lose something and the detours summed with it. The capture is reset first, even
	 * when occlusion is disabled.
	 *
	 * @param listener the listener's position
	 * @param source the source's position
	 * @param rolloff the distance law's exponent
	 * @param maxPathLength the longest detour, in blocks
	 * @param budget the voxels left this tick
	 * @param capture records the paths, or null
	 * @return the occlusion, or null when the budget ran out
	 */
	public @Nullable Occlusion occlusion(Vec listener, Vec source, double rolloff, double maxPathLength,
			TraceBudget budget, @Nullable PathCapture capture) {
		return occlusion(new Query(point(listener), point(source), rolloff, maxPathLength, BEST_DETOURS, capture),
				budget);
	}

	/**
	 * {@link #occlusion(Vec, Vec, double, double, TraceBudget)} summing at most {@code maxDetours} detours, each
	 * through a distinct opening (package-private for tests).
	 */
	@Nullable Occlusion occlusion(Vec listener, Vec source, double rolloff, double maxPathLength, TraceBudget budget,
			int maxDetours) {
		return occlusion(new Query(point(listener), point(source), rolloff, maxPathLength, maxDetours, null), budget);
	}

	private @Nullable Occlusion occlusion(Query q, TraceBudget budget) {
		if (q.capture() != null) {
			q.capture().reset();
		}
		if (!settings.enabled()) {
			return Occlusion.NONE;
		}
		PathLoss direct = segments.trace(q.listener(), q.source(), budget, q.capture());
		return direct == null ? null : withDetours(q, direct, budget);
	}

	/** Sums {@code direct} with the best detours, when it is worth looking for them; null when the budget ran out. */
	private @Nullable Occlusion withDetours(Query q, PathLoss direct, TraceBudget budget) {
		if (q.capture() != null) {
			q.capture().direct(direct);
		}
		List<Route> routes = new ArrayList<>();
		routes.add(new Route(direct.lossDb(), direct.highLossDb(), null, null));
		// An end in a medium that carries nothing (vacuum) mutes every detour as well.
		if (!settings.detours() || direct.lossDb() < DETOUR_THRESHOLD_DB || direct.mute() && segments.endMuted()) {
			return combine(routes);
		}
		List<Route> chosen = detours(q, direct, budget);
		if (chosen == null) {
			return null;
		}
		routes.addAll(chosen);
		if (q.capture() != null) {
			for (Route detour : chosen) {
				q.capture().detour(Objects.requireNonNull(detour.points()), detour.lossDb());
			}
		}
		return combine(routes);
	}

	/**
	 * The detours to sum with {@code direct}, which must be the last trace: at most {@code maxDetours}, the least
	 * lossy first, each through a distinct opening. Null when the budget ran out.
	 */
	private @Nullable List<Route> detours(Query q, PathLoss direct, TraceBudget budget) {
		// The ends' media as the direct path found them (the waypoints must lie in the same medium); a path muted on
		// the way may not have reached them: then the end's own medium, or the dimension's.
		AcousticMedium start = segments.startMedium();
		AcousticMedium end = segments.endMedium();
		AcousticMedium listenerMedium = start != null ? start : segments.ownMedium(q.listener());
		AcousticMedium sourceMedium = end != null ? end : segments.ownMedium(q.source());
		List<Waypoint> nearListener = waypoints(q.listener(), listenerMedium, q.source(), budget);
		if (nearListener == null) {
			return null;
		}
		List<Waypoint> nearSource = waypoints(q.source(), sourceMedium, q.listener(), budget);
		if (nearSource == null) {
			return null;
		}
		List<Route> kept = new ArrayList<>();
		for (Path path : paths(q, nearListener, nearSource)) {
			Route route = route(path, direct, q, budget);
			if (route == null) {
				return null;
			}
			// A detour losing at least as much as the direct path mostly crosses the same obstacles (the same wall a
			// little further along): adding its energy would count that transmission twice.
			if (route != Route.SKIPPED && route.lossDb() < direct.lossDb()) {
				kept.add(route);
			}
		}
		return best(kept, q.maxDetours());
	}

	/** The paths through one waypoint near either end, then through one near each end. */
	private static List<Path> paths(Query q, List<Waypoint> nearListener, List<Waypoint> nearSource) {
		double[] source = q.source();
		double[] listener = q.listener();
		Set<List<Long>> seen = new HashSet<>();
		List<Path> paths = new ArrayList<>();
		for (Waypoint p : nearListener) {
			addPath(paths, seen, new double[][] {source, p.point(), listener}, new PathLoss[] {null, p.fromEnd()});
		}
		for (Waypoint q2 : nearSource) {
			addPath(paths, seen, new double[][] {source, q2.point(), listener}, new PathLoss[] {q2.fromEnd(), null});
		}
		for (Waypoint q2 : nearSource) {
			for (Waypoint p : nearListener) {
				addPath(paths, seen, new double[][] {source, q2.point(), p.point(), listener},
						new PathLoss[] {q2.fromEnd(), null, p.fromEnd()});
			}
		}
		return paths;
	}

	/** The least lossy of {@code detours}, at most {@code maxDetours}, each through a distinct opening. */
	private static List<Route> best(List<Route> detours, int maxDetours) {
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
		return chosen;
	}

	/**
	 * Adds the path through {@code points} unless a waypoint shares a voxel with an end or with the other waypoint
	 * (its bends would be meaningless), or the same waypoint voxels were already added.
	 */
	private static void addPath(List<Path> paths, Set<List<Long>> seen, double[][] points,
			@Nullable PathLoss[] known) {
		long source = Lattice.voxelKey(points[0]);
		long listener = Lattice.voxelKey(points[points.length - 1]);
		List<Long> voxels = new ArrayList<>(points.length - 2);
		for (int i = 1; i + 1 < points.length; i++) {
			long voxel = Lattice.voxelKey(points[i]);
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
			if (Lattice.angleDegrees(d, o) < DISTINCT_OPENING_DEGREES - ANGLE_EPSILON) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The waypoints around the end {@code a}, in {@code medium}, traced from the end in ascending distance to
	 * {@code other}: the first {@value #WAYPOINTS_PER_END} reached losing less than 3 dB, each with the trace that
	 * reached it so that it is not traced again. Null when {@code budget} ran out.
	 */
	private @Nullable List<Waypoint> waypoints(double[] a, AcousticMedium medium, double[] other,
			TraceBudget budget) {
		List<double[]> candidates = candidates(a, medium);
		candidates.sort(Comparator.comparingDouble(p -> Lattice.distance(p, other)));
		List<Waypoint> found = new ArrayList<>(WAYPOINTS_PER_END);
		for (double[] p : candidates) {
			if (found.size() >= WAYPOINTS_PER_END) {
				break;
			}
			PathLoss reach = segments.trace(a, p, budget, null);
			if (reach == null) {
				return null;
			}
			if (reach.lossDb() < DETOUR_THRESHOLD_DB) {
				found.add(new Waypoint(p, reach));
			}
		}
		return found;
	}

	/**
	 * For each of the 26 directions and each radius, the point that far from the centre of the voxel of {@code a}: a
	 * candidate when its voxel holds no material and is in {@code medium}. One per voxel.
	 */
	private List<double[]> candidates(double[] a, AcousticMedium medium) {
		double cx = Math.floor(a[0]) + Lattice.CENTRE;
		double cy = Math.floor(a[1]) + Lattice.CENTRE;
		double cz = Math.floor(a[2]) + Lattice.CENTRE;
		Set<Long> seen = new HashSet<>();
		List<double[]> candidates = new ArrayList<>();
		for (double[] d : Lattice.DIRECTIONS) {
			for (double r : WAYPOINT_RADII) {
				double[] p = {cx + d[0] * r, cy + d[1] * r, cz + d[2] * r};
				if (seen.add(Lattice.voxelKey(p)) && segments.open(p, medium)) {
					candidates.add(p);
				}
			}
		}
		return candidates;
	}

	/**
	 * The cost of {@code path}, tracing only its unknown segments. {@link Route#SKIPPED}, nothing traced, when a leg
	 * has no length, when the path is longer than {@code maxPathLength} or no longer than the direct path, or when its
	 * extra length and bends alone already lose as much as {@code direct}; also {@link Route#SKIPPED} when a segment
	 * is mute. Null when {@code budget} ran out.
	 */
	private @Nullable Route route(Path path, PathLoss direct, Query q, TraceBudget budget) {
		double[][] points = path.points();
		double length = length(points);
		double directLength = direct.length();
		// Waypoints on the straight line give the direct path again: counting it twice would halve its loss.
		if (Double.isNaN(length) || length > q.maxPathLength() || length <= directLength + COLLINEAR_EPSILON) {
			return Route.SKIPPED;
		}
		double[] cost = shapeCost(points, length, directLength, q.rolloff());
		// Segments never lose less than nothing: such a detour could not beat the direct path.
		if (cost[0] >= direct.lossDb()) {
			return Route.SKIPPED;
		}
		return traced(path, cost, budget, q.capture() != null);
	}

	/** The length of the path through {@code points}; NaN when a leg has no length. */
	private static double length(double[][] points) {
		double length = 0;
		for (int i = 0; i + 1 < points.length; i++) {
			double leg = Lattice.distance(points[i], points[i + 1]);
			if (leg < SegmentTracer.MIN_LENGTH) {
				return Double.NaN;
			}
			length += leg;
		}
		return length;
	}

	/** What the extra distance and the bends of a path cost, broadband and treble. */
	private double[] shapeCost(double[][] points, double length, double directLength, double rolloff) {
		double loss = directLength > 0 ? Decibels.fromAmplitude(length / directLength) * rolloff : 0;
		double high = 0;
		for (int i = 1; i + 1 < points.length; i++) {
			double bend = Lattice.bendDegrees(points[i - 1], points[i], points[i + 1]) / RIGHT_ANGLE_DEGREES;
			loss += settings.bendLossDb() * bend;
			high += settings.bendHighLossDb() * bend;
		}
		return new double[] {loss, high};
	}

	/** The route once its segments are added to {@code cost}; skipped when one is mute, null when out of budget. */
	private @Nullable Route traced(Path path, double[] cost, TraceBudget budget, boolean keepPoints) {
		double[][] points = path.points();
		double loss = cost[0];
		double high = cost[1];
		@Nullable PathLoss[] known = path.known();
		for (int i = 0; i < known.length; i++) {
			PathLoss segment = known[i];
			if (segment == null) {
				segment = segments.trace(points[i], points[i + 1], budget, null);
			}
			if (segment == null) {
				return null;
			}
			if (segment.mute()) {
				return Route.SKIPPED;
			}
			loss += segment.lossDb();
			high += segment.highLossDb();
		}
		double[] lastLeg = Lattice.unit(points[points.length - 2], points[points.length - 1]);
		return new Route(loss, high, lastLeg, keepPoints ? points : null);
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

	private static double[] point(Vec v) {
		return new double[] {v.x(), v.y(), v.z()};
	}
}
