package com.ravecraft.mod.core.acoustics.radiation;

/**
 * The far field of one driver: a vibrating piston set in a rigid sphere as big as the cabinet, at one frequency. The
 * exact solution is a series of spherical waves; it is summed at regular angles off the driver's axis, closer as the
 * sphere grows since the phase turns faster, and normalized to a magnitude of 1 on that axis. Waves advance as {@code e^(ikr)}.
 */
public final class PistonOnSphere {
	/** The speed of sound in air, in metres per second. */
	public static final double SPEED_OF_SOUND = 343;

	private static final int DEGREES = 180;
	/** How far the phase, which turns by at most {@code z} per radian, may go between two angles, in radians. */
	private static final double PHASE_STEP = 0.1;
	/** The series is summed up to {@code z + WIDTH × z^(1/3) + MARGIN}, where it has converged. */
	private static final double TERMS_WIDTH = 4;
	private static final double TERMS_MARGIN = 10;
	/** The powers of −i repeat every four terms. */
	private static final int PHASES = 4;

	private final int steps;
	private final double[] real;
	private final double[] imaginary;

	/**
	 * Computes the field of a driver.
	 *
	 * @param pistonRadius the driver's radius, in metres (at most the sphere's counts)
	 * @param sphereRadius the cabinet's equivalent radius, in metres
	 * @param frequency the frequency, in hertz
	 */
	public PistonOnSphere(double pistonRadius, double sphereRadius, double frequency) {
		double z = 2 * Math.PI * frequency / SPEED_OF_SOUND * sphereRadius;
		int terms = (int) (z + TERMS_WIDTH * Math.cbrt(z) + TERMS_MARGIN) + 1;
		Series series = new Series(z, terms, velocities(Math.min(1, pistonRadius / sphereRadius), terms));
		steps = Math.max(DEGREES, (int) Math.ceil(Math.PI * z / PHASE_STEP));
		real = new double[steps + 1];
		imaginary = new double[steps + 1];
		for (int step = 0; step <= steps; step++) {
			series.sum(Math.cos(Math.PI * step / steps));
			real[step] = series.sumReal;
			imaginary[step] = series.sumImaginary;
		}
		double onAxis = Math.hypot(real[0], imaginary[0]);
		for (int step = 0; step <= steps; step++) {
			real[step] /= onAxis;
			imaginary[step] /= onAxis;
		}
	}

	/**
	 * The real part of the pressure.
	 *
	 * @param cosAngle the cosine of the angle off the driver's axis
	 * @return the real part, relative to the magnitude on the axis
	 */
	public double real(double cosAngle) {
		return interpolate(real, cosAngle);
	}

	/**
	 * The imaginary part of the pressure.
	 *
	 * @param cosAngle the cosine of the angle off the driver's axis
	 * @return the imaginary part, relative to the magnitude on the axis
	 */
	public double imaginary(double cosAngle) {
		return interpolate(imaginary, cosAngle);
	}

	/**
	 * The amplitude of the pressure.
	 *
	 * @param cosAngle the cosine of the angle off the driver's axis
	 * @return the amplitude, 1 on the axis
	 */
	public double magnitude(double cosAngle) {
		return Math.hypot(real(cosAngle), imaginary(cosAngle));
	}

	private double interpolate(double[] table, double cosAngle) {
		double step = Math.acos(Math.clamp(cosAngle, -1.0, 1.0)) / Math.PI * steps;
		int below = Math.min((int) step, steps - 1);
		double t = step - below;
		return table[below] * (1 - t) + table[below + 1] * t;
	}

	/** How much of each spherical wave a piston covering a cap of sine {@code sinCap} drives. */
	private static double[] velocities(double sinCap, int terms) {
		double[] legendre = new double[terms + 1];
		legendre(legendre, Math.sqrt(1 - sinCap * sinCap));
		double[] velocities = new double[terms];
		velocities[0] = (1 - legendre[1]) / 2;
		for (int m = 1; m < terms; m++) {
			velocities[m] = (legendre[m - 1] - legendre[m + 1]) / 2;
		}
		return velocities;
	}

	/** Fills {@code out} with the Legendre polynomials of {@code x}, from degree 0. */
	private static void legendre(double[] out, double x) {
		out[0] = 1;
		out[1] = x;
		for (int m = 1; m + 1 < out.length; m++) {
			out[m + 1] = ((2 * m + 1) * x * out[m] - m * out[m - 1]) / (m + 1);
		}
	}

	/** The coefficients of the series: {@code (−i)^m × velocity / h'_m(z)}, with the spherical Hankel functions. */
	private static final class Series {
		private final double[] coefReal;
		private final double[] coefImaginary;
		private final double[] polynomials;
		private double sumReal;
		private double sumImaginary;

		/** Builds the series for the size {@code z} of the sphere, in wavelengths over 2π. */
		Series(double z, int terms, double[] velocities) {
			coefReal = new double[terms];
			coefImaginary = new double[terms];
			polynomials = new double[terms];
			double sin = Math.sin(z);
			double cos = Math.cos(z);
			// h_0 = −i e^(iz) / z and h_1 = e^(iz) (−1/z − i/z²); the previous and current orders, then their step.
			double previousReal = sin / z;
			double previousImaginary = -cos / z;
			double hReal = -cos / z + sin / (z * z);
			double hImaginary = -sin / z - cos / (z * z);
			coefficient(0, velocities[0], -hReal, -hImaginary);
			for (int m = 1; m < terms; m++) {
				coefficient(m, velocities[m], previousReal - (m + 1) / z * hReal,
						previousImaginary - (m + 1) / z * hImaginary);
				double nextReal = (2 * m + 1) / z * hReal - previousReal;
				double nextImaginary = (2 * m + 1) / z * hImaginary - previousImaginary;
				previousReal = hReal;
				previousImaginary = hImaginary;
				hReal = nextReal;
				hImaginary = nextImaginary;
			}
		}

		/** Stores {@code (−i)^m × velocity / (derivative)}. */
		private void coefficient(int m, double velocity, double derivativeReal, double derivativeImaginary) {
			double scale = velocity / (derivativeReal * derivativeReal + derivativeImaginary * derivativeImaginary);
			double re = derivativeReal * scale;
			double im = -derivativeImaginary * scale;
			for (int turn = m % PHASES; turn > 0; turn--) {
				double turned = im;
				im = -re;
				re = turned;
			}
			coefReal[m] = re;
			coefImaginary[m] = im;
		}

		/** Sums the series at {@code x}, the cosine of the angle. */
		void sum(double x) {
			legendre(polynomials, x);
			sumReal = 0;
			sumImaginary = 0;
			for (int m = 0; m < polynomials.length; m++) {
				sumReal += coefReal[m] * polynomials[m];
				sumImaginary += coefImaginary[m] * polynomials[m];
			}
		}
	}
}
