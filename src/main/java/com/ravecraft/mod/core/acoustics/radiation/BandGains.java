package com.ravecraft.mod.core.acoustics.radiation;

/**
 * How loud each part of the sound is heard from one direction, as linear gains relative to the speaker's axis.
 *
 * @param low the gain of the bass
 * @param mid the gain of the mids
 * @param high the gain of the highs
 */
public record BandGains(double low, double mid, double high) {
	/** Everything heard as it is on the axis. */
	public static final BandGains FLAT = new BandGains(1, 1, 1);
	/** Closer than this, in blocks, the direction matters less and less. */
	private static final double NEAR = 1;

	/**
	 * The gain of the loudest band.
	 *
	 * @return the linear gain
	 */
	public double loudest() {
		return Math.max(low, Math.max(mid, high));
	}

	/**
	 * These gains brought down so that the loudest band is untouched.
	 *
	 * @return the gains relative to the loudest one, all at most 1
	 */
	public BandGains relative() {
		double loudest = loudest();
		return new BandGains(low / loudest, mid / loudest, high / loudest);
	}

	/**
	 * These gains heard from {@code distance}: next to the speaker the direction has no meaning any more.
	 *
	 * @param distance the distance to the speaker, in blocks
	 * @return these gains from one block on, flat at the speaker
	 */
	public BandGains near(double distance) {
		if (distance >= NEAR) {
			return this;
		}
		double t = distance / NEAR;
		return new BandGains(towardFlat(low, t), towardFlat(mid, t), towardFlat(high, t));
	}

	private static double towardFlat(double gain, double t) {
		return 1 + (gain - 1) * t;
	}
}
