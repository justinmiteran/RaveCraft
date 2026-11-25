package com.ravecraft.mod.core.acoustics.radiation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The power a cabinet sends in any direction, band by band. The drivers add up in pressure, with the delay that comes
 * from their positions; each band is averaged over a few frequencies, and each direction over its surroundings, which
 * smooths the peaks of a perfect sphere and the narrow interferences.
 */
final class RadiationCalculator {
	/** The bass, the mids and the highs: lowest and highest frequency, in hertz. */
	private static final double[][] BANDS_HZ = {{63, 200}, {500, 2000}, {4000, 12_000}};
	private static final int FREQUENCIES = 9;
	/** Points around each direction, and how far they are from it. */
	private static final int AROUND = 6;
	private static final double AROUND_RAD = Math.toRadians(10);
	private static final double QUARTER_TURN_DEG = 90;

	private final List<Driver> drivers;
	private final double[][] axes;
	/** By band, then frequency. */
	private final Tone[][] tones = new Tone[BANDS_HZ.length][FREQUENCIES];

	/** What each driver plays at one frequency. */
	private record Tone(double waveNumber, PistonOnSphere[] pistons, double[] weights) {
	}

	RadiationCalculator(Cabinet cabinet) {
		drivers = cabinet.drivers();
		axes = drivers.stream().map(d -> direction(d.azimuthDeg(), d.elevationDeg())).toArray(double[][]::new);
		for (int band = 0; band < BANDS_HZ.length; band++) {
			double ratio = BANDS_HZ[band][1] / BANDS_HZ[band][0];
			for (int i = 0; i < FREQUENCIES; i++) {
				double hz = BANDS_HZ[band][0] * Math.pow(ratio, i / (FREQUENCIES - 1.0));
				tones[band][i] = tone(cabinet.sphereRadius(), hz);
			}
		}
	}

	/** Drivers of the same size share their field. */
	private Tone tone(double sphereRadius, double hz) {
		Map<Double, PistonOnSphere> byRadius = new HashMap<>();
		PistonOnSphere[] pistons = drivers.stream().map(driver -> byRadius.computeIfAbsent(driver.radius(),
				radius -> new PistonOnSphere(radius, sphereRadius, hz))).toArray(PistonOnSphere[]::new);
		double[] weights = drivers.stream().mapToDouble(driver -> driver.weight(hz)).toArray();
		return new Tone(2 * Math.PI * hz / PistonOnSphere.SPEED_OF_SOUND, pistons, weights);
	}

	/** The mean power of {@code band} (0 bass, 1 mids, 2 highs) around the given direction. */
	double power(int band, double azimuthDeg, double elevationDeg) {
		double[] centre = direction(azimuthDeg, elevationDeg);
		double[] right = direction(azimuthDeg + QUARTER_TURN_DEG, 0);
		double[] up = cross(centre, right);
		double sum = power(band, centre);
		for (int i = 0; i < AROUND; i++) {
			double turn = 2 * Math.PI * i / AROUND;
			double[] point = new double[centre.length];
			for (int axis = 0; axis < point.length; axis++) {
				double aside = Math.cos(turn) * right[axis] + Math.sin(turn) * up[axis];
				point[axis] = Math.cos(AROUND_RAD) * centre[axis] + Math.sin(AROUND_RAD) * aside;
			}
			sum += power(band, point);
		}
		return sum / (AROUND + 1);
	}

	private double power(int band, double[] direction) {
		double sum = 0;
		for (Tone tone : tones[band]) {
			sum += power(tone, direction);
		}
		return sum / FREQUENCIES;
	}

	/** The power of one tone toward {@code direction}: its drivers add up with their path differences. */
	private double power(Tone tone, double[] direction) {
		double real = 0;
		double imaginary = 0;
		for (int i = 0; i < axes.length; i++) {
			Driver driver = drivers.get(i);
			double cosAngle = dot(axes[i], direction);
			double pistonReal = tone.pistons()[i].real(cosAngle) * tone.weights()[i];
			double pistonImaginary = tone.pistons()[i].imaginary(cosAngle) * tone.weights()[i];
			double ahead = driver.right() * direction[0] + driver.up() * direction[1] + driver.front() * direction[2];
			double cos = Math.cos(tone.waveNumber() * ahead);
			double sin = Math.sin(tone.waveNumber() * ahead);
			real += pistonReal * cos + pistonImaginary * sin;
			imaginary += pistonImaginary * cos - pistonReal * sin;
		}
		return real * real + imaginary * imaginary;
	}

	/** The unit vector (right, up, front) of a direction. */
	private static double[] direction(double azimuthDeg, double elevationDeg) {
		double azimuth = Math.toRadians(azimuthDeg);
		double elevation = Math.toRadians(elevationDeg);
		return new double[] {Math.sin(azimuth) * Math.cos(elevation), Math.sin(elevation),
				Math.cos(azimuth) * Math.cos(elevation)};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}
}
