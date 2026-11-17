package com.ravecraft.mod.core.acoustics.occlusion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Walks a straight segment voxel by voxel (Amanatides–Woo) and adds up what it loses: the materials it crosses
 * (weighted by their fill along the entry axis), the media it travels through (except air, whose absorption
 * {@code Propagation} applies) and the interfaces between media. The start and end voxels bring no material (the
 * speaker's block, the listener's head). A medium of impedance 0 anywhere on the way mutes the segment. Both totals
 * are capped at {@link OcclusionSettings#maxLossDb()}.
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
	/** The 26 unit directions to the neighbours of a voxel, in a fixed order. */
	private static final double[][] DIRECTIONS = directions();

	private final AcousticGrid grid;
	private final OcclusionSettings settings;
	private final AcousticMedium air;
	private final VoxelSample sample = new VoxelSample();
	private final int[] cell = new int[3];
	private final int[] end = new int[3];
	private final int[] step = new int[3];
	private final double[] tMax = new double[3];
	private final double[] tDelta = new double[3];

	/** A candidate waypoint near one end, with the trace that reaches it from that end. */
	private record Waypoint(double x, double y, double z, PathLoss fromEnd, double distanceToOther) {
	}

	/** One way the sound gets through: broadband loss and extra treble loss, both infinite when it is mute. */
	private record Route(double lossDb, double highLossDb) {
		static final Route MUTE = new Route(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
	}

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
		double[] from = {x0, y0, z0};
		double[] delta = {x1 - x0, y1 - y0, z1 - z0};
		double length = Math.sqrt(delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]);
		cell[0] = (int) Math.floor(x0);
		cell[1] = (int) Math.floor(y0);
		cell[2] = (int) Math.floor(z0);
		end[0] = (int) Math.floor(x1);
		end[1] = (int) Math.floor(y1);
		end[2] = (int) Math.floor(z1);
		int entryAxis = 0;
		for (int a = 0; a < 3; a++) {
			double d = delta[a];
			if (d > 0) {
				step[a] = 1;
				tMax[a] = (cell[a] + 1 - from[a]) / d;
				tDelta[a] = 1 / d;
			} else if (d < 0) {
				step[a] = -1;
				tMax[a] = (from[a] - cell[a]) / -d;
				tDelta[a] = -1 / d;
			} else {
				step[a] = 0;
				tMax[a] = Double.POSITIVE_INFINITY;
				tDelta[a] = Double.POSITIVE_INFINITY;
			}
			if (Math.abs(d) > Math.abs(delta[entryAxis])) {
				entryAxis = a;
			}
		}

		double loss = 0;
		double high = 0;
		double t = 0;
		boolean first = true;
		@Nullable AcousticMedium previous = null;
		while (true) {
			if (!budget.take()) {
				return null;
			}
			boolean last = cell[0] == end[0] && cell[1] == end[1] && cell[2] == end[2];
			int next = -1;
			double tExit = 1;
			if (!last) {
				// Lowest axis first on ties; an axis already at its end coordinate never steps again.
				for (int a = 0; a < 3; a++) {
					if (cell[a] != end[a] && (next < 0 || tMax[a] < tMax[next])) {
						next = a;
					}
				}
				tExit = Math.clamp(tMax[next], t, 1);
			}
			double inside = (tExit - t) * length;

			if (inside >= MIN_LENGTH) {
				grid.sample(cell[0], cell[1], cell[2], sample);
				AcousticMedium medium = sample.medium != null ? sample.medium : air;
				if (medium.impedance() <= 0) {
					return PathLoss.mute(length);
				}
				AcousticMaterial material = sample.material;
				if (material != null && !first && !last) {
					double weight = sample.fill(entryAxis) * Math.min(1, inside);
					loss += material.lossDb() * weight;
					high += material.highLossDb() * weight;
				}
				if (!medium.name().equals(air.name())) {
					loss += medium.lossDbPerBlock() * inside;
					high += medium.highLossDbPerBlock() * inside;
				}
				if (previous != null && !previous.equals(medium)) {
					double interfaceLoss = Interfaces.lossDb(previous, medium, settings.interfaceScale());
					if (Double.isInfinite(interfaceLoss)) {
						return PathLoss.mute(length);
					}
					loss += interfaceLoss;
					high += Interfaces.highLossDb(interfaceLoss, settings.interfaceHighRatio());
				}
				previous = medium;
			}

			if (last) {
				break;
			}
			cell[next] += step[next];
			tMax[next] += tDelta[next];
			t = tExit;
			entryAxis = next;
			first = false;
		}
		double max = settings.maxLossDb();
		return new PathLoss(Math.min(loss, max), Math.min(high, max), length);
	}

	/**
	 * The occlusion between a listener at (lx, ly, lz) and a source at (sx, sy, sz): the direct path, plus up to
	 * {@value #BEST_DETOURS} detours through one or two waypoints when detours are enabled and the direct path loses
	 * 3 dB or more. A detour pays its segments, the extra distance ({@code 20·log10(length / direct)·rolloff}) and
	 * each bend ({@code bendLossDb} and {@code bendHighLossDb} per 90°); detours longer than {@code maxPathLength},
	 * running along the straight line or losing at least as much as the direct path are ignored. Null when {@code budget} ran out; {@link Occlusion#NONE} without visiting any voxel when occlusion
	 * is disabled.
	 */
	public @Nullable Occlusion occlusion(double lx, double ly, double lz, double sx, double sy, double sz,
			double rolloff, double maxPathLength, TraceBudget budget) {
		if (!settings.enabled()) {
			return Occlusion.NONE;
		}
		PathLoss direct = trace(lx, ly, lz, sx, sy, sz, budget);
		if (direct == null) {
			return null;
		}
		List<Route> routes = new ArrayList<>();
		routes.add(new Route(direct.lossDb(), direct.highLossDb()));
		if (!settings.detours() || direct.lossDb() < DETOUR_THRESHOLD_DB) {
			return combine(routes);
		}

		List<Waypoint> nearListener = waypoints(lx, ly, lz, sx, sy, sz, budget);
		if (nearListener == null) {
			return null;
		}
		List<Waypoint> nearSource = waypoints(sx, sy, sz, lx, ly, lz, budget);
		if (nearSource == null) {
			return null;
		}
		double[] source = {sx, sy, sz};
		double[] listener = {lx, ly, lz};
		// Each path: its points from source to listener and the segments already traced (null: to trace).
		List<double[][]> paths = new ArrayList<>();
		List<@Nullable PathLoss[]> known = new ArrayList<>();
		for (Waypoint p : nearListener) {
			paths.add(new double[][] {source, point(p), listener});
			known.add(new PathLoss[] {null, p.fromEnd()});
		}
		for (Waypoint q : nearSource) {
			paths.add(new double[][] {source, point(q), listener});
			known.add(new PathLoss[] {q.fromEnd(), null});
		}
		for (Waypoint q : nearSource) {
			for (Waypoint p : nearListener) {
				paths.add(new double[][] {source, point(q), point(p), listener});
				known.add(new PathLoss[] {q.fromEnd(), null, p.fromEnd()});
			}
		}
		List<Route> detours = new ArrayList<>();
		for (int i = 0; i < paths.size(); i++) {
			Route route = route(paths.get(i), known.get(i), direct.length(), rolloff, maxPathLength, budget);
			if (route == null) {
				return null;
			}
			// A detour losing at least as much as the direct path mostly crosses the same obstacles (the same wall a
			// little further along): adding its energy would count that transmission twice.
			if (route.lossDb() < direct.lossDb()) {
				detours.add(route);
			}
		}
		detours.sort(Comparator.comparingDouble(Route::lossDb));
		for (int i = 0; i < Math.min(BEST_DETOURS, detours.size()); i++) {
			routes.add(detours.get(i));
		}
		return combine(routes);
	}

	/**
	 * The waypoints around the end at (ax, ay, az): for each of the 26 directions and each radius, the point that far
	 * from the centre of the end's voxel, kept when its voxel holds no material, is in the end's medium and is reached
	 * from the end losing less than 3 dB; the {@value #WAYPOINTS_PER_END} closest to (ox, oy, oz) are returned, each
	 * with the trace that reached it so that it is not traced again. Null when {@code budget} ran out.
	 */
	private @Nullable List<Waypoint> waypoints(double ax, double ay, double az, double ox, double oy, double oz,
			TraceBudget budget) {
		int bx = (int) Math.floor(ax);
		int by = (int) Math.floor(ay);
		int bz = (int) Math.floor(az);
		grid.sample(bx, by, bz, sample);
		AcousticMedium medium = sample.medium != null ? sample.medium : air;
		Set<Long> seen = new HashSet<>();
		List<Waypoint> found = new ArrayList<>();
		for (double[] d : DIRECTIONS) {
			for (double r : WAYPOINT_RADII) {
				double px = bx + 0.5 + d[0] * r;
				double py = by + 0.5 + d[1] * r;
				double pz = bz + 0.5 + d[2] * r;
				int vx = (int) Math.floor(px);
				int vy = (int) Math.floor(py);
				int vz = (int) Math.floor(pz);
				if (!seen.add(key(vx, vy, vz))) {
					continue;
				}
				grid.sample(vx, vy, vz, sample);
				AcousticMedium here = sample.medium != null ? sample.medium : air;
				if (sample.material != null || !here.equals(medium)) {
					continue;
				}
				PathLoss reach = trace(ax, ay, az, px, py, pz, budget);
				if (reach == null) {
					return null;
				}
				if (reach.lossDb() >= DETOUR_THRESHOLD_DB) {
					continue;
				}
				double dx = ox - px;
				double dy = oy - py;
				double dz = oz - pz;
				found.add(new Waypoint(px, py, pz, reach, Math.sqrt(dx * dx + dy * dy + dz * dz)));
			}
		}
		found.sort(Comparator.comparingDouble(Waypoint::distanceToOther));
		return found.size() > WAYPOINTS_PER_END ? found.subList(0, WAYPOINTS_PER_END) : found;
	}

	/**
	 * The cost of the polyline through {@code points}; {@code segments[i]} is the known loss from point i to i + 1,
	 * or null to trace it. {@link Route#MUTE} when the path is longer than {@code maxPathLength} or no longer than the
	 * direct path (nothing is traced), or when a segment is mute; null when {@code budget} ran out.
	 */
	private @Nullable Route route(double[][] points, @Nullable PathLoss[] segments, double directLength,
			double rolloff, double maxPathLength, TraceBudget budget) {
		double length = 0;
		for (int i = 0; i + 1 < points.length; i++) {
			length += distance(points[i], points[i + 1]);
		}
		// Waypoints on the straight line give the direct path again: counting it twice would halve its loss.
		if (length > maxPathLength || length <= directLength + COLLINEAR_EPSILON) {
			return Route.MUTE;
		}
		double loss = 0;
		double high = 0;
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
				return Route.MUTE;
			}
			loss += segment.lossDb();
			high += segment.highLossDb();
		}
		if (directLength > 0) {
			loss += 20 * Math.log10(length / directLength) * rolloff;
		}
		for (int i = 1; i + 1 < points.length; i++) {
			double bend = bendDegrees(points[i - 1], points[i], points[i + 1]) / 90;
			loss += settings.bendLossDb() * bend;
			high += settings.bendHighLossDb() * bend;
		}
		return new Route(loss, high);
	}

	/** Sums the routes in energy, broadband and treble apart; {@link Occlusion#MUTE} when none gets through. */
	private Occlusion combine(List<Route> routes) {
		double energy = 0;
		double highEnergy = 0;
		for (Route route : routes) {
			energy += Math.pow(10, -route.lossDb() / 10);
			highEnergy += Math.pow(10, -(route.lossDb() + route.highLossDb()) / 10);
		}
		if (energy <= 0) {
			return Occlusion.MUTE;
		}
		double max = settings.maxLossDb();
		// Math.max also turns the -0.0 of a lossless path into 0.0.
		double loss = Math.max(0, -10 * Math.log10(energy));
		double total = highEnergy > 0 ? -10 * Math.log10(highEnergy) : Double.POSITIVE_INFINITY;
		return new Occlusion(Math.min(loss, max), Math.clamp(total - loss, 0, max));
	}

	/** The deviation in degrees between the directions a → b and b → c (0 when either has no length). */
	private static double bendDegrees(double[] a, double[] b, double[] c) {
		double ux = b[0] - a[0];
		double uy = b[1] - a[1];
		double uz = b[2] - a[2];
		double vx = c[0] - b[0];
		double vy = c[1] - b[1];
		double vz = c[2] - b[2];
		double norms = Math.sqrt((ux * ux + uy * uy + uz * uz) * (vx * vx + vy * vy + vz * vz));
		if (norms <= 0) {
			return 0;
		}
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
