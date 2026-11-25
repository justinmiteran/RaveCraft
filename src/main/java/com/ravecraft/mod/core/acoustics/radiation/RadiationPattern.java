package com.ravecraft.mod.core.acoustics.radiation;

import com.ravecraft.mod.core.acoustics.Decibels;
import java.util.Arrays;

/**
 * How a speaker radiates in every direction, band by band, relative to its axis: a grid computed once from its cabinet
 * ({@link RadiationCalculator}), then interpolated. Immutable, so any thread may read it.
 */
public final class RadiationPattern {
	private static final int BANDS = 3;
	private static final int STEP_DEG = 10;
	private static final int FULL_TURN_DEG = 360;
	private static final int POLE_DEG = 90;
	private static final int AZIMUTHS = FULL_TURN_DEG / STEP_DEG;
	private static final int ELEVATIONS = 2 * POLE_DEG / STEP_DEG + 1;
	/** No direction loses more than this. */
	private static final double FLOOR_DB = -48;

	/** The same in every direction. */
	public static final RadiationPattern FLAT = new RadiationPattern(flatPowers());

	/** Powers relative to the axis, by band, elevation from the bottom, and azimuth. */
	private final double[][][] powers;

	private RadiationPattern(double[][][] powers) {
		this.powers = powers;
	}

	/**
	 * Computes the pattern of a cabinet. Takes a noticeable time: do it once, away from the render thread.
	 *
	 * @param cabinet the cabinet
	 * @return its pattern
	 * @throws IllegalArgumentException if the cabinet makes no sound on its axis, or its field cannot be computed
	 */
	public static RadiationPattern of(Cabinet cabinet) {
		RadiationCalculator calculator = new RadiationCalculator(cabinet);
		double[][][] powers = new double[BANDS][ELEVATIONS][];
		for (int band = 0; band < BANDS; band++) {
			double onAxis = calculator.power(band, 0, 0);
			for (int i = 0; i < ELEVATIONS; i++) {
				powers[band][i] = ring(calculator, band, i * STEP_DEG - POLE_DEG, onAxis);
			}
		}
		return new RadiationPattern(powers);
	}

	/** The powers of one elevation; a pole is the same direction whatever the azimuth. */
	private static double[] ring(RadiationCalculator calculator, int band, int elevationDeg, double onAxis) {
		boolean pole = Math.abs(elevationDeg) == POLE_DEG;
		double[] ring = new double[AZIMUTHS];
		for (int i = 0; i < AZIMUTHS; i++) {
			double power = pole && i > 0 ? ring[0] : calculator.power(band, i * STEP_DEG, elevationDeg) / onAxis;
			if (!Double.isFinite(power)) {
				throw new IllegalArgumentException("No sound on the axis of the cabinet");
			}
			ring[i] = Math.max(Decibels.toPower(FLOOR_DB), power);
		}
		return ring;
	}

	/**
	 * The gains heard from a direction.
	 *
	 * @param azimuthDeg the azimuth, in degrees to the right of the speaker's axis
	 * @param elevationDeg the elevation, in degrees above the speaker's axis
	 * @return the gain of each band
	 */
	public BandGains at(double azimuthDeg, double elevationDeg) {
		double azimuth = (azimuthDeg % FULL_TURN_DEG + FULL_TURN_DEG) % FULL_TURN_DEG / STEP_DEG;
		double elevation = (Math.clamp(elevationDeg, -POLE_DEG, POLE_DEG) + POLE_DEG) / STEP_DEG;
		return new BandGains(gain(0, azimuth, elevation), gain(1, azimuth, elevation), gain(2, azimuth, elevation));
	}

	/**
	 * The mean gains all around at the speaker's height, for a speaker that points nowhere.
	 *
	 * @return the gain of each band
	 */
	public BandGains around() {
		return new BandGains(meanGain(0), meanGain(1), meanGain(2));
	}

	private double meanGain(int band) {
		return Math.sqrt(Arrays.stream(powers[band][ELEVATIONS / 2]).average().orElse(1));
	}

	/** Interpolates the powers of the four grid points around a direction given in grid steps. */
	private double gain(int band, double azimuth, double elevation) {
		int low = Math.min((int) elevation, ELEVATIONS - 2);
		double t = elevation - low;
		return Math.sqrt(onRing(powers[band][low], azimuth) * (1 - t) + onRing(powers[band][low + 1], azimuth) * t);
	}

	private static double onRing(double[] ring, double azimuth) {
		int left = (int) azimuth;
		double t = azimuth - left;
		return ring[left % AZIMUTHS] * (1 - t) + ring[(left + 1) % AZIMUTHS] * t;
	}

	private static double[][][] flatPowers() {
		double[][][] powers = new double[BANDS][ELEVATIONS][AZIMUTHS];
		for (double[][] band : powers) {
			for (double[] ring : band) {
				Arrays.fill(ring, 1);
			}
		}
		return powers;
	}
}
